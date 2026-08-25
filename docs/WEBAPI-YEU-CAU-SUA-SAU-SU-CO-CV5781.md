# Yêu cầu sửa Web API — sau sự cố hóa đơn chuyến CV 5781 (24-08-2026)

Tài liệu cho nhóm Web API (ASP.NET Web API, .NET Framework 4.8, EF6, SQL Server).
Phần app Android đã vá và phát hành ở bản **11.7 (versionCode 113)**; tài liệu này chỉ liệt kê
phần **còn lại phải làm ở server**.

---

## 1. Chuyện đã xảy ra

Chuyến **CV 5781** (HAN-ANC, B747, LX-JCV) ngày 24-08-2026, 6 xe tra nạp:

| Xe | Bắt đầu | Kết thúc | Gallon |
|---|---|---|---|
| HAN3-20-7005 | 15:28 | 15:44 | 6.718 |
| HAN3-20-7009 | 15:45 | 15:55 | 4.311 |
| HAN3-20-7012 | 15:50 | 16:03 | 7.003 |
| HAN3-20-7010 | 16:01 | 16:01 | 5.026 |
| HAN3-20-7004 | 16:06 | 16:23 | 8.520 |
| HAN3-20-7002 | 17:10 | 17:26 | 7.071 |

Phiếu **2619EY0** in ra:

```
Start Time : 06:34 24/08/2026     ← SAI, sớm hơn mẻ đầu tiên 8 giờ 54 phút
End Time   : 17:26 24/08/2026     ← đúng
```

**Số lượng và số công-tơ đúng tuyệt đối** (delta công-tơ khớp từng đơn vị với USG in trên phiếu).
Chỉ giờ bắt đầu sai.

### Nguyên nhân (đã xác định, không phải giả thuyết)

Gói tin `SyncDiagnosticLog` id **1133226** lúc 17:26:09 — POST từ xe chốt HAN3-20-7002. Mảng
`Others[]` chứa bản sao mẻ của xe 7005 trên máy đó:

```json
"RealAmount":6718, "StartNumber":32175897, "EndNumber":32182615,
"Status":3, "ReceiptNumber":"2619EY0", "ServerRevision":2,
"StartTime":"2026-08-24T06:34:16", "EndTime":"2026-08-24T06:34:16"
```

Trong khi server đã có giá trị đúng `15:28:26 / 15:44:50` từ **15:45:12** (log 1131388 → 1131389).

Máy chốt tính `MIN(StartTime)` trên tập mẻ local → ra `06:34:16` → in lên phiếu.

Đối chứng: gói tin id **1132259** lúc 16:23:49 từ xe 7004 cho thấy **cùng bản ghi đó với giờ
đúng**. Hai tablet, cùng `ServerRevision = 2`, một máy đúng một máy sai.

Lý do phía app: `RefuelSyncGuard.mergeByOwnership` loại `StartTime`/`EndTime` khỏi **mọi** lượt
trộn. Bản vá 11.7 mở đường nhận hai cột này cho **mẻ của xe khác đã chốt**, và kéo lại danh sách
mẻ khi mở màn hình xuất hóa đơn.

---

## 2. Vì sao vẫn phải sửa server

Bản vá app chữa đúng nguyên nhân, nhưng:

- Không kiểm soát được tablet nào đã lên 11.7. **Một máy còn bản cũ là hóa đơn vẫn sai.**
- App chỉ biết những mẻ đã có sẵn trong Room. Mẻ chưa từng đồng bộ về máy thì **mất hẳn khỏi
  hóa đơn**, không cảnh báo gì.
- Còn khe hở: mở màn hình lúc T, xe khác chốt lúc T+5', bấm xuất lúc T+10'.

Server là nơi duy nhất luôn nắm đủ dữ liệu và miễn nhiễm với phiên bản app.

---

## 3. Việc phải làm — theo thứ tự ưu tiên

### Ưu tiên 1 — Ghi nhận và trả về dữ liệu mới nhất

#### 1.1. `GET /api/refuels?uniqueId=` phải trả đúng giờ đã lưu

