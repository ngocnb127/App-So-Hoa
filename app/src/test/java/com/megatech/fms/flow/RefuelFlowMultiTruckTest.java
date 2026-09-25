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
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.OthersFreshness;
import com.megatech.fms.helpers.RefuelFieldPatch;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CA 3 — chuyến được phân công NHIỀU XE, trong đó có xe đang chạy app.
 *
 * <p>Xe cuối cùng xuất hàng là xe CHỐT: nó gộp mẻ của mọi xe rồi xuất phiếu và hoá đơn.
 * Thiếu một mẻ trong tập gộp là hoá đơn sai số tiền, nên toàn bộ lớp này soi đúng một câu
 * hỏi: <b>tập mẻ sắp in có đủ và có đúng bản mới nhất không</b>.
 *
 * <p>Ba nguyên tắc chủ dự án chốt 2026-09-06 là thước đo:
 * <ul>
 *   <li>NT1 — mọi xe trong chuyến đều ghi nhận được mẻ của mình;</li>
 *   <li>NT2 — chỉ chặn bản ghi "đang tra nạp" treo trên đường lên server rồi trả về SAU khi
 *       mẻ đã Done. Hệ quả: <b>chỉ dữ liệu Done mới được sửa dữ liệu Done trước đó</b>;</li>
 *   <li>NT3 — giữ nguyên các chặn dữ liệu vô lý.</li>
 * </ul>
 *
 * <p>Tablet không nối được (kiểm tra 2026-09-06) nên toàn bộ luồng đồng hồ/mạng được giả lập
 * bằng Room in-memory và một {@link HttpClient} giả — đúng cách làm đã chốt cho đợt này.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class RefuelFlowMultiTruckTest {

    private static final String OWN_TRUCK = "HAN3-20-7002";
    private static final int OWN_TRUCK_ID = 7002;
    private static final String TRUCK_B = "HAN3-20-7012";
    private static final int TRUCK_B_ID = 7012;
    private static final String TRUCK_C = "HAN3-20-7009";
    private static final int TRUCK_C_ID = 7009;

    private static final int FLIGHT_ID = 621;
    private static final String FLIGHT_UID = "flight-3s621";

    private AppDatabase db;
    private DataRepository repo;
    private FakeHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckNo(OWN_TRUCK);
        setting.setTruckId(OWN_TRUCK_ID);
        setting.setReceiptCode("0101");
        setting.setReceiptCount(1);
        ((FMSApplication) context).saveSetting(setting, false);

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new FakeHttpClient();
        installTestDependencies(repo, http);
        invokeHook("clearRefuelPullCursorForTesting");
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        invokeHook("resetTestDependencies");
        db.close();
    }

    /**
     * Các móc tiêm phụ thuộc của {@code DataHelper} là package-private trong
     * {@code com.megatech.fms.helpers}; lớp test này bắt buộc nằm ở gói {@code flow} nên gọi
     * qua reflection. KHÔNG sửa mã sản phẩm để nới quyền truy cập.
     */
    private static void installTestDependencies(DataRepository testRepo, HttpClient testHttp) {
        try {
            java.lang.reflect.Method method = DataHelper.class.getDeclaredMethod(
                    "installTestDependencies", DataRepository.class, HttpClient.class);
            method.setAccessible(true);
            method.invoke(null, testRepo, testHttp);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Không tiêm được phụ thuộc test", ex);
        }
    }

    private static void invokeHook(String name) {
        try {
            java.lang.reflect.Method method = DataHelper.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Không gọi được móc test " + name, ex);
        }
    }

    // =====================================================================================
    // NT1 — mọi xe trong chuyến đều ghi nhận được mẻ của mình
    // =====================================================================================

    /**
     * NT1. Ba xe cùng bơm một chuyến: xe này ghi mẻ của mình, hai xe kia về qua {@code Others}.
     * Màn hình xem trước phải thấy đủ ba mẻ, không mẻ nào bị bỏ.
     */
    @Test
    public void moiXeTrongChuyenDeuGhiNhanDuocMeCuaMinh() throws Exception {
        RefuelItemData own = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4);
        seedOwn(own);

        RefuelItemData b = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 4);
        RefuelItemData c = done("c-1", TRUCK_C, TRUCK_C_ID, 980, 4);
        publishRoot(own, b, c);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own-1");

        assertNotNull(loaded.item);
        assertFalse("dữ liệu ba xe về đủ thì không được báo thiếu", loaded.others.hasFailure());
        assertEquals("mẻ của hai xe khác phải có mặt trong tập gộp",
                2, loaded.item.getOthers().size());
        assertEquals(Arrays.asList("b-1", "c-1"), repo.getRemoteOthersMembership("own-1"));

        List<RefuelItemData> printItems = combinedPrintItems(loaded);
        assertEquals("phiếu gộp phải đứng trên đủ 3 mẻ", 3, printItems.size());
        assertEquals("tổng gallon phải là tổng của cả ba xe",
                1416d + 2100d + 980d, sumGallon(printItems), 0d);
        assertEquals("mọi mẻ đều đủ điều kiện lên chứng từ",
                3, duDieuKien(printItems).size());
    }

    /**
     * NT3 tại phiếu GỘP: tổng lít, tổng Kg và tổng Gallon phải là tổng của đúng các dòng in,
     * và mỗi dòng phải giữ bất biến {@code lít = round(gallon × 3,7854)},
     * {@code kg = round(lít × tỉ trọng)}. Sai một dòng là sai số tiền trên hoá đơn.
     */
    @Test
    public void tongLitKgGallonCuaPhieuGopPhaiNhatQuanVoiTungDong() {
        List<RefuelItemData> items = new ArrayList<>(Arrays.asList(
                printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786),
                printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786),
                printable("c-1", TRUCK_C, TRUCK_C_ID, 980, 0.786)));
        items.get(0).setAirlineModel(airline());

        ReceiptModel receipt = ReceiptModel.createReceipt(items, null, false, null, false);

        assertEquals("phiếu gộp phải có đủ 3 dòng", 3, receipt.getItems().size());

        double gallon = 0, litre = 0, kilo = 0;
        for (RefuelItemData item : items) {
            gallon += item.getRealAmount();
            litre += item.getVolume();
            kilo += item.getWeight();
            assertEquals("số lít của mẻ phải suy từ gallon",
                    Math.round(item.getRealAmount() * RefuelItemData.GALLON_TO_LITTER),
                    item.getVolume(), 0d);
            assertEquals("khối lượng của mẻ phải suy từ số lít và tỉ trọng",
                    Math.round(item.getVolume() * item.getDensity()), item.getWeight(), 0d);
            assertEquals("sản lượng phải bằng hiệu số đồng hồ",
                    item.getRealAmount(), item.getEndNumber() - item.getStartNumber(), 0.5d);
        }

        assertEquals("tổng Gallon của phiếu gộp lệch tổng các mẻ",
                gallon, receipt.getGallon(), 0d);
        assertEquals("tổng lít của phiếu gộp lệch tổng các mẻ",
                litre, receipt.getVolume(), 0d);
        assertEquals("tổng Kg của phiếu gộp lệch tổng các mẻ",
                kilo, receipt.getWeight(), 0d);

        OthersFreshness.DocumentTotals totals = OthersFreshness.documentTotals(items);
        assertEquals("hộp cảnh báo phải nói đúng số mẻ sẽ in", 3, totals.count);
        assertEquals("hộp cảnh báo phải nói đúng tổng lít sẽ in", litre, totals.litres, 0d);
        assertEquals("hộp cảnh báo phải nói đúng tổng Kg sẽ in", kilo, totals.kilos, 0d);
    }

    /**
     * Một mẻ rụng khỏi phiếu gộp là mất đúng phần tiền của mẻ đó. Test này cố định quan hệ
     * đó bằng con số để mọi thay đổi làm rụng mẻ đều lộ ra ở tổng.
     */
    @Test
    public void meRungKhoiPhieuGopLamTongThieuDungPhanCuaMeDo() {
        RefuelItemData own = printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786);
        own.setAirlineModel(airline());
        RefuelItemData b = printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786);
        RefuelItemData c = printable("c-1", TRUCK_C, TRUCK_C_ID, 980, 0.786);

        ReceiptModel full = ReceiptModel.createReceipt(
                new ArrayList<>(Arrays.asList(own, b, c)), null, false, null, false);
        ReceiptModel missing = ReceiptModel.createReceipt(
                new ArrayList<>(Arrays.asList(own, b)), null, false, null, false);

        assertEquals("mất mẻ của xe C phải làm tổng thiếu đúng phần của C",
                c.getWeight(), full.getWeight() - missing.getWeight(), 0d);
        assertTrue("phiếu thiếu mẻ vẫn dựng được — nên KHÔNG có đường chặn nào tự phát hiện,"
                + " chỉ có cảnh báo trước đó mới cứu được", missing.getItems().size() == 2);
    }

    // =====================================================================================
    // Lấy dữ liệu xe khác thất bại / payload xấu
    // =====================================================================================

    /**
     * Payload xấu (kết quả làm mới XẤU HƠN bản đang có) không được nhận. Đây là luật giữ cho
     * mẻ không rụng khỏi phiếu gộp trong khi cờ cũ vẫn báo "đủ dữ liệu".
     */
    @Test
    public void ketQuaLamMoiXauHonKhongDuocNhanDeMeKhongRungKhoiPhieuGop() {
        assertFalse("đang đủ dữ liệu mà lượt mới thiếu ⇒ phải giữ nguyên bản cũ",
                OthersFreshness.adoptRefreshResult(false, true, true));
        assertFalse("refresh lỗi ⇒ không nhận gì cả",
                OthersFreshness.adoptRefreshResult(false, false, false));
        assertTrue("lượt mới tốt hơn hoặc bằng thì nhận",
                OthersFreshness.adoptRefreshResult(true, true, true));
        assertTrue(OthersFreshness.adoptRefreshResult(true, true, false));

        // Và luật ấy phải CHẶN được đúng hậu quả: nếu nhận payload thiếu mẻ thì tổng tụt.
        List<RefuelItemData> duDuLieu = new ArrayList<>(Arrays.asList(
                printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786),
                printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786),
                printable("c-1", TRUCK_C, TRUCK_C_ID, 980, 0.786)));
        List<RefuelItemData> payloadXau = duDuLieu.subList(0, 2);
        assertTrue("payload xấu làm tổng nhỏ hơn — chính là lý do không được nhận",
                OthersFreshness.documentTotals(payloadXau).kilos
                        < OthersFreshness.documentTotals(duDuLieu).kilos);
    }

    /**
     * Lấy dữ liệu xe khác THẤT BẠI MỘT PHẦN: root không mang collection đầy đủ và một xe
     * không lấy được. Phải CẢNH BÁO (gate WARN) chứ không chặn, và dữ liệu cũ của xe đó
     * không được xoá khỏi máy.
     */
    @Test
    public void layDuLieuXeKhacThatBaiMotPhanThiCanhBaoChuKhongChan() throws Exception {
        RefuelItemData own = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4);
        seedOwn(own);

        RefuelItemData bCu = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 4);
        bCu.setRawJson(rawPayload(bCu));
        RefuelItemData cCu = done("c-1", TRUCK_C, TRUCK_C_ID, 980, 4);
        cCu.setRawJson(rawPayload(cCu));
        publishRoot(own, bCu, cCu);
        assertFalse(DataHelper.loadRefuelForPreview("own-1").others.hasFailure());

        // Lượt sau: endpoint trả root KHÔNG có collection, và xe C mất sóng.
        RefuelItemData rootLegacy = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 5);
        rootLegacy.setRawJson(rawPayload(rootLegacy));
        http.responses.put("own-1", rootLegacy);
        RefuelItemData bMoi = done("b-1", TRUCK_B, TRUCK_B_ID, 2250, 5);
        bMoi.setRawJson(rawPayload(bMoi));
        http.responses.put("b-1", bMoi);
        http.responses.put("c-1", null);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own-1");

        assertTrue("thiếu dữ liệu một xe thì phải báo có thất bại", loaded.others.hasFailure());
        assertEquals("cổng trước khi dựng chứng từ chỉ được CẢNH BÁO, không chặn",
                OthersFreshness.IncompleteOthersGate.WARN,
                OthersFreshness.gateForIncompleteOthers(true, loaded.others.hasFailure()));
        assertNotNull("mẻ xe C mất sóng vẫn phải còn trong máy để in được",
                repo.getRefuel("c-1"));
        assertEquals("mẻ xe C giữ nguyên bản cũ, không bị xoá trắng",
                980d, repo.getRefuel("c-1").toRefuelItemData().getRealAmount(), 0d);
    }

    /**
     * Mẻ xe khác chưa đủ điều kiện in thì bị LOẠI kèm cảnh báo, không chặn nút — nhưng phải
     * loại đúng mẻ đó và giữ nguyên mẻ của chính xe này.
     */
    @Test
    public void meXeKhacChuaDuDieuKienBiLoaiConMeXeMinhGiuNguyen() {
        RefuelItemData own = printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786);
        RefuelItemData bDangChay = printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786);
        bDangChay.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        RefuelItemData c = printable("c-1", TRUCK_C, TRUCK_C_ID, 980, 0.786);

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(own, bDangChay, c),
                Arrays.asList(false, true, true));

        assertEquals(2, partition.kept.size());
        assertEquals(Collections.singletonList(TRUCK_B), partition.excludedTruckNumbers());
        assertTrue("mẻ của chính xe này không bao giờ bị loại ở bước này",
                partition.kept.contains(own));
    }

    // =====================================================================================
    // NT2 — POST "đang tra nạp" treo, trả về SAU khi mẻ đã Done
    // =====================================================================================

    /**
     * NT2, đúng ca mô tả: xe A đang tra nạp, gói PROCESSING treo trên đường lên server. Mẻ đã
     * kết thúc thành DONE 1416 GL. Gói treo mới quay về mang bản 500 GL cũ.
     *
     * <p>Trong lúc đó xe B đang gộp để xuất hoá đơn, nên tổng cuối tuyệt đối không được kéo
     * về bản cũ.
     */
    @Test
    public void postDangTraNapTraVeSauKhiMeDaDoneKhongKeoTongVeBanCu() {
        RefuelItemData dangChay = processing("own-1", OWN_TRUCK, OWN_TRUCK_ID, 500, 4);
        seedOwn(dangChay);

        // Ảnh chụp mà gói treo đang cầm — chụp TRƯỚC khi mẻ kết thúc.
        RefuelItemData goiTreo = repo.getRefuel("own-1").toRefuelItemData();

        // Mẻ kết thúc: DONE với bộ số chốt khác hẳn.
        RefuelItemData ketThuc = repo.getRefuel("own-1").toRefuelItemData();
        ketThuc.setStatus(REFUEL_ITEM_STATUS.DONE);
        ketThuc.setRealAmount(1416);
        ketThuc.setEndNumber(ketThuc.getStartNumber() + 1416);
        ketThuc.setEndTime(new Date(1_777_300_600_000L));
        http.postResponse = null; // offline: ghi Room rồi xếp hàng gửi
        RefuelItemData saved = DataHelper.postRefuel(ketThuc, true);
        assertTrue("chốt mẻ phải ghi nhận được (NT2: LUÔN ghi nhận dữ liệu Done)",
                RefuelItemData.isCommitted(saved));
        assertEquals(1416d, repo.getRefuel("own-1").toRefuelItemData().getRealAmount(), 0d);

        // Gói treo quay về: vẫn PROCESSING, vẫn 500 GL, đứng trên nền đã cũ.
        RefuelItemData quayVe = DataHelper.postRefuel(goiTreo, true);

        assertFalse("bản chưa-Done không bao giờ được đè lên bản đã Done",
                RefuelItemData.isCommitted(quayVe));
        RefuelItemData sauCung = repo.getRefuel("own-1").toRefuelItemData();
        assertEquals("tổng cuối bị kéo về bản cũ — vi phạm NT2",
                1416d, sauCung.getRealAmount(), 0d);
        assertEquals("trạng thái Done bị mở lại — vi phạm NT2",
                REFUEL_ITEM_STATUS.DONE, sauCung.getStatus());
    }

    /**
     * NT2 phía dữ liệu xe khác: xe B đã Done 2100 GL. Một bản "đang tra nạp" của chính xe B
     * quay về (server echo cùng revision) không được mở lại mẻ đã chốt — nếu mở lại thì mẻ
     * tụt khỏi {@code eligibleForDocument} và RỤNG khỏi phiếu gộp.
     */
    @Test
    public void banDangTraNapCungRevisionKhongDuocMoLaiMeDaDoneCuaXeKhac() throws Exception {
        RefuelItemData bDone = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 7);
        bDone.setRawJson(rawPayload(bDone));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(bDone));

        RefuelItemData bTreo = processing("b-1", TRUCK_B, TRUCK_B_ID, 800, 7);
        bTreo.setRawJson(rawPayload(bTreo));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(bTreo));

        RefuelItemData sau = repo.getRefuel("b-1").toRefuelItemData();
        assertEquals("bản chưa-Done đè lên bản Done — vi phạm NT2",
                REFUEL_ITEM_STATUS.DONE, sau.getStatus());
        assertEquals(2100d, sau.getRealAmount(), 0d);
        assertTrue("mẻ vẫn phải đủ điều kiện vào phiếu gộp",
                OthersFreshness.eligibleForDocument(sau));
    }

    /**
     * NT2 nói tuyệt đối: <b>chỉ dữ liệu Done mới được sửa dữ liệu Done trước đó</b>.
     *
     * <p>Ca hiểm nhất của CA 3: gói PROCESSING treo của xe B được SERVER xử lý SAU gói Done,
     * nên bản server mới nhất vừa là "đang tra nạp" vừa mang revision CAO HƠN. Lượt pull kế
     * tiếp kéo đúng bản đó về trong lúc xe này đang gộp để xuất hoá đơn.
     *
     * <p>Nếu bản chưa-Done đó được nhận thì mẻ của xe B tụt khỏi {@code eligibleForDocument}
     * và rụng khỏi phiếu gộp — hoá đơn thiếu tiền của cả một xe.
     */
    @Test
    public void banDangTraNapRevisionCaoHonVanKhongDuocMoLaiMeDaDone() throws Exception {
        RefuelItemData bDone = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 7);
        bDone.setRawJson(rawPayload(bDone));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(bDone));

        RefuelItemData bTreoXuLySau = processing("b-1", TRUCK_B, TRUCK_B_ID, 800, 8);
        bTreoXuLySau.setDateUpdated(new Date(9_000_000));
        bTreoXuLySau.setRawJson(rawPayload(bTreoXuLySau));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(bTreoXuLySau));

        RefuelItemData sau = repo.getRefuel("b-1").toRefuelItemData();
        assertEquals("gói 'đang tra nạp' treo được server xử lý sau vẫn mở lại được mẻ đã"
                        + " Done — vi phạm NT2 (chỉ dữ liệu Done mới sửa được dữ liệu Done)",
                REFUEL_ITEM_STATUS.DONE, sau.getStatus());
        assertEquals("sản lượng bị kéo về bản cũ", 2100d, sau.getRealAmount(), 0d);
    }

    /**
     * Hệ quả in ra giấy của ca trên, nói bằng con số: nếu mẻ xe B bị mở lại thì phiếu gộp
     * mất đúng phần Kg của xe B.
     */
    @Test
    public void meBiMoLaiThiPhieuGopMatDungPhanCuaXeDo() throws Exception {
        RefuelItemData own = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4);
        seedOwn(own);
        RefuelItemData b = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 7);
        b.setRawJson(rawPayload(b));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(b));

        List<RefuelItemData> truoc = duDieuKien(Arrays.asList(
                repo.getRefuel("own-1").toRefuelItemData(),
                repo.getRefuel("b-1").toRefuelItemData()));
        double kgTruoc = OthersFreshness.documentTotals(truoc).kilos;

        RefuelItemData bTreo = processing("b-1", TRUCK_B, TRUCK_B_ID, 800, 8);
        bTreo.setDateUpdated(new Date(9_000_000));
        bTreo.setRawJson(rawPayload(bTreo));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(bTreo));

        List<RefuelItemData> sau = duDieuKien(Arrays.asList(
                repo.getRefuel("own-1").toRefuelItemData(),
                repo.getRefuel("b-1").toRefuelItemData()));

        assertEquals("số mẻ vào phiếu gộp đã tụt sau khi một bản chưa-Done về",
                truoc.size(), sau.size());
        assertEquals("tổng Kg của phiếu gộp bị kéo về bản cũ",
                kgTruoc, OthersFreshness.documentTotals(sau).kilos, 0d);
    }

    // =====================================================================================
    // PREVIEW — nhiều mẻ sửa liên tiếp trên cùng baseline
    // =====================================================================================

    /**
     * "Nhập mấy lần mới ăn": ở phiếu gộp người dùng sửa liên tiếp NHIỀU MẺ, mỗi hộp thoại
     * đứng trên baseline chụp lúc mở màn hình. Lần sau không được vì thế mà mất dữ liệu.
     */
    @Test
    public void suaLienTiepNhieuMeTrenCungBaselineKhongPhaiNhapLai() {
        seedOwn(done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4));
        seedOwn(done("own-2", OWN_TRUCK, OWN_TRUCK_ID, 900, 4));

        RefuelItemData baseline1 = repo.getRefuel("own-1").toRefuelItemData();
        RefuelItemData baseline2 = repo.getRefuel("own-2").toRefuelItemData();

        // Một lượt ghi khác commit xong ⇒ baseline của CẢ HAI hộp thoại đã cũ.
        RefuelItemData khac = repo.getRefuel("own-1").toRefuelItemData();
        khac.setParkingLot("A5");
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(khac, true)));

        baseline1.setDensity(0.801);
        RefuelItemData r1 = DataHelper.postRefuel(baseline1, true);
        if (!RefuelItemData.isCommitted(r1)) r1 = DataHelper.savePreviewFields(baseline1);
        assertTrue("tỉ trọng mẻ 1 phải được ghi", RefuelItemData.isCommitted(r1));

        baseline2.setQualityNo("QC-778");
        baseline2.setManualTemperature(29);
        RefuelItemData r2 = DataHelper.postRefuel(baseline2, true);
        if (!RefuelItemData.isCommitted(r2)) r2 = DataHelper.savePreviewFields(baseline2);
        assertTrue("số QC mẻ 2 phải được ghi", RefuelItemData.isCommitted(r2));

        assertEquals(0.801d, repo.getRefuel("own-1").toRefuelItemData().getDensity(), 0d);
        assertEquals("A5", repo.getRefuel("own-1").toRefuelItemData().getParkingLot());
        assertEquals("QC-778", repo.getRefuel("own-2").toRefuelItemData().getQualityNo());
        assertEquals(29d,
                repo.getRefuel("own-2").toRefuelItemData().getManualTemperature(), 0d);
    }

    /**
     * Đường patch của màn hình xem trước không được đắp lên một mẻ đã được chốt bằng bộ số
     * KHÁC — đó chính là hình dạng "POST cũ kéo dữ liệu Done về bản cũ" (NT2).
     */
    @Test
    public void patchXemTruocKhongDapLenMeDaChotBangBoSoKhac() {
        RefuelItemData nen = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 500, 4);
        String baseJson = nen.toJson();

        RefuelItemData toiCam = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 500, 4);
        toiCam.setDensity(0.801);
        toiCam.setRealAmount(777);

        RefuelItemData daChot = done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 5);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.PREVIEW, baseJson, toiCam, daChot.toJson(), 4, 5);

        assertFalse("snapshot cũ không được đắp lên mẻ đã chốt", result.isApplied());
        assertTrue(result.isBlockedByFinalizedRow());
        assertEquals("ROW_ALREADY_FINALIZED", result.describe());
    }

    // =====================================================================================
    // NT3 — các chặn dữ liệu vô lý phải còn nguyên ở phiếu GỘP
    // =====================================================================================

    /** NT3 áp cho MỌI dòng của phiếu gộp, kể cả dòng của xe khác đứng thứ hai trong danh sách. */
    @Test
    public void chanDuLieuVoLyVanConNguyenTrenPhieuGop() {
        assertBlocked("tỉ trọng ngoài khoảng cho phép", item -> item.setDensity(0.90));
        assertBlocked("tỉ trọng ngoài khoảng cho phép (dưới)", item -> item.setDensity(0.70));
        assertBlocked("nhiệt độ ngoài khoảng cho phép", item -> item.setManualTemperature(45));
        assertBlocked("nhiệt độ ngoài khoảng cho phép (dưới)",
                item -> item.setManualTemperature(10));
        assertBlocked("số tàu bay bỏ trống", item -> item.setAircraftCode("  "));
        assertBlocked("loại tàu bay bỏ trống", item -> item.setAircraftType(""));
        assertBlocked("chặng bay bỏ trống", item -> item.setRouteName(""));
        assertBlocked("chặng bay 3 ký tự đầu không phải mã sân bay",
                item -> item.setRouteName("HA-SGN"));
        assertBlocked("giờ kết thúc trước giờ bắt đầu",
                item -> item.setEndTime(new Date(item.getStartTime().getTime() - 60_000)));
    }

    /**
     * NGHI NGỜ có thật, cố định lại bằng test: mẻ của XE KHÁC có tỉ trọng ngoài khoảng
     * KHÔNG bị {@code printableForDocument()} loại (hàm đó chỉ hỏi {@code density > 0}), nên
     * nó đi thẳng vào {@code createReceipt()} và bị chặn cứng ở đó.
     *
     * <p>Máy này không sửa được mẻ của xe khác ⇒ đó là ngõ cụt: không loại được, không sửa
     * được, không in được. Test này ghi nhận hành vi HIỆN TẠI để bản vá sau có mốc so.
     */
    @Test
    public void meXeKhacTiTrongNgoaiKhoangKhongBiLoaiTruocNenChanCungPhieuGop() {
        RefuelItemData own = printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786);
        own.setAirlineModel(airline());
        RefuelItemData b = printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.90);

        assertTrue("bộ lọc mẻ xe khác KHÔNG nhìn khoảng tỉ trọng, chỉ nhìn density > 0",
                OthersFreshness.printableForDocument(b));
        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(own, b), Arrays.asList(false, true));
        assertFalse("nên mẻ đó KHÔNG bị loại khỏi phiếu gộp", partition.hasExclusion());

        try {
            ReceiptModel.createReceipt(new ArrayList<>(Arrays.asList(own, b)),
                    null, false, null, false);
            fail("NT3 yêu cầu giữ chặn tỉ trọng ngoài khoảng");
        } catch (RuntimeException expected) {
            assertTrue(String.valueOf(expected.getMessage()).contains("0.72"));
        }
    }

    /**
     * KHOẢNG TRỐNG so với NT3, ghi nhận bằng test: luật nói "chặng bay có 3 ký tự đầu KHÔNG
     * thuộc danh sách sân bay" thì phải chặn, nhưng {@code createReceipt()} chỉ kiểm ĐỊNH
     * DẠNG bằng regex {@code ^[A-Z]{3}-[A-Z0-9]{3,}$}, không đối chiếu danh sách nào.
     *
     * <p>Vì vậy một mã ba chữ cái không tồn tại vẫn ra được tờ giấy. Test này cố định hành vi
     * hiện tại để bản vá sau có mốc so — KHÔNG phải để hợp thức hoá khoảng trống.
     */
    @Test
    public void changBayKhongThuocDanhSachSanBayVanQuaDuocPhieuGop() {
        RefuelItemData own = printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786);
        own.setAirlineModel(airline());
        own.setRouteName("ZZZ-SGN");
        RefuelItemData b = printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786);
        b.setRouteName("QQQ-XXX");

        ReceiptModel receipt = ReceiptModel.createReceipt(
                new ArrayList<>(Arrays.asList(own, b)), null, false, null, false);

        assertEquals("hiện chỉ kiểm định dạng chặng bay, chưa đối chiếu danh sách sân bay",
                2, receipt.getItems().size());
    }

    /**
     * Chốt chặn hồi quy cho FB-1: {@code isCombinedDocument()} phải quyết định theo DỮ LIỆU
     * sắp in, tuyệt đối không đọc {@code printMode}.
     *
     * <p>Nút XUẤT HOÁ ĐƠN đặt {@code ALL_ITEM} vô điều kiện, nên đọc cờ đó là biến mọi hoá
     * đơn — kể cả hoá đơn đúng một mẻ của chính xe này — thành "chứng từ gộp" và chặn nhầm
     * khi mất mạng. Ràng buộc nằm trong Activity nên canh bằng cách đọc mã nguồn.
     */
    @Test
    public void quyetDinhChungTuGopKhongDuocDocPrintMode() throws Exception {
        String source = docQuyetDinhChungTuGop();
        assertTrue("không tìm thấy thân hàm isCombinedDocument()",
                source.contains("printItems"));
        assertFalse("isCombinedDocument() không được đọc printMode — sẽ chặn nhầm khi"
                        + " mất mạng (FB-1)",
                source.contains("printMode"));
    }

    private static String docQuyetDinhChungTuGop() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                "src/main/java/com/megatech/fms/RefuelPreviewActivity.java");
        if (!java.nio.file.Files.exists(path))
            path = java.nio.file.Paths.get(
                    "app/src/main/java/com/megatech/fms/RefuelPreviewActivity.java");
        String all = new String(java.nio.file.Files.readAllBytes(path), "UTF-8");
        int start = all.indexOf("private boolean isCombinedDocument()");
        assertTrue("không tìm thấy isCombinedDocument() trong RefuelPreviewActivity",
                start > 0);
        int end = all.indexOf("\n    }", start);
        // Bỏ dòng chú thích: khối ghi chú FB-1 nhắc tên printMode để giải thích vì sao KHÔNG
        // được đọc nó, canh trên chú thích thì báo động giả.
        StringBuilder code = new StringBuilder();
        for (String line : all.substring(start, end).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            code.append(line).append('\n');
        }
        return code.toString();
    }

    // =====================================================================================
    // Metadata chứng từ trên phiếu gộp
    // =====================================================================================

    /**
     * Dấu "đã in" phải bám lên MỌI mẻ trong phiếu gộp, kể cả mẻ của xe khác — nếu không, mẻ
     * đó bị in lần hai bằng một số phiếu khác. Với mẻ xe khác thì server phải nhận trước.
     */
    @Test
    public void soPhieuGopDuocGhiLenCaMeXeKhacSauKhiServerNhan() throws Exception {
        seedOwn(done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4));
        RefuelItemData b = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 7);
        b.setRawJson(rawPayload(b));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(b));

        http.postResponse = repo.getRefuel("b-1").toRefuelItemData();
        DataHelper.PatchResult own = DataHelper.patchRefuelDocument("own-1", applyReceipt());
        DataHelper.PatchResult foreign = DataHelper.patchRefuelDocument("b-1", applyReceipt());

        assertTrue(own.applied);
        assertTrue("mẻ xe khác cũng phải nhận dấu đã in", foreign.applied);
        assertEquals("0000ABCD", repo.getRefuel("own-1").toRefuelItemData().getReceiptNumber());
        assertEquals("0000ABCD", repo.getRefuel("b-1").toRefuelItemData().getReceiptNumber());
        assertEquals("ghi metadata chứng từ không được đụng sản lượng",
                2100d, repo.getRefuel("b-1").toRefuelItemData().getRealAmount(), 0d);
    }

    /**
     * Ca hiểm: mẻ đầu ghi được số phiếu, mẻ thứ hai của xe khác bị server từ chối.
     * {@code patchAllPrintItems()} chạy tiếp cả vòng lặp, nên phiếu gộp kết thúc ở trạng thái
     * NỬA VỜI — một phần mẻ mang số phiếu, một phần không.
     *
     * <p>Test cố định hành vi hiện tại để thấy rõ rủi ro: không có bước nào gỡ lại số phiếu
     * đã ghi ở mẻ trước.
     */
    @Test
    public void serverTuChoiMotMeThiDauDaInCuaPhieuGopBiNuaVoi() throws Exception {
        seedOwn(done("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 4));
        RefuelItemData b = done("b-1", TRUCK_B, TRUCK_B_ID, 2100, 7);
        b.setRawJson(rawPayload(b));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(b));

        DataHelper.PatchResult own = DataHelper.patchRefuelDocument("own-1", applyReceipt());
        http.postResponse = null; // server từ chối mẻ xe khác
        DataHelper.PatchResult foreign = DataHelper.patchRefuelDocument("b-1", applyReceipt());

        assertTrue(own.applied);
        assertFalse(foreign.applied);
        assertEquals("REMOTE_PUSH_FAILED", foreign.reason);
        assertEquals("mẻ xe mình đã mang số phiếu", "0000ABCD",
                repo.getRefuel("own-1").toRefuelItemData().getReceiptNumber());
        assertTrue("mẻ xe khác vẫn chưa mang số phiếu ⇒ phiếu gộp nửa vời",
                isBlank(repo.getRefuel("b-1").toRefuelItemData().getReceiptNumber()));
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private interface Mutator {
        void apply(RefuelItemData item);
    }

    /** Dòng thứ hai (của XE KHÁC) bị làm hỏng theo {@code mutator} thì phiếu gộp phải bị chặn. */
    private void assertBlocked(String rangBuoc, Mutator mutator) {
        RefuelItemData own = printable("own-1", OWN_TRUCK, OWN_TRUCK_ID, 1416, 0.786);
        own.setAirlineModel(airline());
        RefuelItemData b = printable("b-1", TRUCK_B, TRUCK_B_ID, 2100, 0.786);
        mutator.apply(b);
        try {
            ReceiptModel.createReceipt(new ArrayList<>(Arrays.asList(own, b)),
                    null, false, null, false);
            fail("NT3 yêu cầu giữ nguyên chặn: " + rangBuoc);
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static DataHelper.RefuelPatch applyReceipt() {
        return latest -> {
            latest.setReceiptNumber("0000ABCD");
            latest.setReceiptUniqueId("receipt-uid");
            latest.setReceiptCount(latest.getReceiptCount() + 1);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static double sumGallon(List<RefuelItemData> items) {
        double total = 0;
        for (RefuelItemData item : items) total += item.getRealAmount();
        return total;
    }

    private static List<RefuelItemData> duDieuKien(List<RefuelItemData> items) {
        List<RefuelItemData> result = new ArrayList<>();
        for (RefuelItemData item : items)
            if (OthersFreshness.eligibleForDocument(item)) result.add(item);
        return result;
    }

    private static List<RefuelItemData> combinedPrintItems(DataHelper.PreviewLoadResult loaded) {
        List<RefuelItemData> items = new ArrayList<>();
        items.add(loaded.item);
        if (loaded.item.getOthers() != null) items.addAll(loaded.item.getOthers());
        return items;
    }

    /** Đưa root + Others lên endpoint như server trả về một chuyến nhiều xe. */
    private void publishRoot(RefuelItemData own, RefuelItemData... others) throws Exception {
        List<RefuelItemData> list = new ArrayList<>();
        for (RefuelItemData other : others) {
            other.setRawJson(rawPayload(other));
            list.add(other);
        }
        own.setRawJson(rawPayload(own));
        own.setOthers(list);
        own.setCompleteOthersSnapshot(true);
        http.responses.put(own.getUniqueId(), own);
    }

    private void seedOwn(RefuelItemData data) {
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(false);
        row.setLocalModified(false);
        repo.insertRefuel(row);
    }

    private static RefuelItemData done(String uid, String truckNo, int truckId,
                                       double amount, int revision) {
        return base(uid, truckNo, truckId, amount, revision, REFUEL_ITEM_STATUS.DONE,
                new Date(1_777_300_600_000L));
    }

    private static RefuelItemData processing(String uid, String truckNo, int truckId,
                                             double amount, int revision) {
        return base(uid, truckNo, truckId, amount, revision, REFUEL_ITEM_STATUS.PROCESSING,
                null);
    }

    private static RefuelItemData base(String uid, String truckNo, int truckId, double amount,
                                       int revision, REFUEL_ITEM_STATUS status, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setId(Math.abs(uid.hashCode()) + 1);
        item.setUniqueId(uid);
        item.setFlightId(FLIGHT_ID);
        item.setFlightUniqueId(FLIGHT_UID);
        item.setFlightCode("3S 621");
        item.setTruckNo(truckNo);
        item.setTruckId(truckId);
        item.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        item.setStatus(status);
        item.setServerRevision(revision);
        item.setClientSeq(revision);
        item.setRealAmount(amount);
        item.setStartNumber(1_000_000);
        item.setEndNumber(1_000_000 + amount);
        item.setStartTime(new Date(1_777_300_000_000L));
        item.setEndTime(end);
        item.setRefuelTime(new Date(1_777_300_000_000L));
        item.setDensity(0.786);
        item.setManualTemperature(27);
        item.setQualityNo("QC-001");
        item.setAircraftCode("VN-A630");
        item.setAircraftType("A321");
        item.setRouteName("HAN-SGN");
        item.setAirlineId(8);
        return item;
    }

    /** Mẻ đã đủ điều kiện lên chứng từ, dùng cho các test dựng phiếu. */
    private static RefuelItemData printable(String uid, String truckNo, int truckId,
                                            double amount, double density) {
        RefuelItemData item = done(uid, truckNo, truckId, amount, 1);
        item.setDensity(density);
        item.setPrice(4.06);
        item.setTaxRate(0.1);
        item.setProductName("JET A-1");
        item.setPName("JET A-1");
        item.setPCode("JET-A1");
        item.setCurrency(RefuelItemData.CURRENCY.USD);
        return item;
    }

    private static AirlineModel airline() {
        AirlineModel airline = new AirlineModel();
        airline.setId(8);
        airline.setName("Vietnam Airlines");
        airline.setCode("VN");
        airline.setAddress("Hà Nội");
        airline.setTaxCode("0100107518");
        airline.setProductName("JET A-1");
        return airline;
    }

    /** Raw không mang Others; test tự gắn collection khi cần. */
    private static String rawPayload(RefuelItemData item) throws Exception {
        JSONObject raw = new JSONObject(item.toJson());
        raw.remove("Others");
        if (item.getStartTime() == null) raw.remove("StartTime");
        if (item.getEndTime() == null) raw.remove("EndTime");
        return raw.toString();
    }

    private static final class FakeHttpClient extends HttpClient {
        RefuelItemData postResponse;
        int postCount;
        final Map<String, RefuelItemData> responses = new HashMap<>();
        final List<String> getUids = new ArrayList<>();

        FakeHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            getUids.add(uniqueId);
            return responses.get(uniqueId);
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            postCount++;
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
