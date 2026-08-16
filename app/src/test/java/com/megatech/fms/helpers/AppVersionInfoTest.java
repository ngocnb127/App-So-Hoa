package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.BuildConfig;

import org.junit.Test;

public class AppVersionInfoTest {

    private static String raw(long versionCode, String date, long patch) {
        return versionCode + "-" + date + "." + patch;
    }

    // ---- parse ----

    @Test
    public void parseDinhDangDayDu() {
        AppVersionInfo info = AppVersionInfo.parse("104-20260812.3");
        assertNotNull(info);
        assertEquals(104, info.versionCode);
        assertEquals(20260812, info.buildDate);
        assertEquals(3, info.patch);
        assertEquals("104-20260812.3", info.raw);
    }

    @Test
    public void parseThieuPatchThiPatchBangKhong() {
        AppVersionInfo info = AppVersionInfo.parse("97-20250811");
        assertNotNull(info);
        assertEquals(97, info.versionCode);
        assertEquals(0, info.patch);
    }

    @Test
    public void parseBoQuaBomVaKhoangTrang() {
        AppVersionInfo info = AppVersionInfo.parse("﻿  104-20260812.1  ");
        assertNotNull(info);
        assertEquals(104, info.versionCode);
        // raw phải sạch, vì nó được dùng để dựng tên file APK
        assertEquals("104-20260812.1", info.raw);
    }

    @Test
    public void parseTraVeNullKhiSaiDinhDang() {
        assertNull(AppVersionInfo.parse(null));
        assertNull(AppVersionInfo.parse(""));
        assertNull(AppVersionInfo.parse("   "));
        assertNull(AppVersionInfo.parse("104"));
        assertNull(AppVersionInfo.parse("104-2026081"));      // ngày không đủ 8 chữ số
        assertNull(AppVersionInfo.parse("abc-20260812.1"));
        // Chuỗi giữ chỗ mà HttpClient trả về cho lỗi HTTP không được coi là phiên bản
        assertNull(AppVersionInfo.parse("GET request not worked"));
        assertNull(AppVersionInfo.parse("Not Authorized"));
        // Trang HTML lỗi cũng vậy
        assertNull(AppVersionInfo.parse("<html><head><title>Test</title></head></html>"));
    }

    // ---- so sánh: thứ tự từ điển của (versionCode, buildDate, patch) ----

    private static String today() {
        return String.valueOf(BuildConfig.BUILD_DATE);
    }

    @Test
    public void versionCodeLonHonThiCoBanMoi() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE + 1, "20200101", 0));
        assertNotNull(info);
        // versionCode quyết định trước, ngày build cũ hơn cũng không lật ngược được.
        assertTrue(info.isNewerThanCurrent());
    }

    @Test
    public void cungVersionCodeMaNgayBuildMoiHonThiCoBanMoi() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE, String.valueOf(BuildConfig.BUILD_DATE + 1), 0));
        assertNotNull(info);
        assertTrue(info.isNewerThanCurrent());
    }

    @Test
    public void cungVersionCodeVaNgayMaPatchLonHonThiCoBanMoi() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE, today(), BuildConfig.PATCH_NUMBER + 1));
        assertNotNull(info);
        assertTrue(info.isNewerThanCurrent());
    }

    @Test
    public void trungCaBaTruongThiKhongCoBanMoi() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE, today(), BuildConfig.PATCH_NUMBER));
        assertNotNull(info);
        assertFalse(info.isNewerThanCurrent());
    }

    /**
     * Hồi quy: không được gộp ba trường bằng OR. Ngày build cũ hơn thì dù patch có lớn tới
     * đâu cũng vẫn là bản cũ.
     */
    @Test
    public void ngayBuildCuHonThiKhongCoBanMoi_daKeCaPatchLonHon() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE, String.valueOf(BuildConfig.BUILD_DATE - 1), 999));
        assertNotNull(info);
        assertFalse(info.isNewerThanCurrent());
    }

    @Test
    public void versionCodeNhoHonThiKhongCoBanMoi_daKeCaNgayVaPatchLonHon() {
        AppVersionInfo info = AppVersionInfo.parse(
                raw(BuildConfig.VERSION_CODE - 1, "29991231", 999));
        assertNotNull(info);
        // Không được đề nghị hạ cấp: Android sẽ từ chối cài và thông báo sẽ lặp mãi.
        assertFalse(info.isNewerThanCurrent());
    }
}
