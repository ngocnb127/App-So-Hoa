package com.megatech.fms.helpers;

import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.List;

/**
 * Quy tắc "độ tươi" của dữ liệu mẻ do XE KHÁC thực hiện, dùng cho màn hình xem trước.
 *
 * <p>Bối cảnh nghiệp vụ: xe chốt/xuất hoá đơn là xe cuối cùng xuất hàng, nên chứng từ GỘP bắt
 * buộc phải đứng trên bản mới nhất của các xe khác. Trước bản vá này, danh sách {@code Others}
 * chỉ được nạp MỘT LẦN lúc mở màn hình rồi màn hình khoá đồng bộ, nên mẻ của xe khác đứng yên
 * cho tới khi người dùng tự bấm CẬP NHẬT.
 *
 * <p><b>Ràng buộc quan trọng nhất của lớp này: không được đẻ ra đường CHẶN MỚI.</b> Việc làm
 * mới dữ liệu là để chứng từ đúng hơn, không phải để thêm lý do không in được. Vì vậy:
 * <ul>
 *   <li>Refresh thất bại (mất sóng) ⇒ giữ nguyên trạng thái cũ, KHÔNG hạ cấp cờ "đã đủ dữ
 *       liệu"; màn hình chỉ hiện cảnh báo "tổng có thể chưa đủ" và vẫn in được.</li>
 *   <li>Refresh thành công nhưng kết quả XẤU HƠN trạng thái đang có ⇒ cũng không nhận, vì
 *       nhận vào là biến một màn hình đang in được thành không in được.</li>
 *   <li>Mẻ của xe khác vừa kéo về mà chưa đủ điều kiện (còn PROCESSING, thiếu giờ, thiếu sản
 *       lượng, thiếu tỉ trọng / nhiệt độ đo tay / số QC) ⇒ LOẠI khỏi phiếu gộp kèm cảnh báo, KHÔNG chặn nút. Máy này không sửa được mẻ
 *       của xe khác nên chặn ở đó là bắt người dùng chờ vô hạn.</li>
 * </ul>
 *
 * <p>Thuần Java, đồng hồ tiêm qua tham số {@code nowMs} để test tất định.
 */
public final class OthersFreshness {

    /** Quá mốc này thì dữ liệu xe khác coi như cũ và nên kéo lại trước khi dựng chứng từ. */
    public static final long FRESH_WINDOW_MS = 3L * 60 * 1000;

    private OthersFreshness() {
    }

    /**
     * Có cần kéo lại dữ liệu xe khác trước khi dựng chứng từ gộp không.
     *
     * @param refreshedAtMs mốc lần làm mới gần nhất theo cùng nguồn thời gian với {@code nowMs}
     *                      ({@code SystemClock.elapsedRealtime()} ở phía gọi); {@code <= 0}
     *                      nghĩa là chưa làm mới lần nào trong màn hình này.
     */
    public static boolean needsRefresh(long refreshedAtMs, long nowMs) {
        if (refreshedAtMs <= 0) return true;
        long age = nowMs - refreshedAtMs;
        // Đồng hồ nhảy lùi: coi như không biết gì, kéo lại còn hơn dùng mốc vô nghĩa.
        if (age < 0) return true;
        return age > FRESH_WINDOW_MS;
    }

    /**
     * Có được phép thay trạng thái Others hiện có bằng kết quả của lượt làm mới vừa chạy không.
     *
     * <p>Chỉ nhận khi kết quả mới KHÔNG xấu đi. Đây chính là điều kiện giữ lời hứa "không tạo
     * đường chặn mới": màn hình đang in được thì sau khi bấm in vẫn phải in được.
     */
    public static boolean adoptRefreshResult(boolean previousHasFailure,
                                             boolean refreshSucceeded,
                                             boolean newHasFailure) {
        if (!refreshSucceeded) return false;
        if (!previousHasFailure && newHasFailure) return false;
        return true;
    }

