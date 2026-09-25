# Hồ sơ bàn giao cho Claude review — toàn vẹn dữ liệu mẻ xe khác

**Ngày lập:** 27-08-2026

**Nhánh làm việc:** `fix/datahelper-offline-sync`

**Base commit hiện tại:** `24523b1` — `Stop the receipt data going out wrong, and stop one conflict poisoning a batch`

**Trạng thái:** code chưa stage, chưa commit; đã chạy toàn bộ unit test và compile Release

**Mục đích tài liệu:** mô tả đầy đủ thay đổi trong working tree để Claude review độc lập trên mã nguồn thật

> Claude không nên chỉ đánh giá từ tài liệu này. Hãy đọc toàn bộ diff, ba file test mới chưa
> được Git track và các caller liên quan. Tài liệu mô tả ý định/invariant; mã nguồn mới là
> nguồn sự thật để tìm sai lệch giữa ý định và triển khai.

## 1. Yêu cầu Claude thực hiện

Review theo hướng correctness và data integrity, ưu tiên theo thứ tự:

1. Có bất kỳ đường nào làm mất, ghi thiếu, trộn nửa cũ/nửa mới hoặc làm stale dữ liệu mẻ xe khác không?
2. Có bất kỳ đường UI/background nào POST, PATCH hoặc mutate replica xe khác không?
3. Cơ chế bảo vệ dữ liệu xe hiện tại có còn giữ đúng offline-first và chống snapshot cũ ghi đè không?
4. Cursor, generation token, transaction và tombstone có đóng hết race giữa Preview GET, modified pull và POST không?
5. Phiếu/hoá đơn gộp có luôn lấy header từ xe hiện tại, nhưng lấy đúng sản lượng và MIN/MAX thời gian từ toàn bộ xe không?
6. Migration 13→14 và bảo trì legacy có an toàn trên thiết bị đang chạy bản 102/103 hoặc các DB cũ hơn không?
7. Test hiện tại còn thiếu case quan trọng nào trước khi canary?

Khi báo lỗi, vui lòng ghi:

- Mức độ `P0/P1/P2/P3`.
- File và dòng cụ thể.
- Chuỗi sự kiện tái hiện.
- Dữ liệu nào bị mất/sai/ghi nhầm.
- Patch tối thiểu đề nghị.
- Test hồi quy cần thêm.

## 2. Cách lấy đúng phạm vi review

Chạy tại thư mục gốc dự án:

```bash
git status --short
git diff --check
git diff -- app/build.gradle app/src/main app/src/test
```

Ba test mới đang là file untracked nên không xuất hiện trong `git diff` thông thường. Phải đọc trực tiếp:

```text
app/src/test/java/com/megatech/fms/data/AppDatabaseMigrationTest.java
app/src/test/java/com/megatech/fms/helpers/RefuelRemoteReplicaIntegrationTest.java
app/src/test/java/com/megatech/fms/model/DocumentReplicaIntegrityTest.java
```

Tại thời điểm lập tài liệu, tracked diff có **17 file, 2169 dòng thêm, 315 dòng xoá**.
Ba test mới có tổng cộng **1303 dòng**.

Các file/thư mục untracked sau có từ trước và không thuộc patch đồng bộ này; không nhận xét
chúng như thay đổi của patch:

```text
backups/refuel-detail-ui-before-20260818-154918/
backups/refuel-detail-ui-before-content-simplification-20260818-164144/
backups/refuel-detail-ui-before-overlap-fix-20260818-162932/
backups/refuel-detail-ui-v2-20260819.zip
docs/YEUCAU-DIEU-TRA-SERVER-GIO-BAT-DAU-SAI.md
```

Tài liệu review này cũng là file mới, không phải production code.

## 3. Bối cảnh nghiệp vụ và hiện tượng

Tab kế hoạch có hai loại dữ liệu:

1. **Mẻ của xe hiện tại:** xe này tạo và chỉnh sửa số đồng hồ, sản lượng, thời gian, trạng thái,
   người thực hiện và metadata chứng từ. Dữ liệu phải lưu offline trước, sau đó đồng bộ lên server.
2. **Mẻ của xe khác:** xe khác tạo/chốt rồi đẩy lên server. Xe hiện tại chỉ nhận bản sao từ server
   để có đủ dòng khi xuất phiếu/hoá đơn gộp. Xe hiện tại không có thẩm quyền tạo, sửa hoặc gửi lại
   mẻ đó.

Người dùng báo hai nhóm tình huống:

- Tình huống cũ: phải bấm cập nhật nhiều lần mới nhận/sửa được giờ kết thúc; snapshot cũ và
  xung đột ghi có thể làm màn hình tiếp tục đứng trên dữ liệu cũ.
- Tình huống mới: một số xe không nhận đầy đủ dữ liệu mẻ của xe khác; ảnh hiện trường có dòng
  xe khác thiếu giờ bắt đầu/kết thúc trong khi server/xe thực hiện đã có dữ liệu. Bản 102/103
  không thấy lỗi này.
- Người dùng còn báo **nhiệt độ và tỷ trọng của xe hiện tại đôi khi phải nhập nhiều lần mới
  nhận**. Hai log được cung cấp chỉ ghi được giá trị cuối `Temperature=27.0`, `Density=0.7940`,
  chưa đủ để đếm số lần nhập. Tuy nhiên log 25-08 lúc 22:06:16 có dòng “Lưu bị chặn, thử lại
  bằng ConfirmFieldsPatch”, chứng minh baseline/version conflict cùng nhóm đã xảy ra trong ca.
  Code Preview hiện xác nhận còn một khe riêng cho hai trường này; xem candidate 0D ở mục 10.

Đánh giá dẫn tới thiết kế patch:

- Không thể dùng cùng một luật merge cho xe hiện tại và xe khác. Với xe hiện tại, số liệu thiết
  bị/local cần được bảo vệ. Với xe khác, chính server là nguồn authoritative cho toàn payload,
  kể cả `StartTime`/`EndTime`.
- Chỉ tải từng UID đã có trong Room không chứng minh được collection đầy đủ và không phát hiện
  xe mới. Membership `Others` phải đến từ snapshot root của server.
