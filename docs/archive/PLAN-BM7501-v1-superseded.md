# PHƯƠNG ÁN: BM 75.01/NLHK — Phiếu yêu cầu hút nhiên liệu từ tàu bay

> Tài liệu này để **Codex review trước khi code**. Chưa có dòng code nào được thay đổi.
> Nguồn: `2_BM 75.01.NLHK.doc` (Ban hành/sửa đổi 01/03, SKYPEC), 3 trang, mục A/B/C.
> Backup đã tạo: `/Users/ngoc/Documents/DevNgoc/_backup/App-So-Hoa-20260810-164130.tar.gz`
> (kèm `HEAD-*.txt`, `status-*.txt`, `worktree-*.patch` cùng timestamp).

---

## 1. Hiện trạng app (nghiệp vụ Hút — `REFUEL_ITEM_TYPE.EXTRACT`)

| Bước | Nơi xử lý | Dữ liệu đang thu thập |
|---|---|---|
| Danh sách hút | `ExtractActivity` | quyền `CREATE_EXTRACT` |
| Tạo chuyến hút | `NewRefuelActivity` (`EXTRACT=true`) | hãng bay, số hiệu chuyến bay (bắt buộc), charter, bãi đỗ, đường bay, loại/số hiệu tàu bay, giờ đến–đi, lượng dự kiến |
| Thực hiện hút | `RefuelDetailActivity` + `edit_refuel_dialog` | chỉ số đồng hồ cuối, lượng thực, nhiệt độ; giờ bắt đầu/kết thúc lấy từ LCR |
| Xem lại | `RefuelPreviewActivity` + `preview_extract` | kg, USG, đồng hồ đầu/cuối, nhiệt độ, tỷ trọng, số phiếu hóa nghiệm, số phiếu hút |
| In | `ReceiptModel.createReturnText()` (ESC/P) và `createReturnThermalText()` (ZPL) | "PHIẾU HOÀN TRẢ NHIÊN LIỆU" |

**Kết luận:** app mới chỉ có phần **đo đếm** (mục C, bảng số liệu). Toàn bộ **mục A** (khách hàng khai
báo) và **mục B** (SKYPEC kiểm tra chất lượng) của BM 75.01 chưa tồn tại trong app.

---

## 2. Đối chiếu BM 75.01 ↔ app (gap analysis)

### Mục A — Khách hàng điền

| # | Trường trong biểu mẫu | Kiểu | App hiện có |
|---|---|---|---|
| A1 | Số phiếu (No.) | text | ~ (`defuelingNo` ở phiếu hoàn trả, khác ngữ cảnh) |
| A2 | Ngày / Giờ | date + time | ✅ (`startTime`) |
| A3 | Hãng hàng không | chọn từ `AirlineModel` | ✅ |
| A4 | Đại diện hãng: **họ tên** + **chữ ký** | text + signature | ❌ |
| A5 | Chức danh / Title | text | ❌ |
| A6 | Điện thoại, Fax | text | ❌ |
| A7 | Loại tàu bay, Số hiệu tàu bay | text | ✅ |
| A8 | Sân bay / Airport | chọn từ `AirportsModel` | ~ (suy ra từ `routeName`) |
| A9 | **Lý do hút**: Điều chỉnh tải trọng / Bảo dưỡng sửa chữa / Khác (ghi rõ) | radio + text | ❌ |
| A10 | Đã xả tất cả thùng lấy mẫu KTCL trước khi hút: Có / Không | radio | ❌ |
| A11 | Kiểm tra vi sinh (nếu có): **thiết bị** Hy-lite / Microb monitor2 / Fuelstat / Khác(text) | radio + text | ❌ |
| A12 | Kết quả vi sinh: Normal / Warning / Action level | radio | ❌ |
| A13 | Phụ gia hiện diện: FSII / Biocide / Aquarius WMA / Không sử dụng / Không xác định | multi-select | ❌ |
| A14 | 2 sân bay nạp trước đó: Location 1 + Grade, Location 2 + Grade (cho phép "Không xác định được") | 4 text | ❌ |

### Mục B — SKYPEC điền

| # | Trường | Kiểu | App hiện có |
|---|---|---|---|
| B1 | Kiểm tra ngoại quan (VAC): Đạt / Không đạt | radio | ❌ |
| B2 | Viên thử nước (CWD): Đạt / Không đạt | radio | ❌ |
| B3 | Kiểm tra KLR — kết quả (kg/m³) | number | ~ (`density` kg/l — **khác đơn vị**) |
| B4 | Độ dẫn điện (nếu yêu cầu) — kết quả (pS/m) | number, optional | ❌ |
| B5 | Vi sinh: thiết bị Hy-lite / Microb monitor2 / Fuelstat | radio | ❌ |
| B6 | Vi sinh: kết quả Normal / Warning / Action | radio | ❌ |

### Mục C — Các bên xác nhận

