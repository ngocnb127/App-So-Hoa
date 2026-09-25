# RÀ SOÁT LUỒNG TRA NẠP — 25 VẤN ĐỀ

Ngày lập: 2026-09-05 · Nhánh: `fix/datahelper-offline-sync`
Phạm vi rà soát: chọn chuyến → kết nối đồng hồ → ghi nhận đến hết mẻ → màn Xác nhận → màn Xem trước/chỉnh sửa → tạo phiếu → ký/chụp ảnh → xuất hoá đơn.

**Cơ sở**: 3 lượt kiểm thử độc lập, 2 phương án sửa, 2 lượt thẩm định phản biện, 3 lượt phản biện chuyên đề chữ ký. Mọi kết luận đều đối chiếu code thật, có `file:line`.

**Nền so sánh**: `./gradlew :app:testDebugUnitTest` → **285 test, 0 fail**. Vì vậy mọi vấn đề dưới đây đều là **lỗ hổng chưa có test phủ**, không phải hồi quy.

---

## 0. Sáu mục tiêu nghiệp vụ đặt ra

1. Không để dữ liệu treo; post cũ quay lại không được ghi đè dữ liệu đã hoàn tất.
2. **Vẫn phải tra nạp/ghi nhận được khi chuyến chưa phân công** (thực tế điều hành không kịp phân công).
3. Ràng buộc giờ tiếp cận / rời đi / bắt đầu / kết thúc hợp lý.
4. Đồng hồ LCR đọc từng trường, có thể Gross thành công mà TotalGross thất bại → **cần cảnh báo, chỉ cảnh báo, không chặn bất cứ thứ gì**.
5. Xe khác được in/xuất phiếu nhưng **không** được sửa dữ liệu; chỉ sửa được mẻ của chính xe đó.
6. Chuyến nhiều mẻ nhiều xe: phải lấy được dữ liệu mới nhất từ server, tránh dùng bản cũ làm tổng sai. **Xe chốt/xuất hoá đơn là xe cuối cùng xuất hàng.**

---

## 1. Ba đính chính giả thiết ban đầu

Ba điều được tin là đúng lúc bắt đầu, nhưng đọc code cho thấy khác. Chúng đổi hướng xử lý nên ghi lại đây.

### 1.1 "TCS gửi theo gói nên dữ liệu luôn đúng" — sai một nửa
`TcsDevice.readField()` (`sdk_tcs/tcs/TcsDevice.java:273-283`) gọi **5 lệnh LCP riêng lẻ** (GROSSDSP, GROSSTOTAL, AVGTEMP, DEL_STATE, TICKETNR) — TCS **cũng đọc từng trường**, không phải một gói.

Khác biệt thật nằm ở chỗ khác: `processValue` (`:450-457`) chỉ ghi khi hợp lệ, lỗi thì **giữ nguyên biến cũ**, rồi `updateDataView()` (`:572-608`) phát **một snapshot gộp**. Nên TCS **vẫn có thể phát ra snapshot trộn Gross mới + Total cũ** — cũng không khớp, cũng không cảnh báo. Điểm hơn LCR là nó không có ngưỡng lọc nên không bị lỗi đóng băng.

**Hệ quả**: cảnh báo đọc trường thất bại phải áp cho **cả TCS**, không riêng LCR.

### 1.2 "Chữ ký là khách ký trên tờ phiếu đã in" — sai chiều
Trên bản máy in nhiệt, chữ ký được **nạp vào máy in rồi in LÊN phiếu**: `ReceiptModel.java:760-772`, `:955-971` chèn `^XGE:BUYER.GRF`; `ZebraWorker.java:401-425` `storeImage()` chạy ngay trước khi gửi ZPL. Luồng thật là **ký trước → in sau**.

Nặng hơn: chữ ký **được gửi lên server** — `ReceiptAPI.java:186-215` encode base64 `signaturePath`/`sellerSignaturePath`/`pdfPath` vào payload. Ảnh chữ ký **là chứng từ điện tử**, không phải trạng thái màn hình.

### 1.3 "Invoice không có UniqueId" — sai ở tầng Room
`BaseEntity.java:91` **đã có** cột `uniqueId`, và `Invoice extends BaseEntity`. `BaseModel.java:84` cũng sinh `uniqueId`, gson dùng UPPER_CAMEL nên `UniqueId` **đã có sẵn trong payload**. Kết luận: sửa lỗi hoá đơn trùng **không cần migration**, giữ `AppDatabase` version 14.

---

## 2. Bảng 25 vấn đề