- Cursor modified pull không được lấy từ `MAX(DateUpdated)` trong Room, vì POST/GET của xe hiện
  tại có thể đẩy mốc đó vượt qua thay đổi xe khác chưa từng tải.
- Root, children `Others` và membership không được commit rời nhau.
- Response cũ về muộn không được thắng response mới chỉ vì revision server bằng 0 hoặc không tăng.

## 4. Invariant bắt buộc sau patch

### I1 — Phân quyền dữ liệu

- `CURRENT`: chỉ khi TruckNo/TruckId đủ bằng chứng là xe hiện tại.
- `FOREIGN`: đủ bằng chứng là xe khác.
- `UNKNOWN`: thiếu định danh hoặc TruckNo và TruckId mâu thuẫn.
- Mọi đường ghi fail-closed với `UNKNOWN`.
- `FOREIGN` là read-only trên UI, direct save, batch save, PATCH và background queue.

### I2 — Replica xe khác là snapshot server

- Nhận full payload server, ưu tiên raw JSON để giữ cả key model app chưa biết.
- Không dùng merge bảo vệ field local cho replica.
- Chỉ giữ metadata nội bộ Room: `localId`, membership cache và các version kỹ thuật cần chống stale.
- Sau lưu: `remoteReplica=true`, `isLocalModified=false`, `synced=true`, `postStatus=SUCCESS`.
- Không tự sinh thời gian hiện tại khi server thiếu `StartTime`/`EndTime`.

### I3 — Mẻ xe hiện tại vẫn offline-first

- Ghi Room trước khi HTTP.
- Server-owned fields được rebase/merge theo luật cũ.
- Client/device-owned fields và thay đổi dirty không bị pull ghi đè.
- Snapshot UI cũ, ACK nhầm hoặc POST response về sau reassignment không được hạ dữ liệu mới.

### I4 — Collection `Others` có ngữ nghĩa rõ

- `Others` vắng/malformed: chưa có snapshot authoritative.
- `Others: []`: snapshot authoritative rỗng.
- `Others: [...]`: đúng danh sách UID theo đúng thứ tự server.
- Sau snapshot đầy đủ đầu tiên, offline chỉ dùng membership đã lưu; không hồi sinh row stale cùng FlightId.
- Phiếu gộp bị chặn khi server chưa xác nhận collection đầy đủ.

### I5 — Tính nguyên tử

- Root + toàn bộ child + membership là một snapshot transaction.
- Trong modified pull, mỗi root + `Others` + `Flight` là một transaction.
- Child/identity/raw JSON lỗi phải rollback root và membership liên quan.
- Batch lỗi không được tiến cursor.

### I6 — Thứ tự response

- Mỗi read có generation token.
- Theo từng UID lưu cả generation mới nhất đã bắt đầu và mới nhất đã commit.
- Chỉ mark applied sau khi transaction thành công.
- Root cũ không được hạ shared child mà root khác đã cập nhật mới hơn.
- Tombstone cũ không được xoá detail mới.

### I7 — Chứng từ

- Header, khách hàng, hãng bay, giá, thuế, currency, template và TechLog luôn từ mẻ xe hiện tại.
- Replica xe khác chỉ đóng góp dòng sản lượng và thời gian để tính MIN start/MAX end.
- Không mutate/reorder danh sách nguồn và không mutate object replica khi dựng chứng từ.
- Không phát hành chứng từ chỉ gồm các mẻ xe khác.

## 5. Kiến trúc luồng sau sửa

### 5.1 Preview

```text
Người dùng mở/bấm CẬP NHẬT
  -> cấp UI loadGeneration + DataHelper readGeneration
  -> đúng 1 GET root
  -> parser giữ raw JSON root và từng Others
  -> xác minh root/child identity, ownership, flight, duplicate
  -> transaction: reconcile root + replace foreign + merge current child + membership
  -> mark generation applied sau commit
  -> đọc lại đúng UID theo membership server
  -> UI bỏ callback superseded
  -> chỉ cho in gộp nếu collectionComplete và từng dòng hợp lệ
```

### 5.2 Modified pull nền

```text
SharedPreferences cursor riêng theo TruckId + TruckNo
  -> query cursor - 5 phút overlap
  -> preflight toàn response: null/UID/ID/tombstone/duplicate
  -> lần lượt xử lý từng root trong transaction riêng
       foreign: full replace authoritative
       current: safe merge
       payload thực sự APPLIED mới ghi Others + Flight
       KEPT_NEWER không dùng metadata payload cũ
       tombstone kiểm identity, generation, revision/date và dirty
  -> chỉ khi tất cả item thành công mới commit max(DateUpdated) của response
```

### 5.3 Ghi từ xe hiện tại

```text
UI lọc current trước khi gọi tầng ghi
  -> DataHelper classify snapshot + row Room
  -> recheck ownership ngay trước local write
  -> ghi offline-first
  -> HTTP POST không có nested Others
  -> validate UID/ID/ownership của ACK
  -> re-read row sau HTTP
  -> nếu role/version đã đổi: bỏ ACK, giữ row mới/dirty
```

## 6. Thay đổi chi tiết theo file

### 6.1 `app/build.gradle`

- Tăng `versionCode` từ `114` lên `115`.
- Không đổi `versionNumber`, `buildNumber` hoặc `patchNumber` trong patch này.

### 6.2 `AppDatabase.java`

- Room database version `13 → 14`.
- Thêm `MIGRATION_13_14`:

```sql
ALTER TABLE RefuelItem
ADD COLUMN remoteReplica INTEGER NOT NULL DEFAULT 0;

ALTER TABLE RefuelItem
ADD COLUMN remoteOthersUidsJson TEXT;
```

- Đăng ký migration trong chuỗi `9→10→11→12→13→14`.
- `fallbackToDestructiveMigration()` vẫn tồn tại như trước; Claude cần đánh giá ảnh hưởng với DB
  ngoài chuỗi migration đã đăng ký.

### 6.3 `RefuelItem.java`

- Thêm `remoteReplica`: marker Room read-only, không chỉ suy từ cấu hình xe tại runtime.
- Thêm `remoteOthersUidsJson`:
  - `null`: chưa từng nhận membership đầy đủ.
  - `"[]"`: server đã xác nhận không có child.
  - JSON array khác: danh sách UID authoritative theo thứ tự server.