| # | Trường | Kiểu | App hiện có |
|---|---|---|---|
| C1 | Phương tiện hút / Defueller | chọn từ `TruckModel` | ✅ (`truckNo`) |
| C2 | Giờ bắt đầu / kết thúc | time | ✅ |
| C3 | **Phương thức hút**: bơm tàu bay / bơm xe tra nạp / cả hai | radio | ❌ |
| C4 | **Phương thức tín hiệu**: ngón cái phải / chéo hai tay / khác (ghi rõ) | multi + text | ❌ |
| C5 | Lượng hút ra (kg): **Dự kiến** và **Thực tế** | 2 number | ~ (chỉ có thực tế `weight`; dự kiến `estimateAmount` đang là USG) |
| C6 | Nhiệt độ thực tế (°C) | number | ✅ |
| C7 | KLR thực tế (kg/m³) | number | ~ (đang lưu kg/l) |
| C8 | Quy đổi: Gal / Litter | number | ✅ |
| C9 | Nhiên liệu hút ra có thể nạp lại cho tàu bay cùng hãng không cần kiểm tra bổ sung: Đồng ý / Không | radio | ❌ |
| C10 | Nếu không nạp lại ngay — chọn 1 trong 5: lưu trữ (Từ/Đến) · nạp lại chính tàu bay · nạp cho tàu bay khác cùng hãng · không nạp lại & ủy quyền SKYPEC · vẫn nạp lại dù có vấn đề (ghi rõ) | radio + 2 date + text | ❌ |
| C11 | Chữ ký đại diện SKYPEC + đại diện khách hàng (ký, ghi rõ họ tên) | 2 signature + 2 text | ~ (có cơ chế `ReceiptSignActivity`, chưa gắn vào BM 75.01) |

**Tổng: 26 nhóm trường mới cần bổ sung.**

---

## 3. Phương án kỹ thuật đề xuất

### 3.1 Nguyên tắc
- Đi theo đúng khuôn mẫu các biểu mẫu đã có trong app (BM 25.05 / 25.08): `entity` + `dao` +
  `model` + `Activity/Fragment` + layout + sync qua `DataRepository`.
- **Không** nhét thêm 26 cột vào `RefuelItem` — bảng này đã rất rộng và nằm trên đường
  đồng bộ nóng. Tách thành bảng riêng `BM7501`, liên kết bằng `refuelItemUniqueId`.
- Offline-first: lưu local trước, sync sau — giống `RefuelSyncGuard` hiện tại.

### 3.2 Thay đổi dữ liệu

**Mới:**
- `data/entity/BM7501.java` — theo khuôn `BM2508.java` (`localId`, `uniqueId`, `jsonData`,
  `isSynced`, `isLocalModified`, `isDeleted`, `dateCreated`, `dateUpdated`).
- `data/dao/BM7501Dao.java` — theo khuôn `BM2508Dao`.
- `model/BM7501Model.java` — POJO đầy đủ 26 nhóm trường ở mục 2, có enum cho các nhóm radio:
  `DEFUEL_REASON`, `MICROBIAL_KIT`, `MICROBIAL_RESULT`, `ADDITIVE` (bitmask), `QC_RESULT`,
  `DEFUEL_METHOD`, `SIGNAL_METHOD` (bitmask), `RETURN_OPTION`.
- Khóa liên kết: `BM7501Model.refuelItemUniqueId` ↔ `RefuelItemData.flightUniqueId`.

**Sửa:**
- `AppDatabase`: thêm `BM7501.class`, version `12 → 13`, thêm `MIGRATION_12_13`
  (`CREATE TABLE IF NOT EXISTS BM7501 (...)`) và đăng ký trong `addMigrations(...)`.
  Lưu ý: `fallbackToDestructiveMigration()` đang bật — vẫn phải viết migration thật để
  không mất dữ liệu offline của máy đang chạy.
- `DataRepository`: thêm `getBM7501/saveBM7501/syncBM7501` theo khuôn BM 25.08.
- `helpers/DataHelper` + API class mới `BM7501API` (theo khuôn `RefuelAPI`).

### 3.3 Thay đổi UI

- `B7501Activity` + `B7501FormFragment` (form dài → `NestedScrollView`, chia 3 card **A / B / C**
  đúng thứ tự biểu mẫu, có tiêu đề song ngữ Việt–Anh như bản giấy).
- Điểm vào: nút **"Phiếu yêu cầu hút"** trên `RefuelDetailActivity` (khi
  `refuelItemType == EXTRACT`) và trên `preview_extract`.
- Chữ ký: dùng lại `ReceiptSignActivity` cho 2 chữ ký (khách hàng / SKYPEC), lưu base64
  giống `Receipt`.
- Prefill từ mẻ hút: hãng bay, loại/số hiệu tàu bay, sân bay, phương tiện hút, giờ bắt đầu/kết thúc,
  nhiệt độ, KLR, USG/Litter/kg — người dùng chỉ nhập phần còn thiếu.

### 3.4 Quy tắc validate đề xuất

Bắt buộc trước khi in/ký:
1. Lý do hút (A9) — nếu chọn "Khác" thì text mô tả không rỗng.
2. A10 (đã xả mẫu) phải chọn Có/Không.
3. A14 — cả 4 ô; cho phép nhập "Không xác định được" đúng như ghi chú biểu mẫu.
4. B1, B2 phải chọn; B3 (KLR kg/m³) > 0.
5. B4 chỉ bắt buộc khi tick "có yêu cầu".
6. Nếu A10 = Không **hoặc** B1/B2 = Không đạt → **bắt buộc** điền mục B5–B6 (vi sinh),
   đúng ghi chú "chỉ thực hiện nếu VAC/CWD không đạt hoặc nghi ngờ".
7. C3 phải chọn; C5 dự kiến và thực tế > 0; C9 phải chọn.
8. Nếu C9 = Không → **bắt buộc** chọn 1 phương án ở C10; nếu chọn "lưu trữ" thì Từ/Đến bắt buộc
   và `Đến >= Từ`.
9. Hai chữ ký C11 bắt buộc trước khi đánh dấu phiếu hoàn tất.
10. Giữ nguyên các ràng buộc sẵn có của mẻ hút (nhiệt độ > 0, tỷ trọng > 0, số phiếu hóa nghiệm
    không rỗng, thời gian mẻ 3–180 phút).

