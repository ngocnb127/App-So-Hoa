package com.megatech.fms.helpers;

import com.megatech.fms.helpers.ZplLayoutBuilder.Align;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.Additive;
import com.megatech.fms.model.BM7501Model.AdditivePresence;
import com.megatech.fms.model.BM7501Model.DefuelMethod;
import com.megatech.fms.model.BM7501Model.DefuelReason;
import com.megatech.fms.model.BM7501Model.HandlingOption;
import com.megatech.fms.model.BM7501Model.MicrobialKit;
import com.megatech.fms.model.BM7501Model.MicrobialResult;
import com.megatech.fms.model.BM7501Model.QcCheck;
import com.megatech.fms.model.ReceiptModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dựng bản in nhiệt (ZPL, máy Zebra) cho BM 75.01/NLHK.
 *
 * <p>Bố cục bám bản PDF của biểu mẫu:
 * <ul>
 *   <li>Mỗi mục lớn mở đầu bằng <b>thanh chữ trắng nền đen</b> — máy in nhiệt không có chữ đậm
 *       thật, phóng to cỡ chữ vẫn lẫn vào phần thân nên phải tách bằng nền.</li>
 *   <li>Phần khai báo in thành <b>hai cột nhãn | giá trị</b> thẳng hàng như ô điền của bản giấy,
 *       thay vì trộn nhãn và giá trị vào một dòng chữ chạy.</li>
 *   <li>Lựa chọn in đủ dạng ô đánh dấu {@code [X]}/{@code [ ]} như bản giấy, thụt vào một mức.</li>
 *   <li>Chỉ dùng <b>ba cỡ chữ</b>: tiêu đề, thân phiếu, dòng tiếng Anh. Trộn nhiều cỡ làm bản in
 *       trông lổn nhổn.</li>
 * </ul>
 *
 * <p>Không có dấu MẪU/SPECIMEN và không đánh số bản sao: phiếu in ra để hai bên ký tay, sửa và
 * in lại bao nhiêu lần cũng được (chốt với chủ dự án ngày 2026-09-23).
 *
 * <p>Lớp thuần Java (không phụ thuộc Android) để chạy golden test trên JVM. Thông tin đơn vị
 * truyền vào qua {@link Options} thay vì đọc từ setting, cũng vì lý do đó.
 */
public final class BM7501Printer {

    private BM7501Printer() {
    }

    /** Tên ảnh chữ ký đã nạp sẵn vào máy in Zebra. */
    public static final String GRF_CUSTOMER_FINAL = "E:BUYER.GRF";
    public static final String GRF_SKYPEC = "E:SELLER.GRF";

    private static final int SIGNATURE_BLOCK_HEIGHT = 190;

    /**
     * Cỡ chữ lấy theo phiếu hoàn trả đang in ngoài hiện trường (tiêu đề 36-40, thân 28-30):
     * chữ nhỏ hơn thì đọc không ra trên giấy nhiệt.
     */
    private static final int F_TITLE = 36;
    private static final int F_SECTION = 30;
    private static final int F_BODY = 28;
    private static final int F_COMPANY = 25;
    private static final int F_META = 22;
    private static final int F_EN = 20;

    /** Lề trái của các ô đánh dấu. */
    private static final int OPTION_INDENT = 16;

    private static final String DATE_TIME = "dd-MM-yyyy HH:mm";
    private static final String DAY = "dd-MM-yyyy";
    private static final String CLOCK = "HH:mm";
    private static final Locale VN = new Locale("vi", "VN");

    public static class Options {
        private String companyName = "CÔNG TY TNHH MTV NHIÊN LIỆU HÀNG KHÔNG VIỆT NAM (SKYPEC)";
        private String branchName = "";
        private boolean zq520;

        public Options companyName(String v) { this.companyName = v; return this; }
        public Options branchName(String v) { this.branchName = v; return this; }
        public Options zq520(boolean v) { this.zq520 = v; return this; }

        public String getCompanyName() { return companyName; }
        public String getBranchName() { return branchName; }
        public boolean isZq520() { return zq520; }
    }

