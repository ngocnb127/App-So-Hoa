package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Loại mẻ khỏi chứng từ phải được NGƯỜI DÙNG ĐỒNG Ý, không được tự ý.
 *
 * <p>Chủ dự án chốt 06-09-2026: trước đây máy tự loại rồi chỉ hiện Toast. Chứng từ in ra khi
 * ấy KHÁC với những mẻ người dùng đã tích chọn — mà Toast thì trôi mất và không ai phải trả
 * lời nó. Đây là lựa chọn làm ĐỔI SỐ TIỀN trên chứng từ.
 */
public class ExcludedItemsConsentTest {

    private static Date at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.SEPTEMBER, 6, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Mẻ đủ điều kiện lên chứng từ. {@code getWeight()} là dẫn xuất density × volume. */
    private static RefuelItemData complete(String truckNo, double litres) {
        RefuelItemData item = new RefuelItemData();
        item.setTruckNo(truckNo);
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setStartTime(at(9, 0));
        item.setEndTime(at(9, 20));
        item.setRealAmount(1000);
        item.setDensity(0.79);
        item.setManualTemperature(30);
        item.setQualityNo("QC-1");
        item.setVolume(litres);
        return item;
    }

    @Test
    public void keTenDungNhungGiThieu() {
        RefuelItemData item = complete("DEMO 02", 1500);
        item.setDensity(0);
        item.setManualTemperature(0);

        String missing = OthersFreshness.missingForDocument(item);

        assertTrue(missing, missing.contains("tỉ trọng"));
        assertTrue(missing, missing.contains("nhiệt độ"));
        assertFalse("số hoá nghiệm đã có thì không được kể là thiếu",
                missing.contains("số hoá nghiệm"));
    }

    /** Danh sách kể ra phải khớp đúng luật loại — không nói thiếu thứ mà máy vẫn cho qua. */
    @Test
    public void keTenThieuKhopVoiLuatLoai() {
        RefuelItemData ok = complete("DEMO 02", 1500);
        assertTrue(OthersFreshness.printableForDocument(ok));
        assertEquals("mẻ đủ điều kiện thì không thiếu gì",
                "", OthersFreshness.missingForDocument(ok));

        RefuelItemData thieu = complete("DEMO 02", 1500);
        thieu.setQualityNo("");
        assertFalse(OthersFreshness.printableForDocument(thieu));
        assertFalse("mẻ bị loại thì phải kể được lý do",
                OthersFreshness.missingForDocument(thieu).isEmpty());
    }