**Cảnh báo (không chặn):** `|KLR mục B3 − density mẻ hút × 1000| > 5 kg/m³` → hỏi lại người dùng.

### 3.5 Bản in nhiệt

Thêm 2 hàm vào `BM7501Model` (song song với `ReceiptModel`):
- `createThermalText()` — ESC/P khổ 66 ký tự cho `PrintWorker` (giống `createReturnText()`).
- `createZplText()` — ZPL cho `ZebraWorker`, tôn trọng
  `THERMAL_PRINTER_TYPE.ZQ520` → `^LH130,0`, ngược lại `^LH000,0`, font `OPENSANS-RE.TTF`, `^CI28`.

Bố cục bản in (khổ 80mm, thứ tự bám biểu mẫu):

```
        CÔNG TY TNHH MTV NLHK VIỆT NAM (SKYPEC)
        CHI NHÁNH <chi nhánh>
     JET FUEL DEFUEL REQUEST FORM
     PHIẾU YÊU CẦU HÚT NHIÊN LIỆU TỪ TÀU BAY
     No.: <số phiếu>            <dd/MM/yyyy HH:mm>
--------------------------------------------------
A. KHÁCH HÀNG / CUSTOMER
Airline        : ...        A/C Type : ...
A/C Reg        : ...        Airport  : ...
Rep. / Title   : ...        Tel/Fax  : ...
Reason         : <Load adj. | Maintenance | Other: ...>
Tank drain     : Yes / No
Microbial      : <kit> - <result>
Additives      : FSII / Biocide / Aquarius WMA / None / Undetermined
Prev. loc. 1   : <sân bay> - <grade>
Prev. loc. 2   : <sân bay> - <grade>
--------------------------------------------------
B. SKYPEC
VAC            : Satisfy / Not satisfy
CWD            : Satisfy / Not satisfy
Density        : <...> kg/m3
Conductivity   : <...> pS/m
Microbial      : <kit> - <result>
--------------------------------------------------
C. XÁC NHẬN / CONFIRMATION
Defueller      : <số xe>
Start / End    : HH:mm - HH:mm
Method         : Aircraft pump / Refueller pump / Both
Signals        : ...
        Expected        Actual
Kg      <...>           <...>
Temp.(°C)               <...>
Density(kg/m3)          <...>
USG                     <...>
Liter                   <...>
--------------------------------------------------
Re-fuellable without extra test : Yes / No
Handling : <1 trong 5 phương án; kèm Từ/Đến hoặc ghi chú>
--------------------------------------------------
   ĐẠI DIỆN KHÁCH HÀNG        ĐẠI DIỆN SKYPEC
    (Ký, ghi rõ họ tên)      (Ký, ghi rõ họ tên)


   <tên khách hàng>            <tên nhân viên>
--------------------------------------------------
                                      BM 75.01/NLHK
```

Ghi chú in:
- Ô nào không nhập → in `.....` chứ không in `null` (lỗi này đang tồn tại ở
  `createReturnText()` với `pCode`).
- Chữ ký ảnh: máy ESC/P không in ảnh → chỉ chừa khoảng trắng ký tay; Zebra in ảnh nếu có
  (`^GFA`) — **cần chốt** (xem mục 5).

### 3.6 Việc dọn dẹp kèm theo (nhỏ, cùng phạm vi)
- `NewRefuelActivity.save()` gọi `refuelData.getAirlineModel()` khi chưa chọn hãng → NPE bị nuốt
  trong `AsyncTask`, form không lưu và không báo lỗi. Bổ sung validate hãng bay.
- Thống nhất đơn vị KLR: app lưu kg/l, biểu mẫu yêu cầu kg/m³ → quy đổi ở tầng hiển thị/in,
  **không** đổi đơn vị lưu trữ (tránh vỡ dữ liệu cũ và phiếu hoàn trả).

---

## 4. Thứ tự thực hiện (đề xuất 5 commit)

| # | Nội dung | File chính |
|---|---|---|
| 1 | Model + enum + unit test quy đổi/validate | `model/BM7501Model.java` |
| 2 | Entity + DAO + migration 12→13 + repository | `data/entity/BM7501.java`, `data/dao/BM7501Dao.java`, `AppDatabase.java`, `DataRepository.java` |
| 3 | UI form A/B/C + prefill + validate | `B7501Activity`, `B7501FormFragment`, `res/layout/b7501_form.xml`, `res/values/strings.xml` |
| 4 | Bản in nhiệt ESC/P + ZPL | `BM7501Model.createThermalText/createZplText`, `PrintWorker`, `ZebraWorker` |
| 5 | Sync API + điểm vào từ `RefuelDetailActivity` / `preview_extract` | `helpers/BM7501API.java`, `DataHelper` |

Mỗi commit build được và không phá luồng hút hiện tại.

---

## 5. Câu hỏi — trạng thái chốt

| # | Câu hỏi | Trả lời |
|---|---|---|
| 1 | Backend đã có endpoint BM 75.01 chưa? | **Đã có backend nhưng chỉ dừng ở nghiệp vụ cũ** → chưa có endpoint cho BM 75.01. Xem §7.2 |
| 2 | Số phiếu do server cấp hay app sinh? | Suy ra từ (1): **app sinh offline**. Xem §7.2.3 |
| 3 | In nhiệt hay in kim A4? | **Chỉ dùng trên phiếu in nhiệt.** Xem §7.3 |
| 4 | Chữ ký ký trên máy hay ký giấy? | Suy ra từ (3): ký trên máy + in ảnh chữ ký (Zebra). Xem §7.3.4 |
| 5 | Mục A: nhập hộ hay chụp ảnh? | **Nhân viên nhập hộ lên máy.** Xem §7.4 |
| 6 | 1 phiếu ↔ 1 mẻ hút hay nhiều mẻ? | **Mỗi phiếu là 1 mẻ hút (1–1).** Xem §11.1 |
| 7 | Quyền riêng hay dùng `CREATE_EXTRACT`? | **Dùng lại `CREATE_EXTRACT`.** Xem §11.2 |
| 8 | In 1 liên hay 2 liên? | **In 1 liên.** Xem §11.3 |

