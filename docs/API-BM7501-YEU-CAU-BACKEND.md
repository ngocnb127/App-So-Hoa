# YÊU CẦU BACKEND — API cho BM 75.01/NLHK (Phiếu yêu cầu hút nhiên liệu)

**Gửi:** đội FMS API
**Từ:** đội app tra nạp (Android)
**Ngày:** 2026-09-23 (bản 2, thay thế bản 2026-08-10)
**Trạng thái app:** màn nhập, chữ ký và bản in nhiệt đã chạy trên máy thật; dữ liệu mới **chỉ
nằm trong máy tablet**. Chờ API để bật đồng bộ.

> **Bổ sung 2026-09-23:** nghiệp vụ "Xuất phiếu" (trạng thái `EXPORTED`, điều kiện đẩy sang
> Omega) nằm ở `docs/API-BM7501-BO-SUNG-XUAT-PHIEU.md` — đọc kèm tài liệu này.

> **Bản này khác bản 2026-08-10 khá nhiều** vì nghiệp vụ đã chốt lại sau khi in thử và đối
> chiếu phiếu giấy thật. Xem §9 để biết đúng những gì đã đổi — đừng dựng bảng theo bản cũ.

---

## 1. Bối cảnh

Biểu mẫu giấy **BM 75.01/NLHK** (phiếu yêu cầu hút nhiên liệu từ tàu bay) đã được số hóa trong
app: nhân viên nhập mục A (hãng khai), mục B (kết quả KTCL của SKYPEC), mục C (hai bên xác
nhận), ký trên tablet rồi in phiếu nhiệt cho hai bên ký tay.

Backend **chưa có endpoint** cho biểu mẫu này, nên mỗi phiếu hiện chỉ tồn tại trên đúng một
tablet. Mất máy hoặc gỡ app là mất phiếu. Đây là lý do của tài liệu này.

Quan hệ nghiệp vụ: **một phiếu BM 75.01 ↔ một mẻ hút** (`RefuelItemUniqueId`).

---

## 2. Năm điểm chốt về nghiệp vụ

### 2.1 Phiếu KHÔNG bị khoá sau khi in

Chủ dự án chốt ngày 2026-09-23: in sai thì sửa rồi in lại, đúng như cách làm trên giấy. App
**không có bước "ký & hoàn tất"**, không đóng băng nội dung, không đánh số bản sao.

Hệ quả cho backend: **cùng một `UniqueId` sẽ được gửi lên nhiều lần với nội dung khác nhau**,
kể cả sau khi phiếu đã in. Lần gửi sau phải **ghi đè** bản cũ chứ không tạo bản mới, và không
được từ chối vì "phiếu đã tồn tại".

### 2.2 Idempotent theo `UniqueId`

`UniqueId` (UUID do app sinh) là khóa idempotency. Gửi trùng → cập nhật bản ghi cũ.
Cần unique constraint trên `UniqueId`.

### 2.3 `LocalNumber` lấy theo số phiếu của mẻ hút

App **không sinh dãy số riêng**. `LocalNumber` chính là `ReceiptNumber` của mẻ hút tương ứng
(một mẻ một phiếu nên hai số trùng nhau là đúng nghiệp vụ, và hiện trường không phải nhớ hai
dãy số). Số được điền khi mẻ hút có số, **bất biến sau đó**.

- Backend nhận nguyên văn, **không cấp số khác**. Cần số nội bộ thì trả ở `ServerNumber`;
  app lưu để tham chiếu, **không in**.
- `LocalNumber` unique **trong phạm vi bảng BM7501**, nhưng **trùng với số phiếu bên bảng phiếu
  hút/hoàn trả** — đó là chủ ý, đừng ép unique chéo bảng.
- Phiếu lập trước khi mẻ hút có số thì `LocalNumber` tạm để trống.

### 2.4 Phiếu gắn với mẻ hút nào, của chuyến bay nào

Đây là phần dễ nối sai nhất, nên mô tả kỹ.

**Cấu trúc dữ liệu bên app:**