    /**
     * Mẻ đã đủ điều kiện để đưa vào chứng từ chưa — cùng bộ điều kiện với phần kiểm tra sẵn
     * có của màn hình xem trước (đủ giờ, đã DONE, có sản lượng).
     */
    public static boolean eligibleForDocument(RefuelItemData item) {
        return item != null
                && item.getStartTime() != null
                && item.getEndTime() != null
                && item.getStatus() == REFUEL_ITEM_STATUS.DONE
                && item.getRealAmount() > 0;
    }

    /**
     * Mẻ có đủ các trường BẮT BUỘC KHI IN chưa: tỉ trọng, nhiệt độ đo tay, số hiệu QC.
     *
     * <p>Đây đúng bộ điều kiện mà {@code RefuelPreviewActivity.validate()} đang CHẶN CỨNG.
     * Với mẻ của chính xe này thì chặn là đúng — người dùng gõ được ngay tại chỗ. Với mẻ của
     * XE KHÁC thì máy này chỉ đọc, không sửa được, nên chặn ở đó là bắt người dùng chờ vô hạn
     * một thứ ngoài tầm tay: "hôm nay in được, mai không in được".
     */
    public static boolean hasRequiredPrintFields(RefuelItemData item) {
        return item != null
                && item.getManualTemperature() > 0
                && item.getDensity() > 0
                && item.getQualityNo() != null && !item.getQualityNo().isEmpty();
    }

    /**
     * Mẻ của XE KHÁC có đưa vào được chứng từ GỘP không: vừa đủ dữ liệu đo
     * ({@link #eligibleForDocument}), vừa đủ trường bắt buộc khi in
     * ({@link #hasRequiredPrintFields}).
     */
    public static boolean printableForDocument(RefuelItemData item) {
        return eligibleForDocument(item) && hasRequiredPrintFields(item);
    }

    /**
     * Kể tên những gì còn thiếu để mẻ này lên được chứng từ, bằng tiếng Việt cho người dùng đọc.
     *
     * <p>Người dùng phải biết THIẾU GÌ mới quyết được nên gọi xe kia bổ sung hay chấp nhận
     * xuất thiếu. Danh sách này bám đúng {@link #printableForDocument}, nên không bao giờ nói
     * "thiếu" một thứ mà máy lại vẫn cho qua, và ngược lại.
     *
     * @return chuỗi rỗng nếu mẻ không thiếu gì (khi đó nó đã không bị loại).
     */
    public static String missingForDocument(RefuelItemData item) {
        List<String> missing = new ArrayList<>();
        if (item == null) return "dữ liệu mẻ";
        if (item.getStatus() != REFUEL_ITEM_STATUS.DONE) missing.add("mẻ chưa kết thúc");
        if (item.getStartTime() == null) missing.add("giờ bắt đầu");
        if (item.getEndTime() == null) missing.add("giờ kết thúc");
        if (item.getRealAmount() <= 0) missing.add("sản lượng");
        if (item.getDensity() <= 0) missing.add("tỉ trọng");
        if (item.getManualTemperature() <= 0) missing.add("nhiệt độ");
        if (item.getQualityNo() == null || item.getQualityNo().isEmpty())
            missing.add("số hoá nghiệm");
        return String.join(", ", missing);
    }

    /** Kết quả sàng lọc danh sách sắp in. */
    public static final class Partition {
        /** Các mẻ được giữ lại để dựng chứng từ. */
        public final List<RefuelItemData> kept;
        /** Các mẻ của XE KHÁC bị loại vì chưa đủ điều kiện (chỉ cảnh báo, không chặn). */
        public final List<RefuelItemData> excluded;

        Partition(List<RefuelItemData> kept, List<RefuelItemData> excluded) {
            this.kept = kept;
            this.excluded = excluded;
        }

        public boolean hasExclusion() {
            return !excluded.isEmpty();
        }