---

## 6. Rủi ro (bản đầu — xem thêm §9)

| Rủi ro | Giảm thiểu |
|---|---|
| Migration Room 12→13 trên máy hiện trường | Viết migration thật; test nâng cấp từ APK bản cũ, không dựa vào `fallbackToDestructiveMigration` |
| Form dài → nhân viên bỏ trống | Prefill tối đa từ mẻ hút; validate theo nhóm, hiện lỗi tại đúng ô |
| Bản in nhiệt tràn giấy | Tính chiều cao ZPL theo số dòng thực tế; test in thật trên ZQ520 |
| Lệch đơn vị KLR (kg/l ↔ kg/m³) | Quy đổi ở tầng hiển thị, có unit test |
| Phá vỡ luồng hút đang chạy | Không sửa `RefuelItem`; BM 75.01 là bảng độc lập, điểm vào tách riêng |

---

# PHẦN BỔ SUNG — sau khi chốt câu 1, 3, 5

## 7. Hệ quả thiết kế của 3 quyết định

### 7.1 Tóm tắt tác động

| Quyết định | Tác động lớn nhất |
|---|---|
| Backend chưa có endpoint | Toàn bộ dữ liệu BM 75.01 **chỉ tồn tại trên máy tablet** cho tới khi backend làm xong → phải có outbox bền bỉ + hợp đồng API viết sẵn để bàn giao |
| Chỉ in nhiệt | **Bản in nhiệt trở thành bản gốc của biểu mẫu**, không phải bản tóm tắt → phải in đủ 100% nội dung mục A/B/C, không được lược bỏ |
| Nhân viên nhập hộ mục A | Form dài ~40 ô do 1 người nhập giữa sân đỗ → cần chia theo mốc thời gian thực tế, lưu nháp liên tục, và chữ ký khách hàng xác nhận lời khai |

### 7.2 Backend chưa sẵn sàng — thiết kế "local-first, sync sau"

**7.2.1 Nguyên tắc:** app phải chạy đủ nghiệp vụ và in được phiếu **mà không cần server**.
Việc backend bổ sung endpoint sau này **không được** yêu cầu sửa lại dữ liệu đã lưu.

**7.2.2 Cơ chế lưu trữ**
- Bảng `BM7501` giữ nguyên thiết kế §3.2 với `jsonData` là **nguồn sự thật duy nhất**
  (serialize toàn bộ `BM7501Model`). Các cột phẳng chỉ để truy vấn/hiển thị danh sách.
  Backend đổi tên trường sau này chỉ cần sửa lớp map, không phải migrate DB.
- Cờ `isSynced = false` cho mọi bản ghi ở giai đoạn này. **Không** xóa bản ghi chưa sync
  trong bất kỳ tác vụ dọn dẹp nào.
- `syncVersion = 1` ghi kèm mỗi bản ghi, để khi backend chạy còn biết bản ghi sinh ở phiên bản
  hợp đồng nào.

**7.2.3 Số phiếu sinh offline**
- Format đề xuất: `75-<mã chi nhánh>-<số xe>-<yyMMdd>-<seq 3 chữ số>`, ví dụ `75-NBA-51F123-260810-001`.
- `seq` đếm theo ngày + theo xe, lấy từ `BM7501Dao.countByDate(truckId, date)`.
- Không đụng vào `ReceiptModel.createNumber()`; đây là dãy số độc lập.
- Rủi ro trùng khi backend cấp số riêng về sau → giữ 2 trường: `localNumber` (app sinh, đã in ra giấy,
  **bất biến**) và `serverNumber` (điền khi sync thành công). Bản in dùng `localNumber`.

**7.2.4 Outbox**
- Dùng lại đúng khuôn `RefuelSyncGuard`: hàng đợi bền bỉ, retry có backoff, không chặn UI.
- Khi chưa có endpoint: outbox **vẫn ghi**, tác vụ sync chỉ no-op (feature flag
  `BuildConfig.BM7501_SYNC_ENABLED = false`). Bật cờ là chạy, không phải sửa logic.
- Bổ sung màn hình đếm số phiếu chưa sync (hoặc badge ở `ExtractActivity`) để nhân viên biết
  dữ liệu còn nằm trên máy — quan trọng vì đây là dữ liệu pháp lý duy nhất.
- Xuất dự phòng: nút "Xuất JSON phiếu 75.01" ghi ra thư mục `getExternalFilesDir()` để cứu dữ liệu
  nếu tablet phải cài lại trước khi backend xong.

**7.2.5 Hợp đồng API đề xuất — bàn giao cho backend**

Mô phỏng đúng `api/bm2508` đang có (`HttpClient.postBM2508Post2` / `getBM2508List2`):

```
POST   api/bm7501/post2        multipart: json + customerSignature + skypecSignature
GET    api/bm7501/get2/{truckId}
GET    api/bm7501/{id}/signature/customer
GET    api/bm7501/{id}/signature/skypec
```

Body JSON (rút gọn, tên trường theo bản Anh trên biểu mẫu):