```
Chuyến bay (Flight)            — FlightUniqueId (UUID app sinh) / FlightId (id server)
   └── nhiều mẻ (RefuelItem)   — RefuelItemUniqueId (UUID app sinh) / RefuelItemId (id server)
          • được phân công cho MỘT xe: TruckId + số xe
          • có loại: 0 = tra nạp, 1 = HÚT, 2 = test
          • có số phiếu riêng: ReceiptNumber
          └── một phiếu BM 75.01 (chỉ với mẻ loại HÚT)
```

Một chuyến bay có thể có **nhiều mẻ của nhiều xe** (một tàu bay hai xe cùng làm, hoặc hút rồi
nạp lại). Vì vậy **chuyến bay KHÔNG đủ để xác định phiếu** — chỉ mẻ mới đủ.

**Các khoá app gửi kèm mỗi phiếu** (không in lên giấy, biểu mẫu không có ô cho chúng):

| Trường | Ý nghĩa | Khi nào rỗng/0 |
|---|---|---|
| `RefuelItemUniqueId` | **Khoá chính để nối.** UUID của mẻ hút, do app sinh, không đổi suốt đời mẻ | không bao giờ rỗng |
| `RefuelItemId` | Id mẻ hút phía server | `0` khi mẻ hút chưa đồng bộ lên server |
| `FlightUniqueId` | UUID của chuyến | rỗng với chuyến do server cấp từ bản app cũ (app không tự sinh UUID cho chuyến có sẵn) |
| `FlightId` | Id chuyến phía server | `0` với chuyến do nhân viên tự tạo tại sân đỗ, chưa đồng bộ |
| `FlightCode` | Số hiệu chuyến, ví dụ `VN 7561` | rỗng với mẻ không gắn chuyến |
| `TruckId` | Xe được phân công mẻ đó | luôn có |
| `LocalNumber` | = `ReceiptNumber` của chính mẻ hút | rỗng khi mẻ chưa xuất phiếu |

**Thuật toán nối đề nghị, theo đúng thứ tự:**

1. Tìm `RefuelItem` theo `RefuelItemUniqueId`. Trúng → xong. Đây là đường đi đúng của ~99% phiếu.
2. Không thấy, và `RefuelItemId > 0` → tìm theo id đó, rồi **ghi lại** `RefuelItemUniqueId` vào
   mẻ để lần sau khớp ngay.
3. Vẫn không thấy → **lưu phiếu ở trạng thái "chưa nối"**, đừng từ chối. Khi mẻ hút được đẩy
   lên sau, đối chiếu lại bằng `RefuelItemUniqueId`.
4. Chỉ dùng `FlightUniqueId`/`FlightId`/`FlightCode` để **hiển thị và đối soát**, hoặc để báo
   cáo theo chuyến — **không** dùng để suy ra mẻ. Một chuyến nhiều mẻ thì suy kiểu đó sẽ gắn
   phiếu vào nhầm mẻ.

⚠️ **Phiếu có thể lên server TRƯỚC mẻ hút của nó.** Nhân viên nhập phiếu ngay tại sân đỗ; mẻ hút
chỉ được đẩy khi máy có mạng, và hai luồng đồng bộ độc lập nhau. Do đó:

- **Không** đặt khoá ngoại cứng bắt `RefuelItemUniqueId` phải tồn tại lúc nhận phiếu — sẽ mất
  phiếu đúng vào những ca mạng kém, là lúc cần phiếu nhất.
- `RefuelItemId = 0` và `FlightId = 0` nghĩa là "chưa biết id server", **không phải** id hợp lệ.
  Đừng nối vào bản ghi có id 0.
- Một phiếu "chưa nối" vẫn tra cứu được bằng dữ liệu tự mang theo: `AircraftReg`, `AircraftType`,
  `AirlineId`, `AirportId`, `TruckId`, `Date`, `StartTime`/`EndTime`.

**Kiểm tra nên có ở server:**

| Kiểm tra | Kỳ vọng |
|---|---|
| Mẻ nối được có phải loại **HÚT** (type = 1) không | Nếu là loại tra nạp → dữ liệu sai, cần cảnh báo |
| `TruckId` của phiếu so với `TruckId` của mẻ | Phải trùng; lệch nghĩa là phiếu bị gán nhầm mẻ |
| `LocalNumber` so với `ReceiptNumber` của mẻ | Phải trùng khi cả hai đều có |
| Số phiếu còn hiệu lực trên một mẻ | Đúng một (`BusinessStatus <> 'CANCELLED'`) |