- `toRefuelItemData()` đọc raw JSON để phân biệt key thời gian vắng/null. Nếu vắng/null thì dùng
  cột Room, kể cả cột là null; replica JSON hỏng cũng không được tự sinh `new Date()`.

### 6.4 `RefuelItemDao.java`

- Thêm update riêng cho `remoteOthersUidsJson`, không làm row dirty.
- Thêm xoá tombstone theo đúng primary key tablet `localId`, với điều kiện `NOT isLocalModified`.
- `getModifiedForSync()` thêm `AND NOT remoteReplica`.
- `getModified()` không đổi để màn hình/bảo trì vẫn nhìn thấy đầy đủ row dirty.

### 6.5 `DataRepository.java`

- Thêm wrapper `runInTransaction` để DataHelper gom root/children/membership.
- Thêm persist/read membership có validate UID rỗng/trùng và phân biệt null với empty.
- Thêm `RemoteSnapshotResult { applied, keptNewer }` để caller phân biệt:
  - payload đã áp dụng;
  - row hiện tại mới hơn nên chủ ý giữ;
  - lỗi.
- `replaceRemoteRefuelSnapshotsDetailed()`:
  - preflight toàn input trước khi ghi;
  - bắt buộc UID, status, item type và raw JSON;
  - raw UID/Id phải khớp model;
  - cấm UID trùng và positive server Id trùng trong batch;
  - kiểm collision theo cả UID và server Id với DB hiện tại;
  - stale guard theo `ServerRevision`, sau đó `DateUpdated` khi revision legacy bằng 0;
  - không cho response revision đứng yên/lùi mở lại row `DONE`;
  - full replace truyền được null cho start/end/deleted;
  - giữ `localId` và membership cache của row cũ;
  - row mới luôn đặt `localId=0` trước insert, không tin localId từ server;
  - raw server JSON được lưu trực tiếp;
  - replica luôn clean/synced/SUCCESS/read-only.
- Thêm xoá tombstone chính xác qua `removeRemoteDeletedRefuel(RefuelItem)`.

### 6.6 `HttpClient.java`

- Dùng chung `parseRefuelItemResponse()` cho detail GET, list GET, modified GET và POST response.
- Parser:
  - giữ raw JSON root;
  - giữ raw JSON riêng cho từng child `Others`;
  - chỉ đặt `completeOthersSnapshot=true` khi `Others` thật sự là array và mọi child parse đủ,
    khác null, có UID;
  - `Others` vắng, malformed hoặc parse thiếu không được coi là authoritative;
  - key `StartTime`/`EndTime` vắng được normalize thành null;
  - UID vắng/rỗng được normalize thành null.
- POST dùng `buildRefuelPostPayload()` và loại hoàn toàn nested `Others`.
- Không còn mutate request object bằng server Id trước khi tầng DataHelper xác minh response.
- Sửa format modified cursor từ `yyy` thành `yyyy` và cố định `Locale.US`.

### 6.7 `RefuelItemData.java`

- Thêm transient `completeOthersSnapshot` để không đánh đồng empty với absent/malformed.
- `setStartTime(null)` được phép và không NPE.
- Cờ transient không được coi là field nghiệp vụ hoặc gửi lên server.

### 6.8 `DataRepository` + `DataHelper`: lưu snapshot `Others`

- `persistCompleteServerOthers()` xác minh:
  - root UID hợp lệ;
  - child UID không rỗng/trùng/root;
  - child cùng flight qua FlightUniqueId hoặc FlightId;
  - toàn bộ UID chưa bị read generation mới hơn supersede;
  - ownership child xác định được.
- Child foreign đi full replace; child current cùng xe (ví dụ tách mẻ/nạp thêm) đi safe merge.
- Foreign + current child + membership nằm trong cùng outer transaction của root.
- Sau commit đọc lại đúng UID trong membership; không query thêm mọi row cùng FlightId.
- Nếu endpoint không có snapshot đầy đủ:
  - ưu tiên membership authoritative đã cache;
  - chỉ máy legacy chưa từng có membership mới fallback theo FlightId;
  - fallback luôn báo `collectionComplete=false`.

### 6.9 `DataHelper`: chống race đọc

- Thêm global atomic `refuelReadGeneration`.
- Thêm hai map theo UID:
  - `latestRefuelReads`: read mới nhất đã bắt đầu;
  - `latestAppliedRefuelReads`: read mới nhất đã commit.
- `beginRefuelRead`, `canApplyRefuelRead`, `markRefuelReadApplied` đóng thứ tự request/commit.
- GET theo server Id alias token sang UID thật sau response.
- `refreshCachedOthers` cấp generation riêng cho từng child.
- `loadRefuelForPreview()` dùng một root GET và trả `PreviewLoadResult` có cờ `superseded`.
- Root cũ và collection cũ bị bỏ nếu bất kỳ shared child đã có read/commit mới hơn.

### 6.10 `DataHelper`: ownership và write guard

- Thêm phân loại ba trạng thái `CURRENT/FOREIGN/UNKNOWN` từ TruckNo + TruckId.
- Nếu hai định danh cùng có nhưng mâu thuẫn: `UNKNOWN`, fail-closed.
- Với row đã lưu, so cả cột Room và payload JSON; hai nguồn mâu thuẫn cũng fail-closed.
- `classifyWriteAccess()` kiểm cả snapshot vào và row mới nhất trong Room.
- Direct POST kiểm ownership ở entry và kiểm lại trong critical section trước write.
- Background queue:
  - DAO loại `remoteReplica`;
  - DataHelper vẫn kiểm `isWritableCurrentTruckItem` trước POST;
  - re-read row mới nhất trước HTTP;
  - re-read và recheck ownership sau HTTP.
- `patchRefuel()` chặn replica/foreign/unknown trước khi chạy callback patch.
- `postRefuels()` bỏ qua foreign replica bình thường, nhưng trả lỗi và anomaly cho ownership invalid.
- Stale snapshot từng là current không được tự gỡ marker sau khi server reassignment thành foreign.

### 6.11 `DataHelper` + `RefuelSyncGuard`: xác minh POST response