**Đây là gốc của toàn bộ sự việc.** App hiện phải loại `StartTime`/`EndTime` khỏi mọi lượt trộn
vì đo trên xe thật ngày 17-08 lúc 23:13 cho thấy **server trả về `StartTime`/`EndTime` = đúng
thời điểm sinh phản hồi** (`"2026-08-17T23:13:42.5072233"`), giống hệt nhau cho hàng chục phiếu
chưa hề tra nạp.

Nhận vào là ghi đè toàn bộ danh sách ở mỗi lượt pull, nên app buộc phải chặn. Chính cái chặn đó
sinh ra `06:34`.

**Yêu cầu:** endpoint trả đúng giá trị đang lưu trong `RefuelItems`. Chưa tra nạp thì trả `null`,
**không** trả `DateTime.Now`. Kiểm tra cả `BuildViewModel` và các projection dùng chung.

Sửa xong việc này, app có thể bỏ hẳn ngoại lệ `DEVICE_MEASURED_TIME_KEYS` — hết một lớp phức tạp.

#### 1.2. `ClientSeq` bằng nhau → coi là gói gửi lại, không áp dụng

Hiện mọi gói `WouldBeStale = 1` vẫn `DataApplied = 1`. Hệ quả đo được **trong đúng một chuyến**:

| Xe | `ClientSeq` đứng yên | Khoảng | `ServerRevision` bị đẩy |
|---|---|---|---|
| HAN3-20-7012 | 466 | 16:07 → 21:22 | 8 → 21 |
| HAN3-20-7010 | 27 | 16:02 → 22:19 | 7 → 21 |

Hơn 40 lượt ghi vô nghĩa cho một chuyến kết thúc lúc 17:26. Mỗi lượt tăng `ServerRevision` làm
bản sao trên các tablet khác trông "cũ" đi.

```csharp
if (refuel.ClientSeq.HasValue && model.ClientSeq.HasValue)
{
    if (refuel.ClientSeq.Value <  model.ClientSeq.Value)
    {
        // Gói CŨ thật sự: từ chối, trả bản hiện tại.
    }
    else if (refuel.ClientSeq.Value == model.ClientSeq.Value)
    {
        // Gửi lại đúng phiên bản đang có: KHÔNG ghi, KHÔNG tăng ServerRevision,
        // trả 200 kèm bản hiện tại để client biết đã nhận và dừng vòng lặp.
        return Ok(BuildViewModel(db, model.Id));
    }
}
```

Không dùng cờ bật/tắt toàn cục cho việc này — `==` là ca an toàn tuyệt đối, không có rủi ro khóa
chết thiết bị.

#### 1.3. Bổ sung endpoint lấy toàn bộ mẻ của một chuyến

App hiện dựng danh sách "mẻ của xe khác" bằng cách đọc Room local. Mẻ chưa từng đồng bộ về thì
không tồn tại trên màn hình xuất hóa đơn — và **không có cảnh báo nào**.

```
GET /api/refuels/by-flight?flightUniqueId={guid}
```

Trả về đầy đủ các mẻ thuộc chuyến, kèm `StartTime`, `EndTime`, `Status`, `RealAmount`,
`StartNumber`, `EndNumber`, `ServerRevision`, `ClientSeq`, `TruckNo`. App 11.8 sẽ dùng endpoint
này thay cho việc ghép từ Room.

#### 1.4. Lưu danh tính thiết bị

Header `Tablet-Id` hiện được đọc rồi bỏ (chỉ ghi file log). Cột `RefuelItems.LastUpdateDevice` và
`RefuelItem_Logs.UpdateDevice` đang là cột chết.

**Yêu cầu:** ghi cả hai mỗi lần áp dụng thay đổi. Chỉ để **quan sát và điều tra** — không dùng
`Tablet-Id` làm điều kiện nới lỏng guard nào, vì nó là header không xác thực và không ổn định
(cài lại app, factory reset).

Không có cột này thì lần điều tra sau lại phải suy luận từ `RawJson` như lần này.

---

#### 1.5. Endpoint `/api/bm2508/multipart` đang hỏng ngay cả khi `Id` hợp lệ

