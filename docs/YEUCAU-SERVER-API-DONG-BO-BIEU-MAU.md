# Yêu cầu bổ sung API — đồng bộ biểu mẫu BM 23.07A, 25.02 – 25.09

**Ngày lập:** 28-09-2026
**Gửi:** đội WebAPI
**Nguồn dữ liệu:** máy ảo Android, app bản 119 (`11.7.1`) nhánh `claude/form-sync-fixes-offline`,
tài khoản `demonv`, xe DEMO-03 (`TruckId = 34`) và DEMO 01 (`TruckId = 118`), sân bay `AirportId = 118`,
server `https://fmsapi.skypec.com.vn/`. Các phiếu thử liệt kê ở mục 8.

---

## 1. Tóm tắt

App vừa sửa xong đợt lỗi đồng bộ biểu mẫu (mất bản sửa, sinh phiếu trùng, phiếu xe này bị ghi đè
bằng phiếu xe khác). Phần còn lại **app không tự khắc phục được**, cần server bổ sung:

| # | Yêu cầu | Biểu mẫu | Ưu tiên |
|---|---|---|---|
| 1 | Lưu và trả về `UniqueId`; POST gửi lại cùng `UniqueId` không được tạo phiếu mới | 23.07A, 25.03, 25.04, 25.05, 25.08 | **Cao** |
| 2 | Dữ liệu sai trả **4xx kèm thông báo**, không trả 500 | 25.03 (đã đo), các biểu mẫu còn lại (cần rà) | **Cao** |
| 3 | Không trả chuỗi có dấu cách thừa ở cuối | 25.02 (đã đo), 25.05 và các cột độ dài cố định khác | Trung bình |
| 4 | Báo cho app biết phiếu đã bị xoá trên web | các biểu mẫu chưa có 410 | Trung bình |
| 5 | `api/bm2508/multipart`: sửa hoặc gỡ | 25.08 | Thấp |

Mục 6 là các câu hỏi cần backend trả lời trước khi app sửa tiếp.

---

## 2. Bối cảnh: vì sao app cần những điều này

App làm việc **ghi trước trên máy, gửi sau**: bấm Lưu là phiếu vào CSDL máy tính bảng và được
đánh dấu chờ gửi; mỗi 30 giây app gửi các phiếu chờ gửi rồi tải danh sách phiếu của xe về.

Hai tình huống xảy ra hằng ngày trên xe:

- **Mất phản hồi.** Gói POST tới server và được lưu, nhưng phản hồi không về tới máy (mất sóng,
  hết thời gian chờ). App không biết phiếu đã lên nên **gửi lại**. Nếu server coi đó là phiếu mới
  (vì `Id = 0`) thì có **hai phiếu giống nhau** trên server.
- **Gửi lại vô hạn.** App không phân biệt được "lỗi tạm" với "dữ liệu sai" nếu server trả 500 cho
  cả hai. Với 5xx app coi là lỗi tạm và gửi lại mãi; phiếu kẹt ở trạng thái chờ gửi, người dùng
  không được báo gì.

BM 25.06 và 25.09 đã theo đúng hợp đồng cần có (mục 3, 4). Đề nghị **đưa các biểu mẫu còn lại về
cùng hợp đồng đó**.

---

## 3. Yêu cầu #1 — `UniqueId` và POST không tạo trùng

**Mức độ: cao.** Đây là nguồn phiếu trùng duy nhất app không chặn được.

### 3.1. Hiện trạng đo được

App **luôn gửi** `UniqueId` (UUID sinh trên máy lúc tạo phiếu) trong mọi lần POST, ví dụ BM 25.03:

```json
{"Date":"2026-09-28T15:22:14", ..., "Id":0, "LocalId":1,
 "UniqueId":"822e6e3f-b1b6-41f1-8071-aba2dfabecdc"}
```

Nhưng khi tải danh sách về, chỉ ba biểu mẫu trả lại `UniqueId`:

| Biểu mẫu | Endpoint GET | Trả `UniqueId` | `LocalId` trả về |
|---|---|---|---|
| 23.07A | `api/checktrucks/{truckId}` | **không** | 0 |
| 25.02 | `api/trucks/fuels?truckId=` | có, khớp máy | 0 |
| 25.03 | `api/bm2503/{truckId}` | **không** | 0 |
| 25.04 | `api/bm2504/{truckId}` | **không** | 0 |
| 25.05 | `api/bm2505/{truckId}` | **không** | 0 |
| 25.06 | `api/bm2506?from=&to=&truckId=` | có, khớp máy | 0 |
| 25.08 | `api/bm2508/{truckId}` | **không** | 0 |
| 25.09 | `api/bm2509?from=&to=&truckId=` | có, khớp máy | 0 |