Trạng thái: **ĐANG SỬA** = trong đợt hiện tại · **HOÃN** = có lý do kỹ thuật · **CHỜ ĐÁNH GIÁ** = cần quyết định.

### Nhóm NGHIÊM TRỌNG

| # | Vấn đề | Bằng chứng | Trạng thái |
|---|---|---|---|
| 1 | **Không có cảnh báo nào khi đọc trường đồng hồ thất bại.** `raiseError` chỉ ghi log. Watchdog độ tươi không bắt được vì `lastMeterDataAt` cập nhật cho **bất kỳ** trường nào — một trường chết mà trường khác sống thì vẫn tính là "tươi" | `LCRReader.java:1809-1814`, `RefuelDetailActivity.java:1834-1838`, `:1726`, `:710-735` | **ĐANG SỬA** |
| 2 | **Ngưỡng lọc bất đối xứng làm đóng băng vĩnh viễn số đồng hồ tổng.** Totalizer lọc `<1000`, Gross lọc `<10000`. Mất kết nối đủ lâu để tổng nhích quá 1000 L thì mọi giá trị sau **bị loại tới hết mẻ** | `LCRReader.java:542` vs `:489` | **CHỜ ĐÁNH GIÁ** |
| 3 | **Gross bị loại vĩnh viễn sau khi app bị kill giữa mẻ** đã bơm >10.000 L: `reset()` đưa `grossQty` về 0, mọi gói sau lệch >10000 | `LCRReader.java:~1066-1070`, `RefuelDetailActivity.java:661` | **CHỜ ĐÁNH GIÁ** |
| 4 | **Mẻ tra nạp hộ kẹt ở màn Xác nhận.** Màn này gọi nhánh fail-closed. Bơm xong nhưng không xác nhận được → **vi phạm trực tiếp mục tiêu #2** | `RefuelDetailConfirmActivity.java:602`; đường dự phòng `DataHelper.java:3662`, `:3699` | **ĐANG SỬA** |
| 5 | **Số âm lan xuống Room.** `StartNumber = EndMeter − GrossQty`; tổng đứng im mà Gross tăng → số đồng hồ đầu giảm dần, **có thể âm**, vẫn được lưu | `RefuelDetailActivity.java:2310-2312`, TCS `:2361-2366` | **ĐANG SỬA** |

> **Lưu ý quan trọng về #2/#3**: hai mục này chưa sửa, nhưng **cảnh báo ở #1 sẽ nổ đúng trong kịch bản đóng băng** — người dùng sẽ thấy ô số đổi màu và dấu kết nối chuyển trạng thái. Tức là lỗi vẫn còn nhưng **không còn âm thầm**. Đây là đánh đổi có chủ ý, không phải bỏ sót.

### Nhóm CAO

| # | Vấn đề | Bằng chứng | Trạng thái |
|---|---|---|---|
| 6 | **Hoá đơn POST hai lần → trùng hoá đơn trên server.** `insertInvoice` trả `void` nên object không nhận `localId`; lần ghi sau tra sai khoá → chèn hàng thứ hai; hàng cũ vẫn `isLocalModified=1` nên vòng sync POST lại | `DataHelper.java:4157-4168`, `DataRepository.java:683-693`, `InvoiceDao.java:21` | **ĐANG SỬA** |
| 7 | **Cờ `Exported` chỉ chặn ở UI, không chặn ở đường ghi/đồng bộ.** `postRefuel` và `syncModifiedRefuels` không đọc `isExported()` lần nào | `RefuelFieldOwnership.java:75`, `RefuelPreviewActivity.java:405,564,729,1750` | **CHỜ ĐÁNH GIÁ** |
| 8 | **Sửa đơn giá/thuế suất lan sang mẻ đã phát hành hoá đơn.** Khoá chỉ xét dòng đang chọn, nhưng `setAll` ghi lên **toàn bộ** mẻ của xe | `RefuelPreviewActivity.java:1871-1881` vs `:2331-2394` | **CHỜ ĐÁNH GIÁ** |
| 9 | **Tổng chuyến do client tự cộng; dữ liệu mẻ xe khác không được làm mới.** Màn xem trước khoá pull; mở lúc 15:00, xe B truyền mẻ lúc 15:05, in lúc 15:10 → **tổng thiếu mẻ xe B mà không cảnh báo gì** | `InvoiceModel.java:590-600`, `RefuelPreviewActivity.java:202-227`, `:748`, `DataHelper.java:1775-1786` | **ĐANG SỬA** |
| 10 | **Xuất hoá đơn thiếu chặn giờ đảo ngược** mà đường in phiếu đã có → HĐĐT in được với giờ kết thúc < giờ bắt đầu | `RefuelPreviewActivity.java:1113-1128` vs `:784` | **ĐANG SỬA** |

