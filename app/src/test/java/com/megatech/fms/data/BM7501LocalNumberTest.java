package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.model.BM7501Model;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Số phiếu của BM 75.01 lấy theo số phiếu của mẻ hút, mà mẻ hút có thể được cấp số SAU lúc
 * nhân viên mở phiếu. Số nằm ở cột phẳng nên autosave (chỉ ghi JSON) không chạm tới được.
 *
 * <p>Chủ dự án chốt ngày 2026-09-23: thêm đúng một câu lệnh điền số, chỉ chạy khi số đang
 * trống — số đã cấp là bất biến.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class BM7501LocalNumberTest {

    private AppDatabase db;
    private BM7501Repository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new BM7501Repository(db.bm7501Dao());
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void phieuLapSom_soPhieuVaoDuocKhiMeHutCoSo() {
        BM7501Model m = newDraft();

        assertEquals(BM7501Repository.WriteResult.OK,
                repository.fillLocalNumber(m, "HT250810-001"));
        assertEquals("HT250810-001", m.getLocalNumber());
        assertEquals("Số phải nằm ở cột phẳng, không chỉ trong bộ nhớ",
                "HT250810-001", repository.getActive(m.getRefuelItemUniqueId()).getLocalNumber());
    }

    @Test
    public void daCoSo_khongBiGhiDe() {
        BM7501Model m = newDraft();
        repository.fillLocalNumber(m, "HT250810-001");

        assertEquals(BM7501Repository.WriteResult.INVALID_STATE,
                repository.fillLocalNumber(m, "HT250810-999"));
        assertEquals("HT250810-001", repository.getActive(m.getRefuelItemUniqueId()).getLocalNumber());
    }

    @Test
    public void phieuDaHuy_khongDienSoNua() {
        BM7501Model m = newDraft();
        repository.cancel(m, "nhập nhầm mẻ");

        assertEquals(BM7501Repository.WriteResult.INVALID_STATE,
                repository.fillLocalNumber(m, "HT250810-001"));
    }

    /** Lần ghi sau vẫn phải trúng: {@code localRevision} trong bộ nhớ đi cùng nhịp với DB. */
    @Test
    public void sauKhiDienSo_luuNoiDungVanTrung() {
        BM7501Model m = newDraft();
        repository.fillLocalNumber(m, "HT250810-001");

        m.setCustomerRepName("Nguyễn Văn A");
        assertEquals(BM7501Repository.WriteResult.OK, repository.savePayload(m));
        assertEquals("Nguyễn Văn A",
                repository.getActive(m.getRefuelItemUniqueId()).getCustomerRepName());
    }

    // ------------------------------------------------------------- hàng đợi đồng bộ

    /**
     * Phiếu sửa sau khi đã đồng bộ phải quay lại hàng đợi. Không có điều này thì mọi lần sửa
     * sau lần gửi đầu sẽ chỉ nằm trong máy, còn server giữ bản cũ.
     */
    @Test
    public void suaPhieuDaDongBo_thiPhaiGuiLai() {
        BM7501Model m = newDraft();
        repository.fillLocalNumber(m, "HT250810-001");
        repository.applySyncResult(m.getUniqueId(), 123, null);
        assertEquals(0, repository.countPendingSync());

        m.setCustomerRepName("Nguyễn Văn A");
        assertEquals(BM7501Repository.WriteResult.OK, repository.savePayload(m));

        assertEquals("Sửa xong thì phải nằm trong hàng đợi gửi", 1, repository.countPendingSync());
        assertEquals(1, repository.getPendingSync().size());
    }

    /** Phiếu bị server từ chối (409/400) thì thôi quay vòng, tới khi nhân viên sửa lại. */
    @Test
    public void phieuBiTuChoi_khongQuayVongGuiLai() {
        BM7501Model m = newDraft();
        repository.markSyncFailed(m.getUniqueId());
        assertEquals(0, repository.countPendingSync());

        m.setCustomerRepName("Nguyễn Văn A");
        repository.savePayload(m);
        assertEquals("Sửa lại thì được thử lại", 1, repository.countPendingSync());
    }

    @Test
    public void phieuDaHuy_khongGuiNua() {
        BM7501Model m = newDraft();
        repository.cancel(m, "nhập nhầm mẻ");
        assertEquals(0, repository.countPendingSync());
    }

    // ------------------------------------------------------------------ xuất phiếu

    /**
     * Xuất phiếu = chốt sổ. Chủ dự án chốt 2026-09-23: chưa xuất thì sửa và in lại thoải mái,
     * xuất rồi thì chỉ in lại được, và trạng thái phải đi lên server (để chuyển sang Omega).
     */
    @Test
    public void xuatPhieu_thiKhoaNoiDung_vaChoDongBoLai() {
        BM7501Model m = newDraft();
        repository.fillLocalNumber(m, "HT250810-001");
        repository.applySyncResult(m.getUniqueId(), 123, null);

        assertEquals(BM7501Repository.WriteResult.OK, repository.export(m, 42));

        assertEquals(BM7501Model.BusinessStatus.EXPORTED, m.getBusinessStatus());
        assertNotNull("Phải ghi mốc chốt sổ", m.getExportedAt());
        assertEquals(42, m.getExportedByUserId());
        assertEquals("Trạng thái mới phải được đẩy lên server", 1, repository.countPendingSync());

        BM7501Model reloaded = repository.getActive(m.getRefuelItemUniqueId());
        assertTrue(reloaded.isExported());
        assertFalse("Phiếu đã xuất không sửa được nữa", reloaded.isEditable());
        assertNotNull("Mốc chốt sổ phải còn sau khi đọc lại", reloaded.getExportedAt());
    }

    @Test
    public void phieuDaXuat_moiLanGhiNoiDungDeuBiTuChoi() {
        BM7501Model m = newDraft();
        repository.fillLocalNumber(m, "HT250810-001");
        repository.export(m, 42);

        m.setCustomerRepName("Sửa trộm sau khi xuất");
        assertEquals(BM7501Repository.WriteResult.INVALID_STATE, repository.savePayload(m));
        assertEquals(BM7501Repository.WriteResult.INVALID_STATE, repository.export(m, 42));

        assertNull(repository.getActive(m.getRefuelItemUniqueId()).getCustomerRepName());
    }

    /** Danh sách mẻ hút chỉ hiện số của phiếu ĐÃ XUẤT, phiếu nháp thì không. */
    @Test
    public void danhSachMeHut_chiLaySoCuaPhieuDaXuat() {
        BM7501Model draft = newDraft();
        repository.fillLocalNumber(draft, "HT250810-001");

        assertTrue("Phiếu nháp chưa được coi là đã xuất",
                repository.getActiveByRefuelItems(
                        java.util.Collections.singletonList("me-hut-1")).get("me-hut-1") != null);
        assertFalse(repository.getActiveByRefuelItems(
                java.util.Collections.singletonList("me-hut-1")).get("me-hut-1").isExported());

        repository.export(draft, 42);

        BM7501Model found = repository.getActiveByRefuelItems(
                java.util.Collections.singletonList("me-hut-1")).get("me-hut-1");
        assertTrue(found.isExported());
        assertEquals("HT250810-001", found.getLocalNumber());
    }

    private BM7501Model newDraft() {
        BM7501Model m = new BM7501Model();
        m.setRefuelItemUniqueId("me-hut-1");
        return repository.createOrGetActive(m);
    }
}
