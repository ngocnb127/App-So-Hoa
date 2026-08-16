# PHƯƠNG ÁN v2 — BM 75.01/NLHK: Phiếu yêu cầu hút nhiên liệu từ tàu bay

> **Trạng thái: ĐÃ PHÊ DUYỆT CÓ ĐIỀU KIỆN (review vòng 3).**
> Được phép triển khai **bước 1 — model + state + validation**.
> **Bị chặn:** bước 2 (storage/migration) và mọi việc phát hành — xem §12 và §16.
> Bản v1 đã được lưu tại `docs/archive/PLAN-BM7501-v1-superseded.md` — **không dùng nữa**,
> vì còn sai khóa liên kết, sai điều kiện vi sinh/C9/C10, backup sai chỗ và gộp sai trạng thái.
> Bản v2 này viết lại toàn bộ theo review vòng 2, **không giữ lại nội dung cũ mâu thuẫn**.
>
> Nguồn nghiệp vụ: `2_BM 75.01.NLHK.doc` (SKYPEC, ban hành/sửa đổi 01/03) — trang 1 mục A,
> trang 2 điều kiện vi sinh + tín hiệu phối hợp, trang 3 xử lý nhiên liệu + chữ ký.
> Backup mã nguồn: `/Users/ngoc/Documents/DevNgoc/_backup/App-So-Hoa-20260810-164130.tar.gz`

## Bảng tiếp nhận review vòng 2

| # | Vấn đề Codex nêu | Xử lý ở v2 |
|---|---|---|
| 1 | Unique + `INSERT OR ABORT` chưa đủ chống mất chứng từ | Nhận. §4 — CAS theo `localRevision`, transaction, một tầng ghi duy nhất |
| 2 | Local-first chưa an toàn, `getExternalFilesDir()` mất khi gỡ app | Nhận. §5 — gói backup ZIP qua SAF, checksum, verify |
| 3 | `localNumber`/`serverNumber` | Nhận điều kiện. §6 — `localNumber` là số pháp lý, backend bắt buộc chấp nhận |
| 4 | Cộng `height` ZPL thủ công | Nhận. §9 — `ZplLayoutBuilder` + golden test |
| 5 | Gộp sai business status và sync status | Nhận. §3 — tách 2 enum |
| 6 | Validation mâu thuẫn biểu mẫu | Nhận toàn bộ. §8 viết lại |
| 7 | Có thể cần 3 chữ ký | Nhận. §7 — thiết kế mặc định **3 chữ ký** |
| 8 | C4 chưa rõ | Nhận. §8.7 — mặc định là xác nhận đã phổ biến + chỉ lưu `otherSignal` |
| 9 | Nối `defuelingNo` | Nhận. §11 — lookup có điều kiện, không mutate, regression test |
| 10 | 1 liên thiếu bản cho khách | Nhận. §10 — bỏ "chụp màn hình", thay bằng bản sao COPY / xuất PDF đã ký |
| 11 | **Sai khóa liên kết** | Nhận. §2 — đã sửa thành `RefuelItem.uniqueId`, kèm bằng chứng mã nguồn |
| 12 | `jsonData` không được là nguồn sự thật duy nhất | Nhận. §2.3 — tách rõ vai trò |
| 13 | Migration là blocker | Nhận, và **nặng hơn Codex nghĩ** — §12, phát hiện migration mồ côi |
| 14 | Đổi thứ tự commit | Nhận. §13 theo đúng thứ tự Codex đề nghị |

---

## 1. Kết quả kiểm chứng trên mã nguồn

Ba điểm dưới đây tôi đã đọc code để xác nhận trước khi viết lại:

**1.1 Khóa liên kết — Codex đúng, v1 sai.**
`RefuelItemData` kế thừa `uniqueId` từ `BaseModel:84` (`UUID.randomUUID()`), còn
`flightUniqueId` là **trường riêng biệt** khai ở `RefuelItemData:961`, do
`NewRefuelActivity:96` gán cho **chuyến bay**. Nhiều mẻ trên cùng chuyến dùng chung
`flightUniqueId` → v1 dùng nó làm khóa 1–1 là sai về ngữ nghĩa và sẽ vỡ ngay khi 2 mẻ cùng chuyến.

**1.2 Repo đã có sẵn khuôn CAS để dùng lại.**
`RefuelItem`/`RefuelItemData` đã có `clientSeq`, `serverRevision`, `baseClientSeq`,
`baseServerRevision`, `baseBusinessFingerprint` và `helpers/RefuelSyncGuard.businessFingerprintOfJson(...)`
(xem `RefuelItem.toRefuelItemData()`). BM 75.01 **không phát minh cơ chế mới** mà dùng lại đúng
khuôn này — giảm rủi ro và giúp người bảo trì đọc quen.

**1.3 Chữ ký hiện đang lưu sai chỗ đúng như Codex cảnh báo.**
`ReceiptSignActivity:31` lưu chữ ký bằng `File.createTempFile(...)` vào
`getExternalFilesDir(DIRECTORY_PICTURES)`. Mất khi gỡ app, và là **temp file**.
Không được lặp lại mô hình này cho BM 75.01.

