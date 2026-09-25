# Giờ bắt đầu tra nạp bị ghi sai — phần cần điều tra ở phía server

**Ngày lập:** 26-08-2026
**Người nhận:** nhóm Web API (FMS server)
**Trạng thái:** phía app đã xác định được cơ chế và đang vá; cần server xác nhận **một** mắt xích
mà chỉ server nhìn thấy được.

---

## 1. Sự việc

Chuyến `FlightId = 1284485` (VJ336, PQC-SGN, 26-08-2026) có giờ bắt đầu tra nạp sai trên server.

Phiếu giấy đã in — số `39070DTR`, xe PQC 32007 — đối chiếu với bản ghi server:

| Trường | Phiếu giấy | Server (`RefuelItems.Id = 2120355`) | |
|---|---|---|---|
| Giờ bắt đầu | **07:47 26/08** | **25/08/2026 18:41:14** | ✗ lệch 13h11m về hôm trước |
| Giờ kết thúc | 07:52 26/08 | 26/08/2026 07:52:47 | ✓ |
| Đồng hồ đầu | 10 952 310 | 10 952 310 | ✓ |
| Đồng hồ cuối | 10 952 477 | 10 952 477 | ✓ |
| USG | 167 | 167 | ✓ |
| Lít | 632 | 632 | ✓ |
| Kg | 495 | 495 | ✓ |

**Chỉ đúng một trường sai.** Mọi con số đo đếm đều khớp tuyệt đối. Đây không phải sự cố mất
dữ liệu hay lệch đồng bộ diện rộng — nó là một trường bị ghi đè bằng giá trị không phải giờ đo.

---

## 2. Bằng chứng quyết định

Lấy toàn bộ 4 mẻ ở Phú Quốc trong ngày 26-08-2026:

| Mẻ | Chuyến | Giờ bắt đầu | Giờ kết thúc | Xe | Thời lượng bơm |
|---|---|---|---|---|---|
| 2120355 | VJ336 | **25/08 18:41:14** | 26/08 07:52:47 | PQC 32007 | 791,6 phút |
| 2120356 | VJ450 | **25/08 18:41:14** | 26/08 08:57:13 | PQC 32007 | 856,0 phút |
| 2120358 | VN1822 | **25/08 18:41:14** | 26/08 09:40:15 | PQC 32007 | 899,0 phút |
| 2120357 | VJ718 | 26/08 09:13:44 | 26/08 09:22:34 | PQC **32006** | 8,8 phút ✓ |

**Ba mẻ khác chuyến, khác giờ kết thúc, trùng giờ bắt đầu đến từng giây.**

Ba mẻ độc lập không thể cùng bắt đầu bơm đúng một giây. Đây là dấu vết của **một lần lấy
thời gian hệ thống duy nhất được đóng lên mọi bản ghi trong cùng một lượt xử lý**.

Đối chứng trong cùng ngày, cùng sân bay: mẻ trên xe **32006** hoàn toàn bình thường. Phạm vi
sự cố là **một thiết bị**, không phải sân bay, không phải toàn hệ thống.

So sánh với các sân bay khác cùng ngày 26-08 (trung vị thời lượng bơm):

```
PQC   4 mẻ   trung vị 791,6 phút   ← bất thường
HAN  78 mẻ   trung vị  10,8 phút
SGN  71 mẻ   trung vị  11,0 phút
DAD  18 mẻ   trung vị   7,4 phút
```

---

## 3. Cơ chế phía app — đã xác định, đang vá

Model `RefuelItemData` của app khởi tạo hai trường giờ bằng thời điểm dựng đối tượng:

```java
private Date endTime = new Date();
private Date startTime = new Date();
```

Gson **chỉ ghi đè field khi JSON có khoá tương ứng**. Khoá vắng mặt thì field giữ nguyên giá
trị khởi tạo — tức đúng thời điểm app parse gói tin.

`18:41:14 ngày 25/08` là lúc máy tính bảng của xe 32007 kéo danh sách chuyến về, tối trước ca.

Phía app đang hợp nhất mọi đường giải mã vào một parser an toàn: khoá giờ vắng mặt được
chuyển thành `null` thay vì để model tự điền.

---

## 4. ⚠️ Mắt xích cần server xác nhận

Đây là phần chính của tài liệu này.

Cơ chế ở mục 3 **chỉ kích hoạt được khi JSON gửi xuống app thiếu khoá `StartTime`**. Nếu
payload có khoá đó — dù giá trị là gì — Gson sẽ ghi đè và giờ bịa không thể xuất hiện.