### 3.2. Yêu cầu

Cho 23.07A, 25.03, 25.04, 25.05, 25.08 (POST `api/checktrucks`, `api/bm2503`, `api/bm2504`,
`api/bm2505`, `api/bm2508/post2`):

1. **Lưu** `UniqueId` do app gửi (cột mới, unique index, cho phép `NULL` với phiếu tạo trên web).
2. **Trả về** `UniqueId` trong response của POST và trong mọi phần tử của GET danh sách.
3. **Idempotent theo `UniqueId`:** POST có `Id = 0` nhưng `UniqueId` đã tồn tại thì **cập nhật
   phiếu đó và trả về `Id` của nó**, không tạo phiếu mới.
4. POST có `Id > 0` mà `UniqueId` khác với phiếu đang có `Id` đó: trả **409** kèm thông báo (dữ liệu
   lẫn giữa hai phiếu), không ghi.

`LocalId` không cần lưu hay trả về: đó là số thứ tự trên **máy đã tạo phiếu**, vô nghĩa với máy
khác. App đã thôi dùng `LocalId` từ server (trước đây chính nó làm phiếu xe này bị ghi đè bằng phiếu
xe khác).

### 3.3. Tiêu chí nghiệm thu

- POST cùng một gói (`Id = 0`, cùng `UniqueId`) hai lần liên tiếp → **cùng một `Id`**, trên web chỉ
  có **một** phiếu.
- GET danh sách → mỗi phiếu có `UniqueId` đúng bằng giá trị app đã gửi lúc tạo.

---

## 4. Yêu cầu #2 — dữ liệu sai trả 4xx, không trả 500

**Mức độ: cao.**

### 4.1. Hiện trạng đo được

Phiếu 25.03 thiếu `TruckId`/`AirportId` (lỗi app, **đã sửa** phía app) được server trả:

```
POST api/bm2503  ->  500
{"Message":"An error has occurred.",
 "ExceptionMessage":"Nullable object must have a value.",
 "ExceptionType":"System.InvalidOperationException",
 "StackTrace":"   at Megatech.FMS.WebAPI.Controllers.BM2503Controller.Post() ..."}
```

App nhận 500 nên coi là lỗi tạm và **gửi lại mỗi 30 giây, không bao giờ dừng**; người dùng không
được báo.

### 4.2. Yêu cầu

Áp dụng cho mọi endpoint POST biểu mẫu, theo đúng bảng mã mà 25.06/25.09 đang dùng:

| Mã | Khi nào | App làm gì |
|---|---|---|
| 200 | Ghi thành công (tạo mới hoặc cập nhật) | Gắn `Id`, xoá cờ chờ gửi |
| 400 | Dữ liệu thiếu/sai (`TruckId`, `AirportId`, trường bắt buộc, sai định dạng) | Dừng gửi lại, hiện `Message` cho người dùng sửa |
| 403 | Không có quyền với phiếu/xe này | Như 400 |
| 404 | `Id > 0` nhưng không có phiếu đó | Như 400 |
| 409 | Xung đột `Id`/`UniqueId` (mục 3.2 ý 4) | Như 400 |
| 410 | Phiếu đã bị xoá trên web | Xoá phiếu khỏi máy |
| 401 | Hết phiên đăng nhập | Gửi lại sau khi đăng nhập lại |
| 5xx | **Chỉ** lỗi hạ tầng thật (CSDL mất kết nối, …) | Gửi lại lượt sau |

Thân phản hồi 4xx: `{"Message": "<câu tiếng Việt người dùng đọc được>"}`. **Không** trả stack trace
ra ngoài (response hiện tại lộ đường dẫn mã nguồn `D:\Sourch So Hoa\api\...`).

### 4.3. Tiêu chí nghiệm thu

- POST 25.03 bỏ trống `TruckId` → **400** kèm `Message`, không phải 500.
- Rà các controller `BM2503`, `BM2504`, `BM2505`, `BM2508`, `CheckTrucks`, `Trucks/Fuels`: mọi
  `.Value` trên kiểu nullable của dữ liệu đầu vào phải được kiểm tra trước và trả 400.

---

## 5. Yêu cầu #3, #4, #5

### 5.1. #3 — chuỗi có dấu cách thừa

