package com.megatech.fms.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.RefuelItemData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/**
 * TEST DÒ của tác nhân P2 — CHỈ để xác minh các NGHI NGỜ N1/N3/N6/N8 trước khi lên phương án.
 *
 * <p><b>Tệp này có thể XOÁ.</b> Nó không canh một luật nghiệp vụ nào; nó chỉ ghi lại hành vi
 * thật của thư viện/SQLite để phương án sửa đứng trên bằng chứng thay vì suy đoán. Khi các
 * phát hiện tương ứng đã được sửa và có test chính thức canh, hãy xoá tệp này.
 *
 * <p>Viết ngày 2026-09-06.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class P2ProbeTest {

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private static RefuelItem row(int localId, String uid, String truckNo, int truckId) {
        RefuelItem item = new RefuelItem();
        item.setLocalId(localId);
        item.setUniqueId(uid);
        item.setTruckNo(truckNo);
        item.setTruckId(truckId);
        item.setRefuelTime(new Date(1_700_000_000_000L));
        item.setJsonData("{\"UniqueId\":\"" + uid + "\"}");
        return item;
    }

    // ------------------------------------------------------------------
    // N8 + F2 — truckNo NULL rơi khỏi CẢ HAI truy vấn danh sách
    // ------------------------------------------------------------------

    /**
     * N8/F2: chứng minh cơ chế SQL, độc lập với việc server trả NULL hay chuỗi rỗng.
     * Row truckNo NULL không thoả `= :truckNo` lẫn `!= :truckNo`; row chuỗi rỗng thì thoả `!=`.
     */
    @Test
    public void probe_rowTruckNoNullRoiKhoiCaHaiDanhSach() {
        db.refuelItemDao().insert(row(0, "uid-own", "DEMO-03", 34));
        db.refuelItemDao().insert(row(0, "uid-null", null, 0));
        db.refuelItemDao().insert(row(0, "uid-empty", "", 0));

        List<RefuelItem> self = db.refuelItemDao().getByTruckNo("DEMO-03");
        List<RefuelItem> others = db.refuelItemDao().getOthers("DEMO-03");

        assertEquals("tab của xe chỉ có đúng phiếu của xe", 1, self.size());
        assertEquals("tab chuyến khác KHÔNG chứa row truckNo NULL,"
                + " chỉ chứa row chuỗi rỗng", 1, others.size());
        assertEquals("uid-empty", others.get(0).getUniqueId());

        assertNotNull("row NULL vẫn nằm trong Room", db.refuelItemDao().get("uid-null"));
    }

    // ------------------------------------------------------------------
    // N1 + N8 — payload server thiếu khoá TruckNo cho truckNo null
    // ------------------------------------------------------------------

    /** N1/N8: JSON không có khoá TruckNo ⇒ getTruckNo() trả null (không phải chuỗi rỗng). */
    @Test
    public void probe_payloadThieuKhoaTruckNoChoTruckNoNull() {
        RefuelItemData item = RefuelItemData.fromJson(
                "{\"UniqueId\":\"u1\",\"FlightCode\":\"VN 1237\",\"TruckId\":0}");
        assertNull("Gson để nguyên null khi payload không có khoá TruckNo", item.getTruckNo());

        RefuelItemData explicitNull = RefuelItemData.fromJson(
                "{\"UniqueId\":\"u2\",\"TruckNo\":null}");
        assertNull("payload TruckNo:null cũng cho null", explicitNull.getTruckNo());

        RefuelItemData empty = RefuelItemData.fromJson(
                "{\"UniqueId\":\"u3\",\"TruckNo\":\"\"}");
        assertEquals("payload TruckNo:\"\" cho chuỗi rỗng chứ không null", "", empty.getTruckNo());
    }

    // ------------------------------------------------------------------
    // N6 — PAUSED và ERROR cùng @SerializedName("2")
    // ------------------------------------------------------------------

    /** N6: hai hằng số cùng tên tuần tự "2" — xem Gson thật sự đọc/ghi ra cái nào. */
    @Test
    public void probe_trangThaiPausedVaErrorTrungTenTuanTu() {
        Gson gson = new Gson();

        REFUEL_ITEM_STATUS parsed = gson.fromJson("\"2\"", REFUEL_ITEM_STATUS.class);
        String pausedOut = gson.toJson(REFUEL_ITEM_STATUS.PAUSED);
        String errorOut = gson.toJson(REFUEL_ITEM_STATUS.ERROR);

        System.out.println("N6 parse(\"2\") = " + parsed);
        System.out.println("N6 toJson(PAUSED) = " + pausedOut);
        System.out.println("N6 toJson(ERROR) = " + errorOut);

        RefuelItemData item = RefuelItemData.fromJson("{\"UniqueId\":\"u\",\"Status\":2}");
        System.out.println("N6 RefuelItemData Status=2 -> " + item.getStatus());

        assertNotNull(parsed);
    }

    // ------------------------------------------------------------------
    // N3 — Gallon (field) trôi khỏi RealAmount trong JSON và lên tới dòng phiếu
    // ------------------------------------------------------------------

    /**
     * N3: getGallon() luôn trả round(realAmount) nên KHÔNG trôi trong RAM, nhưng Gson tuần tự
     * hoá FIELD nên khoá "Gallon" trong JSON trôi được — và dòng chứng từ dựng từ chính JSON đó.
     */
    @Test
    public void probe_khoaGallonTrongJsonTroiKhoiRealAmount() {
        RefuelItemData item = RefuelItemData.fromJson(
                "{\"UniqueId\":\"u\",\"RealAmount\":2101,\"Gallon\":2000,\"Density\":0.8}");

        System.out.println("N3 getGallon()=" + item.getGallon()
                + " getRealAmount()=" + item.getRealAmount()
                + " getVolume()=" + item.getVolume());

        assertEquals("getGallon() bám realAmount, không đọc field gallon",
                2101d, item.getGallon(), 0.001);

        // ReceiptModel dùng chính gson của BaseModel (UPPER_CAMEL_CASE) để dựng dòng in.
        Gson modelGson = new com.google.gson.GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
        ReceiptItemModel line = modelGson.fromJson(item.toJson(), ReceiptItemModel.class);
        System.out.println("N3 JSON=" + item.toJson());
        System.out.println("N3 dòng phiếu gallon=" + line.getGallon());

        assertTrue("dòng chứng từ dựng từ JSON nên nhận đúng field Gallon đã trôi (2000),"
                        + " trong khi số lít dẫn xuất từ RealAmount (2101)",
                Math.abs(line.getGallon() - item.getGallon()) > 1);
    }
}