- `validatePostResponseIdentity()` kiểm UID, server Id và ownership của response trước khi nhận ACK.
- `HttpClient` không còn gán Id response vào request.
- Direct/background POST dùng identity request đã chốt trước HTTP để lookup row sau HTTP.
- Nếu row đã tiến version hoặc đổi role trong lúc request bay:
  - response cũ không được đổi identity;
  - không được clear dirty;
  - không được merge metadata vào replica;
  - row mới hơn tiếp tục nằm trong queue nếu cần.
- `RefuelSyncGuard.describeAck()` cũng fail với UID/ID mismatch như lớp defense-in-depth.

### 6.12 `DataHelper`: cursor modified pull

- Bỏ dùng `MAX(DateUpdated)` trong Room làm cursor.
- Cursor mới lưu SharedPreferences theo key:

```text
REFUEL_MODIFIED_CURSOR_MS_V1_<TruckId>_<TruckNo>
```

- Fresh install/upgrade chưa có key: gửi `lastModified=null` để full pull.
- Mỗi query lùi 5 phút so với committed cursor.
- Cursor chỉ lấy từ `DateUpdated` trong chính response modified.
- Không advance nếu:
  - batch/null item/identity lỗi;
  - bất kỳ root/child/tombstone transaction lỗi;
  - bất kỳ item thiếu DateUpdated;
  - timestamp tương lai quá 5 phút;
  - SharedPreferences synchronous `commit()` thất bại.
- Chỉ tăng cursor, không lùi cursor.
- Empty response hiện không tự tạo high-watermark mới.

### 6.13 `DataHelper`: modified batch, Flight và tombstone

- Preflight response trước mọi write: null, UID, server Id, duplicate, tombstone identity.
- Mỗi non-deleted root xử lý trong transaction gồm refuel + `Others` + `Flight`.
- `APPLIED`: được ghi payload đi kèm.
- `KEPT_NEWER`: không ghi `Flight`/membership từ payload cũ.
- `FAILED`: transaction rollback, batch false, cursor giữ nguyên.
- Tombstone:
  - resolve theo UID và Id, cấm hai identity trỏ hai row;
  - dùng generation guard;
  - stale guard theo revision/date;
  - không xoá row local dirty;
  - xoá đúng `localId`, không xoá theo server Id mơ hồ.
- Batch là atomic theo từng root, không atomic toàn response; xem mục rủi ro cần review.

### 6.14 `DataHelper`: migration maintenance cho replica legacy

- `quarantineLegacyForeignRefuels()` đánh dấu row foreign cũ thành replica, clear dirty/error và
  giữ nguyên payload/cột nghiệp vụ.
- Chạy lại cả khi version maintenance đã được đánh dấu, vì lần đăng nhập đầu có thể chưa có
  cấu hình TruckId/TruckNo để phân loại; lần sau vẫn phải có cơ hội quarantine.
- Row ownership UNKNOWN không bị tự đoán thành foreign/current.

### 6.15 `RefuelPreviewActivity.java`: load và tính đầy đủ

- Thay hai call `refreshOthers + getRefuelItem` bằng đúng một `loadRefuelForPreview`.
- Thêm Activity `loadGeneration`, bỏ callback cũ hoặc `superseded`.
- `warnStaleOthers()` phân biệt incomplete membership và child refresh lỗi.
- `blockIncompleteOthersForCombinedDocument()` chặn phiếu gộp nếu collection chưa được server
  xác nhận đầy đủ.
- `blockInvalidSelectedDocumentItems()` bắt buộc:
  - current header có AirlineModel;
  - mọi dòng chọn là `DONE`;
  - có start/end;
  - `realAmount > 0`;
  - cùng AirlineId với header.
- `validate()` luôn kiểm đúng `printItems` checkbox, không kiểm nhầm dòng `refuelData` đang xem.

### 6.16 `RefuelPreviewActivity.java`: read-only foreign trên UI

- `isCurrentTruckItem()` fail-closed bằng TruckNo/TruckId; bỏ bypass `BuildConfig.FHS`.
- Không enrich Product/Airline vào object foreign trong RAM.
- Spinner hãng:
  - không callback sửa foreign;
  - suppress callback bind đầu tiên nếu vẫn đúng AirlineId.
- Chỉ current được mutate bởi `setAirline`, `setAll`, `setInvoiceForm`, `updateAllReview`.
- Sửa tính thuế theo chính `item.isInternational()`, không dùng global selected row.
- Chặn foreign tại các entry/sink:
  - `doRefuel`;
  - `showSplit`;
  - `openCheckForm`;
  - `updatePrice`/`updateProduct`;
  - chọn hãng;
  - nhập/sửa hoàn trả;
  - sửa thời gian;
  - chọn user;
  - `createNewItem` clone;
  - `updateBinding(false)`.
- `updateBinding(true)` và `updateAllReview()` lọc danh sách current trước khi gọi tầng ghi.
- LeaveTime vẫn được phép sau khi phát hành chứng từ, nhưng chỉ với current row.
- `patchAllPrintItems`, receipt/invoice callback và print status chỉ sửa current rows.

### 6.17 `RefuelPreviewActivity.java`: nguồn header chứng từ

- `currentTruckPrintTarget()` chọn current root nếu có, fallback current item đầu tiên được chọn.
- `documentItemsCurrentFirst()` copy list và đưa current header lên đầu, không mutate `printItems`.
- Cấm chứng từ chỉ gồm foreign replica.
- Invoice dialog lấy Airline/driver/operator/template từ current header.
- Giá/thuế patch sau in lấy từ current header, không lấy selected foreign row.
- Print success/error cập nhật current target.
- Reprint receipt resolve receipt UID từ đúng tập đã in, ưu tiên current target.
- Metadata receipt/invoice patch bằng `DataHelper.patchRefuel()` trên row mới nhất, không POST lại
  snapshot UI cũ.

### 6.18 `InvoiceModel.java`

- `fromRefuel()` bắt buộc explicit header, không còn tự thay header bằng item kết thúc muộn nhất.
- Copy danh sách trước filter/sort; không mutate list caller.
- Bỏ null hoặc item sản lượng không dương; reject nếu không còn dòng.
- Sort EndTime null-safe và reject dòng thiếu start/end bằng business error.
- Header customer/airline/price/tax/currency/type từ explicit current row.
- String/null handling an toàn hơn; AirlineModel header bắt buộc.
- TechLog chỉ lấy `WeightNote` current header; parse lỗi chỉ log.
- Start/end vẫn là MIN/MAX toàn bộ document items; invoice date vẫn là max EndTime.

