# Yêu cầu sửa API — khoá giờ tra nạp và versioning

**Ngày lập:** 27-08-2026
**Gửi:** đội WebAPI / Web
**Nguồn dữ liệu:**
- Mục 3, 4, 5 (khoá giờ, versioning, DTO): máy thử DEMO-03 (`SOFTWARE_ID_87c513d529f3cef7`), phiếu `uid = e5afa312-407e-4d05-8d59-391916938c50`, chuyến VU 635-01, khung giờ 27-08-2026 13:41–14:43
- Mục 6 (`Others: null`): **xe ngoài hiện trường**, ca 27-08-2026 18:26:52 — chưa có số xe/mã chuyến, xem "Thông tin cần bổ sung" trong mục đó

---

## 1. Tóm tắt

Đã xác định được vì sao app không sửa được giờ tra nạp: **đó là hành vi cố ý của API** (`timeLocked` trong `RefuelsController.cs`). App không có lỗi ở chỗ này và không thể tự khắc phục.

Nhưng **điều kiện khoá đang đặt sai mốc**, cộng thêm **ba lỗi thật** quanh cơ chế đó, và **một bất đối xứng web/app** cần người quyết nghiệp vụ chốt.

Tài liệu này **không** đề nghị bỏ `timeLocked`, mà đề nghị **đặt lại mốc khoá cho đúng**: hiện `Status == DONE` khoá ngay từ lúc bấm kết thúc mẻ, trong khi mốc chốt sổ thật sự là lúc xuất hoá đơn.

---

## 2. Bằng chứng

App gửi giờ mới, server nhận bản ghi nhưng trả lại giờ cũ. Lặp lại 6/6 lần:

```
req  amount=33  end=6546  endTime=2026-08-27T13:50:26
res  amount=33  end=6546  endTime=2026-08-27T13:44:26
result=CONFLICT:PAYLOAD_MISMATCH endTime(13:50:26 -> 13:44:26)
```

Mọi trường khác **khớp**; chỉ riêng `endTime` bị giữ nguyên giá trị cũ. Nghĩa là API vẫn xử lý cập nhật bình thường, chỉ bỏ qua đúng trường thời gian — khớp với:

```csharp
// RefuelsController.cs:1105
bool timeLocked = model.Status == REFUEL_ITEM_STATUS.DONE
                || model.Printed
                || model.ReceiptId != null;
```

App gửi `Status` = `"3"` (chuỗi, do enum khai `@SerializedName("3")`), tức payload có `Status = DONE`, nên cửa `refuel.Status == DONE` được thoả. Xem mục 8 về cách xác nhận chắc chắn.

---

## 3. Việc cần sửa #1 — đặt lại mốc khoá: chỉ khoá khi đã xuất hoá đơn

**Mức độ: sai nghiệp vụ, ưu tiên cao nhất.**

`Status == DONE` được đặt **ngay khi bấm kết thúc mẻ**, tức rất lâu trước khi chốt sổ. Lấy nó
làm mốc "dữ liệu đã chốt" nghĩa là **mọi mẻ đã hoàn thành đều không sửa được giờ từ app** —
kể cả khi chưa in gì, chưa xuất hoá đơn nào.

Ca thật đang gặp, phiếu `e5afa312-407e-4d05-8d59-391916938c50`:

| Trường | Giá trị | Có khoá không |
|---|---|---|
| `Status` | `3` (DONE) | **có** |
| `ReceiptId` / `ReceiptNumber` | `2619HG8` | **có** |
| `Printed` | `false` | không |
| `InvoiceNumber` | `null` | — |

Phiếu **chưa xuất hoá đơn**, chưa nhận số hoá đơn, nhưng vẫn bị khoá bởi hai vế.

Comment trong `RefuelsController.cs:1105` nói mục đích là bảo vệ *"du lieu da chot"*. Nhưng
"đã bơm xong" và "đã chốt sổ" là hai chuyện khác nhau. Giữa hai mốc đó, hiện trường vẫn còn
nhu cầu chính đáng: sửa giờ nhập sai, sửa giờ khi đồng hồ không bắt được sự kiện start
(ca này xảy ra thường xuyên — xem `StartTime == EndTime` trong chính phiếu trên).

**Luật mới đã được chốt phía nghiệp vụ: chỉ khoá khi ĐÃ CÓ SỐ HOÁ ĐƠN.**

```csharp
// thay cho: model.Status == DONE || model.Printed || model.ReceiptId != null
bool timeLocked = model.InvoiceNumber != null;
```

Ba vế cũ đều bỏ:

