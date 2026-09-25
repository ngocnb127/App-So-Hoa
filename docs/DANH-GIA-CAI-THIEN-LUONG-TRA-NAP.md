# ĐÁNH GIÁ CẢI THIỆN LUỒNG TRA NẠP

Ngày lập: 2026-09-05 · Nhánh: `fix/datahelper-offline-sync`
Phạm vi: trọn luồng — chọn chuyến → tiếp cận → đồng hồ → ghi số liệu → kết thúc (kể cả thủ công) →
màn Xác nhận → Xem trước/sửa → techlog → phiếu → ký/chụp → hoá đơn → xuất xong.

**Cơ sở**: hai lượt kiểm kê điểm chặn độc lập (46 điểm nửa đầu + 15 điểm nửa sau = **61 điểm**) và
hai lượt phân tích/đề xuất độc lập, tất cả đọc trực tiếp code trên working tree hiện tại. Tài liệu
này **chỉ đánh giá và đề xuất, không sửa một dòng code nào**.

**Quan hệ với đợt sửa vừa xong** ([BAO-CAO-DOT-SUA-LUONG-TRA-NAP.md](BAO-CAO-DOT-SUA-LUONG-TRA-NAP.md)):
9 hạng mục đã sửa (cảnh báo trường đồng hồ, mẻ tra nạp hộ ở màn Xác nhận, số đồng hồ âm, hoá đơn
trùng, làm mới mẻ xe khác, giờ đảo ngược ở đường hoá đơn, ràng buộc giờ, màn ký, mã lỗi HTTP)
**không được đề xuất lại** trong tài liệu này. Nơi nào đợt sửa đó đã đặt đúng nền móng, tài liệu này
nói rõ là **đừng đụng vào**.

---

## 1. TÓM TẮT ĐIỀU HÀNH

Ứng dụng đang chạy sản xuất và phần lớn các quyết định trong code từng **đúng trong phạm vi hẹp của
nó**. Vấn đề không nằm ở 61 quyết định tồi, mà ở chỗ **chưa ai đứng đủ xa để nhìn thấy chúng cộng
lại**. Kiểm kê cho thấy 61 điểm chặn nhưng chỉ **bốn cái gốc**, và bốn cái gốc đó đều sửa rẻ.

Bốn vấn đề lớn nhất, theo thứ tự cần xử lý:

1. **Không xuất được hoá đơn khi mất mạng — tần suất 100%.** Nút XUẤT HOÁ ĐƠN luôn tự gắn nhãn
   "chứng từ gộp nhiều xe", kể cả chuyến một xe một mẻ, nên luôn đòi dữ liệu xe khác từ server. Mất
   mạng là tắc tuyệt đối. Đây là vi phạm thẳng yêu cầu bắt buộc, và **cách chữa là sửa đúng một dòng**.
2. **Nhân viên bị buộc khởi động lại app ngay tại chân tàu bay.** Phím Back bị vô hiệu cho toàn app,
   cộng với các hộp thoại không có nút thoát, tạo ra ít nhất bốn ngõ cụt thật. Ca thiết bị lỗi phải
   kết thúc thủ công **luôn luôn** kết thúc bằng ngõ cụt, không phải đôi khi.
   > **Đính chính (đã kiểm chứng lại trên code).** Ca **bình thường — đồng hồ tự kết thúc — KHÔNG
   > dính chuỗi ngõ cụt này**: sự kiện kết thúc từ đồng hồ đi thẳng `finalizStop` → lưu → màn Xác
   > nhận, chạy đúng. Chuỗi chỉ nổ khi **kết thúc từ app**. Bản kiểm kê ban đầu nói "mọi mẻ phải kết
   > thúc bằng Force Stop" là **nói quá**.
3. **Máy in lỗi lại biến thành lệnh bắt buộc phát hành chứng từ.** In hỏng vẫn hiện hộp "Số hoá đơn"
   không huỷ được; ở màn hoá đơn, gõ xong là dữ liệu lên server ngay. Cộng thêm một lỗi thật ở
   `BaseActivity`: giá trị bị chính hàm kiểm tra **từ chối** vẫn được đẩy đi. Đây là rủi ro chứng từ
   pháp lý cao nhất trong toàn bộ tài liệu, và chi phí sửa thấp.
4. **Thang đo bị lộn ngược trên diện rộng.** Lỗi nhẹ bị chặn cứng, lỗi nặng chỉ cảnh báo. Mẻ 0 phút
   (do chính app tự ghi ra) bị chặn cứng, trong khi giờ đảo ngược chỉ cảnh báo hai nút. Người bị chặn
   thường **không phải người có lỗi và cũng không có quyền sửa** — điển hình là xe A in hộ cho xe B
   bị chặn vì xe B thiếu số QC.

Khuyến nghị: **làm đợt 1 ngay**. Đợt 1 gồm khoảng 14 sửa nhỏ, phần lớn dưới mười dòng mỗi cái, và nó
gỡ được ba trong bảy nguyên tắc nghiệp vụ đang bị vi phạm. Việc nặng nhất (gom luật chứng từ về một
nguồn) để sau cùng và cần chốt nghiệp vụ trước.

Có **một mâu thuẫn giữa hai lượt phân tích** cần chủ dự án phân xử: giờ đảo ngược nên chặn cứng hay
chỉ cảnh báo. Ngoài ra có **11 câu cần trả lời** (5 cho backend, 6 cho nghiệp vụ) trước khi các đợt
sau khởi động — nêu đầy đủ ở §6.

---

## 2. BỨC TRANH CHUNG — 61 ĐIỂM CHẶN, BỐN CÁI GỐC

### 2.1 Bốn đường dây nối các điểm chặn

**Đường dây 1 — một dòng đặt sai chỗ biến ngoại lệ thành đường đi mặc định.**

`setButtonText()` đặt `btnStart.setVisibility(GONE)` ở **dòng đầu tiên** của hàm, mà
`setRefuelStatus()` thì luôn gọi hàm này. Kết quả: nút Bắt đầu/Dừng biến mất vĩnh viễn ngay lần đổi
trạng thái đầu tiên, **trên mọi dòng xe** — không chỉ TCS như bản kiểm kê ban đầu tưởng. Hệ quả dây
chuyền:

> nút Dừng biến mất → **kết thúc TỪ APP chỉ còn đường Force Stop** → rơi vào hộp nhập tay
> **không huỷ được** → nhập sai thì gặp hộp lỗi **không có nút nào** → hoặc qua được thì `finalizStop`
> ghi `StartTime = EndTime` → màn Xác nhận **chặn cứng mẻ 0 phút** → màn Xác nhận **không có nút
> thoát** → phím Back **bị vô hiệu toàn app** → **khởi động lại app**.

Ba cụm nặng nhất của nửa đầu luồng đều được nuôi bởi một dòng `setVisibility`. Bằng chứng đây là lỗi
chứ không phải ý đồ: nếu cố ý bỏ nút Dừng thì đã không viết `stopTCS()`, không viết hằng số chờ 35
giây cho pha ENDING, và không đặt `VISIBLE` cho LCR600 lúc khởi tạo. Toàn bộ đoạn code đó **chưa bao
giờ chạy trong thực tế**.

**Đường dây 2 — Back bị cấm ở lớp cha, nên "có lối thoát" trở thành việc-phải-nhớ.**

`BaseActivity.onBackPressed(){ return; }` cấm Back cho **toàn bộ** app. Lý do ban đầu đúng — thao tác
đeo găng trên tablet, chạm nhầm giữa mẻ là mất dữ liệu — nhưng lý do đó chỉ đúng cho **một trạng thái
của một màn hình**. Đặt lệnh cấm ở lớp cha khiến lối thoát của mọi màn hình phụ thuộc hoàn toàn vào
việc lập trình viên có nhớ đặt nút trong layout hay không. Mỗi lần quên là một ngõ cụt, và đã có bốn.

**Đường dây 3 — một cờ boolean gánh hai câu hỏi khác nhau.** Mẫu bệnh lặp lại bốn lần:

| Cờ | Câu hỏi nó nên trả lời | Câu hỏi bị nhét thêm | Hậu quả |
|---|---|---|---|
| `isCombinedDocument()` | Chứng từ này có gộp nhiều xe không? | Ta đã biết đủ dữ liệu chưa? | Mất mạng ⇒ không xuất hoá đơn |
| `required` (hộp nhập) | Không được để trống | Không được huỷ | In lỗi ⇒ ép phát hành |
| `isLoggedin()` | Người này là ai / token còn dùng được / được mở app không | (cả ba gộp một) | Hết hạn token ⇒ mất luôn quyền dùng app |
| trạng thái mẻ trong `updateRefuelData` | Có được **ghi** số vào phiếu không | Có được **hiển thị** số không | Màn hình chết cứng, không rõ vì sao |

**Đường dây 4 — luật nghiệp vụ nằm rải ở tầng sai, không ai đối chiếu chéo.**

`ReceiptModel.createReceipt` là hàm **dựng model** nhưng bị nhét thêm 9 chỗ `throw` làm trọng tài
nghiệp vụ (chặng bay, tỉ trọng, loại tàu bay, nhiệt độ). Ở tầng sâu nhất, hàm này mất hết ngữ cảnh:
không biết mẻ nào của xe mình (sửa được) và mẻ nào của xe khác (chỉ đọc), không có cách trả về "cảnh
báo", và chỉ báo được **một** vi phạm mỗi lần. Trong khi đó cơ chế cứu duy nhất cho mẻ xe khác lại
lọc theo **một bộ luật hoàn toàn khác** — nên **cái được loại ra không phải cái sẽ gây lỗi**.

