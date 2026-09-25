# Báo cáo tổng hợp — kiểm thử luồng tra nạp → xuất hoá đơn

**Ngày:** 2026-09-06 · **Nhánh:** `fix/datahelper-offline-sync` · **Dự án:** `com.megatech.fms`

Báo cáo này gộp kết quả của bảy tác nhân (3 kiểm thử, 2 lên phương án, 2 soi chéo) và
**chuẩn hoá lại cách đánh số phát hiện** (P1 và P2 dùng cùng nhãn cho phát hiện khác nhau).

> **Quy ước quan trọng nhất của tài liệu này.** Mỗi khẳng định được gắn đúng một trong ba
> trạng thái bằng chứng, và ba loại này **không bao giờ được trộn lẫn**:
>
> - 🟢 **ĐÃ ĐO** — có test JVM chạy thật, in ra số hoặc ném ra lỗi thật.
> - 🟡 **ĐỌC MÃ SUY RA** — đã mở tệp, đối chiếu dòng, nhưng chưa dựng được ca chạy.
> - 🔴 **CHƯA KIỂM CHỨNG** — không có thiết bị / không có backend / không có máy in để hỏi.
>
> Một phát hiện 🟡 **không kém giá trị** hơn 🟢, nhưng nó **không được dùng để bác** một
> khẳng định 🟢.

---

## 1. Đã làm gì

### 1.1 Bảy tác nhân

| Tác nhân | Việc | Sản phẩm |
|---|---|---|
| **T1** | CA 1 — chuyến phân công cho **chính xe** đang chạy app | `RefuelFlowOwnTruckTest` (27 test) |
| **T2** | CA 2 — chuyến của **xe khác** + chuyến **chưa phân công** | `RefuelFlowOtherTruckTest` (27 test) + tệp cầu nối trong nguồn test |
| **T3** | CA 3 — chuyến phân công **nhiều xe**, xe chốt gộp mẻ | `RefuelFlowMultiTruckTest` (18 test) |
| **P1** | Phương án sửa F1–F10, xác minh N1–N8 | `plan-P1.md` + `P1ProbeTest` (6 test dò) |
| **P2** | Phương án sửa độc lập với P1 | `plan-P2.md` + `P2ProbeTest` (4 test dò) |
| **X1** | Soi phương án **P2** bằng con mắt P1 | `cross-X1.md` (không thêm test) |
| **X2** | Soi phương án **P1** bằng con mắt P2 | `cross-X2.md` + `X2ProbeTest` (2 test dò) |

**Không một tác nhân nào sửa mã sản phẩm.** Mọi thay đổi trong cây nguồn nằm hoàn toàn
trong `app/src/test/`.

### 1.2 Ba ca kiểm thử — ánh xạ vào dữ liệu thật trên tablet

Tablet **Samsung Galaxy Tab Active5 (SM-X306B)**, Android 15, serial `R52XA0AYEQZ`, đã kết
nối và đã sao bản `fms_debug.db` về (chỉ đọc, `PRAGMA user_version = 14`). Xe đăng nhập:
**TruckId 34 / `DEMO-03` / DeviceType LCR**.

| Ca | Dữ liệu thật tương ứng |
|---|---|
| CA 1 — chuyến của chính xe | `VU 635-01`, `VN 7561` (truck 34, DONE, đã đồng bộ) |
| CA 2 — mẻ của xe khác | localId 3 — mẻ của `DEMO 02`, `remoteReplica = 1` |
| CA 3 — chuyến nhiều xe | `VN 1237-01` (flightId 1294359) có **hai mẻ**: một của `DEMO 02`, một của `DEMO-03` |

Đồng hồ trên tablet **không nối được**, nên luồng dữ liệu đồng hồ được **giả lập ở mức dữ
liệu** (mỗi nhịp = một snapshot `PROCESSING`, chốt mẻ = một snapshot `DONE`). Theo BRIEF,
đây là cách làm đúng chứ không phải giải pháp tạm.

### 1.3 Số test — con số của chính lượt chạy này

Lệnh chạy thật lúc viết báo cáo (2026-09-06):

```
./gradlew testDebugUnitTest
768 tests completed, 6 failed
```

**768 test · 6 fail · 64 lớp test.** Số học khớp hoàn toàn:

```
684 (nền trước đợt)  +  72 (test nghiệp vụ: 27 T1 + 27 T2 + 18 T3)  +  12 (test dò: 6 P1 + 4 P2 + 2 X2)  =  768
```

> **Sửa lại các con số cũ.** `FINDINGS.md` ghi **756** — đó là `684 + 72`, chốt **trước** khi
> P1/P2/X2 thêm tệp dò vào cây nguồn. P1 báo **766** và nói *"lệch 4 test tôi không giải
> thích được"* — chỗ lệch chính là 4 test của `P2ProbeTest` đang chạy song song, cộng thêm 2
> test của `X2ProbeTest` về sau thành 768. **Không còn con số nào không giải thích được.**
> Từ nay dẫn số 768, không dẫn 756 hay 766.

**Sáu test đỏ** (giữ nguyên có chủ ý — mỗi test đỏ là một phát hiện, không nới assert nào):

```
flow.RefuelFlowMultiTruckTest > banDangTraNapRevisionCaoHonVanKhongDuocMoLaiMeDaDone   :407
flow.RefuelFlowMultiTruckTest > meBiMoLaiThiPhieuGopMatDungPhanCuaXeDo                 :439
flow.RefuelFlowOtherTruckTest > ghiMocTiepCanChoChuyenXeKhacPhaiLuuDuoc                :528
flow.RefuelFlowOtherTruckTest > ghiMocTiepCanChoChuyenChuaPhanCongPhaiLuuDuoc          :541
flow.RefuelFlowOtherTruckTest > chuyenChuaPhanCongPhaiHienTrongMotTrongHaiDanhSach     :211
flow.RefuelFlowOwnTruckTest   > soHieuChuyenTrongKhongTaoDuocPhieu                     :598
```

### 1.4 Ba tệp test dò có nên giữ lại không

`P1ProbeTest`, `P2ProbeTest`, `X2ProbeTest` (12 test) **không canh luật nghiệp vụ nào** —
chúng chỉ in ra hành vi thật của Gson / SQLite / model để phương án đứng trên bằng chứng.
Đề nghị: **chuyển hoá ba nhóm sau thành test chính thức** rồi xoá phần còn lại —

- `P2ProbeTest.probe_rowTruckNoNullRoiKhoiCaHaiDanhSach` → `app/src/test/.../data/` (F2)
- `X2ProbeTest.x2_dongPhieuLayGallonTuFieldHayTuGetter` → (F12)
- `P1ProbeTest.n6_payloadServerDangSo` / `X2ProbeTest.x2_status4...` → `.../model/` (F11)

---

## 2. Bảng phát hiện hợp nhất F1–F16

Nhãn dưới đây là **nhãn chuẩn của báo cáo tổng**. Cột cuối ánh xạ về nhãn của P1/P2 để tra
ngược. Số dòng đã được tác nhân tổng hợp **tự mở tệp đối chiếu lại**, không dẫn lại.

