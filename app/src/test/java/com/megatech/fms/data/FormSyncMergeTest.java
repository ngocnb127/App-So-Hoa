package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.CheckTrucks;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.CheckTrucksModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * Đồng bộ biểu mẫu BM 25.05, 25.08, 23.07A không được làm mất hay đè phiếu của máy này.
 *
 * <p>Lỗi 2026-09-28: LocalId trong dữ liệu server là số thứ tự của MÁY ĐÃ TẠO phiếu. Lượt tải
 * về ghép theo nó (và insert REPLACE giữ nguyên nó), nên phiếu của xe khác đè lên phiếu cùng
 * localId của máy này. Cùng đợt: gửi xong thì xoá cờ chờ gửi kể cả khi người dùng vừa lưu bản
 * sửa trong lúc gói còn trên đường, và lưu lại phiếu đã có id server thì ghi đè id = 0 (POST
 * tạo phiếu trùng).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class FormSyncMergeTest {

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private CheckTrucksModel checkTrucks(int id, int localId, int truckId) {
        CheckTrucksModel m = new CheckTrucksModel();
        m.setId(id);
        m.setLocalId(localId);
        m.setTruckId(truckId);
        m.setDateCreated(new Date(1_769_000_000_000L));
        return m;
    }

    /** Đúng chuỗi thao tác của {@code DataHelper.postCheckTrucks}. */
    private int saveCheckTrucks(CheckTrucksModel model) {
        CheckTrucks local = CheckTrucks.fromModel(model);
        local.setLocalModified(true);
        int localId = repo.insertCheckTrucks(local);
        model.setLocalId(localId);
        return localId;
    }

    @Test
    public void phieu2307ChoGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        int myLocalId = saveCheckTrucks(checkTrucks(0, 0, 11));

        // phiếu của xe 22 do máy khác tạo, trùng LocalId với phiếu chưa gửi của máy này
        repo.mergeRemoteCheckTrucks(CheckTrucks.fromModel(checkTrucks(500, myLocalId, 22)));

        CheckTrucks kept = db.checkTrucksDao().getByLocalId(myLocalId);
        assertTrue("phiếu chưa gửi phải giữ cờ chờ gửi", kept.isLocalModified());
        assertEquals("phiếu chưa gửi không được mang Id của phiếu xe khác", 0, kept.getId());
        assertEquals("phiếu chưa gửi phải giữ xe của máy này",
                Integer.valueOf(11), kept.toModel().getTruckId());

        CheckTrucks other = db.checkTrucksDao().getById(500);
        assertNotNull("phiếu từ server phải được thêm thành dòng mới", other);
        assertNotEquals("phiếu từ server phải nhận localId mới", myLocalId, other.getLocalId());
    }

    @Test
    public void phieu2307DaGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        int myLocalId = saveCheckTrucks(checkTrucks(0, 0, 11));
        repo.markCheckTrucksSynced(db.checkTrucksDao().getByLocalId(myLocalId), 300);

        // insert REPLACE giữ LocalId của server từng thay nguyên dòng đã gửi của máy này
        repo.mergeRemoteCheckTrucks(CheckTrucks.fromModel(checkTrucks(500, myLocalId, 22)));

        CheckTrucks mine = db.checkTrucksDao().getByLocalId(myLocalId);
        assertEquals("dòng đã gửi phải còn nguyên Id server của nó", 300, mine.getId());
        assertEquals(Integer.valueOf(11), mine.toModel().getTruckId());
        assertEquals(2, db.checkTrucksDao().getAll().size());
    }

    @Test
    public void phieu2307DaGuiDuocCapNhatTheoIdServer() {
        int myLocalId = saveCheckTrucks(checkTrucks(0, 0, 11));
        repo.markCheckTrucksSynced(db.checkTrucksDao().getByLocalId(myLocalId), 700);

        CheckTrucksModel fromServer = checkTrucks(700, 3, 11);
        fromServer.setUserCreatedName("server");
        repo.mergeRemoteCheckTrucks(CheckTrucks.fromModel(fromServer));

        CheckTrucks row = db.checkTrucksDao().getById(700);
        assertEquals("cập nhật đúng dòng cũ, không thêm dòng", myLocalId, row.getLocalId());
        assertEquals("server", row.toModel().getUserCreatedName());
        assertFalse(row.isLocalModified());
        assertEquals(1, db.checkTrucksDao().getAll().size());
    }

    @Test
    public void phieu2307DangSuaKhongBiDuLieuServerGhiDe() {
        CheckTrucksModel mine = checkTrucks(0, 0, 11);
        int myLocalId = saveCheckTrucks(mine);
        repo.markCheckTrucksSynced(db.checkTrucksDao().getByLocalId(myLocalId), 700);

        // người dùng mở lại phiếu từ danh sách, sửa, chưa kịp gửi lần hai
        CheckTrucksModel edited = db.checkTrucksDao().getByLocalId(myLocalId).toModel();
        edited.setUserCreatedName("sua tren may");
        saveCheckTrucks(edited);

        CheckTrucksModel fromServer = checkTrucks(700, 3, 11);
        fromServer.setUserCreatedName("ban cu tren server");
        repo.mergeRemoteCheckTrucks(CheckTrucks.fromModel(fromServer));

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertTrue(row.isLocalModified());
        assertEquals("bản sửa trên máy phải còn", "sua tren may", row.toModel().getUserCreatedName());
        assertEquals("toModel phải mang Id server để lần gửi sau là sửa, không tạo phiếu mới",
                Integer.valueOf(700), row.toModel().getId());
    }

    @Test
    public void phieu2307SuaTrongLucDangGuiVanGiuCoChoGui() {
        CheckTrucksModel mine = checkTrucks(0, 0, 11);
        int myLocalId = saveCheckTrucks(mine);
        CheckTrucks posted = db.checkTrucksDao().getByLocalId(myLocalId);

        // lưu lại phiếu trong lúc worker đồng bộ đang gửi bản cũ
        mine.setUserCreatedName("luu lan hai");
        saveCheckTrucks(mine);

        assertFalse(repo.markCheckTrucksSynced(posted, 800));

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertEquals("vẫn phải là một dòng", 1, db.checkTrucksDao().getAll().size());
        assertEquals(800, row.getId());
        assertTrue("bản lưu lần hai chưa được gửi nên phải giữ cờ", row.isLocalModified());
        assertEquals("luu lan hai", row.toModel().getUserCreatedName());
    }

    @Test
    public void phieu2307GuiXongKhongSuaThiBoCo() {
        int myLocalId = saveCheckTrucks(checkTrucks(0, 0, 11));

        assertTrue(repo.markCheckTrucksSynced(db.checkTrucksDao().getByLocalId(myLocalId), 900));

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertFalse(row.isLocalModified());
        assertEquals(900, row.getId());
        assertEquals(Integer.valueOf(900), row.toModel().getId());
    }

    /**
     * Model trong form vẫn cầm Id = 0 sau khi worker đã gắn id server vào dòng. Lưu lại model
     * đó từng đè dòng về id = 0 và lượt sau POST tạo phiếu thứ hai trên server.
     */
    @Test
    public void luuLaiSauKhiDaGuiKhongXoaIdServer() {
        CheckTrucksModel mine = checkTrucks(0, 0, 11);
        int myLocalId = saveCheckTrucks(mine);
        repo.markCheckTrucksSynced(db.checkTrucksDao().getByLocalId(myLocalId), 700);

        mine.setUserCreatedName("sua sau khi gui");
        saveCheckTrucks(mine);

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertEquals(1, db.checkTrucksDao().getAll().size());
        assertEquals("id server phải được giữ", 700, row.getId());
        assertEquals(Integer.valueOf(700), row.toModel().getId());
        assertTrue(row.isLocalModified());
    }

    @Test
    public void luuLaiCungModelKhongTaoPhieuTrung() {
        CheckTrucksModel mine = checkTrucks(0, 0, 11);
        saveCheckTrucks(mine);
        saveCheckTrucks(mine);
        assertEquals(1, db.checkTrucksDao().getAll().size());
    }

    @Test
    public void phieu2508ChoGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        BM2508Model mine = new BM2508Model();
        mine.setTruckId(11);
        BM2508 local = BM2508.fromModel(mine);
        local.setLocalModified(true);
        int myLocalId = repo.insertBM2508(local);

        BM2508Model other = new BM2508Model();
        other.setId(500);
        other.setLocalId(myLocalId);
        other.setTruckId(22);
        repo.mergeRemoteBM2508(BM2508.fromModel(other));

        BM2508 kept = db.bm2508Dao().getByLocalId(myLocalId);
        assertTrue(kept.isLocalModified());
        assertEquals(0, kept.getId());
        assertEquals(Integer.valueOf(11), kept.toModel().getTruckId());
        assertNotNull(db.bm2508Dao().getById(500));
    }

    /**
     * Chữ ký BM 25.08 đi theo post2; hàng đợi api/bm2508/multipart đã bỏ (server trả 400 kể cả
     * khi Id đúng, đo trên máy ảo 2026-09-28). Cờ ảnh còn sót từ bản cũ phải được gỡ, nếu không
     * phiếu bị loại khỏi lượt tải về và khỏi dọn dữ liệu cũ mãi mãi — nhưng không đụng cờ chờ gửi.
     */
    @Test
    public void goCoAnhCuKhongDungCoChoGui() {
        BM2508Model mine = new BM2508Model();
        mine.setTruckId(11);
        BM2508 local = BM2508.fromModel(mine);
        local.setLocalModified(true);
        local.setAttachmentPending(true);
        int myLocalId = repo.insertBM2508(local);

        repo.clearBM2508AttachmentPending();

        BM2508 row = db.bm2508Dao().getByLocalId(myLocalId);
        assertFalse(row.isAttachmentPending());
        assertTrue("phiếu chờ gửi phải giữ cờ chờ gửi", row.isLocalModified());
    }

    @Test
    public void phieu2505ChoGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        BM2505Model mine = new BM2505Model();
        mine.setTruckId(11);
        BM2505 local = BM2505.fromModel(mine);
        local.setLocalModified(true);
        int myLocalId = repo.insertBM2505(local);

        BM2505Model other = new BM2505Model();
        other.setId(500);
        other.setLocalId(myLocalId);
        other.setTruckId(22);
        repo.mergeRemoteBM2505(BM2505.fromModel(other));

        BM2505 kept = db.bm2505Dao().getByLocalId(myLocalId);
        assertTrue(kept.isLocalModified());
        assertEquals(0, kept.getId());
        assertEquals(11, kept.toModel().getTruckId());
        assertNotNull(db.bm2505Dao().getById(500));
    }
}
