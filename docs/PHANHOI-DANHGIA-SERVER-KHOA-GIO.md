# Phản hồi đánh giá của đội server — khoá giờ tra nạp

**Ngày:** 27-08-2026
**Từ:** đội app
**Về:** đánh giá tài liệu `YEUCAU-SERVER-API-KHOA-GIO-TRA-NAP.md`

Đánh giá rất kỹ và bắt đúng nhiều chỗ tài liệu chúng tôi thiếu. Dưới đây là phần chấp nhận,
hai điểm cần đính chính, và mấy dữ kiện phía app mà đội server đang phải phỏng đoán.

---

## A. Hai điểm cần đính chính trước, vì chúng đổi việc phải làm

### A1. **DEMO-03 là MÁY THỬ, không phải xe hiện trường** — đừng sửa SQL cho phiếu `e5afa312`

Đây là điểm quan trọng nhất trong phản hồi này. Đánh giá viết:

> *"vậy DEMO-03 là tên xe thật đang chạy hiện trường, không phải máy thử"*

Không đúng, và lỗi diễn đạt là của tài liệu chúng tôi. Bằng chứng:

| Dấu hiệu | Giá trị |
|---|---|
| Package | `com.megatech.fms` — **debuggable** (`adb run-as` chạy được) |
| Tệp CSDL | `fms_debug.db` |
| Cấu hình xe | `Code: DEMO-03`, `TruckId: 34`, `IsFHS: false` |
| Lịch sử cài đặt | Bản debug được cài lên chính máy này lúc 14:40 và 14:45 ngày 27-08 |

Toàn bộ dữ liệu khung giờ **13:41–14:43** là **phiên thử nghiệm của đội app** trên máy nối
cáp, không phải thao tác của nhân viên tại sân đỗ.

**Hệ quả:**

- **Không cần chạy SQL sửa `RefuelItems` + `ReceiptItems` cho `e5afa312`.** Đó là bản ghi thử.
  Cảm ơn đề nghị viết câu SQL, nhưng xin dừng lại — sửa dữ liệu production dựa trên bản ghi
  thử là rủi ro không cần thiết.
- Câu *"nhân viên đang thực sự bị chặn ngay tại sân đỗ"* chưa có bằng chứng. Cơ chế thì có
  thật và sẽ chặn xe thật, nhưng 6/6 lượt từ chối đó là số liệu phòng thử.
- Ngược lại, **mục 6 (`Others: null`) đúng là từ xe ngoài hiện trường** — log 18:26:52 đến từ
  một máy khác, không phải DEMO-03. Chúng tôi chưa có số xe/mã chuyến của ca đó; đã ghi rõ
  phần "Thông tin cần bổ sung" trong tài liệu gốc.

Điều này **không hạ mức ưu tiên mục 1**: `Status == DONE` vẫn khoá mọi mẻ đã hoàn thành trên
mọi xe. Chỉ là chưa nên coi phiếu `e5afa312` là ca cần cứu gấp.

### A2. Rủi ro 1 **đã được xử lý** — tài liệu các anh đọc là bản cũ

Đánh giá trích mục 8:

> *"App tự tính lại đúng điều kiện timeLocked (Status == DONE || Printed || ReceiptNumber != null)"*

Đó là bản tài liệu cũ. Lỗi của chúng tôi: bản cập nhật không được ghi đè đúng chỗ. Mã app hiện
tại **đã áp luật mới**, không sao chép luật cũ của server:

```java
public boolean isMeasuredTimeLockedOnServer() {
    return invoiceNumber != null && !invoiceNumber.trim().isEmpty();
}
```

**Nên hai bên không cần deploy đồng thời.** App đang mở hộp thoại cho những ca mà luật mới cho
phép; hiện API còn từ chối, app giữ nguyên giá trị người dùng và gửi lại. API deploy xong là
thông ngay, không cần chạm vào app.

Về nhận xét *"app không nên nhân bản luật của server"* — đồng ý hoàn toàn về nguyên tắc. Điều
kiện hiện tại chỉ là biện pháp tạm để người dùng không gõ vào chỗ chắc chắn hỏng. Khi
`RejectReason` có, chúng tôi sẽ bỏ hẳn phần tự đoán và chuyển sang xử lý theo mã lý do.

---

## B. Chấp nhận, không tranh luận

### B1. Cột khoá — đồng ý siết lại

Lập luận đúng: `RefuelItems.InvoiceNumber` bị chính client ghi được qua
`RefuelsController.cs:1193`, nên khoá dựa vào nó là để client tự quyết được thời điểm khoá
mình. Đề xuất của các anh tốt hơn:

```csharp
bool timeLocked = !string.IsNullOrEmpty(model.InvoiceNumber) || model.Exported;
```

Chúng tôi đồng ý, và cũng đồng ý **không** thêm truy vấn bảng `Invoices` mỗi lượt POST — cái
giá đó không tương xứng.

Đề nghị kèm: nếu `InvoiceNumber` không nên do client ghi thì nên **bỏ hẳn** đường ghi ở
`:1193`. App không có nhu cầu tự đặt số hoá đơn; để cột đó chỉ có một người ghi thì luật sạch
hơn nhiều.

### B2. Rủi ro 2 — liên kết phiếu đứt khi sửa giờ. Phát hiện quan trọng nhất trong đánh giá

Chúng tôi hoàn toàn bỏ sót chỗ này. Phép tra cứu tại `ReceiptModel.cs:522` nối bằng
`r.StartTime == item.StartTime`, nên sửa `StartTime` sau khi đã có phiếu là làm đứt liên kết —
đúng cơ chế đã khiến 2120355/56/58 mất `ReceiptId`.

Đồng ý: **mở đường sửa giờ sau khi in thì bắt buộc phải cập nhật `ReceiptItems` trong cùng
transaction.** Không làm thì mỗi lần sửa lại sinh thêm một bản ghi mất liên kết.

Chúng tôi đề nghị đi xa hơn một bước: nối bằng mốc thời gian là mong manh về bản chất. Đã có
`UniqueId` và `ReceiptUniqueId` thì nên chuyển phép nối sang khoá định danh, tách khỏi việc
sửa giờ. Việc đó nằm ngoài phạm vi bản vá này, nhưng nên vào danh sách nợ kỹ thuật.

### B3. Rủi ro 3 — `REJECT_STALE_POST`. App cung cấp dữ kiện để các anh yên tâm bật

Đồng ý rằng sau mục 1 thì cần thứ thay thế, và `ClientSeq` là căn cứ đúng chứ không phải mốc
thời gian.

**Dữ kiện phía app:** `clientSeq` là trường thường (không `transient`) của `RefuelItemData`,
nên **được serialize vào mọi payload POST**. Nhật ký sản xuất xác nhận: mọi dòng
`POST_EXCHANGE` đều có `seq=` và `baseSeq=` với giá trị thật.

Nên rủi ro "chặn nhầm app cũ không gửi ClientSeq" chỉ còn với các bản app rất cũ. Đội server có
thể tra nhanh bằng `App-Version` header mà app gửi kèm mỗi POST (`App-Version:114-1`) để biết
còn bao nhiêu máy dưới ngưỡng trước khi bật.

### B4. Mục 4 — đồng ý hạ ưu tiên, kèm một lưu ý

Đúng: chuỗi 17→51 do chính `timeLocked` sinh ra, làm mục 1 xong thì nguyên nhân gần như biến
mất. Đồng ý hạ từ "Cao" xuống "Trung bình, giữ làm lớp phòng vệ".

Lưu ý giữ lại: lỗi không nằm ở `timeLocked` mà ở chỗ `dataApplied` đánh dấu "khối thân đã chạy"
thay vì "có trường nào thực sự đổi". Bất kỳ loại từ chối nào thêm về sau cũng sẽ vấp lại đúng
chỗ đó.

**Trả lời câu hỏi về ngữ nghĩa revision:** app **không** dùng revision tăng làm tín hiệu ACK.
Ngay trong mã đã có ghi chú:

```java
// Revision tăng KHÔNG có nghĩa là payload của ta được ghi: server có thể apply
// một phần...
```

Thứ tự app dùng: `Applied` nếu có → nếu không thì đối chiếu giá trị chốt của gói gửi với gói
nhận. Revision chỉ dùng để phân định **thứ tự cũ/mới**, không dùng để kết luận đã ghi.

Nên đổi ngữ nghĩa sang "đếm lượt có thay đổi" là **an toàn cho app**, không cần app sửa gì.

### B5. Mục 5 — đồng ý về `RejectedFields`

Nhận xét đúng: một `bool` cho một request áp dụng được một phần là mô hình thiếu. Trước mắt
`Applied` + `RejectReason` là đủ vì sau mục 1 diện từ chối hẹp lại nhiều.

Khi cần mở rộng, app tiêu thụ được danh sách ngay — không cần đợi phiên bản app mới, chỉ cần
thêm trường vào DTO như quy ước `null` = *server chưa hỗ trợ*.

### B6. Mục 6 — đồng ý đảo trọng tâm