| Vế cũ | Vì sao bỏ |
|---|---|
| `Status == DONE` | Đặt ngay khi bấm kết thúc mẻ, rất lâu trước khi chốt sổ |
| `ReceiptId != null` | Phiếu tra nạp được cấp ngay khi in phiếu giao nhận, chưa phải chốt sổ |
| `Printed` | Cùng lý do — in phiếu không phải mốc chốt sổ |

Một hệ quả cần đội server biết để cân nhắc khi hiện thực: bỏ `Printed` nghĩa là một phiếu đã
in ra giấy mà chưa xuất hoá đơn thì giờ vẫn sửa được, nên tờ giấy đã in có thể lệch với dữ
liệu trong hệ thống. Nếu điều đó không chấp nhận được thì cần quay lại chốt với nghiệp vụ,
**chứ không tự thêm lại vế `Status == DONE`** — vế đó mới là thứ đang chặn hiện trường.

---

## 4. Việc cần sửa #2 — `ServerRevision` tăng dù trường bị từ chối

**Mức độ: lỗi, ưu tiên cao.**

`dataApplied` chỉ đánh dấu "khối thân đã chạy" (`RefuelsController.cs:1188`), còn nhánh `timeLocked` nằm bên trong khối đó. Nên khi giờ bị bỏ, `dataApplied` vẫn `true` và `model.ServerRevision++` vẫn chạy (`:1237`).

**Vì sao nghiêm trọng.** Client dùng `ServerRevision` để phân định bản nào mới hơn. Mỗi lần server từ chối một trường, nó lại tự làm bản ghi của mình "mới hơn" bản client. Hệ quả:

- Client thấy `remoteBehindLocal = false` nên nhận bản server là mới, kéo lùi dữ liệu local.
- Người dùng bấm lại → revision lại tăng → vòng lặp. Đây chính là chuỗi revision `17 → 51` đã ghi nhận.

**Đề nghị:** chỉ tăng `ServerRevision` khi thực sự có trường nghiệp vụ thay đổi. Nếu toàn bộ thay đổi trong request đều bị từ chối thì giữ nguyên revision.

---

## 5. Việc cần sửa #3 — thiếu `Applied` / `RejectReason` trong response DTO

**Mức độ: thiếu tính năng, ưu tiên cao.**

Hiện `RefuelViewModel` / `BaseViewModel` không có hai trường này. Response là `BuildViewModel(db, model.Id)` — thuần dữ liệu bản ghi, không mang trạng thái xử lý. `dataApplied` chỉ vào log text rồi biến mất.

**Hệ quả phía app:** app không biết vì sao bị từ chối, nên chỉ hiện được thông báo chung chung *"Sửa phiếu chưa lưu được"*. Người dùng không biết phải làm gì tiếp.

App **đã có sẵn** hai trường này ở phía nó (`RefuelItemData.applied`, `RefuelItemData.rejectReason`), kèm quy ước rõ: `null` nghĩa là *server chưa hỗ trợ*, và **không được** hiểu `null` thành `false`. Nên chỉ cần server bắt đầu gửi là app dùng được ngay, không cần đổi gì thêm.

**Đề nghị:**

```
Applied      : bool    — request này có được ghi hay không
RejectReason : string  — mã lý do khi Applied = false, ví dụ "TIME_LOCKED"
```

Với `timeLocked`, đề nghị `Applied = false`, `RejectReason = "TIME_LOCKED"`. App sẽ hiện đúng lý do và hướng dẫn người dùng.

---

## 6. Việc cần sửa #4 — `Others` trả về `null` thay vì mảng

**Mức độ: sai hình dạng dữ liệu, ưu tiên trung bình–cao.**

Nhật ký app lặp liên tục, mỗi phiếu trong mỗi lượt đồng bộ một dòng:

```
[27-08-2026 18:26:52] [SYNC] OTHER_SNAPSHOT_INVALID: Others không phải JSON array
[27-08-2026 18:26:52] [SYNC] OTHER_SNAPSHOT_INVALID: Others không phải JSON array
... (6 dòng trong 1 giây)
```

Nguyên nhân: response **có** khoá `Others` nhưng giá trị là `null`, không phải mảng.

Đây là điều kiện xác định trong mã app — dòng log đó chỉ có đúng một đường phát sinh, nên
không phụ thuộc máy nào:

```java
if (!root.has("Others")) return item;              // vắng khoá → im lặng
JSONArray rawOthers = root.optJSONArray("Others");
if (rawOthers == null) {                            // có khoá nhưng KHÔNG phải mảng
    Logger.appendLog("SYNC", "OTHER_SNAPSHOT_INVALID: Others không phải JSON array");
```

