# YÊU CẦU BACKEND — Gộp log tải lên thành 1 file/xe/ngày (`LogController`)

**Gửi:** đội FMS API
**Từ:** đội app tra nạp (Android)
**Ngày:** 2026-08-14
**Trạng thái app:** đã sửa xong và build pass; client gửi lên theo hợp đồng tên file ở mục 3.
**Phạm vi:** `Megatech.FMS.WebAPI.Controllers.LogController` — action `Post2` (`POST api/log2`) và `Get`.

---

## 1. Vấn đề

Thư mục `~/logs/` trên server đang phình rất nhanh và mỗi request upload lại tốn thêm CPU/IO.

Nguyên nhân là `Post2` gộp file theo **nguyên tên client gửi lên**:

```csharp
var baseName = Path.GetFileNameWithoutExtension(file.FileName); // "...fms.log"
var aggregatePath = Path.Combine(dir, baseName);
```

Client (bản cũ) đặt tên có mốc **tới mili-giây** — `truckNo-20260814_093015_123---tabletId--.fms.log.zip`
— nên mỗi lần gửi ra một `baseName` khác nhau, tức **mỗi chunk thành một file riêng**. Ý định
"gộp 1 file/xe/ngày" ghi trong comment của `Post2` không bao giờ đạt được.

Định lượng: app cắt chunk mỗi 60 giây (nay là 3 phút, xem mục 2) → khoảng **720 file/máy/ngày**
với nhịp cũ. 10 xe chạy ca 12 tiếng ⇒ ~7.200 file/ngày, **tất cả nằm chung một thư mục** vì
`Post2` ghi thẳng ở gốc `~/logs/`, không có thư mục con theo ngày.

Hệ quả nặng hơn là đoạn dọn dẹp chạy **trên mỗi request**:

```csharp
foreach (var f in Directory.GetFiles(dir))
    if (File.GetLastWriteTime(f) < DateTime.Now.AddDays(-60))
```

Mỗi upload = liệt kê toàn bộ thư mục + `stat` từng file. Càng nhiều file thì mỗi upload càng
đắt — chi phí tự khuếch đại. Đây nhiều khả năng là nguồn tăng CPU đang thấy trên server.

Ghi chú thêm: comment nói dọn sau 30 ngày nhưng code dùng `AddDays(-60)`.

---

## 2. App đã sửa gì (đã xong, đang chờ server khớp lại)

| Thay đổi | Trước | Sau |
|---|---|---|
| Mốc thời gian trong tên file gửi lên | `yyyyMMdd_HHmmss_SSS` | **`yyyyMMdd`** |
| Nhịp cắt chunk `fms.log` | 60 giây | **3 phút** (hoặc khi đủ 100KB) |
| Nhịp cắt `refuel-anomaly.log` | 60 giây | giữ 60 giây (hoặc khi đủ 20KB) |

Số request giảm còn ~20/máy/giờ. Ngày của chunk lấy từ **thời điểm ghi log**, không phải thời
điểm gửi — chunk cắt lúc 23:59 mà gửi được sau nửa đêm vẫn thuộc về ngày hôm trước.

App **không** thay đổi giao thức: vẫn `POST multipart/form-data` tới `api/log2`, một part tên
`textFile`, nội dung là file `.zip`.

---

## 3. Hợp đồng tên file client gửi lên

```
{truckNo}-{yyyyMMdd}---{tabletId}--.{logType}.zip
```

| Thành phần | Ý nghĩa | Ghi chú |
|---|---|---|
| `truckNo` | Số xe | Do người dùng chọn trong cài đặt; **có thể chứa dấu `-`** |
| `yyyyMMdd` | Ngày **ghi** log | Client cũ gửi `yyyyMMdd_HHmmss_SSS` — xem mục 5 |
| `tabletId` | Định danh máy tính bảng | Dạng `SOFTWARE_ID_<android_id>` hoặc IMEI |
| `logType` | `fms.log` hoặc `refuel-anomaly.log` | Hai loại log tách riêng, không trộn vào nhau |

Ví dụ thực tế:

```
51F-12345-20260814---SOFTWARE_ID_9a2b1c7d4e--.fms.log.zip
51F-12345-20260814---SOFTWARE_ID_9a2b1c7d4e--.refuel-anomaly.log.zip
```

Bên trong `.zip` là **một entry duy nhất**, nội dung text thuần, mỗi dòng đã có sẵn dấu thời
gian dạng `[dd-MM-yyyy HH:mm:ss]`. Tên entry bên trong zip **không có ý nghĩa**, server cứ nối
nội dung là đúng.