Đúng: sau mục 1, chiều nguy hiểm không còn là "app bị khoá" mà là **"web sửa được cả sau khi
đã xuất hoá đơn"**, và hiện không ai canh. Áp cùng biểu thức vào `FlightsController.cs:2543` là
việc nhỏ và làm luật nhất quán.

Việc `EditExtract:491` không ghi `ChangeLogs` — đồng ý bịt bất kể chốt hướng nào.

### B7. Mục 7 — đồng ý hạ xuống giá trị pháp y, và phải chạy trước deploy

Đồng ý cả hai vế. Bổ sung: với đính chính A1, phép đếm này còn cho biết **có bao nhiêu ca thật
ngoài hiện trường** đã bị bỏ âm thầm — con số đó mới là căn cứ để vận hành quyết có truy lại
hay không, thay vì suy từ phiên thử của chúng tôi.

---

## C. Điểm tài liệu bỏ sót — đồng ý, và app có bằng chứng bổ trợ

Nhận xét đúng và quan trọng: `timeLocked` chỉ chặn việc **sửa**, không phải nguồn **sinh** dữ
liệu sai. Nguồn thật là `FlightsController.cs:3034` đặt `StartTime = DateTime.Now` lúc phân xe.

**App đã sửa đúng lỗi cùng dạng ở phía mình** (commit `99abc9a`), và mô tả trùng khớp:

> *"RefuelItemData initialises startTime to `new Date()`, so it is never null and the plan
> value survives when the meter's start event is missed."*

Tức cả hai phía từng cùng mắc một lỗi: lấy "thời điểm tạo bản ghi" làm "giờ bắt đầu tra nạp".
App đã bỏ; server thì `:3034` chưa.

**Bằng chứng hiện trường:** nhật ký các xe cho thấy sự kiện start của đồng hồ **thường xuyên**
không bắt được, và khi đó app buộc phải lấy giờ bấm kết thúc làm giờ bắt đầu:

```
[RFW] Không bắt được sự kiện start của đồng hồ, lấy giờ bấm kết thúc làm giờ bắt đầu
```

Kết quả là `StartTime == EndTime` — đúng hình dạng đang thấy. Nên nhu cầu sửa giờ tay ở hiện
trường là **thường xuyên**, không phải ngoại lệ hiếm. Đây là lý do nghiệp vụ đứng sau mục 1.

---

## D. Thứ tự đề nghị — đồng ý, với một sửa đổi

Đồng ý toàn bộ thứ tự các anh đề nghị, **trừ việc số 1**:

| # | Việc | Ghi chú |
|---|---|---|
| ~~0~~ | ~~Sửa SQL phiếu `e5afa312`~~ | **Bỏ** — bản ghi thử, xem A1 |
| 1 | `grep "TIME LOCKED"` production | Trước mọi deploy |
| 2 | Sửa `FlightsController.cs:3034` dùng sentinel | Chặn nguồn sinh dữ liệu sai |
| 3 | ~~Đồng bộ app bỏ điều kiện tự đoán~~ | **Đã xong**, xem A2 — không chặn deploy |
| 4 | Deploy mục 1 + 4 + 5, kèm quyết định `ReceiptItems` (B2) | |
| 5 | Chốt `REJECT_STALE_POST` và mục 6 | |

Việc 3 rơi ra khỏi đường găng, nên có thể deploy API độc lập.

---

## E. Về đề nghị gộp đánh giá thành một trang

Chúng tôi nghĩ **không cần trang riêng**. Nội dung đánh giá nên gộp thẳng vào
`YEUCAU-SERVER-API-KHOA-GIO-TRA-NAP.md` để chỉ còn một tài liệu là nguồn chuẩn — tài liệu hiện
tại đã sai một lần vì tồn tại hai bản (xem A2), không nên lặp lại.

Chúng tôi đã cập nhật mục 9 của tài liệu gốc cho khớp mã app hiện tại. Phần còn lại (cột khoá,
`ReceiptItems`, `REJECT_STALE_POST`, `FlightsController:3034`) nhờ các anh bổ sung trực tiếp,
vì đó là vùng mã của các anh.

---

## F. Câu hỏi còn mở

1. **Số xe và mã chuyến** của ca `Others: null` lúc 27-08 18:26:52 — chúng tôi chưa có. Cần
   biết chuyến đó **mấy xe** để xác định mục 6 là nhiễu log hay đang chặn in phiếu gộp thật.
2. `InvoiceNumber` có nên tiếp tục cho client ghi ở `:1193` không (xem B1).
3. Quyết định về `ReceiptItems` ở B2 — cập nhật kèm, hay đổi phép nối sang khoá định danh.
