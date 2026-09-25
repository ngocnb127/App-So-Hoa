# BÁO CÁO ĐỢT SỬA — LUỒNG TRA NẠP

Ngày: 2026-09-05 · Nhánh: `fix/datahelper-offline-sync`
Tài liệu liên quan: [RA-SOAT-LUONG-TRA-NAP-25-VAN-DE.md](RA-SOAT-LUONG-TRA-NAP-25-VAN-DE.md)

**Kết quả kiểm chứng cuối** (chạy độc lập, không dựa vào lời khai của tác nhân sửa):
- `./gradlew :app:compileDebugJavaWithJavac` → BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` → **636 test, 0 fail, 0 error, 52 lớp**
- Trước đợt sửa: 592 test → **+44 test mới**
- `AppDatabase` vẫn **version 14**, không migration
- Hai ngưỡng lọc LCR **nguyên văn**; **không chỗ nào** ghi `postStatus = ERROR`

Quy trình: 3 lượt kiểm thử độc lập → 2 phương án → 2 lượt thẩm định → 3 tác nhân sửa → 1 lượt kiểm toán độc lập → vá lỗi kiểm toán phát hiện → kiểm chứng lại.

---

## 1. Phạm vi đã sửa

9 hạng mục được duyệt trong tổng số 25 vấn đề đã phát hiện.

| Mục | Nội dung | Mức |
|---|---|---|
| 1 | Cảnh báo khi đọc trường đồng hồ thất bại (LCR + TCS) | Nghiêm trọng |
| 4 | Mở đường xác nhận cho mẻ tra nạp chuyến chưa phân công | Nghiêm trọng |
| 5 | Chặn số đồng hồ âm lan xuống cơ sở dữ liệu | Nghiêm trọng |
| 6 | Hoá đơn bị gửi hai lần gây trùng hoá đơn | Cao |
| 9 | Làm mới dữ liệu mẻ xe khác trước khi dựng chứng từ gộp | Cao |
| 10 | Cảnh báo giờ đảo ngược khi xuất hoá đơn | Cao |
| 11 | Ràng buộc giờ còn lọt | Trung bình |
| 17 | Màn ký: crash và treo im lặng | Trung bình |
| 20 | Xử lý mã lỗi HTTP (làm một nửa) | Thấp |

---

## 2. Vấn đề → Phương án → Nội dung sửa

### Mục 1 — Không có cảnh báo nào khi đọc trường đồng hồ thất bại

**Vấn đề.** Lỗi đọc trường chỉ được ghi log, không đến tay người dùng. Watchdog độ tươi không bắt được vì mốc "dữ liệu mới nhất" được cập nhật cho **bất kỳ** trường nào — một trường chết mà trường khác còn sống thì hệ thống vẫn coi là bình thường. Đúng kịch bản nghiệp vụ nêu ra: Gross đọc được, TotalGross thất bại, hai số không khớp mà màn hình vẫn xanh.

**Phương án đã loại.** Chèn cảnh báo vào vòng kiểm tra dữ liệu sẵn có. Bị loại vì vòng đó có chu kỳ 60 giây, chỉ chạy nhánh LCR, và **tự huỷ sau 20 lần** — cảnh báo sẽ tắt vĩnh viễn đúng lúc thiết bị hỏng nặng nhất.

**Đã sửa.** Lớp mới `helpers/MeterFieldHealth.java` (thuần Java) theo dõi sức khoẻ **từng trường riêng biệt**: GROSSQTY, TOTALIZER, nhiệt độ, tỉ trọng, số vé. Ba tín hiệu phân biệt rõ:
- `markRead` — đọc và phân tích được giá trị (gọi **trước** bộ lọc)
- `markAccepted` — giá trị **qua được** bộ lọc ngưỡng
- `markFail` — đọc, đăng ký hoặc phân tích thất bại

Điểm đo trải khắp: các nhánh nhận giá trị trong `LCRReader`, các chỗ nuốt lỗi phân tích, nhánh đọc hỏng, nhánh đăng ký hỏng, **và nhánh TCS** `TcsDevice.processValue` — vì TCS cũng đọc từng trường và có thể phát snapshot trộn Gross mới với Total cũ.

Hiển thị **chỉ qua watchdog sẵn có**, một người viết duy nhất: dấu kết nối chuyển trạng thái cảnh báo, ba ô số đổi màu. **Tự tắt** khi dữ liệu trở lại vì watchdog tính lại mỗi chu kỳ, không dùng cờ dính.

Ghi vết: `METER_FIELD_STALE` / `METER_FIELD_RECOVERED` lúc chuyển trạng thái, cộng một dòng `METER_FIELD_SUMMARY` khi kết thúc mẻ để đối soát sau ca.

**Không dialog, không khoá nút, không dừng đo, không bỏ gói dữ liệu, không chặn lưu, không chặn Bắt đầu/Kết thúc.**

**Điểm quan trọng — sửa sau kiểm toán.** Bản đầu đánh dấu "khoẻ" ngay khi *đọc được*, nên ca đóng băng số đồng hồ tổng (Totalizer bị ngưỡng loại liên tục trong khi Gross vẫn chạy) lại **không kêu**. Đã bổ sung: một trường bị bộ lọc loại **liên tiếp quá 15 giây** thì coi là hỏng — nhưng **chỉ áp cho TOTALIZER**, để GROSSQTY lúc chưa mở vòi không báo động giả.

### Mục 5 — Số đồng hồ âm lan xuống cơ sở dữ liệu

**Vấn đề.** Số đồng hồ đầu được tính bằng *số tổng trừ sản lượng*. Khi số tổng đứng im mà sản lượng vẫn tăng, số đồng hồ đầu **giảm dần và có thể âm**, rồi được ghi xuống máy.

**Đã sửa.** Khi số tổng đang mất tín hiệu, hoặc phép trừ ra số âm → **giữ nguyên giá trị cũ**. Vẫn hiển thị, vẫn lưu, vẫn cho bấm Kết thúc. Áp cho **cả hai nhánh LCR và TCS** — nhánh TCS là chỗ phương án ban đầu bỏ sót.

Có ghi vết `START_NUMBER_HELD` / `START_NUMBER_RESUMED`, vì việc này biến "số âm — sai rõ ràng" thành "cặp số trông hợp lý", nên bắt buộc phải để lại dấu, nếu không là giấu lỗi.

### Mục 4 — Mẻ tra nạp hộ kẹt ở màn Xác nhận

**Vấn đề.** Màn tra nạp cho phép tiếp quản chuyến chưa phân công, nhưng màn Xác nhận lại gọi nhánh nghiêm ngặt. Hậu quả: bơm xong rồi **kẹt ngay ở bước xác nhận**. Vi phạm trực tiếp yêu cầu "vẫn tra nạp và ghi nhận được khi không được phân công".

**Đã sửa.** Thêm lối vào cho phép ghi, truyền cờ tiếp quản xuống **cả hai** lượt gọi bên trong đường lưu — phương án ban đầu chỉ sửa một chỗ, thẩm định phát hiện còn đường dự phòng cũng nghiêm ngặt.

Kèm **cảnh báo không chặn** theo đúng yêu cầu ("vẫn cho ghi nhưng thêm cảnh báo") và ghi vết như đường màn tra nạp đang làm.

**Giữ nguyên mức nghiêm ngặt** cho đường đồng bộ theo lô, đường xác nhận từ server, đường lưu giờ kết thúc và đường lưu màn Xem trước. Có 3 test chống hồi quy khẳng định cả hai chiều.

### Mục 6 — Hoá đơn bị gửi hai lần gây trùng hoá đơn

**Vấn đề.** Hàm chèn bản ghi trả về rỗng nên đối tượng không nhận được mã dòng vừa sinh; lần ghi sau tra sai khoá nên **chèn thêm dòng thứ hai**, còn dòng cũ vẫn mang cờ "chưa gửi" nên vòng đồng bộ **gửi lại lần nữa**.

**Đã sửa.** Theo đúng khuôn mẫu đã làm đúng ở đường phiếu: tra theo khoá định danh, trả về mã dòng, ghi ngược vào đối tượng, bọc trong giao dịch.

**Không cần migration** — thẩm định phát hiện cột định danh **đã có sẵn** ở lớp cơ sở, và đã nằm trong dữ liệu gửi server. Kết luận ban đầu "hoá đơn không có mã định danh" chỉ đúng ở tầng gửi đi, sai ở tầng lưu trữ.

### Mục 9 — Dữ liệu mẻ xe khác không được làm mới

**Vấn đề.** Dữ liệu mẻ xe khác chỉ nạp một lần khi mở màn Xem trước, sau đó màn này khoá luôn việc kéo dữ liệu. Mở lúc 15:00, xe khác truyền mẻ lúc 15:05, in lúc 15:10 → **tổng thiếu mẻ đó mà không cảnh báo gì**, vì cờ trạng thái vẫn báo "đủ dữ liệu".

Đây là mục quan trọng vì **xe chốt và xuất hoá đơn là xe cuối cùng xuất hàng**.

**Đã sửa.** Làm mới **ngay trước khi dựng chứng từ gộp**, tại cả ba điểm. Không phải đụng tới cơ chế khoá, vì hàm nạp dữ liệu cho màn Xem trước **không đi qua** vòng đồng bộ — điều này do thẩm định phát hiện và nó gỡ bỏ rủi ro lớn nhất của mục này.

Bốn điểm bảo vệ:
1. **Hàng rào rút cạn hàng đợi ghi trước khi gọi server** — nếu không, dữ liệu người dùng vừa gõ mà chưa kịp lưu sẽ bị bản server ghi đè và **biến mất khỏi chứng từ sắp in**.
2. **Tính lại danh sách in sau khi nạp lại** — kèm khôi phục trạng thái tích. Tác nhân sửa phát hiện việc dựng lại màn hình tạo bộ điều hợp mới với **mọi ô tích bị bỏ**, nên nếu thiếu bước này người dùng sẽ **mất hết lựa chọn mẻ** sau mỗi lần làm mới.
3. **Làm mới thất bại thì vẫn in được** — chỉ cảnh báo tổng có thể chưa đủ, không hạ cấp cờ, không chặn nút.
4. **Mẻ xe khác chưa đủ điều kiện thì loại khỏi chứng từ gộp kèm cảnh báo**, không chặn — vì máy này chỉ đọc mẻ xe khác, chặn là bắt người dùng chờ vô hạn một thứ họ không sửa được. Mẻ của **chính xe mình** thì giữ nguyên mức chặn, vì người dùng sửa được.
5. Mẻ mới xuất hiện thì **hỏi**, không tự tích — tích thêm một mẻ là đổi số sẽ in ra giấy.

**Lỗi nghiêm trọng phát hiện khi kiểm toán, đã vá.** Bản đầu khi nhận thấy "kết quả làm mới xấu hơn dữ liệu đang có" thì chỉ giữ lại **cờ** cũ, nhưng vẫn **gán dữ liệu mới và dựng lại màn hình vô điều kiện**. Hậu quả: server trả thiếu mẻ xe khác → danh sách in teo lại → cờ cũ vẫn nói "đủ dữ liệu" → **không đường chặn nào kêu** → in ra phiếu gộp **thiếu mẻ, tổng nhỏ hơn thực tế**. Đúng thứ mục 9 sinh ra để cứu. Đã vá: kết quả xấu hơn thì **bỏ luôn cả dữ liệu**, giữ nguyên trạng thái đang có, vẫn cho in.

### Mục 10 — Xuất hoá đơn thiếu chặn giờ đảo ngược

**Vấn đề.** Đường in phiếu có chặn giờ kết thúc sớm hơn giờ bắt đầu; đường xuất hoá đơn **không có** → hoá đơn điện tử có thể phát hành với giờ đảo ngược.

**Đã sửa.** Theo mức người dùng chọn: **popup cảnh báo hai nút "Vẫn xuất" / "Kiểm tra lại"**, không chặn cứng. Thông báo **kèm số xe**, vì mẻ lỗi có thể thuộc xe khác mà người dùng không sửa được. Ghi vết khi người dùng chọn vẫn xuất.

**Đường in phiếu giữ nguyên mức chặn cứng** như cũ.

### Mục 11 — Ràng buộc giờ còn lọt

**Đã sửa.** Thêm luật cảnh báo: **giờ tương lai** (dung sai 2 phút), **giờ rời đi sớm hơn giờ tiếp cận**. Thông báo hiện `dd/MM HH:mm` khi mẻ trải nhiều ngày — trước đây chỉ in `HH:mm` nên lỗi qua nửa đêm đọc như lỗi giả. Bộ chọn ngày được chặn không cho chọn ngày tương lai, có bọc chống ngoại lệ.

**Sửa sau kiểm toán.** Hai ghi chú ngữ cảnh (thiếu giờ tiếp cận, mẻ 0 phút) ban đầu bị nối nhầm vào đường bật popup **không tắt được**. Hai ca này phổ biến **do chính thiết kế hiện tại** nên sẽ nổ liên tục, khiến người dùng quen tay bấm bỏ qua rồi bỏ qua luôn cảnh báo thật. Đã tách sang **hiển thị tại chỗ** trên màn hình, không gián đoạn. Cảnh báo lỗi thật giữ nguyên đường cũ.

### Mục 17 — Màn ký

**Đã sửa.** Hết crash khi bấm lưu mà chưa vẽ nét nào. Hết treo im lặng khi ghi tệp hỏng — nay báo lỗi và thoát đúng cách. Cảnh báo lúc thoát **nay xét cả chữ ký chưa xuất**, trước đây chỉ xét đã in hoặc đã chụp ảnh, nên ký xong mà chưa in rồi thoát là **mất im lặng**. Thêm log để đo tần suất thật.

Sửa ở màn ký ảnh hưởng **8 màn dùng chung** theo hướng tốt: đã vẽ thì hành vi y hệt, chưa vẽ thì thay crash bằng thông báo.

### Mục 20 — Xử lý mã lỗi HTTP (làm một nửa)

**Vấn đề.** Client chỉ xử lý 200 và 504; mọi mã khác bị nuốt, bản ghi vẫn mang cờ chưa gửi và **gửi lại mãi**. Không đọc nội dung lỗi.

**Đã sửa (phần client làm được ngay).** Đọc nội dung lỗi, trả về mã HTTP, ghi vết xung đột, backoff **có điểm dừng** (3 lượt rồi chuyển sang chờ người dùng xử lý).

**Tuyệt đối không** đặt trạng thái lỗi cho bản ghi — làm vậy nó sẽ **rơi khỏi hàng đợi**, đúng cái "dữ liệu treo" cần diệt.

**Chưa làm.** Gửi điều kiện phiên bản lên server. Lý do: rủi ro cổng vào từ chối, và nhánh xử lý xung đột đầy đủ sẽ **không hội tụ** nếu bật lên khi server chưa hỗ trợ. Cần thống nhất hợp đồng API với backend trước — xem §4 của tài liệu 25 vấn đề.

---

## 3. Những gì KHÔNG làm trong đợt này

| Nội dung | Lý do |
|---|---|
| Ngưỡng lọc gây đóng băng số đồng hồ (mục 2, 3) | Chưa được duyệt. **Cảnh báo ở mục 1 nay đã nổ đúng trong ca này**, nên lỗi còn nhưng không còn âm thầm |
| Khoá dữ liệu đã xuất hoá đơn (mục 7, 8) | Chưa được duyệt |
| Chuyến thiếu số xe không hiện ở tab nào (mục 21) | Hoãn có chủ ý — câu truy vấn đó còn là nguồn của cơ chế chặn nút Tiếp cận; sửa ẩu sẽ khiến chuyến chưa phân công do xe A tiếp cận **chặn nút Tiếp cận của xe B** |
| Lưu tạm ảnh chữ ký | Thiết kế ban đầu **đã bị bác** sau 3 lượt phản biện; đang chờ quyết định |
| Sửa `reset()` của bộ đọc LCR | Thẩm định chỉ ra vừa vô ích vừa mở rủi ro **rò dữ liệu mẻ cũ sang mẻ mới** |
| Mẻ xe khác đứng **một mình** thiếu tỉ trọng | Lượt loại chỉ chạy khi có nhiều hơn một mẻ; loại nốt mẻ duy nhất thì không còn gì để in, mà in với tỉ trọng 0 là in số sai. Cần quyết định mức xử lý |

---

## 4. Kiểm chứng ràng buộc

Đã truy vết code, không dựa vào lời khai của tác nhân sửa:

- **Không thêm đường chặn mới nào** — không có `setEnabled(false)`, không dialog chặn, không dừng đo, không bỏ gói dữ liệu
- **Làm mới thất bại vẫn in được**
- **Vẫn tra nạp và ghi nhận được chuyến chưa phân công**; các đường nghiêm ngặt khác giữ nguyên
- **Xe khác vẫn in và xuất phiếu được**
- **Không chỗ nào** ghi trạng thái lỗi cho bản ghi; vòng xung đột **hội tụ**
- **Vùng cấm sạch**: hai ngưỡng lọc nguyên văn, `reset()` không đụng, schema vẫn **version 14**, không có tính năng lưu tạm chữ ký
- **Không tìm thấy mảnh code dở dang** từ lần một tác nhân bị ngắt giữa chừng
- 19 hạng mục "đã xác nhận đúng" **không bị phá**

---

## 5. Khuyến nghị trước khi mang ra xe thật

1. **Thử ca đồng hồ mất tín hiệu giữa mẻ**: rút mạng thiết bị đo khoảng 60 giây rồi nối lại — kiểm tra dấu kết nối chuyển cảnh báo, ô số đổi màu, và **tự tắt** khi dữ liệu trở lại.
2. **Thử tra nạp chuyến chưa phân công trọn vòng**: từ chọn chuyến → bơm → xác nhận → tạo phiếu. Đây là đường vừa được mở, cần chạy hết.
3. **Thử phiếu gộp nhiều xe**: để xe khác truyền mẻ lên trong lúc màn Xem trước đang mở, rồi bấm in — kiểm tra tổng có đủ mẻ mới không.
4. **Thử in khi mất mạng**: phải vẫn in được, kèm cảnh báo tổng có thể chưa đủ.
5. **Đọc log sau ca**: các dòng `METER_FIELD_SUMMARY`, `START_NUMBER_HELD`, `COMBINED_DOCUMENT_EXCLUDED_FOREIGN` cho biết thực tế có gặp các ca này không.