### Nhóm TRUNG BÌNH

| # | Vấn đề | Bằng chứng | Trạng thái |
|---|---|---|---|
| 11 | **Ràng buộc giờ còn lọt hoàn toàn**: giờ tương lai (bộ chọn ngày không đặt `setMaxDate`), `rời đi < tiếp cận`, `tiếp cận` rỗng làm bỏ qua toàn bộ kiểm tra, `bắt đầu == kết thúc` | `RefuelTimeValidator.java:64`, `RefuelApproachGuard.java:86-104`, `RefuelDetailConfirmActivity.java:537-558` | **ĐANG SỬA** |
| 12 | **Thông báo lỗi giờ qua nửa đêm đọc như lỗi giả**: so sánh đúng nhưng chỉ in `HH:mm` → "kết thúc 00:05 sớm hơn bắt đầu 23:50" | `RefuelTimeValidator.java:32`, `:210-212` | **ĐANG SỬA** |
| 13 | **Hàng đợi đăng ký trường LCR kẹt ở phần tử đầu**: nhánh đăng ký hỏng thiếu cả `remove` lẫn `processFieldQueue` → các trường sau không bao giờ được đăng ký | `LCRReader.java:739-751` | **CHỜ ĐÁNH GIÁ** |
| 14 | **TCS phát snapshot trộn Gross mới + Total cũ**, không cảnh báo | `TcsDevice.java:450-457`, `:572-608` | **ĐANG SỬA** (gộp vào #1) |
| 15 | **Đường tiếp quản quá rộng**: đóng dấu lại `truckId` sang xe hiện tại và **gỡ cờ read-only** kể cả khi mẻ thuộc xe khác, không hỏi người dùng, không xét mẻ đã hoàn tất/đã có chứng từ | `DataHelper.java:3745-3800`, `:3779-3784` | **CHỜ ĐÁNH GIÁ** |
| 16 | **Mất số liệu màn Xác nhận khi lưu hỏng rồi bấm "Tiếp tục"**: chỉ truyền ID, màn sau đọc lại từ Room/server → tỉ trọng, nhiệt độ, giờ vừa nhập bị thay bằng bản cũ, chỉ có một Toast | `RefuelDetailConfirmActivity.java:750-762`, `RefuelPreviewActivity.java:126-131` | **CHỜ ĐÁNH GIÁ** |
| 17 | **Màn ký crash khi bấm lưu mà chưa vẽ nét nào** — `getGesture()` trả null, dòng `toBitmap` nằm ngoài `try`. Và **lỗi ghi file bị nuốt**: `finish()` nằm trong `try` nên ghi hỏng thì màn ký **treo im lặng** | `ReceiptSignActivity.java:47`, `:64-66` | **ĐANG SỬA** |
| 18 | **`insertRefuel` đọc-sửa-ghi ngoài transaction**, tra theo `(id, localId)` chứ không theo `uniqueId`; entity không có unique index. Hiện chỉ an toàn nhờ **quy ước** mọi caller nằm trong khoá ghi | `DataRepository.java:202-216`, `:475-482`, `RefuelItem.java:15` | **CHỜ ĐÁNH GIÁ** |
| 19 | **`IsInternational` lệch giữa hai bảng phân quyền trường**: xếp SERVER-owned nhưng màn Xem trước vẫn ghi. Điểm lệch duy nhất giữa hai bảng | `RefuelFieldOwnership.java:71-76` vs `RefuelFieldPatch.java:87` | **CHỜ ĐÁNH GIÁ** |

### Nhóm THẤP

| # | Vấn đề | Bằng chứng | Trạng thái |
|---|---|---|---|
| 20 | **Client không xử lý mã lỗi nào ngoài 200/504**; 409/412/423 rơi vào `else` → `return null` → row vẫn dirty và **POST lại mãi**. Không đọc error body | `HttpClient.java:596-613`, `:325`, `DataHelper.java:2467` | **ĐANG SỬA** (một nửa) |
| 21 | **Chuyến `truckNo` rỗng không hiện ở bất kỳ tab nào** (`NULL != 'X'` trong SQLite là NULL nên bị loại khỏi cả hai vế); kèm nguy cơ NPE | `RefuelItemDao.java:43,47`, `RefuelRecyclerViewAdapter.java:123` | **HOÃN** — xem §3 |
| 22 | **`WeightNote` bị techlog ghi đè lên mọi mẻ đang in, gồm cả mẻ xe khác** | `RefuelPreviewActivity.java:2701`, `:2794`, `DataHelper.java:989-994` | **CHỜ ĐÁNH GIÁ** |
| 23 | **Khoá đồng bộ là cờ bật/tắt chứ không theo chủ sở hữu**: hai màn xem trước chồng nhau thì màn đóng trước mở khoá cho màn đang mở | `DataHelper.java:1204-1206`, `:1290-1295` | **CHỜ ĐÁNH GIÁ** |
| 24 | **Bản ghi cũ `postStatus = ERROR` nằm ngoài hàng đợi**; main không còn chỗ nào set ERROR, chỉ dọn một lần theo versionCode | `RefuelItemDao.java:88-92`, `DataHelper.java:1386-1404` | **CHỜ ĐÁNH GIÁ** |
| 25 | **Code chết**: ô Nội địa/Quốc tế bị `enabled="false"` nên listener không bao giờ chạy; ô số đồng hồ đầu/cuối ở màn xem trước bị `gone` và **switch không có case** → nhập xong bị bỏ im lặng | `activity_refuel_preview.xml:366-372`, `preview_extract.xml:293`, `RefuelPreviewActivity.java:2508-2620` | **CHỜ ĐÁNH GIÁ** |

---

## 3. Bốn quyết định kỹ thuật cần ghi nhớ

### 3.1 Vì sao HOÃN #21 (`truckNo` rỗng)
Câu truy vấn `RefuelItemDao:43` **không chỉ** phục vụ tab danh sách — nó còn là nguồn của `RefuelApproachGuard.findBlocking()`, mà hàm này **chặn cứng nút Tiếp cận** (`RefuelRecyclerViewAdapter:322-324`). Nút Tiếp cận chỉ ghi `approachTime` chứ không đóng dấu `truckNo`. Nên sửa ẩu sẽ khiến **một chuyến chưa phân công do xe A tiếp cận chặn nút Tiếp cận của xe B trên mọi chuyến** — hại hơn lỗi gốc, và trái mục tiêu #2. Cần một đợt riêng có kiểm thử đường Tiếp cận.

### 3.2 Vì sao #20 chỉ làm một nửa
Nếu bật xử lý 409/412/423 theo phương án đầy đủ thì vòng thử lại **không bao giờ hội tụ** (`verifyInconclusivePost` → MISMATCH → backoff lặp) — tự đẻ ra đúng "dữ liệu treo" mà mục tiêu #1 muốn diệt. Ngoài ra **tuyệt đối không được** set `postStatus = ERROR` khi gặp xung đột, vì row sẽ **rơi khỏi hàng đợi**.

Đợt này chỉ làm: đọc error body, trả mã HTTP, ghi vết xung đột, backoff **có điểm dừng**, giữ row trong hàng đợi.

Việc gửi precondition `If-Match` bị **loại khỏi đợt này**: có rủi ro gateway/WAF trả 412 hàng loạt, và `sendPOST` chưa có đường truyền header. Cần thống nhất với backend trước — xem §4.

### 3.3 Vì sao #9 rủi ro cao nhất trong nhóm đang sửa
Làm mới dữ liệu sẽ kéo về cả **mẻ xe khác đang dở** (chưa có tỉ trọng/QC), mà guard hiện tại **chặn cứng** những mẻ như vậy — trong khi xe này read-only, không sửa được. Làm ẩu sẽ thành *"hôm nay in được, mai không in được"*.

Nguyên tắc bắt buộc: **làm mới thất bại hoặc dữ liệu thiếu thì vẫn in được**, chỉ cảnh báo tổng có thể chưa đủ. Và phải **rút cạn hàng đợi ghi trước khi gọi server**, nếu không dữ liệu vừa gõ sẽ bị bản server đè mất.

### 3.4 Vì sao KHÔNG sửa `reset()` của LCR
Phương án ban đầu định tách `reset()` để chống #3. Thẩm định cho thấy việc này **vừa vô ích vừa nguy hiểm**: bộ lọc có cờ `seeded` độc lập nên đã tự xử lý; còn tách `reset()` mở rủi ro **rò dữ liệu mẻ cũ sang mẻ mới** vì Activity dùng chung object model (`RefuelDetailActivity:1824`).

---

## 4. Việc cần thống nhất với backend

Chống ghi đè hiện **chỉ là cục bộ trong Room**. An toàn cuối cùng phụ thuộc hoàn toàn vào server, vì client **không gửi precondition phiên bản nào**: `baseClientSeq`/`baseServerRevision` khai báo `transient` (`RefuelItemData.java:212-213`) nên **không vào JSON**.

Đề xuất hợp đồng API:

| Tình huống | Mã | Thân phản hồi | Client xử lý |
|---|---|---|---|
| Bản gửi lên cũ hơn bản server | `412` | Kèm bản server để client rebase | Rebase rồi gửi lại |
| Mẻ đã chốt sổ / đã phát hành chứng từ | `423` | Lý do khoá | Ghi vết, dừng thử lại, báo người dùng |
| Gửi trùng (cùng `UniqueId`) | `200` | Trả bản hiện có | Coi như thành công |

Client sẽ gửi precondition qua **header `If-Match`** để server cũ bỏ qua được — nhưng chỉ bật sau khi backend xác nhận gateway không chặn header lạ.

---

## 5. Những chỗ ĐÃ ĐÚNG — không được sửa

Ghi lại để lần sau không "sửa" nhầm thứ đang chạy tốt.

1. **Giờ bắt đầu lấy từ sự kiện đồng hồ**, không phải lúc bấm nút; có khoá và cờ chống ghi đè khi Activity bị tái tạo.
2. **Tiếp quản chuyến chưa phân công ở màn tra nạp hoạt động đúng**; đường batch/ACK vẫn fail-closed.
3. **Giá trị rỗng/null/0 từ server không xoá dữ liệu local** — merge trên JSON thô, khoá vắng mặt nghĩa là "không nói gì".
4. **Chống hạ cấp và chống response cũ**: chặn hoàn tất → chưa hoàn tất, kiểm tra danh tính phản hồi, kiểm `clientSeq` sau HTTP.
5. **Số đồng hồ đo được đã khoá đúng ở màn Xem trước** — `EndNumber` không nằm trong phạm vi sửa.
6. **Mẻ xe khác read-only, fail-closed hai lớp** (biên UI + kiểm tra lại ở tầng dữ liệu). **Không tìm thấy đường vòng nào** — mục tiêu #5 đang được bảo đảm.
7. **Đường "in hộ" cảnh báo + ghi vết chứ không chặn** — đúng chủ ý.
8. **Phiếu lưu offline an toàn**: local-first, khoá theo số phiếu, có khoá idempotency.
9. **Không còn cờ trạng thái kẹt vĩnh viễn**: mọi lượt bắt đầu post đều có `finally`.
10. **In lại không đụng gì tới chữ ký**: là lối vào riêng, ẩn toàn bộ nút Ký/Chụp/Lưu, chỉ đọc và in.

---

## 6. Việc còn treo — chờ bàn tiếp

**Lưu tạm ảnh chữ ký.** Phương án khoá theo `uniqueId` mẻ **đã bị bác**: khoá theo mẻ là khoá theo *"cùng chuyến bay"*, không phải *"cùng nội dung chứng từ"*, nên nó khôi phục chữ ký **mạnh nhất đúng lúc nội dung vừa bị sửa**. Phanh "so thời điểm xuất phiếu" **vô hiệu** vì `RefuelItemData` không có trường đó, còn `Receipt.date` thực chất là **giờ kết thúc tra nạp**.

Rủi ro cụ thể: ký cho 4.520 L → sửa còn 4.480 L → vào lại, chữ ký tự khôi phục → bấm Xuất. **Không in, không chụp, khách không thấy gì**, server nhận phiếu 4.480 L kèm ảnh chữ ký của khách. Điều kiện xuất chỉ cần *đủ 2 chữ ký **hoặc** có ảnh*, và bản máy in nhiệt bỏ qua bước nhắc chụp ảnh.

Hướng thay thế: khoá theo **vân tay nội dung chứng từ**, đặt luôn vào tên file (`SIG_<uid mẻ neo>_<mã vân tay>_<buyer|seller>.jpg`) nên không cần kho chỉ mục, không đụng Room, không migration. Nội dung đổi → không có file khớp → không khôi phục. Vân tay **không gồm** bãi đỗ và số chứng chỉ chất lượng, vì sửa hai thứ đó thì chữ ký cũ vẫn còn giá trị.

Cần biết thêm trước khi quyết: app **đã có sẵn lối "Xuất chờ ký"** (`signType = 3`). Và hiện **không có log nào** ở chỗ ký và chỗ thoát nên **không đo được tần suất thật** — đợt này đã bổ sung log để lấy số liệu.

**Ngoài phạm vi, cần một đợt riêng**: `PrintReceiptActivity` không có `onSaveInstanceState`, nên khi hệ điều hành thu hồi tiến trình sẽ mất **8 trường** (`isPrinted`, techlog, số phiếu sửa tay, `splitAmount`, `defuelingNo`, `signType`, `currentPhotoPath`…), không riêng chữ ký.