---

## 2. Mô hình dữ liệu

### 2.1 Khóa liên kết (sửa blocker #11)

```
BM7501.refuelItemUniqueId  ↔  RefuelItem.uniqueId   (KHÔNG phải flightUniqueId)
```

`RefuelItem.uniqueId` là khóa của **một mẻ hút**, đúng với quan hệ 1 phiếu ↔ 1 mẻ đã chốt.

### 2.2 Mô hình revision / void / supersede (giải mâu thuẫn ở #1)

Chọn **mô hình 2** theo khuyến nghị Codex: cho phép nhiều revision chứng từ, chỉ một revision active.

Cột trên entity `BM7501`:

| Cột | Vai trò |
|---|---|
| `uniqueId` | khóa chính nghiệp vụ của **một revision**, dùng cho idempotency khi sync |
| `refuelItemUniqueId` | mẻ hút, **không** unique một mình |
| `revisionNumber` | 1, 2, 3… |
| `supersedesUniqueId` | revision bị thay thế |
| `businessStatus` | §3 |
| `syncStatus` | §3 |
| `localRevision` | bộ đếm CAS cho ghi cục bộ (khác `revisionNumber` của chứng từ) |
| `localNumber` / `serverNumber` | §6 |
| `reprintCount`, `printedAt`, `signedAt`, `voidedAt`, `voidReason` | audit |
| `enteredByUserId` | nhân viên nhập hộ |
| `jsonData` | snapshot payload nghiệp vụ |
| `signedSnapshotHash` | băm bản đã ký, §7.4 |

Ràng buộc:

```sql
UNIQUE (refuelItemUniqueId, revisionNumber)
UNIQUE (localNumber)
```

**Về "chỉ một revision active":** SQLite có partial unique index, nhưng `@Index` của Room
**không biểu diễn được** partial index, dẫn tới lệch schema khi Room validate. Vì vậy:
- Không dùng partial unique index.
- Bất biến "một mẻ chỉ có một phiếu chưa VOIDED" do **repository** bảo đảm, trong transaction:
  đọc active hiện tại → `VOIDED` → chèn revision mới với `supersedesUniqueId` — tất cả trong
  một `@Transaction`.
- Có test bảo vệ bất biến này, vì nó không được DB ép buộc.

### 2.3 Vai trò từng nơi lưu (sửa #12)

| Nơi lưu | Vai trò | Không được làm gì |
|---|---|---|
| Cột phẳng | khóa liên kết, `localNumber`, `businessStatus`, `syncStatus`, `localRevision`, `revisionNumber`, audit timestamp, `reprintCount` | — |
| `jsonData` | **snapshot payload nghiệp vụ** (nội dung mục A/B/C) | **không** chứa trạng thái pháp lý; không phải "nguồn sự thật duy nhất" như v1 viết |
| File chữ ký | ảnh PNG + checksum, §5.2 | không nằm trong `jsonData` |
| `signedSnapshotHash` | băm của bản đã ký, để đối chiếu về sau | — |

Model lưu trữ local có `schemaVersion` **ổn định, độc lập với backend**. Backend đổi tên trường
→ sửa **mapper API**, tuyệt đối không sửa payload lịch sử đã ký.

---

## 3. Tách business status và sync status (sửa #5)

```java
enum BusinessStatus { DRAFT, A_DONE, B_DONE, C_DONE, SIGNED, PRINTED, VOIDED, CANCELLED }
enum SyncStatus     { NOT_READY, PENDING, SYNCING, SYNCED, FAILED }
```

- Hai trục độc lập: `businessStatus = PRINTED` + `syncStatus = FAILED` là trạng thái hợp lệ và
  sẽ xảy ra thường xuyên trong giai đoạn backend chưa có.
- `CANCELLED` thêm vào để phục vụ §8.6 (mẻ hút hủy/không thành công).
- **`A_DONE`/`B_DONE`/`C_DONE` chỉ đặt khi người dùng bấm xác nhận hoàn tất bước và validation
  bước đó đạt** — autosave **không** đổi trạng thái. (v1 sai chỗ này.)
- UI vẫn trình bày dạng wizard 3 bước; nhân viên không cần biết state machine.

---

## 4. Chống mất chứng từ (sửa #1)

### 4.1 Bất biến bắt buộc

1. **Chỉ `BM7501Repository` được ghi bảng `BM7501`.** Không Activity/Fragment/worker nào gọi DAO ghi trực tiếp.
2. DAO **không** expose hàm replace cả row tự do. Không có `@Insert(onConflict = REPLACE)`.
3. Autosave chỉ update khi `businessStatus` còn editable.
4. Mọi update đi qua CAS:

```sql
UPDATE BM7501
   SET jsonData = :payload,
       localRevision = localRevision + 1,
       updatedAt = :now
 WHERE uniqueId = :id
   AND localRevision = :expectedRevision
   AND businessStatus IN ('DRAFT','A_DONE','B_DONE','C_DONE')
```