| # | Mức | NT | Nội dung | Vị trí `tệp.java:dòng` | Bằng chứng | P1 gọi | P2 gọi |
|---|---|---|---|---|---|---|---|
| **F1** | Nghiêm trọng | **NT2** | Gói "đang tra nạp" treo, server xử lý SAU gói Done nên mang **revision CAO HƠN** ⇒ vế `<=` cho nó lọt, mẻ đã Done bị hạ về PROCESSING với sản lượng cũ; mẻ rụng khỏi phiếu gộp **không cảnh báo** (lượt pull đó *thành công*, `warnIncompleteOthersThen` chỉ nhìn `hasFailure()`) | `data/DataRepository.java:409-412` (vế 4 ở `:412`); `helpers/OthersFreshness.java` `eligibleForDocument` | 🟢 **ĐÃ ĐO** — 2 test đỏ (T3 `:407`, `:439`) | F1 | F1 |
| **F2** | Nghiêm trọng | **NT1** | Chuyến **chưa phân công** (`truckNo` NULL) không hiện ở **danh sách nào**. SQLite: `NULL != 'X'` cho NULL, không cho TRUE ⇒ rơi khỏi cả `= :truckNo` lẫn `!= :truckNo` | `data/dao/RefuelItemDao.java:47, :50, :53` (`getOthers`); đối chiếu `:37, :40, :43` (`getByTruckNo`) | 🟢 **ĐÃ ĐO** — 1 test đỏ (T2 `:211`) + `P2ProbeTest` chèn 3 row rồi gọi DAO thật | F2 | F2 |
| **F3** | Nghiêm trọng | **NT1** | Nút **"Tiếp cận"/"Rời đi" chết** ở màn danh sách với chuyến xe khác và chuyến chưa phân công: nút hiện theo mỗi `approachTime == null` (không xét xe), rồi gọi thẳng đường fail-closed | `res/layout/cardview_refuel_item.xml:242`; `view/RefuelRecyclerViewAdapter.java:335/403/471`; `helpers/DataHelper.java:1146` (`FOREIGN_READ_ONLY`) và `:1148` (`OWNERSHIP_UNKNOWN`) | 🟢 **ĐÃ ĐO** — 2 test đỏ (T2 `:528`, `:541`) | F3 | F3 |
| **F4** | Nghiêm trọng | **NT3** | Khâu tạo phiếu chặn `AircraftCode` (`:174`), `AircraftType` (`:181`), `RouteName` (`:199`), tỉ trọng (`:210`), nhiệt độ (`:165`) — **không có một dòng nào kiểm `FlightCode`**. Cổng duy nhất còn giữ luật là màn tạo chuyến tự tạo | `model/ReceiptModel.java:174-215` (thiếu); `NewRefuelActivity.java:406` (cổng duy nhất) | 🟢 **ĐÃ ĐO** — 1 test đỏ (T1 `:598`); tổng hợp đã `grep` xác nhận `createReceipt` không nhắc `FlightCode` | F4 | F4 |
| **F5** | Cao | mâu thuẫn nội bộ | `realAmount <= 0` chặn kết thúc ⇒ **mẻ 0 lít không chốt được** bằng Dừng khẩn + nhập tay. Trái skill (`< 0`) và trái `RefuelDetailConfirmActivity`. **Hai luật đang được canh bằng hai test mâu thuẫn nhau.** Còn **hai ngõ cụt anh em** ngay cạnh: `startNumber <= 0` | `RefuelDetailActivity.java:2060` và **`:2061`**; đối chiếu `RefuelDetailConfirmActivity.java:518` và **`:520`** | 🟢 **ĐÃ ĐO** hiện trạng (T1, test khoá hiện trạng) · 🟡 hai ngõ cụt anh em: **ĐỌC MÃ** | F5 | F5 |
| **F6** | Trung bình | **NT1×NT3** | `hasRequiredPrintFields` chỉ hỏi `> 0`, không hỏi **khoảng**. Mẻ xe khác tỉ trọng 0.90 không bị loại kèm cảnh báo mà đi thẳng vào chặn cứng. Máy này **không sửa được** mẻ xe khác ⇒ không loại được, không sửa được, không in được | `helpers/OthersFreshness.java:88-93`; `model/ReceiptModel.java:210-214` và `:164-172` | 🟢 **ĐÃ ĐO** — T3 `:537` (test PASS, khoá lại ngõ cụt) | F6 | F6 |
| **F7** | Trung bình | NT3 | Khoảng **nhiệt độ 15–40 °C chỉ tồn tại ở khâu tạo phiếu**. Cả **ba** ô nhập nhiệt độ không chặn, trong khi **tỉ trọng ngay cạnh thì có** ⇒ người dùng chỉ biết sai ở tận cuối luồng | `model/ReceiptModel.java:165`; ô nhập: `RefuelPreviewActivity.java:2867-2871`, `RefuelDetailConfirmActivity.java:297-299`, `RefuelDetailActivity.java:1159` (đối chiếu tỉ trọng `:2852`/`:290`/`:1151`) | 🟡 **ĐỌC MÃ** (T1) | F7 | F7 |
| **F8** | Trung bình | — | `patchAllPrintItems()` chạy tiếp sau lỗi, **không gỡ số phiếu đã ghi**, và hộp lỗi **không nói mẻ nào** ⇒ mẻ chưa đóng dấu bị in lần hai bằng số khác | `RefuelPreviewActivity.java:3035-3054` | 🟢 **ĐÃ ĐO** — T3 `:656` (PASS, ghi nhận rủi ro) | F8 | F8 |
| **F9** | Thấp / cần chốt | NT3 | (a) Chặng bay chỉ kiểm **định dạng**, chưa đối chiếu bảng `Airports` (25 dòng, đã có sẵn) ⇒ `ZZZ-SGN` qua được. (b) **Ngõ cụt ĐANG TỒN TẠI**: regex bắt đúng **3 ký tự** ở vế trái, mà sân bay `DEMO` có **4 ký tự** ⇒ `DEMO-SGN` **hôm nay đã không tạo được phiếu** | `model/ReceiptModel.java:199` — `^[A-Z]{3}-[A-Z0-9]{3,}$` | 🟢 (a) ĐÃ ĐO — T3 `:566` · 🟡 (b) **ĐỌC MÃ**, tổng hợp đã tự đối chiếu regex | F9 | F9 |
| **F10** | Thấp | — | `valid`/`validQC` **gán lại** thay vì `&=`; hiện đúng **chỉ nhờ** `break` cách đó 14 dòng. Ai bỏ `break` để "gom hết lỗi" là chứng từ ra với dữ liệu thiếu | `RefuelPreviewActivity.java:639-643` (gán), `:652` (`break`) | 🟡 **ĐỌC MÃ**, tổng hợp đã tự đối chiếu | F10 | F10 |
| **F11** | Cao (bom hẹn giờ) | — | `PAUSED(2)` và `ERROR(4)` **cùng** `@SerializedName("2")`. Gson lấy hằng số khai sau ⇒ `{"Status":2}` đọc thành **ERROR**; `{"Status":4}` đọc thành **null**. Và `RefuelItem.java:140` **không null-check** ⇒ **NPE thật** trong `fromRefuelItemData`, tức trong **mọi** đường lưu snapshot. Enum bị **nhân bản ở hai tệp** | `model/REFUEL_ITEM_STATUS.java:8` và `:10`; bản nhân bản `data/entity/RefuelItem.java:687-693`; NPE tại `data/entity/RefuelItem.java:140` | 🟢 **ĐÃ ĐO** — `P1ProbeTest` + `X2ProbeTest` (NPE thật, có stack trace) | F11 | F11 |
| **F12** | **Cao** | **NT3** | **`Gallon` trên tờ phiếu trôi khỏi số lít và Kg.** Dòng in dựng bằng `gson.fromJson(itemData.toJson(), …)` ⇒ cột Gallon lấy từ **field** `gallon`; còn lít/Kg lấy từ **getter dẫn xuất** (`realAmount`). **Không có một dòng `itemModel.setGallon(...)` nào.** Tổng phiếu cộng từ số đã trôi | `model/ReceiptModel.java:341` (dựng từ JSON), `:354-355` (`derivedVolume`/`derivedWeight` từ getter), `:381`, `:388`, `:420`; nguồn trôi: `model/RefuelItemData.java:1110` (getter bỏ qua field) vs `:1114` (setter ghi field), `RefuelDetailActivity.java:2474-2475` và `:2562-2563`, `helpers/RefuelFieldPatch.java` `Scope.PREVIEW` | 🟢 **ĐÃ ĐO** — `P2ProbeTest` và `X2ProbeTest` độc lập cùng in ra: `getGallon()=2101` / `getVolume()=7953` nhưng **dòng phiếu gallon = 2000** | F14 (một nửa, **kết luận sai**) | F12 |
| **F13** | Trung bình | — | `updateAllInvoice()` thấy **một** mẻ đã mang đúng số hoá đơn là `return false` cho **cả lượt** ⇒ các mẻ chưa có số **vĩnh viễn không nhận được**, và trả `false` **im lặng**. **Khoá với F8**: F8 để lại một mẻ thiếu số, F13 làm lần thử lại không bao giờ cứu được | `RefuelPreviewActivity.java:3090-3094`; **bằng chứng mạnh**: hàm anh em `updateAllReceipt` có **đúng khối đó đã bị comment lại** ở `:2997-3002` | 🟡 **ĐỌC MÃ** (nằm trong Activity, phụ thuộc `onActivityResult`) | F14 (nửa kia) | F13 |
| **F14** | Trung bình | — | `refreshOthersBeforeDocument()` thoát sớm khi `!isCombinedDocument()`, mà hàm đó = `printItems.size() > 1`. **Vòng lặp kín**: chưa tích nhiều ⇒ không làm mới ⇒ không thấy mẻ xe khác ⇒ vẫn chưa tích nhiều. Hộp "Có mẻ mới của xe khác" **không bao giờ chạy** | `RefuelPreviewActivity.java:316-321`, `:457-463`, hộp thoại `:426-448` | 🟡 **ĐỌC MÃ** (cần chạy Activity thật) | F13 (một nửa) | F14 |
| **F15** | Trung bình | — | Phiếu gốc phân loại `UNKNOWN` (**chính là ca chuyến chưa phân công**) ⇒ **vứt payload Others tươi**, rơi xuống `refreshCachedOthers` vốn **không phát hiện được mẻ mới**. Và lượt bỏ này **không đếm vào `failed`** ⇒ `hasFailure()` = false ⇒ **không cảnh báo** | `helpers/DataHelper.java:860-864`, rơi xuống `:886` | 🟡 **ĐỌC MÃ** (cần endpoint Others giả đầy đủ) | F13 (nửa kia) | (chỉ ghi nhận) |
| **F16** | Trung bình | **NT1** (crash = chặn tuyệt đối) | `item.getTruckNo().equals(...)` — so quyền sở hữu bằng **một phép so chuỗi trần**, không null-check ⇒ NPE khi chạm dòng phiếu chưa phân công. **F2 đang che F16**: sửa F2 xong là F16 nổ ngay | `view/RefuelRecyclerViewAdapter.java:123` (trong nhánh `:122` `status != DONE`) | 🟢 **tiền đề ĐÃ ĐO** (payload thiếu khoá `TruckNo` ⇒ `getTruckNo()` trả null, `P1ProbeTest`+`P2ProbeTest`) · 🟡 **ca crash: ĐỌC MÃ** (cần Robolectric dựng RecyclerView) | F12 | (gộp vào §2) |

### 2.1 Tám vấn đề **chưa ai trong bảy tác nhân sửa được**, do X1/X2 tìm ra khi soi chéo

Đây là phần giá trị nhất của lượt soi chéo — chúng làm **một nửa các phương án ở trên trở
nên không đủ**.

| # | Nội dung | Vị trí | Bằng chứng |
|---|---|---|---|
| **B1** | **F6 không gỡ hết ngõ cụt.** `excludeIneligibleForeignItems` thoát sớm khi `items.size() <= 1`, và khi loại hết thì **trả lại nguyên danh sách**. ⇒ Với chứng từ **chỉ toàn mẻ xe khác** (ca "in hộ" — nghiệp vụ có thật), mẻ sai tỉ trọng **không bao giờ bị loại**. Ngõ cụt sống sót sau **cả hai** phương án | `helpers/OthersFreshness.java:150` và `:164-167` | 🟡 ĐỌC MÃ (X1; tổng hợp đã tự đối chiếu) |
| **B2** | **Màn danh sách có NĂM đường ghi fail-closed, cả P1 lẫn P2 chỉ đếm ba.** Thiếu: nút **"Huỷ tiếp cận"** (`:373` → `postRefuel(item,false)`), `saveAndUpdate` (`:494`), `postData` (`:537`). Chuyển đúng ba đường thì ngõ cụt chỉ **dời sang nút khác** | `view/RefuelRecyclerViewAdapter.java:335/373/471/494/537`; `helpers/RefuelApproachGuard.java:91`; `RefuelPreviewActivity.java:2320` | 🟡 ĐỌC MÃ (X1) |
| **B3** | **Dấu vết ghi cục bộ lên replica sẽ bị lượt pull xoá.** Với mẻ xe khác, **server phải nhận trước rồi mới ghi Room** — `replaceRemoteRefuelSnapshots` ghi đè toàn bộ, chỉ giữ `localId`, `id`, `remoteOthersUidsJson`. Ghi `approachTime` cục bộ lên replica là tạo dấu vết **sẽ bị xoá**. Không phương án nào nhắc | `helpers/DataHelper.java:1007-1012` (ghi thành lời); `data/DataRepository.java:415-455` (thực thi) | 🟡 ĐỌC MÃ (X1) |
| **B4** | **Hai chặn của NT3 không nói được mẻ nào.** Chặng bay (`:199`) và tỉ trọng (`:210`) ném thẳng `new InvalidRefuelTimeException(...)`, **không** qua `buildItemException`. Ở phiếu gộp, người dùng nhận lỗi mà không biết sửa mẻ nào. Sửa gần như không tốn gì | `model/ReceiptModel.java:199`, `:210` | 🟡 ĐỌC MÃ (X1); tổng hợp đã tự đối chiếu |
| **B5** | **F5 còn ngõ cụt thứ ba.** Ngoài `RefuelDetailActivity.java:2060` và `:2061`, còn `RefuelDetailConfirmActivity.java:520` `getStartNumber()<=0 \|\| getEndNumber()<=0` ⇒ mẻ có đồng hồ khởi điểm 0 **vẫn kẹt ở màn Xác nhận** kể cả sau khi sửa hai dòng kia | `RefuelDetailConfirmActivity.java:520` | 🟡 ĐỌC MÃ (X1) |
| **B6** | **Phạm vi nguyên tắc "cảnh báo, đừng chặn" đã được phân định sẵn trong skill.** `fms-refuel-flow` dòng `:28` viết: *"Chỉ chặn ở khâu **xuất phiếu**, không chặn ghi nhận."* ⇒ chặn ở `createReceipt()` **được phép**; chặn ở **ô nhập** nằm trong luồng tra nạp nên nguyên tắc "cảnh báo, đừng chặn" **áp được**. Hệ quả cho F7 ở §5 | `.claude/skills/fms-refuel-flow/SKILL.md:11-17`, `:28` | 🟡 ĐỌC TÀI LIỆU (X1) |
| **B7** | **`setGallon` có lời gọi thứ tư mà cả hai plan đếm thiếu** — `RefuelItemData.java:455` `setGallon(0)`. X1/X2 đã đọc ngữ cảnh `:440-460`: **vô hại** (dòng `:454` đã có `setRealAmount(0)`), nên đề xuất "uỷ quyền cho `setRealAmount`" vẫn đứng. Ghi lại để không phải kiểm lại | `model/RefuelItemData.java:455` | 🟡 ĐỌC MÃ (X2) |
| **B8** | **NPE thứ hai cùng lớp với F11, cách dòng 140 đúng bảy dòng**: `setRefuelItemType(REFUEL_ITEM_TYPE.getValue(data.getRefuelItemType().ordinal()))` cũng **không null-check**, trong khi bị **kẹp giữa hai khối đã null-check đúng cách**. Vá F11 mà bỏ dòng này thì crash chỉ **dịch đi bảy dòng** | `data/entity/RefuelItem.java:147` (giữa `:143-146` và `:148-151`) | 🟡 ĐỌC MÃ (X2); tổng hợp đã tự đối chiếu |