Ba kiểm tra đầu **chỉ nên cảnh báo/ghi log**, không chặn nhận phiếu: chúng lệch nhau chủ yếu khi
dữ liệu về không cùng lúc, chặn ở đây là mất chứng từ đã in và đã ký tay.

**Ví dụ một chuyến có ba mẻ, chỉ một mẻ có phiếu 75.01:**

| Mẻ (`RefuelItemUniqueId`) | Loại | Xe | `ReceiptNumber` | Phiếu 75.01 |
|---|---|---|---|---|
| `9a12…` | HÚT (1) | DEMO 02 | `261A1E5` | ✅ `UniqueId = 6f0c…`, `LocalNumber = 261A1E5` |
| `4b77…` | Tra nạp (0) | DEMO 02 | `261A1E6` | — |
| `c301…` | Tra nạp (0) | DEMO 07 | `261A1F0` | — |

Cả ba mẻ cùng `FlightUniqueId`/`FlightId`/`FlightCode = VN 7561`. Nếu nối phiếu theo chuyến thì
có ba ứng viên và không có cách chọn đúng; nối theo `RefuelItemUniqueId = 9a12…` thì chỉ có một.

**Trường hợp mẻ bị mở lại / chia cho xe khác:** mẻ giữ nguyên `RefuelItemUniqueId`, nên phiếu
vẫn bám đúng mẻ đó. Nếu nghiệp vụ tách thành mẻ mới (uniqueId mới), phiếu cũ vẫn thuộc mẻ cũ —
đúng ý, vì tờ giấy đã in ra gắn với lần hút đó.

### 2.5 Mô hình revision

Một mẻ hút có thể có nhiều revision chứng từ, chỉ một revision đang hiệu lực:

- `RefuelItemUniqueId` + `RevisionNumber` là **cặp unique**; `RefuelItemUniqueId` một mình
  **không** unique (xem §2.4 về cách nối).
- Revision mới mang `SupersedesUniqueId` trỏ tới revision bị thay thế.
- Một `RefuelItemUniqueId` chỉ được có một revision với `BusinessStatus <> 'CANCELLED'`.

Hiện app chưa có nút tạo revision (sửa trực tiếp là đủ), nhưng cấu trúc dữ liệu giữ nguyên để
sau này thêm không phải đổi schema server.

---

## 3. Endpoint đề nghị

Dựng theo đúng `api/bm2508/post2` đang chạy, để app dùng lại lối gọi sẵn có trong `HttpClient`.

| Method | Đường dẫn | Mục đích |
|---|---|---|
| `POST` | `api/bm7501/post2` | Gửi/cập nhật phiếu — multipart: JSON + tối đa 2 ảnh chữ ký |
| `GET` | `api/bm7501/get2/{truckId}` | Lấy danh sách phiếu theo xe (khôi phục khi đổi/cài lại máy) |
| `GET` | `api/bm7501/{id}/signature/{slot}` | Đọc lại ảnh chữ ký — xem §5 |

**Multipart part names** (giống `bm2508/post2`):

| Part | Nội dung | Bắt buộc |
|---|---|---|
| `model` | JSON của phiếu (§4), `application/json; charset=utf-8` | có |
| `signSkypec` | Ảnh chữ ký đại diện SKYPEC (JPEG 300×200, xem §5) | khi đã ký |
| `signCustomer` | Ảnh chữ ký đại diện khách hàng (JPEG 300×200, xem §5) | khi đã ký |

**Đúng hai chữ ký.** Biểu mẫu giấy có ô "Name & signature" ở mục A nhưng thực tế hãng không ký
ở đó (chốt 2026-09-23), nên app chỉ ghi họ tên ở mục A và lấy hai chữ ký ở cuối phiếu.

Header: `Authorization: Bearer <token>` như các API hiện hành.

---

## 4. Payload JSON

⚠️ **Tên trường PascalCase** (`FieldNamingPolicy.UPPER_CAMEL_CASE`), ngày giờ
`yyyy-MM-dd'T'HH:mm:ss` — giống mọi API hiện có của app.