`affectedRows == 0` → **reload + xử lý conflict, không ghi đè**. Không retry mù.

5. Chuyển `SIGNED` / `PRINTED` / `VOIDED` chạy trong `@Transaction`, kèm kiểm tra trạng thái nguồn.
6. **Sync không được ghi đè row local đã ký.** Response server chỉ được phép cập nhật
   `serverNumber`, `syncStatus`, `id` — không đụng `jsonData`, không đụng `businessStatus`.
7. Callback đến trễ: mọi callback mang theo `expectedLocalRevision`; lệch thì bỏ qua và ghi log.

### 4.2 Việc này chặn được đúng những gì

| Kịch bản | Cơ chế chặn |
|---|---|
| Tạo 2 phiếu cho 1 mẻ | repository + transaction (§2.2) |
| Autosave cũ đè dữ liệu mới | CAS `localRevision` |
| Callback trễ đè `SIGNED` | CAS + điều kiện `businessStatus IN (...)` |
| Sync response đè bản local | quy tắc 6 |
| 2 autosave ngược thứ tự | CAS |
| Không tạo được phiếu thay thế sau VOID | mô hình revision §2.2 |

---

## 5. Backup và lưu trữ (sửa #2) — **thuộc đường tới hạn**

### 5.1 Bỏ hẳn khỏi phương án

- ❌ **"Chụp màn hình phiếu"** — loại bỏ hoàn toàn khỏi vai trò backup và bản mềm pháp lý.
  Không có cấu trúc dữ liệu, không checksum, thiếu nội dung ngoài màn hình, không phục hồi được.
- ❌ Export ra `getExternalFilesDir()` — mất khi gỡ app.

### 5.2 Lưu chữ ký

- Thư mục **app-private persistent**: `context.getFilesDir()/bm7501/<uniqueId>/`, **không**
  `getCacheDir()`, **không** `createTempFile`, **không** external.
- Mỗi ảnh có checksum SHA-256 ghi kèm trong metadata row.
- Không xóa file chữ ký khi `syncStatus != SYNCED` **hoặc** chưa có backup đã xác nhận.

### 5.3 Gói backup qua SAF

Người dùng chọn vị trí bằng Storage Access Framework (`ACTION_CREATE_DOCUMENT`), app ghi 1 file ZIP:

```
bm7501-<localNumber>.zip
├── manifest.json          (localNumber, uniqueId, revisionNumber, businessStatus,
│                           exportedAt, exportedByUserId, appVersion, schemaVersion)
├── bm7501.json            (payload nghiệp vụ)
├── customer-section-a-signature.png
├── customer-final-signature.png
├── skypec-signature.png
└── checksums.sha256
```

Yêu cầu:
- Ghi `exportedAt`, người xuất, checksum vào cả manifest và row.
- **Có chức năng đọc lại/kiểm tra gói export** (verify checksum, đọc manifest) — backup không
  verify được thì không tính là backup.
- Cảnh báo phân biệt **"chưa backup"** và **"chưa sync"** — hai chuyện khác nhau.
- Chặn mọi thao tác dọn dữ liệu/chữ ký nếu chưa sync **và** chưa có backup xác nhận.
- Trước khi cập nhật app / migration DB: kiểm tra còn phiếu BM7501 chưa được bảo vệ hay không,
  nếu còn thì cảnh báo và mời export trước.

---

## 6. Số phiếu (sửa #3)

**Chốt hợp đồng với backend:**
- `localNumber` do app sinh = **số chứng từ pháp lý chính thức**, bất biến sau khi in.
- `serverNumber` = tham chiếu kỹ thuật nội bộ, optional.
- Backend **bắt buộc chấp nhận** `localNumber`; **không** cấp số pháp lý khác sau khi phiếu đã ký/in
  — mô hình hai danh tính cho một chứng từ là không chấp nhận được.
- Backend idempotent theo `uniqueId`, unique constraint trên `localNumber`.

**Sinh sequence:**
- ❌ Bỏ `countByDate() + 1` của v1 (đua và thủng khi xóa).
- ✅ Bảng `BM7501Counter(scopeKey TEXT PRIMARY KEY, lastSeq INTEGER)`, cấp số trong
  `@Transaction`: đọc → +1 → ghi → trả về. Unique index trên `localNumber` là lưới chặn cuối.
- Format: `75-<chi nhánh>-<số xe>-<mã thiết bị>-<yyMMdd>-<seq>`.

**Câu hỏi phải chốt trước commit storage:** *một xe có bao giờ dùng nhiều tablet không?*
- Nếu **có** → giữ `<mã thiết bị>` trong format (đang đề xuất), hoặc cấp dải seq theo thiết bị.
- Nếu **không** → có thể bỏ `<mã thiết bị>` cho số ngắn lại.
Chưa có câu trả lời thì **giữ mã thiết bị**, vì trùng số chứng từ pháp lý là lỗi không sửa được.

---

## 7. Chữ ký: thiết kế cho **ba** chữ ký (sửa #7)

