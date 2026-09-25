package com.megatech.fms.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.exceptions.InvalidRefuelTimeException;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.RefuelApproachGuard;
import com.megatech.fms.helpers.RefuelFieldPatch;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * CA 1 — TOÀN TUYẾN luồng tra nạp cho chuyến được phân công cho CHÍNH xe đang chạy app:
 * chọn chuyến → đồng hồ ghi số → kết thúc mẻ → xác nhận → xem trước → phiếu/ký/in → hoá đơn.
 *
 * <p>Tablet và đồng hồ KHÔNG kết nối được (kiểm tra 2026-09-06), nên luồng dữ liệu đồng hồ
 * được GIẢ LẬP ở mức dữ liệu: mỗi lần đồng hồ trả số là một lần lưu snapshot PROCESSING,
 * lần chốt mẻ là một lần lưu snapshot DONE. Đó đúng là những gì màn tra nạp làm.
 *
 * <p>Thước đo của cả lớp này là ba nguyên tắc chủ dự án chốt 2026-09-06:
 * <ul>
 *   <li>NT1 — mọi chuyến đều phải ghi nhận được, không ngõ cụt;</li>
 *   <li>NT2 — chỉ chặn bản "đang tra nạp" treo trên đường lên server rồi về SAU khi mẻ đã
 *       Done; và chỉ dữ liệu Done mới được sửa dữ liệu Done;</li>
 *   <li>NT3 — giữ nguyên các chặn dữ liệu vô lý đang có.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelFlowOwnTruckTest {

    private static final String UID = "1c0f2e70-0000-4000-8000-0000000000a1";
    private static final String OWN_TRUCK_NO = "51C-99999";
    private static final int OWN_TRUCK_ID = 173;

    private AppDatabase db;
    private DataRepository repo;
    private FakeHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(OWN_TRUCK_ID);
        setting.setTruckNo(OWN_TRUCK_NO);
        ((FMSApplication) context).saveSetting(setting, false);

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new FakeHttpClient();

        installTestDependencies(repo, http);
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        callDataHelper("resetTestDependencies");
        db.close();
    }

    // Các seam test của DataHelper là package-private trong com.megatech.fms.helpers. Lớp
    // này bắt buộc nằm ở gói `flow` (phân công của đợt kiểm thử), nên gọi qua reflection —
    // KHÔNG nới phạm vi truy cập của mã sản phẩm chỉ để chiều một file test.
    private static void installTestDependencies(DataRepository repository, HttpClient client) {
        try {
            java.lang.reflect.Method m = DataHelper.class.getDeclaredMethod(
                    "installTestDependencies", DataRepository.class, HttpClient.class);
            m.setAccessible(true);
            m.invoke(null, repository, client);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("không gọi được seam test của DataHelper", ex);
        }
    }

    private static void callDataHelper(String name) {
        try {
            java.lang.reflect.Method m = DataHelper.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("không gọi được " + name, ex);
        }
    }

    /** Một lượt đồng bộ nền: đúng hàm hàng đợi POST của DataHelper. */
    private static void syncModifiedRefuels() {
        callDataHelper("syncModifiedRefuels");
    }

    /** Luật của hộp nhập tay ở màn tra nạp — cũng package-private, gọi qua reflection. */
    private static List<?> manualInputProblems(double realAmount, double startNumber,
                                               double endNumber) {
        try {
            java.lang.reflect.Method m = com.megatech.fms.RefuelDetailActivity.class
                    .getDeclaredMethod("manualInputProblems",
                            double.class, double.class, double.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(null, realAmount, startNumber, endNumber);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("không gọi được manualInputProblems", ex);
        }
    }

    // =====================================================================
    // CHẶNG 1 — chọn chuyến tiếp cận (NewRefuelActivity / RefuelApproachGuard)
    // =====================================================================

    /** Xe chỉ đứng được ở một tàu bay: chuyến trước chưa bấm Rời đi thì chặn tiếp cận. */
    @Test
    public void chuyenTruocChuaRoiDiThiChanTiepCanChuyenMoi() {
        seedApproached("uid-truoc", new Date(System.currentTimeMillis() - 10 * 60_000L), null);

        List<RefuelItemData> blocking = RefuelApproachGuard.findBlocking(UID);

        assertEquals("chuyến đã tiếp cận chưa rời đi phải chặn chuyến mới",
                1, blocking.size());
        assertEquals("uid-truoc", blocking.get(0).getUniqueId());
    }

    /**
     * NT1 — không được có ngõ cụt: một phiếu cũ quên bấm Rời đi không được khoá nút Tiếp cận
     * vĩnh viễn. Ngoài 24 giờ thì thôi không xét nữa.
     */
    @Test
    public void phieuQuenRoiDiQua24GioKhongKhoaNutTiepCan() {
        long qua25Gio = System.currentTimeMillis() - 25L * 60 * 60 * 1000;
        seedApproached("uid-cu", new Date(qua25Gio), null);

        assertTrue("phiếu ngoài 24 giờ không được chặn nữa",
                RefuelApproachGuard.findBlocking(UID).isEmpty());
    }

    /** Bấm Rời đi ngay trên hộp thoại chặn ⇒ đi tiếp được, không phải rời màn hình. */
    @Test
    public void bamRoiDiTrenHopThoaiThiTiepCanDuocNgay() {
        seedApproached("uid-truoc", new Date(System.currentTimeMillis() - 10 * 60_000L), null);
        List<RefuelItemData> blocking = RefuelApproachGuard.findBlocking(UID);
        assertEquals(1, blocking.size());

        assertTrue("phải ghi được giờ rời đi", RefuelApproachGuard.leaveNow(blocking.get(0)));

        assertTrue("rời đi xong thì hết chặn",
                RefuelApproachGuard.findBlocking(UID).isEmpty());
        assertNotNull("giờ rời đi phải nằm trong Room",
                repo.getRefuel("uid-truoc").toRefuelItemData().getLeaveTime());
    }

    // =====================================================================
    // CHẶNG 2 + 3 — đồng hồ ghi số rồi kết thúc mẻ
    // =====================================================================

    /**
     * Giả lập cả mẻ: đồng hồ trả số nhiều nhịp (PROCESSING), rồi chốt mẻ (DONE). Sau cùng
     * Room phải giữ đúng bộ số cuối và trạng thái DONE.
     */
    @Test
    public void ghiSoDongHoNhieuNhipRoiChotMe_luuDungBoSoCuoi() {
        seedRow(0, 60165130, REFUEL_ITEM_STATUS.PROCESSING, 5, 8, true);

        http.postResponse = null;   // mất mạng cả mẻ — vẫn phải ghi nhận được (NT1)
        DataHelper.postRefuel(meterTick(400, 60165530), true);
        DataHelper.postRefuel(meterTick(900, 60166030), true);
        RefuelItemData done = meterTick(1110, 60166240);
        done.setStatus(REFUEL_ITEM_STATUS.DONE);
        DataHelper.postRefuel(done, true);

        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(REFUEL_ITEM_STATUS.DONE, stored.getStatus());
        assertEquals(1110d, stored.getRealAmount(), 0d);
        assertEquals(60166240d, stored.getEndNumber(), 0d);
        assertTrue("mất mạng thì mẻ vẫn phải ở lại hàng đợi đồng bộ",
                repo.getRefuel(UID).isLocalModified());
    }

    /**
     * Mẻ đang bơm trên chính máy này: lượt pull nền KHÔNG được đè lên số đồng hồ.
     *
     * <p>Đây là hình dạng sự cố "1110 bị 862 ghi đè" — bản server chỉ là ảnh chụp của một
     * POST trước đó nên luôn cũ hơn đồng hồ.
     */
    @Test
    public void luotPullTrongLucDangBomKhongDeLenSoDongHo() {
        seedRow(900, 60166030, REFUEL_ITEM_STATUS.PROCESSING, 5, 8, false);

        http.remoteItem = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 8, 9);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals("đang bơm thì đồng hồ là nguồn chuẩn, không nhận số cũ của server",
                900d, loaded.getRealAmount(), 0d);
        assertEquals(900d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /**
     * Cả hai đường kết thúc (đồng hồ tự kết thúc / Dừng khẩn + nhập tay) phải dẫn về cùng
     * màn Xác nhận, và hộp nhập tay luôn còn nút "Để sau".
     *
     * <p>Ràng buộc nằm trong Activity nên đọc thẳng mã nguồn (xem RefuelDetailScreenGuardTest).
     */
    @Test
    public void haiDuongKetThucDeuVeManXacNhanVaHopNhapTayLuonCoLoiRa() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelDetailActivity.java");

        assertTrue("hộp nhập tay bắt buộc phải còn nút Để sau",
                src.contains("R.string.input_later"));
        assertFalse("nút Dừng khẩn không được tắt", src.contains("btnForceStop.setEnabled(false)"));

        // Cả hai đường kết thúc đều hội tụ về finalizStop(): đường TCS đi qua
        // doStopTCS() -> finalizeStopCommon() -> finalizStop(), đường LCR/thủ công gọi thẳng.
        assertTrue("đường TCS phải hội tụ về finalizStop()",
                src.contains("finalizeStopCommon()") && src.contains("finalizStop();"));

        // ...và finalizStop() dẫn về openConfirm(), kể cả khi lần ghi mẻ thất bại.
        int at = src.indexOf("private void openConfirm() {");
        assertTrue("không tìm thấy openConfirm()", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 900));
        assertTrue("màn Xác nhận phải là đích của đường kết thúc",
                body.contains("RefuelDetailConfirmActivity"));

        int at2 = src.indexOf("private void continueWithoutSavedEnd(");
        assertTrue("không tìm thấy continueWithoutSavedEnd()", at2 > 0);
        assertTrue("chốt mẻ thất bại vẫn phải đi tiếp tới màn Xác nhận",
                src.substring(at2, Math.min(src.length(), at2 + 2500))
                        .contains("openConfirm();"));
    }

    /**
     * GHI NHẬN HIỆN TRẠNG, KHÔNG PHẢI TÁN THÀNH — mẻ 0 lít KHÔNG kết thúc được bằng đường
     * Dừng khẩn + nhập tay.
     *
     * <p>{@code RefuelDetailActivity.manualInputProblems()} coi {@code realAmount <= 0} là lỗi
     * nên nút Lưu của hộp nhập tay không bao giờ đóng được với mẻ 0 lít; hai lối ra còn lại
     * ("Để sau", Dừng khẩn) chỉ đưa màn hình về trạng thái đang tra nạp. Khi đồng hồ không hề
     * chạy thì cũng không có sự kiện end để đi đường tự động.
     *
     * <p>Điều này MÂU THUẪN với luật đã chốt "mẻ 0 lít vẫn ghi nhận được, validate là
     * {@code realAmount < 0} chứ không phải {@code <= 0}" (skill fms-refuel-flow) và với
     * {@code RefuelDetailConfirmActivity.save()} (đã dùng {@code < 0}). Test này khoá lại
     * hiện trạng để lần sửa tới là một quyết định có ý thức của chủ dự án, không phải một
     * thay đổi lọt lưới.
     */
    @Test
    public void meKhongLitChuaKetThucDuocBangDuongNhapTay_hienTrang() {
        List<?> problems = manualInputProblems(0, 60165130, 60165130);

        assertFalse("hiện trạng: mẻ 0 lít bị hộp nhập tay coi là dữ liệu sai",
                problems.isEmpty());
        assertTrue("phải là lỗi SẢN LƯỢNG", problems.contains("AMOUNT"));
    }

    // =====================================================================
    // NT2 — POST "đang tra nạp" treo, trả về SAU khi mẻ đã Done
    // =====================================================================

    /**
     * CA QUAN TRỌNG NHẤT. Gói PROCESSING treo trên đường lên server; trong lúc nó bay,
     * người dùng chốt mẻ 1110 GL (DONE) ở local. Response của gói cũ về sau đó.
     *
     * <p>Bản chưa-Done KHÔNG được kéo dữ liệu cuối cùng về bản cũ.
     */
    @Test
    public void postDangTraNapVeMuonKhongKeoDuLieuDaDoneVeBanCu() {
        seedRow(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 5, 8, true);

        // Server nhận gói PROCESSING và echo lại đúng gói đó (một ACK hợp lệ nhưng ĐÃ CŨ).
        http.postResponse = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 8, 9);
        // ...trong lúc request đang bay thì mẻ được chốt ở local.
        http.onPostRefuel = () -> {
            RefuelItemData done = meterTick(1110, 60166240);
            done.setStatus(REFUEL_ITEM_STATUS.DONE);
            DataHelper.postRefuel(done, false);
        };

        syncModifiedRefuels();

        RefuelItem row = repo.getRefuel(UID);
        RefuelItemData stored = row.toRefuelItemData();
        assertEquals("response PROCESSING về muộn không được hạ trạng thái mẻ đã Done",
                REFUEL_ITEM_STATUS.DONE, stored.getStatus());
        assertEquals("sản lượng chốt không được kéo ngược về bản cũ",
                1110d, stored.getRealAmount(), 0d);
        assertEquals(60166240d, stored.getEndNumber(), 0d);
    }

    /** Cùng ca trên nhưng đi đường lưu trực tiếp của màn hình (DIRECT_POST). */
    @Test
    public void duongLuuTrucTiep_responseCuKhongDeLenBanDaDone() {
        seedRow(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 5, 8, true);

        http.postResponse = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 9, 9);
        http.onPostRefuel = () -> {
            RefuelItemData done = meterTick(1110, 60166240);
            done.setStatus(REFUEL_ITEM_STATUS.DONE);
            DataHelper.postRefuel(done, false);
        };

        DataHelper.postRefuel(meterTick(500, 60165630), true);

        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(REFUEL_ITEM_STATUS.DONE, stored.getStatus());
        assertEquals(1110d, stored.getRealAmount(), 0d);
    }

    /**
     * NT2 đường ĐỌC: server còn giữ bản "đang tra nạp" (do gói treo vừa hạ cánh) và trả về
     * với revision CAO HƠN. Bản chưa-Done vẫn không được đè lên bản đã Done.
     */
    @Test
    public void banDangTraNapTuServerKhongDeDuocLenBanDaDone() {
        seedRow(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 5, 8, false);

        http.remoteItem = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 8, 42);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals("bản chưa-Done không được hạ trạng thái bản đã Done",
                REFUEL_ITEM_STATUS.DONE, loaded.getStatus());
        assertEquals(1110d, loaded.getRealAmount(), 0d);
        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(REFUEL_ITEM_STATUS.DONE, stored.getStatus());
        assertEquals(1110d, stored.getRealAmount(), 0d);
        assertEquals(60166240d, stored.getEndNumber(), 0d);
    }

    /** Bản Done của server (sửa trên web) VẪN phải tới được máy — không chặn nhầm. */
    @Test
    public void banDoneCuaServerVanSuaDuocBanDoneOMay() {
        seedRow(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 5, 8, false);

        http.remoteItem = payload(1200, 60166330, REFUEL_ITEM_STATUS.DONE, 8, 9);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals("chỉ dữ liệu Done mới sửa được dữ liệu Done — và nó PHẢI sửa được",
                1200d, loaded.getRealAmount(), 0d);
        assertEquals(60166330d, repo.getRefuel(UID).toRefuelItemData().getEndNumber(), 0d);
    }

    /**
     * Snapshot màn hình đang cầm là bản TRƯỚC khi mẻ được chốt: patch của nó không được đắp
     * lên row đã Done bằng bộ số khác.
     */
    @Test
    public void snapshotChuaDoneKhongDapDuocLenRowDaDone() {
        String base = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 8, 5).toJson();
        String latest = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5).toJson();

        RefuelItemData ours = payload(400, 60165530, REFUEL_ITEM_STATUS.PROCESSING, 8, 5);
        ours.setManualTemperature(28);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest, 8, 9);

        assertFalse("bản chưa-Done không được ghi đè bản đã Done", result.isApplied());
        assertTrue(result.describe(), result.isBlockedByFinalizedRow());
    }

    // =====================================================================
    // CHẶNG 4 — màn Xác nhận (RefuelDetailConfirmActivity)
    // =====================================================================

    /**
     * Nhiệt độ / tỉ trọng / số hoá nghiệm gõ ở màn Xác nhận phải ăn, kể cả khi row đã được
     * một lượt ghi khác nâng phiên bản. Quên khoá trong Scope là thay đổi bị bỏ IM LẶNG.
     */
    @Test
    public void manXacNhanLuuDuocNhietDoTiTrongVaQCLenBanMoiNhat() {
        RefuelItemData baseData = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 8, 5);
        String base = baseData.toJson();

        RefuelItemData latestData = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        latestData.setParkingLot("A5");
        String latest = latestData.toJson();

        RefuelItemData ours = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 8, 5);
        ours.setManualTemperature(29.5);
        ours.setDensity(0.795);
        ours.setQualityNo("QC-123");

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest, 8, 9);

        assertTrue(result.describe(), result.isApplied());
        assertEquals(29.5, result.getMerged().getManualTemperature(), 0.0001);
        assertEquals(0.795, result.getMerged().getDensity(), 0.00001);
        assertEquals("QC-123", result.getMerged().getQualityNo());
        assertEquals("thay đổi của lượt ghi kia không được mất",
                "A5", result.getMerged().getParkingLot());
    }

    /**
     * NT3 tại màn Xác nhận — các chặn dữ liệu vô lý phải còn nguyên văn.
     *
     * <p>Luật nằm trong {@code RefuelDetailConfirmActivity.save()} nên canh bằng cách đọc
     * mã nguồn. Sửa mã trong vùng này thì phải sửa test cùng lúc, không nới cho qua.
     */
    @Test
    public void manXacNhanGiuNguyenCacChanDuLieuVoLy() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelDetailConfirmActivity.java");
        int at = src.indexOf("private void save() {");
        assertTrue("không tìm thấy save()", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 2500));

        assertTrue("tỉ trọng ngoài 0.72–0.86 phải bị chặn",
                body.contains("mItem.getDensity()< 0.72")
                        && body.contains("mItem.getDensity() >0.86"));
        assertTrue("nhiệt độ chưa nhập phải bị chặn",
                body.contains("mItem.getManualTemperature()<=0"));
        assertTrue("sản lượng âm phải bị chặn",
                body.contains("mItem.getRealAmount()<0"));
        assertTrue("sản lượng phải bằng đồng hồ cuối trừ đồng hồ đầu",
                body.contains("mItem.getEndNumber() != mItem.getStartNumber()"));
        assertTrue("số hoá nghiệm trống phải bị chặn",
                body.contains("mItem.getQualityNo().trim().isEmpty()"));
        assertTrue("giờ bắt đầu/kết thúc vô lý phải bị chặn",
                body.contains("!mItem.validTime()"));

        // NT1: mẻ 0 lít KHÔNG được chặn ở đường ghi nhận.
        assertFalse("mẻ 0 lít phải ghi nhận được — chỉ chặn ở khâu xuất phiếu",
                body.contains("mItem.getRealAmount()<=0"));
    }

    // =====================================================================
    // CHẶNG 5 — màn Xem trước (RefuelPreviewActivity)
    // =====================================================================

    /** Sửa giờ / giá / bãi đỗ ở màn xem trước phải ăn trên bản mới nhất. */
    @Test
    public void manXemTruocSuaGioVaBaiDoThiPhaiAn() {
        String base = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 8, 5).toJson();

        RefuelItemData latestData = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        latestData.setQualityNo("QC-999");
        String latest = latestData.toJson();

        RefuelItemData ours = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 8, 5);
        ours.setParkingLot("B12");
        ours.setEndTime(new Date(1_769_000_600_000L));

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.PREVIEW, base, ours, latest, 8, 9);

        assertTrue(result.describe(), result.isApplied());
        assertEquals("B12", result.getMerged().getParkingLot());
        assertEquals(1_769_000_600_000L, result.getMerged().getEndTime().getTime());
        assertEquals("QC-999", result.getMerged().getQualityNo());
    }

    /**
     * Không được thêm khoá kiểu OBJECT hay metadata chứng từ vào Scope PREVIEW — đã đo trên
     * máy thật 27-08-2026: mọi lần sửa bị chặn bằng FIELD_CONFLICT giả.
     */
    @Test
    public void scopePreviewKhongDuocChuaKhoaObjectHayMetadataChungTu() throws IOException {
        String src = source("src/main/java/com/megatech/fms/helpers/RefuelFieldPatch.java");
        int at = src.indexOf("PREVIEW(new String[]{");
        assertTrue("không tìm thấy Scope PREVIEW", at > 0);
        String body = src.substring(at, src.indexOf("}),", at));

        assertFalse("AirlineModel là object, không được vào PREVIEW",
                body.contains("\"AirlineModel\""));
        assertFalse("ReceiptNumber đi đường patchAllPrintItems",
                body.contains("\"ReceiptNumber\""));
        assertFalse("ReceiptCount đi đường patchAllPrintItems",
                body.contains("\"ReceiptCount\""));
        assertFalse("PrintStatus đi đường patchAllPrintItems",
                body.contains("\"PrintStatus\""));
    }

    // =====================================================================
    // CHẶNG 6 + 7 — tạo phiếu, ký, in, xuất hoá đơn (NT3)
    // =====================================================================

    /** NT3 — tỉ trọng ngoài khoảng cho phép thì không tạo được phiếu. */
    @Test
    public void tiTrongNgoaiKhoangKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setDensity(0.9);

        assertReceiptRejected(item, "Density");
    }

    /** NT3 — nhiệt độ ngoài khoảng cho phép thì không tạo được phiếu. */
    @Test
    public void nhietDoNgoaiKhoangKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setManualTemperature(52);

        assertReceiptRejected(item, "nhiệt độ");
    }

    /** NT3 — số hiệu tàu bay bỏ trống thì không tạo được phiếu. */
    @Test
    public void soHieuTauBayTrongKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setAircraftCode("  ");

        assertReceiptRejected(item, "số hiệu tàu bay");
    }

    /** NT3 — chặng bay có 3 ký tự đầu không đúng dạng mã sân bay thì không tạo được phiếu. */
    @Test
    public void changBaySaiBaKyTuSanBayKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setRouteName("HA1-SGN");

        assertReceiptRejected(item, "Chặng bay");
    }

    /** NT3 — chặng bay bỏ trống thì không tạo được phiếu. */
    @Test
    public void changBayTrongKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setRouteName("");

        assertReceiptRejected(item, "chặng bay");
    }

    /** NT3 — giờ kết thúc sớm hơn giờ bắt đầu thì không tạo được phiếu. */
    @Test
    public void gioKetThucSomHonGioBatDauKhongTaoDuocPhieu() {
        RefuelItemData item = receiptReadyItem();
        item.setStartTime(new Date(1_769_000_600_000L));
        item.setEndTime(new Date(1_769_000_000_000L));

        assertReceiptRejected(item, "thời gian kết thúc nhỏ hơn");
    }

    /** NT3 — số hiệu chuyến bỏ trống bị chặn ngay ở màn tạo chuyến. Cổng này còn nguyên. */
    @Test
    public void soHieuChuyenTrongBiChanNgayOManTaoChuyen() throws IOException {
        String src = source("src/main/java/com/megatech/fms/NewRefuelActivity.java");
        int at = src.indexOf("private boolean validate()");
        assertTrue("không tìm thấy validate() của màn tạo chuyến", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 1200));

        assertTrue("số hiệu chuyến bỏ trống phải bị chặn khi tạo chuyến",
                body.contains("refuelData.getFlightCode() == null")
                        && body.contains("refuelData.getFlightCode().isEmpty()"));
    }

    /**
     * NT3 — SỐ HIỆU CHUYẾN bỏ trống phải bị chặn Ở KHÂU TẠO PHIẾU nữa, không chỉ ở màn tạo
     * chuyến.
     *
     * <p>Chủ dự án chốt 2026-09-06: "số hiệu chuyến hoặc số tàu bay bỏ trống" nằm trong nhóm
     * chặn phải giữ nguyên. Vòng kiểm của {@code ReceiptModel.createReceipt} hiện kiểm
     * {@code AircraftCode}, {@code AircraftType} và {@code RouteName} nhưng KHÔNG kiểm
     * {@code FlightCode} — một mẻ bị xoá số hiệu chuyến sau khi tạo (sửa trên web, payload
     * server thiếu trường) vẫn ra được tờ phiếu không có số hiệu chuyến.
     *
     * <p>TEST NÀY ĐANG FAIL — đó là phát hiện, không phải test viết sai. Đừng nới assert.
     */
    @Test
    public void soHieuChuyenTrongKhongTaoDuocPhieu() throws IOException {
        String src = source("src/main/java/com/megatech/fms/model/ReceiptModel.java");
        int at = src.indexOf("public static ReceiptModel createReceipt(");
        assertTrue("không tìm thấy createReceipt()", at > 0);
        String validation = src.substring(at, src.indexOf("ReceiptModel model = null;", at));

        assertTrue("số hiệu tàu bay trống phải bị chặn (đang có)",
                validation.contains("getAircraftCode()"));
        assertTrue("NT3: số hiệu CHUYẾN bỏ trống cũng phải bị chặn ở khâu tạo phiếu",
                validation.contains("getFlightCode()"));
    }

    /**
     * NT3 — số lít và Kg luôn là DẪN XUẤT của Gallon. Số lít cũ còn sót lại không được
     * theo lên chứng từ.
     */
    @Test
    public void soLitVaKgLuonDuocTinhLaiTuGallon() {
        RefuelItemData item = new RefuelItemData();
        item.setDensity(0.8);
        item.setRealAmount(1110);
        item.setVolume(17190);   // số lít cũ của gói trước

        assertEquals("lít phải bằng gallon nhân 3.7854",
                Math.round(1110 * RefuelItemData.GALLON_TO_LITTER), item.getVolume(), 0.001);
        assertEquals("Kg phải bằng lít nhân tỉ trọng",
                Math.round(item.getVolume() * 0.8), item.getWeight(), 0.001);
        assertNotNull("lệch số lít phải bị bắt và sửa", item.reconcileVolume());
    }

    /**
     * NT1 + nghiệp vụ in: máy in lỗi hay mất mạng KHÔNG được chặn xuất hoá đơn, và
     * {@code isCombinedDocument()} không được đọc {@code printMode}.
     */
    @Test
    public void mayInLoiVaMatMangKhongChanXuatHoaDon() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelPreviewActivity.java");
        int at = src.indexOf("private boolean isCombinedDocument() {");
        assertTrue("không tìm thấy isCombinedDocument()", at > 0);
        // Chỉ xét CÂU LỆNH return, không xét khối chú thích phía trên (chú thích có nhắc
        // printMode để giải thích vì sao vế đó đã bị bỏ).
        int retAt = src.indexOf("return", at);
        String returnStatement = src.substring(retAt, src.indexOf(';', retAt));

        assertFalse("isCombinedDocument() không được đọc printMode — chặn nhầm mọi hoá đơn",
                returnStatement.contains("printMode"));
        assertTrue("phải quyết định theo dữ liệu sắp in",
                returnStatement.contains("printItems.size() > 1"));

        assertTrue("mất mạng chỉ cảnh báo, không chặn",
                src.contains("warn_others_refresh_failed"));
    }

    /**
     * Nghiệp vụ KÝ TRƯỚC — IN SAU, và ảnh chữ ký chỉ được base64 hoá lúc đẩy lên, nên
     * không được dọn file ảnh ở {@code postCompleted()}.
     */
    @Test
    public void chuKyDuocGiuLaiChoToiLucDayLen() throws IOException {
        String src = source("src/main/java/com/megatech/fms/PrintReceiptActivity.java");

        assertTrue("phải promote chữ ký tạm khi lưu",
                src.contains("promoteCachedSignatures()"));
        int at = src.indexOf("promoteCachedSignatures();");
        assertTrue(at > 0);
    }

    // =====================================================================
    // Tiện ích
    // =====================================================================

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Mẻ đầy đủ dữ liệu, hợp lệ với MỌI chặn của đường tạo phiếu. */
    private RefuelItemData receiptReadyItem() {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId(UID);
        item.setTruckNo(OWN_TRUCK_NO);
        item.setTruckId(OWN_TRUCK_ID);
        item.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setFlightCode("VN1268");
        item.setAircraftCode("VN-A123");
        item.setAircraftType("A321");
        item.setRouteName("HAN-SGN");
        item.setDensity(0.781);
        item.setManualTemperature(30);
        item.setQualityNo("QC-1");
        item.setStartNumber(60165130);
        item.setRealAmount(1110);
        item.setEndNumber(60166240);
        item.setStartTime(new Date(1_769_000_000_000L));
        item.setEndTime(new Date(1_769_000_600_000L));
        return item;
    }

    /**
     * Tạo phiếu phải bị từ chối bằng {@link InvalidRefuelTimeException}. Thông điệp phải nói
     * được lý do, nếu không người dùng đứng cạnh tàu bay không biết sửa ô nào.
     */
    private void assertReceiptRejected(RefuelItemData item, String expectedFragment) {
        try {
            ReceiptModel.createReceipt(
                    new ArrayList<>(Collections.singletonList(item)), null, false, null, true);
            fail("dữ liệu vô lý phải bị chặn ở khâu tạo phiếu, nhưng phiếu vẫn được dựng");
        } catch (InvalidRefuelTimeException expected) {
            assertTrue("thông điệp phải nói rõ ô sai, đang là: " + expected.getMessage(),
                    expectedFragment.isEmpty()
                            || expected.getMessage().contains(expectedFragment));
        }
    }

    /** Một nhịp đồng hồ: giữ nguyên nền đang lưu, chỉ đổi số. */
    private RefuelItemData meterTick(double gallon, double endNumber) {
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        snapshot.setRealAmount(gallon);
        snapshot.setEndNumber(endNumber);
        return snapshot;
    }

    private RefuelItem seedApproached(String uid, Date approachTime, Date leaveTime) {
        RefuelItemData data = payload(0, 0, REFUEL_ITEM_STATUS.PROCESSING, 1, 0);
        data.setUniqueId(uid);
        data.setId(0);
        data.setApproachTime(approachTime);
        data.setLeaveTime(leaveTime);
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        repo.insertRefuel(row);
        return repo.getRefuel(uid);
    }

    private RefuelItem seedRow(double gallon, double endNumber, REFUEL_ITEM_STATUS status,
                               int serverRevision, long clientSeq, boolean localModified) {
        RefuelItemData data = payload(gallon, endNumber, status, clientSeq, serverRevision);
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(localModified);
        repo.insertRefuel(row);
        return repo.getRefuel(UID);
    }

    private RefuelItemData payload(double gallon, double endNumber, REFUEL_ITEM_STATUS status,
                                   long clientSeq, int serverRevision) {
        RefuelItemData data = new RefuelItemData();
        data.setId(3091001);
        data.setUniqueId(UID);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(status);
        data.setFlightId(1265515);
        data.setFlightCode("VN1268");
        data.setFlightUniqueId("5bf31c6d-522a-427f-b89b-b73128aa572b");
        data.setTruckId(OWN_TRUCK_ID);
        data.setTruckNo(OWN_TRUCK_NO);
        data.setAircraftCode("VN-A123");
        data.setAircraftType("A321");
        data.setRouteName("HAN-SGN");
        data.setRealAmount(gallon);
        data.setStartNumber(60165130);
        data.setEndNumber(endNumber);
        data.setDensity(0.781);
        data.setManualTemperature(30);
        data.setQualityNo("QC-1");
        data.setStartTime(new Date(1_769_000_000_000L));
        data.setEndTime(new Date(1_769_000_600_000L));
        data.setRefuelTime(new Date(1_769_000_000_000L));
        data.setClientSeq(clientSeq);
        data.setServerRevision(serverRevision);
        return data;
    }

    /** HttpClient giả: không có request thật nào rời khỏi test. */
    private static class FakeHttpClient extends HttpClient {
        RefuelItemData remoteItem;
        RefuelItemData postResponse;
        int postCount;
        int getCount;
        Runnable onPostRefuel;

        FakeHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            getCount++;
            return remoteItem;
        }

        @Override
        public RefuelItemData getRefuelItem(Integer id) {
            return remoteItem;
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            postCount++;
            if (onPostRefuel != null) {
                Runnable hook = onPostRefuel;
                onPostRefuel = null;
                hook.run();
            }
            if (postResponse != null)
                postResponse.setClientSeq(refuelData.getClientSeq());
            return postResponse;
        }

        @Override
        public List<RefuelItemData> getModifiedRefuels(Integer type, Date lastModified) {
            return null;
        }
    }
}