### 2.2 Code tự đánh nhau — các mâu thuẫn nội tại đã xác định

| # | Chỗ A nói | Chỗ B nói ngược lại |
|---|---|---|
| 1 | `finalizStop` **cố ý** ghi `StartTime = EndTime` (comment giải thích rõ: thà 0 phút còn hơn lệch 4 tiếng) | `validTime()` coi đúng giá trị đó là dữ liệu rác và **chặn cứng** |
| 2 | `RefuelTimeValidator` **cố ý** xếp mẻ 0 phút vào nhóm nhắc tại chỗ, không dialog, không chặn | `save()` chặn cứng đúng ca đó |
| 3 | Màn Xác nhận: giờ đảo ngược ⇒ **cảnh báo hai nút** | Cùng màn: mẻ 0 phút ⇒ **chặn cứng**. Lỗi nặng được tha, lỗi nhẹ bị chặn |
| 4 | Đường hoá đơn: giờ đảo ngược ⇒ cảnh báo hai nút, xuất được | Đường phiếu: cùng dữ liệu ⇒ chặn cứng một nút. **Xuất được hoá đơn mà không in được phiếu** |
| 5 | Thời lượng mẻ: cảnh báo ở **60 phút** (Activity) | `validTime()` chặn ở **120 phút**; `createReceipt` ném ở **180 phút**. Ba mốc cho một khái niệm |
| 6 | Tỉ trọng: `validate()` chỉ đòi `> 0` | Ô nhập chặn 0.72–0.86; `createReceipt` chặn 0.72–0.86 |
| 7 | Javadoc `isCombinedDocument()`: *"Quyết định theo DỮ LIỆU sắp in, không theo enum của nút đã bấm"* | Thân hàm vẫn xét enum `printMode` |
| 8 | Comment `showEndSaveFailed()`: *"Người đứng tại tàu bay phải luôn còn đường đi tiếp"* | Ngay dưới đó `setCancelable(true)` biến việc huỷ dialog thành ngõ cụt |
| 9 | `onClick` mở hộp thoại sửa "số đồng hồ đầu" | `updateDialogResult` cho ô đó **đã bị comment** — gõ số xong giá trị bị nuốt im lặng |
| 10 | Toàn bộ pha ENDING của TCS được viết công phu, có timeout 35 giây | Đường vào nó là code chết vì nút đã `GONE` |

### 2.3 Thang đo bị lộn ngược

Nguyên tắc số 1 của chủ dự án là *"ưu tiên cảnh báo, người dùng tự xử lý"*. Thực tế trong code:

| Mức độ sai của dữ liệu | Cách hệ thống xử lý | Nhận xét |
|---|---|---|
| Giờ kết thúc **sớm hơn** giờ bắt đầu (sai nặng, không thể xảy ra thật) | Cảnh báo hai nút ở đường hoá đơn | Nới |
| Mẻ 0 phút (do **chính app** ghi ra ở ca thiết bị lỗi) | **Chặn cứng**, không lối thoát | Lộn ngược |
| Sản lượng > 11.000 GL (ngưỡng chống số rác, không phải luật nghiệp vụ) | **Chặn cứng** — mà lối thoát duy nhất là **sửa số đo có thật xuống dưới ngưỡng** | Chặn để bảo vệ dữ liệu, lối thoát lại là làm hỏng dữ liệu |
| Chặng bay gõ `"HAN - SGN"` thay vì `"HAN-SGN"` (một dấu cách) | **Chặn cứng cả chứng từ gộp của chuyến** | Lộn ngược |
| Nhiệt độ 14 °C (thật ở miền Bắc mùa đông) | **Chặn cứng** | Lộn ngược |
| Xe khác thiếu số QC, người in hộ không có quyền sửa | **Chặn cứng** | Chặn nhầm người |
| Techlog để trống ⇒ ghi đè techlog của xe khác bằng chuỗi `"0"` | **Không cảnh báo gì**, mất dữ liệu im lặng | Nới nhầm chỗ |

Hai dòng cuối bảng cho thấy vấn đề không phải "hệ thống quá chặt", mà là **chặt và lỏng đặt sai chỗ**.

### 2.4 Hai lượt phân tích độc lập hội tụ ở đâu

Nửa đầu và nửa sau luồng được phân tích bởi hai lượt độc lập, không đọc kết quả của nhau. Bốn kết
luận trùng nhau — đây là **bằng chứng mạnh nhất** trong tài liệu này, vì chúng đến từ hai vùng code
khác hẳn:

1. **Cùng chỉ ra mẫu "hạ chặn cứng xuống cảnh báo hai nút có ghi log"** là lời giải đúng, và cùng chỉ
   ra rằng **mẫu đó đã tồn tại sẵn** trong chính file cần sửa — `confirmTimeWarningThenPost` ở màn
   Xác nhận, `warnReversedTimeThen` ở màn Xem trước. Không phải phát minh gì mới; chỉ là nhân rộng
   thứ đã được duyệt ở đợt trước.
2. **Cùng chỉ ra mẫu "một cờ boolean gánh hai khái niệm"** ở bốn chỗ khác nhau (§2.1, đường dây 3).
3. **Cùng chỉ ra rằng người bị chặn thường không có quyền sửa** — nửa đầu là ca danh mục nhân viên
   chưa đồng bộ (lỗi hệ thống, chặn người vận hành), nửa sau là ca in hộ (lỗi xe B, chặn xe A).
4. **Cùng phát hiện comment khẳng định đã sửa nhưng code không đúng vậy** (§8).

### 2.5 Hai lượt phân tích mâu thuẫn ở đâu

**Một mâu thuẫn thật, cần chủ dự án phân xử: GIỜ ĐẢO NGƯỢC (`end < start`) — chặn hay cảnh báo?**

| | Nửa đầu luồng (màn Xác nhận) | Nửa sau luồng (đường phiếu) |
|---|---|---|
| Kết luận | **Giữ chặn cứng.** Đây là ranh giới cuối, không được phá | **Hạ xuống cảnh báo hai nút.** Mẻ sai giờ có thể của **xe khác** mà người đang thao tác không sửa được |
| Lý lẽ | Dữ liệu tự mâu thuẫn không được lọt vào chứng từ | Chặn ở đây khoá luôn việc xuất hoá đơn của **cả chuyến** |

Hai bên đều đúng trong phạm vi của mình. **Hoà giải đề xuất — phán quyết theo QUYỀN SỬA, không theo
loại lỗi**: mẻ của **chính xe mình** ⇒ chặn cứng (người dùng sửa được, và sửa là đúng việc); mẻ của
**xe khác** ⇒ cảnh báo hai nút + ghi log, hoặc loại mẻ đó khỏi chứng từ. Nguyên tắc này giải quyết
luôn ca in hộ và nên được áp cho **mọi** luật chứng từ, không riêng giờ đảo ngược.

**Một va chạm cần điều phối, không phải mâu thuẫn**: cả hai lượt đều đề xuất sửa `BaseActivity` — một
bên đảo mặc định `onBackPressed`, một bên sửa lỗi `showInputData`. Cùng file, phải làm cùng đợt và
thử tay cùng lúc, vì cả hai đều ảnh hưởng mọi màn hình.

---

## 3. ĐỐI CHIẾU VỚI NGUYÊN TẮC NGHIỆP VỤ

| # | Nguyên tắc | Tình trạng | Bị vi phạm ở đâu |
|---|---|---|---|
| 1 | Không gián đoạn luồng; ưu tiên cảnh báo | **Vi phạm rộng** | Thang đo lộn ngược (§2.3): mẻ 0 phút, >11.000 GL, chặng bay có dấu cách, nhiệt độ, thời lượng >180' — đều chặn cứng ở nơi cảnh báo là đủ |
| 2 | Vẫn tra nạp/ghi nhận được chuyến **chưa phân công** | **Gần đạt** | Đường takeover đã được mở ở đợt trước và **đang đúng**. Còn tồn: mở chuyến chưa phân công phải chờ HTTP tới 10 giây với hộp Loading không tắt được; chuyến `CANCELLED` chạm vào chỉ hiện Toast rồi không mở |
| 3 | Chạy được khi **mất mạng hoàn toàn** | **Vi phạm nặng, hai chỗ** | (a) **Không xuất được hoá đơn** — tần suất 100%, kể cả chuyến một xe một mẻ. (b) Phiên đăng nhập 12 giờ hết hạn ⇒ đá về màn login, login chạy đồng bộ trên luồng UI, treo 10 giây rồi báo lỗi kết nối ⇒ **cả ca không làm việc được** |
| 4 | In phiếu thoải mái; dữ liệu chỉ lên server khi bấm XUẤT HOÁ ĐƠN | **Vi phạm** | Máy in **lỗi** vẫn bật hộp "Số hoá đơn" bắt buộc, không huỷ được; ở màn hoá đơn gõ xong là `save()` chạy ngay ⇒ dữ liệu lên server mang **số hoá đơn bịa**. Cộng lỗi `BaseActivity`: giá trị bị `onOK` từ chối vẫn được đẩy đi |
| 5 | Xe khác **in/xuất hộ được** nhưng **không sửa** dữ liệu chuyến | **Vi phạm hai chiều** | Chiều thiếu: in hộ **một mẻ** của xe khác tắc hẳn (lượt lọc mẻ không đủ điều kiện tự thoát khi chỉ có một mẻ), người bị chặn không có quyền sửa. Chiều thừa: techlog ghi đè `WeightNote` của **mọi** mẻ trong danh sách in, để trống ⇒ ghi đè bằng `"0"`, xe kia mất dữ liệu im lặng |
| 6 | Giảm số lần phải ký lại | **Vi phạm** | Ô "chờ ký" (`signType=3`) chết ở tầng gate client: tích vào **vẫn bị bắt chụp ảnh**. Bản in kim bị ẩn cả hai nút ký ⇒ **buộc chụp ảnh**, chậm nhất vào ca bận nhất. Chuỗi cảnh báo khi thoát vẫn nói "sẽ mất chữ ký" trong khi tính năng lưu tạm chữ ký đã làm điều đó không còn đúng |
| 7 | **Không được** phải khởi động lại app giữa ca | **Vi phạm nặng** | Bốn ngõ cụt thật: màn Xác nhận không nút thoát; huỷ hộp "lưu hỏng" ⇒ mọi nút trên màn chết cùng lúc; hộp nhập tay không huỷ được; ô chọn nhân viên không mở khi danh mục chưa đồng bộ. Cộng phím Back bị vô hiệu toàn app ⇒ không có đường lùi |

