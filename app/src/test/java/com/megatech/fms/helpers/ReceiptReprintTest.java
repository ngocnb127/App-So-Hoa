package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.data.entity.TruckInvoice;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * In lại phiếu từ màn "Hoá đơn theo xe" — kể cả phiếu của xe khác.
 *
 * <p>Chủ dự án chốt ngày 2026-09-16: mở xem hoá đơn của xe khác (mặc định chọn xe hiện tại);
 * mỗi hoá đơn có nút in lại phiếu, chỉ ở bản in nhiệt. ZPL lấy từ ReceiptModel như nút "Chỉ
 * in lại phiếu"; ảnh chữ ký lấy ở {@code {API}/receipts/{số phiếu}_BUYER.jpg | _SELLER.jpg}.
 */
public class ReceiptReprintTest {

    private static final String API = "https://fmsapi.skypec.com.vn/";

    // ------------------------------------------------------------------ URL chữ ký

    @Test
    public void urlChuKyNguoiBanDungMauChuDuAnDua() {
        assertEquals("https://fmsapi.skypec.com.vn/receipts/2619VLV_SELLER.jpg",
                ReceiptReprint.signatureUrl(API, "2619VLV", false));
    }

    @Test
    public void urlChuKyNguoiMuaDungMauChuDuAnDua() {
        assertEquals("https://fmsapi.skypec.com.vn/receipts/2619VLV_BUYER.jpg",
                ReceiptReprint.signatureUrl(API, "2619VLV", true));
    }

    /** API_BASE_URL có dấu "/" cuối; ghép bừa ra "//receipts" là trông vào may rủi của IIS. */
    @Test
    public void khongGhepRaHaiDauGachCheo() {
        String url = ReceiptReprint.signatureUrl("https://host//", " 2619VLV ", true);
        assertEquals("https://host/receipts/2619VLV_BUYER.jpg", url);
    }

    @Test
    public void khongCoSoPhieuThiKhongCoUrl() {
        assertNull(ReceiptReprint.signatureUrl(API, "  ", true));
        assertNull(ReceiptReprint.signatureUrl(API, null, false));
    }

    // ------------------------------------------------------------- tên tệp ảnh chữ ký

    /**
     * Tên server đang lưu phải được thử TRƯỚC tên suy ra từ số phiếu: phiếu dùng lại số hoặc
     * phiếu thay thế có thể mang tên tệp khác, đoán tên là lấy nhầm chữ ký của phiếu kia.
     */
    @Test
    public void tenTepServerTraVeDuocThuTruocTenDoanTheoSoPhieu() {
        assertEquals(Arrays.asList("R1795397_BUYER.jpg", "2619VLV_BUYER.jpg"),
                ReceiptReprint.signatureFileNames("R1795397_BUYER.jpg", "2619VLV", true));
    }

    /** Phiếu trong máy giữ ĐƯỜNG DẪN trên máy; chỉ phần tên cuối mới có nghĩa trên server. */
    @Test
    public void duongDanTrenMayChiLayPhanTenTep() {
        assertEquals(Arrays.asList("SIG_2619VLV_BUYER.jpg", "2619VLV_BUYER.jpg"),
                ReceiptReprint.signatureFileNames("/storage/emulated/0/Pictures/SIG_2619VLV_BUYER.jpg",
                        "2619VLV", true));
    }

    @Test
    public void khongCoTenServerThiChiConTenDoanTheoSoPhieu() {
        assertEquals(Arrays.asList("2619VLV_SELLER.jpg"),
                ReceiptReprint.signatureFileNames(null, "2619VLV", false));
        assertEquals(Arrays.asList("2619VLV_SELLER.jpg"),
                ReceiptReprint.signatureFileNames("2619VLV_SELLER.jpg", "2619VLV", false));
    }

    @Test
    public void urlAnhGhepTuTenTepServerTraVe() {
        assertEquals("https://fmsapi.skypec.com.vn/receipts/1795397_BUYER.jpg",
                ReceiptReprint.imageUrl(API, "1795397_BUYER.jpg"));
        assertNull(ReceiptReprint.imageUrl(API, " "));
    }

    // ------------------------------------------------------------------ tệp chữ ký trong máy