Biểu mẫu trang 1 mục A ghi *"Customer Representative (Name & signature)"*; trang 3 có chữ ký
**đại diện SKYPEC** và **đại diện khách hàng**. Số hóa đúng nguyên bản ⇒ **3 chữ ký**:

| # | Chữ ký | Thời điểm | Ý nghĩa |
|---|---|---|---|
| S1 | Đại diện khách hàng — mục A | khi chốt `A_DONE` | xác nhận lời khai mục A (do nhân viên nhập hộ) |
| S2 | Đại diện SKYPEC | khi ký cuối | xác nhận kết quả kiểm tra + số liệu |
| S3 | Đại diện khách hàng — cuối | khi ký cuối | xác nhận phương án xử lý nhiên liệu (C9/C10) |

- **Không tự rút xuống 2 chữ ký** chỉ vì `ZebraWorker` hiện chỉ có 2 slot ảnh
  (`E:BUYER.GRF`, `E:SELLER.GRF`) — đó là ràng buộc kỹ thuật, không phải lý do nghiệp vụ.
  Giải pháp: thêm slot thứ ba `E:CUST_A.GRF` trong `ZebraWorker.print7501(...)`.
- Điểm này ảnh hưởng model, state machine, UI, storage, export và bản in → **phải chốt trước
  commit model** (§14 câu 1).
- Nếu nghiệp vụ xác nhận S1 được S3 bao hàm thì bỏ S1; thiết kế đã tách sẵn nên bỏ dễ hơn thêm.

**7.4 Snapshot bất biến:** khi vào `SIGNED`, sinh snapshot payload + `signedSnapshotHash`
(SHA-256 của `bm7501.json` chuẩn hóa + 3 file chữ ký). Bản in và bản export về sau luôn dựng từ
snapshot này, không dựng lại từ dữ liệu sống.

---

## 8. Validation viết lại theo biểu mẫu (sửa #6)

### 8.1 Vi sinh mục A — là điều kiện, không phải bắt buộc
Biểu mẫu: *"Trường hợp nhiên liệu đã được hãng hàng không thực hiện kiểm tra vi sinh, đề nghị cập nhật"*.

```
customerMicrobialTestPerformed : YES | NO | UNKNOWN   (bắt buộc chọn)
→ chỉ khi YES mới bắt buộc customerMicrobialKit + customerMicrobialResult
```

### 8.2 Vi sinh mục B — điều kiện đúng theo biểu mẫu
v1 sai khi lấy điều kiện `A10 = Không`. Điều kiện thật là **một trong ba**:

```
skypecMicrobialRequired = (VAC == NOT_SATISFY) || (CWD == NOT_SATISFY)
                          || contaminationSuspected == true
                          || customerRequested == true
```

- `contaminationSuspected` và `customerRequested` là **ô nhập riêng**, không suy diễn.
- Khi `skypecMicrobialRequired == true` → bắt buộc kit + result + **`microbialReason`** (lý do vì sao phải test).

### 8.3 Phụ gia — loại trừ lẫn nhau
`NONE` và `UNDETERMINED` loại trừ **toàn bộ** FSII / Biocide / Aquarius WMA. Chọn `NONE` mà vẫn
tick FSII là lỗi validation, không phải cảnh báo.

### 8.4 A10 và A14
- A10 (đã xả mẫu tất cả thùng): bắt buộc chọn Có/Không.
- A14: cả 4 ô bắt buộc; nút nhanh "Không xác định được / Undetermined" đúng ghi chú biểu mẫu.

### 8.5 C9 / C10 — sửa điều kiện
v1 sai khi buộc C10 chỉ khi `C9 = NO`. Điều kiện đúng:

```
C10 bắt buộc nếu  (không nạp lại ngay được)  HOẶC  (nhiên liệu có vấn đề chất lượng)
```

trong đó "có vấn đề chất lượng" suy ra từ mục B (VAC/CWD không đạt, hoặc vi sinh ở mức
Warning/Action). Nếu chọn `STORAGE` → `storageFrom`/`storageTo` bắt buộc, `to >= from`.
Nếu chọn `REFUEL_DESPITE_ISSUE` → `handlingNote` bắt buộc (biểu mẫu có dòng chấm để ghi rõ).

### 8.6 Nhiệt độ, KLR, lượng thực tế — bỏ các ngưỡng bịa
- **Nhiệt độ:** bỏ ràng buộc `> 0`. Nhiên liệu có thể ≤ 0 °C trong điều kiện khai thác nhất định.
  Dùng khoảng cấu hình được, mặc định đề xuất `[-40, +60] °C`, **cần nghiệp vụ xác nhận**;
  ngoài khoảng thì **cảnh báo**, không chặn.
- **KLR:** không chỉ `> 0`. Dùng khoảng theo loại nhiên liệu, lấy từ cấu hình
  (Jet A-1 tham chiếu ~`775–840 kg/m³`) — **không hard-code khi nghiệp vụ chưa duyệt**.
