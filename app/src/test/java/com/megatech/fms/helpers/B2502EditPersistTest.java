package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.B2502NewItemFragement;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.TruckFuel;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.TruckModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bản sửa phiếu 2502 phải còn sau khi đóng mở lại, và phải lên tới server.
 *
 * <p>Lỗi thật (2026-09-15): sửa số phiếu hoá nghiệm, lưu thành công, danh sách hiện số mới;
 * đóng mở lại thì về số cũ. Worker đồng bộ cầm bản chụp đã gửi, POST xong ghi đè cả dòng Room
 * và xoá cờ chờ gửi — người dùng lưu đúng lúc gói còn trên đường thì bản sửa mất khỏi máy và
 * không bao giờ được gửi. Chủ dự án duyệt sửa ngày 2026-09-16 (hạng mục A1, A2).
 *
 * <p>Test đi qua {@link DataHelper#postTruckFuel} và lượt {@code Synchronize()} thật; chỉ
 * server là giả, xử lý phiếu 2502 như {@code TrucksController.PostFuel} (tìm theo Id, không có
 * thì tạo mới) và {@code GetFuels}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class B2502EditPersistTest {

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    private AppDatabase db;
    private DataRepository repo;
    private FakeServer server;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(34);
        setting.setTruckNo("DEMO-03");
        ((FMSApplication) context).saveSetting(setting, false);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        repo = DataRepository.forTesting(db);
        server = new FakeServer();
        DataHelper.installTestDependencies(repo, server);
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    private TruckFuelModel newForm(String qcNo) {
        TruckFuelModel model = new TruckFuelModel();
        model.setQcNo(qcNo);
        model.setTankNo("T1");
        return model;
    }

    private TruckFuel onlyRow() {
        List<TruckFuel> rows = db.truckFuelDao().getAll();
        assertEquals("phải còn đúng một phiếu trong máy", 1, rows.size());
        return rows.get(0);
    }

    /** Người dùng mở phiếu từ danh sách, sửa số phiếu hoá nghiệm, bấm Lưu. */
    private void userEditsQcNo(String qcNo) {
        TruckFuelModel fromList = repo.getTruckFuels(new java.util.Date()).get(0);
        fromList.setQcNo(qcNo);
        DataHelper.postTruckFuel(fromList);
    }

    @Test
    public void suaSoPhieuHoaNghiemLucGoiTaoMoiConDangGuiKhongBiMat() throws Exception {
        server.duringFirstPost = model -> userEditsQcNo("QC-NEW");

        DataHelper.postTruckFuel(newForm("QC-OLD"));
        awaitSyncIdle();

        TruckFuel row = onlyRow();
        assertEquals("bản sửa phải còn trong máy sau khi đồng bộ", "QC-NEW", row.getQcNo());
        assertEquals("bản sửa phải lên tới server", "QC-NEW", server.lastQc);
        assertEquals("bản sửa phải cập nhật đúng phiếu đã tạo, không tạo phiếu thứ hai",
                1, server.stored.size());
        assertFalse("gửi xong thì không còn chờ gửi", row.isLocalModified());
        assertEquals(100, row.getId());
    }

    @Test
    public void xoaPhieuLucGoiConDangGuiKhongBiKhoiPhucLai() throws Exception {
        server.duringFirstPost = model -> DataHelper.deleteTruckFuels(
                new int[]{db.truckFuelDao().getAll().get(0).getLocalId()});

        DataHelper.postTruckFuel(newForm("QC-OLD"));
        awaitSyncIdle();

        assertTrue("phiếu đã xoá không được hiện lại", onlyRow().isDeleted());
        assertTrue("lệnh xoá phải lên tới server", server.stored.get(0).isDeleted());
    }

    @Test
    public void luuBinhThuongChiGuiMotLanRoiHetChoGui() throws Exception {
        DataHelper.postTruckFuel(newForm("QC-OLD"));
        awaitSyncIdle();

        TruckFuel row = onlyRow();
        assertEquals(1, server.posts);
        assertFalse(row.isLocalModified());
        assertEquals(100, row.getId());
        assertEquals("QC-OLD", row.getQcNo());
    }

    /** Hộp thoại sửa phải làm trên bản sao: bấm Quay lại thì danh sách không đổi. */
    @Test
    public void hopThoaiSuaKhongDungVaoDongCuaDanhSach() {
        TruckFuelModel inList = newForm("QC-A");

        B2502NewItemFragement dialog = B2502NewItemFragement.newInstance(inList);
        TruckFuelModel inDialog = (TruckFuelModel) dialog.getArguments().getSerializable("B2502_MODEL");

        assertNotSame("hộp thoại không được giữ chính dòng của danh sách", inList, inDialog);
        assertEquals("bản sao phải mang đủ dữ liệu", "QC-A", inDialog.getQcNo());
        assertEquals(inList.getTime(), inDialog.getTime());

        inDialog.setQcNo("QC-B");
        assertEquals("sửa trên hộp thoại mà chưa lưu thì danh sách giữ nguyên", "QC-A", inList.getQcNo());
    }

    private void awaitSyncIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        int idleStreak = 0;
        while (System.currentTimeMillis() < deadline) {
            boolean busy = DataHelper.isSyncProcessing() || DataHelper.hasPendingSyncRequest();
            idleStreak = busy ? 0 : idleStreak + 1;
            if (idleStreak >= 6) return;
            Thread.sleep(50);
        }
        throw new AssertionError("lượt đồng bộ không xong trong 20 giây");
    }

    /** Mọi endpoint khác lỗi mạng ngay; riêng phiếu 2502 xử lý như TrucksController. */
    private static class FakeServer extends HttpClient {
        final List<TruckFuelModel> stored = new ArrayList<>();
        volatile Consumer<TruckFuelModel> duringFirstPost;
        volatile String lastQc;
        volatile int posts;

        FakeServer() {
            super("test-token");
        }

        @Override public HttpResponse sendGET(String url) throws IOException { throw new IOException("offline"); }
        @Override public HttpResponse sendPOST(String url, String params) throws IOException { throw new IOException("offline"); }
        @Override public HttpResponse sendPOST(String url, String params, String contentType) throws IOException { throw new IOException("offline"); }
        @Override public HttpResponse sendFile(String url, File file) throws IOException { throw new IOException("offline"); }

        private static TruckFuelModel copy(TruckFuelModel m) {
            return GSON.fromJson(m.toJson(), TruckFuelModel.class);
        }

        @Override
        public synchronized TruckFuelModel postTruckFuel(TruckFuelModel model) {
            posts++;
            Consumer<TruckFuelModel> hook = duringFirstPost;
            duringFirstPost = null;
            if (hook != null)
                hook.accept(model);          // người dùng thao tác trong lúc gói còn trên đường

            TruckFuelModel saved = copy(model);
            if (saved.getId() == 0)
                saved.setId(100 + stored.size());
            stored.removeIf(m -> m.getId().equals(saved.getId()));
            stored.add(saved);
            lastQc = saved.getQcNo();
            return copy(saved);
        }

        @Override
        public synchronized List<TruckFuelModel> getTruckFuels() {
            List<TruckFuelModel> out = new ArrayList<>();
            for (TruckFuelModel m : stored) out.add(copy(m));
            return out;
        }
    }
}
