# Cách tạo phiếu hoàn trả — để dựng lại trên web

Tài liệu mô tả **đúng cách app Android đang dựng phiếu hoàn trả**, để web làm ra được phiếu
tương đương. Mọi số hiệu dòng trỏ tới `app/src/main/java/com/megatech/fms/model/ReceiptModel.java`
trừ khi ghi khác.

---

## 1. Hoàn trả là gì

Sau khi bơm, có thể phải hút ngược một phần nhiên liệu ra khỏi tàu bay. Phần đó ghi vào mẻ
(`RefuelItems`) qua hai cột:

| Cột | Ý nghĩa |
|---|---|
| `ReturnAmount` | Lượng hoàn trả, **theo đơn vị người dùng nhập** |
| `ReturnUnit` | `0 = KG`, `1 = GALLON` |

Từ **cùng một tập mẻ**, hệ thống sinh ra **hai phiếu bù nhau**:

| | Phiếu thường | Phiếu hoàn trả |
|---|---|---|
| `IsReturn` | `false` | **`true`** |
| Số phiếu | `2619FML` | `2619FML` **`HT`** |
| Lượng trên dòng | nạp **trừ** hoàn trả | **đúng phần** hoàn trả |
| Số đồng hồ | `StartNumber += returnG` | `EndNumber = StartNumber + returnG` |

Cộng hai phiếu lại đúng bằng lượng đồng hồ đã chạy. Đó là bất biến cần giữ.

---

## 2. Đầu vào cho mỗi mẻ

| Trường | Dùng để |
|---|---|
| `ReturnAmount`, `ReturnUnit` | tính lượng hoàn trả |
| `Density` | quy đổi kg ↔ lít |
| `RealAmount` / `Gallon` | lượng đã nạp |
| `StartNumber`, `EndNumber` | số đồng hồ |
| `StartTime`, `EndTime` | mốc giờ phiếu |
| `ManualTemperature`, `QualityNo` | in trên phiếu |
| `AircraftCode`, `AircraftType`, `RouteName`, `FlightCode` | phần đầu phiếu |
| `AirlineModel` (tên, mã, địa chỉ, mã số thuế) | phần khách hàng |
| `DriverId`, `OperatorId` | chữ ký |

**Chỉ mẻ có `ReturnAmount > 0` mới sinh dòng hoàn trả** (dòng 346). Mẻ không có hoàn trả vẫn nằm
trong phiếu nhưng dòng của nó giữ nguyên lượng nạp — cần xem lại mục 7.2.

---

## 3. Điều kiện tiên quyết — chặn cứng trước khi dựng phiếu

Áp cho **từng mẻ**, sai một mẻ là từ chối cả phiếu (dòng 131–222):

| # | Điều kiện | Thông báo khi vi phạm |
|---|---|---|
| 1 | `StartTime` và `EndTime` đều có | thiếu thời gian bắt đầu hoặc kết thúc |
| 2 | `EndTime >= StartTime` | thời gian kết thúc nhỏ hơn thời gian bắt đầu |
| 3 | `EndTime − StartTime <= 180 phút` | thời gian tra nạp quá dài |
| 4 | `15 <= ManualTemperature <= 40` | nhiệt độ không hợp lệ |
| 5 | `AircraftCode` không rỗng | số hiệu tàu bay không được để trống |
| 6 | `AircraftType` không rỗng | loại tàu bay không được để trống |
| 7 | `RouteName` khớp `^[A-Z]{3}-[A-Z0-9]{3,}$` | chặng bay không hợp lệ (dạng HAN-SGN) |
| 8 | `0.72 <= Density <= 0.86` | Density phải nằm trong khoảng 0.72 đến 0.86 |

**Chỉ cảnh báo, không chặn:** nếu hãng thuộc `AirlineId ∈ {1, 3, 476, 489, 497}` và các mẻ có
tỷ trọng khác nhau → cảnh báo tỷ trọng không đồng nhất.

**Cảnh báo khi nhập lượng hoàn trả** ([RefuelDetailConfirmActivity.java:337](../app/src/main/java/com/megatech/fms/RefuelDetailConfirmActivity.java:337)):

- `returnG > RealAmount` → hoàn trả nhiều hơn đã nạp
- Giá trị sau làm tròn khác giá trị vừa nhập → báo lại con số thực sẽ dùng

---

## 4. Chuẩn hoá lượng hoàn trả — **chỗ dễ sai nhất**

