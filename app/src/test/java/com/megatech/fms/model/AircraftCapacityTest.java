package com.megatech.fms.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tải dầu của tàu bay: số THỰC TẾ do hãng báo thì thay hẳn số dự kiến.
 *
 * <p>Hai số không bao giờ cùng có nghĩa một lúc — dự kiến chỉ là con số chờ. Màn hình tra
 * nạp trước đây in cả hai cạnh nhau, ngay cạnh cặp "sản lượng dự kiến / thực tế" vốn đã dễ
 * nhầm, và không có một dòng code nào thực hiện nguyên tắc này.
 */
public class AircraftCapacityTest {

    @Test
    public void actualReplacesProjectedOnceTheAirlineReportsIt() {
        RefuelItemData item = new RefuelItemData();
        item.setProjectedCapacity(12000);
        item.setActualCapacity(11850);

        assertTrue(item.hasActualCapacity());
        assertEquals(11850, item.getCapacityToShow(), 0.001);
    }

    @Test
    public void projectedIsShownWhileTheActualIsStillMissing() {
        RefuelItemData item = new RefuelItemData();
        item.setProjectedCapacity(12000);

        assertFalse(item.hasActualCapacity());
        assertEquals(12000, item.getCapacityToShow(), 0.001);
    }

    /**
     * Server gửi 0 nghĩa là CHƯA CÓ, không phải "tải dầu bằng không". Coi 0 là số thật sẽ
     * thay số dự kiến đang đúng bằng một con số 0 vô nghĩa.
     */
    @Test
    public void zeroMeansNotReportedYet() {
        RefuelItemData item = new RefuelItemData();
        item.setProjectedCapacity(12000);
        item.setActualCapacity(0);

        assertFalse(item.hasActualCapacity());
        assertEquals(12000, item.getCapacityToShow(), 0.001);
    }

    /** Số âm cũng là dữ liệu hỏng, không được coi là đã báo. */
    @Test
    public void negativeIsNotTreatedAsReported() {
        RefuelItemData item = new RefuelItemData();
        item.setProjectedCapacity(12000);
        item.setActualCapacity(-5);

        assertFalse(item.hasActualCapacity());
        assertEquals(12000, item.getCapacityToShow(), 0.001);
    }

    /** Chưa có số nào thì trả 0 chứ không ném lỗi — màn hình vẫn phải vẽ được. */
    @Test
    public void nothingReportedShowsZero() {
        assertEquals(0, new RefuelItemData().getCapacityToShow(), 0.001);
    }
}