    public static String createZpl(BM7501Model m, Options opt) {
        if (m == null) throw new IllegalArgumentException("model không được null");
        if (opt == null) opt = new Options();

        ZplLayoutBuilder b = new ZplLayoutBuilder(ZplLayoutBuilder.DEFAULT_PRINT_WIDTH, opt.isZq520());
        b.addSpace(80);

        header(b, m, opt);
        sectionA(b, m);
        sectionB(b, m);
        sectionC(b, m);
        signatures(b, m);
        footer(b);

        // Lệnh chất lượng in dùng chung với phiếu hoàn trả: ^JMA (đủ độ phân giải), tăng độ
        // đậm, in chậm cho nét sắc. Riêng chữ đậm chỉ đắp cho tiêu đề (xem setBold).
        return b.build().replaceFirst("\\^XA", "^XA" + ReceiptModel.printQualityHeader());
    }

    // ============================================================== đầu phiếu

    private static void header(ZplLayoutBuilder b, BM7501Model m, Options opt) {
        b.setFont(F_COMPANY);
        b.addWrappedText(opt.getCompanyName().toUpperCase(VN), Align.CENTER);
        if (notBlank(opt.getBranchName())) {
            b.addWrappedText("CHI NHÁNH " + opt.getBranchName().toUpperCase(VN), Align.CENTER);
        }
        b.addSpace(14);

        b.setFont(F_TITLE);
        b.setBold(true);
        b.addWrappedText("PHIẾU YÊU CẦU HÚT NHIÊN LIỆU TỪ TÀU BAY", Align.CENTER);
        b.setBold(false);
        b.setFont(F_SECTION);
        b.addWrappedText("(JET FUEL DEFUEL REQUEST FORM)", Align.CENTER);
        b.addSpace(10);

        // Chỉ số phiếu: ngày và giờ là ô KHÁCH HÀNG điền nên nằm trong mục A, đúng bản giấy.
        b.setFont(F_META);
        b.addLine("Số phiếu (No): " + nz(m.getLocalNumber()), Align.CENTER);
        b.addSpace(6);
        b.addDivider();
    }

    // ================================================================= MỤC A

    private static void sectionA(ZplLayoutBuilder b, BM7501Model m) {
        section(b, "A. KHÁCH HÀNG ĐIỀN", "(To be completed by customer)");

        row(b, "Ngày/Date", formatDay(m.getDate()));
        row(b, "Giờ, phút/Time", formatTime(m.getDate()));
        row(b, "Hãng HK/Airline", m.getAirlineName());
        row(b, "Đại diện/Rep.", m.getCustomerRepName());
        row(b, "Chức danh/Title", m.getCustomerTitle());
        row(b, "Điện thoại/Tel", m.getCustomerTel());
        row(b, "Fax", m.getCustomerFax());
        row(b, "Loại TB/Type", m.getAircraftType());
        row(b, "Số hiệu/Reg.", m.getAircraftReg());
        row(b, "Sân bay/Airport", m.getAirportName());

        heading(b, "Lý do hút/Reason for defuelling:");
        option(b, m.getReason() == DefuelReason.LOAD_ADJUSTMENT,
                "Điều chỉnh tải trọng/Load adjustment");
        option(b, m.getReason() == DefuelReason.MAINTENANCE,
                "Bảo dưỡng, sửa chữa tàu bay/Aircraft maintenance");
        option(b, m.getReason() == DefuelReason.OTHER,
                "Khác, ghi rõ/Other, note: "
                        + valueOrBlank(m.getReason() == DefuelReason.OTHER, m.getReasonOther()));

        item(b, "1. Trước khi hút, hãng hàng không đã xả tất cả các thùng nhiên liệu của tàu bay "
                        + "để lấy mẫu kiểm tra chất lượng",
                "Before defuelling, the Airline has taken aircraft tank drain samples from all "
                        + "aircraft tanks for quality check");
        yesNoRow(b, m.getTankDrainSampled(), "Có/Yes", "Không/No");

        item(b, "2. Trường hợp nhiên liệu đã được hãng hàng không thực hiện kiểm tra vi sinh, "
                        + "đề nghị cập nhật thông tin",
                "Where tested for microbial contamination, please update the information");
        heading(b, "Thiết bị sử dụng để kiểm tra/Test kit to be used:");
        kitOptions(b, m.getCustomerMicrobialKit(), true, m.getCustomerMicrobialKitOther());
        heading(b, "Kết quả kiểm tra/Test result:");
        resultOptions(b, m.getCustomerMicrobialResult());

        item(b, "3. Sự có mặt của các loại phụ gia FSII, chất diệt khuẩn biocide, Aquarius WMA "
                        + "trong nhiên liệu đang chứa trên tàu bay và trong 02 chuyến gần nhất mà "
                        + "tàu bay đã nạp",
                "The presence of FSII, Biocide, Aquarius WMA in the current fuel and 02 recent "
                        + "fuel refuellings.");
        boolean present = m.getAdditivePresence() == AdditivePresence.PRESENT;
        optionsRow(b, new String[]{"FSII", "Biocide", "Aquarius WMA"},
                new boolean[]{present && has(m, Additive.FSII),
                        present && has(m, Additive.BIOCIDE),
                        present && has(m, Additive.AQUARIUS_WMA)});
        optionsRow(b, new String[]{"Không sử dụng/No", "Không xác định/Undetermined"},
                new boolean[]{m.getAdditivePresence() == AdditivePresence.NONE,
                        m.getAdditivePresence() == AdditivePresence.UNDETERMINED});

        item(b, "4. Nhiên liệu đã được nạp tại 2 sân bay trước (ghi rõ tên sân bay & loại nhiên liệu)",
                "The fuel uploaded at the previous two (2) locations (specify both locations & "
                        + "fuel grade)");
        row(b, "Sân bay 1", m.getPrevLocation1());
        row(b, "Loại NL/Grade", m.getPrevGrade1());
        row(b, "Sân bay 2", m.getPrevLocation2());
        row(b, "Loại NL/Grade", m.getPrevGrade2());
        english(b, "Trường hợp không xác định được loại nhiên liệu đã nạp tại 2 sân bay trước đó, "
                + "cần ghi rõ \"Không xác định được / Undetermined\".");
    }

