package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Canonical manifest + hash cho {@code signedSnapshotHash}. */
public class BM7501CanonicalTest {

    private static String manifest(String localNumber, long signedAt) {
        return BM7501Canonical.manifest(
                1,
                "u-1",
                "refuel-1",
                1,
                localNumber,
                "payload-sha",
                "sigA-sha",
                "sigFinal-sha",
                "sigSkypec-sha",
                signedAt);
    }

    @Test
    public void manifest_dungThuTuVaDuSoDong() {
        String m = manifest("75-NBA-51F12345-AB12-260810-001", 1_700_000_000_000L);
        String[] lines = m.split("\n", -1);

        // 10 dòng dữ liệu + phần tử rỗng cuối do dòng cuối cũng kết thúc bằng \n
        assertEquals(11, lines.length);
        assertEquals("1", lines[0]);
        assertEquals("u-1", lines[1]);
        assertEquals("refuel-1", lines[2]);
        assertEquals("1", lines[3]);
        assertEquals("75-NBA-51F12345-AB12-260810-001", lines[4]);
        assertEquals("payload-sha", lines[5]);
        assertEquals("sigA-sha", lines[6]);
        assertEquals("sigFinal-sha", lines[7]);
        assertEquals("sigSkypec-sha", lines[8]);
        assertEquals("1700000000000", lines[9]);
        assertEquals("", lines[10]);
    }

    @Test
    public void manifest_truongNull_thanhChuoiRong_giuNguyenSoDong() {
        String m = BM7501Canonical.manifest(1, null, null, 2, null, null, null, null, null, 0L);
        assertEquals(11, m.split("\n", -1).length);
    }

    @Test
    public void hash_onDinh_giuaCacLanGoi() {
        String m = manifest("75-A-B-AB12-260810-001", 123L);
        assertEquals(BM7501Canonical.hashManifest(m), BM7501Canonical.hashManifest(m));
    }

    @Test
    public void hash_doiKhiDoiBatKyThanhPhanNao() {
        String base = BM7501Canonical.hashManifest(manifest("75-A-B-AB12-260810-001", 123L));

        assertNotEquals(base, BM7501Canonical.hashManifest(manifest("75-A-B-AB12-260810-002", 123L)));
        assertNotEquals(base, BM7501Canonical.hashManifest(manifest("75-A-B-AB12-260810-001", 124L)));
    }

    @Test
    public void hash_laHex64KyTuThuong() {
        String h = BM7501Canonical.hashManifest(manifest("75-A-B-AB12-260810-001", 1L));
        assertEquals(64, h.length());
        assertTrue(h.matches("^[0-9a-f]{64}$"));
    }

    @Test
    public void sha256_dungGiaTriChuan() {
        // Giá trị SHA-256 chuẩn của chuỗi rỗng và "abc".
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                BM7501Canonical.sha256OfString(""));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                BM7501Canonical.sha256OfString("abc"));
    }

    @Test
    public void sha256_xuLyDungTiengViet() {
        // Không lệ thuộc encoding mặc định của máy chạy test.
        String h1 = BM7501Canonical.sha256OfString("Nguyễn Văn A");
        String h2 = BM7501Canonical.sha256OfString("Nguyễn Văn A");
        assertEquals(h1, h2);
        assertNotEquals(h1, BM7501Canonical.sha256OfString("Nguyen Van A"));
    }
}
