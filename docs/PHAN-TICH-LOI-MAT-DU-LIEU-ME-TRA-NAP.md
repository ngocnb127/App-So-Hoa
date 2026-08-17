# Mất dữ liệu mẻ tra nạp — phân tích, logic và cách sửa

**Ngày:** 17-08-2026 · **Nhánh:** `fix/datahelper-offline-sync` · **Thiết bị:** SM-X306B (R52XA0AYEQZ), `com.megatech.fms`

Tài liệu này viết để review độc lập. Phần 1–3 là sự kiện và bằng chứng, phần 4 là cơ chế,
phần 5 là bản sửa, phần 6 là những chỗ **chưa chắc chắn** — mong được soi kỹ nhất ở đó.

---

## 1. Triệu chứng người dùng báo

1. Trong quá trình đồng bộ, dữ liệu không được post về server.
2. Sau khi bấm **End**, dữ liệu mẻ không được ghi nhận.
3. Nhập nhiệt độ, tỉ trọng ở màn hình xác nhận — không ghi nhận.
4. Bấm **Xác nhận** cũng không ghi nhận, phiếu **về 0 GL**, màn hình tự chuyển ra danh sách.
5. Khẳng định: **không có ai can thiệp/sửa dữ liệu** trong lúc test.

---

## 2. Bằng chứng từ log thiết bị

Lấy bằng `adb logcat -b all -s DTH SYNC RFW RFC APPLOG REFUEL_ANOMALY POST_EXCHANGE`.
(`Logger.appendLog` ghi cả logcat lẫn `fms.log`; file bị cắt/upload liên tục nên logcat là
nguồn đầy đủ hơn.)

### 2.1 Phiếu 2111601 — mẻ bị mất trắng

```
09:24:20.565 POST_EXCHANGE BACKGROUND_SYNC uid=be8b7194 id=2111601 localId=2
             | req status=NONE seq=1 baseSeq=1 baseRev=1 amount=0 start=0 end=0
             | res status=NONE seq=1 rev=2 amount=0 start=0 end=0 | result=ACK
09:24:21.015 SYNC REMOTE_PULL KEEP_LOCAL_VALUES uid=be8b7194
             reason=LOCAL_MODIFIED localSeq=2 localRev=2 remoteRev=2
09:24:21.444 REFUEL_ANOMALY VERSION_CONFLICT reason=CONFLICT_PAYLOAD_CHANGED source=LOCAL_SAVE
             storedSeq=2 storedRev=2 baseSeq=2 baseRev=1
             storedAmount=0 storedEnd=0 incomingAmount=0   incomingEnd=817751
09:24:22.370 ... incomingAmount=12  incomingEnd=817758
09:24:23.337 ... incomingAmount=37  incomingEnd=817761
   (lặp lại mỗi giây, đúng nhịp timer đọc đồng hồ LCR)
09:24:38.629 ... incomingAmount=399 incomingEnd=817856
```

Đọc ra:

- `baseRev=1` mà `storedRev=2` ⇒ row **đã bị ghi lại** sau khi màn hình đọc nó.
- Mọi lần lưu trong suốt mẻ đều bị chặn ⇒ `storedAmount` đứng yên ở **0** từ đầu đến cuối.
- Không có lượt lưu nào thành công ⇒ POST nền chỉ có `amount=0` để gửi ⇒ **đúng triệu chứng 1**.

### 2.2 Tiếp theo, màn hình xác nhận