    // ================================================================= MỤC B

    private static void sectionB(ZplLayoutBuilder b, BM7501Model m) {
        section(b, "B. SKYPEC ĐIỀN", "(To be completed by SKYPEC)");

        item(b, "1. Kết quả kiểm tra chất lượng mẫu xả đáy thùng nhiên liệu tàu bay trước khi hút",
                "Quality check results of aircraft tank drain samples before defuelling");
        heading(b, "Kiểm tra ngoại quan/VAC:");
        qcOptions(b, m.getVac());
        heading(b, "Kiểm tra viên thử nước/CWD:");
        qcOptions(b, m.getCwd());
        row(b, "KLR (kg/m3)", num(m.getDensityKgM3(), 1));
        row(b, "Độ dẫn (pS/m)", num(m.getConductivityPsM(), 0));

        item(b, "2. Kiểm tra vi sinh mẫu nhiên liệu (chỉ thực hiện nếu kết quả kiểm tra trực quan "
                        + "không đạt hoặc nghi ngờ nhiên liệu nhiễm vi sinh vật hoặc theo yêu cầu "
                        + "của khách hàng)",
                "Microbiological test (only be used if the result of VAC + CWD is failed or the "
                        + "quality of fuel is suspected or as requested by customer)");
        heading(b, "Thiết bị sử dụng để kiểm tra/Test kit to be used:");
        kitOptions(b, m.getSkypecMicrobialKit(), false, null);
        heading(b, "Kết quả kiểm tra/Test result:");
        resultOptions(b, m.getSkypecMicrobialResult());
    }

    // ================================================================= MỤC C