### 2.2 Ba kết luận của tác nhân trước **bị sửa lại** trong báo cáo này

Theo yêu cầu "thấy tác nhân trước kết luận sai thì nói ra và sửa lại, kèm bằng chứng":

**(1) P1 kết luận SAI về F12 (`Gallon`).** P1 viết: *"tổng Gallon và tổng lít trên tờ phiếu
**luôn cùng một nguồn**, không trôi được… Không phải lỗi phiếu, là rủi ro dữ liệu phía
server. Xếp mức THẤP."* Lập luận của P1 dừng ở `getGallon()` và không đi tiếp tới chỗ dòng
phiếu **được dựng bằng Gson từ JSON**, nơi getter không bao giờ được gọi.
🟢 **Bằng chứng bác bỏ:** hai tác nhân độc lập (`P2ProbeTest`, `X2ProbeTest`) cùng chạy và
cùng in ra — mẻ `{"RealAmount":2101,"Gallon":2000,"Density":0.8}` cho **dòng phiếu Gallon
2000 / Lít 7953 / Kg ≈ 6362**, trong đó 7953 lít là số lít của **2101** gallon. Tổng hợp đã
tự đối chiếu mã: `ReceiptModel.java:341` dựng từ JSON, `:354-355` lấy lít/Kg từ getter,
`:381`/`:388` gán lại, `:420` cộng tổng — và `grep setGallon` trên toàn tệp cho thấy
**không có `itemModel.setGallon(itemData.getGallon())` ở đâu cả**.
⇒ **F12 là vi phạm NT3 nguyên văn** (*"số lít và Kg không khớp với Gallon"*) và nó **in sai
số ra tờ chứng từ pháp lý**. Mức: **Cao**. Đây là mục P2 cứu được — đừng để nó rơi khi gộp.

**(2) P2 kết luận SAI về mức của F11.** P2 xếp **Thấp**, viết *"lỗi tiềm ẩn, chưa gây hậu
quả đo được"* và *"đường Room không đi qua Gson"*. Câu sau sai chỗ: `RefuelItemData` mà
`fromRefuelItemData` nhận **chính là** vật Gson dựng.
🟢 **Bằng chứng bác bỏ:** `X2ProbeTest` ném ra **NPE thật** với stack trace tại
`RefuelItem.java:140`. Tổng hợp đã tự đối chiếu: dòng 140 gọi `data.getStatus().getValue()`
**không null-check**, trong khi ghi chú ngay dưới (`:141-142`) nói rõ ý định null-check, và
hai khối kế bên (`:143-146`, `:148-151`) đã làm đúng. Nặng hơn: cùng tệp có ghi chú một sự
cố **đã đo trên xe thật** — NPE cùng lớp làm **hỏng cả lượt sync mỗi 30 giây suốt nhiều giờ**.
⇒ Mức đúng: **Cao, dạng bom hẹn giờ** (chưa chứng minh được server có gửi `Status = 4`).

**(3) P1 báo SAI ở bảng "6/6 test đỏ xanh, không sửa assert nào".** X2 phát hiện và tổng hợp
đã tự xác minh: hai test "Tiếp cận" gọi **thẳng `DataHelper.patchRefuel`** —

```java
// RefuelFlowOtherTruckTest.java:525
DataHelper.PatchResult result = DataHelper.patchRefuel(UID_OTHER, latest -> latest.setApproachTime(approach));
// :538
DataHelper.PatchResult result = DataHelper.patchRefuel(UID_UNASSIGNED, latest -> latest.setApproachTime(approach));
```

**Cả P1 lẫn P2 đều cố ý giữ `patchRefuel` fail-closed** và thêm một cửa mới **có tên khác**.
Cửa mới **không nằm trên đường mà test đang gọi** ⇒ **cả hai test vẫn đỏ**. P1 khẳng định
dứt khoát là cả hai xanh; P2 nêu đúng cho test FOREIGN (Q7) nhưng vẫn khẳng định sai cho
test UNASSIGNED. **Cả hai phương án đều hụt ở đúng chỗ này** — xem QC1 ở §6.

Ghi thêm cho người sửa: javadoc của chính test đó (`:516-518`) viết *"Test này KHÔNG đòi nới
`patchRefuel`"* trong khi **thân test gọi thẳng `patchRefuel`**. Test đang tự mâu thuẫn với
lời tự mô tả của nó; đó là một phần lý do không ai gộp được hai phương án cho khớp.

---

## 3. Đánh giá theo ba nguyên tắc

### NT1 — Tra nạp và ghi nhận cho MỌI chuyến đều phải được

**Đã đạt (🟢 có bằng chứng đo):**

- **Chuyến của chính xe** — toàn bộ chặng chọn chuyến → ghi số → chốt → xác nhận → xem trước
  chạy được, kể cả **mất mạng suốt mẻ** (T1: 3 nhịp ghi đủ, trạng thái `DONE`, row ở lại
  hàng đợi). Lượt pull nền rơi vào giữa mẻ **không đè được** số đồng hồ
  (`helpers/DataHelper.java:2725` `batchRunningHere`).
- **Chuyến của xe khác — đường tiếp quản ở MÀN TRA NẠP: ĐỦ, cả hai nhịp.** Nhịp một
  (`postRefuelFromRefuelScreen`) và nhịp hai (`saveEndFieldsFromRefuelScreen`,
  `saveConfirmFieldsFromRefuelScreen`) đều tiếp quản được, cho **cả** chuyến xe khác lẫn
  chuyến chưa phân công. **Lỗi fail-closed cũ của nhịp hai đã hết** (T2, 3 test xanh).
- **Chuyến mở dở của xe khác KHÔNG khoá** nút Tiếp cận của xe này (T2).
- **Chuyến nhiều xe**: ba xe cùng chuyến, mẻ của cả ba đều về và đều vào được phiếu gộp;
  membership server đúng thứ tự (T3 `:141`).
- **In hộ**: xe này ghi được số phiếu lên mẻ của xe khác sau khi server nhận, **không đụng
  sản lượng** của mẻ đó (T2, T3 `:629`).
- **Ranh giới đúng ở cả hai chiều**: không hở (số đồng hồ bị `FINAL_VALUES_TOUCHED` chặn),
  không chặt quá (sau tiếp quản mọi đường ghi thường mở lại — T2
  `sauTiepQuanMoiDuongGhiThuongPhaiMoLai`).

**Đang vi phạm:**

| Vi phạm | Bằng chứng |
|---|---|
| **F2** — chuyến chưa phân công **không hiện ở danh sách nào** ⇒ không mở được phiếu ⇒ không bơm được. **Ngõ cụt nặng nhất của cả đợt** | 🟢 ĐÃ ĐO |
| **F3** — nút Tiếp cận/Rời đi **chết** ở màn danh sách; màn danh sách **thiếu hoàn toàn lối vào tiếp quản** trong khi màn tra nạp đã có đủ | 🟢 ĐÃ ĐO |
| **B2** — và ngõ cụt đó có **năm** đường, không phải ba: "Huỷ tiếp cận" cũng chết, mà nó lại là **lối thoát duy nhất** của hộp "Chuyến này chưa tra nạp?" | 🟡 ĐỌC MÃ |
| **F16** — NPE khi chạm dòng chưa phân công (đang bị F2 che) | 🟡 ĐỌC MÃ (tiền đề 🟢) |
| **F6 + B1** — mẻ xe khác sai tỉ trọng: không loại được, không sửa được, không in được | 🟢 F6 / 🟡 B1 |
| **F9(b)** — `DEMO-SGN` **hôm nay đã không tạo được phiếu** vì regex bắt cứng 3 ký tự | 🟡 ĐỌC MÃ |
| **F5 + B5** — mẻ 0 lít / đồng hồ khởi điểm 0: ba chỗ chặn ở ba màn | 🟢 hiện trạng / 🟡 hai chỗ còn lại |

**Chưa kiểm chứng được:** 🔴 hành vi của **server thật** với chuyến chưa phân công (NULL /
chuỗi rỗng / mã giữ chỗ) — quyết định phạm vi của F2 và F16. Trên tablet **không tồn tại
mẫu nào** để đọc ra (4/4 dòng đều đã phân công, đều có khoá `TruckNo`).

### NT2 — Chỉ chặn bản ghi "đang tra nạp" treo trả về SAU thời điểm Done

Đây là nguyên tắc được phủ kỹ nhất, và **phần lớn thiết kế hiện tại là đúng**.

**Đã đạt (🟢 ĐÃ ĐO — bốn cổng chặn độc lập, T1 + T2 + T3 kiểm chéo nhau):**

