package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Luật lưu tạm chữ ký và mời dùng lại theo CHUYẾN.
 *
 * <p>Điểm phải khoá chặt nhất: tính năng này chỉ được HỎI, không được tự áp và không được
 * biến thành một đường chặn mới. Vì vậy các ca kiểm thử đều nhìn theo hướng "thiếu thông tin
 * thì vẫn mời dùng lại, chỉ nói ít đi", chứ không phải "thiếu thông tin thì thôi không mời".
 */
public class SignatureCacheTest {

    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    private static final int USER_A = 12;
    private static final int USER_B = 99;

    // ------------------------------------------------------------------ khoá

    @Test
    public void khoaChuyenUuTienMaChuyenTrongHeThong() {
        assertEquals("F4321", SignatureCache.flightKey(4321, "VN-123"));
    }

    @Test
    public void khongCoMaChuyenThiDungSoHieuChuyenDaChuanHoa() {
        assertEquals("CVN-123", SignatureCache.flightKey(0, " vn 123 "));
    }

    @Test
    public void khongXacDinhDuocChuyenThiKhongLuuTamGiCa() {
        assertNull(SignatureCache.flightKey(0, null));
        assertNull(SignatureCache.flightKey(0, "   "));
        assertNull(SignatureCache.flightKey(0, "///"));
    }

    /** Ca bắt buộc: mã chuyến có ký tự lạ vẫn phải cho ra tên tệp an toàn và đọc ngược được. */
    @Test
    public void maChuyenCoKyTuLaVanAnToanLamTenTep() {
        String key = SignatureCache.flightKey(0, "VN/123 *?<>|:\\\"đ_1");
        assertNotNull(key);
        for (char c : key.toCharArray()) {
            boolean ok = Character.isLetterOrDigit(c) && c < 128 || c == '-';
            assertTrue("ký tự không an toàn cho tên tệp: " + c, ok);
        }
        // Khoá không được chứa '_' vì '_' là dấu tách các phần của tên tệp.
        assertFalse(key.contains("_"));

        String buyer = SignatureCache.buyerFileName(key);
        String seller = SignatureCache.sellerFileName(key, USER_A);
        assertEquals(key, SignatureCache.parse(buyer).flightKey);
        assertEquals(key, SignatureCache.parse(seller).flightKey);
        assertEquals(USER_A, SignatureCache.parse(seller).userId);
    }

    // -------------------------------------------------------------- phân tích

    @Test
    public void phanTichTenTepChuKy() {
        SignatureCache.Entry buyer = SignatureCache.parse("SIGTMP_F7_buyer.jpg");
        assertNotNull(buyer);
        assertTrue(buyer.buyer);
        assertEquals("F7", buyer.flightKey);

        SignatureCache.Entry seller = SignatureCache.parse("SIGTMP_F7_seller_12.jpg");
        assertNotNull(seller);
        assertFalse(seller.buyer);
        assertEquals("F7", seller.flightKey);
        assertEquals(12, seller.userId);
    }

    @Test
    public void tepLaKhongBiNhanNhamLaChuKyLuuTam() {
        assertNull(SignatureCache.parse(null));
        assertNull(SignatureCache.parse("JPEG_Signature123456.jpg"));
        assertNull(SignatureCache.parse("SIGTMP_F7.meta"));
        assertNull(SignatureCache.parse("SIGTMP_F7_seller_abc.jpg"));
        assertNull(SignatureCache.parse("SIGTMP_buyer.jpg"));
        // Chữ ký ĐÃ XUẤT (đã ra khỏi vùng lưu tạm) không bao giờ được mời lại.
        assertNull(SignatureCache.parse(SignatureCache.promotedFileName("2619EY0", true)));
    }

    // ----------------------------------------------------------- lời mời

    @Test
    public void cungNguoiDangNhapThiMoiCaHaiChuKy() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg", "SIGTMP_F7_seller_12.jpg", "SIGTMP_F7.meta");

        SignatureCache.Offer offer = SignatureCache.offer(files, "F7", USER_A, false);