    private static void sectionC(ZplLayoutBuilder b, BM7501Model m) {
        section(b, "C. CÁC BÊN XÁC NHẬN THÔNG TIN",
                "(Parties confirm the defueling information)");

        row(b, "Xe hút/Truck", m.getDefuellerTruckNo());
        row(b, "Bắt đầu/Start", formatDate(m.getStartTime()));
        row(b, "Kết thúc/Finish", formatDate(m.getEndTime()));

        heading(b, "Phương thức hút/Defuelling method:");
        option(b, m.getMethod() == DefuelMethod.AIRCRAFT_PUMP, "Bơm của tàu bay/Aircraft's pump");
        option(b, m.getMethod() == DefuelMethod.REFUELLER_PUMP, "Bơm của xe tra nạp/Refueller's pump");
        option(b, m.getMethod() == DefuelMethod.BOTH,
                "Bơm của tàu bay và bơm của xe tra nạp/Aircraft's pump & refueller's pump");
        english(b, "Ghi chú: Ưu tiên sử dụng bơm của tàu bay / Note: Priority to use the pump "
                + "of aircraft.");

        heading(b, "Phương thức ra tín hiệu trong công tác phối hợp/Signals in the coordination:");
        option(b, isThumbUp(m), "Giơ ngón cái của cánh tay phải hướng lên trên và ngang mặt là đã "
                + "sẵn sàng hút nhiên liệu/Raise the thumb of the right hand up and in front of "
                + "the face to start defuelling.");
        option(b, isCrossArms(m), "Giơ chéo hai tay ra phía trước ngang mặt để thông báo cho nhau "
                + "là có bất thường/Cross arms in front of the face to inform that there is an "
                + "abnormal event.");
        option(b, notBlank(m.getOtherSignal()),
                "Tín hiệu khác/Other signals: " + valueOrBlank(true, m.getOtherSignal()));

        // Khối số lượng là cái bảng duy nhất của biểu mẫu -> đóng khung đúng như bản giấy.
        itemDivider(b);
        heading(b, "Lượng nhiên liệu hút ra/Defuel quantity:");
        int tableTop = b.currentY();
        row(b, "Dự kiến (kg)", num(m.getExpectedKg(), 0) );
        row(b, "Thực tế (kg)", num(m.getActualKg(), 0) );
        row(b, "Nhiệt độ/Temp.", num(m.getActualTempC(), 1) + " °C");
        row(b, "KLR/Density", num(m.getActualDensityKgM3(), 1) + " kg/m3");
        row(b, "Quy đổi/Gal", num(m.getGallon(), 0));
        row(b, "Quy đổi/Litter", num(m.getLiter(), 0));
        b.addBoxAround(tableTop, b.currentY(), 8);
        b.addSpace(12);

        item(b, "1. Nhiên liệu được hút ra có thể tra nạp cho tàu bay của cùng Hãng Hàng không mà "
                        + "không cần phải kiểm tra bổ sung về chất lượng",
                "The defuelled fuel can be refuelled, without any additional quality test, to "
                        + "aircraft of the same Airline.");
        yesNoRow(b, m.getRefuellableWithoutTest(), "Đồng ý/Yes", "Không/No");

        item(b, "2. Nếu nhiên liệu không thể nạp lại ngay cho tàu bay của Hãng hàng không hoặc nếu "
                        + "nhiên liệu có vấn đề về chất lượng, yêu cầu xác nhận",
                "If fuel cannot be immediately returned to aircraft or if fuel has quality issue, "
                        + "please select from below:");
        HandlingOption h = m.getHandling();
        option(b, h == HandlingOption.STORAGE, "Yêu cầu lưu trữ/Storage");
        if (h == HandlingOption.STORAGE) {
            row(b, "   Từ/From", formatDate(m.getStorageFrom()));
            row(b, "   Đến/To", formatDate(m.getStorageTo()));
        }
        option(b, h == HandlingOption.SAME_AIRCRAFT,
                "Nạp lại cho chính tàu bay đã hút/Fuelling into the same aircraft");
        option(b, h == HandlingOption.OTHER_AIRCRAFT_SAME_AIRLINE,
                "Nạp lại cho tàu bay khác của hãng/Fuelling into other aircraft of the same airlines");
        option(b, h == HandlingOption.AUTHORIZE_SKYPEC,
                "Không có nhu cầu nạp lại lượng nhiên liệu đã hút và ủy quyền cho SKYPEC xử lý theo "
                        + "quy định/There is no need to refuel the defueled fuel and authorize "
                        + "SKYPEC to process it according to the regulation");
        option(b, h == HandlingOption.REFUEL_DESPITE_ISSUE,
                "Xác nhận nạp lại cho tàu bay của hãng dù nhiên liệu có vấn đề như sau/Confirmation "
                        + "of refuelling even though the fuel has quality issue as below: "
                        + valueOrBlank(h == HandlingOption.REFUEL_DESPITE_ISSUE, m.getHandlingNote()));
    }

    // ============================================================== chữ ký