```jsonc
{
  "SchemaVersion": 1,
  "UniqueId": "6f0c…",              // khóa idempotency của phiếu
  "RefuelItemUniqueId": "9a12…",    // khóa của MẺ HÚT — xem §2.4
  "RefuelItemId": 4455,             // id mẻ hút phía server, 0 nếu mẻ chưa đồng bộ
  "FlightUniqueId": "c7b1…",        // khóa của CHUYẾN BAY
  "FlightId": 778,                  // id chuyến phía server, 0 nếu chưa có
  "FlightCode": "VN 7561",
  "RevisionNumber": 1,
  "SupersedesUniqueId": null,
  "LocalNumber": "261A1E5",         // = số phiếu của mẻ hút; có thể null khi mẻ chưa có số
  "ServerNumber": null,             // backend điền khi trả về
  "BusinessStatus": "DRAFT",        // DRAFT | EXPORTED | CANCELLED — xem 4.3 và tài liệu bổ sung
  "TabletSerial": "R52XA0AYEQZ",    // app điền lúc gửi
  "EnteredByUserId": 42,            // nhân viên NHẬP HỘ, khác người khai ở mục A
  "EnteredByUserName": "Nguyen Van B",
  "TruckId": 12,
  "Date": "2026-09-23T09:30:00",    // ngày giờ khách hàng khai (mục A)

  "AirlineId": 3,  "AirlineName": "Tổng công ty Hàng không Việt Nam - CTCP",
  "AirportId": 1,  "AirportName": "TÂN SƠN NHẤT",

  // ---- MỤC A — hãng khai, nhân viên nhập hộ ----
  "CustomerRepName": "Nguyen Ba Ngoc",
  "CustomerTitle": "Nhân viên kỹ thuật",
  "CustomerTel": "0964173188",
  "CustomerFax": null,
  "AircraftType": "A321",
  "AircraftReg": "VNA514",
  "Reason": "MAINTENANCE",          // LOAD_ADJUSTMENT | MAINTENANCE | OTHER
  "ReasonOther": null,              // bắt buộc khi Reason = OTHER
  "TankDrainSampled": true,         // mục A.1 — đã xả tất cả thùng lấy mẫu KTCL
  "CustomerMicrobialTestPerformed": "NO",  // YES | NO  (suy từ hai trường dưới)
  "CustomerMicrobialKit": null,     // HY_LITE | MICROB_MONITOR2 | FUELSTAT | OTHER
  "CustomerMicrobialKitOther": null,
  "CustomerMicrobialResult": null,  // NORMAL | WARNING | ACTION
  "AdditivePresence": "NONE",       // PRESENT | NONE | UNDETERMINED
  "Additives": [],                  // FSII | BIOCIDE | AQUARIUS_WMA — rỗng khi NONE/UNDETERMINED
  "PrevLocation1": "PXU", "PrevGrade1": "JET A-1",
  "PrevLocation2": "SGN", "PrevGrade2": "JET A-1",

  // ---- MỤC B — SKYPEC ----
  "Vac": "SATISFY",                 // SATISFY | NOT_SATISFY
  "Cwd": "SATISFY",
  "DensityKgM3": 795.2,             // ⚠️ kg/m3 — KHÁC đơn vị kg/l của phiếu hoàn trả
  "ConductivityPsM": null,          // "nếu yêu cầu"; null = không đo
  "SkypecMicrobialKit": null,       // HY_LITE | MICROB_MONITOR2 | FUELSTAT
  "SkypecMicrobialResult": null,    // NORMAL | WARNING | ACTION

  // ---- MỤC C — hai bên xác nhận ----
  "DefuellerTruckNo": "51F-123.45",
  "StartTime": "2026-09-23T09:40:00",
  "EndTime": "2026-09-23T10:05:00",
  "Method": "AIRCRAFT_PUMP",        // AIRCRAFT_PUMP | REFUELLER_PUMP | BOTH
  "SignalThumbUp": true,            // tín hiệu chuẩn 1 (giơ ngón cái)
  "SignalCrossArms": true,          // tín hiệu chuẩn 2 (giơ chéo hai tay)
  "SignalsBriefed": true,           // cờ cũ, = SignalThumbUp || SignalCrossArms
  "OtherSignal": null,
  "ExpectedKg": 3000,
  "ActualKg": 2980,
  "ActualTempC": 28.5,              // ⚠️ CÓ THỂ ÂM
  "ActualDensityKgM3": 795.2,
  "Gallon": 990,
  "Liter": 3748,
  "RefuellableWithoutTest": true,   // mục C.1
  "Handling": null,                 // STORAGE | SAME_AIRCRAFT | OTHER_AIRCRAFT_SAME_AIRLINE
                                    // | AUTHORIZE_SKYPEC | REFUEL_DESPITE_ISSUE
  "StorageFrom": null, "StorageTo": null,
  "HandlingNote": null,
  "SkypecRepName": "Tran Van B",    // họ tên đại diện SKYPEC; đại diện hãng lấy ở CustomerRepName

  // ---- chữ ký (ảnh gửi ở part riêng, xem §5) ----
  "SkypecSignatureSha256": "…",         // SHA-256 (hex thường) của đúng file gửi kèm
  "CustomerFinalSignatureSha256": "…",

  // ---- huỷ phiếu ----
  "CancelledAt": null,
  "CancelReason": null
}
```

