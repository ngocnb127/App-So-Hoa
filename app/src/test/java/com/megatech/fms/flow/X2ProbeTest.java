package com.megatech.fms.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

/**
 * TEST DÒ của tác nhân soi chéo X2 — CHỈ để phân xử hai khẳng định mâu thuẫn giữa plan-P1
 * và plan-P2. KHÔNG phải test nghiệp vụ chính thức, có thể xoá.
 *
 * <p>Các assert ở đây khoá lại HIỆN TRẠNG (kể cả hiện trạng sai), không phát biểu mong muốn.
 */
public class X2ProbeTest {

    /**
     * P1 khẳng định payload {"Status":4} đọc ra null rồi gây NPE CHẮC CHẮN ở
     * data/entity/RefuelItem.java:140. P2 xếp cùng phát hiện đó mức THẤP và không nhắc NPE.
     * Đây là phép đo để phân xử.
     */
    @Test
    public void x2_status4DocRaNullVaFromRefuelItemDataCoNemNPEKhong() {
        RefuelItemData s4 = RefuelItemData.fromJson("{\"UniqueId\":\"u4\",\"Status\":4}");
        System.out.println("[X2-PROBE] payload Status=4 -> getStatus()=" + s4.getStatus());
        assertNull("hiện trạng: mã 4 không có hằng số nào nhận", s4.getStatus());

        RefuelItemData s2 = RefuelItemData.fromJson("{\"UniqueId\":\"u2\",\"Status\":2}");
        System.out.println("[X2-PROBE] payload Status=2 -> getStatus()=" + s2.getStatus());

        try {
            com.megatech.fms.data.entity.RefuelItem row =
                    com.megatech.fms.data.entity.RefuelItem.fromRefuelItemData(s4);
            System.out.println("[X2-PROBE] KHÔNG nổ NPE; row.status=" + row.getStatus());
        } catch (NullPointerException npe) {
            StackTraceElement top = npe.getStackTrace().length > 0 ? npe.getStackTrace()[0] : null;
            System.out.println("[X2-PROBE] NPE THẬT tại " + top);
            return;
        } catch (Throwable other) {
            System.out.println("[X2-PROBE] ném thứ khác NPE: " + other);
            fail("cần xem lại: " + other);
        }
    }

    /**
     * Phân xử F12/N3: P1 nói tờ phiếu KHÔNG bị ảnh hưởng vì getGallon() luôn trả
     * Math.round(realAmount); P2 nói dòng phiếu dựng từ JSON nên nhận field gallon đã trôi.
     * Dựng lại đúng gson của BaseModel (UPPER_CAMEL_CASE) mà ReceiptModel:341 đang dùng.
     */
    @Test
    public void x2_dongPhieuLayGallonTuFieldHayTuGetter() {
        RefuelItemData item = RefuelItemData.fromJson(
                "{\"UniqueId\":\"u\",\"RealAmount\":2101,\"Gallon\":2000,\"Density\":0.8}");
        com.google.gson.Gson g = new com.google.gson.GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
        com.megatech.fms.model.ReceiptItemModel line =
                g.fromJson(item.toJson(), com.megatech.fms.model.ReceiptItemModel.class);
        System.out.println("[X2-PROBE] getGallon()=" + item.getGallon()
                + " getVolume()=" + item.getVolume()
                + " dòng phiếu gallon=" + line.getGallon());
        assertEquals("hiện trạng: dòng phiếu nhận field Gallon đã trôi",
                2000d, line.getGallon(), 0.001);
    }
}