    private static void signatures(ZplLayoutBuilder b, BM7501Model m) {
        b.addSpace(10);
        signature(b, "ĐẠI DIỆN SKYPEC (Ký, ghi rõ họ tên)",
                "Skypec Rep. Name (print) and Signature",
                m.getSkypecSignaturePath(), GRF_SKYPEC, m.getSkypecRepName());
        b.addSpace(16);
        // Họ tên đại diện hãng đã khai ở mục A nên khối ký chỉ còn chữ ký.
        signature(b, "ĐẠI DIỆN KHÁCH HÀNG (Ký, ghi rõ họ tên)",
                "Customer Rep. Name (print) and Signature",
                m.getCustomerFinalSignaturePath(), GRF_CUSTOMER_FINAL, null);
    }

    private static void signature(ZplLayoutBuilder b, String titleVi, String titleEn,
                                  String signaturePath, String grf, String name) {
        b.setFont(F_BODY);
        b.addWrappedText(titleVi, Align.CENTER);
        b.setFont(F_EN);
        b.addWrappedText(titleEn, Align.CENTER);
        if (notBlank(signaturePath)) {
            b.addSignatureImage(grf, SIGNATURE_BLOCK_HEIGHT);
        } else {
            // Chưa ký trong app: chừa chỗ ký tay trên giấy.
            b.addSpace(SIGNATURE_BLOCK_HEIGHT);
        }
        if (notBlank(name)) {
            b.setFont(F_BODY);
            b.addWrappedText(name, Align.CENTER);
        }
    }

    private static void footer(ZplLayoutBuilder b) {
        b.addDivider();
        english(b, "Ghi chú: Sau khi hút nhiên liệu, nhân viên giao lại phiếu này cho cán bộ đội "
                + "tra nạp. Khi hãng hàng không có yêu cầu tra nạp lại, cán bộ đội tra nạp giao "
                + "phiếu, giao nhiệm vụ cho nhân viên tra nạp cho nạp đủ số đã hút. Nếu tra nạp "
                + "lại vượt số lượng hút thì phải viết phiếu xuất số lượng vượt.");
        b.addSpace(10);
        b.addLine("BM 75.01/NLHK", Align.RIGHT);
        b.addLine("Ban hành/sửa đổi: 01/03", Align.RIGHT);
    }

    // ============================================================== khối in

    /**
     * Tiêu đề mục: kẻ ngang - chữ to canh giữa - kẻ ngang, giống khối "DETAIL" của phiếu
     * hoàn trả. Trước đây bôi đen cả thanh, nhìn nặng và tốn mực nung.
     */
    private static void section(ZplLayoutBuilder b, String titleVi, String titleEn) {
        b.addSpace(10);
        b.addDivider();
        b.setFont(F_SECTION);
        b.setBold(true);
        b.addWrappedText(titleVi, Align.CENTER);
        b.setBold(false);
        b.setFont(F_EN);
        b.addWrappedText(titleEn, Align.CENTER);
        b.addSpace(4);
        b.addDivider();
        b.addSpace(6);
    }

    /** Kẻ phân cách giữa các hạng mục đánh số trong cùng một mục. */
    private static void itemDivider(ZplLayoutBuilder b) {
        b.addSpace(6);
        b.addDivider();
        b.addSpace(4);
    }

    /**
     * Dòng khai báo: nhãn trái - dấu hai chấm - giá trị căn phải, đúng cột của phiếu hoàn trả
     * để hai phiếu đọc lên giống nhau.
     */
    private static void row(ZplLayoutBuilder b, String label, String value) {
        b.setFont(F_BODY);
        b.addLabelValue(label, value);
    }

    /** Dòng dẫn cho một nhóm ô đánh dấu. */
    private static void heading(ZplLayoutBuilder b, String text) {
        b.setFont(F_BODY);
        b.addWrappedText(text, Align.LEFT);
    }

    /** Lời dẫn đánh số của biểu mẫu: câu tiếng Việt cỡ thân, câu tiếng Anh cỡ nhỏ. */
    private static void item(ZplLayoutBuilder b, String vi, String en) {
        itemDivider(b);
        b.setFont(F_BODY);
        b.addWrappedText(vi, Align.LEFT);
        english(b, en);
    }

    private static void english(ZplLayoutBuilder b, String text) {
        b.setFont(F_EN);
        b.addWrappedText(text, Align.LEFT);
    }