**Đo được:** GET `api/trucks/fuels` trả `"AppearanceCheck":"C&B       "` (đệm tới 10 ký tự, kiểu
cột `nchar(10)`). Màn hình so với `"C&B"` nên phiếu tải về hiện sai thành "Khác". App đã tự gọt
trường này ở 25.02 và 25.05, nhưng các cột độ dài cố định khác sẽ gặp lại đúng lỗi này.

**Yêu cầu:** đổi các cột chuỗi `nchar(n)` sang `nvarchar(n)` (hoặc `RTRIM` khi đọc) cho mọi bảng biểu mẫu.

### 5.2. #4 — báo phiếu đã bị xoá trên web

Hiện chỉ 25.02 (`IsDeleted` trong GET) và 25.06/25.09 (POST trả 410) cho app biết phiếu đã bị xoá.
Với 23.07A, 25.03, 25.04, 25.05, 25.08, phiếu xoá trên web **vẫn nằm trên máy** và lần sửa sau trên
máy sẽ gửi lên một phiếu đã xoá.

**Yêu cầu:** với các biểu mẫu trên, một trong hai cách (chọn một, dùng thống nhất):
- GET danh sách trả cả phiếu đã xoá trong khoảng ngày, kèm `IsDeleted = true`; hoặc
- POST vào phiếu đã xoá trả **410**.

### 5.3. #5 — `api/bm2508/multipart`

**Đo được:** gọi với `Id = 622451` hợp lệ vẫn trả
`400 {"success":false,"attachmentsSaved":false,"error":"Missing or invalid BM2508 id"}`.
Chữ ký đã được lưu qua `api/bm2508/post2` (response trả đủ `UrlImageAirline`, `UrlImageSkypec`).

App **đã thôi gọi** endpoint này. Đề nghị: nếu không còn client nào dùng thì gỡ; nếu còn thì sửa
cách đọc `Id` từ phần `BM2508-Data`.

---

## 6. Câu hỏi cần backend trả lời

1. `api/bm2508/post2` với `Id > 0`: là **cập nhật** phiếu đó (app đo được là có — sửa phiếu 622451
   không sinh phiếu mới) hay có trường hợp nào tạo mới? Chữ ký gửi lại có ghi đè ảnh cũ không?
2. Xoá phiếu 25.02 (POST `IsDeleted = true`) có trả lại lượng nhiên liệu tồn kho của xe phía server
   không? App hiện **không** trừ lại tồn kho trên máy khi xoá (đo: tạo phiếu +50, xoá, tồn kho giữ nguyên).
3. POST 23.07A và 25.05 có dùng `Id` gửi lên để cập nhật đúng phiếu không? App đo được là có (sửa
   phiếu 141516 và 1821739 không sinh phiếu mới); cần xác nhận đó là hành vi chính thức.
4. GET danh sách theo `truckId`: có giới hạn khoảng ngày không? Nếu có, bao nhiêu ngày — app cần biết
   để không coi phiếu cũ "biến mất" là đã bị xoá.

---

## 7. Phía app đã làm (để backend không phải xử lý)

- Không ghép hay ghi đè phiếu theo `LocalId` của server; ghép theo `Id` (và `UniqueId` khi server trả).
- Gửi xong chỉ xoá cờ chờ gửi nếu phiếu không bị sửa trong lúc gói đang trên đường.
- Lưu lại phiếu đã có `Id` không còn gửi lên với `Id = 0`.
- Chặn bấm Lưu hai lần; danh sách chỉ hiện phiếu của xe đang cài trên máy.
- 25.03 luôn gửi `TruckId`, `AirportId`; 25.04 sửa phiếu cũ không còn sinh phiếu mới.
- 25.06/25.09 giữ `UniqueId` của máy khi nhận response.

---

## 8. Phiếu thử đã tạo trên server (tài khoản `demonv`)

| Biểu mẫu | `Id` | Xe |
|---|---|---|
| 23.07A | 141516 | DEMO-03 |
| 23.07A | 141523 | DEMO 01 |
| 25.02 | 796458 (còn), 796462 (đã xoá) | DEMO-03 |
| 25.03 | 40 | DEMO-03 |
| 25.04 | 2634 | DEMO-03 |
| 25.05 | 1821739 | DEMO-03 |
| 25.06 | 2 | DEMO-03 |
| 25.08 | 622451 | DEMO-03 |
| 25.09 | 1 | DEMO-03 |

Có thể dùng các phiếu này để thử yêu cầu #1 (POST lại với `Id = 0` và cùng `UniqueId`).