### 6.19 `ReceiptModel.java`

- Reject list null/rỗng bằng `InvalidRefuelTimeException`.
- AirlineModel header bắt buộc, string trim null-safe.
- TechLog chỉ lấy từ header; không bị foreign row cuối vòng lặp ghi đè.
- Không còn gọi `reconcileVolume()` trên source object vì hàm đó mutate replica.
- Tính derived volume và chỉ set vào `ReceiptItemModel` dòng in; source replica giữ nguyên.
- Hiện ReceiptModel vẫn coi phần tử đầu là header; Activity đảm bảo current-first.

### 6.20 `refuel_preview_item.xml`

- Dòng `PROCESSING` hiển thị StartTime nếu có.
- Dòng `DONE` hiển thị StartTime/EndTime tương ứng nếu non-null.
- Thêm null guard để không format thời gian thiếu.

### 6.21 Các test cũ được cập nhật

Bốn test cũ chỉ bổ sung setting/fixture TruckId/TruckNo để đường ghi được phân loại rõ là CURRENT:

- `RefuelConfirmFieldsPersistTest`
- `RefuelRebaseTest`
- `RefuelSyncIntegrationTest`
- `UnknownKeyPreservationTest`

Không thay đổi assertion nghiệp vụ chính của các test này.

## 7. Test mới và phạm vi bao phủ

### 7.1 `AppDatabaseMigrationTest` — 1 test

- Chạy DDL migration 13→14 trên bảng legacy tối giản.
- Xác nhận hai cột mới tồn tại, default `remoteReplica=0`, membership null và row cũ còn nguyên.

### 7.2 `RefuelRemoteReplicaIntegrationTest` — 36 test

Các hành vi được cover (một test có thể cover nhiều hành vi):

1. Parser giữ raw JSON từng child, phân biệt `Others` vắng/empty/malformed.
2. Giờ vắng không biến thành thời điểm hiện tại.
3. POST loại nested `Others` và không mutate danh sách đang hiển thị.
4. Snapshot đầy đủ phát hiện xe khác mới chưa có trong Room và giữ unknown server keys.
5. Preview chỉ GET root một lần.
6. Dùng chính xác membership/thứ tự server, không cộng row stale cùng flight.
7. Preview response cũ không hạ membership mới.
8. `Others=[]` không fallback cache stale.
9. Root ownership mâu thuẫn không ghi root hoặc child.
10. Offline dùng authoritative cached membership, không hồi sinh stale row.
11. Legacy fallback chỉ refresh known UID và luôn incomplete.
12. Mixed Preview batch chỉ ghi current; foreign raw JSON giữ nguyên.
13. Replica không vào queue dù bị gắn dirty bởi code legacy.
14. Cột Room và JSON ownership mâu thuẫn fail-closed.
15. Direct POST/PATCH không mutate replica.
16. Stale current snapshot không clear replica sau reassignment.
17. Batch stale sau reassignment báo failure.
18. Direct POST in-flight không chạm replica sau reassignment.
19. Direct ACK khác UID không đổi identity và row vẫn dirty.
20. Background POST in-flight không chạm replica sau reassignment.
21. Background ACK khác UID giữ row trong queue.
22. UNKNOWN fail-closed cho POST/PATCH.
23. Replica batch giữ member mới hơn trong khi vẫn áp member hợp lệ khác.
24. Modified pull bắt đầu trước detail GET không ghi đè detail mới, kể cả revision 0.
25. Root cũ không hạ shared child mới hơn từ root khác.
26. Tombstone cũ không xoá detail mới.
27. Server legacy revision 0 dùng DateUpdated chống stale.
28. Pull raw JSON lỗi không tạo Refuel/Flight rác.
29. Child lỗi rollback root + child + membership.
30. Server localId không `REPLACE` row tablet khác.
31. Duplicate positive server Id trong batch bị reject trước write.
32. Cursor độc lập `MAX(DateUpdated)` Room và có overlap 5 phút.
33. UID mới dùng server Id của UID khác bị reject.
34. Foreign legacy thiếu giờ được quarantine mà không bịa thời gian.

Bổ sung sau vòng review của Claude:

- Current root cũ tuần tự không được làm tụt authoritative `Others` membership.
- Modified pull current root cũ không được ghi đè membership hoặc `Flight` mới.
- Tombstone có UID đúng nhưng server Id sai bị từ chối.
- Revision dương thắng row legacy revision 0 dù `DateUpdated` cũ hơn.
- Tombstone revision dương xoá được row legacy revision 0 dù `DateUpdated` cũ hơn.

### 7.3 `DocumentReplicaIntegrityTest` — 5 test

- Invoice dùng explicit current header dù foreign đứng trước/kết thúc muộn hơn.
- Invoice không reorder/mutate input; thời gian/date vẫn tính đúng toàn dòng.
- Missing measured time báo domain error, không NPE.
- Không có dòng sản lượng dương bị chặn trước khi index list.
- Receipt derived volume không mutate foreign source.
- Foreign WeightNote không override current TechLog trên invoice/receipt.

## 8. Kết quả kiểm tra đã chạy

Lệnh cuối cùng:

```bash
zsh ./gradlew :app:testDebugUnitTest --rerun-tasks :app:compileReleaseJavaWithJavac
```

Kết quả:

```text
BUILD SUCCESSFUL
438 tests
0 failures
0 errors
Release Java compile: PASS
git diff --check: PASS
rg BLOCK_POST_REMOTE_REPLICA app/src/main app/src/test: không còn kết quả
```

Cảnh báo javac còn lại là deprecated API và Room field/setter mismatch đã có sẵn; không có lỗi build.

## 9. Thay đổi logging

Đã xoá hoàn toàn log spam:

```java
Logger.appendLog("SYNC", "BLOCK_POST_REMOTE_REPLICA uid=" + item.getUniqueId());
```

Replica bị skip trong batch/queue bình thường không log từng item. Chỉ log khi có lỗi thật, ví dụ:

- `OTHER_SNAPSHOT_FAILED`
- `REMOTE_REPLICA_FAILED`
- `REFUEL_WRITE_REJECTED`
- `REFUEL_WRITE_OWNERSHIP_INVALID`
- `REFUEL_POST_RESPONSE_REJECTED`
- `REFUEL_POST_RESPONSE_DROPPED ... ROLE_CHANGED`
- `PREVIEW_WRITE_REJECTED`
- cursor/batch/identity failure

Claude cần kiểm tra các lỗi lặp có còn khả năng gây volume log quá lớn hay cần rate-limit thêm.

## 10. Các điểm rủi ro/trade-off cần Claude phản biện kỹ

### Đã xử lý sau review 0A — Current root cũ không còn được đưa `Others`/`Flight` cũ vào

`applyRemoteToLocal()` nay trả `APPLIED/KEPT_NEWER/FAILED`. Khi current root bị nhận diện cũ theo
ClientSeq, ServerRevision hoặc DateUpdated, row vẫn có thể nhận các server-owned field an toàn nhưng
caller không được dùng payload đó để thay membership/Flight. Cả Preview/detail GET và modified pull
đều đã có gate này; test bao phủ ca stale tuần tự, không chỉ race request đồng thời.

### Đã xử lý sau review 0B — Tombstone phải khớp cả UID và Id được cung cấp

Khi tombstone mang cả UID và Id, code kiểm tra Id của row chọn theo UID phải khớp Id tombstone, kể cả
khi lookup theo Id không tìm thấy row. Khe sau đã được đóng và có regression test:

```text
tombstone UID -> tìm thấy row X
tombstone Id  -> không tìm thấy row nào
tombstone Id  != server Id đang lưu của X
```

Kết quả hiện tại là từ chối delete, giữ nguyên row và chỉ log `DELETE_ID_MISMATCH` vì đây là lỗi thật.

### Đã xử lý sau review 0C — Revision dương thắng legacy revision `0`

Contract đã chốt: incoming revision dương và lớn hơn stored revision luôn có thẩm quyền, bao gồm chuyển
từ legacy 0 lên 1. Đã áp dụng nhất quán cho full replacement và tombstone, kèm hai regression test có
`DateUpdated` incoming cũ hơn.

### P0/P1 candidate 0D — Nhiệt độ/tỷ trọng trên Preview có thể phải nhập lại sau conflict

Luồng hiện tại:

```text
người dùng sửa Density hoặc ManualTemperature
  -> mutate object `refuelData` trong RAM
  -> `updateBinding(false)`
  -> `DataHelper.postRefuel(refuelData, true)` với toàn snapshot + baseline cũ
  -> `decideSave()` trả conflict nếu ClientSeq/baseline đã dịch chuyển
  -> Room không nhận giá trị vừa nhập
```

Màn Confirm/End đã có `ConfirmFieldsPatch`/`EndFieldsPatch`, và RefuelDetail có đường
`rebaseScreenOnStored()`. Riêng `RefuelPreviewActivity.updateBinding(false)` hiện chỉ báo save failed;
không patch riêng field, không rebase baseline và không retry. Vì vậy báo cáo “nhiệt độ/tỷ trọng phải
nhập đi nhập lại mới nhận” là phù hợp với code, đặc biệt khi background sync/ACK vừa làm ClientSeq hoặc
baseline của row dịch chuyển.

Đây là dữ liệu **xe hiện tại**, tách biệt với lỗi nhận replica xe khác. Claude cần đề nghị một trong hai
hướng và test race tương ứng:

1. Thêm scoped patch cho các field Preview được phép sửa, đọc row mới nhất dưới khoá và chỉ đắp field
   người dùng thực sự đổi; hoặc
2. Khi conflict chỉ do version dịch chuyển nhưng business fingerprint nền không đổi, rebase snapshot và
   retry đúng một lần, vẫn fail-closed nếu payload nghiệp vụ thật sự đã đổi.

Không nên retry nguyên snapshot vô điều kiện vì có thể ghi đè thay đổi mới của thiết bị/server.

### P0/P1 candidate A — Atomic theo root, không atomic toàn modified response

Các root hợp lệ trước một root lỗi có thể đã commit, nhưng cursor không tiến. Lượt sau response được
tải lại và áp idempotent. Đây là lựa chọn ưu tiên availability, nhưng cần xác minh mọi side effect
(`Flight`, membership, notifications) thật sự idempotent.

### P0/P1 candidate B — Equal timestamp + revision 0

Stale guard dùng `DateUpdated.before()`. Hai payload khác nhau có cùng DateUpdated và revision 0 có thể
phụ thuộc thứ tự response. Generation chống concurrent response trong cùng process, nhưng Claude cần
đánh giá retry/process restart và server timestamp resolution.

### P1 candidate C — Backend chưa có opaque high-watermark

Client tự dùng max `DateUpdated` + overlap 5 phút. Nếu server có late event cũ hơn cursor quá 5 phút,
client vẫn có thể bỏ lỡ. Giải pháp chắc chắn dài hạn là server trả `nextCursor`/sequence opaque.

### P1 candidate D — Empty response và timestamp thiếu/tương lai

- Empty response không tiến cursor, gây tải lặp nhưng không mất dữ liệu.
- Một item thiếu DateUpdated giữ cursor cho cả batch.
- Clock server lệch tương lai hơn 5 phút giữ cursor.

Cần xác nhận tải lặp có chấp nhận được và chính sách clock skew phù hợp hiện trường.

### P1 candidate E — Startup migration/quarantine

Migration thêm `remoteReplica DEFAULT 0`; an toàn phụ thuộc `quarantineLegacyForeignRefuels()` chạy
trước background sync khi setting xe đã sẵn sàng. Hiện quarantine được gọi lại các lần đăng nhập,
nhưng chưa có end-to-end startup test chứng minh thứ tự thực tế.

Ngoài ra:

- `exportSchema=false`, migration test chưa validate schema v13 thật qua `MigrationTestHelper`.
- Có `MIGRATION_8_14` trong code nhưng không đăng ký và không nên đăng ký nguyên trạng nếu chưa đủ schema.
- Thiết bị DB thấp hơn chuỗi migration hỗ trợ có thể đi qua `fallbackToDestructiveMigration()`.
- Cần xác nhận DB version thật của bản 102/103.

