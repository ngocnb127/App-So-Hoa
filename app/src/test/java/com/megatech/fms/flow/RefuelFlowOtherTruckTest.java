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
import com.megatech.fms.helpers.RefuelApproachGuard;
import com.megatech.fms.helpers.RefuelFlowOtherTruckTestBridge;
import com.megatech.fms.model.AirlineModel;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * CA 2 — toàn tuyến luồng tra nạp cho chuyến ĐƯỢC PHÂN CÔNG CHO XE KHÁC, và ca gần kề là
 * chuyến CHƯA ĐƯỢC PHÂN CÔNG cho xe nào.
 *
 * <p>Đây là ca nghiệp vụ THẬT chứ không phải ngoại lệ: điều độ không kịp phân công thì người
 * dùng chọn chuyến từ danh sách "chuyến khác" và vẫn phải ghi nhận bình thường
 * (skill {@code fms-refuel-flow}, mục "Những thứ TUYỆT ĐỐI không được đụng").
 *
 * <p>Thước đo là ba nguyên tắc chủ dự án chốt 2026-09-06:
 * <ul>
 *   <li><b>NT1</b> — mọi chuyến đều phải ghi nhận được, không ca nào bị chặn;</li>
 *   <li><b>NT2</b> — chỉ chặn đúng bản ghi "đang tra nạp" treo trên đường lên server rồi
 *       trả về SAU khi mẻ đã Done; bản chưa-Done không bao giờ đè lên bản đã Done;</li>
 *   <li><b>NT3</b> — giữ nguyên các chặn dữ liệu vô lý đang có.</li>
 * </ul>
 *
 * <p>Ranh giới thứ tư (skill {@code fms-sync-concurrency}): xe này ĐƯỢC in/xuất phiếu cho mẻ
 * của xe khác, nhưng KHÔNG được sửa số liệu mẻ của xe khác.
 *
 * <p>Tablet và đồng hồ không kết nối được (kiểm tra 2026-09-06), nên luồng đồng hồ được giả
 * lập bằng chính chuỗi lời gọi mà {@code RefuelDetailActivity} thực hiện.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelFlowOtherTruckTest {

    private static final String OWN_TRUCK = "HAN3-20-7002";
    private static final int OWN_TRUCK_ID = 7002;

    private static final String OTHER_TRUCK = "HAN3-20-7012";
    private static final int OTHER_TRUCK_ID = 7012;

    /** Chuyến đã phân công cho XE KHÁC. */
    private static final String UID_OTHER = "aa11bb22-cc33-dd44-ee55-ff6677889900";
    /** Chuyến CHƯA phân công cho xe nào. */
    private static final String UID_UNASSIGNED = "11223344-5566-7788-99aa-bbccddeeff00";

    private AppDatabase db;
    private DataRepository repo;
    private RecordingHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(OWN_TRUCK_ID);
        setting.setTruckNo(OWN_TRUCK);
        ((FMSApplication) context).saveSetting(setting, false);

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new RecordingHttpClient();
        RefuelFlowOtherTruckTestBridge.install(repo, http);
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        RefuelFlowOtherTruckTestBridge.reset();
        db.close();
    }

    // ---------------------------------------------------------------- dữ liệu nền

    private RefuelItemData baseFlight(String uid, int id, String flightCode) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uid);
        data.setFlightUniqueId("flight-" + id);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(id + 1);
        data.setFlightCode(flightCode);
        data.setAircraftCode("VN-A869");
        data.setAircraftType("A321");
        data.setRouteName("HAN-SGN");
        data.setStartNumber(56636400);
        data.setEndNumber(56636400);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_100_000L));
        data.setRefuelTime(new Date(1_787_000_100_000L));
        return data;
    }

    /** Phiếu của XE KHÁC, kéo về máy này dưới dạng bản sao chỉ đọc. */
    private void seedOtherTruckReplica() {
        RefuelItemData data = baseFlight(UID_OTHER, 2200100, "VN 247");
        data.setTruckId(OTHER_TRUCK_ID);
        data.setTruckNo(OTHER_TRUCK);
        data.setRawJson("{\"UniqueId\":\"" + UID_OTHER + "\",\"Id\":2200100,"
                + "\"TruckNo\":\"" + OTHER_TRUCK + "\",\"TruckId\":" + OTHER_TRUCK_ID
                + ",\"Status\":\"1\"}");

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(true);
        repo.insertRefuel(row);
    }

    /**
     * Chuyến CHƯA phân công: server không gửi TruckNo/TruckId nào cả.
     *
     * <p>Cố ý để {@code truckNo} null chứ không phải chuỗi rỗng — đó là thứ Gson dựng ra khi
     * khoá vắng mặt trong payload.
     */
    private void seedUnassignedFlight() {
        RefuelItemData data = baseFlight(UID_UNASSIGNED, 2200200, "VJ 130");
        data.setTruckId(0);
        data.setTruckNo(null);
        data.setRawJson("{\"UniqueId\":\"" + UID_UNASSIGNED + "\",\"Id\":2200200,"
                + "\"Status\":\"1\"}");

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        repo.insertRefuel(row);
    }

    /** Ảnh chụp mà màn tra nạp cầm khi đồng hồ đang chạy (chưa chốt). */
    private RefuelItemData meterRunning(String uid, double endNumber) {
        RefuelItemData screen = repo.getRefuel(uid).toRefuelItemData();
        screen.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        screen.setEndNumber(endNumber);
        screen.setRealAmount(endNumber - screen.getStartNumber());
        screen.setGallon(endNumber - screen.getStartNumber());
        return screen;
    }

    /** Ảnh chụp lúc bấm Kết thúc. */
    private RefuelItemData meterDone(String uid, double endNumber) {
        RefuelItemData screen = meterRunning(uid, endNumber);
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setEndTime(new Date(1_787_000_900_000L));
        return screen;
    }

    private RefuelItemData stored(String uid) {
        RefuelItem row = repo.getRefuel(uid);
        return row == null ? null : row.toRefuelItemData();
    }

    // ================================================================
    // CHẶNG 1 — CHỌN CHUYẾN
    // ================================================================

    /** Chuyến của xe khác phải nhìn thấy được ở danh sách "chuyến khác" để mà chọn. */
    @Test
    public void chuyenXeKhacPhaiHienTrongDanhSachChuyenKhac() {
        seedOtherTruckReplica();

        List<RefuelItemData> others = repo.getRefuelList(OWN_TRUCK, OWN_TRUCK_ID, false, 0);

        assertTrue("NT1: chuyến của xe khác phải chọn được từ danh sách chuyến khác",
                containsUid(others, UID_OTHER));
    }

    /**
     * NT1: chuyến CHƯA PHÂN CÔNG cũng phải chọn được.
     *
     * <p>Không nhìn thấy trong danh sách nào thì không có cách nào tra nạp — đây là ngõ cụt
     * nặng nhất có thể có với ca "điều độ không kịp phân công".
     */
    @Test
    public void chuyenChuaPhanCongPhaiHienTrongMotTrongHaiDanhSach() {
        seedUnassignedFlight();

        // Phân biệt "row không có trong Room" với "row có nhưng truy vấn lọc mất":
        // đây là điều kiện tiên quyết để kết luận đúng nguyên nhân.
        assertNotNull("phiếu chưa phân công phải nằm trong Room", repo.getRefuel(UID_UNASSIGNED));

        List<RefuelItemData> mine = repo.getRefuelList(OWN_TRUCK, OWN_TRUCK_ID, true, 0);
        List<RefuelItemData> others = repo.getRefuelList(OWN_TRUCK, OWN_TRUCK_ID, false, 0);

        assertTrue("NT1: chuyến chưa phân công phải chọn được từ danh sách của xe"
                        + " hoặc danh sách chuyến khác — hiện không có ở đâu cả",
                containsUid(mine, UID_UNASSIGNED) || containsUid(others, UID_UNASSIGNED));
    }

    /**
     * Chuyến đang mở dở của XE KHÁC không được khoá nút Tiếp cận của xe này.
     *
     * <p>Xe này chỉ đứng được ở một tàu bay; chuyện xe khác quên bấm Rời đi không liên quan.
     */
    @Test
    public void chuyenDangMoDoCuaXeKhacKhongChanTiepCanCuaXeNay() {
        seedOtherTruckReplica();
        RefuelItem row = repo.getRefuel(UID_OTHER);
        RefuelItemData withApproach = row.toRefuelItemData();
        withApproach.setApproachTime(new Date());
        withApproach.setLeaveTime(null);
        row.updateData(withApproach);
        repo.insertRefuel(row);

        List<RefuelItemData> blocking = RefuelApproachGuard.findBlocking(null);

        assertTrue("NT1: chuyến đang mở dở của xe khác không được chặn tiếp cận của xe này",
                blocking.isEmpty());
    }

    // ================================================================
    // CHẶNG 2 — GHI SỐ (giả lập đồng hồ)
    // ================================================================

    /** Số đồng hồ trung gian trên chuyến của xe khác phải vào được Room. */
    @Test
    public void ghiSoDongHoTrungGianTrenChuyenXeKhacDuocNhan() {
        seedOtherTruckReplica();

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(
                meterRunning(UID_OTHER, 56637000), false);

        assertTrue("NT1: số đồng hồ đang chạy phải ghi được", RefuelItemData.isCommitted(saved));
        assertEquals(56637000d, stored(UID_OTHER).getEndNumber(), 0d);
        assertFalse("cờ chỉ-đọc phải được gỡ sau khi tiếp quản",
                repo.getRefuel(UID_OTHER).isRemoteReplica());
        assertEquals(OWN_TRUCK, repo.getRefuel(UID_OTHER).getTruckNo());
    }

    /** Cùng như trên nhưng cho chuyến CHƯA PHÂN COÔNG (không xe nào, id = 0). */
    @Test
    public void ghiSoDongHoTrenChuyenChuaPhanCongDuocNhan() {
        seedUnassignedFlight();

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(
                meterRunning(UID_UNASSIGNED, 56637500), false);

        assertTrue("NT1: chuyến chưa phân công vẫn phải ghi nhận được",
                RefuelItemData.isCommitted(saved));
        assertEquals(56637500d, stored(UID_UNASSIGNED).getEndNumber(), 0d);
        assertEquals("xe đang bơm phải trở thành xe sở hữu mẻ",
                OWN_TRUCK, repo.getRefuel(UID_UNASSIGNED).getTruckNo());
    }

    /** Ghi liên tiếp nhiều nhịp đồng hồ: nhịp sau không được kẹt vì guard. */
    @Test
    public void nhieuNhipDongHoLienTiepTrenChuyenXeKhacDeuGhiDuoc() {
        seedOtherTruckReplica();

        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuelFromRefuelScreen(
                meterRunning(UID_OTHER, 56636900), false)));
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuelFromRefuelScreen(
                meterRunning(UID_OTHER, 56637900), false)));
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuelFromRefuelScreen(
                meterRunning(UID_OTHER, 56639429), false)));

        assertEquals(56639429d, stored(UID_OTHER).getEndNumber(), 0d);
    }

    // ================================================================
    // CHẶNG 3 — KẾT THÚC (hai nhịp của chuỗi chốt mẻ)
    // ================================================================

    /** Nhịp một: {@code postRefuelFromRefuelScreen}. */
    @Test
    public void chotMeChuyenXeKhacNhipMotGhiDuoc() {
        seedOtherTruckReplica();

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(
                meterDone(UID_OTHER, 56639429), false);

        assertTrue("NT1: nhịp một của chốt mẻ phải ghi được",
                RefuelItemData.isCommitted(saved));
        assertEquals(REFUEL_ITEM_STATUS.DONE, stored(UID_OTHER).getStatus());
        assertEquals(3029d, stored(UID_OTHER).getRealAmount(), 0d);
    }

    /**
     * Nhịp hai: đường khôi phục khi nhịp một trả CONFLICT.
     *
     * <p>Trước đây nhịp này đi {@code saveEndFields} fail-closed nên mẻ tra nạp hộ gặp xung
     * đột lúc chốt bị từ chối VĨNH VIỄN. Phải còn quyền tiếp quản như nhịp một.
     */
    @Test
    public void chotMeChuyenXeKhacNhipHaiKhoiPhucGhiDuoc() {
        seedOtherTruckReplica();

        RefuelItemData saved = DataHelper.saveEndFieldsFromRefuelScreen(
                meterDone(UID_OTHER, 56639429));

        assertTrue("NT1: nhịp hai (EndFieldsPatch) phải ghi được mẻ tra nạp hộ",
                RefuelItemData.isCommitted(saved));
        assertEquals(3029d, stored(UID_OTHER).getRealAmount(), 0d);
        assertFalse(repo.getRefuel(UID_OTHER).isRemoteReplica());
    }

    /** Nhịp hai trên chuyến CHƯA PHÂN CÔNG — ca chưa từng được canh ở test nào. */
    @Test
    public void chotMeChuyenChuaPhanCongNhipHaiKhoiPhucGhiDuoc() {
        seedUnassignedFlight();

        RefuelItemData saved = DataHelper.saveEndFieldsFromRefuelScreen(
                meterDone(UID_UNASSIGNED, 56639429));

        assertTrue("NT1: nhịp hai phải ghi được cả chuyến chưa phân công",
                RefuelItemData.isCommitted(saved));
        assertEquals(3029d, stored(UID_UNASSIGNED).getRealAmount(), 0d);
    }

    /** CHỐNG HỒI QUY: đường End THƯỜNG (không phải từ màn tra nạp) vẫn fail-closed. */
    @Test
    public void duongEndThuongVanChanMeCuaXeKhac() {
        seedOtherTruckReplica();

        assertFalse("saveEndFields không được nới theo",
                RefuelItemData.isCommitted(DataHelper.saveEndFields(meterDone(UID_OTHER, 56639429))));
        assertTrue("cờ chỉ-đọc phải còn nguyên", repo.getRefuel(UID_OTHER).isRemoteReplica());
        assertEquals(OTHER_TRUCK, repo.getRefuel(UID_OTHER).getTruckNo());
    }

    // ================================================================
    // CHẶNG 4 — XÁC NHẬN
    // ================================================================

    /** Màn xác nhận ghi nhiệt độ / tỉ trọng / số hoá nghiệm lên mẻ tra nạp hộ. */
    @Test
    public void xacNhanMeChuyenXeKhacGhiDuoc() {
        seedOtherTruckReplica();
        DataHelper.postRefuelFromRefuelScreen(meterDone(UID_OTHER, 56639429), false);

        RefuelItemData confirm = stored(UID_OTHER);
        confirm.setManualTemperature(30.5);
        confirm.setDensity(0.795);
        confirm.setQualityNo("QC-2026-09");

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(confirm, false);

        assertTrue("NT1: màn xác nhận phải ghi được", RefuelItemData.isCommitted(saved));
        assertEquals(30.5d, stored(UID_OTHER).getManualTemperature(), 0.001d);
        assertEquals(0.795d, stored(UID_OTHER).getDensity(), 0.00001d);
        assertEquals("QC-2026-09", stored(UID_OTHER).getQualityNo());
    }

    /** Nhịp hai của màn xác nhận, trên chuyến chưa phân công. */
    @Test
    public void xacNhanNhipHaiChuyenChuaPhanCongGhiDuoc() {
        seedUnassignedFlight();

        RefuelItemData confirm = meterDone(UID_UNASSIGNED, 56639429);
        confirm.setManualTemperature(29.0);
        confirm.setDensity(0.801);
        confirm.setQualityNo("QC-77");

        RefuelItemData saved = DataHelper.saveConfirmFieldsFromRefuelScreen(confirm);

        assertTrue("NT1: nhịp hai của xác nhận phải ghi được chuyến chưa phân công",
                RefuelItemData.isCommitted(saved));
        assertEquals(0.801d, stored(UID_UNASSIGNED).getDensity(), 0.00001d);
    }

    /** Màn hình phải biết đây là mẻ tiếp quản để còn cảnh báo (cảnh báo, KHÔNG chặn). */
    @Test
    public void manHinhNhanBietDuocMeTiepQuanDeCanhBao() {
        seedOtherTruckReplica();
        seedUnassignedFlight();

        assertTrue("mẻ của xe khác phải được nhận diện là tiếp quản",
                DataHelper.isForeignTruckRefuel(stored(UID_OTHER)));
        assertTrue("mẻ chưa phân công phải được nhận diện là tiếp quản",
                DataHelper.isForeignTruckRefuel(stored(UID_UNASSIGNED)));
    }

    // ================================================================
    // NT2 — POST "đang tra nạp" treo, trả về SAU khi mẻ đã Done
    // ================================================================

    /**
     * NT2: bản chưa-Done không bao giờ được đè lên bản đã Done.
     *
     * <p>Ở ca tra nạp hộ rủi ro này CAO HƠN ca xe mình: lối vào tiếp quản bỏ qua guard quyền
     * sở hữu, nên nếu nó cũng bỏ qua luôn bảo vệ trạng thái thì một snapshot PROCESSING còn
     * đọng trong hàng đợi ghi sẽ kéo mẻ đã chốt về 0.
     */
    @Test
    public void snapshotDangTraNapTreoKhongKeoNguocMeDaDoneOLoiVaoTiepQuan() {
        seedOtherTruckReplica();
        RefuelItemData stale = meterRunning(UID_OTHER, 56637000);
        DataHelper.postRefuelFromRefuelScreen(meterDone(UID_OTHER, 56639429), false);

        DataHelper.postRefuelFromRefuelScreen(stale, false);

        assertEquals("NT2: mẻ đã Done không được hạ về PROCESSING",
                REFUEL_ITEM_STATUS.DONE, stored(UID_OTHER).getStatus());
        assertEquals("NT2: sản lượng chốt không được bị bản cũ ghi đè",
                3029d, stored(UID_OTHER).getRealAmount(), 0d);
        assertEquals(56639429d, stored(UID_OTHER).getEndNumber(), 0d);
    }

    /** NT2, đường nhịp hai: patch END của một bản chưa Done cũng không được hạ trạng thái. */
    @Test
    public void patchEndCuaBanChuaDoneKhongDeLenBanDaDone() {
        seedOtherTruckReplica();
        RefuelItemData stale = meterRunning(UID_OTHER, 56637000);
        DataHelper.postRefuelFromRefuelScreen(meterDone(UID_OTHER, 56639429), false);

        DataHelper.saveEndFieldsFromRefuelScreen(stale);

        assertEquals("NT2: patch END của bản chưa Done không được hạ mẻ đã chốt",
                REFUEL_ITEM_STATUS.DONE, stored(UID_OTHER).getStatus());
        assertEquals(3029d, stored(UID_OTHER).getRealAmount(), 0d);
    }

    /** NT2 chiều ngược lại: dữ liệu Done VẪN phải sửa được dữ liệu Done trước đó. */
    @Test
    public void banDoneVanSuaDuocBanDoneTruocDo() {
        seedOtherTruckReplica();
        DataHelper.postRefuelFromRefuelScreen(meterDone(UID_OTHER, 56639429), false);

        RefuelItemData correction = stored(UID_OTHER);
        correction.setStatus(REFUEL_ITEM_STATUS.DONE);
        correction.setEndNumber(56639500);
        correction.setRealAmount(3100);
        correction.setGallon(3100);

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(correction, false);

        assertTrue("NT2: chỉ dữ liệu Done mới sửa được Done — và nó PHẢI sửa được",
                RefuelItemData.isCommitted(saved));
        assertEquals(3100d, stored(UID_OTHER).getRealAmount(), 0d);
    }

    // ================================================================
    // RANH GIỚI — in/xuất phiếu hộ ĐƯỢC, sửa số liệu mẻ xe khác KHÔNG
    // ================================================================

    /** In hộ: xe này được ghi số phiếu lên mẻ của xe khác (server nhận trước, rồi mới ghi). */
    @Test
    public void xeNayGhiDuocSoPhieuLenMeCuaXeKhac() {
        seedOtherTruckReplica();
        http.echoPost = true;

        DataHelper.PatchResult result = DataHelper.patchRefuelDocument(UID_OTHER,
                latest -> latest.setReceiptNumber("26194DV"));

        assertNotNull(result);
        assertTrue("in hộ phải ghi được số phiếu, lý do=" + result.reason, result.applied);
        assertEquals("26194DV", stored(UID_OTHER).getReceiptNumber());
        assertTrue("mẻ vẫn là của xe khác: cờ chỉ-đọc phải giữ nguyên",
                repo.getRefuel(UID_OTHER).isRemoteReplica());
        assertEquals(OTHER_TRUCK, repo.getRefuel(UID_OTHER).getTruckNo());
    }

    /** Ranh giới: đường chứng từ KHÔNG được dùng để sửa số đồng hồ của mẻ xe khác. */
    @Test
    public void xeNayKhongSuaDuocSoDongHoCuaMeXeKhac() {
        seedOtherTruckReplica();
        http.echoPost = true;

        DataHelper.PatchResult result = DataHelper.patchRefuelDocument(UID_OTHER,
                latest -> latest.setEndNumber(99999999));

        assertFalse("sửa số đồng hồ của mẻ xe khác phải bị từ chối", result.applied);
        assertEquals(56636400d, stored(UID_OTHER).getEndNumber(), 0d);
    }

    /**
     * Ranh giới: đường patch thường KHÔNG được dùng để sửa dữ liệu nghiệp vụ của mẻ xe khác.
     *
     * <p>Đây là chiều "đúng" của guard — bãi đỗ là dữ liệu của phiếu, không phải chứng từ.
     */
    @Test
    public void patchThuongKhongSuaDuocDuLieuMeCuaXeKhac() {
        seedOtherTruckReplica();

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID_OTHER,
                latest -> latest.setParkingLot("A12"));

        assertFalse("patch thường không được ghi lên mẻ của xe khác", result.applied);
    }

    /**
     * NT1 — MỐC TIẾP CẬN cho chuyến của xe khác phải ghi được.
     *
     * <p>Đây là bước ĐẦU TIÊN của luồng CA 2. Màn hình danh sách hiện nút "Tiếp cận" cho mọi
     * phiếu (điều kiện hiển thị trong {@code cardview_refuel_item.xml} chỉ là
     * {@code approachTime == null}, không xét xe), và trình xử lý của nó gọi thẳng
     * {@code DataHelper.patchRefuel} — đường fail-closed. Người dùng bấm nút hiện rõ trên màn
     * hình và nhận "chưa lưu được", không có lối nào khác ở màn đó.
     *
     * <p>Test này KHÔNG đòi nới {@code patchRefuel}: guard đó đúng cho mọi caller nền. Nó đòi
     * màn danh sách phải có lối vào tiếp quản riêng, y như màn tra nạp đã có
     * {@code postRefuelFromRefuelScreen}.
     */
    @Test
    public void ghiMocTiepCanChoChuyenXeKhacPhaiLuuDuoc() {
        seedOtherTruckReplica();
        final Date approach = new Date(1_787_000_050_000L);

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID_OTHER,
                latest -> latest.setApproachTime(approach));

        assertTrue("NT1: bấm Tiếp cận trên chuyến của xe khác phải ghi được, lý do hiện tại="
                + (result == null ? "null" : result.reason), result != null && result.applied);
    }

    /** Cùng ngõ cụt đó với chuyến CHƯA PHÂN CÔNG. */
    @Test
    public void ghiMocTiepCanChoChuyenChuaPhanCongPhaiLuuDuoc() {
        seedUnassignedFlight();
        final Date approach = new Date(1_787_000_050_000L);

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID_UNASSIGNED,
                latest -> latest.setApproachTime(approach));

        assertTrue("NT1: bấm Tiếp cận trên chuyến chưa phân công phải ghi được, lý do hiện tại="
                + (result == null ? "null" : result.reason), result != null && result.applied);
    }

    /**
     * Ranh giới chiều ngược: sau khi tiếp quản, mẻ là CỦA CHÍNH XE NÀY và mọi đường ghi
     * thường phải mở lại — nếu không thì chặt quá và người dùng kẹt ở các màn sau.
     */
    @Test
    public void sauTiepQuanMoiDuongGhiThuongPhaiMoLai() {
        seedOtherTruckReplica();
        DataHelper.postRefuelFromRefuelScreen(meterDone(UID_OTHER, 56639429), false);

        DataHelper.PatchResult patched = DataHelper.patchRefuel(UID_OTHER,
                latest -> latest.setLeaveTime(new Date(1_787_001_500_000L)));
        assertTrue("sau tiếp quản, patch thường phải chạy được: " + patched.reason,
                patched.applied);

        RefuelItemData preview = stored(UID_OTHER);
        preview.setManualTemperature(31.0);
        assertTrue("sau tiếp quản, màn xem trước phải lưu được",
                RefuelItemData.isCommitted(DataHelper.savePreviewFields(preview)));
    }

    // ================================================================
    // NT3 — các chặn dữ liệu vô lý phải còn nguyên trên mẻ tiếp quản
    // ================================================================

    private RefuelItemData receiptReady() {
        RefuelItemData item = baseFlight("receipt-uid", 2200300, "VN 247");
        item.setTruckId(OWN_TRUCK_ID);
        item.setTruckNo(OWN_TRUCK);
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setEndNumber(56639429);
        item.setRealAmount(3029);
        item.setGallon(3029);
        item.setStartTime(new Date(1_787_000_100_000L));
        item.setEndTime(new Date(1_787_000_400_000L));
        item.setManualTemperature(30.0);
        item.setDensity(0.795);
        item.setQualityNo("QC-1");
        AirlineModel airline = new AirlineModel();
        airline.setName("Vietnam Airlines");
        item.setAirlineModel(airline);
        return item;
    }

    private static void assertReceiptRejected(String why, RefuelItemData item) {
        List<RefuelItemData> batch = new ArrayList<>();
        batch.add(item);
        try {
            ReceiptModel.createReceipt(batch, null, false, null, false);
            fail("NT3: phải chặn — " + why);
        } catch (RuntimeException expected) {
            // đúng như mong đợi
        }
    }

    /** NT3: tỉ trọng ngoài khoảng cho phép vẫn bị chặn ở khâu xuất phiếu. */
    @Test
    public void nt3TiTrongNgoaiKhoangVanBiChan() {
        RefuelItemData item = receiptReady();
        item.setDensity(0.91);
        assertReceiptRejected("tỉ trọng 0.91 ngoài khoảng 0.72–0.86", item);
    }

    /** NT3: nhiệt độ ngoài khoảng cho phép vẫn bị chặn. */
    @Test
    public void nt3NhietDoNgoaiKhoangVanBiChan() {
        RefuelItemData item = receiptReady();
        item.setManualTemperature(9.0);
        assertReceiptRejected("nhiệt độ 9°C ngoài khoảng 15–40", item);
    }

    /** NT3: chặng bay sai định dạng ba ký tự sân bay vẫn bị chặn. */
    @Test
    public void nt3ChangBaySaiDinhDangVanBiChan() {
        RefuelItemData item = receiptReady();
        item.setRouteName("HA-SGN");
        assertReceiptRejected("chặng bay HA-SGN không đúng định dạng", item);
    }

    /** NT3: số hiệu tàu bay bỏ trống vẫn bị chặn. */
    @Test
    public void nt3SoHieuTauBayBoTrongVanBiChan() {
        RefuelItemData item = receiptReady();
        item.setAircraftCode("   ");
        assertReceiptRejected("số hiệu tàu bay bỏ trống", item);
    }

    /** NT3: mẻ 0 lít vẫn GHI NHẬN được nhưng KHÔNG xuất được hoá đơn. */
    @Test
    public void nt3MeKhongLitGhiNhanDuocNhungKhongXuatDuocHoaDon() {
        seedOtherTruckReplica();
        RefuelItemData zero = repo.getRefuel(UID_OTHER).toRefuelItemData();
        zero.setStatus(REFUEL_ITEM_STATUS.DONE);
        zero.setRealAmount(0);
        zero.setGallon(0);
        zero.setEndTime(new Date(1_787_000_400_000L));

        assertTrue("NT1: mẻ 0 lít vẫn phải ghi nhận được",
                RefuelItemData.isCommitted(DataHelper.postRefuelFromRefuelScreen(zero, false)));

        ArrayList<RefuelItemData> items = new ArrayList<>();
        items.add(stored(UID_OTHER));
        try {
            com.megatech.fms.model.InvoiceModel.fromRefuel(stored(UID_OTHER), items);
            fail("NT3: mẻ 0 lít không được xuất hoá đơn");
        } catch (IllegalArgumentException expected) {
            // đúng như mong đợi
        }
    }

    // ---------------------------------------------------------------- tiện ích

    private static boolean containsUid(List<RefuelItemData> list, String uid) {
        if (list == null) return false;
        for (RefuelItemData item : list)
            if (item != null && uid.equals(item.getUniqueId())) return true;
        return false;
    }

    /**
     * Mặc định là MẤT MẠNG (dữ liệu phải nằm lại Room và hàng đợi). Bật {@code echoPost} cho
     * các ca cần server nhận trước, ví dụ đường ghi chứng từ hộ xe khác.
     */
    private static class RecordingHttpClient extends HttpClient {
        boolean echoPost = false;

        RecordingHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            if (!echoPost || refuelData == null) return null;
            RefuelItemData response = new com.google.gson.GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE)
                    .create()
                    .fromJson(refuelData.toJson(), RefuelItemData.class);
            response.setApplied(true);
            return response;
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            return null;
        }
    }
}