```jsonc
{
  "localNumber": "75-NBA-51F123-260810-001",
  "uniqueId": "<uuid>",
  "refuelItemUniqueId": "<uuid của mẻ hút>",
  "truckId": 12, "airlineId": 3, "airportId": 1,
  "date": "2026-08-10T09:30:00+07:00",
  "customer": {
    "repName": "...", "title": "...", "tel": "...", "fax": "...",
    "aircraftType": "A321", "aircraftReg": "VN-A123",
    "reason": "LOAD_ADJUSTMENT|MAINTENANCE|OTHER", "reasonOther": "...",
    "tankDrainSampled": true,
    "microbialKit": "HY_LITE|MICROB_MONITOR2|FUELSTAT|OTHER|NONE",
    "microbialKitOther": "...",
    "microbialResult": "NORMAL|WARNING|ACTION|NONE",
    "additives": ["FSII","BIOCIDE","AQUARIUS_WMA"],   // hoặc [] + additiveNote
    "additiveNote": "NONE|UNDETERMINED|null",
    "prevLocation1": "...", "prevGrade1": "...",
    "prevLocation2": "...", "prevGrade2": "..."
  },
  "skypec": {
    "vac": "SATISFY|NOT_SATISFY",
    "cwd": "SATISFY|NOT_SATISFY",
    "densityKgM3": 795.2,
    "conductivityRequired": false, "conductivityPsM": null,
    "microbialKit": "...", "microbialResult": "..."
  },
  "confirmation": {
    "defuellerTruckNo": "51F-123.45",
    "startTime": "...", "endTime": "...",
    "method": "AIRCRAFT_PUMP|REFUELLER_PUMP|BOTH",
    "signals": ["THUMB_UP","CROSSED_ARMS"], "signalOther": "...",
    "expectedKg": 3000, "actualKg": 2980,
    "actualTempC": 28.5, "actualDensityKgM3": 795.2,
    "gallon": 990, "liter": 3748,
    "refuellableWithoutTest": true,
    "handling": "STORAGE|SAME_AIRCRAFT|OTHER_AIRCRAFT_SAME_AIRLINE|AUTHORIZE_SKYPEC|REFUEL_DESPITE_ISSUE|null",
    "storageFrom": null, "storageTo": null, "handlingNote": null,
    "customerRepName": "...", "skypecRepName": "..."
  },
  "printedAt": "...", "syncVersion": 1
}
```

Yêu cầu với backend: **idempotent theo `uniqueId`** (gửi lại không tạo bản ghi trùng),
và chấp nhận `localNumber` do app sinh.

### 7.3 Chỉ in nhiệt — bản in là bản gốc

**7.3.1 Hệ quả bắt buộc:** bố cục ở §3.5 là bản rút gọn, **không dùng được nữa**.
Bản in phải chứa đủ mọi mục A/B/C, kể cả các dòng ghi chú pháp lý của biểu mẫu:
- Ghi chú mục A4 về "Không xác định được" khi không rõ loại nhiên liệu 2 sân bay trước.
- Ghi chú "Ưu tiên sử dụng bơm của tàu bay / Priority to use the pump of aircraft".
- Ghi chú cuối phiếu về chuyển phiếu cho cán bộ đội tra nạp và việc tra nạp lại vượt số lượng hút.
- Mã hiệu `BM 75.01/NLHK` và `Ban hành/sửa đổi: 01/03` ở chân phiếu.

Giữ song ngữ Việt–Anh cho **nhãn mục và các dòng cam kết**; nhãn trường phụ có thể chỉ tiếng Anh
để tiết kiệm giấy (bản in nhiệt 80mm, `^FB600` ≈ 3 inch vùng in).

**7.3.2 Ước tính chiều dài giấy** (203 dpi, cỡ chữ 30 dots/dòng):

| Khối | Dòng | Dots |
|---|---|---|
| Đầu phiếu (công ty, tên form 2 thứ tiếng, số phiếu, ngày) | 8 | ~330 |
| Mục A (14 nhóm, một số 2 dòng) | ~20 | ~600 |
| Mục B (6 nhóm) | 7 | ~210 |
| Mục C phần thông tin + bảng dự kiến/thực tế | ~16 | ~480 |
| Cam kết C9/C10 (câu dài, `^FB` nhiều dòng) | ~10 | ~300 |
| 2 chữ ký (ảnh 300×200 + tên) | — | ~500 |
| Ghi chú chân phiếu + mã biểu mẫu | 6 | ~200 |
| **Tổng** | | **≈ 2 620 dots ≈ 33 cm** |

→ **Một bản ≈ 33 cm giấy; in 2 liên (khách + SKYPEC) ≈ 66 cm.** Cần xác nhận với nghiệp vụ là
chấp nhận được, và kiểm tra cuộn giấy ZQ511/ZQ520 đủ dài. Nếu không chấp nhận: phương án B là
in 1 liên + gửi bản mềm, nhưng bản mềm lại phụ thuộc backend chưa có.

**7.3.3 Kỹ thuật ZPL**
- `^LL` phải tính động theo `height` cuối cùng — bám đúng cách `createReturnThermalText()`
  đang làm: `builder.insert(3, "^LL" + (height + 200))`.
- Giữ `LEFT_INDENT` theo `THERMAL_PRINTER_TYPE`: `ZQ520` → `^LH130,0`, `ZQ511` → `^LH000,0`
  (mặc định hiện tại của `TruckModel` là **ZQ511**).
- Font `^CWZ,E:OPENSANS-RE.TTF` + `^CI28` để có dấu tiếng Việt — bắt buộc, vì phiếu này nhiều
  tiếng Việt hơn phiếu hoàn trả.
