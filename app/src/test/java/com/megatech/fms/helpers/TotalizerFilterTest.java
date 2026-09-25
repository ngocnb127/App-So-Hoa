package com.megatech.fms.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Ca kiểm thử bộ lọc nhiễu của số tổng.
 *
 * <p>Hai yêu cầu ngược chiều nhau phải cùng đúng: một nhịp nhiễu đơn lẻ KHÔNG được đạp lên
 * số tổng, còn một mốc mới có thật thì PHẢI được nhận, nếu không số tổng đóng băng vĩnh viễn.
 */
public class TotalizerFilterTest {

    @Test
    public void giaTriDauTienLuonDuocNhan() {
        TotalizerFilter loc = new TotalizerFilter();
        // Chưa có mốc nào (0) thì không có gì để so.
        assertTrue(loc.accept(0, 999_999));
    }

    @Test
    public void nhichDeuTrongLucBomThiNhanBinhThuong() {
        TotalizerFilter loc = new TotalizerFilter();
        double moc = 5000;
        for (int i = 0; i < 100; i++) {
            double doc = moc + 8;   // ~8 đơn vị mỗi giây
            assertTrue("nhịp " + i, loc.accept(moc, doc));
            moc = doc;
        }
    }

    @Test
    public void motNhipNhieuDonLeVanBiChan() {
        TotalizerFilter loc = new TotalizerFilter();

        assertFalse(loc.accept(5000, 987_654));   // nhiễu
        assertTrue(loc.accept(5000, 5008));       // nhịp thật ngay sau đó vẫn vào
    }

    /**
     * CHỐNG HỒI QUY — ca sinh ra toàn bộ việc sửa này.
     *
     * <p>App rời đồng hồ một lúc (đổi màn hình, màn in ngắt kết nối, rớt Bluetooth) và trong
     * lúc đó xe bơm hơn 1000 đơn vị. Luật cũ viết thẳng thành một câu {@code if} nên mốc so
     * sánh không bao giờ đổi được nữa: MỌI giá trị sau đó đều bị loại, số tổng trên app đóng
     * băng vĩnh viễn trong khi đồng hồ vẫn trả số đúng.
     */
    @Test
    public void nhayXaKeoDaiThiLayLaiMocChuKhongTuKhoa() {
        TotalizerFilter loc = new TotalizerFilter();
        double mocCu = 5000;
        double soThat = 9000;           // lệch 4000 sau quãng mất kết nối

        int nhipBiLoai = 0;
        boolean nhan = false;
        for (int i = 0; i < TotalizerFilter.REBASE_AFTER; i++) {
            nhan = loc.accept(mocCu, soThat);
            if (!nhan) nhipBiLoai++;
            soThat += 8;                // đồng hồ vẫn đang chạy trong lúc chờ
        }

        assertTrue("phải lấy lại được mốc, không được khoá vĩnh viễn", nhan);
        assertTrue("không được nhận ngay nhịp đầu", nhipBiLoai > 0);
    }

    /** Lấy lại mốc phải xong trước khi MeterFieldHealth kịp kết luận trường đã chết. */
    @Test
    public void layLaiMocXongTruocKhiBiKetLuanLaChet() {
        long chuKyDocMs = 1000;
        long thoiGianLayLaiMoc = TotalizerFilter.REBASE_AFTER * chuKyDocMs;
        assertTrue("lấy lại mốc (" + thoiGianLayLaiMoc + "ms) phải nhanh hơn ngưỡng chết ("
                        + MeterFieldHealth.STALE_AFTER_MS + "ms)",
                thoiGianLayLaiMoc < MeterFieldHealth.STALE_AFTER_MS);
    }

    /**
     * Nhiễu rời rạc thì dù có nhiều lần cũng không được lấy làm mốc: mỗi giá trị một nơi,
     * không cái nào khẳng định cái nào.
     */
    @Test
    public void nhieuRoiRacKeoDaiVanKhongDuocLayLamMoc() {
        TotalizerFilter loc = new TotalizerFilter();
        double[] rac = {900_000, 12_345, 777_777, 40_000, 999_999, 3_000_000, 88_888};

        for (double v : rac)
            assertFalse("giá trị rời rạc " + v + " không được thành mốc", loc.accept(5000, v));
    }

    /** Chuỗi lệch bị cắt giữa chừng bởi một nhịp bình thường thì phải đếm lại từ đầu. */
    @Test
    public void chuoiBiCatThiDemLai() {
        TotalizerFilter loc = new TotalizerFilter();

        for (int i = 0; i < TotalizerFilter.REBASE_AFTER - 1; i++)
            assertFalse(loc.accept(5000, 9000));

        assertTrue(loc.accept(5000, 5008));      // nhịp bình thường xen vào, xoá chuỗi

        // Chuỗi cũ không được cộng dồn: nhịp lệch tiếp theo lại phải bắt đầu từ 1.
        assertFalse(loc.accept(5008, 9000));
    }

    @Test
    public void baoDuocLucNaoLaLayLaiMoc() {
        TotalizerFilter loc = new TotalizerFilter();

        assertTrue(loc.justRebased(5000, 9000));     // nhảy xa
        assertFalse(loc.justRebased(5000, 5008));    // nhích đều
        assertFalse(loc.justRebased(0, 9000));       // giá trị đầu tiên, không phải lấy lại mốc
    }

    @Test
    public void resetXoaChuoiDangDo() {
        TotalizerFilter loc = new TotalizerFilter();

        for (int i = 0; i < TotalizerFilter.REBASE_AFTER - 1; i++)
            assertFalse(loc.accept(5000, 9000));

        loc.reset();
        assertFalse("sau reset phải đếm lại từ đầu", loc.accept(5000, 9000));
    }
}
