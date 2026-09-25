package com.megatech.fms.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quy tắc LƯU TẠM CHỮ KÝ và MỜI DÙNG LẠI theo CHUYẾN.
 *
 * <p>Bối cảnh: ký xong, phát hiện phải sửa một thông tin nào đó của chuyến, quay ra sửa rồi
 * vào lại thì chữ ký mất và phải ký lại từ đầu. Mục tiêu duy nhất của lớp này là GIẢM SỐ LẦN
 * PHẢI KÝ LẠI.
 *
 * <p><b>Đây là bản HỎI NGƯỜI DÙNG, không phải bản tự động khôi phục.</b> Lớp này chỉ trả lời
 * "có gì để mời dùng lại không" và "câu hỏi nên nói gì"; quyết định vẫn thuộc về người dùng.
 * Vì vậy:
 * <ul>
 *   <li>Phần so sánh khối lượng CHỈ để HIỂN THỊ trong câu hỏi. Tuyệt đối không dùng nó để
 *       chặn, để tự bỏ chữ ký hay để không mời dùng lại.</li>
 *   <li>Thiếu tệp meta (không biết khối lượng lúc ký) ⇒ VẪN mời dùng lại, chỉ là câu hỏi
 *       không có dòng so sánh.</li>
 *   <li>Đổi người đăng nhập ⇒ loại chữ ký NHÂN VIÊN (người bán), vẫn mời chữ ký KHÁCH.</li>
 * </ul>
 *
 * <p>Không có kho chỉ mục: khoá nằm ngay trong TÊN TỆP, đặt cạnh ảnh trong thư mục PICTURES
 * của ứng dụng. Không SharedPreferences, không Room, không migration. Phần còn lại để cơ chế
 * {@code DataRetention} sẵn có tự tiêu.
 *
 * <p>Thuần Java, không phụ thuộc Android runtime để test được tất định.
 */
public final class SignatureCache {

    /** Tiền tố nhận dạng tệp chữ ký lưu tạm. */
    public static final String PREFIX = "SIGTMP_";

    public static final String EXT_IMAGE = ".jpg";
    public static final String EXT_META = ".meta";

    /** Đơn vị khối lượng dùng trong câu hỏi — Kg, KHÔNG phải lít. */
    public static final String WEIGHT_UNIT = "Kg";

    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_SELLER = "seller";

    private SignatureCache() {
    }

    // ------------------------------------------------------------------ khoá

    /**
     * Chuẩn hoá một mẩu chữ để dùng an toàn làm TÊN TỆP.
     *
     * <p>Chỉ giữ chữ cái/chữ số, mọi ký tự khác (kể cả dấu gạch dưới) thành '-'. Bỏ gạch dưới
     * là cố ý: '_' là dấu tách các phần của tên tệp, khoá không được chứa nó thì việc phân
     * tích ngược mới không nhập nhằng.
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
                sb.append(c);
            else
                sb.append('-');
        }
        // gộp gạch nối thừa và cắt hai đầu cho tên tệp gọn
        String s = sb.toString().replaceAll("-{2,}", "-");
        while (s.startsWith("-")) s = s.substring(1);
        while (s.endsWith("-")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * Khoá CHUYẾN. Ưu tiên mã chuyến trong hệ thống ({@code flightId}) vì đó là số ổn định;
     * không có thì dùng số hiệu chuyến đã chuẩn hoá.
     *
     * @return {@code null} khi không xác định được chuyến — lúc đó KHÔNG lưu tạm gì cả, thà
     *         không có tính năng còn hơn dán chữ ký của chuyến này sang chuyến khác.
     */
    public static String flightKey(int flightId, String flightCode) {
        if (flightId > 0) return "F" + flightId;
        String code = sanitize(flightCode == null ? "" : flightCode.toUpperCase(Locale.US));
        if (code.isEmpty()) return null;
        return "C" + code;
    }