### 4.1 Ba chỗ dễ làm sai

1. **`DensityKgM3` / `ActualDensityKgM3` là kg/m³**, còn phiếu hoàn trả hiện hành dùng **kg/l**
   — chênh nhau 1000 lần. Đừng gộp vào cùng một cột với bảng phiếu hoàn trả.
2. **`ActualTempC` có thể ≤ 0.** Không đặt ràng buộc `> 0`.
3. **Mọi trường mục A/B/C đều có thể null** khi nhân viên còn đang nhập dở. Đừng đặt `NOT NULL`
   ngoài nhóm khóa (`UniqueId`, `RefuelItemUniqueId`, `RevisionNumber`, `TruckId`).

### 4.2 Quy tắc điều kiện (app đã kiểm, server nên kiểm lại để bắt dữ liệu bẩn)

| Điều kiện | Bắt buộc có |
|---|---|
| `Reason = OTHER` | `ReasonOther` |
| Có `CustomerMicrobialKit` **hoặc** `CustomerMicrobialResult` | phải có **cả hai** |
| `CustomerMicrobialKit = OTHER` | `CustomerMicrobialKitOther` |
| `AdditivePresence ∈ {NONE, UNDETERMINED}` | `Additives` phải **rỗng** |
| `AdditivePresence = PRESENT` | `Additives` có ít nhất 1 phần tử |
| `Vac` hoặc `Cwd` = `NOT_SATISFY` | `SkypecMicrobialKit` + `SkypecMicrobialResult` |
| Có `SkypecMicrobialKit` **hoặc** `SkypecMicrobialResult` | phải có **cả hai** |
| `RefuellableWithoutTest = false` **hoặc** nhiên liệu có vấn đề chất lượng¹ | `Handling` |
| `Handling = STORAGE` | `StorageFrom`, `StorageTo` (`To >= From`) |
| `Handling = REFUEL_DESPITE_ISSUE` | `HandlingNote` |
| `BusinessStatus = CANCELLED` | `CancelledAt`, `CancelReason` |

¹ "có vấn đề chất lượng" = `Vac`/`Cwd` = `NOT_SATISFY`, hoặc `SkypecMicrobialResult` ∈
{`WARNING`, `ACTION`}.

**Không** suy điều kiện vi sinh mục B từ `TankDrainSampled` — hai việc khác nhau.

### 4.3 `BusinessStatus`

Phiếu lập từ bản app hiện tại chỉ có **`DRAFT`** (đang dùng) hoặc **`CANCELLED`** (đã huỷ).

Một số tablet đã dùng bản thử nghiệm trước đó còn giữ phiếu ở `A_DONE`, `B_DONE`, `C_DONE`,
`SIGNED`, `PRINTED`, `VOIDED`. Cột nên để **chuỗi tự do** (hoặc enum có đủ 8 giá trị trên) để
những phiếu đó vẫn đẩy lên được; nghiệp vụ chỉ cần phân biệt "đã huỷ" với "còn hiệu lực".

---

