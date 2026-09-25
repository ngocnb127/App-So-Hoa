package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.data.entity.Invoice;
import com.megatech.fms.model.InvoiceModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/**
 * MỤC 6 — hoá đơn POST hai lần ⇒ TRÙNG HOÁ ĐƠN.
 *
 * <p>Hình dạng lỗi cũ: {@code postInvoice} chèn bản local (localId = 0) rồi POST;
 * {@code insertInvoice} trả {@code void} nên object Java KHÔNG nhận được localId Room vừa sinh.
 * POST xong, {@code setId(idServer)} rồi chèn lại — truy vấn {@code get(id, localId)} với
 * {@code id = idServer, localId = 0} không khớp hàng cũ ({@code id = 0, localId = N}) nên Room
 * ĐẺ THÊM MỘT HÀNG. Hàng cũ vẫn {@code isLocalModified} ⇒ vòng {@code Synchronize} POST lại ⇒
 * hai hoá đơn trên server.
 *
 * <p>Các ca ở đây tái hiện đúng CHUỖI THAO TÁC của {@code DataHelper.postInvoice} ở tầng
 * repository — nơi lỗi thực sự nằm — nên không cần dựng cả tầng mạng.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class InvoiceDuplicatePostTest {

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

    private InvoiceModel newModel() {
        InvoiceModel model = new InvoiceModel();
        model.setInvoiceNumber("00024411");
        model.setDate(new Date(1_769_000_000_000L));
        return model;
    }

    @Test
    public void luotGhiThuNhatGhiNguocLocalIdVaoObject() {
        Invoice local = Invoice.fromModel(newModel());
        local.setLocalModified(true);

        int rowId = repo.insertInvoice(local);

        assertTrue("Room phải sinh localId > 0", rowId > 0);
        assertEquals("localId phải được ghi NGƯỢC vào object, nếu không lượt ghi thứ hai sẽ"
                + " không tìm thấy hàng vừa tạo", rowId, local.getLocalId());
        assertEquals(1, db.invoiceDao().getAll().size());
    }

    /** Kịch bản đầy đủ của {@code postInvoice}: chèn dirty → POST thành công → chèn lại. */
    @Test
    public void hauPostChiConDungMotHangVaKhongConDirty() {
        InvoiceModel model = newModel();
        Invoice local = Invoice.fromModel(model);
        local.setLocalModified(true);
        repo.insertInvoice(local);

        // Server cấp id, app ghi lại như postInvoice đang làm.
        model.setId(778899);
        model.setLocalId(local.getLocalId());
        local.setId(778899);
        local.setJsonData(model.toJson());
        local.setLocalModified(false);
        repo.insertInvoice(local);

        List<Invoice> all = db.invoiceDao().getAll();
        assertEquals("phải là CẬP NHẬT hàng cũ, không phải đẻ hàng thứ hai", 1, all.size());
        assertEquals(778899, all.get(0).getId());

        // Không còn dirty ⇒ vòng Synchronize sẽ không POST lại ⇒ không trùng hoá đơn.
        assertTrue(repo.getModifiedInvoice().isEmpty());
    }

    /** Không có localId nhưng có uniqueId: vẫn phải nhận ra đúng hàng cũ. */
    @Test
    public void traTheoUniqueIdKhiObjectMatLocalId() {
        Invoice local = Invoice.fromModel(newModel());
        local.setLocalModified(true);
        repo.insertInvoice(local);
        String uid = local.getUniqueId();
        assertNotNull(uid);

        Invoice second = Invoice.fromModel(newModel());
        second.setUniqueId(uid);
        second.setId(778899);
        second.setLocalId(0);
        repo.insertInvoice(second);

        assertEquals(1, db.invoiceDao().getAll().size());
        assertEquals(local.getLocalId(), second.getLocalId());
    }

    /**
     * Điều kiện {@code uniqueId IS NOT NULL AND uniqueId <> ''} là bắt buộc: thiếu nó thì mọi
     * hàng cũ có uniqueId rỗng bị gom về một nhóm và hoá đơn khác nhau sẽ ĐÈ LÊN NHAU.
     */
    @Test
    public void haiHoaDonUniqueIdRongKhongBiGomLamMot() {
        Invoice first = Invoice.fromModel(newModel());
        first.setUniqueId("");
        repo.insertInvoice(first);

        InvoiceModel other = newModel();
        other.setInvoiceNumber("00024412");
        Invoice second = Invoice.fromModel(other);
        second.setUniqueId("");
        second.setLocalId(0);
        repo.insertInvoice(second);

        assertEquals("hai hoá đơn riêng biệt phải là hai hàng", 2,
                db.invoiceDao().getAll().size());
    }

    /** Hoá đơn thứ hai (uniqueId khác) không bao giờ được đè lên hoá đơn thứ nhất. */
    @Test
    public void hoaDonKhacNhauVanLaHaiHang() {
        Invoice first = Invoice.fromModel(newModel());
        repo.insertInvoice(first);

        InvoiceModel other = newModel();
        other.setInvoiceNumber("00024412");
        Invoice second = Invoice.fromModel(other);
        repo.insertInvoice(second);

        assertEquals(2, db.invoiceDao().getAll().size());
        assertTrue(first.getLocalId() != second.getLocalId());
    }
}
