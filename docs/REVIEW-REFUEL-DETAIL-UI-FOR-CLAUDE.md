# Review giao diện Refuel Detail — gửi Claude đánh giá

**Ngày:** 18-08-2026  
**Phạm vi:** `RefuelDetailActivity` và hai layout `activity_refuel_detail`  
**Trạng thái:** đã build, cài đè và kiểm tra trực tiếp trên tablet test SM-X306B

## 1. Mục tiêu thay đổi

Thiết kế lại màn hình thao tác một chuyến bay đang nối đồng hồ bơm theo thứ tự ưu tiên:

1. Nhận dạng chuyến bay/tàu bay trước; loại nhiên liệu được ẩn theo yêu cầu vận hành ngày 18-08.
2. Sản lượng và số đồng hồ tổng là vùng nổi bật; meter bắt đầu không hiển thị.
3. Trạng thái kết nối phải phân biệt rõ `CONNECTING / OK / STALE / ERROR`, không dùng nền xanh cho mọi trạng thái.
4. Ba thao tác thiết bị luôn hiện, không nằm trong dropdown: `KẾT NỐI LẠI`, `KẾT THÚC THỦ CÔNG`, `KHỞI ĐỘNG LẠI ỨNG DỤNG`.
5. Không gọi nút kết thúc thủ công là E-STOP. Dòng giải thích thường trực đã được bỏ theo yêu cầu làm gọn UI; cần phản biện xem có phải chuyển cảnh báo này vào dialog xác nhận hay không.
6. Tất cả trường sửa được ở bản cũ vẫn phải bấm sửa được y như cũ.

## 2. Backup và rollback

Backup nguyên trạng trước thay đổi nằm tại:

```text
backups/refuel-detail-ui-before-20260818-154918/
```

Khôi phục ngay từ thư mục gốc dự án:

```bash
zsh backups/refuel-detail-ui-before-20260818-154918/restore.sh
```

Script khôi phục đúng bản working tree trước khi đổi UI, gồm Java, hai layout,
resource values và `app/build.gradle`; đồng thời xóa layout test và các drawable
`refuel_ui_*` mới. Script không đụng DB, APK hay Git index.

Hai mốc rollback hẹp hơn cũng đã có:

- `backups/refuel-detail-ui-before-overlap-fix-20260818-162932/`
- `backups/refuel-detail-ui-before-content-simplification-20260818-164144/`

## 3. File thay đổi

- `app/src/main/java/com/megatech/fms/RefuelDetailActivity.java`
- `app/src/main/res/layout/activity_refuel_detail.xml`
- `app/src/main/res/layout-land/activity_refuel_detail.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/megatech/fms/RefuelDetailLayoutTest.java`
- `app/build.gradle` (bật Android resources cho Robolectric layout test)
- Các drawable mới có prefix `app/src/main/res/drawable/refuel_ui_`

Không có migration, thay đổi schema DB, API payload hoặc logic đồng bộ trong patch giao diện này.

## 4. Hợp đồng tương thích đã giữ

Hai layout dọc/ngang giữ toàn bộ ID runtime cũ và đúng kiểu view mà Activity ép kiểu. Layout ngang có thêm ba ID nội bộ chỉ phục vụ đo layout. Audit độc lập xác nhận:

- Không thiếu ID runtime.
- Không có ID sai kiểu hoặc trùng ID.
- Các nút/hàng thao tác vẫn có `android:onClick="onClick"` như code cũ.
- Nút Start vẫn mặc định `GONE`; chỉ LCR600 được code cũ cho hiện. Không mở nhầm Start cho TCS.
- Giữ đủ 9/9 trường sửa được:
  1. Tên in hóa đơn.
  2. Loại tàu bay.
  3. Số hiệu tàu bay.
  4. Bãi đỗ.
  5. Số phiếu hóa nghiệm.
  6. Nhiệt độ thủ công.
  7. Tỷ trọng.
  8. Nhân viên lái xe.
  9. Nhân viên tra nạp.

Hãng bay và mã chuyến bay vẫn read-only đúng hành vi Java hiện tại.

Các view bị bỏ khỏi giao diện nhưng code còn tham chiếu (`lblFuelGrade`, meter bắt đầu, freshness) vẫn tồn tại đúng kiểu và được đặt `GONE`; không xóa ID nên không tạo `findViewById == null`.

