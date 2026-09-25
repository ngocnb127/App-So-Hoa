package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/**
 * MỤC 20 — server trả mã TỪ CHỐI (409/412/423) khi POST phiếu tra nạp.
 *
 * <p>Trước bản vá, mọi mã khác 200 rơi vào cùng một nhánh {@code newData == null} rồi
 * {@code continue}: row vẫn dirty nên vòng đồng bộ POST LẠI MÃI, mỗi lượt một lần, không ai
 * biết lý do.
 *
 * <p>Ba điều BẮT BUỘC được khoá ở đây:
 * <ul>
 *   <li>Row KHÔNG rơi khỏi hàng đợi — vẫn {@code localModified}, vẫn đọc được bằng
 *       {@code getModifiedRefuel()}.</li>
 *   <li>{@code postStatus} KHÔNG bao giờ bị đặt thành ERROR (row sẽ bị loại khỏi truy vấn
 *       hàng đợi và dữ liệu người dùng đứng lại vĩnh viễn trên máy).</li>
 *   <li>Vòng thử lại HỘI TỤ: sau số lượt tối đa thì DỪNG thử lại tự động, không quay vô hạn.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class PostConflictHandlingTest {

    private static final String UID = "6e0d4f2a-1c33-4a52-9f0e-2ad91f7c5b10";

    private AppDatabase db;
    private DataRepository repo;
    private ConflictHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(173);
        ((FMSApplication) context).saveSetting(setting, false);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new ConflictHttpClient();
        DataHelper.installTestDependencies(repo, http);
        DataHelper.lockSync();
        PostConflictPolicy.shared().clearAll();
    }

    @After
    public void tearDown() {
        PostConflictPolicy.shared().clearAll();
        DataHelper.resetTestDependencies();
        db.close();
    }

    // ----------------------------------------------- chính sách thuần Java

    @Test
    public void chiBaMaXungDotDuocXuLyRieng() {
        assertTrue(PostConflictPolicy.isConflictCode(409));
        assertTrue(PostConflictPolicy.isConflictCode(412));
        assertTrue(PostConflictPolicy.isConflictCode(423));
        assertFalse(PostConflictPolicy.isConflictCode(200));
        assertFalse(PostConflictPolicy.isConflictCode(500));
        assertFalse(PostConflictPolicy.isConflictCode(504));
    }

    /** Điều kiện dừng rõ ràng: hết lượt thì chuyển sang chờ người dùng, không quay mãi. */
    @Test
    public void vongThuLaiHoiTuSauSoLuotToiDa() {
        PostConflictPolicy policy = new PostConflictPolicy();
        long now = 1_000_000L;

        for (int i = 1; i <= PostConflictPolicy.MAX_AUTO_RETRIES; i++) {
            assertEquals("lượt " + i, PostConflictPolicy.Action.VERIFY_AND_BACKOFF,
                    policy.onConflict(UID, 7, 409, now));
        }
        assertEquals(PostConflictPolicy.Action.WAIT_FOR_USER,
                policy.onConflict(UID, 7, 409, now));

        // Đã dừng: mọi lượt đồng bộ sau đều bỏ qua, kể cả rất lâu về sau.
        assertTrue(policy.isWaitingForUser(UID, 7));
        assertTrue(policy.shouldDefer(UID, 7, now + 365L * 24 * 3600 * 1000));
    }

    @Test
    public void trongThoiGianBackoffThiHoanRoiTuMoLai() {
        PostConflictPolicy policy = new PostConflictPolicy();
        long now = 1_000_000L;

        policy.onConflict(UID, 7, 409, now);
        assertTrue(policy.shouldDefer(UID, 7, now + 1000));
        assertFalse(policy.shouldDefer(UID, 7, now + PostConflictPolicy.BACKOFF_MS[0] + 1));
    }

    /** Phiên bản mới chưa hề thất bại lần nào — hoãn nó là hoãn oan. */
    @Test
    public void phienBanMoiCuaCungPhieuBatDauLaiTuDau() {
        PostConflictPolicy policy = new PostConflictPolicy();
        long now = 1_000_000L;
        for (int i = 0; i <= PostConflictPolicy.MAX_AUTO_RETRIES; i++)
            policy.onConflict(UID, 7, 409, now);
        assertTrue(policy.shouldDefer(UID, 7, now));

        assertFalse(policy.shouldDefer(UID, 8, now));
        assertEquals(PostConflictPolicy.Action.VERIFY_AND_BACKOFF,
                policy.onConflict(UID, 8, 409, now));
    }

    @Test
    public void postThanhCongThiQuenTrangThaiXungDot() {
        PostConflictPolicy policy = new PostConflictPolicy();
        policy.onConflict(UID, 7, 409, 1_000_000L);
        policy.clear(UID);
        assertFalse(policy.shouldDefer(UID, 7, 1_000_000L));
        assertEquals(0, policy.attempts(UID));
    }

    // --------------------------------------------- tích hợp với hàng đợi Room

    @Test
    public void maXungDotKhongLamMatRowKhoiHangDoiVaKhongDatERROR() {
        seedDirtyRow();
        http.responseCode = 409;

        DataHelper.syncModifiedRefuels();

        assertEquals("phải có đúng một lượt POST", 1, http.postCount);
        RefuelItem row = repo.getRefuel(UID);
        assertNotNull(row);
        assertTrue("row phải Ở LẠI hàng đợi", row.isLocalModified());
        // Đặt ERROR là loại row khỏi truy vấn hàng đợi (RefuelItemDao lọc postStatus <> 2)
        // — đúng cái "dữ liệu treo" mà bản vá này phải diệt.
        assertFalse("KHÔNG được đặt postStatus = ERROR",
                row.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR);

        List<RefuelItem> queue = repo.getModifiedRefuel();
        assertEquals(1, queue.size());
        assertEquals(UID, queue.get(0).getUniqueId());
    }

    /**
     * Không có bản vá thì mỗi lượt đồng bộ lại bắn thêm một POST vô ích. Có backoff thì các
     * lượt ngay sau bị hoãn, và sau khi hết lượt thì dừng hẳn — nhưng row vẫn nằm trong hàng đợi.
     */
    @Test
    public void cacLuotDongBoTiepTheoBiHoanChuKhongBanLienTuc() {
        seedDirtyRow();
        http.responseCode = 409;

        for (int i = 0; i < 5; i++) DataHelper.syncModifiedRefuels();

        assertEquals("chỉ lượt đầu được gửi, các lượt sau bị backoff hoãn",
                1, http.postCount);
        RefuelItem row = repo.getRefuel(UID);
        assertTrue(row.isLocalModified());
        assertFalse(row.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR);
    }

    /** Mã không thuộc nhóm xung đột giữ nguyên hành vi cũ: không backoff, không dừng. */
    @Test
    public void maLoiKhacKhongKichHoatChinhSachXungDot() {
        seedDirtyRow();
        http.responseCode = 500;

        DataHelper.syncModifiedRefuels();
        DataHelper.syncModifiedRefuels();

        assertEquals(2, http.postCount);
        assertFalse(PostConflictPolicy.shared().shouldDefer(UID, 1, System.currentTimeMillis()));
    }

    private void seedDirtyRow() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2087400);
        data.setUniqueId(UID);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setFlightId(1265515);
        data.setFlightCode("VN1268");
        data.setFlightUniqueId("0a0e5d2f-7d4f-4b6e-9a5f-1b2c3d4e5f60");
        data.setTruckId(173);
        data.setRealAmount(1200);
        data.setStartNumber(60165130);
        data.setEndNumber(60166330);
        data.setDensity(0.781);
        data.setEndTime(new Date(1_769_000_000_000L));
        data.setRefuelTime(new Date(1_769_000_000_000L));
        data.setClientSeq(1);
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(true);
        repo.insertRefuel(row);
    }

    /**
     * HttpClient giả trả về mã lỗi đã khai báo, đi đúng qua {@code noteRefuelPostStatus} như
     * tầng mạng thật, để {@code postRefuelWithStatus} đọc lại được mã đó.
     */
    private static class ConflictHttpClient extends HttpClient {
        int responseCode = 409;
        int postCount;
        int getCount;

        ConflictHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            postCount++;
            noteRefuelPostStatus(responseCode, "{\"Message\":\"conflict\"}");
            return null;
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            getCount++;
            return null;
        }

        @Override
        public RefuelItemData getRefuelItem(Integer id) {
            getCount++;
            return null;
        }

        @Override
        public List<RefuelItemData> getModifiedRefuels(Integer type, Date lastModified) {
            return null;
        }
    }
}