> **Vậy việc giờ bịa đã xuất hiện là bằng chứng gián tiếp rằng có ít nhất một endpoint đang
> trả về gói tin THIẾU trường `StartTime`.**

Cần server trả lời chính xác:

### 4.1. Endpoint `GET /api/refuels/modified` có trả `StartTime` không?

App dùng hai đường nhận dữ liệu khác nhau:

| Đường | Dùng khi |
|---|---|
| `GET /api/refuels/?uniqueId=…` | mở chi tiết một mẻ |
| `GET /api/refuels/modified?type=…&lastModified=…` | đồng bộ danh sách định kỳ |

Nghi vấn: hai endpoint dùng **DTO hoặc serializer khác nhau**, và bản dành cho `/modified`
bỏ sót trường giờ. Đề nghị kiểm tra và trả lời:

- Hai endpoint có dùng chung một DTO không? Nếu không, khác nhau ở những trường nào?
- `/modified` có `StartTime` và `EndTime` trong payload không?
- Có cấu hình bỏ trường `null` khi serialize không
  (`NullValueHandling.Ignore`, `DefaultIgnoreCondition = WhenWritingNull`)? Nếu có thì mẻ
  chưa bắt đầu sẽ **không có khoá `StartTime`** trong JSON — và đó chính là điều kiện đủ để
  lỗi xảy ra.

### 4.2. Giá trị sentinel `9999-12-31` được xử lý thế nào khi serialize?

Theo catalog dữ liệu, `RefuelItems.StartTime` dùng sentinel `9999-12-31` cho mẻ chưa hoàn
thành — **41.824 dòng** trên toàn bảng.

Giả thuyết đáng kiểm tra nhất: **serializer bỏ trường khi giá trị là sentinel** (hoặc quy đổi
sentinel thành `null` rồi bỏ vì cấu hình bỏ null). Nếu đúng, chuỗi sự kiện khép kín:

```
Web phân xe tối 25/08
   → RefuelItems.StartTime = 9999-12-31 (chưa bơm)
   → GET /modified bỏ khoá StartTime khỏi JSON
   → app parse lúc 18:41:14, model tự điền new Date()
   → app đẩy 18:41:14 lên server
   → server ghi nhận 18:41:14 làm giờ bắt đầu
```

Xin xác nhận hoặc bác bỏ giả thuyết này bằng **payload thật**: xin một bản JSON nguyên văn mà
`/modified` trả về cho một mẻ **chưa bắt đầu bơm**.

### 4.3. Vì sao giờ đúng 07:47 không thay được giờ sai?

Đây là lớp thứ hai, độc lập với mục 4.1–4.2.

App đã ghi giờ bắt đầu thật lúc 07:47 — **phiếu giấy in ra chứng minh điều đó**. Nhưng bản
ghi server vẫn giữ 18:41:14. Nghĩa là bản cập nhật từ app **không được chấp nhận**.

Log thiết bị ngày 25-08 cho thấy hiện tượng này lặp có hệ thống. Một mẻ bị từ chối **35 lần
liên tiếp trong 9 tiếng**, gói tin giống hệt nhau từng ký tự:

```
[POST_EXCHANGE] id=2118887 seq=11 baseSeq=11 amount=7042 start=70449767 end=70456809
   result=CONFLICT:PAYLOAD_MISMATCH
   endTime(app gửi 24/08 06:40:12  →  server giữ 24/08 17:11:57)
```

Trong một ngày, trên một xe: **5 mẻ, 90 lượt từ chối**. `ServerRevision` bị đẩy lên vô ích —
riêng mẻ 2118887 từ **17 lên 51**, tức 34 lượt ghi không mang thay đổi nào.

Cần server trả lời:

- Quy tắc `timeLocked` (hoặc cơ chế tương đương) đang khoá `StartTime`/`EndTime` theo điều
  kiện nào? Sau khi `Status = DONE`? Sau khi có `ReceiptNumber`? Sau một mốc thời gian?
- Khi từ chối, server có trả về **lý do tường minh** không? Hiện app chỉ suy ra được là "bị
  từ chối" bằng cách đối chiếu giá trị trong response, nên không phân biệt được
  *server chủ động từ chối* với *lỗi mạng/ghi hụt*.
- Có đường nào để sửa giờ hợp lệ không — endpoint riêng, hay cờ cho phép ghi đè kèm audit?
  Hiện tại nhân viên sửa giờ trên máy, lưu được tại máy, nhưng **không bao giờ lên tới
  server** — và không có thông báo nào cho biết việc sửa đã bị huỷ.

