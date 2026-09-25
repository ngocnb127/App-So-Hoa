package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Phiếu của xe khác tải từ server phải dựng được ZPL y như phiếu trong máy.
 *
 * <p>Server trả JSON kiểu .NET (PascalCase) và có thể thiếu trường chữ. ZPL ghép thẳng chuỗi
 * nên trường thiếu sẽ in ra chữ "null"; tên khách thiếu thì dựng ZPL văng NPE.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class ReceiptReprintZplTest {

    /**
     * Đúng hình dạng {@code GET api/receipts/{id}} trả về (đo 2026-09-16, phiếu Id 1813107):
     * đủ số phiếu, ngày, giờ, khách, chuyến, tàu bay, tổng lượng và từng mẻ — nhưng
     * {@code QualityNo} và {@code IsFHS} vẫn null vì phép chiếu phía server chưa gán.
     */
    private static final String SERVER_JSON = "{"
            + "\"Id\":1813107,\"Number\":\"36050VCT\",\"Date\":\"2026-09-15T00:00:00\","
            + "\"StartTime\":\"2026-09-15T13:10:00\",\"EndTime\":\"2026-09-15T13:31:30\","
            + "\"CustomerName\":\"HANG HANG KHONG ABC\",\"FlightCode\":\"VN1234\","
            + "\"AircraftCode\":\"VN-A321\",\"AircraftType\":\"A321\",\"RouteName\":\"HAN-SGN\","
            + "\"QualityNo\":null,\"IsFHS\":null,\"PCode\":null,"
            + "\"SignaturePath\":null,\"SellerPath\":null,\"Signature\":null,\"SellerSignature\":null,"
            + "\"Gallon\":1896.0,\"Volume\":7177.0,\"Weight\":5612.0,\"IsReturn\":false,"
            + "\"Items\":[{\"TruckId\":2,\"TruckNo\":\"HAN3-20-7002\",\"StartNumber\":7550855.0,"
            + "\"EndNumber\":7552751.0,\"Temperature\":32.0,\"Density\":0.782,"
            + "\"Gallon\":1896.0,\"Volume\":7177.0,\"Weight\":5612.0,\"QualityNo\":null}]"
            + "}";

    /** Cùng phiếu đó nhưng server đã gán hai trường còn thiếu. */
    private static final String SERVER_JSON_DU_TRUONG =
            SERVER_JSON.replace("\"QualityNo\":null,\"IsFHS\":null", "\"QualityNo\":\"QC-2026-09\",\"IsFHS\":true");

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckNo("HAN3-20-7002");
        setting.setTruckId(7002);
        ((FMSApplication) context).saveSetting(setting, false);
        SharedPreferences prefs = context.getSharedPreferences("FMS", Context.MODE_PRIVATE);
        prefs.edit().putInt("USER_ID", 5).putString("TOKEN", "t")
                .putString("INVOICE_NAME", "Skypec").commit();
    }

    private static ReceiptModel fromServer() {
        ReceiptModel model = new ReceiptAPI().gson.fromJson(SERVER_JSON, ReceiptModel.class);
        assertNotNull(model);
        ReceiptReprint.normalizeForPrint(model);
        return model;
    }

    @Test
    public void phieuTuServerDungDuocZplVoiDungSoPhieuVaTungMe() {
        String zpl = fromServer().createThermalText();

        assertTrue(zpl.contains("Receipt No. : 36050VCT"));
        assertTrue(zpl.contains("HAN3-20-7002"));
        assertTrue("số đồng hồ đầu mẻ phải in đúng", zpl.contains("^FD7550855^FS"));
        assertTrue("số đồng hồ cuối mẻ phải in đúng", zpl.contains("^FD7552751^FS"));
        assertTrue(zpl.contains("^FD1896^FS"));
        assertFalse("trường server để trống không được in ra chữ null", zpl.contains("^FDnull^FS"));
    }

    /**
     * Hiện trạng còn thiếu, ghi lại để không ai tưởng là đã xong: server chưa gán
     * {@code QualityNo} và {@code IsFHS} nên phiếu in lại của XE KHÁC bỏ trống dòng Cert No.
     * và luôn ghi "Refueler". Server gán hai trường đó thì test dưới đây phải đổi theo.
     */
    @Test
    public void serverChuaTraCertNoVaFhsThiPhieuInLaiThieuHaiDongDo() {
        String zpl = fromServer().createThermalText();

        assertTrue("nhãn Cert No. vẫn in, chỉ trống giá trị", zpl.contains("Cert No."));
        assertFalse("không có số chứng chỉ thì không được bịa", zpl.contains("QC-"));
        assertTrue("IsFHS null bị hiểu thành Refueler", zpl.contains("^FDRefueler^FS"));
    }

    /** Khi server gán đủ hai trường, phiếu in lại có Cert No. và đúng cách nạp. */
    @Test
    public void serverTraDuTruongThiPhieuInLaiCoCertNoVaFhs() {
        ReceiptModel model = new ReceiptAPI().gson.fromJson(SERVER_JSON_DU_TRUONG, ReceiptModel.class);
        ReceiptReprint.normalizeForPrint(model);

        String zpl = model.createThermalText();

        assertTrue(zpl.contains("^FDQC-2026-09^FS"));
        assertTrue(zpl.contains("^FDFHS^FS"));
    }

    /**
     * Không có ảnh chữ ký thì ZPL KHÔNG được gọi ảnh trong máy in — ảnh còn trong bộ nhớ E: là
     * chữ ký của lần in trước, tức của một khách khác.
     */
    @Test
    public void khongCoChuKyThiKhongGoiAnhConLaiTrongMayIn() {
        ReceiptModel model = fromServer();
        model.setSignaturePath(null);
        model.setSellerSignaturePath(null);

        String zpl = model.createThermalText();

        assertFalse(zpl.contains("E:BUYER.GRF"));
        assertFalse(zpl.contains("E:SELLER.GRF"));
    }

    @Test
    public void coChuKyTaiVeThiZplGoiAnhChuKy() {
        ReceiptModel model = fromServer();
        model.setSignaturePath("/cache/reprint_signatures/36050VCT_BUYER.jpg");
        model.setSellerSignaturePath("/cache/reprint_signatures/36050VCT_SELLER.jpg");

        String zpl = model.createThermalText();

        assertTrue(zpl.contains("^XGE:BUYER.GRF,1,1^FS"));
        assertTrue(zpl.contains("^XGE:SELLER.GRF,1,1^FS"));
    }

    /** Phiếu đi qua Intent dưới dạng JSON: đường dẫn chữ ký tải về phải còn nguyên. */
    @Test
    public void quaIntentVanGiuDuongDanChuKyTaiVe() {
        ReceiptModel model = fromServer();
        model.setSignaturePath("/cache/reprint_signatures/36050VCT_BUYER.jpg");

        ReceiptModel back = ReceiptModel.fromJson(model.toJson());

        assertEquals("36050VCT", back.getNumber());
        assertEquals("/cache/reprint_signatures/36050VCT_BUYER.jpg", back.getSignaturePath());
    }
}