| Cổng | Vị trí | Đã kiểm |
|---|---|---|
| Hàng đợi nền — response non-DONE trên row đã DONE bị bỏ (`BLOCK_OLD_RESPONSE`) | `helpers/DataHelper.java:2547` | T1 |
| Lưu trực tiếp (`DIRECT_POST`) — cùng luật | `helpers/DataHelper.java:4156` | T1 |
| **Đường ĐỌC — `statusDowngrade` KHÔNG kèm điều kiện revision** ⇒ bản PROCESSING của server **dù revision 42 trên local revision 5** vẫn không hạ được trạng thái. **Đây là cổng mạnh nhất của thiết kế hiện tại** | `helpers/DataHelper.java:2728` | T1 |
| `postRefuel` — `localItem` DONE + snapshot non-DONE ⇒ `finishConflict`, không xét revision | `helpers/DataHelper.java:3957-3962` | T2 |
| `RefuelFieldPatch` — snapshot chưa Done không đắp được lên row đã Done (`ROW_ALREADY_FINALIZED`) | `helpers/RefuelFieldPatch.java` | T1, T3 `:489` |
| **Chiều ngược lại vẫn mở — KHÔNG chặn oan**: bản **Done** của server (sửa trên web) **vẫn sửa được** bản Done ở máy | — | T1 `banDoneCuaServerVanSuaDuocBanDoneOMay`, T3 `:368` |
| Lối vào tiếp quản **không** bỏ qua bảo vệ trạng thái (rủi ro về nguyên tắc cao nhất — đã kiểm: **không hở**) | `helpers/DataHelper.java:3956-3959` | T2 |
| "Nhập mấy lần mới ăn" **đã hết** ở ca nhiều mẻ: hai lượt sửa đứng trên cùng baseline cũ đều được ghi | — | T3 `:454` |

**Đang vi phạm — đúng một chỗ, và nó là chỗ nghiêm trọng nhất:**

**F1** 🟢 — `data/DataRepository.java:412`, vế `snapshot.getServerRevision() <= current.getServerRevision()`.

Ghi chú ngay trên nó (`:407-408`) viết: *"Khi server có revision cao hơn thì đó là thay đổi
có chủ ý và được nhận nguyên bản."* Giả định này **không kiểm chứng được từ phía client**:
`serverRevision` là **thứ tự server GHI**, không phải thứ tự nghiệp vụ. Một gói gửi lúc
10:00 mà server xử lý lúc 10:05 cũng nhận revision mới nhất. Nhìn từ máy, *"điều độ sửa có
chủ ý"* và *"gói treo xử lý muộn"* là **cùng một hình dạng dữ liệu**.

Điều làm F1 nguy hiểm hơn nó trông: mẻ bị hạ trạng thái **rụng khỏi phiếu gộp mà không ai
biết**, vì `warnIncompleteOthersThen()` chỉ nhìn `hasFailure()` mà lượt pull đó *thành công*.
Đo được trong test: tập in tụt từ **2 mẻ xuống 1**, tổng Kg mất trọn phần của xe B.

⇒ **F1 là một chỗ sót của một luật đã được áp đúng ở bốn cổng khác**, không phải một cân
nhắc thiết kế riêng của đường replica.

**Chưa kiểm chứng được:** 🔴 nghiệp vụ thật có cần đường "điều độ mở lại một mẻ đã Done từ
web" hay không (câu hỏi CH1 ở §6). Và 🔴 server có echo lại `clientSeq` của đúng gói nó vừa
xử lý hay không (quyết định xem có cách phân biệt nào không cần đổi backend).

### NT3 — Giữ NGUYÊN các chặn dữ liệu vô lý đang có

| Ràng buộc NT3 | Trạng thái | Bằng chứng |
|---|---|---|
| Tỉ trọng ngoài khoảng (0.72–0.86) | ✅ **CÒN NGUYÊN** ở `ReceiptModel.java:210`, `RefuelDetailConfirmActivity.java:509-527`, và ở **cả ba ô nhập** | 🟢 T1, T2, T3 `:513` |
| Nhiệt độ ngoài khoảng (15–40 °C) | ⚠️ **CÒN ở khâu tạo phiếu** (`ReceiptModel.java:165`) nhưng **KHÔNG ở ô nhập nào** (F7); màn Xác nhận chỉ đòi `> 0` | 🟢 chặn cuối luồng · 🟡 F7 |
| Chặng bay — 3 ký tự đầu **thuộc danh sách sân bay** | ❌ **CHƯA CÀI**: chỉ kiểm **định dạng**, `ZZZ-SGN` qua được (F9a). Và regex bắt cứng 3 ký tự nên **chặn oan `DEMO-SGN`** (F9b) | 🟢 F9a · 🟡 F9b |
| **Số hiệu chuyến** bỏ trống | ❌ **BỊ BỎ SÓT** ở khâu tạo phiếu (F4) — chỉ còn ở màn tạo chuyến tự tạo | 🟢 |
| **Số tàu bay** bỏ trống | ✅ CÒN NGUYÊN (`ReceiptModel.java:174` `AircraftCode`, `:181` `AircraftType`) | 🟢 T1, T2, T3 |
| Sản lượng ≠ (đồng hồ cuối − đồng hồ đầu) | ✅ CÒN NGUYÊN (`RefuelDetailConfirmActivity.java:509-527`) | 🟢 T1, T3 `:171` |
| **Số lít và Kg khớp với Gallon** | ❌ **BỊ VI PHẠM TRÊN CHÍNH TỜ PHIẾU** (F12) — và `createReceipt()` **không hề đối chiếu** hai nguồn này | 🟢 |

**Chỗ NT3 đang được thực thi tốt và không nên đụng:** T3 đã đo — tổng Gallon / lít / Kg của
`ReceiptModel` bằng đúng tổng các dòng; mỗi dòng giữ `lít = round(gallon × 3,7854)` và
`kg = round(lít × tỉ trọng)`; **chín** ràng buộc dữ liệu vô lý vẫn chặn đủ **kể cả khi mẻ
hỏng nằm ở dòng thứ hai — dòng của xe khác** (`:513`). Không có đường tiếp quản nào đi vòng
qua chúng: mọi chặn nằm ở `ReceiptModel.createReceipt` và `RefuelDetailConfirmActivity.save()`,
**trước** mọi lời gọi ghi (T2).

**Chưa kiểm chứng được:** 🔴 ngưỡng **15 °C** có đúng không (sân bay phía Bắc mùa đông xuống
dưới 15 °C là bình thường), và nhiệt độ đo là **nhiên liệu hay không khí**. Nếu ngưỡng sai
thì F7 không phải chuyện UI mà là **mẻ hợp lệ bị chặn in** — xem CH6.

---

## 4. Phương án sửa được khuyến nghị

Cột "Theo ai" ghi rõ **lấy phần nào của ai**; hai lượt soi chéo đã phân xử từng điểm.