```
09:24:44.856 RFC Cập nhật nhiệt độ    Old value: 0,00   New value: 30
09:24:47.637 RFC Cập nhật tỷ trọng    Old value: 0,0000 New value: 0,789
09:24:52.071 RFC Cập nhật số phiếu hóa nghiệm          New value: D
09:24:54.535 RFC StartNumber: 817457 EndNumber: 817856 RealAmount: 399
                 Temperature: 30.0 Density: 0.7890
09:24:54.590 REFUEL_ANOMALY VERSION_CONFLICT CONFLICT_PAYLOAD_CHANGED source=LOCAL_SAVE
                 storedSeq=2 storedRev=3 baseSeq=2 baseRev=1
                 storedAmount=0 storedEnd=0 incomingAmount=399 incomingEnd=817856
09:24:54.681 APPLOG START RefuelPreviewActivity      ← vẫn đi tiếp như không có gì
09:24:55.231 APPLOG END   RefuelDetailConfirmActivity
```

⇒ **triệu chứng 2, 3, 4** cùng một nguyên nhân.

### 2.3 Phiếu 2111600 — cùng dạng, xảy ra trước đó

```
09:12:59.590 BACKGROUND_SYNC req seq=19 baseRev=1 | res rev=2 | ACK
09:12:59.628 DTH REBASED_ON_SERVER_METADATA oldBaseRev=1 storedRev=2 seq=19
09:12:59.944 SYNC REMOTE_PULL SERVER_FIELDS flightStatus(ASSIGNED->REFUELING)
09:12:59.945 SYNC REMOTE_PULL KEEP_LOCAL_VALUES reason=LOCAL_MODIFIED localSeq=20 localRev=2
09:13:00.541 VERSION_CONFLICT CONFLICT_PAYLOAD_CHANGED storedSeq=20 storedRev=2 baseSeq=20 baseRev=2
             storedAmount=408 storedEnd=0 incomingAmount=413 incomingEnd=817665
   (lặp lại 09:13:01 … 09:13:05, rồi 09:13:23)
```

Ở đây `baseSeq == storedSeq` và `baseRev == storedRev` mà **vẫn** `CONFLICT_PAYLOAD_CHANGED`
⇒ chặn hoàn toàn do vân tay payload, không liên quan version.

---

## 3. Cơ chế đang có trong code (để review hiểu bối cảnh)

Cơ chế này được thêm ở commit `7219238` sau sự cố mẻ 1110 GL bị bản cũ 862 GL ghi đè.

| Khái niệm | Vị trí | Ý nghĩa |
|---|---|---|
| `ClientSeq` | cột `RefuelItem` | tăng 1 mỗi lần người dùng thay đổi dữ liệu thật |
| `ServerRevision` | cột `RefuelItem` | phiên bản do server cấp |
| baseline | `RefuelItemData.base*` (transient) | ảnh chụp phiên bản + vân tay lúc màn hình ĐỌC row |
| vân tay | `RefuelSyncGuard.businessFingerprintOfJson` | SHA-256 của JSON đã canonical hoá |
| precondition | `RefuelSyncGuard.decideSave` | so baseline với row hiện tại, quyết định cho ghi hay chặn |

`decideSave` trả `CONFLICT_PAYLOAD_CHANGED` khi vân tay row hiện tại khác vân tay baseline —
tức "có ai đó sửa payload sau khi màn hình mở". Khi bị chặn, `DataHelper.postRefuel` trả về
**row cũ trong Room** và **không** rebase baseline của caller (cố ý: rebase thì chính snapshot
vừa bị từ chối sẽ vượt guard ở lần sau).

Baseline được đóng dấu ở `RefuelItem.toRefuelItemData()` và truyền qua Intent bằng
`RefuelIntent` (vì nó `transient`, không nằm trong JSON).

---

## 4. Nguyên nhân

### 4.1 Vân tay tính cả nhóm trường KHÔNG thuộc quyền của app

`SERVER_OWNED_KEYS` (FlightId, FlightCode, **FlightStatus**, ParkingLot, RouteName,
ArrivalTime, DepartureTime, RefuelTime, AircraftCode, AircraftType, AirlineId, AirlineModel,
IsInternational, IsDeleted, SortOrder, EstimateAmount, DateUpdated) là nhóm mà **server sở hữu** —
`applyRemoteToLocal` luôn cho server thắng ở nhóm này, kể cả khi row đang dirty.