- **`actualKg`:** không ép `> 0`. Hai đường:
  - mẻ hút thực hiện được → tạo BM 75.01 bình thường;
  - mẻ hút hủy/không thành công → `businessStatus = CANCELLED` + `cancelReason`, **không ký**
    như phiếu hoàn tất, không in bản gốc.
  *Đề xuất mặc định:* chỉ tạo BM 75.01 khi có phát sinh lượng hút; `CANCELLED` dành cho phiếu
  đã lỡ tạo rồi mẻ bị hủy.
- **Thời gian 3–180 phút:** đây là rule của **mẻ hút** (`ReceiptModel:125`), **không** sao chép
  vào validator chứng từ. Giữ nguyên chỗ cũ.

### 8.7 C4 — tín hiệu phối hợp
Biểu mẫu mô tả ý nghĩa hai tín hiệu chuẩn nhưng **không có ô tick** như các mục khác.
*Đề xuất theo khuyến nghị Codex:*
- Hai tín hiệu chuẩn = **nội dung hướng dẫn**, hiển thị trên form + 1 ô xác nhận "đã phổ biến",
  in nguyên văn lên phiếu.
- Chỉ lưu `otherSignal` (text) khi hai bên thống nhất phương thức khác.
- ❌ Bỏ `SIGNAL_METHOD` bitmask của v1.
- Cần nghiệp vụ xác nhận (§14 câu 3).

---

## 9. Bản in nhiệt (sửa #4)

### 9.1 `ZplLayoutBuilder`

Không cộng `height` thủ công rải rác như `ReceiptModel` đang làm. Viết helper nhỏ, không thêm thư viện:

```java
addText(...)  addWrappedText(...)  addDivider()  addSignature(slot)  currentY()  buildLabel()
```

Quy tắc:
- **Wrap chủ động ở client**, tính số dòng sau wrap, cộng `currentY` theo số dòng **thực tế**.
- ❌ Không vừa để `^FB` tự wrap vừa đoán số dòng theo độ dài chuỗi (lỗi hiện có của `ReceiptModel`).
- Tính theo bề rộng vùng in và font đang dùng (`OPENSANS-RE.TTF`, `^CI28`).
- Nội dung tự do có **giới hạn số dòng**; vượt thì báo người dùng **trước khi ký/in**, không cắt âm thầm.
- `^LL` sinh từ `currentY()` cuối cùng.
- `^LH130,0` cho ZQ520, `^LH000,0` cho ZQ511 (mặc định `TruckModel` hiện là ZQ511).

### 9.2 Test bắt buộc
Golden test cho chuỗi ZPL, phủ: chuỗi tiếng Việt có dấu · tên dài · "lý do khác" dài ·
ghi chú chất lượng dài · không có vi sinh · đầy đủ mọi trường · 3 ảnh chữ ký · header reprint.
**Kèm in thử trên ZQ511/ZQ520 thật** — golden test không thay được việc này.

### 9.3 ESC/P
`PrintWorker` không in được ảnh → in đủ phần chữ, chừa chỗ ký tay, ghi rõ hạn chế trong hướng dẫn.
Nghiệp vụ BM 75.01 ưu tiên máy Zebra.

---

## 10. Một liên và bản cho khách (sửa #10)

Hai bên ký trên cùng một liên, SKYPEC giữ bản gốc — **không mâu thuẫn** về thao tác.
Vấn đề còn lại là khách hàng không có bản lưu khi backend chưa chạy. Phương án (chọn 1, cần nghiệp vụ chốt):

1. In **bản sao đóng dấu COPY** giao khách khi họ yêu cầu (`reprintCount++`, log lại); hoặc
2. **Xuất bản điện tử hoàn chỉnh đã ký** (PDF/ảnh dựng từ snapshot §7.4) chia sẻ qua SAF; hoặc
3. Tạm thời **in 2 bản** trong giai đoạn backend chưa có.

❌ Không mặc định "khách hàng sẽ chờ backend". ❌ Không dùng screenshot.
*Đề xuất của tôi: (1) làm ngay ở commit in, (2) làm khi có snapshot ổn định.*

---

## 11. Nối `localNumber` vào `ReceiptModel.defuelingNo` (sửa #9)

`createReturnThermalText()` đang in dòng *"…tại số phiếu hút số:"* rồi **bỏ trống**.
Điều kiện để nối mà không phá bản in hiện hành:
- Không đổi format phiếu **không** thuộc `EXTRACT`.
- Lookup BM7501 **bằng `RefuelItem.uniqueId`**, chỉ lấy revision **active** (không `VOIDED`).
- Chỉ gán khi BM7501 đã đạt trạng thái nghiệp vụ được chốt (đề xuất: `SIGNED` trở đi).
- Chưa có BM7501 → **giữ nguyên hành vi hiện tại**.
- Không overwrite `defuelingNo` đã nhập tay (`PrintReceiptActivity:527`) khi chưa có quy tắc migration.
- **Truyền số vào formatter lúc dựng bản in**, không mutate/persist `ReceiptModel`, vì trường này
  còn được luồng khác dùng.