| # | Phương án chốt | Theo ai | Ghi chú bắt buộc |
|---|---|---|---|
| **F1** | Bỏ vế thứ tư ở `DataRepository.java:412`, để `staleDoneDowngrade` chỉ còn "current DONE + snapshot non-DONE". **Kèm bước 2:** ghi anomaly `REPLICA_DONE_DOWNGRADE_BLOCKED`, và cho `keptNewer` (đang bị **đếm rồi vứt** ở `:349/:420/:465`) đi tới `OthersFreshness.gateForIncompleteOthers` trả **`WARN`** | P1+P2 cho bước 1 (giống nhau) · **P2 cho bước 2** | **Tuyệt đối không thêm `BLOCK`** — javadoc `OthersFreshness.java:170-177` cấm rõ. **Không làm bước 3 (`clientSeq`)**: X1 tìm ra bản phác thảo của P2 **fail-OPEN** khi `clientSeq = 0` (server legacy) ⇒ mở lại đúng lỗ F1 dưới tên khác |
| **F2** | Đổi **văn bản `@Query`** của ba hàm `getOthers` (`RefuelItemDao.java:47/50/53`) để bắt cả NULL. Giữ nguyên ba hàm `getByTruckNo` — chuyến chưa phân công thuộc tab "chuyến khác", đúng câu chữ skill | P1 cho câu SQL và **quy trình chứng minh** · P2 cho số dòng | **Bước bắt buộc trước commit** (P1 §5.4): `AppDatabase` `exportSchema = false` nên không có `app/schemas/14.json` để đối chiếu ⇒ **so `identityHash` trong `app/build/generated/**/AppDatabase_Impl.java` trước và sau**. Nếu hash không đổi thì khẳng định "không đụng schema" mới đứng được. **Vào cùng commit với F16 và F15-nửa-đếm-`failed`** |
| **F3** | **Chưa chốt được — cần chủ dự án.** Ba việc phải xong trước khi viết dòng nào: (a) chọn mô hình cho ca xe khác; (b) chọn cách làm xanh hai test đỏ (CH2); (c) nếu chọn "đóng dấu xe khi tiếp cận" thì **bắt buộc kèm đường gỡ dấu**. Kèm **B2** (năm đường, không phải ba) và **B3** (server-first cho replica) | — | Xem §5.1 — đây là chỗ tranh chấp lớn nhất, có **ba** phương án trên bàn |
| **F4** | Thêm chặn `FlightCode` trống vào vòng kiểm `createReceipt()`, dùng `buildItemException` (nói **mẻ nào** sai). **Phần A một mình = ngõ cụt, không được làm lẻ** | P1 cho chẩn đoán ngõ cụt (**điểm mạnh nhất của P1**) · P2 cho câu hỏi CH7 | Lối ra có **ba** ứng viên, xem §5.2. Trả lời **CH7** (`refuelItemType` nào hợp lệ mà không có `FlightCode`) **trước cả** khi chọn lối ra |
| **F5** | Không tự quyết — chờ **CH5**. Nếu chốt `< 0`: sửa `:2060`, **và** sửa `RefuelDetailScreenGuardTest` **theo ý nghĩa mới** (không phải nới assert), **và** xử lý `:2061` (P2) **và** `RefuelDetailConfirmActivity.java:520` (B5) | P2 (bắt thêm `:2061`) + **B5** | Giữ xanh `nt3MeKhongLitGhiNhanDuocNhungKhongXuatDuocHoaDon` (T2): mẻ 0 lít **ghi nhận được nhưng không xuất phiếu** |
| **F6** | Đưa bốn ngưỡng (`DENSITY_MIN/MAX`, `TEMP_MIN/MAX`) vào **một nơi duy nhất**, rồi `OthersFreshness.hasRequiredPrintFields` và `ReceiptModel:165/:210` **cùng đọc**. Mẻ xe khác hỏng bị **loại kèm cảnh báo** thay vì chặn cứng | P2 cho nội dung · **P1 cho chỗ đặt** (lớp riêng `RefuelValidationRanges`, không nhét vào `RefuelItemData` — giữ `OthersFreshness` thuần, không chạm Android) | **Bắt buộc xử lý B1** cùng lúc, nếu không ngõ cụt sống sót nguyên vẹn với chứng từ toàn mẻ xe khác. Và **hỏi CH8 trước**: hoá đơn thiếu phần của một xe có chấp nhận được về kế toán không |
| **F7** | **Cảnh báo tại ô nhập + giữ chặn cứng ở `createReceipt`** | **P1** (X1 và X2 cùng phán quyết) | Lập luận phân xử là **B6**: skill phân định rõ *"Chỉ chặn ở khâu xuất phiếu, không chặn ghi nhận"* ⇒ ô nhập nằm trong luồng tra nạp nên "cảnh báo, đừng chặn" áp được. Nếu muốn hết bất đối xứng thì **hạ tỉ trọng-tại-ô xuống cảnh báo**, KHÔNG nâng nhiệt độ lên chặn — và làm vậy **không nới NT3** vì chặn cứng ở khâu xuất phiếu vẫn nguyên. **Hỏi CH6 trước** |
| **F8** | **Không chặn, không rollback.** Gom `failed`, **một** hộp thoại sau vòng lặp liệt kê **số xe + số hiệu chuyến** của mẻ chưa đóng dấu, ghi anomaly `DOCUMENT_STAMP_PARTIAL`, và thêm nút **"Đóng dấu lại các mẻ còn thiếu"** | P2 (thêm nút thử lại) · **giữ nguyên văn lập luận chống rollback của P1** | Lập luận của P1 là bằng chứng tốt nhất của cả hai tài liệu: `patchAllPrintItems` được gọi từ `onActivityResult` (`:1246`/`:1253`) tức **SAU khi giấy đã ra** ⇒ *rollback là nói dối về tờ giấy đang có thật*. **F8 và F13 phải đi cùng nhau**: nút thử lại chỉ có tác dụng sau khi F13 được sửa. Hàm chạy trong `new Thread` (`:3103`) ⇒ mọi hộp thoại phải `runOnUiThread` + kiểm `isFinishing()` |
| **F9** | **Tách làm hai việc.** (b) Nới regex thành `^[A-Z]{3,4}-[A-Z0-9]{3,}$` — **việc độc lập, làm ngay, không cần chờ ai**: nó gỡ một ngõ cụt đang tồn tại. (a) Nếu chốt cài luật đối chiếu `Airports` thì dùng thiết kế hàm thuần của P2: **so tiền tố**, không cắt cứng 3 ký tự; **bảng rỗng ⇒ fail-open** | **P1 cho phát hiện (b)** · **P2 cho thiết kế hàm thuần (a)** | X1 bắt được: phương án P2 **tự mâu thuẫn** — `isKnownRoute` cho `DEMO-SGN` qua nhưng regex đứng **trước** nó vẫn chặn, nên test P2 tự đặt ra sẽ đỏ. **Bắt buộc gộp cả hai.** Lần đầu triển khai (a) nên **cảnh báo**, không chặn |
| **F10** | `valid &= …`, `validQC &= …`, **giữ nguyên `break`** | Trùng nhau, lấy bản nào cũng được | Chi phí bằng 0, hành vi hôm nay không đổi một chút nào. Kèm test quét mã nguồn |
| **F11** | **TÁCH LÀM HAI ĐỢT.** Phần (2) **làm trước, riêng**: null-check ở `RefuelItem.java:140` **và `:147`** (B8) theo đúng khuôn hai khối kế bên. Phần (1) đổi `@SerializedName("2")` → `"4"` cho `ERROR` ở **cả hai** tệp — **chờ backend trả lời CH9** | **P1 cho cách sửa** · P2 cho việc phải sửa **cả hai tệp** · **X2 cho việc tách đợt** · **B8 cho dòng `:147`** | Phần (2) **không chạm dây**, chặn được cả mã 5/6 sau này, và có **tiền lệ đo trên xe thật ngay trong cùng tệp**. Phần (1) **chạm dây**. Cả P1 lẫn P2 gộp hai phần vào một đợt là **sai thứ tự rủi ro**. Bác phương án dự phòng "đổi thứ tự khai báo enum" của P2 — sửa bằng một thứ tự khai báo ngầm sẽ hỏng ở lần refactor sau |
| **F12** | Bước 1: thêm `itemModel.setGallon(itemData.getGallon())` cạnh `ReceiptModel.java:381`, **đặt trước** khối `getReturnAmount() > 0` (`:392-417`) để phần trừ hàng trả đứng trên số đã chốt. Bước 2: log `GALLON_MISMATCH` cùng khuôn hai khối `VOLUME_MISMATCH`/`WEIGHT_MISMATCH` đã có ở `:364-378` | **P2 — dứt khoát. P1 SAI ở mục này** | Bước 1 **biến NT3 từ một thứ phải kiểm tra thành một thứ không dựng lên được**, và làm cho ghi chú đã có ở `:342-344` (*"GALLON là số duy nhất đo được… lít và khối lượng đều là dẫn xuất"*) **thành sự thật** — hiện nó chưa đúng. Bước 3 (`setGallon` uỷ quyền) chờ **CH10**; B7 đã kiểm sẵn lời gọi thứ tư là vô hại. **Ca thường (`Gallon == RealAmount`) không đổi một số nào** |
| **F13** | Đổi từ "**một** mẻ đã có số ⇒ bỏ cả lượt" sang "**mọi** mẻ đã có số ⇒ bỏ" | **Mã của P2 · bằng chứng của P1** | Ghi vào javadoc bằng chứng của P1: hàm anh em `updateAllReceipt` (`:2997-3002`) có **đúng khối đó đã bị comment lại** ⇒ cùng lỗi đã được nhận ra một lần và **bỏ sót đường hoá đơn**. Bác cách "bỏ hẳn khối kiểm" của P1: mỗi lần in lại sẽ POST toàn bộ và bơm `clientSeq` vô ích |
| **F14** | Thêm **vị từ thứ hai, tên khác** (`mayHaveForeignItems()`), **không đụng** `isCombinedDocument()` | **P2** | `isCombinedDocument()` đang bị test T3 `:589` canh (FB-1). Gốc lỗi là **một hàm bị dùng cho hai câu hỏi khác nhau**. Giữ `OthersFreshness.needsRefresh` làm van chống gọi mạng liên tục. Bác cách P1 (bỏ hẳn điều kiện): thêm một lượt mạng cho **mọi** chuyến một xe |
| **F15** | **Tách hai nửa.** Nửa **an toàn, làm ngay**: lượt bỏ payload ở `DataHelper.java:861-865` phải **đếm vào `failed`** để `hasFailure()` bật cảnh báo. Nửa **cần cân**: nới điều kiện từ "từ chối `UNKNOWN`" thành "chỉ từ chối `FOREIGN`" | **P1** (P2 chỉ ghi nhận, quá thụ động) | **Xếp vào cùng đợt với F2/F3**, không để ở đợt cuối như P1: ca `UNKNOWN` **chính là** ca chuyến chưa phân công — đúng lúc cần dữ liệu nhất. P2 đúng khi nói **không mất toàn bộ** dữ liệu xe khác (vẫn rơi xuống `refreshCachedOthers`); mất mát đúng bằng **khả năng phát hiện mẻ MỚI** |
| **F16** | Thay so chuỗi trần ở `RefuelRecyclerViewAdapter.java:123` bằng `DataHelper.isForeignTruckRefuel(item)` — **một luật một chỗ**, đã null-safe, đã qua `classifyWriteAccess` | **P1** (tốt hơn cách null-safe thủ công của P2) | Kèm theo **bắt buộc**: `R.string.assigned_to_another_truck` (*"đã phân công cho xe khác"*) **nói sai sự thật** với chuyến chưa phân công ⇒ cần chuỗi thứ hai + một seam đọc được enum `TruckOwnership` từ Activity. **Vào cùng commit với F2** (F2 làm F16 lộ ra ngay) |
| **B4** | Đổi hai chỗ ném `new InvalidRefuelTimeException(...)` thẳng ở `ReceiptModel.java:199` và `:210` sang `buildItemException` | Mới — chưa ai làm | Ở phiếu gộp, đây là khác biệt giữa **sửa được** và **không biết sửa mẻ nào**. Gần như không tốn gì |

### Thứ tự thực thi

Ba tài liệu đề xuất ba thứ tự khác nhau. **Chốt theo X1 + X2 (cùng phán quyết), không theo P1:**

> **Lý do đảo so với P1.** F1 và F12 làm **số liệu sai đi ra ngoài** — mẻ rụng khỏi phiếu,
> Gallon in sai trên chứng từ pháp lý. F2/F3 làm **không ghi được** — người dùng **biết**
> mình bị chặn và gọi điện được. Mất dữ liệu đã có tệ hơn không tạo được dữ liệu mới.
> P1 tự nêu đúng nghi ngờ này ở §5.6 nhưng không đảo thứ tự của mình.

**Đợt 1 — không chờ trả lời câu hỏi nào, rủi ro thấp, làm được ngay:**

1. **F1** bước 1 + bước 2 — xương sống NT2, làm xanh 2 test đỏ
2. **F12** bước 1 + bước 2 — một dòng, chặn số sai ra giấy
3. **F11 phần (2)** + **B8** — null-check `:140` và `:147`, không chạm dây
4. **F13 + F8** — đi cùng nhau (khoá nhau)
5. **F9 phần (b)** — nới regex, gỡ ngõ cụt `DEMO-SGN`
6. **F15 nửa "đếm vào `failed`"**
7. **F10** · **B4** — chi phí gần bằng 0

**Đợt 2 — một commit, vì chúng che nhau:** **F2 + F16 + F15 nửa còn lại**.
F2 làm F16 lộ ra ngay; cả hai cùng ca "chưa phân công". **Kèm bước so `identityHash`.**

**Đợt 3 — sau khi chủ dự án chốt:** F3 (CH2, CH3, CH4) · F4 (CH7 trước) · F5 (CH5) ·
F6 (CH8) · F7 (CH6) · F9 phần (a) (CH11) · F11 phần (1) (CH9) · F12 bước 3 (CH10) ·
F1 bước 3 (CH1, và chỉ khi backend xác nhận).

**F14** làm chung màn hình với F8/F13, xếp vào đợt 1 hoặc 2 tuỳ dung lượng.

---

## 5. Chỗ còn tranh chấp

Bốn chỗ dưới đây **P1/P2/X1/X2 chưa thống nhất**. Không gộp bừa thành một ý kiến giả vờ đồng
thuận — nêu lập luận từng phía và **cái gì sẽ phân xử được**.

### 5.1 F3 — bấm "Tiếp cận" trên phiếu của **xe khác** thì ghi vào đâu?

**Có ba phương án trên bàn, và không phương án nào được đo.**