Nhưng `canonicalBusinessJson` chỉ loại `NON_BUSINESS_KEYS`, **giữ nguyên nhóm server sở hữu
trong vân tay**. Hệ quả:

> Lượt pull nền chạy **30 giây một lần** (log: 09:27:44 → 09:28:14 → 09:28:44). Nó ghi đè
> `jsonData` mỗi lần chạy. Chỉ cần MỘT khoá server sở hữu đổi giá trị là vân tay đổi ⇒ mọi
> màn hình đang mở mất quyền lưu, **vĩnh viễn** cho tới khi đóng/mở lại — vì nhánh chặn cố ý
> không rebase baseline.

`FlightStatus` đổi `ASSIGNED → REFUELING` **ngay khi mẻ bắt đầu** (thấy ở cả hai phiếu trong
log) ⇒ gần như mẻ nào cũng dính.

### 4.2 Không có lối thoát và không có tín hiệu

- `postRefuel` trả về row cũ cho cả hai trường hợp "đã lưu" và "bị chặn" ⇒ caller không
  phân biệt được.
- `RefuelDetailConfirmActivity` làm `mItem = DataHelper.postRefuel(mItem, false)` ⇒ **gán row
  cũ (amount 0) đè lên dữ liệu người dùng vừa nhập** ⇒ màn hình về 0 GL.
- `postRefuelCompleted` gọi `openPreview()` vô điều kiện ⇒ đi tiếp như đã lưu thành công.
- Timer đọc đồng hồ gọi `saveData()` mỗi giây ⇒ hàng chục dòng anomaly, không dòng nào tới
  được mắt người dùng.

### 4.3 Log không đủ để truy nguyên

`logVersionConflict` chỉ ghi amount/end, **không ghi khoá nào lệch**. `applyRemoteToLocal`
chỉ ghi diff của 6 trường trong `describeServerOwnedDiff` (flightId, flightCode, parkingLot,
flightStatus, deleted, refuelTime) — 11 khoá server sở hữu còn lại đổi thì **im lặng tuyệt đối**,
dù chúng vẫn vào vân tay. Đây là lý do một lỗi mất dữ liệu sống được lâu.

---

## 5. Bản sửa

### 5.1 Vân tay chỉ tính phần CLIENT sở hữu

`RefuelSyncGuard.businessFingerprintOfJson` nay dùng `canonicalClientOwnedJson` — loại cả
`NON_BUSINESS_KEYS` lẫn `SERVER_OWNED_KEYS`. Server đổi kế hoạch bay không còn là "xung đột
với người dùng".

Guard chống ghi đè vẫn nguyên vẹn cho ca thật: Web sửa **số đồng hồ / lượng / trạng thái mẻ**
vẫn cho `CONFLICT_PAYLOAD_CHANGED` (có test).

### 5.2 Trộn ba chiều cho nhóm trường server

Không thể phủ đè, vì người dùng **sửa được** vài trường trong nhóm đó
(`RefuelDetailActivity:852-874` bãi đỗ/số hiệu/loại tàu bay, `RefuelPreviewActivity:1749-1799`
thêm chặng, hãng, quốc tế). Luật:

| base (lúc mở màn hình) | ours (snapshot) | theirs (row) | kết quả |
|---|---|---|---|
| A | A | B | **B** — người dùng không đụng, nhận bản server |
| A | C | B | **C** — người dùng đã sửa, giữ bản người dùng |
| không có base | — | — | giữ ours, không nhận gì (chiều an toàn) |

Cần chiều thứ ba nên snapshot mang thêm `baseServerOwnedJson` — giá trị nguyên bản của nhóm
trường server lúc đọc row (vân tay là hash, chỉ nói "có đổi", không nói "đổi ở đâu").
Truyền qua Intent cùng baseline hiện có.