Dòng 346–357. Đọc kỹ: **gallon là đơn vị gốc**, kg chỉ là đầu vào để suy ra gallon rồi tính
ngược lại. Không phải quy đổi một chiều.

```
GALLON_TO_LITTER = 3.7854

// bước 1 — suy ra gallon
returnA = item.ReturnAmount
returnV = round(returnA / item.Density)
returnG = round(returnV / 3.7854)

if (item.ReturnUnit == GALLON)
    returnG = item.ReturnAmount          // nhập thẳng gallon thì bỏ bước 1

// bước 2 — LUÔN tính lại lít và kg TỪ gallon đã làm tròn
returnV = round(returnG * 3.7854)
returnA = round(returnV * item.Density)
```

Bước 2 chạy **cho cả hai đơn vị**. Nghĩa là nhập 1.000 kg không nhất thiết ra đúng 1.000 kg trên
phiếu — nó đi qua gallon rồi quay lại, và app báo cho người dùng con số cuối cùng.

Ví dụ kiểm chứng, `Density = 0.7930`:

| Nhập | Đơn vị | `returnG` | `returnV` | `returnA` |
|---|---|---|---|---|
| 1.000 | KG | `round(round(1000/0.793)/3.7854)` = 333 | `round(333×3.7854)` = 1.261 | `round(1261×0.793)` = 1.000 |
| 500 | GALLON | 500 | `round(500×3.7854)` = 1.893 | `round(1893×0.793)` = 1.501 |

Làm tròn: **nửa lên** (`Math.round` của Java), làm tròn về **số nguyên**, không giữ phần thập phân.

---

## 5. Dựng dòng phiếu

Dòng phiếu khởi tạo bằng cách **sao chép nguyên mẻ** rồi ghi đè (dòng 338–345):

```
line = copy(refuelItem)
line.RefuelItemId = item.UniqueId
line.RefuelId     = item.Id
line.Temperature  = item.ManualTemperature      // KHÔNG phải Temperature đo tự động
line.QualityNo    = item.QualityNo
line.DriverId     = item.DriverId
line.OperatorId   = item.OperatorId
```

Rồi, khi `ReturnAmount > 0`:

**Phiếu hoàn trả** (`IsReturn = true`, dòng 360–365):
```
line.Gallon    = returnG
line.Volume    = returnV
line.EndNumber = line.StartNumber + returnG
receipt.ReturnAmount += returnA
```

**Phiếu thường** (`IsReturn = false`, dòng 366–370):
```
line.Gallon      = line.Gallon - returnG
line.Volume      = line.Volume - returnV
line.StartNumber = line.StartNumber + returnG
```

---

## 6. Dựng phần đầu phiếu

Lấy từ **mẻ đầu tiên** trong danh sách (dòng 249–280):

```
UniqueId     = GUID mới
Id, LocalId  = 0
IsReturn     = true
CustomerId   = item.AirlineId
CustomerName = item.InvoiceNameCharter, rỗng thì lấy AirlineModel.Name
CustomerCode / CustomerAddress / TaxCode  = từ AirlineModel
CustomerType = AirlineModel.IsInternational ? 1 : 0
FlightType   = item.IsInternational ? 1 : 0
ProductName  = AirlineModel.ProductName
SignType     = 1
RefuelMethod = REFUELER
```

Nếu **bất kỳ** mẻ nào có `ProductId = 6` thì lấy `ProductId`/`PName`/`PCode` của mẻ đó cho cả
phiếu (dòng 282–289).

Cộng dồn qua các mẻ (dòng 292–303, 375–377):

```
StartTime = MIN(item.StartTime)
EndTime   = MAX(item.EndTime)
Date      = EndTime
Gallon    = Σ line.Gallon
Volume    = Σ line.Volume
Weight    = Σ line.Weight        ← xem mục 7.1
TechLog   = item.WeightNote cuối cùng khác rỗng
```

---

## 7. Hai điểm phải quyết định trước khi code — **app đang làm sai**

### 7.1. Số kg trên phiếu hoàn trả là kg của CẢ MẺ

Dòng 343 đặt `line.Weight = item.getWeight()` — kg của **toàn bộ mẻ**. Dòng đáng lẽ ghi đè bằng
phần hoàn trả thì **đang bị comment**:

```java
itemModel.setWeight(itemData.getWeight());   // dòng 343 — kg CẢ MẺ
...
if (model.isReturn) {
    itemModel.setGallon(returnG);            // ✓ phần hoàn trả
    itemModel.setVolume(returnV);            // ✓ phần hoàn trả
    //itemModel.setWeight(returnA);          // ✗ ĐANG BỊ COMMENT
```

Và cột kg **có in ra giấy** — cả dòng chi tiết lẫn dòng Total (dòng 963, 970). Nên phiếu hoàn trả
hiện tại in ra:

| Cột | Giá trị |
|---|---|
| Gallon | phần hoàn trả ✓ |
| Liter | phần hoàn trả ✓ |
| **Kg** | **của cả mẻ ✗** |

Cùng lỗi ở phiếu thường: `line.Weight` không bị trừ đi `returnA`.

**Web phải chọn một:**

- **(A) Tính đúng** — `line.Weight = returnA` cho phiếu hoàn trả, `line.Weight -= returnA` cho
  phiếu thường. Đúng nghiệp vụ, nhưng phiếu web sẽ **khác phiếu app** cho tới khi app được vá.
- **(B) Sao chép y hệt app** — giữ nguyên cái sai để hai bên khớp nhau, rồi vá cả hai cùng lúc.

Khuyến nghị: chọn **(A)** và vá app trong cùng đợt phát hành. Nếu chọn (A) mà không vá app, sẽ có
hai phiếu cùng nghiệp vụ mang số kg khác nhau tuỳ nơi tạo — đúng loại lỗi vừa mất nhiều công truy
tìm ở chuyến CV5781.

`ReturnAmount` ở phần đầu phiếu thì **đúng** (dòng 365, cộng `returnA`), chỉ cột kg từng dòng sai.

### 7.2. Mẻ không có hoàn trả vẫn vào phiếu hoàn trả

Vòng lặp dòng 292 gọi `addItem` cho **mọi** mẻ được chọn. Mẻ nào `ReturnAmount = 0` thì nhánh
dòng 346 không chạy, nên dòng đó giữ nguyên **lượng nạp đầy đủ** và vẫn được cộng vào tổng phiếu
hoàn trả.

Trên app điều này không lộ ra vì màn hình chặn trước: nút Hoàn trả từ chối nếu không mẻ nào có
`ReturnAmount > 0` ([RefuelPreviewActivity.java:661](../app/src/main/java/com/megatech/fms/RefuelPreviewActivity.java:661)).
Nhưng nếu chọn 3 mẻ mà chỉ 1 mẻ có hoàn trả thì hai mẻ kia vẫn vào phiếu với lượng nạp đầy đủ.

**Đề nghị cho web:** khi dựng phiếu hoàn trả, **lọc bỏ mẻ có `ReturnAmount <= 0`** ngay từ đầu.

---

## 8. Sinh số phiếu

Số phiếu là **8 ký tự base36** mã hoá thời điểm + kho + xe + lần in, cộng hậu tố `HT` nếu hoàn trả
(dòng 40–68).

```
receiptCode = 4 chữ số cấu hình theo xe:  2 số đầu = kho (1..43), 2 số sau = xe (1..99)
lan         = min(10, max(1, item.ReceiptCount + 1))
epoch       = 2026-01-01 00:00 theo giờ địa phương
day         = floor((EndTime − epoch) / 86 400 000)
minute      = giờ(EndTime) × 60 + phút(EndTime)

n = day
n = n × 1440 + minute
n = n × 43   + (kho − 1)
n = n × 99   + (xe  − 1)
n = n × 10   + (lan − 1)

number = base36(n).toUpperCase()  → đệm '0' bên trái cho đủ 8 ký tự
if (IsReturn) number += "HT"
```

Giải mã ngược có sẵn ở `decodeReceiptNumber` (dòng 72) — cắt bỏ `HT` trước rồi lần lượt lấy dư
`% 10`, `% 99`, `% 43`, `% 1440`.

Sau khi sinh, app còn `ensureUniqueLocalNumber` để tránh trùng trong máy. **Trên web việc này phải
là ràng buộc duy nhất ở tầng DB**, vì nhiều người dùng có thể cùng thao tác.

**Ba đường lấy số** (dòng 306–318) — web nên hỗ trợ đủ:

| Trường hợp | Số phiếu |
|---|---|
| Thay thế phiếu cũ (`oldNumber != null`, không tạo mới) | dùng lại `oldNumber`, đặt `Reuse = true` |
| Mẻ đã có `ReceiptNumber` và không yêu cầu tạo mới | giữ nguyên số cũ |
| Còn lại | sinh mới theo công thức trên |

---

## 9. Gửi lên server

**Không có endpoint riêng cho hoàn trả.** Dùng đúng endpoint của phiếu thường:

```
POST {API_BASE_URL}/api/receipts
Content-Type: application/json
Authorization: Bearer <token>
```

Đặt tên trường JSON theo **UpperCamelCase** (app dùng Gson `FieldNamingPolicy.UPPER_CAMEL_CASE`,
ngày giờ định dạng `yyyy-MM-dd'T'HH:mm:ss`).

Khung gói tin:

```json
{
  "UniqueId": "27468eb2-3024-4376-b53b-6de3caab5c02",
  "Number": "2619FMLHT",
  "IsReturn": true,
  "Date": "2026-08-25T12:11:22",
  "StartTime": "2026-08-25T11:53:00",
  "EndTime": "2026-08-25T12:11:22",
  "CustomerId": 84,
  "CustomerName": "…",
  "CustomerCode": "…",
  "TaxCode": "…",
  "CustomerType": 1,
  "FlightType": 1,
  "Gallon": 333,
  "Volume": 1261,
  "Weight": 1000,
  "ReturnAmount": 1000,
  "SignType": 1,
  "Items": [
    {
      "RefuelItemId": "1b8e2e2f-7505-44e6-97e1-20789c5a670e",
      "RefuelId": 2119773,
      "TruckNo": "HAN3-20-7006",
      "Gallon": 333,
      "Volume": 1261,
      "Weight": 1000,
      "StartNumber": 78512510,
      "EndNumber": 78512843,
      "Density": 0.7930,
      "Temperature": 28.0,
      "QualityNo": "707",
      "DriverId": 701,
      "OperatorId": 2237
    }
  ]
}
```

Response trả về phiếu đã lưu kèm `Id` do server sinh.

---

## 10. Bộ kiểm thử đối chiếu

Chạy cùng một mẻ trên app và trên web, hai bên phải ra **giống hệt**:

| # | Ca | Phải khớp |
|---|---|---|
| 1 | Nhập hoàn trả theo **KG** | `Gallon`, `Volume`, `ReturnAmount` sau chuẩn hoá hai bước |
| 2 | Nhập hoàn trả theo **GALLON** | như trên, nhưng bỏ bước quy đổi đầu |
| 3 | Nhập kg lẻ (ví dụ 1.001 kg) | con số cuối cùng sau làm tròn qua gallon |
| 4 | Một chuyến **nhiều mẻ**, chỉ một mẻ có hoàn trả | xem mục 7.2 — thống nhất cách lọc |
| 5 | Cùng mẻ, cùng `EndTime`, cùng xe | **số phiếu sinh ra phải trùng nhau** |
| 6 | Phiếu thường + phiếu HT của cùng mẻ | `Gallon` hai phiếu cộng lại = lượng đồng hồ đã chạy |
| 7 | `Density` biên (0.72 và 0.86) | không bị chặn nhầm |
| 8 | Hoàn trả > lượng nạp | cả hai bên cùng cảnh báo |

Ca **5** và **6** là hai ca quan trọng nhất: ca 5 chứng minh thuật toán sinh số khớp, ca 6 chứng
minh không thất thoát số lượng.

---

## Phụ lục — vị trí mã nguồn

| Việc | Vị trí |
|---|---|
| Toàn bộ hàm dựng phiếu | `ReceiptModel.createReceipt()` dòng 123 |
| Kiểm tra tiên quyết | dòng 131–222 |
| Chuẩn hoá lượng hoàn trả | `ReceiptModel.addItem()` dòng 346–357 |
| Ghi đè dòng hoàn trả / dòng thường | dòng 360–371 |
| Sinh số phiếu | `genReceiptNumber()` dòng 40 |
| Giải mã số phiếu | `decodeReceiptNumber()` dòng 72 |
| Nhập lượng hoàn trả trên app | `RefuelDetailConfirmActivity.calculateReturnAmount()` dòng 337 |
| Nút Hoàn trả | `RefuelPreviewActivity.openReceipt(true)` dòng 660 |
| Gửi lên server | `ReceiptAPI.post()` — `POST /api/receipts` |