| Phía | Lập luận | Bằng chứng chống lưng |
|---|---|---|
| **P1** — tiếp quản luôn mẻ của xe kia (đóng dấu xe hiện tại, sau hộp thoại hỏi) | Lặp lại **nguyên văn khuôn đã ship** cho màn tra nạp | Đường tiếp quản **đã phát hành** cho ca này: `RefuelRecyclerViewAdapter.java:122-147` → hộp `assigned_to_another_truck` → `showRefuel(item)` trên **đúng mẻ của xe kia** → `postRefuel(..., refuellingTruckOverride=true)` (`DataHelper.java:3852-3888`) **đóng dấu xe và gỡ cờ replica**. Có javadoc, có anomaly. Test xanh `sauTiepQuanMoiDuongGhiThuongPhaiMoLai` đang khoá hành vi đó |
| **P2** — không đụng mẻ xe khác; tạo **mẻ của chính xe này** trên cùng chuyến (`copyForNewBatch`) | Mô hình dữ liệu là **mỗi xe một mẻ**; ranh giới "không sửa dữ liệu mẻ xe khác" không bị đụng một dòng nào | DB thật: flightId 1294359 mang **hai row**, mỗi xe một mẻ. Adapter đã có `findCorrectItem` (`:155-163`). Và `RefuelPreviewActivity.java:3261-3276` (`createNewItem`, "Nạp thêm") có javadoc nói thẳng: *"Được phép cả khi dòng đang xem là của XE KHÁC — nhìn thấy xe bạn đã nạp cho chuyến này, xe mình nạp tiếp phần còn lại."* |
| **X1** — cửa hẹp `patchRefuelApproach` theo **khuôn `patchRefuelDocument`**: khoá trắng `{ApproachTime, LeaveTime}`, **server-first**, **không đổi chủ**, **không gỡ replica** | Không phát minh khuôn mới; giữ được cả hai ranh giới | `patchRefuelDocument` (`DataHelper.java:1015-1060`) tồn tại vì đúng một lý do cùng loại — "in hộ": ghi được lên mẻ xe khác, giới hạn bằng `DOCUMENT_KEYS` (`:990-995`), không đổi quyền sở hữu |

**Phán quyết của X1:** cả P1 lẫn P2 đều lệch — **hai mô hình cùng tồn tại trong app**, cho
hai tình huống khác nhau: *tiếp quản* (xe kia không nạp nữa) và *nạp thêm* (chia nhau một
chuyến). P2 xây một mô hình mới trên **đúng một** ví dụ trong DB; P1 bỏ qua rằng mô hình kia
cũng có javadoc chống lưng.

**Cái sẽ phân xử được:**
1. **Chủ dự án nói ra tình huống hiện trường thật.** Đây là câu hỏi nghiệp vụ, không phân xử
   được bằng mã — CH3.
2. **B3 loại bớt một nhánh ngay lập tức**: bất kỳ phương án nào cho ghi lên mẻ xe khác **phải
   server-first**, vì `replaceRemoteRefuelSnapshots` (`DataRepository.java:415-455`) ghi đè
   toàn bộ replica, chỉ giữ `localId`/`id`/`remoteOthersUidsJson`. **Không phương án nào của
   P1/P2 nhắc điều này** ⇒ cả hai phải sửa lại bất kể chủ dự án chọn gì.
3. **Một test đo được**: dựng ca "ghi `approachTime` cục bộ lên replica rồi chạy một lượt
   pull" và xem dấu vết có bị xoá không. Test này **chưa ai viết** và nó phân xử được B3 mà
   không cần hỏi ai.

### 5.2 F4 — lối ra cho mẻ mất `FlightCode` là gì?

Chặn ở `createReceipt()` thì cả bốn tài liệu đồng ý là đúng chỗ. Tranh chấp là **lối ra**.

| Phía | Đề xuất | Vấn đề của nó |
|---|---|---|
| **P2** | Chỉ chặn, không nhắc lối ra | 🟢 **Tạo ngõ cụt tuyệt đối** — X1 và X2 độc lập cùng kiểm chứng: `FlightCode` là **`<TextView>` chỉ đọc** (`res/layout/activity_refuel_preview.xml:87-97`) **và** không nằm trong `RefuelFieldPatch.Scope.PREVIEW` (`:86-101`). Mẻ mất số hiệu chuyến **không in được và không sửa được ở bất kỳ đâu** |
| **P1** | Đổi `TextView` thành ô nhập + **thêm khoá `"FlightCode"` vào `Scope.PREVIEW`** | Đúng về mặt gỡ ngõ cụt, nhưng X2 chỉ ra: đó là **cho sửa số hiệu chuyến bay ngay trước lúc in chứng từ pháp lý** — một thay đổi nghiệp vụ đáng kể, không phải bước phụ trợ. P1 nêu thành câu hỏi rồi lại đóng khung là *"bắt buộc kèm theo"*, tức **đã tự quyết trước khi hỏi** |
| **X2** | Chặn, nhưng lối ra là **kéo `FlightCode` về từ `Flight` liên kết cho người dùng XÁC NHẬN** — không tự động điền: *"Mẻ này thiếu số hiệu chuyến. Chuyến 1294359 trong danh mục là `VN 1237-01`. Dùng số hiệu này?"* | Người dùng vẫn là bên quyết định (không phải "đoán dữ liệu" — cả P1 và P2 đều loại đúng cách tự điền), và không mở một ô gõ tự do ngay trước máy in. Nhưng **chưa ai kiểm** liệu `Flight` liên kết có luôn sẵn khi `FlightCode` trống hay không |

**Cái sẽ phân xử được:** trả lời **CH7** trước (`refuelItemType` nào hợp lệ mà không có
`FlightCode` — BM7501? tra nạp phương tiện mặt đất?). Nếu tồn tại loại mẻ như vậy thì F4
phải **loại trừ chúng** trước, nếu không chính bản vá đẻ ra ngõ cụt mới. Sau đó là quyết
định nghiệp vụ của chủ dự án — CH4.

Một dữ kiện có sẵn: trên bản sao DB tablet, **0/4 dòng** có `FlightCode` trống. Dấu hiệu tốt
nhưng **mẫu 4 dòng quá nhỏ để kết luận**. Trước phát hành nên chạy một truy vấn đếm trên DB
máy thật đang vận hành.

### 5.3 F1 — có cách nào phân biệt "sửa có chủ ý" với "gói treo xử lý muộn" mà **không** đổi backend?

| Phía | Trả lời |
|---|---|
| **P1** | **Không.** Phải có cờ tường minh từ backend (`StatusChangedByUser` / `ReopenedAt`) |
| **P2** | **Có một ứng viên: `clientSeq`.** Gói PROCESSING treo do chính xe B gửi mang `clientSeq` của lần ghi đó; gói DONE gửi sau mang số **lớn hơn** ⇒ khi gói treo về muộn, `snapshot.clientSeq < current.clientSeq` ⇒ nhận diện được là bản cũ **bất kể revision**. P2 **tự khuyên không làm ngay** |
| **X1** | **P2 trả lời câu hỏi này tốt hơn** — cơ chế đứng được (`DataHelper.java:1170` cho thấy `clientSeq` là bộ đếm tăng đơn điệu **theo từng row**; `DataRepository.java:439-440` đã có sẵn nhánh "0 = không có thông tin"). **Nhưng bản phác thảo có lỗ**: `downgradeProvenStale` đòi **cả hai** `clientSeq > 0` ⇒ server legacy trả 0 ⇒ điều kiện false ⇒ mẻ Done **bị mở lại**, tức **lỗ F1 quay về nguyên vẹn** cho đúng lớp payload legacy mà `:435-440` sinh ra để xử lý |

**Đồng thuận thực chất:** cả bốn tài liệu đồng ý **bước 1 (bỏ vế revision) là đúng và đủ cho
lúc này**, và **không ai đề nghị làm bước 3 ngay**. Tranh chấp chỉ về việc `clientSeq` có
phải một đường đi được trong tương lai không.

**Cái sẽ phân xử được:** một câu trả lời từ backend — **khi xử lý một gói POST đến muộn,
server echo lại `clientSeq` của chính gói đó, hay ghi số của lần ghi cuối?** (CH1b). Nếu
làm bước 3, nó **phải fail-CLOSED khi thiếu thông tin**: không chứng minh được là "sửa có
chủ ý" thì chặn. Đây là chỗ P2 có linh cảm đúng nhưng không chỉ ra được lỗ.

### 5.4 F7 — ô nhập nhiệt độ: chặn hay cảnh báo?

| Phía | Đề xuất | Lập luận |
|---|---|---|
| **P2** | **Chặn** 15–40 tại ô, cho **đối xứng** với tỉ trọng đang chặn | Bất đối xứng giữa hai trường cùng loại chính là thứ đã đẻ ra F7. P2 **tự nhận lưỡng lự** (§U.8) |
| **P1** | **Cảnh báo tại ô + giữ chặn cứng ở `createReceipt`** | Chặn tại ô lúc đang bơm là rủi ro hiện trường |
| **X1 + X2** | **P1 đúng** | Phân xử bằng **B6** — skill viết nguyên tắc "cảnh báo, đừng chặn" cho **luồng tra nạp**, và dòng `:28` phân định rõ *"Chỉ chặn ở khâu xuất phiếu, không chặn ghi nhận."* ⇒ ô nhập nằm trong luồng tra nạp. **Cách gỡ bất đối xứng đúng là hạ tỉ trọng-tại-ô xuống cảnh báo, không phải nâng nhiệt độ lên chặn** — và làm vậy **không nới NT3** vì chặn cứng ở khâu xuất phiếu vẫn nguyên. Không tác nhân nào đề xuất hướng này |

**Tranh chấp còn lại, và nó lớn hơn câu hỏi chính:** **ngưỡng 15 °C có đúng không.** P1 hỏi
(sân bay phía Bắc mùa đông xuống dưới 15 °C là bình thường; nhiệt độ đo là **nhiên liệu hay
không khí**); P2 chỉ nêu rủi ro. **Cái sẽ phân xử được:** chủ dự án hoặc bộ phận kỹ thuật
nhiên liệu trả lời — nếu ngưỡng sai thì F7 không phải chuyện UI mà là **mẻ hợp lệ bị chặn
in**, và ưu tiên của nó cao hơn hẳn mức "Trung bình" hiện tại (CH6).

### 5.5 Một điểm nhỏ nhưng đáng ghi — P2 bịa ra một ưu thế cho phương án của mình