Cài đặt: `RefuelSyncGuard.adoptServerOwned(target, storedJson, baseServerOwnedJson)`,
gọi trong `DataHelper.postRefuel` ở nhánh row đã tồn tại, **trước** khi tính `userChange`.

### 5.3 Không huỷ dữ liệu người dùng khi bị chặn

- `RefuelItemData.saveRejected` (transient), đặt bởi `DataHelper.finishConflict(stored, true)`
  **chỉ** ở hai nhánh mà lần ghi local thực sự bị từ chối. Các nhánh "POST không xác nhận được"
  giữ `false` vì dữ liệu local đã lưu an toàn.
- `RefuelDetailConfirmActivity.postData` chỉ gán `mItem = saved` khi **không** bị chặn.
- `postRefuelCompleted` bị chặn thì báo lỗi và **ở lại màn hình**, không `openPreview()`.
- Chuỗi thông báo không đổ lỗi cho người khác (bản đầu viết "phiếu đã thay đổi từ nơi khác" —
  sai, vì thủ phạm là chính app).

### 5.4 Bổ sung log ở mức khoá

- `RefuelSyncGuard.describeJsonDiff(a, b, serverOwnedOnly)`.
- `VERSION_CONFLICT` nay kèm `clientDiff=[...] serverDiff=[...]`.
- `applyRemoteToLocal` ghi `JSON_REWRITTEN uid=... adoptClientOwned=... server=[...] client=[...]`
  mỗi khi lượt nhận làm đổi `jsonData`.

Đây là phần **bắt buộc** để lần chạy tới có bằng chứng, xem mục 6.

### 5.5 Tệp thay đổi

| Tệp | Nội dung |
|---|---|
| `helpers/RefuelSyncGuard.java` | vân tay client-owned, `serverOwnedProjection`, `adoptServerOwned`, `describeJsonDiff` |
| `helpers/DataHelper.java` | gọi `adoptServerOwned`, `finishConflict(stored, rejected)`, log khoá lệch |
| `model/RefuelItemData.java` | `baseServerOwnedJson`, `saveRejected` (đều transient) |
| `data/entity/RefuelItem.java` | đóng dấu `baseServerOwnedJson` khi đọc row |
| `helpers/RefuelIntent.java` | truyền `baseServerOwnedJson` qua Intent |
| `RefuelDetailConfirmActivity.java` | không gán đè khi bị chặn, báo lỗi, không đi tiếp |
| `res/values/strings.xml` | `error_refuel_save_rejected` |

### 5.6 Test

`246 unit test pass` (235 gốc + 11 mới).

- `RefuelServerFieldRebaseTest` (6): dựng lại đúng chuỗi log ở mục 2 — pull đổi `FlightStatus`
  không được chặn lưu; Web sửa số đồng hồ vẫn phải chặn; trộn ba chiều; `FlightId=0` không xoá
  liên kết.
- `RefuelConfirmFieldsPersistTest` (5, Robolectric + Room thật): liệt kê **20 trường** màn hình
  xác nhận cho nhập và khẳng định tất cả nằm trong Room sau khi bấm Xác nhận — có pull nền chen
  vào, có trường hợp người dùng tự sửa bãi đỗ/tàu bay, và lần lưu thứ hai.

---

## 5bis. Đợt 1 — đã triển khai sau review