- Câu dài dùng `^FB600,n,0,L,0`; **phải** cộng `height` đúng số dòng thực tế, nếu không sẽ đè chữ.

**7.3.4 Chữ ký**
- `ZebraWorker` đã có sẵn cơ chế `storeImage("E:BUYER.GRF", ...)` / `E:SELLER.GRF` rồi in bằng
  `^XGE:BUYER.GRF,1,1`. Dùng lại nguyên: `BUYER.GRF` ← chữ ký khách hàng,
  `SELLER.GRF` ← chữ ký SKYPEC. Thêm hàm `print7501(BM7501Model)` song song `print2503(...)`.
- **`PrintWorker` (ESC/P qua TCP) không in được ảnh.** Với máy loại này: in đủ nội dung chữ,
  phần chữ ký chừa 4 dòng trắng có tên in sẵn để ký tay. Ghi rõ hạn chế này trong hướng dẫn sử dụng.
- Bản in lại phải đóng dấu `BẢN SAO / COPY` ở đầu phiếu và tăng `reprintCount`, để bản gốc
  không bị nhân bản không kiểm soát (biểu mẫu này là chứng từ giao nhận).

**7.3.5 Liên kết với phiếu hoàn trả đang có**
`createReturnThermalText()` hiện in sẵn dòng cam kết *"…không đạt theo quy định tại số phiếu hút số:"*
nhưng **đang bỏ trống số phiếu**. Sau khi có BM 75.01, `BM7501Model.localNumber` phải chảy vào
`ReceiptModel.defuelingNo` và in vào đúng chỗ trống đó — đây là mảnh ghép còn thiếu của luồng hiện tại,
làm luôn trong phạm vi này.

### 7.4 Nhân viên nhập hộ mục A — thiết kế nhập liệu

**7.4.1 Chia form theo mốc thời gian thực tế**, không phải theo mục A/B/C của tờ giấy:

| Bước | Khi nào | Nội dung | Trạng thái |
|---|---|---|---|
| 1 | Trước khi hút, cạnh đại diện hãng | Mục A (A2–A14) | `DRAFT` → `A_DONE` |
| 2 | Sau khi lấy mẫu, có kết quả KTCL | Mục B (B1–B6) | `A_DONE` → `B_DONE` |
| 3 | Sau khi hút xong | Mục C (C1–C10), phần lớn prefill từ mẻ hút | `B_DONE` → `C_DONE` |
| 4 | Hai bên ký | 2 chữ ký + họ tên | `C_DONE` → `SIGNED` |
| 5 | In | Bản gốc | `SIGNED` → `PRINTED` |
| 6 | Có mạng | Đẩy outbox | `PRINTED` → `SYNCED` |

Sau `SIGNED` thì khóa sửa; muốn sửa phải tạo phiếu mới (giống cách app đang xử lý phiếu đã in).

**7.4.2 Chống mất dữ liệu khi nhập giữa sân đỗ**
- Autosave xuống Room sau mỗi ô (debounce ~500 ms), không chờ nút Lưu.
- Khôi phục nháp khi mở lại `RefuelDetailActivity` của cùng mẻ hút.
- Không dùng `AlertDialog` từng ô như `showEditDialog` hiện tại cho form 40 ô — quá chậm.
  Dùng form cuộn nhập trực tiếp, bàn phím số cho ô số.

**7.4.3 Mục A là lời khai của khách hàng, không phải của SKYPEC**
- Trên màn hình mục A hiện băng cảnh báo: *"Thông tin do đại diện hãng cung cấp; nhân viên nhập hộ"*.
- Chữ ký đại diện hãng ở bước 4 chính là xác nhận cho toàn bộ lời khai mục A → **không cho ký
  khi mục A còn ô bắt buộc trống**.
- Lưu kèm `enteredByUserId` (nhân viên nhập) tách khỏi `customerRepName` (người khai) để truy vết.

**7.4.4 Giảm gõ**
- `Hãng hàng không`, `Loại/Số hiệu tàu bay`, `Sân bay`, `Phương tiện hút`, giờ bắt đầu/kết thúc,
  nhiệt độ, KLR, USG/Lít/Kg: prefill từ mẻ hút, cho sửa.
- `Đại diện hãng / Chức danh / Tel / Fax`: gợi ý từ lần nhập gần nhất **của cùng hãng** (đọc từ
  `BM7501Dao.getLastByAirline(airlineId)`).
- `Sân bay thứ 1 / thứ 2` + grade: có nút nhanh "Không xác định được / Undetermined".
- Mặc định `Phương thức hút = Bơm của tàu bay` (biểu mẫu ghi rõ ưu tiên).

## 8. Kế hoạch thực hiện (thay thế §4)