## 5. Ảnh chữ ký — lưu và đọc lại

Đây là phần **quan trọng nhất về mặt chứng từ**: sau khi tablet bị gỡ app hoặc hỏng, ảnh chữ ký
chỉ còn ở server.

### 5.1 App tạo và giữ ảnh thế nào

| Hạng mục | Giá trị |
|---|---|
| Kích thước | **300 × 200 px**, nét đen trên nền trắng |
| Định dạng | **JPEG**, chất lượng 90 (khoảng 5–15 KB/ảnh) |
| Nơi lưu trên máy | `getFilesDir()/bm7501/<UniqueId>/<slot>.jpg` — vùng riêng của app |
| `<slot>` | `skypec`, `customer-final` |
| Checksum | SHA-256 của **đúng file đó**, hex thường, lưu kèm trong phiếu |

Ảnh ký được chép từ file tạm vào vùng riêng của app **ngay khi ký xong**, rồi xoá file tạm —
chứng từ chỉ tồn tại ở một nơi. Vùng này **mất khi gỡ app hoặc xoá dữ liệu app**, nên bản trên
server là bản lưu lâu dài duy nhất.

Cùng bộ ảnh này được nạp vào máy in Zebra để in lên phiếu; chữ ký nào chưa có thì bản in chừa
chỗ ký tay.

### 5.2 Server cần lưu gì

1. **File ảnh** của từng slot, gắn với `UniqueId` của phiếu.
2. **`…SignatureSha256`** gửi kèm trong JSON — lưu nguyên văn để sau này đối chiếu xem file có
   bị thay không. Server **không cần** tự tính lại; nếu có tính để kiểm tra thì phải tính trên
   **đúng byte đã nhận**, đừng nén lại hay đổi định dạng ảnh trước khi băm, vì làm vậy checksum
   sẽ không bao giờ khớp nữa.
3. **Đừng resize, đừng nén lại, đừng chuyển sang PNG/WebP.** Ảnh nhỏ sẵn, và mọi biến đổi đều
   phá checksum.

### 5.3 Cập nhật và xoá

- Phiếu sửa được sau khi in, nên nhân viên có thể **ký lại**: lần gửi sau mang ảnh mới và
  checksum mới cho cùng `UniqueId` + slot → **ghi đè** ảnh cũ của slot đó.
- Nếu một lần gửi **không kèm** part ảnh của một slot: giữ nguyên ảnh đã có, **không xoá**.
  App chỉ gửi ảnh khi có ảnh, không có cách nào báo "hãy xoá chữ ký".
- Phiếu `CANCELLED` vẫn giữ ảnh — là chứng từ đã huỷ, không phải rác.

### 5.4 Endpoint đọc lại (cần cho việc đổi/cài lại máy)

| Method | Đường dẫn | Trả về |
|---|---|---|
| `GET` | `api/bm7501/{id}/signature/skypec` | JPEG, `image/jpeg` |
| `GET` | `api/bm7501/{id}/signature/customer-final` | JPEG, `image/jpeg` |

`404` khi slot đó chưa có chữ ký. Nếu backend thích trả link thay vì file, thì thêm hai trường
`SkypecSignatureUrl` / `CustomerFinalSignatureUrl` vào response của `get2` — app tải theo link
cũng được, chỉ cần chốt một trong hai cách.

Lưu ý: JSON **không mang đường dẫn file**. Đường dẫn trong máy (`/data/.../bm7501/…`) chỉ có
nghĩa với đúng tablet đó nên app không gửi lên; server định danh ảnh bằng `UniqueId` + slot.

---

## 6. Response mong muốn

```jsonc
{
  "Id": 12345,
  "UniqueId": "6f0c…",          // trả nguyên vẹn
  "LocalNumber": "261A1E5",     // trả nguyên vẹn, KHÔNG đổi
  "ServerNumber": null,         // tùy chọn
  "Success": true,
  "Message": null
}
```

App chỉ lấy `Id`, `ServerNumber` và trạng thái thành công. **Không** trả payload nghiệp vụ để
app ghi đè bản local: bản trên tablet là bản nhân viên đang sửa.

Mã lỗi: `409` khi `LocalNumber` trùng của một `UniqueId` khác — app hiện thông báo cho nhân
viên, **không tự đổi số đã in**.