Ba nguyên tắc bị vi phạm **nặng nhất** là #3, #4 và #7 — và cả ba đều được gỡ phần lớn ngay trong đợt 1.

---

## 4. DANH MỤC VẤN ĐỀ THEO CỤM

Mỗi cụm: bản chất → hậu quả thực tế → đề xuất → công sức/rủi ro/ưu tiên. `file:line` gom cuối mỗi cụm
để người đọc nghiệp vụ không bị cản.

### CỤM 1 — Khoá ngoại tuyến ở đường hoá đơn *(ưu tiên #1, công sức NHỎ)*

**Bản chất.** Nút XUẤT HOÁ ĐƠN đặt cờ "in tất cả" vô điều kiện, nên chứng từ **luôn** bị coi là gộp
nhiều xe, kể cả chuyến một xe một mẻ. Từ đó nó đòi khoá dữ liệu xe khác từ server; server không trả
(hoặc trả `null`, hoặc mất mạng) ⇒ chặn cứng, không nút đi tiếp. Bản vá trước nhằm gỡ đúng ca này
**chưa bao giờ chạm tới nó**, vì cờ đã bật trước rồi.

**Hậu quả.** Sân đỗ 22h, 4G chập chờn, khách đứng chờ hoá đơn. Bấm XUẤT HOÁ ĐƠN → chờ Loading (rút
hàng đợi tối đa 30 giây + nhiều request, mỗi cái 10 giây kết nối / 30 giây đọc) → cuối cùng ra dòng
chữ *"Hãy bấm CẬP NHẬT khi có kết nối"*. Không nút đi tiếp. Cả chuyến tắc.

**Đề xuất.**
- **(a)** Bỏ vế `printMode == ALL_ITEM` khỏi `isCombinedDocument()`, chỉ còn "số mẻ > 1". Trường
  `printMode` **chỉ được đọc ở đúng dòng này** (hai chỗ dùng còn lại đã bị comment) ⇒ rủi ro hồi quy
  gần bằng không, và code khớp lại với chính javadoc của nó.
- **(b)** Hạ chặn "chưa nhận đủ dữ liệu xe khác" xuống **cảnh báo hai nút**, theo đúng khuôn đã có
  sẵn trong file. Hộp thoại **phải in số mẻ và tổng lít/kg sẽ lên chứng từ**, không nói chung chung;
  bấm "Vẫn xuất" thì ghi anomaly. Vẫn cần (b) dù đã có (a), vì chuyến nhiều xe + mất mạng vẫn phải in.
- **(c)** Đặt trần thời gian chờ: hạ 30 giây rút hàng đợi xuống 5 giây; bọc lượt làm mới trong
  deadline tổng ~10 giây, quá hạn thì đóng Loading, cảnh báo, đi tiếp. Nhánh đi tiếp **đã tồn tại
  sẵn**, chỉ thiếu đường kích hoạt theo thời gian.
- **(d) — việc của server, không chờ được:** API nên trả `"Others": []` thay vì `null`. Đó là chữa
  gốc, nhưng mất mạng thì server đúng cũng không cứu được ⇒ **làm (a)(b)(c) trước**.

> **✅ QUYẾT ĐỊNH ĐÃ CHỐT — chủ dự án, 2026-09-05.**
> Giữ **nguyên tắc cũ**: mất mạng vẫn tạo được phiếu, có mạng thì phiếu tự đẩy về.
> **KHÔNG khoá xuất nhiều mẻ khi mất mạng — chỉ CẢNH BÁO**, nội dung đại ý: *"Đang ở trạng thái không
> có mạng, lưu ý kiểm tra dữ liệu khi xuất."* Người dùng ngoài hiện trường sẽ tìm mọi cách để đồng bộ
> được dữ liệu; chặn họ lại không làm dữ liệu đúng hơn, chỉ làm tắc việc.
> ⇒ Phương án **(b) trở thành BẮT BUỘC**, không còn là tuỳ chọn. Cổng "chưa nhận đủ dữ liệu xe khác"
> hạ xuống cảnh báo hai nút ở **mọi** đường chứng từ, kể cả phiếu gộp nhiều mẻ và phiếu hoàn.

**Rủi ro.** Hoá đơn in thiếu mẻ ⇒ sai tiền. Phòng bằng nội dung hộp thoại (b) và log anomaly.
`file:line`: `RefuelPreviewActivity.java:457-460`, `:480-489`, `:1614-1616`, `:314-346`, `:358`,
`:382-389`; `HttpClient.java:1401-1407`, `:958-959`; `DataHelper.java:958-962`, `:752-754`.

### CỤM 2 — Ngõ cụt điều hướng *(ưu tiên #1, công sức VỪA)*

**Bản chất.** Không phải nhiều lỗi riêng lẻ mà **một quyết định kiến trúc nhân lên nhiều lần**: cấm
Back ở lớp cha, trong khi lý do chỉ đúng cho một trạng thái của một màn hình. Cộng thói quen
`setCancelable(false)` rải khắp nơi (có chỗ đặt **ba lần liên tiếp** cho cùng một dialog).

**Hậu quả.** Ba kịch bản đã dựng lại được từ code:
- Thiết bị lỗi ⇒ Force Stop ⇒ nhập tay ⇒ `StartTime = EndTime` ⇒ màn Xác nhận báo giờ không hợp lệ
  ⇒ **không có nút nào để lùi** ⇒ giết app tại chân tàu bay. Chuỗi này chạy đủ **100%** số lần, không
  phải xác suất.
- Mẻ đã bơm 8.000 GL, lưu hỏng, người dùng chạm ra ngoài hộp thoại theo phản xạ ⇒ trạng thái `ENDED`
  ⇒ nút Trở về disabled, Force Stop disabled, Huỷ INVISIBLE, Bắt đầu GONE — **mọi nút chết cùng lúc**.
- Tablet mới cài, danh mục nhân viên chưa đồng bộ, ngoài sân đỗ không sóng ⇒ ô chọn nhân viên bấm
  **không có gì xảy ra**, mà không chọn thì không lưu được ⇒ **ngõ cụt tuyệt đối**.

**Đề xuất — ba luật, áp một lần ở lớp nền, không vá lẻ từng màn.**
- **Luật 1:** Back mặc định **được phép**; cấm là ngoại lệ **có khai báo**. Chỉ màn tra nạp override,
  và chỉ chặn khi đồng hồ **đang chạy hoặc đang chốt số** — trạng thái `ENDED` không chặn nữa. Nút
  Trở về trên màn nối cùng điều kiện để nút và phím cứng nói cùng một điều.
- **Luật 2:** mọi `setCancelable(false)` phải kèm ít nhất một nút thoát. Ba chỗ vi phạm đã xác định:
  hộp nhập tay thêm nút **"Để sau"**; hộp lỗi nhập tay thêm nút **OK**; hộp "lưu hỏng" đổi
  `setCancelable(true)` → `false` (**sửa một chữ, rẻ nhất trong toàn tài liệu** — hộp này đã có sẵn
  hai lối ra tốt trong nút, chỉ cần bịt lối huỷ câm).
- **Luật 3:** mọi màn đầu-cuối phải có nút thoát trong layout. Màn Xác nhận thêm nút Trở về (an toàn:
  mẻ đã ghi xong trong Room trước khi vào màn này). Ô chọn nhân viên thêm nhánh báo lý do khi danh
  mục rỗng.

**Đã loại.** "Giữ nguyên cấm Back toàn cục, chỉ thêm nút cho ba màn đang hỏng" — rẻ hơn nửa ngày
công, nhưng **đây chính là cách hệ thống đi tới tình trạng hiện tại**; màn hình tiếp theo ai đó viết
sẽ lại thiếu nút. Cũng loại "chuyển sang `OnBackPressedDispatcher`" — đúng chuẩn hơn nhưng phải đụng
vòng đời của mọi Activity, rủi ro cao hơn giá trị thu được ở app đang chạy sản xuất.