| # | Commit | Nội dung | Điều kiện xong |
|---|---|---|---|
| 1 | `feat(bm7501): model + enums` | `BM7501Model`, 8 enum, quy đổi kg/l ↔ kg/m³, sinh số phiếu offline | Unit test quy đổi + sinh số + validate §3.4 |
| 2 | `feat(bm7501): storage` | Entity, DAO, `AppDatabase` 12→13 + `MIGRATION_12_13`, `DataRepository` | Test nâng cấp DB từ bản 12 có dữ liệu |
| 3 | `feat(bm7501): form nhập 3 bước` | `B7501Activity` + 3 fragment, autosave, prefill, máy trạng thái | Nhập tay đủ 1 phiếu trên máy thật |
| 4 | `feat(bm7501): chữ ký + máy trạng thái khóa sửa` | Dùng lại `ReceiptSignActivity`, 2 chữ ký, khóa sau `SIGNED` | Ký được, không sửa được sau ký |
| 5 | `feat(bm7501): bản in nhiệt` | `createThermalText()` ESC/P + `createZplText()` ZPL, `ZebraWorker.print7501`, `PrintWorker.print7501`, `BẢN SAO` khi in lại | **In thử trên ZQ511 thật**, đo chiều dài giấy |
| 6 | `feat(bm7501): outbox + cờ sync` | Outbox theo khuôn `RefuelSyncGuard`, `BM7501_SYNC_ENABLED=false`, badge chưa sync, xuất JSON dự phòng | Tắt mạng vẫn chạy đủ; bật cờ không lỗi biên dịch |
| 7 | `feat(extract): số phiếu hút vào phiếu hoàn trả` | `BM7501.localNumber` → `ReceiptModel.defuelingNo`, in vào chỗ trống §7.3.5 | So bản in trước/sau |
| 8 | `fix(new-refuel): validate hãng bay` | Sửa NPE bị nuốt ở `NewRefuelActivity.save()` | Bấm Lưu khi chưa chọn hãng → báo lỗi rõ |

Commit 1–5 là đường tới hạn (in được phiếu). 6–8 làm sau, không chặn.

## 9. Rủi ro bổ sung

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Dữ liệu pháp lý chỉ nằm trên tablet** cho tới khi backend xong | Cao | Outbox bền bỉ + badge cảnh báo + nút xuất JSON; **không** xóa bản ghi chưa sync khi dọn dữ liệu cũ |
| Backend đặt tên trường khác lúc làm thật | Trung bình | `jsonData` là nguồn sự thật + lớp map riêng + `syncVersion` |
| Phiếu 33 cm/liên, 66 cm/2 liên — tốn giấy, dễ hết cuộn giữa chừng | Trung bình | Đo thật ở commit 5; nếu quá dài, rút nhãn tiếng Anh trước, **không** cắt nội dung pháp lý |
| ESC/P không in được chữ ký ảnh | Trung bình | Chừa chỗ ký tay + ghi rõ trong hướng dẫn; ưu tiên Zebra cho nghiệp vụ này |
| Form 40 ô nhập giữa sân đỗ, nắng/gắt/vội | Cao | Chia 3 bước theo mốc thời gian, autosave từng ô, prefill tối đa |
| Số phiếu offline trùng khi backend cấp số | Thấp | Tách `localNumber` (đã in, bất biến) và `serverNumber` |
| Sai đơn vị KLR khi in (kg/l lưu, kg/m³ in) | Trung bình | Quy đổi tập trung 1 hàm + unit test; không đổi đơn vị lưu trữ |

## 10. Câu hỏi còn treo (không chặn commit 1–5)

1. Sau khi backend xong, phiếu đã `PRINTED` nhưng chưa sync có được backend chấp nhận
   `localNumber` không, hay phải cấp lại số? *Đề xuất: chấp nhận, vì số đã in ra giấy có chữ ký.*
2. In 1 liên (§11.3) — **liên giấy duy nhất giao cho ai?** Xem §11.3.2, cần nghiệp vụ chốt.

Các câu 1/2/3 trước đây (1–1, `CREATE_EXTRACT`, 1 liên) đã chốt — xem §11.


---

# PHẦN BỔ SUNG 2 — sau khi chốt 1–1, quyền, 1 liên

## 11. Ba quyết định mới và tác động

### 11.1 Mỗi phiếu là 1 mẻ hút (1–1)

**Đơn giản hóa được:**
- `BM7501.refuelItemUniqueId` là **UNIQUE**, không cần bảng con:
  `@Index(value = {"refuelItemUniqueId"}, unique = true)` trên entity **và**
  `CREATE UNIQUE INDEX index_BM7501_refuelItemUniqueId ON BM7501(refuelItemUniqueId)` trong
  `MIGRATION_12_13` — Room chỉ tạo index khi migration viết đúng, nếu quên sẽ lệch schema và
  crash lúc validate.
- Bảng số liệu mục C (§7.3.2) chỉ có **1 dòng**, không lặp `items` như `createReturnThermalText()`
  đang làm → bản in ngắn hơn ước tính cũ, xem §11.3.1.
- Không cần màn hình danh sách mẻ để gộp; phiếu mở trực tiếp từ mẻ hút đang xem.

**Ràng buộc phải thêm:**
- Mở form 75.01 lần 2 trên cùng mẻ → **mở lại phiếu cũ**, không tạo mới. Nếu phiếu đã `SIGNED`
  thì mở ở chế độ chỉ đọc + nút "In lại (BẢN SAO)".
- Chèn phiếu phải `INSERT OR ABORT` rồi bắt lỗi unique, **không** dùng `REPLACE` — `REPLACE`
  sẽ âm thầm xóa phiếu đã ký khi có đua luồng giữa autosave và sync.
- `BM7501Dao.getByRefuelItem(String refuelItemUniqueId)` trả 1 bản ghi, là API chính của DAO.
- Mẻ hút bị xóa/hủy: **không** cascade xóa phiếu đã `SIGNED`/`PRINTED` (chứng từ đã ký),
  chỉ đánh dấu `orphan = true` để nhân viên xử lý.

**Hệ quả với §7.2.5 (hợp đồng API):** `refuelItemUniqueId` là khóa nghiệp vụ, backend nên
đặt unique constraint tương ứng; idempotency vẫn theo `uniqueId` của phiếu.

### 11.2 Dùng lại quyền `CREATE_EXTRACT`

- **Không** thêm giá trị mới vào `UserInfo.USER_PERMISSION` → không đụng bitmask đang dùng,
  không cần backend cấp quyền mới. Đây là lý do chính khiến lựa chọn này ít rủi ro nhất.