---

## 4. Yêu cầu

### 4.1 Gộp theo xe + ngày + loại log

Đích đến của mọi chunk trong cùng một ngày của cùng một xe là **một file duy nhất**. Sang ngày
mới thì tự nhiên rơi vào file mới, không cần thao tác gì thêm.

Đề nghị đặt trong thư mục con theo ngày để việc dọn dẹp không phải quét cả kho:

```
~/logs/2026-08-14/51F-12345.fms.log
~/logs/2026-08-14/51F-12345.refuel-anomaly.log
~/logs/2026-08-15/51F-12345.fms.log          <- sang ngày mới, file mới
```

Khóa gộp là **(truckNo, ngày, logType)**. `tabletId` **không** nằm trong tên file: một xe đổi
tablet giữa ca vẫn phải ra một dòng thời gian liên tục. Xem 4.4 để giữ lại thông tin này.

### 4.2 Không tin tên file client gửi

Tên file đến từ client nên phải parse rồi **tự dựng lại** đường dẫn, không dùng trực tiếp.

- Bỏ mọi ký tự trong `Path.GetInvalidFileNameChars()` khỏi `truckNo` trước khi ghép đường dẫn.
- Kiểm tra `logType` nằm trong danh sách trắng `{ "fms.log", "refuel-anomaly.log" }`; giá trị
  khác thì gom vào `unknown.log` chứ không lấy chuỗi tùy ý từ client làm tên file.
- Không parse được (client lạ, tên sai định dạng) ⇒ ghi vào `~/logs/{ngày nhận}/_unparsed/`
  với tên duy nhất, trả `200`. **Không** trả lỗi, vì lỗi sẽ khiến client giữ chunk và gửi lại vô hạn.

Gợi ý regex, chấp nhận cả định dạng cũ lẫn mới:

```csharp
// truckNo lười (.+?) để dừng ở token ngày đầu tiên; ngày cũ có thể kèm _HHmmss_SSS
static readonly Regex LogName = new Regex(
    @"^(?<truck>.+?)-(?<day>\d{8})(?:_\d{6}_\d{3})?---(?<tablet>.*)--\.(?<type>fms\.log|refuel-anomaly\.log)$",
    RegexOptions.Compiled);
```

Áp lên `Path.GetFileNameWithoutExtension(file.FileName)` (đã bỏ `.zip`).

### 4.3 Dọn dẹp ra khỏi đường request

Vòng `Directory.GetFiles` hiện chạy mỗi lần upload phải bỏ đi. Thay bằng một trong hai:

- Job theo lịch (Hangfire/Windows Task) chạy mỗi ngày một lần, hoặc
- Chạy trong request nhưng **chỉ khi ngày đổi** (giữ một `static DateTime lastCleanupDate`).

Khi đã có thư mục theo ngày thì dọn là `Directory.Delete(dayDir, true)` cho các thư mục quá hạn
— rẻ hơn nhiều so với `stat` từng file. Xin chốt lại **30 hay 60 ngày**; code và comment hiện
đang lệch nhau.

### 4.4 Ghi nhận ranh giới chunk (khuyến nghị)

Vì nhiều tablet có thể cùng ghi vào file của một xe, đề nghị chèn một dòng phân cách trước mỗi
lần append:

```
===== chunk received 2026-08-14 09:33:12 tablet=SOFTWARE_ID_9a2b1c7d4e bytes=41235 =====
```

Giúp truy vết khi phải đọc log sự cố mà không cần tách file theo tablet.

### 4.5 Ghi đồng thời

Hai tablet cùng xe (hoặc chunk gửi liên tiếp) có thể append cùng lúc. Mở với
`FileMode.Append, FileAccess.Write, FileShare.ReadWrite` như hiện tại là chưa đủ để tránh xen
kẽ nội dung. Đề nghị:

- Giải nén ra buffer/temp trước, rồi **append một lần duy nhất**, và
- Khóa theo đường dẫn đích (ví dụ `lock` trên chuỗi đã intern, hoặc `Mutex` đặt tên) quanh đúng
  thao tác append.

### 4.6 Giữ nguyên hợp đồng mã trả về

Đây là phần app phụ thuộc trực tiếp, **xin đừng đổi**:

- `200` ⇒ client **xóa** chunk khỏi máy. Chỉ trả `200` khi dữ liệu đã ghi xuống đĩa xong.
- Khác `200` ⇒ client **giữ** chunk và gửi lại ở phiên sync sau. Zip hỏng thì trả `500` như
  hiện tại là đúng.

Lưu ý hệ quả: nếu server ghi xong nhưng client không nhận được `200` (mạng đứt giữa chừng),
client sẽ gửi lại và đoạn log đó **bị lặp**. Chấp nhận được với log. Nếu muốn khử lặp thì cần
client gửi kèm định danh chunk — việc này cần sửa cả hai phía, xem mục 7.

### 4.7 `Get` phải tìm được file mới

`Get` hiện đã tìm đệ quy nên vẫn chạy được với thư mục theo ngày. Chỉ cần đảm bảo tra cứu theo
tên mới (`51F-12345.fms.log`) trả đúng file, và cân nhắc thêm tham số ngày để không phải quét
`SearchOption.AllDirectories` trên toàn bộ kho log.

---

## 5. Tương thích ngược

Sẽ có một giai đoạn **các tablet chưa cập nhật app vẫn gửi tên kiểu cũ** (`yyyyMMdd_HHmmss_SSS`).
Regex ở 4.2 đã nuốt phần `_HHmmss_SSS` và chỉ lấy 8 ký tự ngày, nên **máy cũ cũng được gộp đúng**
ngay khi server lên bản mới. Không cần ép cập nhật app đồng loạt.

Ngược lại, app bản mới gửi lên server bản cũ thì vẫn chạy, chỉ là mỗi ngày ra một file
(`truckNo-20260814---tabletId--.fms.log`) — vẫn tốt hơn hiện trạng nhiều. Nghĩa là **hai phía
triển khai độc lập được**, không cần canh giờ.

---

## 6. Dữ liệu cũ

Thay đổi này không tự dọn đống file đã sinh ra. Cần **dọn một lần thủ công** trên server, nếu
không thì mọi thao tác liệt kê thư mục `~/logs/` vẫn chậm cho tới khi hết hạn lưu trữ. Có thể
gộp luôn: nhóm các file cũ theo `truckNo` + ngày trong tên rồi nối lại thành một file/xe/ngày,
hoặc đơn giản là archive/xóa theo chính sách lưu trữ đã chốt.

---

## 7. Ngoài phạm vi, nhưng cần biết

**Thiếu xác thực.** `PostList` có `[Authorize]`, còn `Post`, `Post2` và `Get` thì **không**. Ai
biết URL cũng upload được file zip vào server và tải log về. `Get` đã chặn path traversal bằng
`Path.GetFileName`, nhưng vẫn cho tải bất kỳ file log nào theo tên. App đã luôn gửi kèm
`Authorization: bearer <token>` ở mọi request, nên **bật `[Authorize]` cho ba action này không
làm hỏng client**.

**Khử lặp chunk (giai đoạn 2, nếu cần).** Client gửi kèm header `X-Chunk-Id` là tên file
`.pending` gốc (đã duy nhất tới mili-giây); server ghi marker đã nhận và bỏ qua chunk trùng.
Việc này cần sửa cả app lẫn API — báo lại nếu đội API muốn làm, phía app sẽ bổ sung header.

---

## 8. Tiêu chí nghiệm thu

1. Một xe chạy trọn ca 12 tiếng ⇒ đúng **một** file `fms.log` cho ngày đó (thêm một file
   `refuel-anomaly.log` nếu có sự cố), thay vì hàng trăm file.
2. Nội dung file là các chunk nối liền nhau, **đúng thứ tự thời gian**, không mất dòng nào so
   với log trên máy tính bảng.
3. Qua nửa đêm: chunk cắt trước 00:00 nằm ở file ngày hôm trước, chunk sau nằm ở file ngày mới.
4. Tablet gửi bằng **định dạng tên cũ** vẫn được gộp vào đúng file ngày đó.
5. Thời gian xử lý một request upload **không tăng** theo số file đang có trong kho log.
6. Ngắt mạng giữa lúc upload ⇒ chunk còn nguyên trên máy tính bảng và được gửi lại ở phiên sau.

---

## 9. Đầu mối

Phía app: đội Android FMS. Các file liên quan để đối chiếu:

- `app/src/main/java/com/megatech/fms/helpers/LogEntryAPI.java` — dựng tên file, nén, upload
- `app/src/main/java/com/megatech/fms/helpers/Logger.java` — ghi log, cắt chunk, hàng đợi gửi
