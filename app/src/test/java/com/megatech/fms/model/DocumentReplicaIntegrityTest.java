package com.megatech.fms.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.RefuelItemData.CURRENCY;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/** Chứng từ chỉ đọc replica xe khác; header luôn thuộc mẻ xe hiện tại. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class DocumentReplicaIntegrityTest {

    @Before
    public void setUpApplicationSetting() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckNo("HAN3-20-7002");
        setting.setTruckId(7002);
        setting.setReceiptCode("0101");
        setting.setReceiptCount(1);
        ((FMSApplication) context).saveSetting(setting, false);
    }

    @Test
    public void invoiceUsesExplicitCurrentHeaderWithoutReorderingOrEnrichingForeignInput() {
        RefuelItemData current = printable("current", 100,
                new Date(2_000), new Date(3_000));
        AirlineModel currentAirline = new AirlineModel();
        currentAirline.setId(8);
        currentAirline.setName("Current Airline");
        currentAirline.setCode("VN");
        currentAirline.setProductName("JET A-1");
        current.setAirlineId(8);
        current.setAirlineModel(currentAirline);
        current.setInvoiceNameCharter(null); // builder phải fallback null-safe
        current.setPrice(4.06);
        current.setTaxRate(0.1);
        current.setCurrency(CURRENCY.USD);

        RefuelItemData foreign = printable("foreign", 200,
                new Date(1_000), new Date(5_000));
        foreign.setAirlineId(8);
        foreign.setAirlineModel(null); // child không cần denormalized master object
        foreign.setPrice(99);
        foreign.setCurrency(CURRENCY.VND);

        ArrayList<RefuelItemData> input = new ArrayList<>(Arrays.asList(foreign, current));

        InvoiceModel invoice = InvoiceModel.fromRefuel(current, input);

        assertEquals("Current Airline", invoice.getCustomerName());
        assertEquals(4.06, invoice.getPrice(), 0d);
        assertEquals(CURRENCY.USD, invoice.getCurrency());
        assertEquals(new Date(1_000), invoice.getStartTime());
        assertEquals(new Date(5_000), invoice.getEndTime());
        assertEquals(new Date(5_000), invoice.getDate());
        assertEquals(2, input.size());
        assertSame("builder không được sort list checkbox của Activity", foreign, input.get(0));
        assertSame(current, input.get(1));
    }

    @Test
    public void invoiceRejectsMissingMeasuredTimeWithDomainErrorInsteadOfNullPointer() {
        RefuelItemData current = printable("current", 100, new Date(1_000), new Date(2_000));
        current.setAirlineModel(airline());
        RefuelItemData foreign = printable("foreign", 200, null, new Date(3_000));

        try {
            InvoiceModel.fromRefuel(current,
                    new ArrayList<>(Arrays.asList(current, foreign)));
            fail("Phải chặn mẻ thiếu giờ");
        } catch (IllegalArgumentException ex) {
            // Thông báo có ngữ nghĩa để Activity hiển thị, không phải NPE từ compareTo().
            org.junit.Assert.assertTrue(ex.getMessage().contains("thiếu giờ"));
        }
    }

    @Test
    public void invoiceRejectsSelectionWithoutPositiveAmountBeforeIndexingItems() {
        RefuelItemData current = printable("current", 0, new Date(1_000), new Date(2_000));
        current.setAirlineModel(airline());

        try {
            InvoiceModel.fromRefuel(current,
                    new ArrayList<>(Arrays.asList(current)));
            fail("Phải chặn tập không có sản lượng");
        } catch (IllegalArgumentException ex) {
            org.junit.Assert.assertTrue(ex.getMessage().contains("sản lượng"));
        }
    }

    @Test
    public void receiptLineDerivesVolumeWithoutMutatingForeignReplica() throws Exception {
        RefuelItemData foreign = printable("foreign", 100,
                new Date(1_000), new Date(2_000));
        foreign.setVolume(1); // mô phỏng field legacy stale; getter vẫn suy đúng từ gallon
        String before = foreign.toJson();

        ReceiptModel receipt = new ReceiptModel();
        Method addItem = ReceiptModel.class.getDeclaredMethod(
                "addItem", ReceiptModel.class, RefuelItemData.class);
        addItem.setAccessible(true);
        addItem.invoke(null, receipt, foreign);

        assertEquals("builder không được sửa source replica", before, foreign.toJson());
        assertEquals(foreign.getVolume(), receipt.getItems().get(0).getVolume(), 0d);
    }

    /**
     * Khối lượng cũng phải suy từ GALLON, không được đi theo số lít cũ.
     *
     * <p>{@code RefuelItemData} không có field khối lượng, nên dòng in tự tính nó từ số lít.
     * Nếu số lít trong bộ nhớ đã stale mà chỉ sửa mỗi số lít thì khối lượng vẫn có thể mang
     * số của lần cập nhật trước — tờ giấy ra ngoài với số ký sai.
     */
    @Test
    public void receiptLineDerivesWeightFromGallonNotFromStaleVolume() throws Exception {
        RefuelItemData item = printable("stale-weight", 100,
                new Date(1_000), new Date(2_000));
        item.setVolume(1); // field legacy stale: 1 lít

        ReceiptItemModel line = buildReceiptLine(item);

        double expectedVolume = Math.round(100d * RefuelItemData.GALLON_TO_LITTER);
        assertEquals(expectedVolume, line.getVolume(), 0d);
        assertEquals(Math.round(expectedVolume * 0.8), line.getWeight(), 0d);
        // Nếu khối lượng đi theo số lít cũ thì kết quả là round(1 × 0,8) = 1.
        org.junit.Assert.assertNotEquals(1d, line.getWeight(), 0d);
    }

    /** Dữ liệu vốn đã đúng thì kết quả không đổi — đây là đường đi bình thường. */
    @Test
    public void receiptLineKeepsVolumeAndWeightThatAlreadyMatchGallon() throws Exception {
        RefuelItemData item = printable("clean", 100,
                new Date(1_000), new Date(2_000));
        double correctVolume = Math.round(100d * RefuelItemData.GALLON_TO_LITTER);
        item.setVolume(correctVolume);

        ReceiptItemModel line = buildReceiptLine(item);

        assertEquals(correctVolume, line.getVolume(), 0d);
        assertEquals(Math.round(correctVolume * 0.8), line.getWeight(), 0d);
    }

    private static ReceiptItemModel buildReceiptLine(RefuelItemData item) throws Exception {
        ReceiptModel receipt = new ReceiptModel();
        Method addItem = ReceiptModel.class.getDeclaredMethod(
                "addItem", ReceiptModel.class, RefuelItemData.class);
        addItem.setAccessible(true);
        addItem.invoke(null, receipt, item);
        return receipt.getItems().get(0);
    }

    @Test
    public void foreignWeightNoteCannotOverrideCurrentHeaderTechLog() {
        RefuelItemData current = printable("current", 100,
                new Date(1_000), new Date(61_000));
        current.setAirlineId(8);
        current.setAirlineModel(airline());
        current.setWeightNote("12.5");
        current.setReceiptNumber("EXISTING");

        RefuelItemData foreign = printable("foreign", 200,
                new Date(2_000), new Date(62_000));
        foreign.setAirlineId(8);
        foreign.setWeightNote("99.9");

        ArrayList<RefuelItemData> items = new ArrayList<>(Arrays.asList(current, foreign));
        InvoiceModel invoice = InvoiceModel.fromRefuel(current, items);
        ReceiptModel receipt = ReceiptModel.createReceipt(
                items, null, false, "EXISTING", false);

        assertEquals(12.5, invoice.getTechLog(), 0d);
        assertEquals(12.5, receipt.getTechLog(), 0d);
    }

    private static RefuelItemData printable(
            String uid, double amount, Date start, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId(uid);
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(amount);
        item.setStartNumber(1_000);
        item.setEndNumber(1_000 + amount);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setDensity(0.8);
        item.setManualTemperature(25);
        item.setCurrency(CURRENCY.USD);
        item.setPrice(4);
        item.setTaxRate(0);
        item.setProductName("JET A-1");
        item.setPName("JET A-1");
        item.setPCode("JET-A1");
        item.setAircraftCode("VN-A123");
        item.setAircraftType("B777");
        item.setRouteName("HAN-SGN");
        return item;
    }

    private static AirlineModel airline() {
        AirlineModel airline = new AirlineModel();
        airline.setId(8);
        airline.setName("Current Airline");
        airline.setCode("VN");
        airline.setProductName("JET A-1");
        return airline;
    }
}