| Hạng mục | Cài đặt |
|---|---|
| Kết quả lưu tường minh | `RefuelItemData.SAVE_OUTCOME{COMMITTED,CONFLICT,FAILED}` + `isCommitted(result)` — `null` luôn thất bại. `postRefuels` trả `boolean` allCommitted |
| Điều hướng | End, Confirm, Preview, NewRefuel, adapter: chỉ điều hướng sau `COMMITTED`. **Chưa phải "đã audit toàn bộ caller"** — xem 5ter |
| `ConfirmFieldsPatch` | 21 khoá đúng phạm vi màn hình xác nhận; `DataHelper.saveConfirmFields` đọc row mới nhất **dưới `REFUEL_WRITE_LOCK`** rồi mới quyết định |
| Guard của patch | (1) latest `DONE` với `RealAmount/StartNumber/EndNumber/EndTime` khác nền ⇒ `ROW_ALREADY_FINALIZED`; (2) cùng trường hai phía cùng đổi khác giá trị ⇒ `FIELD_CONFLICT`; (3) không baseline ⇒ không đắp |
| Tồn xe | Bỏ khỏi `finalizStop`; chỉ chạy ở `postRefuelCompleted` khi `isCommitted && transitionedToDone`. **Chỉ là "không trừ lặp trong tiến trình hiện tại"**, chưa phải exactly-once bền vững — cờ là `transient`, crash sau khi Room commit và trước khi trừ tồn thì tồn không giảm |
| ACK | `describeFinalValueDiff` thêm `ManualTemperature`, `Density`, `QualityNo` cho mẻ `DONE`, chỉ đòi khi gói gửi lên có giá trị |
| ERROR | Bỏ hoàn toàn đường đẩy row sang `postStatus=ERROR`. Row ở lại hàng đợi với backoff luỹ tiến 1/5/15/60 phút; `postStatus=NONE` nên UI thấy "chưa gửi" |
| `postStatus` | Chép từ cột entity sang model trong `toRefuelItemData()` |
| Log | `describeJsonDiff` chỉ in tên khoá; allowlist giá trị: Status, RealAmount, StartNumber, EndNumber, ManualTemperature, Density, FlightStatus, ClientSeq, ServerRevision |

**Test: 261 pass** (235 gốc + 26 mới/sửa). Hai test cũ khẳng định hợp đồng `→ERROR` đã được
viết lại theo hợp đồng mới. Bộ test cũng đã bắt được một lỗi do chính đợt này tạo ra: map
backoff static không được dọn trong `resetTestDependencies()` nên rò trạng thái giữa các test.

Vẫn đúng như review đã nêu: bộ test mới chứng minh tới mức "dữ liệu vào Room" và các quyết
định thuần; **chưa** kiểm chứng workflow UI thật, partial ACK từ server, process death,
migration và transaction tồn xe.

## 5ter. Hồi lưu đồng hồ và thứ tự ghi (sau review vòng 2)

### Câu hỏi nghiệp vụ

Đồng hồ chạy `398 → 399 → 400`, ngừng bơm, **hồi lưu** làm số lùi về `399`, rồi bấm End.
Số `399` cuối cùng có bị guard chặn không?

### Trả lời: không

Không có luật monotonic ở bất kỳ đâu trong đường lưu:

- `finalRefuelValuesChanged` so KHÁC NHAU, không so lớn/nhỏ;
- `ConfirmFieldsPatch` guard `ROW_ALREADY_FINALIZED` so với NỀN, không so hướng;
- không có `max(EndNumber)` ở bất kỳ đâu.

Hồi lưu là nghiệp vụ hợp lệ; **tuyệt đối không được** thêm luật "chỉ nhận số lớn nhất".

### Nhưng thứ tự ghi thì phải bảo đảm bằng cấu trúc

`399` cuối chỉ thắng nếu lần ghi của End chạy SAU lần ghi `400`. Trước đợt này, các lần lưu
của `RefuelDetailActivity` nằm rải trên hai cơ chế: `AsyncTask` (có thứ tự, dùng
`SERIAL_EXECUTOR`) và **5 chỗ `new Thread` thô** (không có thứ tự với nhóm kia). Hai nhóm đó
có thể đảo nhau, và một số đo trung gian ghi đè số chốt.