        /**
         * Mô tả từng mẻ bị loại: xe nào, sản lượng bao nhiêu, và THIẾU GÌ.
         *
         * <p>Chỉ nói "đã loại mẻ của xe X" là không đủ để người dùng quyết: họ cần biết mẻ đó
         * đáng bao nhiêu và thiếu đúng trường nào thì mới biết nên gọi xe kia bổ sung hay
         * chấp nhận xuất thiếu.
         *
         * <p>Nói bằng LÍT, không phải Kg. {@code getWeight()} là số dẫn xuất
         * {@code density × volume}, nên mẻ thiếu tỉ trọng — đúng ca hay bị loại nhất — luôn
         * ra <b>0 Kg</b>. Báo "0 Kg" cho một mẻ có thật là nói sai với người đang phải quyết.
         * Kg chỉ được nói thêm khi tính ra được.
         */
        public List<String> describeExcluded() {
            List<String> lines = new ArrayList<>();
            for (RefuelItemData item : excluded) {
                if (item == null) continue;
                String truckNo = item.getTruckNo() == null || item.getTruckNo().trim().isEmpty()
                        ? "(chưa rõ xe)" : item.getTruckNo().trim();
                StringBuilder line = new StringBuilder(String.format(java.util.Locale.US,
                        "Xe %s — %.0f lít", truckNo, item.getVolume()));
                if (item.getDensity() > 0)
                    line.append(String.format(java.util.Locale.US, " (%.0f Kg)",
                            item.getWeight()));
                line.append(" — thiếu ").append(missingForDocument(item));
                lines.add(line.toString());
            }
            return lines;
        }

        /**
         * Tổng số LÍT sẽ không lên chứng từ nếu người dùng đồng ý loại.
         *
         * <p>Lít chứ không phải Kg — xem lý do ở {@link #describeExcluded()}.
         */
        public double excludedVolume() {
            double sum = 0;
            for (RefuelItemData item : excluded) if (item != null) sum += item.getVolume();
            return sum;
        }

        /** Danh sách số xe của các mẻ bị loại, để hiện cho người dùng. */
        public List<String> excludedTruckNumbers() {
            List<String> result = new ArrayList<>();
            for (RefuelItemData item : excluded) {
                String truckNo = item == null || item.getTruckNo() == null
                        || item.getTruckNo().trim().isEmpty()
                        ? "(chưa rõ xe)" : item.getTruckNo().trim();
                if (!result.contains(truckNo)) result.add(truckNo);
            }
            return result;
        }
    }

    /**
     * Loại các mẻ của XE KHÁC chưa đủ điều kiện ra khỏi chứng từ GỘP.
     *
     * <p>Mẻ của CHÍNH xe này không bao giờ bị loại ở đây: người dùng sửa được nó, và các
     * đường kiểm tra sẵn có phải tiếp tục báo lỗi để họ sửa. Cũng không loại khi danh sách chỉ
     * có một mẻ (chứng từ đơn) hay khi loại xong sẽ không còn gì để in — lúc đó để nguyên cho
     * các đường kiểm tra cũ xử lý, hành vi y hệt trước bản vá.
     *
     * @param foreign cờ "mẻ này KHÔNG thuộc xe hiện tại", tính sẵn ở phía gọi (màn hình biết
     *                số xe/id xe đang đăng nhập) để lớp này không phải chạm Android.
     */
    public static Partition excludeIneligibleForeignItems(List<RefuelItemData> items,
                                                          List<Boolean> foreign) {
        List<RefuelItemData> kept = new ArrayList<>();
        List<RefuelItemData> excluded = new ArrayList<>();
        if (items == null || items.isEmpty())
            return new Partition(kept, excluded);
        if (foreign == null || foreign.size() != items.size() || items.size() <= 1) {
            kept.addAll(items);
            return new Partition(kept, excluded);
        }

        for (int i = 0; i < items.size(); i++) {
            RefuelItemData item = items.get(i);
            boolean isForeign = Boolean.TRUE.equals(foreign.get(i));
            if (isForeign && !printableForDocument(item)) excluded.add(item);
            else kept.add(item);
        }

        // Loại hết thì không còn chứng từ nào để dựng: trả lại nguyên danh sách và để đường
        // kiểm tra cũ báo lỗi, thay vì im lặng đưa ra một chứng từ rỗng.
        if (kept.isEmpty()) {
            kept.addAll(items);
            excluded.clear();
        }
        return new Partition(kept, excluded);
    }