    /** Tên tệp chữ ký KHÁCH của chuyến. */
    public static String buyerFileName(String flightKey) {
        return PREFIX + flightKey + "_" + ROLE_BUYER + EXT_IMAGE;
    }

    /**
     * Tên tệp chữ ký NHÂN VIÊN của chuyến, có gắn người đăng nhập.
     *
     * <p>Gắn {@code userId} chỉ ở phía người bán là đủ để thực hiện luật "đổi người đăng nhập
     * thì không mời dùng lại chữ ký nhân viên" mà không phải lưu thêm bất cứ thứ gì.
     */
    public static String sellerFileName(String flightKey, int userId) {
        return PREFIX + flightKey + "_" + ROLE_SELLER + "_" + userId + EXT_IMAGE;
    }

    public static String fileName(String flightKey, boolean buyer, int userId) {
        return buyer ? buyerFileName(flightKey) : sellerFileName(flightKey, userId);
    }

    /**
     * Tên tệp chữ ký THẬT của một phiếu đã xuất — không còn mang tiền tố lưu tạm.
     *
     * <p>Lý do phải có bước đổi tên này: hoá đơn được đẩy lên máy chủ theo hàng đợi, và
     * {@code ReceiptAPI} đọc tệp ảnh tại LÚC ĐẨY chứ không phải lúc xuất. Nếu cứ để nguyên tên
     * lưu tạm rồi xoá theo luật "xuất xong thì xoá", máy đang mất sóng sẽ đẩy lên một hoá đơn
     * KHÔNG CÒN CHỮ KÝ. Đổi tên ra khỏi vùng lưu tạm giữ đúng cả hai luật: chữ ký tạm biến mất,
     * chữ ký thật của phiếu ở lại cho tới khi {@code DataRetention} dọn.
     */
    public static String promotedFileName(String receiptNumber, boolean buyer) {
        String number = sanitize(receiptNumber);
        if (number.isEmpty()) number = "NA";
        return "SIG_" + number + "_" + (buyer ? ROLE_BUYER : ROLE_SELLER) + EXT_IMAGE;
    }

    /** Tên tệp phụ giữ khối lượng lúc ký, dùng chung cho cả chuyến. */
    public static String metaFileName(String flightKey) {
        return PREFIX + flightKey + EXT_META;
    }

    // -------------------------------------------------------------- phân tích

    /** Một tệp chữ ký lưu tạm đã phân tích được từ tên. */
    public static final class Entry {
        public final String fileName;
        public final String flightKey;
        public final boolean buyer;
        /** Chỉ có nghĩa với chữ ký người bán; chữ ký khách luôn là -1. */
        public final int userId;

        Entry(String fileName, String flightKey, boolean buyer, int userId) {
            this.fileName = fileName;
            this.flightKey = flightKey;
            this.buyer = buyer;
            this.userId = userId;
        }
    }

    /** Phân tích tên tệp; trả {@code null} nếu không phải tệp chữ ký lưu tạm. */
    public static Entry parse(String fileName) {
        if (fileName == null) return null;
        if (!fileName.startsWith(PREFIX) || !fileName.endsWith(EXT_IMAGE)) return null;
        String body = fileName.substring(PREFIX.length(), fileName.length() - EXT_IMAGE.length());

        String buyerSuffix = "_" + ROLE_BUYER;
        if (body.endsWith(buyerSuffix)) {
            String key = body.substring(0, body.length() - buyerSuffix.length());
            if (key.isEmpty()) return null;
            return new Entry(fileName, key, true, -1);
        }

        int lastUnderscore = body.lastIndexOf('_');
        if (lastUnderscore <= 0) return null;
        String idPart = body.substring(lastUnderscore + 1);
        String rest = body.substring(0, lastUnderscore);
        String sellerSuffix = "_" + ROLE_SELLER;
        if (!rest.endsWith(sellerSuffix)) return null;
        String key = rest.substring(0, rest.length() - sellerSuffix.length());
        if (key.isEmpty()) return null;
        int userId;
        try {
            userId = Integer.parseInt(idPart);
        } catch (NumberFormatException ex) {
            return null;
        }
        return new Entry(fileName, key, false, userId);
    }