- Regression test so text/ZPL bản in trước và sau.

---

## 12. Migration (sửa #13) — **blocker, và nặng hơn dự kiến**

### 12.1 Phát hiện mới khi đọc code

`AppDatabase.java` định nghĩa `MIGRATION_5_8`, `MIGRATION_8_11`, `MIGRATION_8_12`, `MIGRATION_8_14`
nhưng `Room.databaseBuilder(...)` **chỉ đăng ký `MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12`**
(`AppDatabase.java:213`). Nghĩa là:

- Không có đường đi nào tới version 9 → **mọi thiết bị đang ở DB ≤ 8 sẽ rơi vào
  `fallbackToDestructiveMigration()` và bị xóa sạch dữ liệu offline.**
- Đây là lỗi đang tồn tại, **không phải do BM 75.01 gây ra**, nhưng nếu bê nguyên mô hình đó cho
  chứng từ pháp lý thì sẽ mất chứng từ. Phải xử lý trước.

### 12.2 Không dựng được ma trận version từ repo

Lịch sử git đã bị squash (`git log` trên `AppDatabase.java` chỉ còn 3 commit "Initial…"),
`versionCode` hiện tại là **104**. Không có dữ liệu để suy ra "app version → DB version".
Ma trận phải lấy từ nguồn ngoài:
- kho APK đã phát hành (giải nén, đọc hằng `version` trong `AppDatabase`), và/hoặc
- khảo sát thực địa: log `PRAGMA user_version` từ các tablet đang chạy.

**Yêu cầu bạn cung cấp** kho APK cũ hoặc cho phép thêm một lệnh log `user_version` vào bản kế tiếp.

### 12.3 Việc phải làm

1. Lập ma trận **app version → DB version** từ nguồn ở §12.2.
2. Bổ sung đủ đường migration tới 13, gồm cả các version đang mồ côi (≤ 8) hoặc kết luận rõ ràng
   "không còn thiết bị nào ở version đó" **có bằng chứng**.
3. Test bằng **database fixture có dữ liệu thật**, không chỉ DB rỗng.
4. Chính sách destructive fallback bằng văn bản: từ bản có BM 75.01 trở đi,
   **fallback không được phép xóa bảng `BM7501` và thư mục chữ ký** — nếu buộc phải reset DB thì
   export gói backup (§5.3) trước.

---

## 13. Kế hoạch thực hiện (theo thứ tự Codex đề nghị)

| # | Bước | Nội dung | Điều kiện xong |
|---|---|---|---|
| 0 | **Chốt nghiệp vụ** | 3 chữ ký? C4? các khoảng validation? một xe nhiều tablet? | Có văn bản trả lời — **chặn mọi commit sau** |
| 1 | Model + state | `BM7501Model`, enum, tách `BusinessStatus`/`SyncStatus`, validation §8 | Unit test đủ nhánh validation |
| 2 | Storage | Entity, DAO, migration + ma trận §12, `localRevision` CAS, bảng counter, sequence transaction | Test fixture DB thật; test đua autosave |
| 3 | Chữ ký + backup | Lưu `getFilesDir()`, checksum, gói ZIP qua SAF, **verify đọc lại được** | Khôi phục thành công từ gói export |
| 4 | Form + autosave | Wizard 3 bước, autosave CAS, prefill, khóa theo `CREATE_EXTRACT` | Nhập đủ 1 phiếu trên máy thật |
| 5 | Ký + snapshot + void/supersede | Snapshot bất biến, `signedSnapshotHash`, tạo revision thay thế | Không sửa được sau ký; void → tạo revision mới OK |
| 6 | In nhiệt | `ZplLayoutBuilder`, golden test, COPY/reprint | **In thử ZQ511/ZQ520 thật** |
| 7 | Outbox | Hàng đợi + cờ `BM7501_SYNC_ENABLED` | Tắt mạng vẫn chạy đủ |
| 8 | Nối `defuelingNo` | §11 | Regression test bản in |
| 9 | Fix validate hãng bay | NPE bị nuốt ở `NewRefuelActivity.save()` | Báo lỗi rõ khi chưa chọn hãng |

Thay đổi so với v1: **storage + backup + chữ ký (bước 2–3) nằm trong đường tới hạn**, không phải
làm sau. Vì backend chưa có, đây mới là chỗ dữ liệu pháp lý có thể mất.

---

## 14. Bảy quyết định đã chốt (review vòng 3)