- Kiểm tra quyền đúng một chỗ, y hệt `ExtractActivity:51`:
  `(currentUser.getPermission() & UserInfo.USER_PERMISSION.CREATE_EXTRACT.getValue()) > 0`
- Áp dụng cho: nút mở form 75.01, nút ký, nút in, và cả nút "In lại (BẢN SAO)".
- Không có quyền → **ẩn nút** (giống cách `btnNewExtract` đang làm), không hiện rồi báo lỗi.
- Ghi nhận: người ký ở mục C là đại diện SKYPEC — app lấy `currentUser` đang đăng nhập, nên
  quyền `CREATE_EXTRACT` cũng chính là điều kiện được đứng tên trên phiếu. Nếu sau này nghiệp vụ
  muốn tách "người hút" và "người ký duyệt" thì mới cần quyền riêng; ghi lại đây làm mốc.

### 11.3 In 1 liên

**11.3.1 Ước tính lại chiều dài giấy** (thay bảng ở §7.3.2, đã trừ phần lặp items nhờ 1–1):

| Khối | Dots |
|---|---|
| Đầu phiếu (công ty, tên form song ngữ, số phiếu, ngày) | ~330 |
| Mục A (14 nhóm) | ~600 |
| Mục B (6 nhóm) | ~210 |
| Mục C thông tin + bảng số liệu **1 dòng** | ~390 |
| Cam kết C9/C10 (câu dài, `^FB` nhiều dòng) | ~300 |
| 2 chữ ký (ảnh 300×200 + họ tên) | ~500 |
| Ghi chú chân phiếu + `BM 75.01/NLHK` | ~200 |
| **Tổng** | **≈ 2 530 dots ≈ 31–32 cm** |

→ **1 liên ≈ 32 cm giấy 80mm.** `^PQ1` (đang là mặc định trong `createReturnThermalText()`) là
đúng — **không** đổi thành `^PQ2`.

**11.3.2 Câu hỏi nghiệp vụ còn lại:** ghi chú cuối biểu mẫu nói *"sau khi hút nhiên liệu, nhân viên
giao lại phiếu này cho cán bộ đội tra nạp"*, nhưng mục C lại có chữ ký của **cả hai bên**. Với 1 liên
duy nhất thì khách hàng không giữ bản nào.
- *Đề xuất mặc định:* liên giấy giao **cán bộ đội tra nạp** (đúng ghi chú biểu mẫu); khách hàng
  nhận bản mềm khi backend xong (§7.2.5). Trong lúc chờ backend, thêm nút **"Chụp màn hình phiếu"**
  lưu ảnh vào `getExternalFilesDir()` để gửi khách khi cần — dùng lại `helpers/ScreenshotAPI`.
- Cần bạn hoặc nghiệp vụ xác nhận điểm này trước commit 5.

**11.3.3 Bản sao**
- In lần 2 trở đi: header đóng `BẢN SAO / COPY` + `Lần in: n`, tăng `reprintCount`, ghi
  `LogEntryModel.LOG_TYPE.APP_LOG`. Bản gốc chỉ in một lần.
- Vì chỉ có 1 liên, việc in lại sẽ xảy ra thường xuyên hơn (giấy kẹt, mờ) → luồng in lại phải
  chạy được cả khi mất mạng, lấy dữ liệu từ Room chứ không gọi server.

## 12. Cập nhật kế hoạch commit

Không thay đổi số commit ở §8, chỉ siết thêm điều kiện xong:

| Commit | Bổ sung do 3 quyết định mới |
|---|---|
| 2 (storage) | Unique index `refuelItemUniqueId` ở **cả** entity và `MIGRATION_12_13`; `INSERT OR ABORT`; test tạo phiếu trùng mẻ → mở lại phiếu cũ |
| 3 (form) | Ẩn nút theo `CREATE_EXTRACT`; mở form lần 2 → phiếu cũ; phiếu `SIGNED` → chỉ đọc |
| 5 (in) | `^PQ1`, bảng số liệu 1 dòng; đo giấy thật, kỳ vọng ~32 cm; `BẢN SAO` + `reprintCount`; in lại chạy offline |

## 13. Điểm cần Codex soi kỹ

1. **§11.1 — unique index và `INSERT OR ABORT`:** đây là chỗ dễ mất chứng từ đã ký nhất nếu
   autosave và sync đua nhau. Cách chống đua đã đủ chưa?
2. **§7.2 — local-first:** dữ liệu pháp lý chỉ nằm trên tablet cho tới khi backend xong. Cơ chế
   outbox + xuất JSON + badge đã đủ an toàn chưa, hay cần thêm backup định kỳ?
3. **§7.2.3 — `localNumber` vs `serverNumber`:** cách tách này có chỗ nào hỏng khi backend
   cấp số riêng không?
4. **§7.3.3 — tính `height` ZPL thủ công** cho các câu dài `^FB600,n`: rủi ro đè chữ trên bản in
   ~32 cm. Có nên viết helper đo dòng thay vì cộng tay như `ReceiptModel` đang làm?
5. **§7.4 — máy trạng thái 6 bước** có quá nặng cho thao tác hiện trường không?
6. **§3.4 — 10 quy tắc validate** có mâu thuẫn hay bỏ sót so với biểu mẫu gốc không?
7. **§7.3.5 — nối `localNumber` vào `defuelingNo`** của phiếu hoàn trả: có phá bản in hiện hành không?
8. **§11.3.2** — mô hình 1 liên có mâu thuẫn với việc mục C cần chữ ký hai bên không?