**Mẫu đã xác minh được:** trên máy thử DEMO-03, cả ba phiếu đều lưu `Others = null` trong
payload nguyên bản nhận từ server. Đây là chuyến **một xe**, nên ở đó hậu quả chỉ là nhiễu log.

**Ca đang báo lại đến từ xe NGOÀI HIỆN TRƯỜNG, không phải máy thử.** Nếu đó là chuyến **nhiều
xe**, hậu quả nặng hơn hẳn: app không nhìn thấy mẻ của các xe khác nên **không in được phiếu
gộp**, và đó là mất nghiệp vụ thật. Cần đội server tra đúng bản ghi của xe đó (xem mục
"Thông tin cần bổ sung" bên dưới) để xác định `Others` đang `null` trong ca một xe hay ca
nhiều xe.

### Ba giá trị, ba ý nghĩa khác nhau

App phân biệt rạch ròi ba trường hợp, và đây là điểm mấu chốt:

| Server gửi | App hiểu | Hệ quả |
|---|---|---|
| **Không có khoá `Others`** | "Response này không nói gì về collection" | Im lặng, dùng cache đã có. Bình thường. |
| **`"Others": []`** | "Đã xác nhận: chuyến này không có mẻ xe khác" | `collectionComplete = true` → **in được phiếu gộp** |
| **`"Others": null`** ← hiện tại | Không rõ nghĩa, coi như dữ liệu hỏng | Ghi cảnh báo, `collectionComplete = false` → **chặn phiếu gộp** |

`null` là lựa chọn tệ nhất trong ba, vì nó không phân biệt được "không có mẻ nào" với "không
trả collection trong response này".

### Hệ quả

Parser này dùng chung cho **bốn** đường: GET chi tiết, GET danh sách, GET modified, và response
của POST. Nên tác động khác nhau tuỳ đường:

- **Response POST và GET danh sách:** chỉ là nhiễu log. Những endpoint đó vốn không phải nguồn
  có thẩm quyền của collection.
- **GET chi tiết (màn hình xem trước):** có hại thật. `collectionComplete` không bao giờ đạt
  nên **phiếu gộp bị chặn**, dù chuyến chỉ có một xe và lẽ ra in được bình thường.

Ngoài ra lượng log này đủ lớn để đẩy các dòng đáng chú ý khác ra khỏi tầm nhìn khi tra sự cố.

### Đề nghị

Chọn một trong hai, tuỳ ý nghĩa thật của từng endpoint:

- **Endpoint CÓ trả collection** (GET chi tiết): gửi `"Others": []` khi chuyến không có mẻ xe
  khác. App sẽ hiểu là "đã xác nhận rỗng" và in gộp được.
- **Endpoint KHÔNG trả collection** (danh sách, modified, response POST): **bỏ hẳn khoá
  `Others`** khỏi payload. App đã xử lý đúng trường hợp vắng khoá và không ghi log gì.

Điểm cần tránh: **đừng gửi `null`**.

### Thông tin cần bổ sung

Log gốc chỉ có 6 dòng cảnh báo, không kèm số xe / mã chuyến / thời điểm đầy đủ, nên chưa tra
được bản ghi cụ thể. Cần bổ sung để đội server đối chiếu:

- Số xe và mã chuyến của ca 27-08-2026 18:26:52
- Chuyến đó có **bao nhiêu xe** tham gia
- Vài dòng log ngay TRƯỚC 6 dòng cảnh báo — có `[PRW] Start loading` (đường GET chi tiết,
  nghiêm trọng) hay `[SYNC] START Receipt` (pull nền, chỉ là nhiễu)

Phía app cố ý **không** tự coi `null` tương đương "không gửi", vì làm vậy là che mất tín hiệu
đang chỉ đúng vào chỗ dữ liệu chưa chuẩn. Khi server sửa xong thì cảnh báo tự hết.

---

## 7. Cần chốt nghiệp vụ — bất đối xứng web/app

**Mức độ: không phải lỗi kỹ thuật, cần người quyết nghiệp vụ.**

Cùng một dữ liệu, cùng một quy tắc, nhưng hai đường vào có hai luật khác nhau:

| Đường | Cơ chế | Có bị `timeLocked` không |
|---|---|---|
| App | qua WebAPI `RefuelsController` | **Có** |
| Web | ghi thẳng EF (`FlightsController.cs:2543`, `EditExtract :491`) | **Không** |

Web gọi `TryUpdateModel(model)` rồi gán thẳng `model.StartTime` / `model.EndTime` và `db.SaveChanges()` — không kiểm `Status`, không kiểm `Printed`, không qua guard nào.