| # | Quyết định | Áp dụng ở |
|---|---|---|
| 1 | **Ba chữ ký**: khách hàng mục A · SKYPEC cuối · khách hàng cuối | §7 |
| 2 | Nhiệt độ/KLR **bắt buộc là số hợp lệ**; ngưỡng chỉ **cảnh báo** và **cấu hình được**, chỉ thành lỗi chặn khi nghiệp vụ/KTCL duyệt bằng văn bản | §8.6 |
| 3 | C4 = hai tín hiệu chuẩn là **nội dung hướng dẫn** + ô xác nhận "đã phổ biến/thống nhất"; chỉ lưu `otherSignal` khi dùng phương thức khác; nội dung chuẩn vẫn in đầy đủ | §8.7 |
| 4 | Chưa bảo đảm một xe một tablet → **giữ mã thiết bị ổn định** trong `localNumber`; mã do app quản lý, không dùng Android ID có thể đổi sau reset | §6 |
| 5 | Migration: thu thập APK/DB cũ **và** thêm log `VERSION_CODE` + `PRAGMA user_version`; phải kiểm thử trước phát hành | §12 |
| 6 | **Một bản gốc SKYPEC giữ**; cho phép in `COPY` cho khách khi yêu cầu; PDF đã ký làm sau khi snapshot ổn định; **không tự động in hai bản** | §10 |
| 7 | Trước ký dùng `CANCELLED`, sau ký/in dùng `VOIDED` | §14.1 |

### 14.1 Ngữ nghĩa CANCELLED vs VOIDED

```
DRAFT / A_DONE / B_DONE / C_DONE  →  CANCELLED
SIGNED / PRINTED                  →  VOIDED
```

- `CANCELLED`: phiếu/mẻ hủy **trước khi ký**, không trở thành chứng từ hoàn tất.
- `VOIDED`: phiếu **đã ký hoặc đã in** rồi bị vô hiệu hóa → **bắt buộc** lý do, người thực hiện,
  thời gian, và revision thay thế nếu có.
- `CANCELLED` **không** chuyển được sang `SIGNED`. Muốn làm lại → tạo revision mới.

### 14.2 Ba bổ sung bắt buộc khi triển khai

**a) Invariant check nhiều revision active** (§4)
- DAO có truy vấn phát hiện một mẻ có **> 1** revision chưa `VOIDED`/`CANCELLED`.
- Chạy check khi **mở phiếu** và **trước khi ký**.
- Phát hiện bất thường → **khóa ký/in + ghi log**; **không** tự chọn ngẫu nhiên một revision.
- Cân nhắc SQLite trigger bảo vệ ở tầng DB nếu không làm phức tạp Room schema validation.

**b) `signedSnapshotHash` theo canonical manifest** (§7.4)
Không nối byte tùy ý, **không** hash trực tiếp JSON do Gson sinh (thứ tự field có thể đổi giữa
các phiên bản). Hash một manifest canonical, thứ tự cố định, UTF-8:

```
schemaVersion
uniqueId
refuelItemUniqueId
revisionNumber
localNumber
payloadSha256
customerSectionASignatureSha256
customerFinalSignatureSha256
skypecSignatureSha256
signedAt
```

**c) Backup phải chứng minh khôi phục được** (§5.3)
"Verify ZIP" chưa đủ. Test hoàn thành phải đi hết: export → đọc lại ZIP → validate checksum →
import vào DB thử nghiệm → dựng lại đúng snapshot → **sinh lại bản in giống bản trước export**.
Import trên production có thể cần quyền quản trị, nhưng **format dữ liệu phải hỗ trợ phục hồi**.

## 15. Việc tôi chưa làm và lý do

- Chưa sửa code — chờ phê duyệt.
- Chưa dựng ma trận migration — thiếu dữ liệu ngoài repo (§12.2).
- Chưa chốt khoảng nhiệt độ/KLR — cố tình không bịa số vào tài liệu kỹ thuật.


---

## 16. Trạng thái triển khai

| Bước | Trạng thái | Ghi chú |
|---|---|---|
| 0 — Chốt nghiệp vụ | ✅ xong | §14 |
| 1 — Model + state + validation | 🟢 **được phép làm** | Không phụ thuộc storage |
| 2 — Storage + migration | 🔴 **BỊ CHẶN** | Chỉ bắt đầu khi có kế hoạch migration kiểm thử được (§12) |
| 3–9 | ⏸ chờ bước 2 | |
| Phát hành | 🔴 **BỊ CHẶN** | Không phát hành BM7501 khi chưa chứng minh đường migration từ DB thực địa |

### 16.1 Blocker migration — điều kiện gỡ chặn

1. Có ma trận **app version → DB version** từ kho APK cũ và/hoặc log thực địa.
2. Bản cầu nối có log: `BuildConfig.VERSION_CODE`, tên database, `PRAGMA user_version`,
   thiết bị/variant. **Không log dữ liệu nghiệp vụ.**
3. Có đường migration tới 13 cho mọi DB version còn tồn tại, gồm cả nhóm ≤ 8 đang mồ côi
   (`AppDatabase.java:213` chỉ đăng ký 9→10, 10→11, 11→12).
4. Test bằng database fixture có dữ liệu thật.
5. Chính sách destructive fallback bằng văn bản: không được xóa bảng `BM7501` và thư mục chữ ký.

---

## 17. Phạm vi áp dụng theo bản build (chốt 2026-08-10)

**Quyết định:** BM 75.01 áp dụng cho **tất cả các bản**, nhưng **chỉ chế độ in nhiệt mới có nút In**.

