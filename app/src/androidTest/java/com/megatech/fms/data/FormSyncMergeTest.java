package com.megatech.fms.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

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

import java.lang.reflect.Constructor;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * Luồng tải phiếu từ server về (BM25.05, BM25.08, BM23.07A) không được ghi đè phiếu đang chờ gửi.
 *
 * <p>Lỗi 2026-09-28: LocalId trong dữ liệu server là số thứ tự của máy đã tạo phiếu, nên nó trùng
 * với phiếu chưa gửi của máy này và phiếu đó bị thay bằng dữ liệu của xe khác, mất cờ chờ gửi.
 */
@RunWith(AndroidJUnit4.class)
public class FormSyncMergeTest {

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        // DataRepository là singleton gắn với DB thật của app, test cần bản riêng trên DB trong bộ nhớ
        Constructor<DataRepository> ctor = DataRepository.class.getDeclaredConstructor(AppDatabase.class);
        ctor.setAccessible(true);
        repo = ctor.newInstance(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private CheckTrucksModel checkTrucksModel(int id, int localId, int truckId) {
        CheckTrucksModel m = new CheckTrucksModel();
        m.setId(id);
        m.setLocalId(localId);
        m.setTruckId(truckId);
        m.setDateCreated(new Date());
        return m;
    }

    private int savePendingCheckTrucks(CheckTrucksModel model) {
        CheckTrucks local = CheckTrucks.fromModel(model);
        local.setLocalModified(true);
        int localId = repo.insertCheckTrucks(local);
        model.setLocalId(localId);
        return localId;
    }

    @Test
    public void phieu2307ChoGuiKhongBiPhieuXeKhacCungLocalIdGhiDe() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        int myLocalId = savePendingCheckTrucks(mine);

        // phiếu của xe 22 do máy khác tạo, trùng LocalId với phiếu chưa gửi của máy này
        repo.mergeCheckTrucksFromServer(CheckTrucks.fromModel(checkTrucksModel(500, myLocalId, 22)));

        CheckTrucks kept = db.checkTrucksDao().getByLocalId(myLocalId);
        assertNotNull("phiếu chưa gửi phải còn", kept);
        assertTrue("phiếu chưa gửi phải giữ cờ chờ gửi", kept.isLocalModified());
        assertEquals("phiếu chưa gửi không được mang Id của phiếu xe khác", 0, kept.getId());
        assertEquals("phiếu chưa gửi phải giữ xe của máy này", Integer.valueOf(11), kept.toModel().getTruckId());

        CheckTrucks other = db.checkTrucksDao().getById(500);
        assertNotNull("phiếu từ server phải được thêm thành dòng mới", other);
        assertNotEquals("phiếu từ server phải có localId mới", myLocalId, other.getLocalId());
        assertEquals(1, db.checkTrucksDao().getModified().size());
    }

    @Test
    public void phieu2307DaGuiDuocCapNhatTheoIdServer() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        int myLocalId = savePendingCheckTrucks(mine);
        CheckTrucks pending = db.checkTrucksDao().getByLocalId(myLocalId);
        repo.markCheckTrucksPosted(pending, pending.getJsonData(), 700, checkTrucksModel(700, 999, 11).toJson());

        CheckTrucksModel fromServer = checkTrucksModel(700, 3, 11);
        fromServer.setUserCreatedName("server");
        repo.mergeCheckTrucksFromServer(CheckTrucks.fromModel(fromServer));

        CheckTrucks row = db.checkTrucksDao().getById(700);
        assertEquals("cập nhật đúng dòng cũ, không thêm dòng", myLocalId, row.getLocalId());
        assertEquals("server", row.toModel().getUserCreatedName());
        assertFalse(row.isLocalModified());
        assertEquals(1, db.checkTrucksDao().getAll().size());
    }

    @Test
    public void phieu2307DangSuaKhongBiDuLieuServerGhiDe() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        int myLocalId = savePendingCheckTrucks(mine);
        CheckTrucks pending = db.checkTrucksDao().getByLocalId(myLocalId);
        repo.markCheckTrucksPosted(pending, pending.getJsonData(), 700, mine.toJson());

        // người dùng sửa lại phiếu đã gửi, chưa kịp gửi lần hai
        CheckTrucksModel edited = db.checkTrucksDao().getByLocalId(myLocalId).toModel();
        edited.setUserCreatedName("sua tren may");
        CheckTrucks editedEntity = CheckTrucks.fromModel(edited);
        editedEntity.setLocalModified(true);
        repo.insertCheckTrucks(editedEntity);

        CheckTrucksModel fromServer = checkTrucksModel(700, 3, 11);
        fromServer.setUserCreatedName("ban cu tren server");
        repo.mergeCheckTrucksFromServer(CheckTrucks.fromModel(fromServer));

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertTrue(row.isLocalModified());
        assertEquals("bản sửa trên máy phải còn", "sua tren may", row.toModel().getUserCreatedName());
        assertEquals("toModel phải mang Id server để lần gửi sau là sửa, không tạo phiếu mới",
                Integer.valueOf(700), row.toModel().getId());
    }

    @Test
    public void phieu2307SuaTrongLucDangGuiVanGiuCoChoGui() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        int myLocalId = savePendingCheckTrucks(mine);
        CheckTrucks posted = db.checkTrucksDao().getByLocalId(myLocalId);
        String postedJson = posted.getJsonData();

        // lưu lại phiếu trong lúc luồng sync đang gửi bản cũ
        mine.setUserCreatedName("luu lan hai");
        savePendingCheckTrucks(mine);

        repo.markCheckTrucksPosted(posted, postedJson, 800, checkTrucksModel(800, 0, 11).toJson());

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertEquals("vẫn phải là một dòng", 1, db.checkTrucksDao().getAll().size());
        assertEquals(800, row.getId());
        assertTrue("bản lưu lần hai chưa được gửi nên phải giữ cờ", row.isLocalModified());
        assertEquals("luu lan hai", row.toModel().getUserCreatedName());
    }

    @Test
    public void phieu2307GuiXongKhongSuaThiBoCo() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        int myLocalId = savePendingCheckTrucks(mine);
        CheckTrucks posted = db.checkTrucksDao().getByLocalId(myLocalId);

        repo.markCheckTrucksPosted(posted, posted.getJsonData(), 900, checkTrucksModel(900, 0, 11).toJson());

        CheckTrucks row = db.checkTrucksDao().getByLocalId(myLocalId);
        assertFalse(row.isLocalModified());
        assertEquals(900, row.getId());
    }

    @Test
    public void luuLaiCungModelKhongTaoPhieuTrung() {
        CheckTrucksModel mine = checkTrucksModel(0, 0, 11);
        savePendingCheckTrucks(mine);
        savePendingCheckTrucks(mine);
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
        repo.mergeBM2508FromServer(BM2508.fromModel(other));

        BM2508 kept = db.bm2508Dao().getByLocalId(myLocalId);
        assertTrue(kept.isLocalModified());
        assertEquals(0, kept.getId());
        assertEquals(Integer.valueOf(11), kept.toModel().getTruckId());
        assertNotNull(db.bm2508Dao().getById(500));
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
        repo.mergeBM2505FromServer(BM2505.fromModel(other));

        BM2505 kept = db.bm2505Dao().getByLocalId(myLocalId);
        assertTrue(kept.isLocalModified());
        assertEquals(0, kept.getId());
        assertEquals(11, (int) kept.toModel().getTruckId());
        assertNotNull(db.bm2505Dao().getById(500));
    }
}
