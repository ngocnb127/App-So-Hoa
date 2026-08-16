# YÊU CẦU BACKEND — API cho BM 75.01/NLHK (Phiếu yêu cầu hút nhiên liệu)

**Gửi:** đội FMS API
**Từ:** đội app tra nạp (Android)
**Ngày:** 2026-08-10
**Trạng thái app:** đã hoàn thành model/state/validation phía client; chờ API để bật đồng bộ.

---

## 1. Bối cảnh

App Android hiện đã số hóa nghiệp vụ **hút nhiên liệu** (defuel) nhưng mới có phần đo đếm.
Biểu mẫu giấy **BM 75.01/NLHK** còn hai phần chưa có nơi lưu: mục A (khách hàng khai) và
mục B (SKYPEC kiểm tra chất lượng). Backend hiện **chưa có endpoint** cho biểu mẫu này.

Trong lúc chờ, app **lưu cục bộ và in ra giấy bình thường**, dữ liệu xếp hàng ở outbox.
Vì vậy khi API sẵn sàng, backend sẽ nhận **cả những phiếu đã được ký và in từ trước**.
Điều này chi phối toàn bộ các ràng buộc dưới đây.

Quan hệ nghiệp vụ: **một phiếu BM 75.01 ↔ một mẻ hút**.

---

## 2. Bốn ràng buộc bắt buộc

### 2.1 `LocalNumber` là số chứng từ pháp lý chính thức

App sinh số **offline** và **in lên giấy có chữ ký hai bên** trước khi backend nhìn thấy phiếu.

- Backend **bắt buộc chấp nhận** `LocalNumber` do app gửi lên.
- Backend **không được cấp một số pháp lý khác** cho phiếu đã ký/in — sẽ tạo hai danh tính cho
  cùng một chứng từ, không chấp nhận được về mặt chứng từ.
- Nếu backend cần số nội bộ riêng thì trả về ở `ServerNumber`; app lưu để tham chiếu nhưng
  **không in** và **không thay thế** `LocalNumber`.
- Cần **unique constraint trên `LocalNumber`**.

Định dạng: `75-<chi nhánh>-<số xe>-<mã thiết bị>-<yyMMdd>-<seq>`
Ví dụ: `75-NBA-51F12345-A3F1-260810-001`

`<mã thiết bị>` là 4 ký tự hex, có mặt vì một xe có thể dùng nhiều tablet.

### 2.2 Idempotent theo `UniqueId`

App có thể gửi lại cùng một phiếu nhiều lần (mất mạng, retry, cài lại máy).
Gửi trùng `UniqueId` → **cập nhật bản ghi cũ, không tạo bản mới**.

### 2.3 Không ghi đè nội dung phiếu đã ký

Response của backend chỉ được dùng để cập nhật `Id`, `ServerNumber`, trạng thái đồng bộ.
**Không** trả về payload nghiệp vụ để app ghi đè bản local — bản local là bản đã ký, đã in.

### 2.4 Mô hình revision

Một mẻ hút có thể có nhiều revision chứng từ, nhưng **chỉ một revision đang hiệu lực**:

- `RefuelItemUniqueId` + `RevisionNumber` là **cặp unique**.
- `RefuelItemUniqueId` **một mình không unique**.
- Revision mới mang `SupersedesUniqueId` trỏ tới revision bị thay thế.
- Backend nên có ràng buộc/kiểm tra: một `RefuelItemUniqueId` chỉ có một revision với
  `BusinessStatus` khác `VOIDED`/`CANCELLED`.

---

## 3. Endpoint đề nghị

Mô phỏng đúng `api/bm2508` đang chạy để đội backend làm nhanh và app dùng lại `HttpClient` sẵn có.

| Method | Đường dẫn | Mục đích |
|---|---|---|
| `POST` | `api/bm7501/post2` | Gửi phiếu, multipart: JSON + tối đa 3 ảnh chữ ký |
| `GET` | `api/bm7501/get2/{truckId}` | Lấy danh sách phiếu theo xe |
| `GET` | `api/bm7501/{id}/signature/customer-a` | Ảnh chữ ký khách hàng — mục A |
| `GET` | `api/bm7501/{id}/signature/customer-final` | Ảnh chữ ký khách hàng — xác nhận cuối |
| `GET` | `api/bm7501/{id}/signature/skypec` | Ảnh chữ ký đại diện SKYPEC |

**Ba chữ ký, không phải hai** — biểu mẫu yêu cầu khách hàng ký ở mục A (xác nhận lời khai)
và ký lại ở cuối (xác nhận phương án xử lý), cộng chữ ký SKYPEC.

Multipart part names: `json`, `customerSectionASignature`, `customerFinalSignature`,
`skypecSignature`. Ảnh PNG.

Header đã dùng sẵn trong app: `Tablet-Id` (serial tablet), token xác thực như các API hiện hành.

---

## 4. Payload JSON