Nhật ký xe **HAN3-20-7005** ngày 25-08-2026 (5,7 MB, 52.000 dòng) — riêng tag `BM2508-1` chiếm
**22.748 dòng**, gần một nửa file:

| Loại lỗi | Số dòng |
|---|---|
| `Unable to resolve host` (mất mạng, app vẫn thử lại) | 18.638 |
| `HTTP 400 Missing or invalid BM2508 id` | 3.912 |
| **`HTTP 500 Unable to cast object of type 'System.Int32' to type 'System.String'`** | **35** |
| **`HTTP 500 The ROLLBACK TRANSACTION request has no corresponding BEGIN TRANSACTION`** | **3** |

Phần 400 do app gửi khi phiếu chưa có `Id` — **đã vá ở bản 11.7**. Nhưng hai lỗi 500 là **lỗi
phía server**, và chúng chỉ xuất hiện khi `Id` HỢP LỆ. Nghĩa là sửa xong phía app thì các gói tin
hợp lệ sẽ đâm vào đúng hai lỗi này.

- **Lỗi ép kiểu:** endpoint đọc `Id` như `String` trong khi app gửi số JSON. Cần đọc đúng kiểu,
  hoặc chấp nhận cả hai dạng.
- **Lỗi transaction:** `ROLLBACK` không có `BEGIN` tương ứng — nhánh xử lý lỗi gọi rollback trên
  transaction chưa mở hoặc đã đóng. Thường là `try/catch` bọc ngoài `using` của transaction.

Ngoài ra: cả hai lỗi trả về **stack trace HTML đầy đủ** ra client. Cần tắt `customErrors` trên
môi trường chạy thật — vừa lộ cấu trúc nội bộ, vừa làm phình nhật ký trên xe.

#### 1.6. *(Bối cảnh — app đã vá, server không phải làm gì)* Thế kẹt `endTime` giữa hai lớp bảo vệ

Cùng nhật ký trên: **90 dòng `CONFLICT:PAYLOAD_MISMATCH`, tất cả đều đúng một cột `endTime`**,
trên 5 phiếu:

| Id | Giờ trên máy | Giờ trên server | Lệch |
|---|---|---|---|
| 2118887 | 24/08 06:40:12 | 24/08 17:11:57 | 10h32 |
| 2119363 | 24/08 18:43:46 | 24/08 23:24:03 | 4h40 |
| 2119753 | 25/08 03:49:12 | 25/08 08:32:26 | 4h43 |
| 2119160 | 24/08 15:52:18 | 24/08 16:01:18 | 9 phút |
| 2118895 | 24/08 16:05:13 | 24/08 15:58:13 | 7 phút |

Vòng lặp: app đẩy giờ local → **server `timeLocked` từ chối** vì mẻ đã `DONE` → server trả về giá
trị của nó → app từ chối nhận → đánh dấu conflict, giữ local → đẩy lại. Không bên nào nhường.

Riêng phiếu 2118887 đẩy `ServerRevision` từ **17 lên 45 trong 8 giờ**.

Phía app đã vá ở 11.7: khi khác biệt duy nhất còn lại là mốc giờ và mẻ đã chốt ở cả hai phía, app
**nhận giá trị server** rồi rời hàng đợi. Đó cũng là đường duy nhất để sửa giờ trên web về được
tới xe.

Phía server không cần sửa gì cho riêng việc này — nhưng nó là **bằng chứng đo được** cho phần
`timeLocked` ở mục 4: quy tắc đó đang chặn đúng luồng sửa lại giờ, và mỗi lần chặn lại sinh một
vòng lặp đẩy–từ chối tiêu tốn `ServerRevision`.

#### 1.7. Xác nhận ghi chéo thiết bị

Cũng trong nhật ký này: máy **HAN3-20-7005** `BACKGROUND_SYNC` đẩy mẻ **2119160** — mẻ của xe
**HAN3-20-7010** (`uid=5e83d6f4-…`, `localId=1625`). Đây chính là vòng lặp `ClientSeq = 27` chạy
tới 22:19 mà `SyncDiagnosticLog` ghi nhận hôm 24-08.