    /** Mọi tệp (ảnh và meta) thuộc về chuyến này — dùng để xoá sau khi xuất hoá đơn. */
    public static List<String> filesOfFlight(List<String> fileNames, String flightKey) {
        List<String> out = new ArrayList<>();
        if (fileNames == null || flightKey == null) return out;
        String meta = metaFileName(flightKey);
        for (String name : fileNames) {
            if (name == null) continue;
            if (name.equals(meta)) {
                out.add(name);
                continue;
            }
            Entry e = parse(name);
            if (e != null && flightKey.equals(e.flightKey)) out.add(name);
        }
        return out;
    }

    // ------------------------------------------------------------- lời mời

    /** Kết quả tra cứu: có gì để mời dùng lại cho chuyến này. */
    public static final class Offer {
        /** Tên tệp chữ ký khách được mời, {@code null} nếu không có. */
        public final String buyerFileName;
        /** Tên tệp chữ ký nhân viên được mời, {@code null} nếu không có. */
        public final String sellerFileName;
        /**
         * Có tệp chữ ký nhân viên của chuyến nhưng do NGƯỜI ĐĂNG NHẬP KHÁC ký nên không mời.
         * Câu hỏi phải nói rõ chỉ còn chữ ký khách.
         */
        public final boolean sellerDroppedByUserChange;

        Offer(String buyerFileName, String sellerFileName, boolean sellerDroppedByUserChange) {
            this.buyerFileName = buyerFileName;
            this.sellerFileName = sellerFileName;
            this.sellerDroppedByUserChange = sellerDroppedByUserChange;
        }

        public boolean hasAny() {
            return buyerFileName != null || sellerFileName != null;
        }

        public boolean hasBuyer() {
            return buyerFileName != null;
        }

        public boolean hasSeller() {
            return sellerFileName != null;
        }
    }

    public static final Offer NOTHING = new Offer(null, null, false);

    /**
     * Xác định chữ ký nào của chuyến được mời dùng lại.
     *
     * @param fileNames     tên các tệp đang có trong thư mục ảnh
     * @param flightKey     khoá chuyến đang mở, {@code null} thì không mời gì
     * @param currentUserId người đang đăng nhập
     * @param reprint       màn IN LẠI — nghiệp vụ in lại giữ nguyên như cũ, BỎ QUA hoàn toàn
     */
    public static Offer offer(List<String> fileNames, String flightKey, int currentUserId,
                              boolean reprint) {
        if (reprint || flightKey == null || fileNames == null) return NOTHING;

        String buyer = null;
        String seller = null;
        boolean sellerDropped = false;

        for (String name : fileNames) {
            Entry e = parse(name);
            if (e == null || !flightKey.equals(e.flightKey)) continue;
            if (e.buyer) {
                buyer = e.fileName;
            } else if (e.userId == currentUserId) {
                seller = e.fileName;
            } else {
                // Người đăng nhập đã khác: chữ ký nhân viên là chữ ký của NGƯỜI KHÁC, không
                // được mời dùng lại. Tệp KHÔNG bị xoá — người kia đăng nhập lại vẫn dùng được.
                sellerDropped = true;
            }
        }

        if (buyer == null && seller == null && !sellerDropped) return NOTHING;
        return new Offer(buyer, seller, sellerDropped && seller == null);
    }

    // ----------------------------------------------------------- nội dung hỏi