    @Test
    public void tepChuKyKhongConThiPhaiTaiLai() throws IOException {
        File missing = new File(Files.createTempDirectory("sig").toFile(), "khong_co.jpg");
        assertFalse("tệp không tồn tại mà coi là dùng được thì ZPL gọi ảnh cũ trong máy in",
                ReceiptReprint.isUsableFile(missing.getAbsolutePath()));
        assertFalse(ReceiptReprint.isUsableFile(null));
        assertFalse(ReceiptReprint.isUsableFile(""));
    }

    @Test
    public void tepChuKyRongCungKhongDung() throws IOException {
        File empty = File.createTempFile("sig", ".jpg");
        empty.deleteOnExit();
        assertFalse(ReceiptReprint.isUsableFile(empty.getAbsolutePath()));
    }

    @Test
    public void tepChuKyConNguyenThiDungLuon() throws IOException {
        File file = File.createTempFile("sig", ".jpg");
        file.deleteOnExit();
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        assertTrue(ReceiptReprint.isUsableFile(file.getAbsolutePath()));
    }

    @Test
    public void tenTepTaiVeKhongMangKyTuDuongDan() {
        assertEquals("___etc_2619VLV", ReceiptReprint.safeFileName("../etc/2619VLV"));
        assertEquals("2619VLV", ReceiptReprint.safeFileName("2619VLV"));
    }

    // ------------------------------------------------------------------ cảnh báo thiếu chữ ký

    @Test
    public void duHaiChuKyThiKhongCanhBao() {
        assertNull(ReceiptReprint.missingSignatureWarning(false, false));
    }

    @Test
    public void thieuChuKyNaoThiNoiDungChuKyDo() {
        assertTrue(ReceiptReprint.missingSignatureWarning(true, false).contains("người mua"));
        assertFalse(ReceiptReprint.missingSignatureWarning(true, false).contains("người bán"));
        assertTrue(ReceiptReprint.missingSignatureWarning(false, true).contains("người bán"));
        assertTrue(ReceiptReprint.missingSignatureWarning(true, true).contains("người mua và người bán"));
    }

    // ------------------------------------------------------------------ hoá đơn xe khác

    @Test
    public void hoaDonXeKhacLocDungKhoangNgayNhuXeMinhVaMoiNhatLenDau() {
        TruckInvoice before = invoice(1, 999);
        TruckInvoice first = invoice(2, 1_000);
        TruckInvoice later = invoice(3, 1_500);
        TruckInvoice atEnd = invoice(4, 2_000);
        TruckInvoice noDate = invoice(5, -1);

        List<TruckInvoice> result = TruckInvoiceSync.filterRange(
                Arrays.asList(before, first, later, atEnd, noDate, null), 1_000, 2_000);

        assertEquals("from tính vào, toExclusive không tính, thiếu ngày thì bỏ",
                Arrays.asList(later, first), result);
    }

    @Test
    public void khongTaiDuocHoaDonXeKhacThiDanhSachRongKhongVang() {
        assertTrue(TruckInvoiceSync.filterRange(null, 0, Long.MAX_VALUE).isEmpty());
    }

    // ------------------------------------------------------------------ Id phiếu

    /**
     * Hoá đơn mang theo Id phiếu thì in lại tải thẳng {@code GET api/receipts/{id}} — endpoint
     * đã có sẵn trên server. Server chưa trả trường này thì Id = 0 và app tra theo số phiếu.
     */
    @Test
    public void hoaDonNhanDuocIdPhieuTuApi() {
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

        TruckInvoice withId = gson.fromJson(
                "{\"InvoiceId\":965258,\"BillNo\":\"36050VCT\",\"ReceiptId\":1795397}", TruckInvoice.class);
        assertEquals(1795397, withId.getReceiptId());

        TruckInvoice withoutId = gson.fromJson(
                "{\"InvoiceId\":965258,\"BillNo\":\"36050VCT\"}", TruckInvoice.class);
        assertEquals("server chưa trả ReceiptId thì phải là 0, không được đoán", 0, withoutId.getReceiptId());
    }