### P1 candidate F — UID không có unique index

Incoming duplicate/collision đã bị chặn, nhưng DB legacy có sẵn duplicate UID chưa có test. Query update
membership yêu cầu đúng một row; duplicate có thể làm update count khác 1 và rollback snapshot. Claude
cần quyết định có migration cleanup/unique index an toàn hay chỉ log/quarantine.

### P1 candidate G — Membership cache hỏng

`remoteOthersUidsJson` JSON hỏng/trùng UID được đọc thành null rồi fallback FlightId, có thể hồi sinh stale
row. Chưa có test corruption này. Nên cân nhắc fail-closed/incomplete thay vì fallback sau khi cột non-null
nhưng invalid.

### P1 candidate H — Receipt header vẫn positional

`ReceiptModel.createReceipt()` vẫn lấy phần tử đầu làm header. Activity đưa current lên đầu, nhưng caller
mới hoặc caller khác có thể tái lỗi. Cân nhắc đổi API giống Invoice: nhận explicit current header.

### P1 candidate I — Mutable `printItems` qua callback async

`printItems` là field mutable. Callback máy in/onActivityResult có thể chạy sau khi selection/load thay đổi,
khi đó metadata có nguy cơ patch sang nhóm current khác. Cân nhắc capture immutable UID list + header UID
tại lúc launch print.

### P1/P2 candidate J — Invoice completeness có thể đang chặn quá mức

Receipt phân biệt single và combined. `openPrintInvoice()`/legacy `preview()` hiện luôn yêu cầu Others
complete, kể cả chỉ chọn đúng một current item. Xác nhận đây là nghiệp vụ mong muốn hay cần áp logic
`selectedCount > 1` giống Receipt.

### P2 candidate K — Airline spinner initial callback

Callback đầu chỉ suppress khi selected AirlineId bằng current AirlineId. Nếu master list không chứa hãng
hiện tại, spinner default sang hãng khác và có thể bị coi là thao tác người dùng. Cân nhắc suppress initial
callback vô điều kiện hoặc yêu cầu touch/user intent.

### P2 candidate L — Các form phụ khi đang chọn foreign

`showReview()`, `openNew()` BM2505 và `openBM7501()` chưa có ownership guard trực tiếp. Chúng không ghi
RefuelItem qua đường chính, nhưng Claude cần xác nhận nghiệp vụ: xe này có được tạo/xem các form đó dựa
trên flight/refuel foreign hay phải read-only hoàn toàn.

### P2 candidate M — Clone current sau khi đã phát hành

`createNewItem()` dùng lock chung, nên current row invoice/exported cũng không được clone thành “nạp thêm”.
Xác nhận có luồng nghiệp vụ cần nạp thêm sau khi phát hành hay không.

### P2 candidate N — Ownership alias/renumber

TruckId đúng nhưng TruckNo lệch, hoặc ngược lại, bị fail-closed. Đây là lựa chọn an toàn, nhưng cần xác nhận
server không giữ alias/số xe lịch sử cho chính xe hiện tại.

### P2 candidate O — Nested Room transaction

`reconcileRefuelItem()` mở outer transaction rồi gọi repository method cũng dùng `db.runInTransaction()`.
Room thường hỗ trợ transaction lồng trên cùng thread, nhưng Claude cần xác minh version Room đang dùng và
đảm bảo exception ở inner call rollback đúng outer transaction.

### P2 candidate P — Generation chỉ nằm trong memory

Map started/applied reset khi process restart. DB stale guards và cursor phải đủ bảo vệ sau restart. Hãy rà
case request cũ không thể sống qua process death, và case response/cache persisted từ proxy/server.

Map generation hiện cũng không evict UID trong suốt vòng đời process. Rủi ro bộ nhớ có thể nhỏ nhưng
tăng theo số phiếu; cần xác nhận giới hạn thực tế hoặc cơ chế cleanup.

### P2 candidate P2 — POST response legacy thiếu ownership

Response POST đúng UID/Id nhưng thiếu cả TruckNo và TruckId sẽ bị phân loại UNKNOWN, giữ row dirty và
retry. Đây là fail-closed có chủ ý, nhưng cần xác nhận schema response thật của mọi backend/version.

### P2 candidate P3 — Full-replace API tin caller đã chứng minh foreign

`replaceRemoteRefuelSnapshotsDetailed()` tự nó không kiểm row chắc chắn FOREIGN; một caller mới dùng nhầm
có thể biến current row thành replica. Cân nhắc thu hẹp visibility hoặc yêu cầu ownership proof rõ ràng.

### P1/P2 candidate P4 — Reassignment có thể bỏ local edit chưa POST

Khi server chuyển một current row thành foreign, full-replace authoritative có chủ ý clear local dirty và
thay payload. Cần xác nhận nghiệp vụ: nếu xe cũ vừa đo dữ liệu thiết bị nhưng chưa POST thì có chấp nhận
bỏ edit đó, hay phải quarantine/conflict/export diagnostic trước khi nhận reassignment.

### P2 candidate P5 — Raw JSON toàn vẹn theo nghĩa semantic, không phải raw bytes

Child raw JSON đi qua `JSONObject.toString()`, nên giữ field/value và unknown keys nhưng có thể đổi
whitespace/format/order. Nếu backend dùng chữ ký/hash trên raw bytes thì chưa đáp ứng; nếu “toàn vẹn” là
semantic data integrity thì phù hợp.

### P2 candidate P6 — Root `jsonData` còn nested `Others`

Membership đã tách riêng và POST đã strip `Others`, nhưng raw JSON root vẫn có nested collection tại thời
điểm response. Hãy tìm mọi caller đọc thẳng `toRefuelItemData()` mà không qua `attachResolvedOthers()`;
caller đó có thể nhìn nested `Others` stale trong root JSON.

### P2 candidate P7 — Child thiếu Flight identity vẫn được tin

Root-child chỉ bị từ chối khi có đủ FlightUniqueId hoặc FlightId để so và chúng lệch nhau. Child thiếu cả
hai có thể được nhận vì membership root được tin authoritative. Cần xác nhận contract server có bắt buộc
mọi child mang Flight identity hay không.

### P2 candidate Q — Test UI và migration còn thiếu