**Nhật ký server ghi `TruckCode = HAN3-20-7010`** vì nó lấy theo bản ghi, nên phía server tưởng
xe 7010 gửi. Thực tế là máy 7005.

Đây là lý do cụ thể và đo được cho mục **1.4**: không có `LastUpdateDevice` thì mọi kết luận về
"máy nào ghi" đều là suy đoán sai. App đã chặn đường đẩy này ở 11.7, nhưng server vẫn nên ghi
danh tính thiết bị để đối chiếu.

---

### Ưu tiên 2 — Server tự tính giờ trên chứng từ

#### 2.1. Bỏ qua giờ client gửi khi tạo phiếu / hóa đơn

Đây là lưới an toàn cho mọi phiên bản app đang lưu hành.

```csharp
var items = db.RefuelItems
              .Where(x => itemIds.Contains(x.Id) && x.Gallon > 0)
              .ToList();

receipt.StartTime = items.Min(x => x.StartTime);   // KHÔNG lấy giá trị client gửi
receipt.EndTime   = items.Max(x => x.EndTime);
```

Ba chi tiết bắt buộc:

- **`x.Gallon > 0`** — loại bản ghi phân xe rỗng. Bản ghi phân công buổi sáng mang
  `StartTime` = đúng giờ tạo dòng; không lọc thì `MIN` lại vớ phải nó.
- **Chỉ tính trên các mẻ thuộc phiếu**, không mở rộng ra cả chuyến.
- **Ghi lại giá trị client gửi lên** (mục 2.3) trước khi bỏ qua.

#### 2.2. Chặn chốt khi tập mẻ chưa đủ hoặc có mẻ hỏng

Từ chối tạo phiếu, nêu đích danh xe, khi:

- Có xe được phân cho chuyến mà chưa có mẻ `Status = 3`
- Có mẻ `Gallon > 0` mà thiếu `StartTime` hoặc `EndTime`
- Có mẻ `EndTime <= StartTime`
- Có mẻ tốc độ ngoài dải 100–800 gallon/phút

Mẻ của xe HAN3-20-7010 trong chuyến này — `Bắt đầu = Kết thúc = 16:01:18`, 5.026 gallon trong
**0 phút**, `Rời đi 15:56:49` **trước** `Bắt đầu` — lẽ ra phải bị chặn ở đây.

Thông báo lỗi phải nêu tên xe: *"Xe HAN3-20-7010 có giờ tra nạp không hợp lệ"*, không phải
*"Dữ liệu không hợp lệ"*.

#### 2.3. Bảng `ReceiptMismatch`

Khi giờ client gửi lệch quá 2 phút so với giờ server tính, ghi một dòng:

```sql
CREATE TABLE ReceiptMismatch (
    Id            int IDENTITY PRIMARY KEY,
    ReceiptId     int          NOT NULL,
    ReceiptNumber nvarchar(50) NULL,
    FieldName     nvarchar(50) NOT NULL,
    ClientValue   datetime     NULL,   -- giá trị đã IN RA GIẤY
    ServerValue   datetime     NULL,   -- giá trị server tính, đã lưu vào DB
    DeviceId      nvarchar(100) NULL,
    UserId        int          NULL,
    LoggedAt      datetime     NOT NULL DEFAULT GETDATE()
);
```

Bảng này cho vận hành **danh sách phiếu đã in sai trong khi DB đúng**, để in lại trước khi giao
khách. Nó biến một sự cố kế toán thành một thao tác in lại — và là mitigation duy nhất cho khoảng
trống trước khi toàn bộ tablet lên 11.7.

---

### Ưu tiên 3 — Quan sát và đối soát

#### 3.1. Từ chối phải ghi vào bảng, không phải file log

Hiện `PostRefuel` từ chối gì thì ghi `Logger.AppendLog("POST", "TIME LOCKED ...")` vào file text.
Không truy vấn được, không ai trên hiện trường thấy.

Bằng chứng vì sao việc này quan trọng — xe HAN3-20-7010 trong chính chuyến này:

| Giờ server | `ClientSeq` | Giờ gửi lên | `Status` trong DB | Kết quả |
|---|---|---|---|---|
| 16:01:38 | 26 | `16:01:18` / `16:01:18` | 0 | ghi được |
| 16:02:13 | 27 | **`15:35:18` / `15:52:18`** | **3** | **bị `timeLocked` chặn** |
| 16:03 → 22:19 | 27 (~20 lượt) | `15:35:18` / `15:52:18` | 3 | chặn hết |

Người vận hành **đã bấm sửa lại giờ**. Bản vá nuốt sửa đổi đó, im lặng, và đóng băng một giá trị
bất khả thi. Không có bảng ghi từ chối thì không ai biết chuyện này đã xảy ra.

Đề nghị bảng `RefuelItemRejectedChange(RefuelItemId, FieldName, DbValue, IncomingValue, Reason,
DeviceId, UserId, LoggedAt)`.

#### 3.2. Job đối soát định kỳ

Chạy hằng đêm, cảnh báo khi lệch:

```sql
SELECT  rc.Id, rc.ReceiptNumber,
        SoMe          = COUNT(*),
        SoXe          = COUNT(DISTINCT r.TruckId),
        Phieu_Start   = MIN(rc.StartTime),
        Me_MinStart   = MIN(r.StartTime),
        LechStartPhut = DATEDIFF(minute, MIN(r.StartTime), MIN(rc.StartTime))
FROM    Receipts     rc
JOIN    ReceiptItems ri ON ri.ReceiptId = rc.Id
JOIN    RefuelItems  r  ON r.Id = ri.RefuelItemId
WHERE   r.Gallon > 0
GROUP BY rc.Id, rc.ReceiptNumber
HAVING  ABS(DATEDIFF(minute, MIN(r.StartTime), MIN(rc.StartTime))) > 2
     OR ABS(DATEDIFF(minute, MAX(r.EndTime),   MAX(rc.EndTime)))   > 2;
```

Đây là thứ lẽ ra đã phát hiện sự cố này **trong ngày**, thay vì để phát hiện qua tờ giấy in.

Chạy một lần trên toàn bộ lịch sử trước, phân tổ theo tháng, để biết phạm vi thiệt hại đã tích
lũy — con số cần có khi làm việc với kế toán về phần hóa đơn đã phát hành.

#### 3.3. Tách `CreatedAt` khỏi `StartTime`

Khi tạo bản ghi lúc phân xe, hệ thống gán `StartTime = giờ hiện tại`. Xác nhận trên bản ghi
2118866: lúc `06:20:57` dòng được tạo và `StartTime` = `06:20:57.190`.

Bản ghi nào không có mẻ bơm thật đè lên sẽ giữ nguyên giờ phân công vĩnh viễn — đó là nguồn của
mọi giá trị `06:xx` đã gặp (`06:12:09`, `06:20:57`, `06:21:09`, `06:34:16`).

**Yêu cầu:** `StartTime` để `NULL` cho tới khi có tra nạp thật; giờ tạo dòng lưu vào cột
`CreatedAt` riêng. Đây là sửa gốc — không làm thì lỗi còn tái sinh ở chỗ khác.

---

## 4. Đang hoãn — `timeLocked`

Phần khóa `StartTime`/`EndTime` khi `Status == DONE || Printed || ReceiptId != null` **giữ nguyên,
chưa sửa trong đợt này** theo quyết định của chủ dự án: ưu tiên trước mắt là ghi nhận dữ liệu mới
nhất.

Ghi lại để không quên khi quay lại:

- Nó **không liên quan** tới sự cố CV5781 — dữ liệu server đúng suốt từ 15:45.
- Nó **đang chặn luồng sửa lại giờ** (bằng chứng ở mục 3.1), nên kịch bản "nhập nhầm rồi nhập
  lại" hiện không hoạt động sau khi mẻ vào `DONE`.
- Hướng sửa khi quay lại: tách ba mức thay vì một cờ —
  `documentIssued` (`ReceiptId`/`InvoiceNumber` khác null) → **khóa cứng**;
  `settled` (`DONE` hoặc `Printed`, chưa có chứng từ) → **cho sửa, ghi kiểm toán**;
  còn lại → tự do.
