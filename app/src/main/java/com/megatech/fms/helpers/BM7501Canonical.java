package com.megatech.fms.helpers;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Băm SHA-256 cho BM 75.01: dùng cho ảnh chữ ký (đối chiếu file còn nguyên vẹn) và cho
 * phần sinh số chứng từ.
 *
 * <p>Trước đây lớp này còn sinh {@code signedSnapshotHash} của bản đã ký; phần đó đã bỏ cùng
 * luồng khoá phiếu sau khi in (chốt 2026-09-23) — phiếu sửa và in lại được nên không có
 * "bản đã đóng băng" để băm.
 */
public final class BM7501Canonical {

    private BM7501Canonical() {
    }

    /** Ký tự phân tách dòng cố định, không phụ thuộc nền tảng. */
    private static final String LF = "\n";

    public static String sha256OfString(String s) {
        return sha256Hex(utf8(s));
    }

    public static String sha256Hex(byte[] data) {
        if (data == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xFF;
                if (v < 0x10) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có trên Android; nếu thiếu thì không được lặng lẽ bỏ qua.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] utf8(String s) {
        if (s == null) return new byte[0];
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not available", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