## 5. Phần Java được nối với UI mới

- Header đổi theo `REFUEL`/`EXTRACT`, kèm mã chuyến và số hiệu tàu bay.
- Loại nhiên liệu vẫn được resolve nội bộ từ `PName`/`ProductName`, nhưng view được đặt `GONE` theo yêu cầu mới.
- Đơn vị estimate hiện `GAL` theo invariant hiện có: `BM7501Prefill` đổi trực tiếp `estimateAmount * GALLON_TO_LITTER`. Không dùng `mItem.unit` vì field đó là đơn vị tính tiền (`0 = USG`, khác `0 = KG`).
- Đơn vị gross và totalizer lấy `BuildConfig.METER_UNIT`, không trộn với đơn vị hóa đơn.
- Tải dầu hiện `KG`.
- Tuổi dữ liệu đồng hồ vẫn cập nhật nội bộ từ `lastMeterDataAt`, nhưng dòng `DỮ LIỆU MỚI · n GIÂY` không còn hiển thị.
- Kết nối được gộp một dòng theo thiết bị và trạng thái: `TCS: Đang kết nối`, `TCS: Đã kết nối`, `TCS: Dữ liệu chậm`, `TCS: Lỗi kết nối` (tương tự LCR).
- Card kết nối đổi nền/icon/chữ theo bốn trạng thái, màu không phải tín hiệu duy nhất.
- `btnReconnect` luôn giữ vị trí; khi OK thì disable thay vì `INVISIBLE`.
- Restart bị disable trong `STARTING/STARTED/ENDING`, đồng thời handler có guard lần hai chống click đã xếp hàng trước lúc trạng thái đổi.
- Khi TCS rớt kết nối giữa mẻ, không đổi trạng thái nghiệp vụ thành `NONE`; vẫn giữ `STARTED` để không hiển thị sai “chưa tra nạp” và vẫn khóa Back/Restart. Luồng nhận TCS vẫn về `NONE` để chờ reconnect như trước.
- `lbl_refuel_status` không còn bị `setBackgroundColor()` phá nền bo góc; panel cha nhận drawable theo trạng thái.

## 6. Kết quả kiểm tra

```text
xmllint: PASS cho cả hai layout và toàn bộ resource XML mới
git diff --check: PASS
zsh ./gradlew :app:assembleDebug: BUILD SUCCESSFUL
zsh ./gradlew :app:testDebugUnitTest: BUILD SUCCESSFUL
Unit tests: 365 passed, 0 failed, 0 errors
Layout regression: PASS ở đúng viewport SM-X306B `960 × 576dp`, và PASS với font scale `1.3`
APK thermal: app/build/outputs/apk/thermal/fms-thermal-110-20260818.1.apk
assembleThermal + lintVitalThermal: BUILD SUCCESSFUL
Tablet: cài `adb install -r -t` thành công; package/version/chữ ký APK mới khớp bản đang cài
Quan sát thật: không còn text bị cắt/chồng; toàn bộ console phải vừa trong viewport, không cần cuộn ở font scale 1.0
Ảnh trước/sau: `backups/refuel-detail-ui-before-content-simplification-20260818-164144/tablet-before.png` và `tablet-after.png`
lintDebug: project còn 15 lỗi/1971 cảnh báo có sẵn; lỗi đầu ở SettingActivity (MissingPermission). Không có lint Error trong RefuelDetailActivity hoặc hai layout mới.
```

Cảnh báo javac còn lại là deprecated/Room field-setter đã có sẵn, không phát sinh lỗi build.

## 7. Những điểm đề nghị Claude phản biện kỹ

### A. Xác nhận đơn vị nghiệp vụ

Patch tách đơn vị meter khỏi đơn vị hóa đơn, nhưng cần xác nhận với dữ liệu thật:

- `BuildConfig.METER_UNIT = GALLON` có đúng với cả LCR và TCS trên tất cả xe không?
- `estimateAmount` có luôn là USG ở toàn bộ endpoint/phiếu cũ như code `BM7501Prefill` đang giả định không?
- `ProjectedCapacity/ActualCapacity` có luôn là KG không? Nếu backend có `CapacityUnit`, phải dùng trường đó thay vì cố định `KG`.

Đây là điểm cần NO-GO nếu chưa xác nhận.

### B. Loại nhiên liệu đã được ẩn — có cần gate ở lớp nghiệp vụ hay không