    /**
     * Kết quả của cổng "dữ liệu mẻ xe khác chưa toàn vẹn" trước khi dựng chứng từ.
     *
     * <p><b>Cố ý KHÔNG có giá trị BLOCK.</b> Mất mạng không bao giờ được chặn xuất phiếu/hoá
     * đơn: người dùng ngoài hiện trường sẽ tìm mọi cách để đồng bộ được dữ liệu, chặn họ lại
     * không làm dữ liệu đúng hơn mà chỉ làm tắc việc. Thêm một giá trị BLOCK vào đây là làm
     * hỏng đúng quyết định nghiệp vụ này.
     */
    public enum IncompleteOthersGate {
        /** Dữ liệu xe khác đủ tin cậy — đi thẳng, không hỏi gì. */
        CONTINUE,
        /** Chưa chắc đủ dữ liệu — CẢNH BÁO hai nút rồi vẫn cho đi tiếp nếu người dùng chọn. */
        WARN
    }

    /**
     * Quyết định cổng trước khi dựng chứng từ.
     *
     * @param othersKnown      đã có kết quả làm mới dữ liệu xe khác chưa ({@code false} khi
     *                         chưa lần nào thành công — mất mạng từ đầu)
     * @param othersHasFailure kết quả đó có mẻ chưa lấy được không
     */
    public static IncompleteOthersGate gateForIncompleteOthers(boolean othersKnown,
                                                               boolean othersHasFailure) {
        if (!othersKnown || othersHasFailure) return IncompleteOthersGate.WARN;
        return IncompleteOthersGate.CONTINUE;
    }

    /** Số mẻ và tổng lít/kg SẼ LÊN chứng từ, để hộp cảnh báo nói bằng con số. */
    public static final class DocumentTotals {
        /** Số mẻ thực sự sẽ dựng thành chứng từ. */
        public final int count;
        /** Tổng số lít. */
        public final double litres;
        /** Tổng số kg. */
        public final double kilos;

        DocumentTotals(int count, double litres, double kilos) {
            this.count = count;
            this.litres = litres;
            this.kilos = kilos;
        }
    }

    /**
     * Cộng số mẻ và tổng lít/kg của danh sách sắp in.
     *
     * <p>Dùng cho hộp cảnh báo "chưa nhận đủ dữ liệu xe khác": nói chung chung thì người dùng
     * không có cách nào đối chiếu, còn in ra "3 mẻ, 12.000 lít / 9.600 kg" thì họ biết ngay
     * con số này có khớp với thực tế chuyến hay không. Mẻ null bị bỏ qua, không làm hỏng tổng.
     */
    public static DocumentTotals documentTotals(List<RefuelItemData> items) {
        int count = 0;
        double litres = 0;
        double kilos = 0;
        if (items != null) {
            for (RefuelItemData item : items) {
                if (item == null) continue;
                count++;
                litres += item.getVolume();
                kilos += item.getWeight();
            }
        }
        return new DocumentTotals(count, litres, kilos);
    }

    /**
     * Các UID vừa xuất hiện sau lượt làm mới. Mẻ mới KHÔNG được tự động tích vào chứng từ —
     * người dùng phải được hỏi, vì tích thêm một mẻ là đổi số liệu sẽ in ra giấy.
     */
    public static List<String> newlyAppearedUniqueIds(List<String> beforeUids,
                                                      List<String> afterUids) {
        List<String> result = new ArrayList<>();
        if (afterUids == null) return result;
        for (String uid : afterUids) {
            if (uid == null || uid.trim().isEmpty()) continue;
            if (beforeUids != null && beforeUids.contains(uid)) continue;
            if (result.contains(uid)) continue;
            result.add(uid);
        }
        return result;
    }
}