---

## 5. Việc cần làm ngay, không chờ điều tra

Ba bản ghi dưới đây đang sai và **app không tự sửa được** chừng nào mục 4.3 chưa xử lý.
Giá trị đúng lấy từ phiếu giấy đã in:

| Mẻ | Chuyến | Giờ bắt đầu hiện tại | Giờ bắt đầu đúng |
|---|---|---|---|
| 2120355 | VJ336 | 25/08 18:41:14 | **26/08 07:47** (phiếu 39070DTR) |
| 2120356 | VJ450 | 25/08 18:41:14 | cần lấy từ phiếu tương ứng |
| 2120358 | VN1822 | 25/08 18:41:14 | cần lấy từ phiếu tương ứng |

---

## 6. Rà quét diện rộng — vân tay để tìm

Bản ghi dính lỗi có dấu hiệu nhận dạng rất rõ: **nhiều mẻ khác chuyến nhưng trùng
`StartTime` đến từng giây**, và thường trùng luôn theo từng xe.

Truy vấn khoanh vùng:

```sql
-- Các mốc StartTime bị dùng lại cho nhiều mẻ khác chuyến trong cùng một ngày
SELECT  r.StartTime,
        SoMe        = COUNT(*),
        SoChuyen    = COUNT(DISTINCT r.FlightId),
        SoXe        = COUNT(DISTINCT r.TruckId),
        BomLauNhat  = MAX(DATEDIFF(MINUTE, r.StartTime, r.EndTime))
FROM    RefuelItems r
WHERE   r.IsDeleted = 0
  AND   r.EndTime  >= '2026-08-01' AND r.EndTime < '2026-09-01'
  AND   r.StartTime < '9999-01-01'
GROUP BY r.StartTime
HAVING  COUNT(DISTINCT r.FlightId) > 1
ORDER BY SoMe DESC;
```

Và đếm mức độ ảnh hưởng theo ngày:

```sql
-- Mẻ có thời lượng bơm bất khả thi (> 4 giờ)
SELECT  Ngay   = CAST(r.EndTime AS date),
        SoMe   = COUNT(*),
        SoXe   = COUNT(DISTINCT r.TruckId)
FROM    RefuelItems r
WHERE   r.IsDeleted = 0
  AND   r.EndTime >= '2026-07-01' AND r.EndTime < '2026-09-01'
  AND   r.StartTime < '9999-01-01'
  AND   DATEDIFF(MINUTE, r.StartTime, r.EndTime) > 240
GROUP BY CAST(r.EndTime AS date)
ORDER BY Ngay;
```

Ngày đầu tiên có kết quả khác 0 chính là mốc lỗi bắt đầu — đối chiếu với lịch phát hành app
sẽ biết bản nào mang lỗi vào.

---

## 7. Cách xác nhận đã hết lỗi

Sau khi vá cả hai phía, dấu hiệu để nghiệm thu:

| Kiểm tra | Đạt khi |
|---|---|
| Truy vấn ở mục 6 | không còn nhóm nào có `SoChuyen > 1` |
| Thời lượng bơm | không còn mẻ vượt 4 giờ |
| Mẻ chưa bơm | giờ bắt đầu là `null` hoặc sentinel — **không phải giờ đồng bộ** |
| Sửa giờ trên máy | giá trị mới lên tới server, hoặc bị từ chối **kèm lý do đọc được** |

Điểm cuối quan trọng nhất về mặt vận hành: **thiếu dữ liệu thì nhìn ra được, dữ liệu bịa thì
không.** Một mẻ không có giờ bắt đầu sẽ bị phát hiện ngay; một mẻ có giờ bắt đầu sai 13 tiếng
thì đi thẳng vào hoá đơn.

---

## Phụ lục — dữ liệu tra cứu

| | |
|---|---|
| Chuyến | `FlightId = 1284485`, VJ336, PQC-SGN, A321, VN-A200 |
| Khởi hành kế hoạch | 26/08/2026 08:30 |
| Tra nạp kế hoạch | 26/08/2026 07:35 |
| Mẻ | `RefuelItems.Id = 2120355` |
| Phiếu | `Receipts.Id = 1795397`, số `39070DTR` |
| Xe | PQC 32007 (`Trucks` — SKYPEC) |
| Nhân viên | Nguyễn Ngọc Quân (2350) |
| Lái xe | Trần Cao Long (617) |
| Nhiệt độ / tỷ trọng | 27 °C / 0,7840 |
