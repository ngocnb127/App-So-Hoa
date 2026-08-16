package com.megatech.fms.helpers;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Sinh {@code signedSnapshotHash} cho BM 75.01 theo <b>canonical manifest</b>.
 *
 * <p>Yêu cầu của review: không nối byte tùy ý và <b>không hash trực tiếp JSON do Gson sinh</b>,
 * vì thứ tự field có thể đổi giữa các phiên bản thư viện/model. Thay vào đó hash một manifest
 * có thứ tự cố định, mã hóa UTF-8:
 *
 * <pre>
 * schemaVersion
 * uniqueId
 * refuelItemUniqueId
 * revisionNumber
 * localNumber
 * payloadSha256
 * customerSectionASignatureSha256
 * customerFinalSignatureSha256
 * skypecSignatureSha256
 * signedAt
 * </pre>
 *
 * <p>{@code payloadSha256} là băm của <b>đúng chuỗi payload đã lưu</b> (bytes UTF-8), không
 * phải của một lần serialize lại — nhờ vậy giá trị ổn định theo thời gian.
 */
public final class BM7501Canonical {

    private BM7501Canonical() {
    }

    /** Ký tự phân tách dòng cố định, không phụ thuộc nền tảng. */
    private static final String LF = "\n";

    /**
     * Dựng manifest canonical. Trường null được ghi thành chuỗi rỗng để độ dài dòng ổn định.
     *
     * @param signedAtEpochMilli thời điểm ký, tính bằng mili giây epoch (UTC)
     */
    public static String manifest(int schemaVersion,
                                  String uniqueId,
                                  String refuelItemUniqueId,
                                  int revisionNumber,
                                  String localNumber,
                                  String payloadSha256,
                                  String customerSectionASignatureSha256,
                                  String customerFinalSignatureSha256,
                                  String skypecSignatureSha256,
                                  long signedAtEpochMilli) {
        StringBuilder b = new StringBuilder();
        b.append(schemaVersion).append(LF);
        b.append(nz(uniqueId)).append(LF);
        b.append(nz(refuelItemUniqueId)).append(LF);
        b.append(revisionNumber).append(LF);
        b.append(nz(localNumber)).append(LF);
        b.append(nz(payloadSha256)).append(LF);
        b.append(nz(customerSectionASignatureSha256)).append(LF);
        b.append(nz(customerFinalSignatureSha256)).append(LF);
        b.append(nz(skypecSignatureSha256)).append(LF);
        b.append(signedAtEpochMilli).append(LF);
        return b.toString();
    }

    /** SHA-256 của manifest, trả về hex thường. */
    public static String hashManifest(String manifest) {
        return sha256Hex(utf8(manifest));
    }

    /** SHA-256 của một chuỗi payload đã lưu. */
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