UI không còn hiển thị loại nhiên liệu theo yêu cầu người dùng, và chưa thêm điều kiện vào Start gate vì chưa chứng minh `PName/ProductName` luôn có cho mọi phiếu hợp lệ. Đề nghị Claude truy dữ liệu/API và quyết định:

- Nếu field luôn bắt buộc: thêm `fuelGradeKnown` vào gate.
- Nếu có luồng hợp lệ không có field: xác định nguồn fuel order đúng trước khi chặn.

Không nên tự chặn vận hành chỉ từ giả định UI.

### C. Start và “gói dữ liệu đầu tiên”

Patch chưa thêm điều kiện `lastMeterDataAt > 0` vào Start gate. Đây là chủ ý tạm thời, không phải kết luận an toàn cuối: với LCR hiện tại, `reader.requestData()` chỉ bắt đầu trong luồng Start, nên thêm điều kiện đó trực tiếp có thể tạo deadlock — phải có dữ liệu trước khi Start nhưng app chỉ hỏi dữ liệu sau Start.

Nếu muốn gate chặt hơn, Claude cần thiết kế một phép “ready probe” trước Start (ví dụ đọc totalizer/serial/field phù hợp), phân biệt packet quản trị với packet meter thật, rồi mới đưa `meterDataReady` vào gate. Không nên chỉ kiểm tra timestamp hiện có.

### D. Hộp thoại Kết thúc thủ công

Dialog hiện vẫn là logic cũ và chưa đủ ngữ cảnh. Nên có patch riêng hiển thị:

- Chuyến bay, tàu bay, loại thiết bị và trạng thái kết nối.
- Gross hiện tại, meter đầu/cuối và tuổi dữ liệu.
- Hậu quả “mẻ sẽ được chốt”.
- Lý do kết thúc thủ công nếu quy trình yêu cầu.

Không gộp phần này vào patch layout để tránh thay đổi luồng chốt mẻ chưa được test trên thiết bị.

### E. Race trạng thái UI

Kiểm tra kỹ thứ tự callback giữa `setConnectionCheckmark`, `setRefuelStatus`, watchdog và TCS reconnect:

- Restart phải luôn khóa khi `started == true`, kể cả callback disconnect đặt luồng nhận TCS về `NONE`.
- `ENDED` phải mở lại restart dù `started` được reset ngay sau lệnh cập nhật UI.
- ACK/save conflict không được làm panel báo hoàn tất khi local commit chưa thành công.

### F. Portrait

Manifest hiện khóa Activity ở landscape, nên layout ngang là runtime chính. Layout dọc vẫn giữ đủ contract và build được, nhưng ba nút thiết bị nằm trong vùng cuộn. Nếu sau này bỏ khóa landscape, cần pin header/kết nối/live/action trước khi cho dùng portrait ngoài hiện trường.

### G. Kiểm thử thiết bị thật trước canary

Đã đo đúng tablet Samsung SM-X306B: `960 × 600dp`, app viewport `960 × 576dp`, `320dpi`, font scale `1.0`. Robolectric xác nhận không scroll/không clip ở viewport này. Vẫn cần kiểm tra trực quan và thao tác thật:

1. Nắng trực tiếp và ban đêm.
2. Đeo găng, rung xe, màn hình bẩn/ướt.
3. LCR và TCS riêng.
4. Mất số nhưng socket còn kết nối: card phải chuyển STALE, số cũ không được trông như live.
5. Mất kết nối giữa mẻ: Restart/Back vẫn khóa, Kết thúc thủ công còn dùng được.
6. Reconnect và dữ liệu chạy lại: tuổi dữ liệu về mới, gross/totalizer tiếp tục đúng.
7. Hồi lưu `398 → 399 → 400 → 399 → End`: UI và Room vẫn chốt 399.

## 8. Tiêu chí chấp nhận đề nghị

Chỉ nhận patch khi Claude xác nhận cả bốn điều sau:

1. ID/type/onClick tương thích với Activity.
2. Đơn vị meter/order/capacity đúng nguồn và đúng nghiệp vụ.
3. Restart không có đường nào chạy giữa mẻ.
4. Trạng thái stale/error không thể bị hiểu nhầm là dữ liệu live.

Sau đó mới cài bằng `adb install -r -t`; không uninstall và không dùng chuyến khai thác cho vòng UI đầu tiên.