**Rủi ro.** Một số màn chưa từng nhận Back nay nhận được — phải thử tay, đặc biệt màn phiếu (đã có
xác nhận thoát riêng, phải nối vào cơ chế mới để không bỏ qua xác nhận chữ ký).
`file:line`: `BaseActivity.java:75-78`, `:82-129`; `RefuelDetailActivity.java:1683`, `:2009-2019`,
`:2057-2062`, `:2325-2349`, `:2830-2846`; `RefuelDetailConfirmActivity.java:449-506`, `:824-862`;
`res/layout/activity_refuel_detail_confirm.xml:46,63`.

### CỤM 3 — Hộp "Số chứng từ" ép phát hành *(ưu tiên #2, công sức NHỎ→VỪA)*

**Bản chất.** Cờ `required` bị dùng cho **hai việc khác hẳn nhau**: "không được để trống" **và**
"không được huỷ". Ý đồ ban đầu không sai — đã in ra giấy mà không ghi số thì chứng từ giấy không khớp
hệ thống. Nhưng nó bị gắn vào callback máy in, **kể cả nhánh in LỖI**.

**Hậu quả.** Máy in kim/nhiệt lỗi là chuyện thường ngày ngoài sân đỗ. In lỗi ⇒ vẫn hỏi số hoá đơn ⇒
không huỷ được ⇒ lối ra duy nhất là gõ một chuỗi bất kỳ ⇒ ở màn hoá đơn, gõ xong `save()` chạy ngay
⇒ **dữ liệu lên server mang số hoá đơn bịa**. Ngược thẳng nguyên tắc "in sai thì sửa rồi in lại".
Nhập trùng số thì hộp thoại báo lỗi mà **không đóng** — cộng `setCancelable(false)` là nhốt hẳn.

**Lỗi thật, ngoài mô tả của bản kiểm kê:** `BaseActivity` gọi `onComplete.onCompleted()` **ngoài**
nhánh `onOK()` trả true. Nghĩa là một giá trị đã bị chính hàm kiểm tra **từ chối** vẫn được đẩy đi.
Không có lý lẽ nào bênh được hành vi này.

**Đề xuất.** (a) Đưa `onCompleted()` vào trong nhánh thành công — **một dòng, vá ngay, tách riêng**.
(b) Tách `required` thành hai cờ: "không được trống" và "được huỷ" (mặc định được huỷ); mọi hộp nhập
số chứng từ đặt được-huỷ, nút phụ ghi **"Để sau"**. (c) **Nhánh in LỖI không hỏi số chứng từ nữa** —
chỉ báo lỗi máy in + nút "In lại"; chưa in ra tờ giấy nào thì không có gì để đánh số. (d) Thay chặn
bằng **nhắc**: mẻ đã in thành công mà chưa có số thì hiện dấu cảnh báo cố định trên dòng mẻ và hỏi
lại khi rời màn — khuôn đã có sẵn ở màn hoá đơn.

**Rủi ro.** Đã in giấy mà hệ thống không có số ⇒ chứng từ giấy không khớp sổ. **(d) bắt buộc làm cùng
lúc với (b)(c), không được tách.** Thêm log để đối chiếu cuối ca.
`file:line`: `BaseActivity.java:306-325`; `RefuelPreviewActivity.java:152-170`, `:2733-2742`,
`:2792-2795`, `:3038-3042`; `PrintInvoiceActivity.java:54-76`, `:71`, `:290-297`, `:320`.

### CỤM 4 — Ca kết thúc thủ công tự mâu thuẫn *(ưu tiên #3, công sức VỪA)*

**Bản chất.** Hai đoạn code do hai lần sửa khác nhau viết ra, mỗi đoạn đúng theo lý lẽ riêng, **cộng
lại thành cái bẫy đóng kín** (§2.2 mục 1–3). Thêm một ngòi nổ chưa ai nêu: nếu giờ bắt đầu hoặc giờ
kết thúc là null thì hàm kiểm tra **ném NPE** ⇒ **crash app**, không phải hiện thông báo.

**Hậu quả.** Lối thoát duy nhất trên lý thuyết là chạm vào ô giờ và **tự bịa một con số** để qua cửa
— biến ô dữ liệu chứng từ thành ô gõ bừa. Kết cục tệ nhất: vừa mất thời gian, vừa làm bẩn dữ liệu,
vừa dạy người dùng thói quen điền số giả.

**Đề xuất.** (a) `finalizStop` **suy ra** giờ bắt đầu thay vì bỏ cuộc, theo thang ưu tiên dừng ở
nguồn đầu tiên có dữ liệu: mốc gói số liệu đầu tiên nhận được trong phiên → giờ tiếp cận nếu nằm
trong cửa sổ hợp lý → suy từ sản lượng chia lưu lượng danh định → cuối cùng mới `EndTime − 1 phút`
(**không phải `EndTime`**). Mọi nhánh suy diễn đặt cờ "giờ ước tính" hiển thị tại chỗ + ghi log nguồn
suy. (b) Hạ kiểm tra giờ ở màn Xác nhận từ chặn cứng xuống **cảnh báo hai nút**, dùng khuôn có sẵn
trong chính file. (c) Guard null, trả `false` thay vì ném NPE.

**Đã loại.** "Chỉ gỡ chặn, để nguyên `StartTime = EndTime`" — một dòng, gỡ ngõ cụt ngay, nhưng **cố
tình phát hành chứng từ ghi mẻ 0 phút**; một mẻ 8.000 GL kéo dài 0 giây là dữ liệu tự mâu thuẫn và
làm hỏng mọi báo cáo thời lượng. Cũng loại "chỉ sửa `finalizStop`, giữ chặn cứng" — vẫn để lại chặn
cứng nổ trên mẻ dài thật (tàu bay thân rộng có ca > 2 giờ).

**Rủi ro.** Giờ ước tính đi vào chứng từ — phòng bằng cờ hiển thị + log + ưu tiên tuyệt đối dữ liệu
thật. **Tuyệt đối giữ nguyên** cơ chế "giờ bắt đầu chỉ lấy từ sự kiện đồng hồ" đã được xác nhận đúng.
`file:line`: `RefuelDetailActivity.java:2270-2296`; `model/RefuelItemData.java:1417-1419`;
`RefuelDetailConfirmActivity.java:409-433`, `:513-535`, `:542-592`; `helpers/RefuelTimeValidator.java:113-139`.

### CỤM 5 — Mẻ 0 GL, hộp nhập tay, và mốc tiếp cận bị xoá *(ưu tiên #3, công sức VỪA)*

**Bản chất.** Ba chỗ cùng một giả định ngầm: **"đã tiếp cận thì chắc chắn đã bơm"**. Sai với nghiệp
vụ thật — xe ra tới tàu bay, ghi tiếp cận, rồi chuyến bị huỷ / đổi bãi / cơ trưởng từ chối / vòi hỏng.
**Mẻ 0 lít là sự kiện có thật, cần được ghi nhận** — nó là bằng chứng xe đã ra hiện trường và là số
liệu năng suất. Tệ hơn: lựa chọn "Huỷ tiếp cận" **xoá mốc tiếp cận có thật đã xảy ra** — đây không
phải chặn, đây là **mất dữ liệu do thiết kế**.

**Hậu quả.** 02:15 sáng, tiếp cận VN123 xong chuyến bị huỷ. Bấm "Rời đi" ⇒ chỉ có "Huỷ tiếp cận"
(xoá mốc thật) hoặc "Trở về" (không làm gì). **Chọn gì cũng sai.** Chọn "Trở về" thì chuyến này giữ
mốc tiếp cận mà không có mốc rời đi ⇒ **chặn luôn việc tiếp cận chuyến tiếp theo**. Ở hộp nhập tay:
bơm chưa ra giọt nào thì phát hiện vòi hỏng ⇒ nhập 0 ⇒ hộp lỗi không nút ⇒ quay lại hộp nhập ⇒ **vòng
lặp vô tận** ⇒ nhân viên gõ số bịa, vì đang đứng cạnh tàu bay.