⚠️ **Quy ước tên trường: PascalCase (UPPER_CAMEL_CASE)** — app dùng
`FieldNamingPolicy.UPPER_CAMEL_CASE`, giống mọi API hiện có. Định dạng ngày:
`yyyy-MM-dd'T'HH:mm:ss`.

```jsonc
{
  "SchemaVersion": 1,
  "UniqueId": "6f0c…",              // khóa của REVISION — idempotency key
  "RefuelItemUniqueId": "9a12…",    // khóa của MẺ HÚT
  "RevisionNumber": 1,
  "SupersedesUniqueId": null,
  "LocalNumber": "75-NBA-51F12345-A3F1-260810-001",
  "ServerNumber": null,             // backend điền khi trả về
  "BusinessStatus": "PRINTED",      // DRAFT|A_DONE|B_DONE|C_DONE|SIGNED|PRINTED|CANCELLED|VOIDED
  "EnteredByUserId": 42,            // nhân viên NHẬP HỘ, khác người khai
  "EnteredByUserName": "…",
  "TabletSerial": "…",
  "Date": "2026-08-10T09:30:00",
  "AirlineId": 3, "AirlineName": "…",
  "AirportId": 1, "AirportName": "…",
  "TruckId": 12,

  // ---- MỤC A: khách hàng khai, nhân viên nhập hộ ----
  "CustomerRepName": "…",
  "CustomerTitle": "…",
  "CustomerTel": "…",
  "CustomerFax": "…",
  "AircraftType": "A321",
  "AircraftReg": "VN-A123",
  "Reason": "LOAD_ADJUSTMENT",      // LOAD_ADJUSTMENT|MAINTENANCE|OTHER
  "ReasonOther": null,              // bắt buộc khi Reason = OTHER
  "TankDrainSampled": true,         // đã xả tất cả thùng lấy mẫu KTCL
  "CustomerMicrobialTestPerformed": "NO",   // YES|NO|UNKNOWN
  "CustomerMicrobialKit": null,     // HY_LITE|MICROB_MONITOR2|FUELSTAT|OTHER — chỉ khi ...Performed = YES
  "CustomerMicrobialKitOther": null,
  "CustomerMicrobialResult": null,  // NORMAL|WARNING|ACTION
  "AdditivePresence": "NONE",       // PRESENT|NONE|UNDETERMINED
  "Additives": [],                  // FSII|BIOCIDE|AQUARIUS_WMA — rỗng khi NONE/UNDETERMINED
  "PrevLocation1": "SGN", "PrevGrade1": "JET A-1",
  "PrevLocation2": "HAN", "PrevGrade2": "Không xác định được",

  // ---- MỤC B: SKYPEC ----
  "Vac": "SATISFY",                 // SATISFY|NOT_SATISFY
  "Cwd": "SATISFY",
  "DensityKgM3": 795.2,             // ⚠️ kg/m3, KHÁC đơn vị kg/l của phiếu hoàn trả
  "ConductivityRequired": false,
  "ConductivityPsM": null,          // bắt buộc khi ConductivityRequired = true
  "ContaminationSuspected": false,
  "CustomerRequestedMicrobial": false,
  "SkypecMicrobialKit": null,
  "SkypecMicrobialResult": null,
  "MicrobialReason": null,

  // ---- MỤC C: xác nhận ----
  "DefuellerTruckNo": "51F-123.45",
  "StartTime": "2026-08-10T09:40:00",
  "EndTime": "2026-08-10T10:05:00",
  "Method": "AIRCRAFT_PUMP",        // AIRCRAFT_PUMP|REFUELLER_PUMP|BOTH
  "SignalsBriefed": true,           // đã phổ biến 2 tín hiệu chuẩn
  "OtherSignal": null,              // chỉ khi dùng phương thức tín hiệu khác
  "ExpectedKg": 3000, "ActualKg": 2980,
  "ActualTempC": 28.5,              // ⚠️ CÓ THỂ ÂM
  "ActualDensityKgM3": 795.2,
  "Gallon": 990, "Liter": 3748,
  "RefuellableWithoutTest": true,
  "Handling": null,                 // STORAGE|SAME_AIRCRAFT|OTHER_AIRCRAFT_SAME_AIRLINE|AUTHORIZE_SKYPEC|REFUEL_DESPITE_ISSUE
  "StorageFrom": null, "StorageTo": null,
  "HandlingNote": null,
  "CustomerRepFinalName": "…",
  "SkypecRepName": "…",

  // ---- chữ ký & audit ----
  "CustomerSectionASignatureSha256": "…",
  "CustomerFinalSignatureSha256": "…",
  "SkypecSignatureSha256": "…",
  "SignedSnapshotHash": "…",
  "SignedAt": "2026-08-10T10:12:00",
  "PrintedAt": "2026-08-10T10:13:00",
  "ReprintCount": 0,
  "CancelledAt": null, "CancelReason": null,
  "VoidedAt": null, "VoidReason": null, "VoidedByUserId": 0
}
```