Đã sửa: mọi lần ghi phiếu của màn hình đi qua **một `saveExecutor` đơn luồng** theo đúng thứ
tự gọi — số đo trung gian, ghi nhận tiếp cận/rời đi, lưu lúc bắt đầu, và lần chốt của End.
`onDestroy` dùng `shutdown()` chứ không `shutdownNow()`, và mỗi lần ghi chụp **bản sao độc
lập** của phiếu ngay lúc xếp hàng.

Test: `meterRollbackFromRefluxIsTheCommittedValue` chạy đúng chuỗi `398 → 399 → 400 → End(399)`
và khẳng định Room giữ `399`, `transitionedToDone` chỉ bật một lần, không có conflict. Hai test
kèm theo: hồi lưu khi có pull nền chen giữa, và màn hình xác nhận mở sau hồi lưu.

**Chưa làm:** coalesce các số đo trung gian (tối ưu, không phải đúng/sai) và test ở mức UI
thật. Đã có test tự động dùng executor + latch cho thứ tự/race ở tầng hàng đợi và lưu.

### Lỗi P0 do chính bản sửa executor tạo ra (review vòng 3 bắt được)

Bản sửa executor đầu tiên chụp **tham chiếu** (`final RefuelItemData item = mItem`), không
phải bản sao. Chuỗi hỏng:

1. autosave số đo `400` xếp hàng, giữ tham chiếu tới `mItem`;
2. người dùng bấm End ⇒ `finalizStop()` đặt `DONE` + `399` lên **chính** `mItem` đó;
3. task autosave mới chạy, đọc object đã đổi ⇒ **chính autosave** thực hiện chuyển sang DONE;
4. `transitionedToDone` rơi vào kết quả của autosave — thứ bị `warnIfNotSaved` bỏ đi;
5. lần ghi của End thấy row đã DONE ⇒ báo `false` ⇒ **tồn xe không bao giờ được trừ**.

Đã sửa theo đúng ba luật:

- `RefuelItemData.snapshotForSave()` — deep copy tại thời điểm xếp hàng, **kèm baseline**
  (baseline là `transient` nên không đi theo JSON, phải chép tay);
- `enqueueSave(finalize, onResult)` là đường ghi DUY NHẤT của màn hình; autosave mang
  `DONE` bị chặn ngay tại chỗ xếp hàng — chỉ `postData()` được chốt mẻ. Điều này cũng bịt
  luôn lỗ của `updateRefuelDataTCS()`, vốn không có guard `status != DONE`;
- `RefuelItemData.adoptSaveState()` trả danh tính + baseline về cho đối tượng của màn hình
  sau mỗi lần ghi thành công — không có bước này thì lần lưu kế tiếp luôn conflict.

Test: `queuedSnapshotIsIndependentOfLaterEdits`, `onlyTheEndSaveReportsTransitionAfterReflux`,
`screenAdoptsVersionFromQueuedSave`, và `queuedAutosaveCannotFinalizeWhileEndIsPending` —
chạy trên `ExecutorService` thật với `CountDownLatch` giữ autosave lại đúng lúc End xen vào.
Đã kiểm chứng ngược: cho `snapshotForSave()` trả về `this` thì **3 test đổ**, nên các test này
thật sự bắt được lỗi chứ không pass vì không mô phỏng được race.

### Race baseline của snapshot End (review vòng 4 bắt được)

Deep copy giải quyết mutation dữ liệu nhưng tạo thêm một cửa conflict: snapshot End được chụp
khi autosave `400` phía trước còn nằm chờ, nên nó giữ `baseClientSeq` cũ. Autosave chạy trước
làm tăng `clientSeq`; nếu End dùng nguyên baseline lúc enqueue thì chính End bị chặn dù hai
task nằm đúng thứ tự trong cùng executor.

Đã sửa theo nguyên tắc tách hai phần:

- dữ liệu nghiệp vụ (`399 + DONE`) giữ nguyên từ thời điểm enqueue;
- ngay trước khi task chạy, chỉ identity/version/baseline được refresh từ kết quả task đứng
  trước bằng `snapshot.adoptSaveState(source)`.