P2 loại câu SQL của P1 với lý do *"không xử lý được ca `truckNo` là khoảng trắng"`*.
**X1 và X2 độc lập cùng bác:** `IFNULL(x,'')` **không trim**; với `truckNo = ' '`, hai câu
cho kết quả y hệt. Hai câu **tương đương hoàn toàn**. Không đổi kết luận nào (cả hai đều
đúng cho cả ba khả năng của CH12), nhưng đây là một ưu thế **được bịa ra để loại phương án
đối thủ**, và người đọc báo cáo nên biết.

---

## 6. Câu hỏi cần chủ dự án chốt

Đã gom Q1–Q8 của P1, Q1–Q10 của P2, QX1–QX3 của X2, bỏ trùng, **đánh số lại CH1–CH13**.
Mỗi câu nêu rõ: **chặn việc gì**, và **mỗi lựa chọn dẫn tới hệ quả gì**.

### Nhóm A — chặn đợt sửa đầu tiên

**CH1 — Điều độ có cần đường "mở lại một mẻ đã Done từ web" không?**
*(P1 Q1 = P2 Q2)* · **Chặn:** F1 bước 3, và quyết định xem bước 1 đã đủ chưa.
- **Trả lời "không cần"** → bước 1 (bỏ vế revision) là **đủ và đóng hẳn**. NT2 đọc theo đúng
  câu chữ là chấp nhận điều này. Xoá trên web **vẫn xuống được** (`DataRepository.java:429`
  `setDeleted` đi đường riêng); sửa **số liệu** của mẻ Done **vẫn về được máy** (chỉ việc
  **hạ trạng thái** bị chặn).
- **Trả lời "có cần"** → client **không tự phân biệt được**. Cần backend gửi một cờ tường
  minh (`StatusChangedByUser` / `ReopenedAt`). Đó là thay đổi backend, phải chốt trước.
- **CH1b (chỉ hỏi nếu trả lời "có cần"):** khi server xử lý một gói POST đến muộn, nó
  **echo lại `clientSeq` của chính gói đó**, hay **ghi số của lần ghi cuối**? Câu này quyết
  định `clientSeq` có dùng làm tín hiệu phân biệt được không (§5.3).

**CH2 — Hai test đỏ về "Tiếp cận" được xử lý thế nào?** *(QX1 của X2 — không tài liệu nào
khác nêu)* · **Chặn:** F3, và **tính đúng đắn của báo cáo về "6/6 test đỏ".**
Hai test gọi **thẳng `DataHelper.patchRefuel`**. Cả P1 lẫn P2 đều cố ý giữ cổng đó
fail-closed và thêm một cửa **có tên khác** ⇒ **cả hai test vẫn đỏ**.
- **(a) Viết lại test cho gọi lối vào mới.** Không phải "nới assert" (assert giữ nguyên),
  nhưng **đổi điều test đang phát biểu**: từ *"cổng chung phải cho ghi mốc tiếp cận"* thành
  *"có một cổng nào đó cho ghi"*. Đó là quyết định của chủ dự án, không phải của tác nhân sửa lỗi.
- **(b) Nới `patchRefuel` theo TRƯỜNG BỊ CHẠM thay vì theo caller** — luật: *"patch chỉ đụng
  `approachTime`/`leaveTime` thì cho qua bất kể chủ sở hữu"*, với `FINAL_VALUES_TOUCHED`
  (`DataHelper.java:1158-1164`) vẫn canh mọi thứ chạm số liệu chốt. **Đây là ứng viên duy
  nhất làm xanh cả hai test đỏ ĐÚNG NHƯ CHÚNG ĐƯỢC VIẾT.** Rủi ro cần cân: nó vẫn ghi lên mẻ
  của xe khác, và phải chắc mốc giờ không gỡ cờ replica (xem B3).

**CH3 — Bấm "Tiếp cận" trên phiếu của xe khác thì ghi vào đâu?** *(P2 Q7)* · **Chặn:** F3.
- **(a) Tiếp quản mẻ của xe kia** (P1) — lặp lại khuôn đã ship, nhưng một cú chạm nhầm ở màn
  danh sách là **chiếm mẻ của xe khác** rồi gỡ cờ replica, hỏng phiếu gộp của cả chuyến.
- **(b) Tạo mẻ của chính xe này trên cùng chuyến** (P2) — giữ nguyên ranh giới, nhưng tạo
  **row mới** chạm đường đồng bộ, membership `remoteOthersUidsJson`, preflight identity, và
  phiếu gộp. P2 tự nhận đây là **phần yếu nhất** của cả tài liệu mình.
- **(c) Cửa hẹp theo khoá trắng `{ApproachTime, LeaveTime}`, server-first, không đổi chủ**
  (X1) — không chiếm mẻ, không tạo row mới, không phát minh khuôn mới.
- Ba tình huống hiện trường tương ứng: *xe kia không nạp nữa* (a) / *chia nhau một chuyến* (b) /
  *chỉ ghi nhận mốc phục vụ* (c). **Chủ dự án nói ra tình huống thật thì mã sẽ tự chọn được.**

**CH4 — Bấm "Tiếp cận" có phải là lời tuyên bố "xe tôi phục vụ chuyến này" không?**
*(QX2 của X2 + điểm P1 tự ngờ)* · **Chặn:** F3, và đây là **rủi ro lớn nhất của F3**.
Cả P1 lẫn P2 đều **đóng dấu xe** ngay tại nút Tiếp cận. X1 và X2 độc lập cùng bác:
tiếp cận **không phải** tra nạp — chính mã xác nhận điều đó bằng nhánh **"Huỷ tiếp cận"**
cho ca `gallon <= 0` (*"Chuyến này chưa tra nạp?"*).
- **Nếu chốt "có đóng dấu"** → **bắt buộc kèm đường gỡ dấu**. X1/X2 đã kiểm:
  `RefuelRecyclerViewAdapter.java:362-378` chỉ đặt `setApproachTime(null)`/`setLeaveTime(null)`
  rồi gọi `postRefuel(itemData, false)` — **không gỡ `truckId`/`truckNo`/`remoteReplica`**,
  và bản 2 tham số truyền `refuellingTruckOverride = false` ⇒ **chính nó cũng fail-closed**.
  Hiện trạng: **một chiều, không có đường lùi.** Rủi ro lớn nhất của F3 **không có giảm nhẹ
  nào** — P2 khẳng định là có, nhưng đã được kiểm và bác.
- **Nếu chốt "không đóng dấu"** → đóng dấu ở đúng chỗ đang có bằng chứng nghiệp vụ mạnh:
  lần ghi tra nạp thật qua `postRefuelFromRefuelScreen`. Điều này khả thi nếu cửa tiếp cận
  là cửa theo phạm vi trường (CH2b hoặc CH3c).

### Nhóm B — chặn các phát hiện NT3

**CH5 — `manualInputProblems`: `realAmount < 0` hay `<= 0`?** *(P1 Q4 = P2 Q3)* · **Chặn:** F5.
**Hai luật đang được canh bằng hai test mâu thuẫn nhau** — sửa bên nào cũng làm đỏ bên kia.
- **Chọn `< 0`** (khớp skill `fms-refuel-flow` chốt 2026-09-05 và khớp
  `RefuelDetailConfirmActivity.java:518`) → hết ngõ cụt; **và** phải sửa
  `RefuelDetailScreenGuardTest.baoLoiNhapTayChiDungOSai` **theo ý nghĩa mới** (đây **không**
  phải nới assert). Hệ quả: một mẻ **thật sự chưa bơm gì** chốt được thành DONE bằng nút Lưu
  — đúng ý skill, nhưng nó **đổi số liệu ca trực**.
- **Chọn `<=`** (giữ hiện trạng) → phải trả lời **người dùng làm gì tiếp** khi mẻ 0 lít không
  đóng được, vì hiện **không có lối ra nào**.
- **Dù chọn gì**, còn hai ngõ cụt anh em phải xử cùng: `RefuelDetailActivity.java:2061`
  (`startNumber <= 0`) và `RefuelDetailConfirmActivity.java:520`.

**CH6 — Ngưỡng nhiệt độ 15–40 °C có đúng không, và đo cái gì?** *(P1 Q5)* · **Chặn:** F7,
và **có thể nâng mức F7 lên hẳn**.
- (a) **15 °C có đúng ngưỡng không?** Sân bay phía Bắc mùa đông xuống dưới 15 °C là bình
  thường. Nếu ngưỡng sai thì đây **không phải chuyện UI** mà là **mẻ hợp lệ bị chặn in**.
- (b) Nhiệt độ đo được là **nhiên liệu** hay **không khí**? Câu trả lời quyết định (a).
- (c) Ô nhập nên **cảnh báo** (khuyến nghị, xem §5.4) hay **chặn**? Nếu chọn cảnh báo, có
  **hạ tỉ trọng-tại-ô xuống cảnh báo** cho khỏi lệch nhau không? (Chặn cứng ở khâu xuất phiếu
  giữ nguyên trong mọi trường hợp ⇒ **không nới NT3**.)

**CH7 — Có loại mẻ hợp lệ nào KHÔNG có `FlightCode` không?** *(P2 Q8)* · **Chặn:** F4, và
**phải trả lời trước cả CH4 về lối ra**. BM7501 (hút nhiên liệu)? Tra nạp phương tiện mặt
đất? `refuelItemType` khác? Nếu **có** thì F4 phải loại trừ chúng, nếu không **chính bản vá
đẻ ra ngõ cụt mới**.

**CH8 — Hoá đơn thiếu phần của một xe có chấp nhận được về kế toán không?**
*(P1 §5.3 — nghi ngờ P1 nêu mà P2 hoàn toàn không có)* · **Chặn:** F6.
F6 đánh đổi *"không in được"* lấy *"in ra tờ thiếu mẻ, kèm cảnh báo"*. Skill nói *"cảnh báo,
đừng chặn"* — nhưng skill viết cho **luồng tra nạp**, còn chứng từ nhiên liệu là **giấy tờ
pháp lý**. Một tờ hoá đơn thiếu phần của một xe có thể là vấn đề kế toán nặng hơn là không
in được rồi gọi điện xử lý.
- **Giảm nhẹ quan trọng mà không tài liệu nào nói ra** (X2 tìm ra): chứng từ chỉ bị thiếu mẻ
  khi mẻ đó là **của xe khác** *và* dữ liệu của nó sai khoảng — tức chính tờ giấy đó **hôm
  nay cũng không in ra được**. **Không có ca nào "đang in đủ, sau bản vá thành in thiếu".**

**CH9 — Server có hiểu `Status = "4"` cho ERROR không?** *(P2 Q9 = P1 Q2b)* · **Chặn:** F11
phần (1). Hôm nay app phát ra `"2"` cho ERROR (trùng mã của PAUSED). Sau bản vá nó phát ra
`"4"`.
- Đã `grep` toàn bộ `app/src/main`: **không có chỗ nào đặt `REFUEL_ITEM_STATUS.ERROR` hay
  `.PAUSED` lên một mẻ** ⇒ app **chưa bao giờ** chủ động gửi hai giá trị này. Nhưng một row
  **đọc từ server** đang mang ERROR **vẫn có thể được POST lại**.
- **Câu hỏi kèm:** server **có thật sự** gửi `Status = 2` hoặc `4` cho client không? Nếu
  **chưa bao giờ** thì F11 là **bom hẹn giờ** chứ chưa phải sự cố đang xảy ra (DB trên tablet
  chỉ có status 0 và 3), và phần (1) có thể lùi lại. **Phần (2) — null-check — làm ngay bất
  kể câu trả lời**, vì nó không chạm dây.

**CH10 — `RefuelItemData.setGallon()` có được phép bỏ hẳn (hoặc uỷ quyền cho
`setRealAmount`) không?** *(P2 Q10)* · **Chặn:** F12 bước 3.
Bỏ field `gallon` **đổi hình dạng JSON gửi lên server**. Bốn lời gọi thật đã được rà hết
(`RefuelDetailActivity:2475`, `:2563`, `split():1398`, và `RefuelItemData:455` — B7 đã kiểm
là vô hại), **cả bốn đều đặt cùng giá trị vừa truyền cho `setRealAmount`** ⇒ đều **thừa**.
**Bước 1 và bước 2 của F12 không phụ thuộc câu này** và làm được ngay.

**CH11 — NT3 "chặng bay 3 ký tự đầu không thuộc danh sách sân bay": cài luật hay sửa phát
biểu?** *(P1 Q6 = P2 Q5)* · **Chặn:** F9 phần (a). *(Phần (b) — nới regex — **không** chờ câu
này, làm ngay.)*
- **Cài luật đối chiếu bảng `Airports`** (25 dòng, `AirportsDao.getAll()`, đã có sẵn trên
  máy) → dùng thiết kế hàm thuần **so tiền tố**, **không cắt cứng 3 ký tự** (sân bay `DEMO`
  có **4** ký tự), và **bảng rỗng ⇒ fail-open** (máy mới cài chưa đồng bộ danh mục không
  được thành ngõ cụt). Rủi ro: đây là ràng buộc **thêm mới** ⇒ rủi ro **chặn oan** cao nhất
  trong cả danh sách. Chặng bay quốc tế / mã sân bay chưa có trong bảng sẽ không in được ⇒
  lần đầu triển khai nên **cảnh báo**, không chặn.
- **Sửa phát biểu NT3 thành "đúng định dạng"** → hiện trạng đã thoả, không phải làm gì thêm
  ngoài phần (b).
- **Câu hỏi kèm:** `DEMO` là dữ liệu test hay một sân bay thật trong hệ thống?

### Nhóm C — không chặn phương án nào, nhưng cần biết

**CH12 — Server trả gì cho `TruckNo` của chuyến chưa phân công: NULL, chuỗi rỗng, vắng khoá,
hay mã giữ chỗ?** *(P1 Q7 = P2 Q1)* · **Không chặn** — phương án F2 đã cố ý đúng cho **cả ba**
khả năng đầu.
- **Nhưng nếu là MÃ GIỮ CHỖ** (ví dụ `"CHUA_PHAN_CONG"`) thì **phương án F2 SAI và phải làm
  lại**. Trên tablet **không tồn tại mẫu nào** để đọc ra (4/4 dòng đều đã phân công).
- **Câu hỏi kèm (thủ tục, phải hỏi dù phương án đã sẵn sàng):** được phép đổi **văn bản truy
  vấn** trong `data/dao/` không — không đổi schema, không thêm migration, `AppDatabase` vẫn
  version 14? BRIEF liệt `data/dao/` là vùng phải hỏi trước. **Kèm cam kết**: trước khi
  commit sẽ so `identityHash` trong `AppDatabase_Impl.java` trước/sau để chứng minh.

**CH13 — F12 đã in sai số ra giấy: có cần rà lại chứng từ đã xuất không?** *(QX3 của X2)* ·
**Không chặn** bản vá, nhưng ảnh hưởng vận hành.
Sau bản vá, **bản in lại của một mẻ đang trôi sẽ KHÁC bản in cũ**. Ca thường
(`Gallon == RealAmount`) không đổi một số nào. Đường trôi có tần suất cao nhất là
`RefuelDetailActivity.java:2474-2475`/`:2562-2563` — chạy **mỗi nhịp đồng hồ trên mọi mẻ**,
biên độ nhỏ (< 0,5 gallon) nhưng tần suất 100%; đường trôi biên độ lớn là **payload server**,
mà tần suất của nó **chưa ai chứng minh được**.

**CH14 — Chặng ký + in ZPL: có kiểm thủ công trước khi phát hành không?**
*(P1 Q8 = P2 Q6)* · **Không chặn** phương án nào (không phương án nào chạm vào chặng này),
nhưng xem §7 — đây là mảng tối lớn nhất của cả đợt.
*Ghi chú:* P1 nêu "BRIEF và DEVICE mâu thuẫn về tablet" thành một câu hỏi riêng. **Đã giải
quyết, không cần hỏi chủ dự án**: BRIEF viết trước khi kết nối được (sáng 2026-09-06),
DEVICE.md viết sau khi kết nối thành công — **DEVICE.md đúng**, tablet đã kết nối. Nhưng
điều đó **không đổi kết luận nào**, vì chặng in nhiệt vẫn cần **máy in Zebra thật**.

---

## 7. Chưa kiểm chứng được — nói thẳng

### 7.1 Toàn bộ chặng **ký / in ZPL / máy in**: TRẮNG

🔴 **Không một khẳng định nào trong toàn bộ bảy tài liệu về ZPL, ZebraWorker, SignatureCache
hay máy in được kiểm chứng trên thiết bị.** Tất cả đều là **đọc mã nguồn**.

Cụ thể, những thứ **chỉ được canh bằng mắt**:
- Thứ tự **ký trước — in sau** (`ZebraWorker.storeImage("E:BUYER.GRF", …)` rồi ZPL tham
  chiếu `^XGE:BUYER.GRF,1,1^FS`).
- Việc **không dọn ảnh chữ ký sớm** (`PrintReceiptActivity.promoteCachedSignatures()` gọi
  trong `save()`) — T1 có test `chuKyDuocGiuLaiChoToiLucDayLen` nhưng nó kiểm ở mức **model**,
  không phải trên máy in.
- Toàn bộ `ZplLayoutBuilder`, `BM7501Printer`, `PrinterProvisioner`.
- Ca **máy in lỗi / mất mạng không chặn xuất hoá đơn** — T1 có test
  `mayInLoiVaMatMangKhongChanXuatHoaDon`, nhưng nó **giả lập** lỗi ở mức mã, không phải một
  máy in thật bị rút cáp.

**Vì sao không kiểm được:** **không có máy in nhiệt Zebra** trong đợt này. Tablet thì có,
máy in thì không — và không tài liệu nào của đợt (kể cả DEVICE.md) nhắc tới một máy in.

### 7.2 Tablet đã kết nối nhưng bộ `androidTest` **chưa được phép chạy**

Tablet đã kết nối (`R52XA0AYEQZ`), bản **debug** (`run-as` dùng được), gói
`com.megatech.fms.test` đã cài sẵn ⇒ về mặt kỹ thuật `./gradlew connectedDebugAndroidTest`
chạy được.

**Nhưng bộ `androidTest` sẵn có là bài dò có GHI vào `fms.log` thật và GỌI ROUTER THẬT**, nên
**chưa được phép chạy** — nó sẽ làm bẩn nhật ký và dữ liệu của chủ dự án trên máy đang vận
hành. Mọi thao tác với tablet trong đợt này giới hạn ở **đọc**: sao `fms_debug.db` về, đọc
`shared_prefs/FMS.xml`. **Không ghi ngược lên máy, không xoá gì.**

⇒ Nếu chủ dự án muốn phủ thêm phần này, cần một trong hai: (a) cho phép chạy và chấp nhận
nhật ký bị bẩn; hoặc (b) tách một bộ `androidTest` mới **không** ghi `fms.log` và **không**
gọi router.

### 7.3 Không có backend để đối chiếu

🔴 Bốn câu hỏi dưới đây **không thể trả lời từ phía client**, và mỗi câu đang chặn hoặc định
hình một phương án:

| Câu | Chặn |
|---|---|
| Server trả gì cho `TruckNo` của chuyến chưa phân công | F2/F16 (CH12) |
| Server có gửi `Status` = 2 hoặc 4 không, và có hiểu `"4"` không | F11 (CH9) |
| Server có echo lại `clientSeq` của gói nó vừa xử lý không | F1 bước 3 (CH1b) |
| Payload server thật có bao giờ gửi `Gallon ≠ RealAmount` không | **Tần suất** của F12 (không phải cơ chế — cơ chế đã đo) |

### 7.4 Những ca cụ thể chưa dựng được test

| Nội dung | Vì sao chưa dựng được |
|---|---|
| **F16** — ca crash thật ở Adapter | Cần Robolectric dựng `RecyclerView` + binding. Tiền đề (`getTruckNo()` trả null) **đã đo**; ca crash thì chưa. Và **F2 đang che nó** — dòng đó hiện không bao giờ hiển thị |
| **F13, F14** — `updateAllInvoice`, `refreshOthersBeforeDocument` | Nằm trong Activity, phụ thuộc `invoiceModel` và `onActivityResult` / cần chạy Activity thật |
| **F15** — payload Others bị vứt khi root `UNKNOWN` | Cần giả lập endpoint Others đầy đủ |
| **B3** — dấu vết ghi cục bộ lên replica bị lượt pull xoá | Chưa ai viết. **Test này phân xử được §5.1 mà không cần hỏi ai** — đề nghị viết trước khi chốt F3 |
| **B8** — NPE thứ hai ở `RefuelItem.java:147` | Kết luận là **đọc mã**, chưa đo. (Ca `:140` thì **đã đo**, có stack trace) |
| `RefuelFieldPatch.apply()` chỉ chặn "row đã chốt" khi `anotherWriterMoved` | P1 lập luận từ mã rằng mọi lần ghi DONE ở local đều tăng `ClientSeq` nên ca đó không dựng được, nhưng **chưa chứng minh bằng test**. Nếu tồn tại một đường ghi DONE **không** tăng seq (một nhánh ACK nào đó), cổng này bị vô hiệu |
| `ReceiptModel.createReceipt()` **sau** vòng kiểm | Chạm `FMSApplication.getSetting()` và repository thật ⇒ chỉ **vòng kiểm** được test. Phần sinh số phiếu và dựng dòng chứng từ **chưa chạy qua test end-to-end** |
| Cấu hình **`BuildConfig.FHS = true`** | Đang là `false` (`app/build.gradle:90`, `:125`). Bản FHS đi hoàn toàn bằng đường `takeover`; **chưa kiểm thử được cấu hình đó** |
| **Xe LCR600 thật**, đường bắt đầu/dừng | Skill `fms-refuel-flow` yêu cầu **bắt buộc trước phát hành**. **KHÔNG thực hiện được** trong đợt này |

### 7.5 Ranh giới của báo cáo này

Đừng đọc tài liệu này như một **đánh giá đầy đủ về luồng in**. Nó là một đánh giá đầy đủ về
**luồng dữ liệu tra nạp cho tới lúc dựng chứng từ**, và mọi thứ **sau** điểm
`ReceiptModel.createReceipt()` trả về đều chỉ được canh bằng đọc mã.

---

## Phụ lục — tệp gốc

`scratchpad/BRIEF.md` · `DEVICE.md` · `FINDINGS.md` · `test-T1.md` · `test-T2.md` ·
`test-T3.md` · `plan-P1.md` · `plan-P2.md` · `cross-X1.md` · `cross-X2.md`

Luật nghiệp vụ đã chốt: `.claude/skills/fms-refuel-flow` · `fms-sync-concurrency` ·
`fms-receipt-print` · `fms-build-test`. **Ba nguyên tắc NT1/NT2/NT3 đã được ghi vào skill
cùng ngày 2026-09-06** — xem `fms-refuel-flow` (NT1, NT3) và `fms-sync-concurrency` (NT2).