    /** Hai lựa chọn Có/Không nằm cạnh nhau như bản giấy. */
    private static void yesNoRow(ZplLayoutBuilder b, Boolean value, String yes, String no) {
        optionsRow(b, new String[]{yes, no},
                new boolean[]{Boolean.TRUE.equals(value), Boolean.FALSE.equals(value)});
    }

    /** Nhiều lựa chọn ngắn trên cùng một dòng. */
    private static void optionsRow(ZplLayoutBuilder b, String[] labels, boolean[] checked) {
        b.setFont(F_BODY);
        b.addCheckOptionsRow(labels, checked, OPTION_INDENT);
    }

    /** Một ô đánh dấu (hình vuông vẽ thật) trên dòng riêng. */
    private static void option(ZplLayoutBuilder b, boolean checked, String label) {
        b.setFont(F_BODY);
        b.addCheckOption(checked, label, OPTION_INDENT);
    }

    private static void qcOptions(ZplLayoutBuilder b, QcCheck v) {
        optionsRow(b, new String[]{"Đạt/Satisfy", "Không đạt/Not Satisfy"},
                new boolean[]{v == QcCheck.SATISFY, v == QcCheck.NOT_SATISFY});
    }

    private static void kitOptions(ZplLayoutBuilder b, MicrobialKit kit, boolean withOther, String other) {
        optionsRow(b, new String[]{"Hy-lite", "Microb monitor2", "Fuelstat"},
                new boolean[]{kit == MicrobialKit.HY_LITE,
                        kit == MicrobialKit.MICROB_MONITOR2,
                        kit == MicrobialKit.FUELSTAT});
        if (withOther) {
            option(b, kit == MicrobialKit.OTHER,
                    "Khác/Other: " + valueOrBlank(kit == MicrobialKit.OTHER, other));
        }
    }

    private static void resultOptions(ZplLayoutBuilder b, MicrobialResult r) {
        option(b, r == MicrobialResult.NORMAL, "Mức độ được chấp nhận/Normal level");
        option(b, r == MicrobialResult.WARNING, "Mức độ cảnh báo/Warning level");
        option(b, r == MicrobialResult.ACTION, "Mức độ nặng/Action level");
    }

    // ============================================================== chuyển ngữ

    static String yesNo(Boolean v) {
        if (v == null) return ".....";
        return v ? "Có / Yes" : "Không / No";
    }

    static String qcText(QcCheck v) {
        if (v == null) return ".....";
        return v == QcCheck.SATISFY ? "Đạt / Satisfy" : "Không đạt / Not satisfy";
    }

    /** Phiếu cũ chỉ có cờ chung "đã phổ biến tín hiệu" → coi như đã thống nhất cả hai tín hiệu. */
    static boolean isThumbUp(BM7501Model m) {
        return m.isSignalThumbUp() || legacySignals(m);
    }

    static boolean isCrossArms(BM7501Model m) {
        return m.isSignalCrossArms() || legacySignals(m);
    }

    private static boolean legacySignals(BM7501Model m) {
        return m.isSignalsBriefed() && !m.isSignalThumbUp() && !m.isSignalCrossArms();
    }

    private static boolean has(BM7501Model m, Additive a) {
        List<Additive> list = m.getAdditives();
        return list != null && list.contains(a);
    }

    // ============================================================== tiện ích

    /** Số theo cách viết Việt Nam: dấu chấm phân nhóm nghìn, dấu phẩy thập phân. */
    static String num(Double value, int decimals) {
        if (value == null) return ".....";
        return String.format(VN, "%,." + decimals + "f", value);
    }

    static String formatDay(Date d) {
        return d == null ? "....." : new SimpleDateFormat(DAY, Locale.US).format(d);
    }

    static String formatTime(Date d) {
        return d == null ? "....." : new SimpleDateFormat(CLOCK, Locale.US).format(d);
    }

    static String formatDate(Date d) {
        if (d == null) return ".....";
        return new SimpleDateFormat(DATE_TIME, Locale.US).format(d);
    }

    /** Giá trị của một lựa chọn: chỉ in khi lựa chọn đó được chọn, còn lại chừa trống. */
    private static String valueOrBlank(boolean selected, String value) {
        return selected && notBlank(value) ? value : "";
    }

    private static String nz(String s) {
        return notBlank(s) ? s : ".....";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
