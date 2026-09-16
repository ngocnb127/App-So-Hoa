package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.TruckModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/**
 * Số phiếu hoá nghiệm điền sẵn cho chuyến sau lấy theo phiếu 2502 mới nhất còn lại.
 *
 * <p>Chủ dự án chốt ngày 2026-09-16 (hạng mục A3, A4): sửa một phiếu cũ KHÔNG được kéo số quay
 * ngược lại, và xoá phiếu mới nhất thì số trở về phiếu còn lại mới nhất. Trước đó app ghi số
 * sau mọi lần lưu, nên sửa phiếu 8h sau khi đã có phiếu 14h làm các chuyến sau nhận số của 8h.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class TruckFuelQcPolicyTest {

    private static final long GIO_8 = 1_789_000_000_000L;
    private static final long GIO_14 = GIO_8 + 6 * 60 * 60 * 1000L;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(34);
        setting.setTruckNo("DEMO-03");
        ((FMSApplication) context).saveSetting(setting, false);
    }

    private TruckFuelModel form(int truckId, long time, String qcNo, boolean deleted) {
        TruckFuelModel model = new TruckFuelModel();
        model.setTruckId(truckId);
        model.setTime(new Date(time));
        model.setQcNo(qcNo);
        model.setDeleted(deleted);
        return model;
    }

    @Test
    public void suaPhieuCuKhongKeoSoQuayNguocLai() {
        String qcNo = TruckFuelQcPolicy.qcNoForNextFlights(Arrays.asList(
                form(34, GIO_8, "QC-A", false),
                form(34, GIO_14, "QC-B", false)), 34);

        assertEquals("phải là số của phiếu mới nhất, dù người dùng vừa lưu phiếu nào", "QC-B", qcNo);
    }

    @Test
    public void xoaPhieuMoiNhatThiSoVeLaiPhieuConLai() {
        String qcNo = TruckFuelQcPolicy.qcNoForNextFlights(Arrays.asList(
                form(34, GIO_8, "QC-A", false),
                form(34, GIO_14, "QC-B", true)), 34);

        assertEquals("QC-A", qcNo);
    }

    @Test
    public void khongLayPhieuCuaXeKhac() {
        String qcNo = TruckFuelQcPolicy.qcNoForNextFlights(Arrays.asList(
                form(34, GIO_8, "QC-A", false),
                form(77, GIO_14, "QC-XE-KHAC", false)), 34);

        assertEquals("QC-A", qcNo);
    }

    /** Không tính được thì GIỮ NGUYÊN số hiện hành: xoá số sẽ chặn màn hình xem trước. */
    @Test
    public void khongConPhieuNaoThiGiuNguyenSoHienHanh() {
        assertNull(TruckFuelQcPolicy.qcNoForNextFlights(Collections.emptyList(), 34));
        assertNull(TruckFuelQcPolicy.qcNoForNextFlights(null, 34));
        assertNull(TruckFuelQcPolicy.qcNoForNextFlights(
                Collections.singletonList(form(34, GIO_8, "QC-A", true)), 34));
    }

    @Test
    public void phieuMoiNhatKhongCoSoThiGiuNguyenSoHienHanh() {
        assertNull(TruckFuelQcPolicy.qcNoForNextFlights(Arrays.asList(
                form(34, GIO_8, "QC-A", false),
                form(34, GIO_14, "   ", false)), 34));
    }

    /**
     * Màn hình 2502 phải đi qua luật này, không được ghi thẳng số của phiếu vừa lưu.
     *
     * <p>Quyết định nằm trong Activity/Fragment nên không tách ra hàm thuần được; test đọc mã
     * nguồn để chống hồi quy.
     */
    @Test
    public void manHinh2502KhongGhiThangSoCuaPhieuVuaLuu() throws java.io.IOException {
        String dialog = source("src/main/java/com/megatech/fms/B2502NewItemFragement.java");
        String list = source("src/main/java/com/megatech/fms/B2502Activity.java");

        org.junit.Assert.assertFalse("không được ghi thẳng số của phiếu vừa lưu",
                dialog.contains("setQCNo(model.getQcNo())"));
        org.junit.Assert.assertTrue("lưu xong phải tính lại số theo phiếu mới nhất",
                dialog.contains("DataHelper.qcNoForNextFlights("));
        org.junit.Assert.assertTrue("xoá xong phải tính lại số theo phiếu mới nhất",
                list.contains("DataHelper.qcNoForNextFlights("));
    }

    private static String source(String relative) throws java.io.IOException {
        java.io.File file = new java.io.File(relative);
        if (!file.exists()) file = new java.io.File("app/" + relative);
        if (!file.exists()) file = new java.io.File("../app/" + relative);
        org.junit.Assert.assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void khongLocTheoXeKhiChuaBietXe() {
        String qcNo = TruckFuelQcPolicy.qcNoForNextFlights(Collections.singletonList(
                form(77, GIO_14, "QC-B", false)), 0);

        assertEquals("QC-B", qcNo);
    }
}
