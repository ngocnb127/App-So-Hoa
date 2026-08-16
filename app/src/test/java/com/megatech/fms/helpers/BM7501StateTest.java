package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.BM7501Model.BusinessStatus;

import org.junit.Test;

/** Luật chuyển trạng thái nghiệp vụ BM 75.01 — §14.1 của phương án. */
public class BM7501StateTest {

    @Test
    public void luongThuan_diDuocTungBuoc() {
        assertTrue(BM7501State.canTransition(BusinessStatus.DRAFT, BusinessStatus.A_DONE));
        assertTrue(BM7501State.canTransition(BusinessStatus.A_DONE, BusinessStatus.B_DONE));
        assertTrue(BM7501State.canTransition(BusinessStatus.B_DONE, BusinessStatus.C_DONE));
        assertTrue(BM7501State.canTransition(BusinessStatus.C_DONE, BusinessStatus.SIGNED));
        assertTrue(BM7501State.canTransition(BusinessStatus.SIGNED, BusinessStatus.PRINTED));
    }

    @Test
    public void trongNhomConSuaDuoc_quayLuiDuoc() {
        assertTrue(BM7501State.canTransition(BusinessStatus.C_DONE, BusinessStatus.A_DONE));
        assertTrue(BM7501State.canTransition(BusinessStatus.B_DONE, BusinessStatus.DRAFT));
    }

    @Test
    public void daKy_khongQuayLaiSuaDuoc() {
        assertFalse(BM7501State.canTransition(BusinessStatus.SIGNED, BusinessStatus.C_DONE));
        assertFalse(BM7501State.canTransition(BusinessStatus.PRINTED, BusinessStatus.DRAFT));
    }

    @Test
    public void khongDuocKyTat_boQuaCacBuoc() {
        assertFalse(BM7501State.canTransition(BusinessStatus.DRAFT, BusinessStatus.SIGNED));
        assertFalse(BM7501State.canTransition(BusinessStatus.A_DONE, BusinessStatus.SIGNED));
        assertFalse(BM7501State.canTransition(BusinessStatus.B_DONE, BusinessStatus.PRINTED));
    }

    @Test
    public void truocKhiKy_dungCANCELLED() {
        assertTrue(BM7501State.canTransition(BusinessStatus.DRAFT, BusinessStatus.CANCELLED));
        assertTrue(BM7501State.canTransition(BusinessStatus.C_DONE, BusinessStatus.CANCELLED));
        assertEquals(BusinessStatus.CANCELLED,
                BM7501State.cancellationTargetFor(BusinessStatus.B_DONE));
    }

    @Test
    public void sauKhiKyHoacIn_dungVOIDED() {
        assertTrue(BM7501State.canTransition(BusinessStatus.SIGNED, BusinessStatus.VOIDED));
        assertTrue(BM7501State.canTransition(BusinessStatus.PRINTED, BusinessStatus.VOIDED));
        assertEquals(BusinessStatus.VOIDED,
                BM7501State.cancellationTargetFor(BusinessStatus.PRINTED));
    }

    @Test
    public void daKyRoiThiKhongDuocCANCELLED() {
        assertFalse(BM7501State.canTransition(BusinessStatus.SIGNED, BusinessStatus.CANCELLED));
        assertFalse(BM7501State.canTransition(BusinessStatus.PRINTED, BusinessStatus.CANCELLED));
    }

    @Test
    public void chuaKyThiKhongDuocVOIDED() {
        assertFalse(BM7501State.canTransition(BusinessStatus.DRAFT, BusinessStatus.VOIDED));
        assertFalse(BM7501State.canTransition(BusinessStatus.C_DONE, BusinessStatus.VOIDED));
    }

    @Test
    public void cancelledKhongBaoGioThanhSigned() {
        assertFalse(BM7501State.canTransition(BusinessStatus.CANCELLED, BusinessStatus.SIGNED));
        assertFalse(BM7501State.canTransition(BusinessStatus.CANCELLED, BusinessStatus.DRAFT));
    }

    @Test
    public void voidedLaTrangThaiCuoi() {
        for (BusinessStatus to : BusinessStatus.values()) {
            if (to == BusinessStatus.VOIDED) continue;
            assertFalse("VOIDED không được chuyển sang " + to,
                    BM7501State.canTransition(BusinessStatus.VOIDED, to));
        }
    }

    @Test
    public void next_dungThuTuWizard() {
        assertEquals(BusinessStatus.A_DONE, BM7501State.next(BusinessStatus.DRAFT));
        assertEquals(BusinessStatus.SIGNED, BM7501State.next(BusinessStatus.C_DONE));
        assertEquals(BusinessStatus.PRINTED, BM7501State.next(BusinessStatus.SIGNED));
        assertNull(BM7501State.next(BusinessStatus.PRINTED));
        assertNull(BM7501State.next(BusinessStatus.VOIDED));
    }

    @Test
    public void nhomConSuaDuoc_dungNhuThietKe() {
        assertTrue(BusinessStatus.DRAFT.isEditable());
        assertTrue(BusinessStatus.C_DONE.isEditable());
        assertFalse(BusinessStatus.SIGNED.isEditable());
        assertFalse(BusinessStatus.PRINTED.isEditable());
        assertFalse(BusinessStatus.CANCELLED.isEditable());
        assertFalse(BusinessStatus.VOIDED.isEditable());
    }
}