### 4.1 Ba chỗ dễ làm sai

1. **`DensityKgM3` là kg/m³**, trong khi phiếu hoàn trả hiện hành dùng **kg/l**.
   Chênh nhau 1000 lần. Đừng gộp hai trường này vào một cột.
2. **`ActualTempC` có thể ≤ 0.** Không đặt ràng buộc `> 0`.
3. **`ActualKg` có thể bằng 0** với phiếu `CANCELLED` (mẻ hút hủy/không thành công).

### 4.2 Quy tắc điều kiện (nên kiểm tra ở server để bắt dữ liệu bẩn)

| Điều kiện | Bắt buộc có |
|---|---|
| `Reason = OTHER` | `ReasonOther` |
| `CustomerMicrobialTestPerformed = YES` | `CustomerMicrobialKit` + `CustomerMicrobialResult` |
| `AdditivePresence ∈ {NONE, UNDETERMINED}` | `Additives` phải **rỗng** |
| `AdditivePresence = PRESENT` | `Additives` có ít nhất 1 phần tử |
| `Vac`/`Cwd` = `NOT_SATISFY` **hoặc** `ContaminationSuspected` **hoặc** `CustomerRequestedMicrobial` | `SkypecMicrobialKit` + `SkypecMicrobialResult` + `MicrobialReason` |
| `ConductivityRequired = true` | `ConductivityPsM` |
| Không nạp lại ngay được **hoặc** có vấn đề chất lượng | `Handling` |
| `Handling = STORAGE` | `StorageFrom`, `StorageTo` (`To >= From`) |
| `Handling = REFUEL_DESPITE_ISSUE` | `HandlingNote` |
| `BusinessStatus ∈ {SIGNED, PRINTED}` | đủ 3 chữ ký + `SignedSnapshotHash` |
| `BusinessStatus = VOIDED` | `VoidReason`, `VoidedAt`, `VoidedByUserId` |

Lưu ý điều kiện vi sinh mục B: **không** suy ra từ `TankDrainSampled`. Đây là ba điều kiện độc lập
theo đúng ghi chú trang 2 của biểu mẫu.

### 4.3 `SignedSnapshotHash`

Là SHA-256 (hex thường) của một manifest canonical, các dòng theo **đúng thứ tự này**,
mỗi dòng kết thúc bằng `\n`, mã hóa UTF-8:

```
SchemaVersion
UniqueId
RefuelItemUniqueId
RevisionNumber
LocalNumber
<sha256 của payload đã lưu>
CustomerSectionASignatureSha256
CustomerFinalSignatureSha256
SkypecSignatureSha256
<SignedAt tính bằng mili giây epoch>
```

Trường null ghi thành chuỗi rỗng. Backend chỉ cần **lưu lại** giá trị này để đối chiếu khi cần;
không cần tính lại.

---

## 5. Response mong muốn

```jsonc
{
  "Id": 12345,
  "UniqueId": "6f0c…",          // trả nguyên vẹn
  "LocalNumber": "75-NBA-…",    // trả nguyên vẹn, KHÔNG đổi
  "ServerNumber": "…",          // tùy chọn
  "Success": true,
  "Message": null
}
```

Mã lỗi: `409` khi vi phạm unique (`LocalNumber` trùng của phiếu khác `UniqueId`) — app sẽ hiển thị
cho nhân viên xử lý, **không tự sinh số mới đè lên số đã in**.

---

## 6. Câu hỏi cho đội backend

1. Có thể nhận `LocalNumber` do client sinh làm số chứng từ chính thức không? Nếu quy trình
   backend bắt buộc phải cấp số, cần bàn lại **trước khi** app phát hành, vì số đã in trên giấy có chữ ký.
2. Chọn multipart (`post2`) như `bm2508`, hay muốn dùng base64 trong JSON cho 3 ảnh chữ ký?
3. Có endpoint nhận **phiếu cũ tồn đọng** (đã ký/in từ trước khi API tồn tại) không, hay dùng chung `post2`?
4. Backend có sẵn bảng phụ gia / test kit chuẩn để app đồng bộ danh mục, hay app giữ enum cứng như trên?
5. Thời điểm dự kiến có API để đội app lên lịch bật cờ đồng bộ?

---

## 7. Phía app đã sẵn sàng những gì

| Hạng mục | Trạng thái |
|---|---|
| Model, enum, state machine, validation | ✅ xong, 60 unit test pass |
| Số phiếu `LocalNumber` + canonical hash | ✅ xong |
| Lưu trữ (Room) + outbox | ⏸ chờ gỡ blocker migration nội bộ |
| Form nhập, chữ ký, bản in nhiệt | ⏸ theo sau |
| Bật đồng bộ | ⏸ chờ API này |

Tham chiếu thiết kế đầy đủ: `docs/PLAN-BM7501-HUT-NHIEN-LIEU.md`.