    /**
     * Id phiếu KHÔNG được thành cột Room: thêm cột là đổi schema, mà đổi schema thì máy đang
     * giữ dữ liệu chờ đẩy không cập nhật được.
     */
    @Test
    public void idPhieuKhongDuocLuuVaoRoom() throws IOException {
        String entity = source("src/main/java/com/megatech/fms/data/entity/TruckInvoice.java");
        int at = entity.indexOf("receiptId");
        assertTrue("không thấy trường receiptId", at > 0);
        String before = entity.substring(0, at);
        assertTrue("trường receiptId phải mang @Ignore",
                before.lastIndexOf("@Ignore") > before.lastIndexOf(";"));
    }

    // ------------------------------------------------------------------ canh mã nguồn

    /** Nút in lại chỉ có ở bản máy in nhiệt: ZPL không in được trên máy in kim. */
    @Test
    public void nutInLaiChiHienOBanInNhiet() throws IOException {
        String adapter = source("src/main/java/com/megatech/fms/view/TruckInvoiceAdapter.java");
        assertTrue(adapter.contains("reprintButton.setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE)"));
        String activity = source("src/main/java/com/megatech/fms/TruckInvoiceActivity.java");
        assertTrue("đường in lại phải tự chặn khi không phải bản in nhiệt",
                activity.contains("if (!BuildConfig.THERMAL_PRINTER || reprintBusy) return;"));
    }

    /**
     * Phiếu in lại mở vào màn in ở chế độ in lại (ẩn ký/lưu/chụp) và KHÔNG nối đồng hồ — số
     * đồng hồ của xe này không liên quan tới phiếu cũ của xe khác.
     */
    @Test
    public void phieuInLaiMoOCheDoInLaiVaKhongNoiDongHo() throws IOException {
        String src = source("src/main/java/com/megatech/fms/PrintReceiptActivity.java");
        int at = src.indexOf("else if (reprintData != null)");
        assertTrue("không thấy nhánh in lại từ màn hoá đơn theo xe", at > 0);
        int end = src.indexOf("else", at + 10);
        String branch = src.substring(at, end);
        assertTrue(branch.contains("reprint = true;"));
        assertFalse(branch.contains("initDeviceConnection"));
    }

    /**
     * Xe MÌNH phải hiển thị danh sách vừa tải về, không phải bản đọc từ Room.
     *
     * <p>Đo trên tablet 16-09-2026: chọn xe khác thì in lại được, chọn chính xe của máy thì báo
     * "không có dữ liệu phiếu". Lý do: {@code ReceiptId} là trường {@code @Ignore} nên không
     * nằm trong Room, đọc từ Room ra là mất Id và không tải được phiếu về. Bản đọc từ Room chỉ
     * dùng khi không tải được (mất mạng).
     */
    @Test
    public void xeMinhHienDanhSachVuaTaiVeChuKhongPhaiBanDocTuRoom() throws IOException {
        String activity = source("src/main/java/com/megatech/fms/TruckInvoiceActivity.java");
        int at = activity.indexOf("if (ownTruck) {");
        assertTrue("không thấy nhánh xe mình", at > 0);
        String branch = activity.substring(at, activity.indexOf("} else {", at));
        assertTrue("phải dùng danh sách trả về từ synchronize()",
                branch.contains("fresh = syncFirst ? TruckInvoiceSync.synchronize()"));
        assertTrue("có bản vừa tải thì lọc trên bản đó", branch.contains("TruckInvoiceSync.filterRange(fresh"));
        assertTrue("mất mạng mới đọc bản lưu trong máy", branch.contains("getBetween(effectiveFrom, to)"));
    }

    /** Ảnh chữ ký của phiếu khác không được lọt vào thư mục Pictures (nơi đẩy phiếu đọc). */
    @Test
    public void chuKyTaiVeNamTrongCacheKhongNamTrongPictures() throws IOException {
        String src = source("src/main/java/com/megatech/fms/helpers/ReceiptReprint.java");
        assertFalse(src.contains("DIRECTORY_PICTURES"));
        assertFalse(src.contains("getExternalFilesDir"));
    }

    // ------------------------------------------------------------------ tiện ích

    private static TruckInvoice invoice(long id, long time) {
        TruckInvoice invoice = new TruckInvoice();
        invoice.setInvoiceId(id);
        invoice.setBillDate(time < 0 ? null : new Date(time));
        return invoice;
    }

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