**Đề xuất.** (a) Hộp nhập tay luôn có "Để sau" (Cụm 2 Luật 2). (b) Hộp lỗi có nút OK **và** nói rõ ô
nào sai. (c) Nới luật: `realAmount == 0` chuyển từ chặn sang **nhánh xác nhận riêng** ("Mẻ này không
nạp lít nào?"), đồng ý thì chốt mẻ 0 GL và đi tiếp; điều kiện "sản lượng ≥ số tổng" hạ xuống cảnh báo
hai nút cho ca đồng hồ mới thay. **Phải nới đồng bộ ở cả màn tra nạp và màn Xác nhận**, nếu không chỉ
là dời ngõ cụt sang màn sau. (d) Thêm lựa chọn thứ ba **"Vẫn ghi rời đi"** — giữ nguyên mốc tiếp cận,
chỉ đặt mốc rời đi, và dùng đường ghi bình thường thay vì đường fail-closed hiện tại.

**Rủi ro.** **Mẻ 0 GL có được server chấp nhận không?** Nếu API từ chối thì phần (c) chỉ chạy được ở
máy và sẽ kẹt trong hàng đợi đồng bộ vĩnh viễn — **phải hỏi backend trước khi code** (§6).
`file:line`: `RefuelDetailActivity.java:2009-2019`, `:2053-2062`;
`view/RefuelRecyclerViewAdapter.java:353-391`, `:403`; `RefuelDetailConfirmActivity.java:513-514`;
`helpers/DataHelper.java:3870-3873`.

### CỤM 6 — Thiết bị đo: mất nút, mất số liệu *(ưu tiên #4, công sức VỪA, RỦI RO HIỆN TRƯỜNG CAO)*

**Bản chất.** Bốn lỗi độc lập, cùng một hệ quả: **màn hình đứng im mà không ai biết vì sao**.
(i) Nút Dừng biến mất trên mọi dòng xe (§2.1 đường dây 1). (ii) Không bắt được sự kiện bắt đầu ⇒ code
gộp "không ghi" với "không hiển thị" làm một ⇒ nhân viên nhìn màn hình chết cứng 15 phút, không phân
biệt được với mất kết nối. (iii) Hàng đợi đăng ký trường của đồng hồ **kẹt vĩnh viễn** ở phần tử lỗi
— nhánh thành công có hai dòng dọn hàng đợi, nhánh lỗi thiếu (nhánh timeout thì lại làm đúng ⇒ đây là
lỗi sót, không phải thiết kế). (iv) Mất kết nối giữa mẻ ⇒ mọi gói bị vứt cho tới khi **có người bấm nút**.

**Hậu quả.** Đây là **nguồn** của Cụm 4 và Cụm 5: sửa (i) làm "kết thúc thủ công" từ **đường đi mặc
định** trở lại thành **ngoại lệ thật sự**, kéo tần suất của hai cụm kia xuống mức hiếm.

**Đề xuất.** (a) Trả lại nút Dừng bằng **một hàm quyết định hiển thị duy nhất** thay cho `GONE` rải
rác. (b) **Tách "hiển thị" khỏi "ghi"**: luôn cập nhật UI với số đọc được, chỉ đặt điều kiện trạng
thái quanh phần ghi vào phiếu; ô số ở chế độ chỉ-hiển-thị tô màu khác + nhãn "chưa ghi nhận". Thêm
nút **"Đánh dấu đã bắt đầu"** khi đồng hồ đang trả số tăng mà chưa bắt được sự kiện — **cắt tận gốc**
Cụm 4, vì có mốc thật thì không phải suy diễn. (c) Hai dòng dọn hàng đợi ở nhánh lỗi — **sửa rẻ nhất,
giá trị cao**. (d) Watchdog tự gọi nối lại khi phát hiện mất tín hiệu quá 2 chu kỳ, có giãn cách tăng
dần; cơ chế chống chồng socket sẵn có giữ nguyên làm lớp bảo vệ.

> **✅ QUYẾT ĐỊNH ĐÃ CHỐT — chủ dự án, 2026-09-05.**
> **Nút kết thúc thủ công (Force Stop) LUÔN PHẢI HOẠT ĐỘNG.** Không được gắn điều kiện "chỉ hiện khi
> mất kết nối đồng hồ", không được ẩn, không được vô hiệu hoá theo trạng thái thiết bị.
> Hiện nút này khai báo `visibility="visible"` ngay trong layout nên **luôn hiện** — **giữ nguyên như
> vậy**. (Trong lúc rà soát có đề xuất khoá nút này theo cờ `deviceIsError`; đề xuất đó **bị bác**.)
> Việc cần làm ở (a) vì thế **không phải** thay Force Stop, mà là **trả lại nút Dừng bình thường** để
> kết thúc từ app có hai đường: dừng bình thường khi đồng hồ còn kết nối, Force Stop khi không.
> Ràng buộc kèm theo: cả hai đường **đều phải dẫn về màn Xác nhận** — hiện đã đúng, không được phá.

**Rủi ro — cao nhất trong toàn tài liệu.** (a) làm **sống lại một đường code chưa từng chạy thật**
(pha ENDING 35 giây của TCS). Bắt buộc thử trên xe thật của **cả hai dòng thiết bị**; giữ nút Force
Stop nguyên vẹn làm lối thoát; cân nhắc cờ cấu hình để bật dần theo xe. **Cần một câu trả lời nghiệp
vụ trước** (§6): xe TCS có được điều khiển Bắt đầu/Dừng từ app không, hay bắt buộc thao tác tại đồng
hồ? Nếu bắt buộc tại đồng hồ thì giữ ẩn nút **và xoá code chết kèm ghi chú giải thích**.
`file:line`: `RefuelDetailActivity.java:663`, `:1264-1265`, `:1683`, `:2325-2349`, `:2362`, `:2450`,
`:711-786`, `:137`, `:171-178`; `helpers/LCRReader.java:737-776`, `:1131-1134`, `:519-521`, `:806-812`;
`sdk_tcs/sdk_tcs/tcs/TcsDevice.java:176-186`.

### CỤM 7 — Luật chứng từ nằm sai tầng *(ưu tiên #5, công sức LỚN)*

**Bản chất.** §2.1 đường dây 4. Hai bộ luật ở hai file, **không đồng bộ với nhau**, và bộ lọc mẻ xe
khác **thoát ngay khi chỉ có một mẻ** ⇒ ca in hộ một mẻ không được lọc gì cả.

**Hậu quả.** (i) **In hộ tắc hẳn**: máy in xe B hỏng, xe A in hộ đúng một mẻ của B; B chưa nhập số QC
⇒ chặn, mà A **không có quyền sửa** ⇒ ngõ cụt tuyệt đối, người bị chặn không phải người có lỗi. (ii)
**Một dấu cách làm hỏng cả chuyến**: chặng bay gõ `"HAN - SGN"` ⇒ cả phiếu gộp không dựng được. (iii)
**Bức tường sau lời mời**: mẻ 200 phút ⇒ thấy cảnh báo 60 phút ⇒ bấm "Tiếp tục" đúng như giao diện
mời ⇒ đâm thẳng vào chặn 180 phút không lối ra.

**Đề xuất.** (a) Rút luật ra khỏi hàm dựng model, gom vào **một lớp phán quyết duy nhất** trả về danh
sách vi phạm có phân mức CHẶN/CẢNH BÁO — cùng kiểu các lớp thuần Java đã có trong repo, test được.
Hàm dựng model **chỉ giữ lại** các `throw` cho thứ khiến model không dựng nổi về mặt kỹ thuật (thiếu
giờ, thiếu hãng bay). (b) **Phán quyết theo QUYỀN SỬA, không theo loại luật** — xem bảng hoà giải ở
§2.5; loại điều kiện "chỉ lọc khi có nhiều hơn một mẻ" (đây là **sửa duy nhất** cho ca in hộ). (c)
Thống nhất các mốc mâu thuẫn: chuẩn hoá chặng bay (bỏ dấu cách, viết hoa) **trước** khi so — một mình
việc này xoá phần lớn ca gặp thật; thời lượng và nhiệt độ hạ xuống cảnh báo có ghi log.
(d) **GIỮ NGUYÊN CHẶN, không nới**, cho nhóm luật ảnh hưởng **tiền** — tỉ trọng 0.72–0.86, sản lượng
> 0, giá, thuế suất — với mẻ của **chính xe mình**. Tỉ trọng vào công thức khối lượng ⇒ vào số tiền
trên hoá đơn. **Đây là ranh giới không được vượt và không đem ra bàn.**

**Đã loại.** "Bọc try/catch rồi thêm nút Bỏ qua" — cho đi tiếp **cả** luật ảnh hưởng tiền, và vẫn chỉ
báo được một vi phạm mỗi lần. "Chỉ thêm luật vào bộ lọc mẻ xe khác" — hai bộ luật vẫn ở hai file và
**sẽ lại lệch nhau ở lần sửa sau**; đúng căn bệnh đang có. Một nguồn sự thật là điều kiện để cụm này
không tái phát.

**Rủi ro.** Nới quá tay ⇒ phát hành hoá đơn sai số liệu (phòng bằng (d)); mẻ bị loại âm thầm ⇒ chứng
từ thiếu tiền (mọi lần loại phải toast tên xe + ghi anomaly + **hiện tổng lít/kg sau khi loại** trước
khi dựng chứng từ); hai bộ luật lại lệch (viết unit test đối chiếu hai chiều).
`file:line`: `model/ReceiptModel.java:123-232`, `:274`, `:307-310`;
`RefuelPreviewActivity.java:501-521`, `:549-551`, `:584-626`, `:671-703`, `:712-752`, `:988-1050`,
`:1339-1353`, `:1367-1393`, `:2136-2146`; `helpers/OthersFreshness.java:99-104`.

### CỤM 8 — Chặn câm và nuốt ngoại lệ *(ưu tiên #4, công sức NHỎ)*

**Bản chất.** Màn Xem trước có nhánh `return` **không một lời nào** khi thiếu dữ liệu đầu, và **toàn
bộ hàm** nằm trong một `catch (Exception)` chỉ ghi log (mà ghi bằng `getMessage()`, với NPE là `null`).
Người dùng bấm nút, **không có gì xảy ra**.

**Hậu quả.** Hiếm gặp nhưng **cực khó chẩn đoán ngoài sân đỗ**, và nó phá hỏng mọi nỗ lực chẩn đoán
của các cụm khác: một lỗi thuộc Cụm 1 hay Cụm 7 rơi vào `catch` này thì biến mất không dấu vết.

**Đề xuất.** Thay `return` câm bằng thông báo nói rõ lý do; `catch` phải hiện lỗi **và** ghi anomaly
kèm `toString()`; rà nốt các `return` không thông báo khác trên hai đường dựng chứng từ.
**Nên làm trước hoặc cùng Cụm 7** — nó là điều kiện để **đo được** hiệu quả của Cụm 7 ngoài hiện trường.
`file:line`: `RefuelPreviewActivity.java:1230-1238`, `:1327-1331`.

### CỤM 9 — Đăng nhập: vi phạm thẳng yêu cầu ngoại tuyến *(ưu tiên #6, công sức VỪA)*

**Bản chất.** Một biến boolean gánh ba câu hỏi (§2.1 đường dây 3) ⇒ **hết hạn token = mất luôn quyền
dùng app**. Màn đăng nhập gọi API **thẳng trên luồng UI**. Không có chế độ ngoại tuyến, không gia hạn
nền, không ân hạn.

**Hậu quả.** 02:00 sáng, sân đỗ không sóng backhaul, token hết hạn lúc 01:47. Gõ đúng mật khẩu ⇒ UI
**treo 10 giây** ⇒ "Lỗi kết nối". Thử lại, treo tiếp. **Không có cách nào vào app, toàn bộ dữ liệu ca
đêm không được ghi nhận.** Tần suất thấp hơn các cụm trên nhưng **mức nghiêm trọng cao nhất**: không
phải một mẻ hỏng mà **cả ca không làm việc được**.

**Đề xuất.** (a) Tách "còn gọi API được không" khỏi "còn được mở màn nghiệp vụ không"; khi có danh
tính mà phiên hết hạn thì chạy **chế độ chỉ-ghi-local** (kiến trúc local-first đã sẵn sàng — mọi
đường ghi đều vào Room trước rồi mới đồng bộ), kèm dải cảnh báo thường trực và **trần ân hạn** (đề
xuất 72 giờ) để tablet thất lạc không mở được vô hạn. (b) Đưa đăng nhập sang luồng nền, bỏ
`StrictMode.permitAll`, có tiến trình và nút Huỷ, giảm timeout riêng đường login — **nên làm bất kể
(a)**, treo UI 10 giây là lỗi độc lập. (c) Nếu API có refresh token thì gia hạn im lặng mỗi khi có
mạng — **cách sạch nhất, cần backend xác nhận**. (d) Dọn cờ `isDebug = BuildConfig.DEBUG || true` —
hiện nó **vô tình đang bảo vệ** luồng ngoại tuyến; ai đó gỡ `|| true` mà chưa sửa chỗ đọc danh sách
thì mất mạng = màn hình trống. Nên **cố định đường đọc Room**, biến hành vi đúng thành chính thức
thay vì tai nạn may mắn.

**Đã loại.** "Kéo dài hạn token lên 7 ngày" — vẫn có ngày thứ 8, lúc đó tắc y hệt nhưng hiếm hơn nên
khó chẩn đoán hơn; đồng thời nới an ninh mà không đổi lấy gì. "Cache mật khẩu để đăng nhập ngoại
tuyến" — lưu thông tin xác thực trên thiết bị hiện trường là rủi ro không đáng đánh đổi; thứ nhân
viên cần là **ghi được dữ liệu**, không phải **gọi được API**.

**Ghi chú an ninh.** Phương án (a) thực ra **chặt hơn hiện trạng** ở một điểm: hôm nay khi tắc, áp
lực công việc dẫn tới chia sẻ tài khoản hoặc mượn tablet người khác. Chế độ chỉ-ghi-local giữ đúng
danh tính người đã đăng nhập trong log, và **không cấp thêm quyền nào lên server** — mọi lệnh gọi API
vẫn cần token hợp lệ.
`file:line`: `FMSApplication.java:228-243`; `UserBaseActivity.java:50-54`; `LoginActivity.java:36-58`;
`HttpClient.java:954-959`; `helpers/DataHelper.java:103`; `RefuelListFragment.java:79-89`.

### CỤM 10 — Bằng chứng giao nhận và nghiệp vụ "chờ ký" *(ưu tiên #7, phụ thuộc quyết định ngoài client)*

**Bản chất.** Cửa duy nhất đẩy dữ liệu lên server đòi **đủ 2 chữ ký HOẶC có ảnh chụp** — đúng về
nguyên tắc. Nhưng bản in kim bị **ẩn cả hai nút ký** ⇒ mọi phiếu **bắt buộc chụp ảnh**, và code còn
ép mở máy ảnh ngay. Ô "chờ ký" đặt cờ `signType=3`; **đính chính bản kiểm kê**: cờ này **không chết ở
tầng dữ liệu** — nó vẫn được đẩy lên server — nó chỉ chết ở **tầng gate client**, nên người dùng tích
ô xong **vẫn bị bắt chụp ảnh**. Giao diện hứa một lối đi mà code không cho đi.

**Đề xuất.** (a) **Cần chốt với server/kế toán trước**: server có thực sự chấp nhận và xử lý
`signType=3` không? **Có** ⇒ bỏ yêu cầu ảnh khi ở trạng thái chờ ký, thay bằng xác nhận rõ ràng +
ghi anomaly — đây là **cách giảm số lần phải ký lại rẻ nhất** trong tài liệu. **Không** ⇒ **gỡ ô đó
khỏi giao diện**; để lại một lối đi không tồn tại còn tệ hơn không có. (b) Bỏ ẩn hai nút ký ở bản in
kim — màn ký đã có sẵn và dùng chung; ép chụp ảnh phiếu ngoài sân đỗ ban đêm là ép thao tác chậm nhất
vào ca bận nhất. (c) Sửa chuỗi cảnh báo khi thoát (§8) — một dòng.
`file:line`: `PrintReceiptActivity.java:161-166`, `:593-768`, `:712-758`, `:1210-1244`, `:884-889`;
`data/entity/Receipt.java:256-263`; `res/layout/activity_invoice.xml:208`; `res/values/strings.xml:604`.

### CỤM 11 — Techlog ghi đè dữ liệu xe khác *(ưu tiên #4 cho phần rẻ, công sức NHỎ)*

**Bản chất.** Techlog được ghi cho **mọi** mẻ trong danh sách in. Để trống ⇒ giá trị 0 ⇒ ghi đè bằng
chuỗi `"0"`. Nếu xe khác đã có techlog riêng, giá trị đó **mất im lặng** và xe kia phải vào web sửa
lại. Xảy ra ở **mỗi lần** xuất chứng từ gộp nhiều xe.

**Đề xuất.** (a) **Làm ngay:** techlog ≤ 0 thì **không đụng** trường đó. Sửa nhỏ nhất trong cả tài
liệu, chặn đứng một ca mất dữ liệu im lặng. (b) **Cần chốt nghiệp vụ:** techlog là số của **chuyến**
hay của **mẻ**? Của chuyến ⇒ giữ cách ghi chung nhưng **hiện giá trị hiện có trước khi ghi đè**; của
mẻ ⇒ chỉ ghi cho mẻ của xe mình.
`file:line`: `helpers/DataHelper.java:990-994`; `RefuelPreviewActivity.java:2966`, `:3059`;
`PrintReceiptActivity.java:855-858`, `:1190-1193`.

### CỤM 12 — Danh sách chuyến: NPE, lọc, chờ mạng *(ưu tiên #5, công sức NHỎ→VỪA)*

**Bản chất.** Bốn vấn đề nhỏ, ba là lỗi thuần tuý. (i) So chuỗi số xe **không guard null** ⇒ crash.
(ii) Truy vấn dùng `truckNo != :truckNo`; trong SQLite `NULL != 'X'` cho ra `NULL` chứ không phải
`TRUE` ⇒ phiếu thiếu số xe **rơi khỏi cả hai tab**. (iii) Chuyến `CANCELLED` chỉ Toast rồi thoát —
chuyến bị huỷ **sau khi** xe đã bơm là ca thật. (iv) Mở chuyến chưa phân công phải chờ HTTP tới 10
giây với hộp Loading không tắt được.

**Cảnh báo quan trọng: (i) và (ii) phải sửa CÙNG LÚC.** Hiện (ii) đang **giấu** các phiếu thiếu số xe
đi, nên (i) hiếm khi nổ. Sửa (ii) mà quên (i) sẽ **biến một lỗi hiếm thành lỗi thường xuyên**.

**Đề xuất.** Guard null bằng cách đảo vế (hằng số đứng trước); dùng `IFNULL(truckNo,'') <> :truckNo`
để phiếu thiếu số xe hiện ở tab "xe khác" — đúng ngữ nghĩa; chuyến huỷ hạ Toast thành **dialog hai
nút** có ghi anomaly như đường takeover; và **đọc Room trước, làm mới sau** cho ca chưa phân công —
vẽ màn hình ngay rồi cập nhật khi có kết quả, **giữ nguyên** cơ chế chống response cũ đè bản mới.

**Đã loại.** "Giảm timeout xuống 3 giây" — làm hỏng các đường khác dùng chung tầng HTTP (đồng bộ nền
cần timeout dài), và 3 giây đứng màn hình vẫn là 3 giây không cần thiết khi dữ liệu **đã nằm sẵn
trong Room**. Nghịch lý đáng chú ý: mất mạng **hẳn** thì nhanh (fail ngay); wifi có sóng nhưng không
có backhaul mới là ca xấu nhất.
`file:line`: `view/RefuelRecyclerViewAdapter.java:118-123`, `:149-150`, `:156-165`;
`data/dao/RefuelItemDao.java:47,50,53`; `RefuelDetailActivity.java:439-455`;
`helpers/DataHelper.java:1314-1330`.

### CỤM 13 — Mẻ thứ hai cùng chuyến cùng xe *(ưu tiên #8, chờ chốt nghiệp vụ)*

**Bản chất.** Không phải điểm chặn cố ý mà **một ca nghiệp vụ chưa từng được thiết kế**. Ba đoạn code
độc lập vô tình bịt kín cả ba lối; lối duy nhất còn lại là "Tra nạp mới", mà nút đó bị ẩn nếu thiếu
quyền tạo chuyến.

**Hậu quả.** Tàu bay thân rộng tiếp thêm sau khi mẻ đầu đã chốt. Nhân viên không có quyền ⇒ **không
có bất kỳ đường nào** ghi mẻ thứ hai ⇒ hoặc gọi điều hành xin quyền (mất thời gian, cần mạng), hoặc
**ghi dồn vào mẻ cũ** — làm sai cả cặp số đồng hồ lẫn giờ, và mẻ cũ có thể **đã xuất chứng từ**.

**Đề xuất.** Đây là **mục cần chốt nghiệp vụ trước, không phải mục code trước**. Nếu cho phép: thêm
nút "Tra nạp thêm mẻ" ở màn Xem trước, tạo **phiếu mới** cùng chuyến cùng xe; quyền cho nút này
**không nên dùng quyền tạo chuyến mới** — người đã tra nạp mẻ đầu hiển nhiên được tra nạp tiếp cùng
chuyến đó. Nếu không cho phép: giữ nguyên nhưng **thay im lặng bằng thông báo rõ ràng** kèm hướng dẫn
quy trình thay thế.

**Đã loại.** "Cho mở lại phiếu đã chốt để bơm tiếp" — ít code nhất nhưng **phá thẳng bất biến quan
trọng nhất của hệ thống**: chặn "đã hoàn tất → chưa hoàn tất" là cơ chế chống ghi đè cốt lõi, và mẻ
đã có số hoá đơn còn bị đóng băng ở cả server. Tạo phiếu mới là hướng duy nhất an toàn — **cần backend
xác nhận có chấp nhận nhiều bản ghi cùng chuyến cùng xe không**.
`file:line`: `view/RefuelRecyclerViewAdapter.java:149-150`, `:156-165`, `:184-190`;
`RefuelPreviewActivity.java:1600-1603`; `MainActivity.java:167-179`.

---

## 5. LỘ TRÌNH ĐỀ XUẤT — MỘT LỘ TRÌNH HỢP NHẤT

Hai lượt phân tích đều đặt cụm của mình ở vị trí **#1**. Cả hai đều có lý, và may mắn là **cả hai đều
rẻ** ⇒ gộp chung vào đợt 1.

| Đợt | Nội dung | Công sức | Rủi ro | Gỡ được vi phạm nguyên tắc |
|---|---|---|---|---|
| **1** — "Hết ngõ cụt, hết khoá ngoại tuyến" | Cụm 1 (a)(b)(c) · Cụm 2 toàn bộ · Cụm 3 (a) · Cụm 8 · Cụm 11 (a) · Cụm 12 (i)(ii) · Cụm 6 (c) · Cụm 7 phần rẻ: bỏ điều kiện "chỉ lọc khi >1 mẻ" + chuẩn hoá chặng bay · Cụm 10 (c) | Nhỏ–Vừa | **Thấp** | **#3** (một nửa), **#5**, **#7** |
| **2** — "Không ép phát hành chứng từ sai" | Cụm 3 (b)(c)(d) | Vừa | Trung bình | **#4** |
| **3** — "Hoàn thành được phiếu ở ca kết thúc thủ công" | Cụm 4 toàn bộ · Cụm 5 toàn bộ · phần hạ chặn ở màn Xác nhận (>11.000 GL, ô số đồng hồ đầu, danh mục nhân viên rỗng) | Vừa | Trung bình | **#1** |
| **4** — "Trả lại quyền điều khiển thiết bị" | Cụm 6 (a)(b)(d) | Vừa | **CAO — bắt buộc thử xe thật cả hai dòng** | Làm **cạn nguồn** của đợt 3 |
| **5** — "Ngoại tuyến thật sự" | Cụm 9 (a)(b)(d) | Vừa | Trung bình | **#3** (nốt nửa còn lại) |
| **6** — "Một nguồn luật chứng từ duy nhất" | Cụm 7 đầy đủ · Cụm 12 (iii)(iv) | **Lớn** | Trung bình–cao | **#1** và **#5** triệt để |
| **7** — Phụ thuộc quyết định ngoài client | Cụm 10 (a)(b) · Cụm 11 (b) · Cụm 13 · Cụm 9 (c) · API trả `Others: []` | — | — | **#2**, **#6** |

**Vì sao thứ tự này.** Đợt 1 rẻ, rủi ro thấp, và **gỡ mọi tình huống buộc khởi động lại app** — thứ
đề bài nói thẳng là không chấp nhận được. Sau đợt 1, mọi chặn còn lại **đều có đường lùi**, nên các
đợt sau bớt cấp bách và có thể làm kỹ. Đợt 2 đứng trước đợt 3 vì rủi ro **chứng từ pháp lý** cao hơn
rủi ro trải nghiệm. Đợt 4 đứng sau đợt 3 dù giá trị dài hạn cao hơn, vì nó làm sống lại code chưa
từng chạy thật — nhưng **đây là đợt quan trọng nhất về dài hạn**: nó biến "kết thúc thủ công" từ
đường mặc định trở lại thành ngoại lệ, làm phần lớn thứ sửa ở đợt 3 ít khi bị dùng tới.

**Điều phối bắt buộc.** Đợt 1 đụng `BaseActivity` từ hai hướng (đảo mặc định Back + sửa lỗi hộp nhập)
⇒ phải làm cùng nhánh và thử tay **toàn bộ các màn** một lượt, không tách hai người làm song song.

**Nếu backend xác nhận có refresh token**, đôn đợt 5 lên ngang đợt 3.

---

## 6. VIỆC CẦN CHỐT TRƯỚC KHI CODE

### 6.1 Câu hỏi nghiệp vụ (chủ dự án / vận hành / kế toán trả lời)

| # | Câu hỏi | Khoá đợt nào | Vì sao cần |
|---|---|---|---|
| N1 | Xe TCS **có được** bấm Bắt đầu/Dừng từ app không, hay bắt buộc thao tác tại đồng hồ? | Đợt 4 | Một câu trả lời khoá cả một cụm sửa. "Không" ⇒ giữ ẩn nút và **xoá code chết** |
| N2 | Một chuyến + một xe **có được nhiều mẻ** không, và ai được phép tạo? | Đợt 7 | Hiện không có đường nào ghi mẻ thứ hai |
| N3 | **Mẻ 0 lít** có phải sự kiện cần ghi nhận không (xe đã ra hiện trường, chuyến bị huỷ)? | Đợt 3 | Quyết định (c) của Cụm 5 |
| N4 | **Giờ đảo ngược**: chặn cứng hay cảnh báo? Có phân biệt mẻ của mình / mẻ xe khác không? | Đợt 3, 6 | **Đây là chỗ hai lượt phân tích mâu thuẫn** (§2.5) |
| N5 | **Thời lượng mẻ tối đa** là bao nhiêu? Hiện có **ba mốc khác nhau** trong code: 60 / 120 / 180 phút | Đợt 3, 6 | Không thể thống nhất nếu chưa có con số đúng |
| N6 | **Dải nhiệt độ hợp lệ** 15–40 °C có đúng cho mùa đông miền Bắc không? | Đợt 6 | 14 °C là số đo thật, đang bị chặn cứng |
| N7 | **Techlog** là số của **chuyến** hay của **mẻ**? | Đợt 7 | Quyết định có được ghi chung lên mẻ xe khác không |
| N8 | Nghiệp vụ **"chờ ký"** (phát hành trước, bổ sung chữ ký sau) có thật không? | Đợt 7 | Có ⇒ làm sống; không ⇒ gỡ ô khỏi giao diện |
| N9 | **Trần ân hạn ngoại tuyến** bao lâu là chấp nhận được về an ninh? (đề xuất **72 giờ**) | Đợt 5 | — |
| N10 | Chuyến **đã huỷ** có được tra nạp tiếp không (ca huỷ sau khi xe đã bơm)? | Đợt 6 | — |

> **Không đem ra bàn:** dải tỉ trọng 0.72–0.86. Nó vào công thức tính khối lượng ⇒ vào số tiền trên
> hoá đơn. Giữ chặn cứng cho mẻ của chính xe mình.

### 6.2 Câu hỏi cho backend (gửi đi ngay từ đợt 1 để câu trả lời kịp cho các đợt sau)

| # | Câu hỏi | Khoá đợt nào | Hậu quả nếu trả lời "không" |
|---|---|---|---|
| S1 | API có **chấp nhận `RealAmount = 0`** không? | Đợt 3 | Mẻ 0 GL sẽ **kẹt trong hàng đợi đồng bộ vĩnh viễn** — phần (c) Cụm 5 phải đổi hướng |
| S2 | Có **refresh token** không? | Đợt 5 | Mất lời giải sạch nhất cho phiên hết hạn; vẫn làm được ân hạn ngoại tuyến |
| S3 | Có chấp nhận **nhiều bản ghi cùng (chuyến, xe)** không? | Đợt 7 | Cụm 13 chuyển từ "vừa" sang "lớn", phải đổi server |
| S4 | Có thể trả **`"Others": []`** thay vì `null` khi chuyến không có mẻ xe khác không? | Đợt 7 | Không chặn tiến độ — đợt 1 đã xử lý ở client |
| S5 | Server có thực sự **xử lý `signType=3`** (chờ ký) không? | Đợt 7 | Gỡ ô "chờ ký" khỏi giao diện |
| S6 | Cửa sổ **3 ngày** của danh sách hoá đơn xe là giới hạn API hay giới hạn client? | — | Chỉ ảnh hưởng trải nghiệm tra cứu |

---

## 7. NHỮNG CHỖ ĐANG ĐÚNG — KHÔNG ĐƯỢC ĐỤNG

Ghi lại để đợt sửa sau không vô tình phá.

| Nội dung | Vì sao giữ |
|---|---|
| **Hỏi mở lại chuyến còn dở khi vào màn chính** | Cơ chế cứu dữ liệu sau khi app bị kill. **Là lưới an toàn cho phương án "Để sau"** ở Cụm 2 và 5 ⇒ càng phải giữ |
| **Chốt "giờ bắt đầu chỉ lấy từ sự kiện đồng hồ"** | Nền tảng của toàn bộ độ tin cậy giờ tra nạp. Cụm 4 và Cụm 6 đều **xây quanh nó**, không được phá |
| **Chặn "đã hoàn tất → chưa hoàn tất"**, kiểm tra danh tính response, kiểm `clientSeq` | Cơ chế chống ghi đè cốt lõi. Cụm 13 chọn hướng "tạo phiếu mới" chính vì không được đụng vào đây |
| **Đường takeover chuyến chưa phân công** (vừa mở ở đợt trước) | Đúng nguyên tắc #2 |
| **Cơ chế chặn tiếp cận chuyến mới khi chưa rời chuyến cũ** | **Thiết kế tốt nhất trong toàn bộ kiểm kê**: có lý do nghiệp vụ, có lối xử lý ngay trong dialog, có cửa sổ thời gian, fail-open khi lỗi DB. **Nên dùng làm khuôn mẫu** |
| **Mẫu cảnh báo hai nút có ghi log** (giờ bất thường / giờ đảo ngược khi xuất hoá đơn) | **Mẫu chuẩn của toàn hệ thống.** Mọi đề xuất "hạ chặn cứng xuống cảnh báo" trong tài liệu này đều dùng lại mẫu này, không phát minh mới |
| **Checklist an toàn trước khi bơm; không gửi lệnh vào kết nối đã chết; chống chồng socket** | An toàn hiện trường và an toàn kết nối |
| **Chỉ đường kết thúc mới đặt trạng thái hoàn tất; chống dồn request** | Đúng luật, đang chạy tốt |
| **Mẻ xe khác read-only ở màn Xem trước** (fail-closed hai lớp) | Khớp chính xác nguyên tắc #5 |
| **Đã có số hoá đơn ⇒ khoá sửa** | Server đã đóng băng nhóm sản lượng; cho sửa là lừa người dùng. Việc **không** dùng "đã in"/"số phiếu"/"số lần in" làm dấu hiệu khoá chính là thứ giữ cho **"in phiếu thoải mái"** hoạt động |
| **Khoá 3 giây nút XUẤT** | Chống phát hành hai lần, có lưới tự mở |
| **Nút kết thúc thủ công (Force Stop) luôn hiện, luôn bấm được** | **Chủ dự án chốt ngày 2026-09-05.** Đây là lối thoát cuối cùng khi thiết bị đo hỏng. Không được gắn điều kiện `deviceIsError`, không ẩn, không vô hiệu hoá |
| **Hai đường kết thúc đều dẫn về màn Xác nhận** | Đồng hồ tự kết thúc → `finalizStop` → lưu → Xác nhận; Force Stop → nhập tay → cùng đích. Đúng nghiệp vụ, đang chạy tốt |
| **Phiếu ghi vào máy trước rồi mới đẩy lên** (`isLocalModified` + lượt quét đẩy lại) | Nền của nguyên tắc "mất mạng vẫn tạo được phiếu, có mạng thì tự đẩy về". Mọi thay đổi ở đường chứng từ **không được** biến việc tạo phiếu thành phụ thuộc mạng |
| **In lỗi không chặn xuất hoá đơn** | Đúng nguyên tắc |
| **Toàn bộ 9 hạng mục vừa sửa ở đợt trước** — cảnh báo trường đồng hồ, giữ số đồng hồ đầu khi mất tín hiệu, làm mới mẻ xe khác trước khi dựng chứng từ (kèm bốn điểm bảo vệ), hoá đơn trùng, màn ký, xử lý mã lỗi HTTP có điểm dừng | Đã qua kiểm toán độc lập. **Không đề xuất lại, không viết đè lên** |
| `AppDatabase` **version 14**, không migration; hai ngưỡng lọc LCR nguyên văn | Vùng cấm đã tuyên bố ở đợt trước |

**Một mục sát ranh giới:** popup bắt buộc "chuyến này chưa ghi nhận tiếp cận" nổ gần như mỗi chuyến.
Đang **đúng** về nội dung, nhưng nếu sau các đợt sửa vẫn thấy phiền thì cân nhắc hạ xuống dải nhắc
tại chỗ. Không phải ưu tiên đợt này.

---

## 8. SAI LỆCH GIỮA TÀI LIỆU / CHÚ THÍCH VÀ CODE THẬT

Những chỗ chú thích khẳng định một đằng, code làm một nẻo. Đây là **nguồn hiểu nhầm cho mọi người
đọc code sau**, nên dọn cùng đợt sửa tương ứng.

| # | Chỗ | Chú thích nói | Code thật | Dọn ở đợt |
|---|---|---|---|---|
| 1 | `RefuelPreviewActivity.java:462-470` | Đã sửa việc "hoá đơn bị chặn ngay cả khi chỉ chọn đúng MỘT mẻ" | **Chưa sửa** — cờ gộp vẫn bật vì `printMode` đặt trước | 1 |
| 2 | `RefuelPreviewActivity.java:455-460` javadoc | *"Quyết định theo DỮ LIỆU sắp in, không theo enum của nút đã bấm"* | Thân hàm **vẫn xét enum** | 1 |
| 3 | `RefuelDetailActivity.java:2830-2840` | *"Người đứng tại tàu bay phải luôn còn đường đi tiếp"* | Ngay dưới đó `setCancelable(true)` tạo ngõ cụt | 1 |
| 4 | `res/values/strings.xml:604` | Thoát ra "sẽ mất chữ ký trước đó và cần phải ký và in lại" | **Sai** — chữ ký đã được lưu tạm và sẽ được mời dùng lại. Lời thoại sai đang **tự tạo ra một điểm chặn tâm lý** mà code đã gỡ rồi | 1 |
| 5 | `helpers/OthersFreshness.java:20-30` | *"Không đẻ ra đường chặn mới"* | Đúng với lớp đó, nhưng **gây hiểu nhầm**: đường chặn cũ vẫn nguyên và nay áp cho mọi hoá đơn | 1 (kèm ghi chú) |
| 6 | `helpers/UpdateSensitiveScreen.java:17` | Mô tả cơ chế chặn hộp thoại cập nhật | **Marker chết** — không nơi nào trong `app/src/main/java` đọc interface này; màn Xem trước cũng không khai báo nó | 6 |
| 7 | `RefuelDetailConfirmActivity.java:303-305` | (đã comment) | `onClick` vẫn mở hộp thoại sửa "số đồng hồ đầu"; giá trị gõ vào **bị nuốt im lặng** | 3 |
| 8 | `RefuelDetailActivity.java` — `stopTCS()` + hằng số chờ 35 giây | Được viết như code đang chạy | **Code chết** vì đường vào đã bị `GONE` | 4 |
| 9 | `helpers/DataHelper.java:103` | `isDebug = BuildConfig.DEBUG \|\| true` | Dấu vết tạm, hiện **vô tình đang bảo vệ** luồng ngoại tuyến | 5 |
| 10 | `findings.md [T1-07]` | Màn Xác nhận gọi nhánh fail-closed | **Đã lỗi thời** — đợt trước đã mở đường takeover. Đã được đính chính trong kiểm kê | (đã dọn) |
| 11 | Bản kiểm kê R2 nói `signType=3` là "cờ chết" | — | **Đính chính**: chết ở tầng gate client, **không chết ở tầng dữ liệu** — cờ vẫn được đẩy lên server. Điều này đổi hẳn hướng xử lý Cụm 10 | (đã đính chính) |
| 12 | Bản kiểm kê R1 xếp nút Dừng biến mất là "không rõ ý đồ", chỉ với TCS | — | **Nâng mức**: là **lỗi nghiêm trọng**, và ảnh hưởng **mọi dòng xe** | (đã đính chính) |

---

## 9. MỘT NHẬN XÉT CUỐI

Sáu mươi mốt điểm chặn này không phải sáu mươi mốt quyết định tồi. Phần lớn từng là quyết định **đúng
trong phạm vi hẹp của nó** — cấm Back để khỏi thoát nhầm giữa mẻ, bắt nhập đủ số để phiếu không rỗng,
`StartTime = EndTime` để không ghi giờ lệch bốn tiếng, đòi khoá dữ liệu xe khác để tổng không thiếu.

Vấn đề là **không có ai đứng ở chỗ đủ xa để nhìn thấy chúng cộng lại**.

Hai thay đổi đáng ưu tiên nhất đều **nhỏ về lượng code**: bỏ một điều kiện thừa trong hàm quyết định
"chứng từ gộp" mở lại toàn bộ đường xuất hoá đơn khi mất mạng; và đảo mặc định của phím Back bảo đảm
rằng **mọi sai sót còn lại — kể cả những cái chưa ai tìm ra — chỉ tốn một cú bấm, chứ không tốn một
lần khởi động lại app giữa lúc đứng cạnh tàu bay**.