### 17.1 Cờ dùng để phân biệt

App đã có sẵn `BuildConfig.THERMAL_PRINTER` (`app/build.gradle`), không cần thêm cờ mới:

| buildType | `THERMAL_PRINTER` |
|---|---|
| `defaultConfig` | `false` |
| `debug` | `true` |
| `thermal` | `true` |
| `demo` | `false` |
| `demo_thermal` | `true` |
| `release` | `false` |

Đây đúng là cờ đang được dùng cho nút in ở `PrintReceiptActivity:142`, `RefuelPreviewActivity:637`,
`UserBaseActivity:341` — dùng lại cho nhất quán.

### 17.2 Cái gì áp dụng ở đâu

| Thành phần | Mọi bản | Chỉ khi `THERMAL_PRINTER` |
|---|---|---|
| Model, enum, state machine | ✅ | |
| Validation A/B/C, ngưỡng cảnh báo | ✅ | |
| Lưu trữ, revision, CAS, outbox | ✅ | |
| Form nhập 3 bước, autosave | ✅ | |
| Ba chữ ký, snapshot bất biến | ✅ | |
| Backup/export ZIP qua SAF | ✅ | |
| **Nút In / In lại (BẢN SAO)** | | ✅ **chỉ hiện khi bật cờ** |

Nghĩa là: bản không phải chế độ in nhiệt vẫn **nhập, ký, lưu và đồng bộ** phiếu 75.01 đầy đủ,
chỉ không in được tại chỗ.

### 17.3 Cách gán khi dựng UI (bước 4/6)

Ẩn nút, không hiện rồi báo lỗi — giống cách `btnNewExtract` đang làm:

```java
btnPrint7501.setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE);
```

Áp cho **cả** nút In bản gốc và nút In lại (BẢN SAO).

### 17.4 Hệ quả cần chốt: bản ESC/P

`BM7501Printer.createEscpText(...)` đã viết và đã có test, dùng cho máy ESC/P qua `PrintWorker` —
tức là các bản **không** bật `THERMAL_PRINTER`. Nhưng theo quyết định này thì đúng những bản đó lại
**không có nút In**, nên đường ESC/P hiện **không có lối gọi**.

Hai lựa chọn, cần chốt trước bước 6:
1. **Giữ** `createEscpText` như dự phòng (nếu sau này có bản không-nhiệt cần in) — chi phí gần bằng 0,
   đã có test bảo vệ.
2. **Bỏ** để không nuôi mã chết, và xoá luôn phần test tương ứng.

*Đề xuất: giữ ở lựa chọn 1 cho tới khi phát hành thật, rồi rà lại.*

---

## 18. Điểm vào tạm: nút "In thử BM 75.01"

Trong lúc chờ gỡ blocker migration (bước 2) nên chưa có form nhập, đã thêm một đường vào
**tạm thời** để nghiệm thu bản in trên máy thật.

### 18.1 Đường đi

`Hút Jet A-1` → chọn mẻ hút → màn hình **"Xem lại mẻ hút nhiên liệu"** → nút **"In thử BM 75.01"**
trên thanh tiêu đề.

Nút chỉ hiện khi `BuildConfig.THERMAL_PRINTER == true` (§17), tức các bản
`debug`, `thermal`, `demo_thermal`.

### 18.2 Ràng buộc an toàn

- Bản in đóng dấu **`MẪU / SPECIMEN – KHÔNG CÓ GIÁ TRỊ PHÁP LÝ / NOT A LEGAL DOCUMENT`**,
  in **hai lần** (đầu và chân phiếu) phòng khi tờ giấy bị xé rời.
- **Không thu thập dữ liệu, không lưu, không đổi trạng thái** — nên không vi phạm kết luận
  "storage phải đi trước form nhập".
- Phiếu điền sẵn từ mẻ hút đang xem qua `BM7501Prefill`; mục A và mục B để trống.
- Có hộp thoại xác nhận giải thích rõ đây là bản thử trước khi in.

### 18.3 Việc này dùng để nghiệm thu

- Bố cục 3 mục, 3 khối chữ ký, các ghi chú pháp lý.
- Chữ tiếng Việt có dấu (`OPENSANS-RE.TTF` + `^CI28`).
- **Độ dài giấy thật** so với ước tính. Golden test đo `^LL` ≈ 3.400 dots ≈ **42 cm**,
  dài hơn ước tính 32 cm ở §11.3.1 vì bản in giữ nguyên các đoạn ghi chú song ngữ.
  Con số 42 cm cần được xác nhận bằng thước trên ZQ511 trước khi chốt.
- Căn lề ZQ511 vs ZQ520 (`^LH000,0` / `^LH130,0`).

### 18.4 Phải gỡ bỏ khi nào

Khi bước 4 (form nhập) và bước 6 (in bản gốc) hoàn thành, nút này **phải được thay** bằng nút
in thật. Không để bản thử tồn tại song song với bản gốc trên bản phát hành, tránh nhân viên
in nhầm phiếu không có giá trị pháp lý.