Test latch đã được chỉnh để chụp snapshot End **trước khi** autosave được thả chạy, giống code
production; khi tới lượt End mới refresh baseline. Đồng thời `enqueueSave` xử lý việc queue bị
đóng trong race với `onDestroy`, tránh `RejectedExecutionException` làm crash ứng dụng.

### Dung sai khi đối chiếu ACK

Đối chiếu nhiệt độ/tỉ trọng thêm ở đợt 1 dùng so sánh `double` tuyệt đối — backend lưu
`decimal(x,y)` nên làm tròn sẽ thành non-ACK hàng loạt và giữ mọi phiếu ở trạng thái chờ.
Đã đổi sang so theo dung sai: nhiệt độ `0.01`, tỉ trọng `0.0001`.

**Biên đã chốt: chênh lệch ĐÚNG BẰNG dung sai vẫn được coi là ACK** (điều kiện báo lệch là
`> tolerance`). Riêng chỗ này có một cái bẫy đã đo được: `30.01 - 30.00` cho
`0.0100000000000016`, nên so thẳng `> 0.01` vẫn báo lệch cho đúng ca mà dung sai sinh ra để
bỏ qua — test `differenceExactlyAtToleranceIsStillAck` đổ ngay lần chạy đầu. Đã thêm
`differsBeyond()` nới `1e-9` để biên đó thành đúng thật. Kèm test cho ca vượt dung sai và ca
phiếu chưa nhập (giá trị 0 thì không đối chiếu).

## 6. Chỗ CHƯA chắc chắn — mong được review kỹ

### 6.1 Chưa chứng minh được khoá nào đã đổi ở ca 09:24:21

Log ghi `KEEP_LOCAL_VALUES reason=LOCAL_MODIFIED`, tức lượt pull **không** nhận nhóm client
sở hữu. Vậy khoá làm đổi vân tay phải nằm trong nhóm server sở hữu — nhưng
`describeServerOwnedDiff` không in ra nên **không có bằng chứng trực tiếp**, chỉ là suy luận
loại trừ.

Đã loại trừ được một nghi can bằng probe test: `mergeServerMetadata`/`applyServerAck` (đường
ACK) round-trip model rồi ghi lại `jsonData`, nhưng thực tế **chỉ đổi `IsLocalModified` và
`ServerRevision`** — cả hai đều ngoài vân tay. Không phải thủ phạm.

Còn lại các khoá server sở hữu mà `describeServerOwnedDiff` không kiểm: `ArrivalTime`,
`DepartureTime`, `EstimateAmount`, `RouteName`, `AircraftCode`, `AircraftType`, `AirlineId`,
`AirlineModel`, `IsInternational`, `SortOrder`, `DateUpdated`. Bản sửa 5.1 xử lý **toàn bộ**
nhóm này nên đúng dù là khoá nào; log 5.4 để lần chạy tới xác nhận.

**Câu hỏi cho review:** có nên chặn merge (5.2 + `applyRemoteToLocal`) ghi lại `jsonData` khi
canonical JSON không đổi về mặt ngữ nghĩa, để giảm số lần row bị đụng?

### 6.2 Phương án mạnh hơn đã cân nhắc nhưng CHƯA làm: trộn ba chiều cho TOÀN BỘ payload

Hiện chỉ trộn ba chiều nhóm server sở hữu; phần client vẫn dùng vân tay (hash) nên chỉ biết
"có đổi", và mọi thay đổi ở phần client — dù do app tự ghi — vẫn thành conflict cứng.

Phương án triệt để: snapshot mang **nguyên base JSON**, `decideSave` trộn ba chiều mọi khoá,
chỉ conflict khi **cả hai phía cùng đổi một khoá sang giá trị khác nhau**. Ưu điểm: loại sạch
false conflict bất kể khoá nào, và xử lý đúng cả ca 1110/862 (bản cũ giữ nguyên giá trị base ⇒
nhận bản mới). Nhược: đụng vào vùng an toàn dữ liệu, tốn bộ nhớ giữ base JSON cho từng phần tử
danh sách.