Nên hiện tại **web sửa được giờ, app thì không** — không phải vì quy tắc nghiệp vụ khác nhau, mà vì kiến trúc khác nhau.

Hai hướng, cần chọn một:

- **Giờ tra nạp là bất biến sau khi chốt** → web cũng phải bị khoá. Đường sửa hợp lệ nên là một thao tác riêng có kiểm soát và có ghi vết ai sửa, lúc nào.
- **Được phép sửa có kiểm soát** → nên mở cho app đúng phạm vi đó, để người dùng không phải rời hiện trường vào web chỉ để sửa một mốc giờ.

Trước mắt quy trình tạm thời là **sửa trên web** — đường đó có sẵn, không cần endpoint mới.

---

## 8. Cần xác nhận — production đang chạy bản nào

Triệu chứng "mọi trường khác khớp, riêng `endTime` giữ giá trị cũ" khớp với `timeLocked`, **nhưng bản HEAD cũng tạo ra đúng triệu chứng đó** nếu `refuel.Status != DONE`, vì cửa `if (refuel.Status == DONE)` có trong cả hai bản.

Cách phân định dứt điểm, không cần app làm gì:

```
grep "TIME LOCKED EndTime" ~/logs/<thư mục theo mã xe>
```

- **Có dòng đó** → chắc chắn đang chạy bản `timeLocked`, và biết luôn nó lên production từ ngày nào.
- **Không có** → đang chạy HEAD, và nguyên nhân là binding `Status`; khi đó cần kiểm server có nhận `Status` dạng **chuỗi** `"3"` thành enum hay không.

Phía app đã xác nhận payload mang `Status = "3"`.

---

## 9. Phía app đã làm gì (để đội server nắm bối cảnh)

Không chờ server, app đã sửa các phần thuộc trách nhiệm của mình:

- **Đã áp LUẬT MỚI, không sao chép luật cũ của server.** App chặn hộp thoại sửa giờ theo đúng
  mốc hoá đơn:

  ```java
  public boolean isMeasuredTimeLockedOnServer() {
      return invoiceNumber != null && !invoiceNumber.trim().isEmpty();
  }
  ```

  Nên khi API đổi sang mốc hoá đơn, hai bên khớp nhau ngay, không cần deploy đồng thời. Trong
  lúc chờ, phiếu đã DONE mà chưa xuất hoá đơn sẽ **mở hộp thoại cho sửa** rồi bị API từ chối —
  app giữ nguyên giá trị người dùng và gửi lại, không tự đè về bản server.
- **Không gửi lại vô ích.** Khi nhận ra phiếu bị khoá giờ, app dừng giữ giá trị và ghi `TIME_LOCKED_BY_SERVER` vào nhật ký bất thường.
- **Không âm thầm mất dữ liệu.** Trước đây app tự nhận giờ của server và xoá cờ chờ gửi, nên giá trị người dùng vừa nhập biến mất không dấu vết. Nay mọi lần từ chối đều để lại `SERVER_REJECTED_TIME_EDIT` kèm giá trị đã gửi và giá trị server trả về.

---

## 10. Tóm tắt việc cần làm

| # | Việc | Loại | Ưu tiên |
|---|---|---|---|
| 1 | Đổi `timeLocked` thành **chỉ `InvoiceNumber != null`** | Sai nghiệp vụ | **Cao nhất** |
| 2 | Không tăng `ServerRevision` khi mọi thay đổi đều bị từ chối | Lỗi | Cao |
| 3 | Thêm `Applied` + `RejectReason` vào response DTO | Thiếu tính năng | Cao |
| 4 | `Others`: gửi `[]` hoặc bỏ hẳn khoá, **đừng gửi `null`** | Sai hình dạng | Cao nếu là chuyến nhiều xe |
| 5 | Chốt: web có bị khoá giờ như app không | Nghiệp vụ | Trung bình |
| 6 | `grep "TIME LOCKED EndTime"` để xác nhận bản production | Xác minh | Làm trước #2 |

**Mục 1 là thứ chặn nghiệp vụ hiện trường ngay lúc này** — sửa giờ tra nạp. Mục 2 và 3 làm cho
việc từ chối trở nên minh bạch và không phá versioning, nhưng tự chúng không mở lại được đường
sửa giờ. **Mục 4 chặn một nghiệp vụ khác**: in phiếu gộp — và mức độ của nó phụ thuộc câu trả lời cho "chuyến của xe đó có mấy xe". Một xe thì chỉ là nhiễu log; nhiều xe thì mất nghiệp vụ thật.