- Chưa có instrumentation/Robolectric Activity test click foreign rồi sửa/tra nạp/clone/in.
- Chưa có test race: background ACK/pull dịch baseline ngay trước khi lưu Density/Temperature trên Preview;
  cần xác nhận một lần nhập hợp lệ hoặc được commit, hoặc báo conflict rõ mà không bắt nhập lại âm thầm.
- Chưa có full Room migration test từ schema v13 thật.
- Chưa có end-to-end cursor test cho đổi xe, app restart, batch failure, timestamp thiếu/tương lai.
- Chưa test membership vẫn tồn tại sau local save/ACK root current.
- Chưa test DB legacy duplicate UID hoặc corrupted membership JSON.

### P3 — Comment không còn đúng hành vi

Comment trên `warnStaleOthers()` nói “chỉ cảnh báo, chưa chặn gì”, trong khi combined document hiện bị
chặn bởi hàm khác. Nên cập nhật để người bảo trì không hiểu sai.

## 11. Checklist acceptance đề nghị

Không chấp nhận canary nếu còn câu trả lời “không chắc” cho bất kỳ mục nào sau:

- [ ] Foreign row không thể lọt vào POST/PATCH ở direct, batch, queue hoặc callback async.
- [ ] Reassignment current→foreign trong lúc POST bay không thể clear dirty/mutate replica.
- [ ] Root/child/membership không bao giờ tồn tại ở trạng thái commit một phần.
- [ ] Shared child không bị root cũ hạ dữ liệu.
- [ ] Tombstone cũ không xoá detail mới hoặc row local dirty.
- [ ] Cursor không phụ thuộc Room DateUpdated và không tiến khi batch chưa đủ.
- [ ] `Others=[]` không hồi sinh stale cache; `Others` absent không giả là complete.
- [ ] Foreign raw JSON/unknown keys/time null được giữ đúng.
- [ ] Current local dirty/device fields không bị full replace.
- [ ] Phiếu gộp bắt buộc có current header và complete membership.
- [ ] Header/price/tax/customer/TechLog không bao giờ lấy từ foreign.
- [ ] Document builder không mutate replica hoặc source list.
- [ ] Migration thực tế từ DB của bản 102/103 không mất dữ liệu.
- [ ] Logging normal replica skip không còn spam; error logging vẫn đủ điều tra.

## 12. Lệnh xác minh đề nghị Claude chạy lại

```bash
zsh ./gradlew :app:testDebugUnitTest --rerun-tasks :app:compileReleaseJavaWithJavac
git diff --check
rg -n "BLOCK_POST_REMOTE_REPLICA" app/src/main app/src/test
rg -n "postRefuel|postRefuels|patchRefuel" app/src/main/java/com/megatech/fms
rg -n "removeRemoteDeletedRefuel|replaceRemoteRefuelSnapshots|remoteReplica" app/src/main app/src/test
```

Nếu có thiết bị/canary DB thật, nên test thêm:

1. Upgrade trực tiếp từ bản 102 và 103, không uninstall.
2. Một chuyến có 3 xe; xe thứ ba xuất hiện sau khi Preview đã từng cache 2 xe.
3. `Others=[]` sau khi trước đó có child.
4. Mất mạng giữa root/child persist và mở lại app.
5. Bấm CẬP NHẬT liên tục với response đảo thứ tự.
6. Detail GET mới chạy song song modified pull cũ revision 0.
7. Reassignment current→foreign trong lúc direct/background POST bay.
8. Tombstone cũ chạy song song detail mới.
9. In gộp khi một foreign row PROCESSING/thiếu time/amount.
10. In xong rồi thay selection trước callback để kiểm tra immutable print target.

## 13. Kết quả review mong muốn

Claude vui lòng kết luận một trong ba mức:

- **GO:** không còn P0/P1; chỉ rõ test/canary bắt buộc.
- **GO WITH FIXES:** liệt kê patch cần làm trước canary và patch có thể để sau.
- **NO-GO:** chỉ rõ invariant bị phá, chuỗi tái hiện và dữ liệu có thể mất/sai.

Không chấp nhận kết luận chung chung kiểu “code có vẻ ổn”. Trọng tâm là chứng minh hoặc bác bỏ các
invariant dữ liệu ở mục 4 bằng đường chạy cụ thể trong code.

## 14. Inventory chính xác của working diff

```text
+1    -1    app/build.gradle
+376  -107  app/src/main/java/com/megatech/fms/RefuelPreviewActivity.java
+14   -2    app/src/main/java/com/megatech/fms/data/AppDatabase.java
+256  -0    app/src/main/java/com/megatech/fms/data/DataRepository.java
+9    -1    app/src/main/java/com/megatech/fms/data/dao/RefuelItemDao.java
+50   -0    app/src/main/java/com/megatech/fms/data/entity/RefuelItem.java
+1223 -139  app/src/main/java/com/megatech/fms/helpers/DataHelper.java
+82   -27   app/src/main/java/com/megatech/fms/helpers/HttpClient.java
+11   -0    app/src/main/java/com/megatech/fms/helpers/RefuelSyncGuard.java
+59   -19   app/src/main/java/com/megatech/fms/model/InvoiceModel.java
+39   -18   app/src/main/java/com/megatech/fms/model/ReceiptModel.java
+22   -0    app/src/main/java/com/megatech/fms/model/RefuelItemData.java
+1    -1    app/src/main/res/layout/refuel_preview_item.xml
+8    -0    app/src/test/java/com/megatech/fms/helpers/RefuelConfirmFieldsPersistTest.java
+6    -0    app/src/test/java/com/megatech/fms/helpers/RefuelRebaseTest.java
+5    -0    app/src/test/java/com/megatech/fms/helpers/RefuelSyncIntegrationTest.java
+7    -0    app/src/test/java/com/megatech/fms/helpers/UnknownKeyPreservationTest.java
```

File test mới chưa track:

```text
77 dòng    app/src/test/java/com/megatech/fms/data/AppDatabaseMigrationTest.java
1045 dòng  app/src/test/java/com/megatech/fms/helpers/RefuelRemoteReplicaIntegrationTest.java
181 dòng   app/src/test/java/com/megatech/fms/model/DocumentReplicaIntegrityTest.java
```
