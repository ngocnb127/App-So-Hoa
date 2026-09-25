package com.megatech.fms.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

/**
 * TEST DÒ của tác nhân P1 — chỉ để XÁC MINH các nghi ngờ N1/N3/N6 trước khi lên phương án.
 *
 * <p>KHÔNG phải test nghiệp vụ chính thức. Có thể xoá sau khi phương án được duyệt.
 * Các assert ở đây khoá lại HIỆN TRẠNG (kể cả hiện trạng sai) để chứng minh nghi ngờ,
 * chứ không phát biểu điều mong muốn.
 */
public class P1ProbeTest {

    // ---------- N6: PAUSED(2) và ERROR(4) cùng @SerializedName("2") ----------

    @Test
    public void n6_gsonDocChuoi2RaTrangThaiNao() {
        Gson gson = new Gson();
        REFUEL_ITEM_STATUS parsed = gson.fromJson("\"2\"", REFUEL_ITEM_STATUS.class);
        System.out.println("[P1-PROBE][N6] fromJson(\"2\") = " + parsed);
        System.out.println("[P1-PROBE][N6] toJson(PAUSED) = " + gson.toJson(REFUEL_ITEM_STATUS.PAUSED));
        System.out.println("[P1-PROBE][N6] toJson(ERROR)  = " + gson.toJson(REFUEL_ITEM_STATUS.ERROR));
        System.out.println("[P1-PROBE][N6] fromJson(\"4\") = "
                + gson.fromJson("\"4\"", REFUEL_ITEM_STATUS.class));
    }

    @Test
    public void n6_roundTripTrenRefuelItemData() {
        RefuelItemData item = new RefuelItemData();
        item.setStatus(REFUEL_ITEM_STATUS.PAUSED);
        String json = item.toJson();
        System.out.println("[P1-PROBE][N6] json PAUSED chứa: "
                + json.substring(Math.max(0, json.indexOf("\"Status\"")),
                Math.min(json.length(), json.indexOf("\"Status\"") + 20)));
        System.out.println("[P1-PROBE][N6] PAUSED -> json -> "
                + RefuelItemData.fromJson(json).getStatus());

        RefuelItemData err = new RefuelItemData();
        err.setStatus(REFUEL_ITEM_STATUS.ERROR);
        System.out.println("[P1-PROBE][N6] ERROR -> json -> "
                + RefuelItemData.fromJson(err.toJson()).getStatus());
    }

    /** Server gửi Status dạng SỐ (không phải chuỗi) — đây là dạng thật của payload. */
    @Test
    public void n6_payloadServerDangSo() {
        for (int s = 0; s <= 4; s++) {
            RefuelItemData item = RefuelItemData.fromJson("{\"Status\":" + s + "}");
            System.out.println("[P1-PROBE][N6] payload {\"Status\":" + s + "} -> "
                    + item.getStatus());
        }
        RefuelItemData done = RefuelItemData.fromJson("{\"Status\":3}");
        assertEquals("DONE(3) phải đọc đúng, nếu không thì cả NT2 sụp",
                REFUEL_ITEM_STATUS.DONE, done.getStatus());
    }

    // ---------- N3: Gallon trôi khỏi RealAmount ----------

    @Test
    public void n3_setGallonCoLamTroiKhongGetGallonDocTuDau() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(1000);
        item.setGallon(7777);

        System.out.println("[P1-PROBE][N3] getGallon()=" + item.getGallon()
                + " getRealAmount()=" + item.getRealAmount()
                + " getVolume()=" + item.getVolume());

        String json = item.toJson();
        int at = json.indexOf("\"Gallon\"");
        System.out.println("[P1-PROBE][N3] JSON quanh Gallon: "
                + (at < 0 ? "(không có khoá Gallon)"
                : json.substring(at, Math.min(json.length(), at + 24))));

        // getGallon() đọc từ realAmount, KHÔNG đọc field gallon.
        assertEquals("getGallon() phải trả về realAmount (hiện trạng)",
                1000d, item.getGallon(), 0.001);
    }

    @Test
    public void n3_gallonTuServerCoDeLenRealAmountKhong() {
        // Payload server đặt Gallon nhưng KHÔNG đặt RealAmount.
        String json = "{\"Gallon\":5000}";
        RefuelItemData item = new Gson().fromJson(json, RefuelItemData.class);
        System.out.println("[P1-PROBE][N3] từ {\"Gallon\":5000} -> getGallon()="
                + item.getGallon() + " getRealAmount()=" + item.getRealAmount()
                + " getVolume()=" + item.getVolume());
    }

    // ---------- N1: NPE khi truckNo null ----------

    @Test
    public void n1_truckNoNullThiGetTruckNoTraVeNull() {
        RefuelItemData item = new Gson().fromJson("{\"FlightCode\":\"VN123\"}",
                RefuelItemData.class);
        System.out.println("[P1-PROBE][N1] getTruckNo() = " + item.getTruckNo());
        assertNull("Gson KHÔNG đặt giá trị mặc định cho truckNo khi server không gửi khoá",
                item.getTruckNo());
        try {
            //noinspection ConstantConditions,ResultOfMethodCallIgnored
            item.getTruckNo().equals("DEMO-03");
            fail("Không ném NPE — nghi ngờ N1 sai");
        } catch (NullPointerException expected) {
            System.out.println("[P1-PROBE][N1] NPE đúng như dự đoán ở dạng "
                    + "item.getTruckNo().equals(...)");
        }
    }
}