    /** Câu hỏi phải nói được XE NÀO, BAO NHIÊU KG, THIẾU GÌ — đủ để người dùng quyết. */
    @Test
    public void moTaMeBiLoaiPhaiDuXeKgVaThieuGi() {
        RefuelItemData mine = complete("DEMO-03", 2000);
        RefuelItemData other = complete("DEMO 02", 1500);
        other.setDensity(0);

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mine, other), Arrays.asList(false, true));

        assertTrue(partition.hasExclusion());
        List<String> lines = partition.describeExcluded();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("DEMO 02"));
        // getVolume() là số DẪN XUẤT từ realAmount (gallon → lít), không đọc field volume.
        assertTrue(lines.get(0), lines.get(0).contains("3785"));
        assertTrue(lines.get(0), lines.get(0).contains("lít"));
        assertTrue(lines.get(0), lines.get(0).contains("tỉ trọng"));
        assertFalse("mẻ thiếu tỉ trọng thì Kg tính ra 0 — KHÔNG được nói 0 Kg cho một mẻ có thật",
                lines.get(0).contains("0 Kg"));
        assertEquals("tổng lít bị bỏ ra ngoài chứng từ phải nói ra được",
                3785d, partition.excludedVolume(), 0.001);
    }

    /** Mẻ của CHÍNH xe này không bao giờ bị loại — người dùng sửa được nó ngay tại chỗ. */
    @Test
    public void meCuaChinhXeNayKhongBaoGioBiLoai() {
        RefuelItemData mine = complete("DEMO-03", 2000);
        mine.setDensity(0);
        RefuelItemData other = complete("DEMO 02", 1500);

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mine, other), Arrays.asList(false, true));

        assertFalse("mẻ xe mình thiếu dữ liệu vẫn phải ở lại để đường kiểm tra cũ báo lỗi",
                partition.excluded.contains(mine));
    }

    // ------------------------------------------------------------ ràng buộc màn hình

    private static String previewSource() throws IOException {
        String rel = "src/main/java/com/megatech/fms/RefuelPreviewActivity.java";
        File file = new File(rel);
        if (!file.exists()) file = new File("app/" + rel);
        if (!file.exists()) file = new File("../app/" + rel);
        assertTrue("không tìm thấy mã nguồn màn xem trước", file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Loại mẻ phải đi qua hộp thoại HỎI, và hộp thoại đó phải là chữ ĐỎ IN ĐẬM.
     *
     * <p>Canh cả ba đường xuất chứng từ: in phiếu, xem trước, xuất hoá đơn. Bỏ sót một đường
     * là đường đó lại âm thầm loại mẻ như cũ.
     */
    @Test
    public void baDuongXuatChungTuDeuPhaiHoiTruocKhiLoaiMe() throws IOException {
        String src = previewSource();

        assertFalse("không được còn đường tự loại rồi chỉ Toast",
                src.contains("private void excludeIneligibleForeignPrintItems()"));

        int asks = 0;
        int at = 0;
        while (true) {
            int next = src.indexOf("confirmExcludedForeignPrintItemsThen(", at);
            if (next < 0) break;
            asks++;
            at = next + 1;
        }
        assertTrue("phải có 1 khai báo + 3 lượt gọi (in phiếu, xem trước, xuất hoá đơn),"
                + " đếm được " + asks, asks >= 4);

        int decl = src.indexOf("private void confirmExcludedForeignPrintItemsThen(");
        String body = src.substring(decl, Math.min(src.length(), decl + 3000));
        assertTrue("cảnh báo phải là chữ ĐỎ", body.contains("#D32F2F"));
        assertTrue("cảnh báo phải IN ĐẬM", body.contains("<b>"));
        assertTrue("phải là câu hỏi có nút đồng ý", body.contains("setPositiveButton"));
        assertTrue("phải có lối quay lại kiểm tra", body.contains("setNegativeButton"));
        assertTrue("không được đóng bằng cách bấm ra ngoài", body.contains("setCancelable(false)"));
    }

    /** Lượt loại ngầm cũ phải biến mất khỏi đường kiểm tra, nếu không mẻ bị loại hai lần. */
    @Test
    public void duongKiemTraKhongDuocTuLoaiMeNua() throws IOException {
        String src = previewSource();
        int at = src.indexOf("private boolean blockInvalidSelectedDocumentItems()");
        assertTrue(at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 2500));
        assertFalse("đường kiểm tra không được tự loại mẻ — việc đó đã được hỏi trước rồi",
                body.contains("excludeIneligibleForeignItems("));
    }

    // -------------------------------------------- trường rơi ngoài scope chỉ CẢNH BÁO

    /**
     * Trường ngoài {@code Scope.PREVIEW} phải kể tên được để cảnh báo.
     *
     * <p>Bốn ô kiểm tra thiết bị BM2508 và mã sản phẩm nằm ngoài scope: lưu cả gói bị chặn
     * thì lượt thử lại bằng patch bỏ im lặng chúng đi.
     *
     * <p>Lưu ý: {@code setBM2508BondingCable()} và ba setter anh em KHÔNG ghi vào bốn field
     * boolean cùng tên — chúng dồn bit vào {@code BM2508Result}. Bốn field boolean đó là
     * field chết. Nên thứ thật sự phải cảnh báo là {@code BM2508Result}.
     */
    @Test
    public void keTenTruongSeBiPatchBoQua() {
        RefuelItemData base = complete("DEMO-03", 2000);
        String baseJson = base.toJson();

        RefuelItemData edited = RefuelItemData.fromJson(baseJson);
        edited.setBM2508BondingCable(true);
        edited.setProductId(99);
        edited.setDensity(0.81); // trong scope — KHÔNG được kể là bị bỏ

        List<String> dropped = RefuelFieldPatch.droppedKeysOutsideScope(
                RefuelFieldPatch.Scope.PREVIEW, baseJson, edited);

        assertTrue("ô kiểm tra thiết bị đi qua BM2508Result, phải cảnh báo được",
                dropped.contains("BM2508Result"));
        assertTrue(dropped.toString(), dropped.contains("ProductId"));
        assertFalse("trường nằm TRONG scope thì patch ghi được, không được cảnh báo nhầm",
                dropped.contains("Density"));
    }

    @Test
    public void khongSuaGiThiKhongCanhBaoGi() {
        RefuelItemData base = complete("DEMO-03", 2000);
        String baseJson = base.toJson();
        RefuelItemData same = RefuelItemData.fromJson(baseJson);

        assertEquals("không sửa gì thì không được hiện cảnh báo",
                new ArrayList<String>(),
                RefuelFieldPatch.droppedKeysOutsideScope(
                        RefuelFieldPatch.Scope.PREVIEW, baseJson, same));
    }
}