    /** Đọc khối lượng lúc ký từ nội dung tệp meta; {@code null} khi thiếu hoặc hỏng. */
    public static Double parseMetaWeight(String content) {
        if (content == null) return null;
        String s = content.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Nội dung tệp meta cho khối lượng hiện tại. */
    public static String metaContent(double weightKg) {
        return String.format(Locale.US, "%.3f", weightKg);
    }

    /**
     * Dòng SẢN LƯỢNG của phiếu để HIỂN THỊ trong câu hỏi.
     *
     * <p>LUÔN nói ra số Kg khi biết được, kể cả khi không đổi. Chủ dự án chốt 06-09-2026: câu
     * hỏi phải cho người dùng đối chiếu ngay con số trên phiếu, chứ không chỉ báo "có thay
     * đổi". Chỉ nói "đã đổi" thì lúc không đổi người dùng chẳng có gì để đối chiếu, và phải
     * thoát ra xem lại phiếu — đúng thứ tính năng này sinh ra để tránh.
     *
     * <p>So theo Kg đã làm tròn về số nguyên, đúng như con số in ra phiếu — chênh lệch dưới
     * 1 Kg là nhiễu của phép tính, không phải "người dùng đã sửa số liệu".
     *
     * @return {@code null} CHỈ khi thiếu tệp meta nên không biết khối lượng lúc ký. Trả về
     *         {@code null} KHÔNG có nghĩa là không mời dùng lại.
     */
    public static String weightLine(Double weightAtSign, double weightNow, NumberFormat nf) {
        if (weightAtSign == null) return null;
        long before = Math.round(weightAtSign);
        long after = Math.round(weightNow);
        NumberFormat format = nf != null ? nf : NumberFormat.getInstance(Locale.US);
        String now = "Sản lượng trên phiếu: " + format.format(after) + " " + WEIGHT_UNIT;
        if (before == after) return now + " — không đổi từ lúc ký";
        return now + " — ĐÃ ĐỔI, lúc ký là " + format.format(before) + " " + WEIGHT_UNIT;
    }

    /** Mô tả "có những chữ ký nào" cho câu hỏi. */
    public static String signerSummary(Offer offer) {
        if (offer == null || !offer.hasAny()) return "";
        if (offer.hasBuyer() && offer.hasSeller()) return "khách hàng và nhân viên";
        if (offer.hasBuyer()) {
            return offer.sellerDroppedByUserChange
                    // Nói rõ vì sao chỉ còn một chữ ký, tránh người dùng tưởng máy làm mất.
                    ? "chỉ chữ ký khách hàng (đã đổi người đăng nhập nên bỏ chữ ký nhân viên)"
                    : "chỉ chữ ký khách hàng";
        }
        return "chỉ chữ ký nhân viên";
    }

    /**
     * Nội dung câu hỏi "Dùng chữ ký đã lưu?".
     *
     * <p>Phải nói đủ ba thứ để người dùng quyết được mà không cần thoát ra tra lại (chủ dự án
     * chốt 06-09-2026): chuyến này ĐÃ CÓ chữ ký lưu tạm, ký vào THỜI ĐIỂM nào, và SỐ KG trên
     * phiếu. Trước đây câu hỏi chỉ có giờ và tên bên ký, nên người dùng không biết chữ ký đó
     * gắn với bộ số nào — mà đây đúng là lúc hay có nhiều mẻ và nhiều lần sửa.
     *
     * @param signedAt   thời điểm ký (lấy từ {@code lastModified()} của tệp), có thể rỗng
     * @param weightLine dòng sản lượng, hoặc {@code null} khi thiếu meta
     */
    public static String buildMessage(Offer offer, String signedAt, String weightLine) {
        StringBuilder sb = new StringBuilder("Chuyến này đã có chữ ký lưu tạm");
        if (signedAt != null && !signedAt.isEmpty())
            sb.append(" lúc ").append(signedAt);
        sb.append(".\nĐã ký: ").append(signerSummary(offer)).append('.');
        if (weightLine != null && !weightLine.isEmpty())
            sb.append('\n').append(weightLine);
        return sb.toString();
    }
}
