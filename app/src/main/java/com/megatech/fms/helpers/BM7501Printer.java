package com.megatech.fms.helpers;

import com.megatech.fms.helpers.ZplLayoutBuilder.Align;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.Additive;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dựng bản in nhiệt cho BM 75.01/NLHK.
 *
 * <p>Bản in nhiệt là <b>bản gốc của biểu mẫu</b> (đã chốt: chỉ dùng phiếu in nhiệt, in một liên),
 * nên phải in đủ mục A/B/C kể cả các dòng ghi chú pháp lý của bản giấy — không được rút gọn.
 *
 * <p>Hai đầu ra:
 * <ul>
 *   <li>{@link #createZpl} cho máy Zebra qua {@code ZebraWorker} — in được cả ảnh chữ ký.</li>
 *   <li>{@link #createEscpText} cho máy ESC/P qua {@code PrintWorker} — không in được ảnh,
 *       chừa chỗ ký tay.</li>
 * </ul>
 *
 * <p>Lớp thuần Java (không phụ thuộc Android) để chạy golden test trên JVM. Thông tin đơn vị
 * truyền vào qua {@link Options} thay vì đọc từ setting, cũng vì lý do đó.
 */
public final class BM7501Printer {

    private BM7501Printer() {
    }

    /** Tên ảnh chữ ký đã nạp sẵn vào máy in Zebra. */
    public static final String GRF_CUSTOMER_SECTION_A = "E:CUSTA.GRF";
    public static final String GRF_CUSTOMER_FINAL = "E:BUYER.GRF";
    public static final String GRF_SKYPEC = "E:SELLER.GRF";

    private static final int SIGNATURE_BLOCK_HEIGHT = 200;
    private static final int ESCP_WIDTH = 66;

    public static class Options {
        private String companyName = "CÔNG TY TNHH MTV NHIÊN LIỆU HÀNG KHÔNG VIỆT NAM (SKYPEC)";
        private String branchName = "";
        private boolean zq520;
        /** In bản sao: đóng dấu BẢN SAO/COPY ở đầu phiếu. */
        private boolean copy;
        /**
         * In thử: đóng dấu MẪU/SPECIMEN. Dùng cho việc nghiệm thu bố cục trên máy in thật
         * khi chưa có form nhập liệu — tờ in ra KHÔNG có giá trị pháp lý.
         */
        private boolean specimen;

        public Options companyName(String v) { this.companyName = v; return this; }
        public Options branchName(String v) { this.branchName = v; return this; }
        public Options zq520(boolean v) { this.zq520 = v; return this; }
        public Options copy(boolean v) { this.copy = v; return this; }
        public Options specimen(boolean v) { this.specimen = v; return this; }

        public String getCompanyName() { return companyName; }
        public String getBranchName() { return branchName; }
        public boolean isZq520() { return zq520; }
        public boolean isCopy() { return copy; }
        public boolean isSpecimen() { return specimen; }
    }

    /** Dòng cảnh báo in trên bản thử, cố ý dài và rõ để không ai nhầm là chứng từ thật. */
    static final String SPECIMEN_BANNER =
            "*** MẪU / SPECIMEN - KHÔNG CÓ GIÁ TRỊ PHÁP LÝ / NOT A LEGAL DOCUMENT ***";

    // ============================================================== ZPL (Zebra)

    public static String createZpl(BM7501Model m, Options opt) {
        if (m == null) throw new IllegalArgumentException("model không được null");
        if (opt == null) opt = new Options();

        ZplLayoutBuilder b = new ZplLayoutBuilder(ZplLayoutBuilder.DEFAULT_PRINT_WIDTH, opt.isZq520());
        b.addSpace(80);

        // ---- đầu phiếu
        b.setFont(25);
        b.addWrappedText(opt.getCompanyName().toUpperCase(Locale.getDefault()), Align.CENTER);
        if (notBlank(opt.getBranchName())) {
            b.addWrappedText("CHI NHÁNH " + opt.getBranchName().toUpperCase(Locale.getDefault()), Align.CENTER);
        }
        b.addSpace(10);

        b.setFont(35);
        b.addWrappedText("JET FUEL DEFUEL REQUEST FORM", Align.CENTER);
        b.setFont(30);
        b.addWrappedText("PHIẾU YÊU CẦU HÚT NHIÊN LIỆU TỪ TÀU BAY", Align.CENTER);

        if (opt.isSpecimen()) {
            b.setFont(25);
            b.addWrappedText(SPECIMEN_BANNER, Align.CENTER);
        }

        if (isCopyPrint(m, opt)) {
            b.setFont(35);
            b.addLine("*** BẢN SAO / COPY ***", Align.CENTER);
            b.setFont(20);
            b.addLine("Lần in: " + (m.getReprintCount() + 1), Align.CENTER);
        }

        b.setFont(25);
        b.addLine("No.: " + nz(m.getLocalNumber()), Align.CENTER);
        b.addLine(formatDate(m.getDate(), "HH:mm dd/MM/yyyy"), Align.CENTER);
        b.addDivider();

        // ---- MỤC A
        b.setFont(28);
        b.addLine("A. KHÁCH HÀNG / CUSTOMER", Align.LEFT);
        b.setFont(25);
        b.addLabelValue("Airline", m.getAirlineName());
        b.addLabelValue("Airport", m.getAirportName());
        b.addLabelValue("A/C Type", m.getAircraftType());
        b.addLabelValue("A/C Reg", m.getAircraftReg());
        b.addLabelValue("Rep. / Đại diện", m.getCustomerRepName());
        b.addLabelValue("Title / Chức danh", m.getCustomerTitle());
        b.addLabelValue("Tel", m.getCustomerTel());
        b.addLabelValue("Fax", m.getCustomerFax());
        b.addLabelValue("Reason / Lý do", reasonText(m));
        b.addLabelValue("Tank drain sample", yesNo(m.getTankDrainSampled()));
        b.addLabelValue("Microbial test", customerMicrobialText(m));
        b.addLabelValue("Additives / Phụ gia", additivesText(m));
        b.addLabelValue("Prev. loc. 1", joinLocation(m.getPrevLocation1(), m.getPrevGrade1()));
        b.addLabelValue("Prev. loc. 2", joinLocation(m.getPrevLocation2(), m.getPrevGrade2()));
        b.setFont(18);
        b.addWrappedText("Không xác định được loại nhiên liệu 2 sân bay trước thì ghi rõ "
                + "\"Không xác định được / Undetermined\".", Align.LEFT);
        b.setFont(25);
        b.addDivider();

        // ---- MỤC B
        b.setFont(28);
        b.addLine("B. SKYPEC", Align.LEFT);
        b.setFont(25);
        b.addLabelValue("VAC / Ngoại quan", qcText(m.getVac()));
        b.addLabelValue("CWD / Viên thử nước", qcText(m.getCwd()));
        b.addLabelValue("Density (kg/m3)", ZplLayoutBuilder.num(m.getDensityKgM3(), 1));
        b.addLabelValue("Conductivity (pS/m)",
                m.isConductivityRequired() ? ZplLayoutBuilder.num(m.getConductivityPsM(), 0) : "N/A");
        b.addLabelValue("Microbial test", skypecMicrobialText(m));
        if (notBlank(m.getMicrobialReason())) {
            b.addLabelValue("Lý do KT vi sinh", m.getMicrobialReason());
        }
        b.addDivider();

        // ---- MỤC C
        b.setFont(28);
        b.addLine("C. XÁC NHẬN / CONFIRMATION", Align.LEFT);
        b.setFont(25);
        b.addLabelValue("Defueller / P.tiện hút", m.getDefuellerTruckNo());
        b.addLabelValue("Start / Bắt đầu", formatDate(m.getStartTime(), "HH:mm dd/MM/yyyy"));
        b.addLabelValue("End / Kết thúc", formatDate(m.getEndTime(), "HH:mm dd/MM/yyyy"));
        b.addLabelValue("Method / P.thức hút", methodText(m));
        b.setFont(18);
        b.addWrappedText("Ghi chú: Ưu tiên sử dụng bơm của tàu bay / "
                + "Note: Priority to use the pump of aircraft.", Align.LEFT);
        b.addWrappedText("Tín hiệu phối hợp / Signals: giơ ngón cái tay phải hướng lên ngang mặt "
                + "là đã sẵn sàng hút; giơ chéo hai tay ra phía trước ngang mặt là có bất thường.",
                Align.LEFT);
        b.setFont(25);
        b.addLabelValue("Signals briefed", m.isSignalsBriefed() ? "Đã phổ biến / Briefed" : ".....");
        if (notBlank(m.getOtherSignal())) {
            b.addLabelValue("Tín hiệu khác", m.getOtherSignal());
        }
        b.addSpace(6);
        b.addLabelValue("Expected / Dự kiến (kg)", ZplLayoutBuilder.num(m.getExpectedKg(), 0));
        b.addLabelValue("Actual / Thực tế (kg)", ZplLayoutBuilder.num(m.getActualKg(), 0));
        b.addLabelValue("Temp. (°C)", ZplLayoutBuilder.num(m.getActualTempC(), 1));
        b.addLabelValue("Density (kg/m3)", ZplLayoutBuilder.num(m.getActualDensityKgM3(), 1));
        b.addLabelValue("USG / Gal", ZplLayoutBuilder.num(m.getGallon(), 0));
        b.addLabelValue("Liter / Lít", ZplLayoutBuilder.num(m.getLiter(), 0));
        b.addDivider();

        // ---- C9 / C10
        b.setFont(20);
        b.addWrappedText("1. Nhiên liệu hút ra có thể tra nạp cho tàu bay của cùng Hãng Hàng không "
                + "mà không cần kiểm tra bổ sung về chất lượng / The defuelled fuel can be refuelled, "
                + "without any additional quality test, to aircraft of the same Airline:", Align.LEFT);
        b.setFont(25);
        b.addLabelValue("Đồng ý / Yes - Không / No", yesNo(m.getRefuellableWithoutTest()));

        b.setFont(20);
        b.addWrappedText("2. Nếu không thể nạp lại ngay hoặc nhiên liệu có vấn đề chất lượng, "
                + "phương án xử lý / If fuel cannot be immediately returned or has quality issue:",
                Align.LEFT);
        b.setFont(25);
        b.addLabelValue("Phương án / Handling", handlingText(m));
        if (m.getStorageFrom() != null || m.getStorageTo() != null) {
            b.addLabelValue("Lưu trữ từ / From", formatDate(m.getStorageFrom(), "HH:mm dd/MM/yyyy"));
            b.addLabelValue("Lưu trữ đến / To", formatDate(m.getStorageTo(), "HH:mm dd/MM/yyyy"));
        }
        if (notBlank(m.getHandlingNote())) {
            b.addLabelValue("Ghi rõ / Note", m.getHandlingNote());
        }
        b.addDivider();

        // ---- chữ ký: 3 chữ ký theo đúng biểu mẫu
        b.setFont(25);
        b.addLine("ĐẠI DIỆN KHÁCH HÀNG (mục A)", Align.CENTER);
        b.addLine("Customer Rep. - Section A", Align.CENTER);
        if (notBlank(m.getCustomerSectionASignaturePath())) {
            b.addSignatureImage(GRF_CUSTOMER_SECTION_A, SIGNATURE_BLOCK_HEIGHT);
        } else {
            b.addSpace(SIGNATURE_BLOCK_HEIGHT);
        }
        b.addLine(nz(m.getCustomerRepName()), Align.CENTER);
        b.addSpace(10);

        b.addLine("ĐẠI DIỆN SKYPEC", Align.CENTER);
        b.addLine("Skypec Rep. (Ký, ghi rõ họ tên)", Align.CENTER);
        if (notBlank(m.getSkypecSignaturePath())) {
            b.addSignatureImage(GRF_SKYPEC, SIGNATURE_BLOCK_HEIGHT);
        } else {
            b.addSpace(SIGNATURE_BLOCK_HEIGHT);
        }
        b.addLine(nz(m.getSkypecRepName()), Align.CENTER);
        b.addSpace(10);

        b.addLine("ĐẠI DIỆN KHÁCH HÀNG", Align.CENTER);
        b.addLine("Customer Rep. (Ký, ghi rõ họ tên)", Align.CENTER);
        if (notBlank(m.getCustomerFinalSignaturePath())) {
            b.addSignatureImage(GRF_CUSTOMER_FINAL, SIGNATURE_BLOCK_HEIGHT);
        } else {
            b.addSpace(SIGNATURE_BLOCK_HEIGHT);
        }
        b.addLine(nz(m.getCustomerRepFinalName()), Align.CENTER);
        b.addDivider();

        // ---- ghi chú chân phiếu + mã biểu mẫu
        b.setFont(18);
        b.addWrappedText("Ghi chú: Sau khi hút nhiên liệu, nhân viên giao lại phiếu này cho cán bộ "
                + "đội tra nạp. Khi hãng hàng không có yêu cầu tra nạp lại, cán bộ đội tra nạp giao "
                + "phiếu, giao nhiệm vụ cho nhân viên tra nạp cho nạp đủ số đã hút. Nếu tra nạp lại "
                + "vượt số lượng hút thì phải viết phiếu xuất số lượng vượt.", Align.LEFT);
        b.addSpace(10);
        b.addLine("BM 75.01/NLHK", Align.RIGHT);
        b.addLine("Ban hành/sửa đổi: 01/03", Align.RIGHT);

        // Nhắc lại ở chân phiếu: bản thử có thể bị xé rời phần đầu.
        if (opt.isSpecimen()) {
            b.addSpace(10);
            b.addWrappedText(SPECIMEN_BANNER, Align.CENTER);
        }

        return b.build();
    }

    // ============================================================== ESC/P

    /**
     * Bản in cho máy ESC/P. Máy này không in được ảnh nên phần chữ ký chỉ chừa chỗ ký tay.
     */
    public static String createEscpText(BM7501Model m, Options opt) {
        if (m == null) throw new IllegalArgumentException("model không được null");
        if (opt == null) opt = new Options();

        StringBuilder b = new StringBuilder();
        line(b, center(opt.getCompanyName()));
        if (notBlank(opt.getBranchName())) {
            line(b, center("CHI NHÁNH " + opt.getBranchName()));
        }
        divider(b);
        line(b, center("JET FUEL DEFUEL REQUEST FORM"));
        line(b, center("PHIẾU YÊU CẦU HÚT NHIÊN LIỆU TỪ TÀU BAY"));
        if (opt.isSpecimen()) {
            line(b, center("*** MẪU / SPECIMEN ***"));
            line(b, center("KHÔNG CÓ GIÁ TRỊ PHÁP LÝ / NOT A LEGAL DOCUMENT"));
        }
        if (isCopyPrint(m, opt)) {
            line(b, center("*** BẢN SAO / COPY - Lần in: " + (m.getReprintCount() + 1) + " ***"));
        }
        line(b, center("No.: " + nz(m.getLocalNumber())
                + "   " + formatDate(m.getDate(), "HH:mm dd/MM/yyyy")));
        divider(b);

        line(b, "A. KHÁCH HÀNG / CUSTOMER");
        kv(b, "Airline", m.getAirlineName());
        kv(b, "Airport", m.getAirportName());
        kv(b, "A/C Type", m.getAircraftType());
        kv(b, "A/C Reg", m.getAircraftReg());
        kv(b, "Rep. / Title", nz(m.getCustomerRepName()) + " / " + nz(m.getCustomerTitle()));
        kv(b, "Tel / Fax", nz(m.getCustomerTel()) + " / " + nz(m.getCustomerFax()));
        kv(b, "Reason", reasonText(m));
        kv(b, "Tank drain sample", yesNo(m.getTankDrainSampled()));
        kv(b, "Microbial (customer)", customerMicrobialText(m));
        kv(b, "Additives", additivesText(m));
        kv(b, "Prev. loc. 1", joinLocation(m.getPrevLocation1(), m.getPrevGrade1()));
        kv(b, "Prev. loc. 2", joinLocation(m.getPrevLocation2(), m.getPrevGrade2()));
        divider(b);

        line(b, "B. SKYPEC");
        kv(b, "VAC", qcText(m.getVac()));
        kv(b, "CWD", qcText(m.getCwd()));
        kv(b, "Density (kg/m3)", ZplLayoutBuilder.num(m.getDensityKgM3(), 1));
        kv(b, "Conductivity (pS/m)",
                m.isConductivityRequired() ? ZplLayoutBuilder.num(m.getConductivityPsM(), 0) : "N/A");
        kv(b, "Microbial (SKYPEC)", skypecMicrobialText(m));
        if (notBlank(m.getMicrobialReason())) kv(b, "Lý do KT vi sinh", m.getMicrobialReason());
        divider(b);

        line(b, "C. XÁC NHẬN / CONFIRMATION");
        kv(b, "Defueller", m.getDefuellerTruckNo());
        kv(b, "Start / End", formatDate(m.getStartTime(), "HH:mm dd/MM/yyyy")
                + " - " + formatDate(m.getEndTime(), "HH:mm dd/MM/yyyy"));
        kv(b, "Method", methodText(m));
        line(b, "Ghi chú: Ưu tiên sử dụng bơm của tàu bay /");
        line(b, "         Priority to use aircraft pump.");
        line(b, "Tín hiệu: ngón cái tay phải hướng lên = sẵn sàng hút;");
        line(b, "          chéo hai tay ngang mặt = có bất thường.");
        kv(b, "Signals briefed", m.isSignalsBriefed() ? "Đã phổ biến / Briefed" : ".....");
        if (notBlank(m.getOtherSignal())) kv(b, "Tín hiệu khác", m.getOtherSignal());
        kv(b, "Expected (kg)", ZplLayoutBuilder.num(m.getExpectedKg(), 0));
        kv(b, "Actual (kg)", ZplLayoutBuilder.num(m.getActualKg(), 0));
        kv(b, "Temp. (oC)", ZplLayoutBuilder.num(m.getActualTempC(), 1));
        kv(b, "Density (kg/m3)", ZplLayoutBuilder.num(m.getActualDensityKgM3(), 1));
        kv(b, "USG / Liter", ZplLayoutBuilder.num(m.getGallon(), 0)
                + " / " + ZplLayoutBuilder.num(m.getLiter(), 0));
        divider(b);

        line(b, "1. Nhiên liệu hút ra nạp lại được cho tàu bay cùng hãng, không cần");
        line(b, "   kiểm tra bổ sung / Refuellable without additional test:");
        kv(b, "   Đồng ý / Yes - Không / No", yesNo(m.getRefuellableWithoutTest()));
        line(b, "2. Nếu không nạp lại ngay hoặc có vấn đề chất lượng, phương án xử lý:");
        kv(b, "   Handling", handlingText(m));
        if (m.getStorageFrom() != null || m.getStorageTo() != null) {
            kv(b, "   Lưu trữ Từ/Đến", formatDate(m.getStorageFrom(), "HH:mm dd/MM/yyyy")
                    + " - " + formatDate(m.getStorageTo(), "HH:mm dd/MM/yyyy"));
        }
        if (notBlank(m.getHandlingNote())) kv(b, "   Ghi rõ", m.getHandlingNote());
        divider(b);

        // Máy ESC/P không in được ảnh chữ ký -> chừa chỗ ký tay.
        line(b, center("ĐẠI DIỆN KHÁCH HÀNG (mục A) / Customer Rep. - Section A"));
        blank(b, 4);
        line(b, center(nz(m.getCustomerRepName())));
        line(b, center("ĐẠI DIỆN SKYPEC / Skypec Rep."));
        blank(b, 4);
        line(b, center(nz(m.getSkypecRepName())));
        line(b, center("ĐẠI DIỆN KHÁCH HÀNG / Customer Rep."));
        blank(b, 4);
        line(b, center(nz(m.getCustomerRepFinalName())));
        divider(b);

        line(b, "Ghi chú: Sau khi hút nhiên liệu, nhân viên giao lại phiếu này cho cán");
        line(b, "bộ đội tra nạp. Khi hãng có yêu cầu tra nạp lại, cán bộ đội tra nạp");
        line(b, "giao phiếu và giao nhiệm vụ cho nhân viên tra nạp đủ số đã hút. Nếu");
        line(b, "tra nạp lại vượt số lượng hút thì phải viết phiếu xuất số lượng vượt.");
        line(b, right("BM 75.01/NLHK"));
        line(b, right("Ban hành/sửa đổi: 01/03"));

        return b.toString();
    }

    // ============================================================== chuyển ngữ

    static String reasonText(BM7501Model m) {
        if (m.getReason() == null) return ".....";
        switch (m.getReason()) {
            case LOAD_ADJUSTMENT:
                return "Điều chỉnh tải trọng / Load adjustment";
            case MAINTENANCE:
                return "Bảo dưỡng, sửa chữa / Maintenance";
            case OTHER:
                return "Khác / Other: " + nz(m.getReasonOther());
            default:
                return ".....";
        }
    }

    static String yesNo(Boolean v) {
        if (v == null) return ".....";
        return v ? "Có / Yes" : "Không / No";
    }

    static String qcText(BM7501Model.QcCheck v) {
        if (v == null) return ".....";
        return v == BM7501Model.QcCheck.SATISFY ? "Đạt / Satisfy" : "Không đạt / Not satisfy";
    }

    static String kitText(BM7501Model.MicrobialKit kit, String other) {
        if (kit == null) return null;
        switch (kit) {
            case HY_LITE: return "Hy-lite";
            case MICROB_MONITOR2: return "Microb monitor2";
            case FUELSTAT: return "Fuelstat";
            case OTHER: return "Khác / Other: " + nz(other);
            default: return null;
        }
    }

    static String resultText(BM7501Model.MicrobialResult r) {
        if (r == null) return null;
        switch (r) {
            case NORMAL: return "Được chấp nhận / Normal level";
            case WARNING: return "Cảnh báo / Warning level";
            case ACTION: return "Mức độ nặng / Action level";
            default: return null;
        }
    }

    static String customerMicrobialText(BM7501Model m) {
        if (m.getCustomerMicrobialTestPerformed() == null) return ".....";
        switch (m.getCustomerMicrobialTestPerformed()) {
            case NO:
                return "Chưa kiểm tra / Not tested";
            case UNKNOWN:
                return "Không xác định / Unknown";
            case YES:
                String kit = kitText(m.getCustomerMicrobialKit(), m.getCustomerMicrobialKitOther());
                String res = resultText(m.getCustomerMicrobialResult());
                return nzDash(kit) + " - " + nzDash(res);
            default:
                return ".....";
        }
    }

    static String skypecMicrobialText(BM7501Model m) {
        if (!m.isSkypecMicrobialRequired()
                && m.getSkypecMicrobialKit() == null
                && m.getSkypecMicrobialResult() == null) {
            return "Không yêu cầu / Not required";
        }
        return nzDash(kitText(m.getSkypecMicrobialKit(), null))
                + " - " + nzDash(resultText(m.getSkypecMicrobialResult()));
    }

    static String additivesText(BM7501Model m) {
        if (m.getAdditivePresence() == null) return ".....";
        switch (m.getAdditivePresence()) {
            case NONE:
                return "Không sử dụng / No";
            case UNDETERMINED:
                return "Không xác định được / Undetermined";
            case PRESENT:
                List<Additive> list = m.getAdditives();
                if (list == null || list.isEmpty()) return ".....";
                StringBuilder sb = new StringBuilder();
                for (Additive a : list) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(a == Additive.AQUARIUS_WMA ? "Aquarius WMA"
                            : a == Additive.FSII ? "FSII" : "Biocide");
                }
                return sb.toString();
            default:
                return ".....";
        }
    }

    static String methodText(BM7501Model m) {
        if (m.getMethod() == null) return ".....";
        switch (m.getMethod()) {
            case AIRCRAFT_PUMP: return "Bơm của tàu bay / Aircraft's pump";
            case REFUELLER_PUMP: return "Bơm của xe tra nạp / Refueller's pump";
            case BOTH: return "Bơm tàu bay và xe tra nạp / Both";
            default: return ".....";
        }
    }

    static String handlingText(BM7501Model m) {
        if (m.getHandling() == null) {
            return m.isHandlingRequired() ? "....." : "Không áp dụng / N/A";
        }
        switch (m.getHandling()) {
            case STORAGE:
                return "Yêu cầu lưu trữ / Storage";
            case SAME_AIRCRAFT:
                return "Nạp lại cho chính tàu bay đã hút / Same aircraft";
            case OTHER_AIRCRAFT_SAME_AIRLINE:
                return "Nạp lại cho tàu bay khác của hãng / Other aircraft, same airline";
            case AUTHORIZE_SKYPEC:
                return "Không nạp lại, ủy quyền SKYPEC xử lý / Authorize SKYPEC";
            case REFUEL_DESPITE_ISSUE:
                return "Xác nhận nạp lại dù nhiên liệu có vấn đề / Refuel despite issue";
            default:
                return ".....";
        }
    }

    // ============================================================== tiện ích

    private static boolean isCopyPrint(BM7501Model m, Options opt) {
        return opt.isCopy() || m.getReprintCount() > 0;
    }

    static String joinLocation(String location, String grade) {
        if (!notBlank(location) && !notBlank(grade)) return ".....";
        return nz(location) + " - " + nz(grade);
    }

    static String formatDate(Date d, String pattern) {
        if (d == null) return ".....";
        return new SimpleDateFormat(pattern, Locale.US).format(d);
    }

    private static String nz(String s) {
        return notBlank(s) ? s : ".....";
    }

    private static String nzDash(String s) {
        return notBlank(s) ? s : ".....";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void line(StringBuilder b, String s) {
        b.append(s).append('\n');
    }

    private static void blank(StringBuilder b, int count) {
        for (int i = 0; i < count; i++) b.append('\n');
    }

    private static void divider(StringBuilder b) {
        for (int i = 0; i < ESCP_WIDTH; i++) b.append('-');
        b.append('\n');
    }

    private static void kv(StringBuilder b, String key, String value) {
        b.append(String.format(Locale.US, "%-24s: %s%n", key, nz(value)));
    }

    private static String center(String s) {
        if (s == null) return "";
        if (s.length() >= ESCP_WIDTH) return s;
        int pad = (ESCP_WIDTH - s.length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pad; i++) sb.append(' ');
        return sb.append(s).toString();
    }

    private static String right(String s) {
        if (s == null) return "";
        if (s.length() >= ESCP_WIDTH) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ESCP_WIDTH - s.length(); i++) sb.append(' ');
        return sb.append(s).toString();
    }
}