        assertTrue(offer.hasBuyer());
        assertTrue(offer.hasSeller());
        assertFalse(offer.sellerDroppedByUserChange);
        assertEquals("khách hàng và nhân viên", SignatureCache.signerSummary(offer));
    }

    /** Ca bắt buộc: đổi người đăng nhập ⇒ loại chữ ký nhân viên, GIỮ chữ ký khách. */
    @Test
    public void doiNguoiDangNhapThiLoaiChuKyNhanVienGiuChuKyKhach() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg", "SIGTMP_F7_seller_12.jpg");

        SignatureCache.Offer offer = SignatureCache.offer(files, "F7", USER_B, false);

        assertTrue("chữ ký khách vẫn phải được mời", offer.hasBuyer());
        assertFalse("chữ ký nhân viên của người khác không được mời", offer.hasSeller());
        assertTrue(offer.sellerDroppedByUserChange);
        assertTrue(SignatureCache.signerSummary(offer).contains("đổi người đăng nhập"));
    }

    @Test
    public void nguoiCuDangNhapLaiThiChuKyNhanVienCuaHoVanDung() {
        // Tệp của người khác KHÔNG bị xoá, nên chính chủ quay lại vẫn dùng được.
        List<String> files = Arrays.asList("SIGTMP_F7_seller_12.jpg");
        assertFalse(SignatureCache.offer(files, "F7", USER_B, false).hasSeller());
        assertTrue(SignatureCache.offer(files, "F7", USER_A, false).hasSeller());
    }

    @Test
    public void chiMoiChuKyCuaDungChuyenDangMo() {
        List<String> files = Arrays.asList(
                "SIGTMP_F8_buyer.jpg", "SIGTMP_F8_seller_12.jpg");

        assertFalse(SignatureCache.offer(files, "F7", USER_A, false).hasAny());
        assertTrue(SignatureCache.offer(files, "F8", USER_A, false).hasAny());
    }

    /** Ca bắt buộc: màn IN LẠI không bao giờ được hỏi — nghiệp vụ in lại giữ nguyên như cũ. */
    @Test
    public void manInLaiKhongBaoGioDuocMoi() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg", "SIGTMP_F7_seller_12.jpg");

        SignatureCache.Offer offer = SignatureCache.offer(files, "F7", USER_A, true);

        assertFalse(offer.hasAny());
        assertFalse(offer.sellerDroppedByUserChange);
    }

    @Test
    public void khongXacDinhDuocChuyenThiKhongMoiGi() {
        List<String> files = Arrays.asList("SIGTMP_F7_buyer.jpg");
        assertFalse(SignatureCache.offer(files, null, USER_A, false).hasAny());
    }

    @Test
    public void thuMucRongThiKhongMoiGi() {
        assertFalse(SignatureCache.offer(new ArrayList<>(), "F7", USER_A, false).hasAny());
        assertFalse(SignatureCache.offer(null, "F7", USER_A, false).hasAny());
    }

    // --------------------------------------------------- dòng sản lượng của phiếu

    /** Ca bắt buộc: khối lượng đổi ⇒ nói cả số hiện tại lẫn số lúc ký, đơn vị Kg. */
    @Test
    public void khoiLuongDoiThiNoiCaSoHienTaiVaSoLucKy() {
        String line = SignatureCache.weightLine(1520d, 1480d, NF);

        assertNotNull(line);
        assertTrue(line, line.contains("1,480"));
        assertTrue(line, line.contains("1,520"));
        assertTrue("phải nói rõ là đã đổi", line.contains("ĐÃ ĐỔI"));
        assertTrue("phải là Kg, không phải lít", line.contains(SignatureCache.WEIGHT_UNIT));
        assertEquals("Kg", SignatureCache.WEIGHT_UNIT);
    }

    /**
     * Ca bắt buộc: khối lượng KHÔNG đổi thì VẪN phải nói ra số Kg.
     *
     * <p>Chủ dự án chốt 06-09-2026: câu hỏi phải cho đối chiếu ngay con số trên phiếu. Im
     * lặng khi không đổi thì người dùng phải thoát ra xem lại phiếu — đúng thứ tính năng này
     * sinh ra để tránh.
     */
    @Test
    public void khoiLuongKhongDoiThiVanPhaiNoiSoKg() {
        String line = SignatureCache.weightLine(1520d, 1520d, NF);
        assertNotNull("không đổi vẫn phải nói số Kg", line);
        assertTrue(line, line.contains("1,520"));
        assertTrue(line, line.contains("không đổi"));

        // Chênh dưới 1 Kg là nhiễu của phép tính, không phải người dùng sửa số liệu.
        String noise = SignatureCache.weightLine(1520.4d, 1520.2d, NF);
        assertNotNull(noise);
        assertFalse("chênh dưới 1 Kg không được báo là đã đổi", noise.contains("ĐÃ ĐỔI"));
    }

    /** Ca bắt buộc: thiếu meta ⇒ VẪN mời dùng lại, chỉ là không có dòng sản lượng. */
    @Test
    public void thieuTepMetaThiVanMoiDungLaiChiMatDongSanLuong() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg", "SIGTMP_F7_seller_12.jpg"); // không có .meta

        SignatureCache.Offer offer = SignatureCache.offer(files, "F7", USER_A, false);
        assertTrue("thiếu meta KHÔNG được làm mất lời mời", offer.hasAny());

        Double weightAtSign = SignatureCache.parseMetaWeight(null);
        assertNull(weightAtSign);
        assertNull(SignatureCache.weightLine(weightAtSign, 1480d, NF));

        String message = SignatureCache.buildMessage(offer, "09:12 06/09/2026", null);
        assertTrue(message, message.contains("09:12 06/09/2026"));
        assertTrue(message, message.contains("khách hàng và nhân viên"));
        assertFalse(message, message.contains("Sản lượng"));
    }

    /**
     * Câu hỏi phải nói đủ BA thứ: đã có chữ ký lưu tạm, thời điểm ký, số Kg của phiếu.
     *
     * <p>Chủ dự án chốt 06-09-2026. Trước đó câu hỏi chỉ có giờ và tên bên ký, nên người dùng
     * không biết chữ ký đó gắn với bộ số nào.
     */
    @Test
    public void cauHoiPhaiNoiDuChuKyTamThoiDiemVaSoKg() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg", "SIGTMP_F7_seller_12.jpg");
        SignatureCache.Offer offer = SignatureCache.offer(files, "F7", USER_A, false);

        String message = SignatureCache.buildMessage(offer, "09:12 06/09/2026",
                SignatureCache.weightLine(1520d, 1480d, NF));

        assertTrue("phải nói rõ chuyến này ĐÃ CÓ chữ ký lưu tạm",
                message.contains("đã có chữ ký lưu tạm"));
        assertTrue("phải nói thời điểm ký, có cả ngày", message.contains("09:12 06/09/2026"));
        assertTrue("phải nói số Kg của phiếu", message.contains("1,480"));
        assertTrue("phải nói đơn vị Kg", message.contains("Kg"));
        assertTrue("phải nói ai đã ký", message.contains("khách hàng và nhân viên"));
    }

    @Test
    public void metaHongCungKhongLamMatLoiMoi() {
        assertNull(SignatureCache.parseMetaWeight(""));
        assertNull(SignatureCache.parseMetaWeight("   "));
        assertNull(SignatureCache.parseMetaWeight("khong-phai-so"));
        assertEquals(1520d, SignatureCache.parseMetaWeight(" 1520.000 \n"), 0.0001);
        assertEquals(1520d,
                SignatureCache.parseMetaWeight(SignatureCache.metaContent(1520d)), 0.0001);
    }

    // Ca "câu hỏi có đủ nội dung" nằm ở cauHoiPhaiNoiDuChuKyTamThoiDiemVaSoKg() phía trên —
    // luật mới 06-09-2026 đòi đủ BA thứ, chặt hơn bản cũ chỉ đòi giờ + bên ký.

    // ------------------------------------------------------------------ xoá

    @Test
    public void xoaDungTepCuaChuyenDoVaKhongDungTepCuaChuyenKhac() {
        List<String> files = Arrays.asList(
                "SIGTMP_F7_buyer.jpg",
                "SIGTMP_F7_seller_12.jpg",
                "SIGTMP_F7_seller_99.jpg",
                "SIGTMP_F7.meta",
                "SIGTMP_F8_buyer.jpg",
                "SIGTMP_F8.meta",
                "JPEG_2619EY0_1234.jpg",
                "screenshot_2619EY0.jpg");

        List<String> deleted = SignatureCache.filesOfFlight(files, "F7");

        assertEquals(4, deleted.size());
        assertTrue(deleted.contains("SIGTMP_F7_buyer.jpg"));
        assertTrue(deleted.contains("SIGTMP_F7_seller_12.jpg"));
        assertTrue("cả chữ ký của người đăng nhập khác cũng là rác của chuyến này",
                deleted.contains("SIGTMP_F7_seller_99.jpg"));
        assertTrue(deleted.contains("SIGTMP_F7.meta"));
        assertFalse(deleted.contains("SIGTMP_F8_buyer.jpg"));
        assertFalse(deleted.contains("JPEG_2619EY0_1234.jpg"));
    }

    @Test
    public void tenChuKyDaXuatKhongTrungVungLuuTam() {
        String promoted = SignatureCache.promotedFileName("2619EY0", true);
        assertFalse(promoted.startsWith(SignatureCache.PREFIX));
        assertFalse(SignatureCache.filesOfFlight(Arrays.asList(promoted), "F7")
                .contains(promoted));
        // Số phiếu rỗng vẫn phải ra một tên tệp hợp lệ, không được ném lỗi.
        assertNotNull(SignatureCache.promotedFileName(null, false));
    }
}