---

## 7. Phía app còn phải làm để bật đồng bộ

| Hạng mục | Trạng thái |
|---|---|
| Model, validation, màn nhập, chữ ký, bản in nhiệt | ✅ xong, chạy trên máy thật |
| Lưu cục bộ (Room, bảng `BM7501`) + cột trạng thái đồng bộ | ✅ xong |
| `HttpClient.postBM7501Post2(...)` | ✅ xong (2026-09-23, theo `API-BM7501-PHAN-HOI-BACKEND.md`) |
| Outbox: đẩy phiếu chưa đồng bộ, retry khi có mạng | ✅ xong — chạy trong vòng đồng bộ chung, task `bm7501` |
| Khôi phục khi đổi/cài lại máy (`get2` + tải 2 ảnh chữ ký) | ⏸ chưa làm |
| Dọn phiếu cũ (`DataRetention`) | ⏸ hiện chỉ xoá phiếu đã `SYNCED`, nên chưa xoá gì |

**Thời điểm app gửi (đã làm):** phiếu vào hàng đợi ngay khi nội dung được ghi (kể cả autosave),
và vòng đồng bộ chung đẩy cả hàng đợi mỗi lượt. Không gửi theo từng phím gõ — autosave chỉ đổi
cờ trong máy, việc gửi do vòng đồng bộ quyết định.

---

## 8. Câu hỏi cho đội backend

1. Nhận `LocalNumber` do app gửi (= số phiếu của mẻ hút) làm số chứng từ chính thức được không?
2. Dùng multipart như `bm2508/post2`, hay muốn base64 hai ảnh chữ ký trong JSON?
3. `GET api/bm7501/get2/{truckId}` trả theo xe có đủ không, hay cần lọc thêm theo khoảng ngày?
4. Ảnh chữ ký (§5): app gửi kèm ở **mọi** lần cập nhật, hay chỉ khi checksum đổi? Và backend
   muốn trả ảnh về dạng file (`GET …/signature/<slot>`) hay dạng link trong `get2`?
5. Thời điểm dự kiến có API, để đội app lên lịch bật đồng bộ?

---

## 9. Đổi gì so với bản 2026-08-10

Bản cũ viết khi app mới có model; sau khi in thử và đối chiếu phiếu giấy thật, nghiệp vụ chốt
lại như sau — **bảng dữ liệu server nên dựng theo bản này**:

| Bản 2026-08-10 | Bản này | Vì sao |
|---|---|---|
| 3 chữ ký (mục A + 2 cuối phiếu) | **2 chữ ký** cuối phiếu | Thực tế hãng không ký ở mục A |
| `BusinessStatus` 8 mức, có ký/khoá | **DRAFT hoặc CANCELLED** | Bỏ bước ký & khoá; in lại thoải mái |
| `SignedSnapshotHash`, `SignedAt`, `PrintedAt`, `ReprintCount` | **bỏ** | Không còn bản "đóng băng" để băm |
| `LocalNumber` dãy số riêng `75-NBA-…` | **= số phiếu của mẻ hút** | Hiện trường không phải nhớ hai dãy số |
| `ConductivityRequired` | **bỏ** — có `ConductivityPsM` nghĩa là có đo | Biểu mẫu chỉ ghi "(nếu yêu cầu)" |
| `ContaminationSuspected`, `CustomerRequestedMicrobial`, `MicrobialReason` | **bỏ** | Biểu mẫu không có ô nào để khai |
| `CustomerRepFinalName` | **bỏ** | Họ tên đã khai ở mục A, không hỏi lại |
| — | **thêm** `SignalThumbUp`, `SignalCrossArms` | Biểu mẫu có hai ô tín hiệu riêng |
| — | **thêm** `RefuelItemId`, `FlightUniqueId`, `FlightId`, `FlightCode` | Bản cũ chỉ có `RefuelItemUniqueId`; server cần nối được phiếu vào chuyến (§2.4) |

Tham chiếu thiết kế: `docs/PLAN-BM7501-HUT-NHIEN-LIEU.md` (một số mục của tài liệu đó cũng đã
cũ theo đúng bảng trên).