- Trước khi nới, bật mục 3.1 để đo xem hiện đang chặn nhầm bao nhiêu ca thật.

---

## 5. Tóm tắt

| # | Việc | Mức | Chặn được gì |
|---|---|---|---|
| 1.1 | `GET refuels` trả đúng giờ đã lưu, không trả `Now` | **Cao** | Gốc của việc app phải loại 2 cột thời gian |
| 1.2 | `ClientSeq ==` → no-op, không tăng revision | **Cao** | 40+ lượt ghi rác mỗi chuyến |
| 1.3 | Endpoint lấy toàn bộ mẻ theo chuyến | Cao | Mẻ thiếu hẳn khỏi hóa đơn |
| 1.4 | Ghi `LastUpdateDevice` / `UpdateDevice` | **Cao** | Nhật ký server đang ghi sai máy gửi (mục 1.7) |
| 1.5 | Sửa lỗi ép kiểu `Int32`→`String` và lỗi transaction ở `/api/bm2508/multipart`; tắt `customErrors` | **Cao** | Endpoint hỏng ngay cả khi `Id` hợp lệ |
| 1.6 | *(không phải việc của server — đã vá ở app 11.7)* | — | Thế kẹt `endTime`, 90 conflict/ngày |
| 2.1 | Server tự tính `MIN`/`MAX` header | **Cao** | Mọi tablet chưa lên 11.7 |
| 2.2 | Chặn chốt khi tập mẻ chưa đủ / có mẻ hỏng | Cao | Ca xe 7010 |
| 2.3 | Bảng `ReceiptMismatch` | Cao | Biết phiếu nào cần in lại |
| 3.1 | Ghi từ chối vào bảng | Trung bình | Từ chối âm thầm = mất dữ liệu |
| 3.2 | Job đối soát hằng đêm | Trung bình | Phát hiện trong ngày |
| 3.3 | Tách `CreatedAt` khỏi `StartTime` | Trung bình | Sửa gốc, hết giá trị `06:xx` |
| 4 | `timeLocked` | **Hoãn** | — |

---

## Phụ lục — tra cứu bằng chứng

| Log id | Giờ | Nội dung |
|---|---|---|
| 1131388 / 1131389 | 15:45:12 | Xe 7005 chốt mẻ 2118888, server nhận `StartTime = 15:28:26`, `ServerRevision → 2` |
| 1132259 | 16:23:49 | Xe 7004 gửi lên, `Others[]` chứa 2118888 với giờ **đúng** `15:28:26` |
| 1133226 | 17:26:09 | Xe 7002 (xe chốt) gửi lên, `Others[]` chứa 2118888 với giờ **sai** `06:34:16` |
| 1131756 / 1131760 | 16:01:38 / 16:02:13 | Xe 7010: ghi được `16:01:18`, rồi sửa lại `15:35:18` bị `timeLocked` chặn |
| 1131874 … 1136785 | 16:07 → 22:19 | Vòng lặp gửi lại của 7012 (`seq 466`) và 7010 (`seq 27`) |

Nhật ký thiết bị `HAN3-20-7005.fms.log` (24-08 22:20 → 25-08 09:20):

| Dòng | Nội dung |
|---|---|
| 836, 6333, … | 90 lượt `CONFLICT:PAYLOAD_MISMATCH endTime(...)` trên 5 phiếu |
| 37668 | `Click: refuel_preview_starttime` lúc 06:52:32 — sau đó bấm làm mới 4 lần trong 90 giây |
| 37735 | `HTTP 500 Unable to cast object of type 'System.Int32' to type 'System.String'` |
| 50866 | `Click: refuel_preview_endtime` lúc 09:00:02 |
| 50821 | Máy 7005 `BACKGROUND_SYNC` mẻ 2119160 của xe 7010 |

Bản ghi liên quan của sự cố trước: `RefuelItem` **2118866** (24-08-2026) — tablet của xe cũ ghi
đè `StartTime`/`EndTime` lên mẻ đã bàn giao sang xe khác. Đó là **lỗi khác**, thuộc phạm vi
`timeLocked` ở mục 4.
