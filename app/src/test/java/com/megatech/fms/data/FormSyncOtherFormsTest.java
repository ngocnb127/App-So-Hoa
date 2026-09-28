package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2506;
import com.megatech.fms.data.entity.BM2509;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM2504Model;
import com.megatech.fms.model.BM2506Model;
import com.megatech.fms.model.BM2509Model;
import com.megatech.fms.model.TruckModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * BM 25.03, 25.04, 25.06, 25.09 có cùng các lỗi đồng bộ đã sửa ở 25.05/25.08/23.07A
 * (xem {@link FormSyncMergeTest}), sửa theo cùng khuôn ngày 2026-09-28:
 * <ul>
 *   <li>Gửi xong không được xoá cờ chờ gửi của bản người dùng vừa lưu trong lúc gói còn trên đường.</li>
 *   <li>Lưu lại phiếu đã có id server không được ghi đè id = 0 (lượt sau POST tạo phiếu trùng).</li>
 *   <li>Phiếu tải về không được ghép hay chèn đè theo LocalId của máy khác.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class FormSyncOtherFormsTest {

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // model 25.03/25.04 đọc xe hiện tại khi khởi tạo
        TruckModel setting = new TruckModel();
        setting.setTruckId(11);
        ((FMSApplication) context).saveSetting(setting, false);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // ===== BM 25.03 =====

    private int save2503(BM2503Model model) {
        BM2503 local = BM2503.fromModel(model);
        local.setLocalModified(true);
        int localId = repo.insertBM2503(local);
        model.setLocalId(localId);
        return localId;
    }

    @Test
    public void phieu2503LuuLaiSauKhiDaGuiKhongXoaIdServer() {
        BM2503Model mine = new BM2503Model();
        mine.setTruckId(11);
        int localId = save2503(mine);
        repo.markBM2503Synced(db.bm2503Dao().getByLocalId(localId), 700);

        mine.setTruckNo("sua");
        save2503(mine);

        BM2503 row = db.bm2503Dao().getByLocalId(localId);
        assertEquals(1, db.bm2503Dao().getAll().size());
        assertEquals("id server phải được giữ", 700, row.getId());
        assertEquals(Integer.valueOf(700), row.toModel().getId());
        assertTrue(row.isLocalModified());
    }

    @Test
    public void phieu2503SuaTrongLucDangGuiVanGiuCoChoGui() {
        BM2503Model mine = new BM2503Model();
        mine.setTruckId(11);
        int localId = save2503(mine);
        BM2503 posted = db.bm2503Dao().getByLocalId(localId);

        mine.setTruckNo("luu lan hai");
        save2503(mine);

        assertFalse(repo.markBM2503Synced(posted, 800));
        BM2503 row = db.bm2503Dao().getByLocalId(localId);
        assertEquals(800, row.getId());
        assertTrue("bản lưu lần hai chưa được gửi nên phải giữ cờ", row.isLocalModified());
        assertEquals("luu lan hai", row.toModel().getTruckNo());
    }

    @Test
    public void phieu2503ChoGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        BM2503Model mine = new BM2503Model();
        mine.setTruckId(11);
        int localId = save2503(mine);

        BM2503Model other = new BM2503Model();
        other.setId(500);
        other.setLocalId(localId);
        other.setTruckId(22);
        repo.mergeRemoteBM2503(BM2503.fromModel(other));

        BM2503 kept = db.bm2503Dao().getByLocalId(localId);
        assertTrue(kept.isLocalModified());
        assertEquals(0, kept.getId());
        assertEquals(Integer.valueOf(11), kept.toModel().getTruckId());
        BM2503 remote = db.bm2503Dao().getById(500);
        assertNotNull("phiếu từ server phải được thêm thành dòng mới", remote);
        assertNotEquals(localId, remote.getLocalId());
    }

    // ===== BM 25.04 =====

    private int save2504(BM2504Model model) {
        BM2504 local = BM2504.fromModel(model);
        local.setLocalModified(true);
        int localId = repo.insertBM2504(local);
        model.setLocalId(localId);
        return localId;
    }

    @Test
    public void phieu2504LuuLaiSauKhiDaGuiKhongXoaIdServer() {
        BM2504Model mine = new BM2504Model();
        mine.setTruckId(11);
        int localId = save2504(mine);
        repo.markBM2504Synced(db.bm2504Dao().getByLocalId(localId), 700);

        mine.setTruckNo("sua");
        save2504(mine);

        BM2504 row = db.bm2504Dao().getByLocalId(localId);
        assertEquals(700, row.getId());
        assertTrue(row.isLocalModified());
        assertEquals(Integer.valueOf(700), row.toModel().getId());
    }

    @Test
    public void phieu2504DaGuiKhongBiPhieuXeKhacCungLocalIdThayMat() {
        BM2504Model mine = new BM2504Model();
        mine.setTruckId(11);
        int localId = save2504(mine);
        repo.markBM2504Synced(db.bm2504Dao().getByLocalId(localId), 300);

        // insert REPLACE giữ LocalId của server từng thay nguyên dòng đã gửi của máy này
        BM2504Model other = new BM2504Model();
        other.setId(500);
        other.setLocalId(localId);
        other.setTruckId(22);
        repo.mergeRemoteBM2504(BM2504.fromModel(other));

        BM2504 row = db.bm2504Dao().getByLocalId(localId);
        assertEquals("dòng đã gửi phải còn nguyên Id server của nó", 300, row.getId());
        assertEquals(Integer.valueOf(11), row.toModel().getTruckId());
        assertNotNull(db.bm2504Dao().getById(500));
    }

    // ===== BM 25.06 =====

    private BM2506Model new2506() {
        BM2506Model m = new BM2506Model();
        m.setTruckId(11);
        m.setDate(new Date(1_769_000_000_000L));
        return m;
    }

    private int save2506(BM2506Model model) {
        BM2506 local = BM2506.fromModel(model);
        local.setLocalModified(true);
        int localId = repo.insertBM2506(local);
        model.setLocalId(localId);
        return localId;
    }

    @Test
    public void phieu2506SuaTrongLucDangGuiVanGiuCoChoGui() {
        BM2506Model mine = new2506();
        int localId = save2506(mine);
        String sent = db.bm2506Dao().getByLocalId(localId).getJsonData();

        mine.setSampleNo("luu lan hai");
        save2506(mine);

        BM2506Model fromServer = new2506();
        fromServer.setId(900);
        assertFalse(repo.markBM2506Posted(localId, sent, BM2506.fromModel(fromServer)));

        BM2506 row = db.bm2506Dao().getByLocalId(localId);
        assertEquals(900, row.getId());
        assertTrue(row.isLocalModified());
        assertEquals("luu lan hai", row.toModel().getSampleNo());
    }

    @Test
    public void phieu2506GuiXongLayBanServerVaGiuUniqueId() {
        BM2506Model mine = new2506();
        int localId = save2506(mine);
        BM2506 posted = db.bm2506Dao().getByLocalId(localId);

        BM2506Model fromServer = new2506();
        fromServer.setId(900);
        fromServer.setSampleNo("server");
        fromServer.setUniqueId(mine.getUniqueId());
        assertTrue(repo.markBM2506Posted(localId, posted.getJsonData(), BM2506.fromModel(fromServer)));

        BM2506 row = db.bm2506Dao().getByLocalId(localId);
        assertFalse(row.isLocalModified());
        assertEquals(900, row.getId());
        assertEquals("server", row.toModel().getSampleNo());
        assertEquals("UniqueId là của máy, server nhận ra phiếu ở lần gửi sau nhờ nó",
                posted.getUniqueId(), row.getUniqueId());
        assertTrue(db.bm2506Dao().getModified().isEmpty());
    }

    @Test
    public void phieu2506BiTuChoiKhongDeBanVuaSua() {
        BM2506Model mine = new2506();
        int localId = save2506(mine);
        String sent = db.bm2506Dao().getByLocalId(localId).getJsonData();

        mine.setSampleNo("sua sau khi gui");
        save2506(mine);

        repo.markBM2506Rejected(localId, sent, "{\"SyncError\":\"loi\"}");

        BM2506 row = db.bm2506Dao().getByLocalId(localId);
        assertTrue("bản sửa mới phải còn chờ gửi", row.isLocalModified());
        assertEquals("sua sau khi gui", row.toModel().getSampleNo());
    }

    @Test
    public void phieu2506LuuLaiSauKhiDaGuiKhongXoaIdServer() {
        BM2506Model mine = new2506();
        int localId = save2506(mine);
        BM2506 posted = db.bm2506Dao().getByLocalId(localId);
        BM2506Model fromServer = new2506();
        fromServer.setId(900);
        fromServer.setUniqueId(mine.getUniqueId());
        repo.markBM2506Posted(localId, posted.getJsonData(), BM2506.fromModel(fromServer));

        mine.setSampleNo("sua");
        save2506(mine);

        BM2506 row = db.bm2506Dao().getByLocalId(localId);
        assertEquals(1, db.bm2506Dao().getModified().size());
        assertEquals(900, row.getId());
        assertEquals(Integer.valueOf(900), row.toModel().getId());
    }

    @Test
    public void phieu2506TaiVeNhanLocalIdMoi() {
        BM2506Model mine = new2506();
        int localId = save2506(mine);

        BM2506Model other = new2506();
        other.setId(500);
        other.setLocalId(localId);
        other.setTruckId(22);
        repo.mergeRemoteBM2506(BM2506.fromModel(other));

        BM2506 kept = db.bm2506Dao().getByLocalId(localId);
        assertTrue(kept.isLocalModified());
        assertEquals(Integer.valueOf(11), kept.toModel().getTruckId());
        BM2506 remote = db.bm2506Dao().getById(500);
        assertNotNull(remote);
        assertNotEquals(localId, remote.getLocalId());
    }

    // ===== BM 25.09 =====

    @Test
    public void phieu2509SuaTrongLucDangGuiVanGiuCoChoGui() {
        BM2509Model mine = new BM2509Model();
        mine.setTruckId(11);
        BM2509 local = BM2509.fromModel(mine);
        local.setLocalModified(true);
        int localId = repo.insertBM2509(local);
        mine.setLocalId(localId);
        String sent = db.bm2509Dao().getByLocalId(localId).getJsonData();

        mine.setFlightNo("VN123");
        BM2509 edited = BM2509.fromModel(mine);
        edited.setLocalModified(true);
        repo.insertBM2509(edited);

        BM2509Model fromServer = new BM2509Model();
        fromServer.setId(900);
        assertFalse(repo.markBM2509Posted(localId, sent, BM2509.fromModel(fromServer)));

        BM2509 row = db.bm2509Dao().getByLocalId(localId);
        assertEquals(900, row.getId());
        assertTrue(row.isLocalModified());
        assertEquals("VN123", row.toModel().getFlightNo());
        assertEquals("vẫn phải là một dòng", 1, db.bm2509Dao().getModified().size());
    }
}