**Câu hỏi cho review:** có nên đổi sang phương án này không?

### 6.3 Điều hướng đã chặn, nhưng caller audit CHƯA xong

Mọi đường điều hướng nay đòi `COMMITTED`, và các lần lưu nền của `RefuelDetailActivity`,
`RefuelPreviewActivity`, adapter đã báo lỗi thay vì nuốt. Nhưng **không được tuyên bố "đã
audit toàn bộ caller"**: các lần lưu nền mới chỉ báo lỗi, chưa có đường phục hồi baseline.
Một conflict thật ở lần lưu nền vẫn có thể làm baseline của `mItem` cũ đi, khiến người dùng
bấm "Thử lại" ở End mà không bao giờ thành công. Đường phục hồi kiểu `ConfirmFieldsPatch`
cần được áp cho luồng End (một `EndFieldsPatch` tương ứng) — chưa làm.

### 6.3b Tồn xe mới là "không trừ lặp trong tiến trình", chưa exactly-once

`transitionedToDone` là cờ `transient`. Crash sau khi Room commit nhưng trước
`applyStockChange()` ⇒ mẻ DONE mà tồn chưa giảm. Tồn xe và row mẻ cũng không nằm trong cùng
transaction. Muốn đúng thật thì cần cờ bền vững (`stockAdjustmentApplied`) hoặc outbox phục
hồi sau restart — thuộc hạng mục ChangeSet/outbox.

### 6.3c ACK vẫn là xác minh MỘT PHẦN

Đã có status, lượng, số đồng hồ, giờ kết thúc, nhiệt độ, tỉ trọng, QC (có dung sai). Các
trường xác nhận khác — lái xe, nhân viên, số hoá đơn, lượng hoàn trả — vẫn chưa được đối
chiếu, nên vẫn có thể được đánh dấu đã gửi trong khi server chưa ghi. Lời giải đúng là
backend trả `Applied` / revision / payload hash, không phải đoán thêm ở client.

### 6.4 Row đang kẹt `postStatus = ERROR`

`noteUnconfirmedPost` đánh ERROR sau 3 lần POST không xác nhận được, và
`getModifiedForSync()` loại row ERROR khỏi hàng đợi. Row chỉ quay lại khi người dùng lưu
thành công (`resumeSync`) hoặc qua `DatabaseMaintenance` một lần mỗi phiên bản. Với các phiếu
đã hỏng trên xe, cần kiểm tra xem chúng có tự lên được server sau khi cài bản vá không.

### 6.5 Chưa test trên thiết bị thật

Bản đang chạy trên máy là bản phát hành, khác chữ ký với debug build — cài đè phải gỡ app và
sẽ mất dữ liệu local chưa đồng bộ. Cần build bản release cùng keystore rồi mới thử lại trên xe.

---

## 7. Cách xác minh sau khi cài

Chạy một mẻ thật rồi lọc log:

```bash
adb logcat -b all -s DTH SYNC RFC REFUEL_ANOMALY POST_EXCHANGE | grep -E "VERSION_CONFLICT|JSON_REWRITTEN|ADOPT_SERVER_FIELDS|POST_EXCHANGE"
```

Kỳ vọng:

- **Không** còn dòng `VERSION_CONFLICT ... source=LOCAL_SAVE` trong suốt mẻ.
- `POST_EXCHANGE` mang `amount` tăng dần theo đồng hồ, không còn đứng ở 0.
- Có `JSON_REWRITTEN` mỗi lượt pull — đọc `server=[...]` để biết đích xác khoá nào server đổi.
- Sau khi bấm Xác nhận: phiếu ở màn hình danh sách hiện đúng số GL, nhiệt độ, tỉ trọng.
