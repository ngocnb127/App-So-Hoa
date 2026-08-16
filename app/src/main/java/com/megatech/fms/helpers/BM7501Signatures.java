package com.megatech.fms.helpers;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lưu ảnh chữ ký của BM 75.01.
 *
 * <p>Màn hình ký dùng chung ({@code ReceiptSignActivity}) ghi ảnh ra
 * {@code getExternalFilesDir()} bằng {@code createTempFile} — vừa là bộ nhớ ngoài (mất khi gỡ app)
 * vừa là file tạm. Chứng từ pháp lý không được nằm ở đó, nên ảnh phải được chép vào
 * <b>vùng riêng bền vững của app</b> kèm checksum ngay khi ký xong.
 *
 * <p>Đường dẫn: {@code getFilesDir()/bm7501/<uniqueId>/<slot>.jpg}
 */
public final class BM7501Signatures {

    private BM7501Signatures() {
    }

    /** Chữ ký đại diện hãng xác nhận lời khai mục A. */
    public static final String SLOT_CUSTOMER_SECTION_A = "customer-section-a";
    /** Chữ ký người mua — đại diện khách hàng xác nhận cuối. */
    public static final String SLOT_CUSTOMER_FINAL = "customer-final";
    /** Chữ ký người bán — đại diện SKYPEC. */
    public static final String SLOT_SKYPEC = "skypec";

    /** Kết quả lưu: đường dẫn bền vững và checksum để đối chiếu về sau. */
    public static class Stored {
        private final String path;
        private final String sha256;

        Stored(String path, String sha256) {
            this.path = path;
            this.sha256 = sha256;
        }

        public String getPath() { return path; }
        public String getSha256() { return sha256; }
    }

    /**
     * Chép ảnh chữ ký từ file tạm vào vùng riêng của app và tính checksum.
     *
     * @param sourcePath đường dẫn file tạm do màn hình ký trả về
     * @return null nếu không đọc được ảnh nguồn
     */
    public static Stored store(Context context, String documentUniqueId, String slot,
                               String sourcePath) throws IOException {
        if (context == null || documentUniqueId == null || slot == null || sourcePath == null) {
            return null;
        }
        File source = new File(sourcePath);
        if (!source.exists()) return null;

        File dir = directory(context, documentUniqueId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục chữ ký: " + dir.getAbsolutePath());
        }

        File dest = new File(dir, slot + ".jpg");
        copy(source, dest);

        String sha = BM7501Canonical.sha256Hex(readAll(dest));

        // Ảnh tạm nằm ở bộ nhớ ngoài, xoá ngay để chứng từ chỉ tồn tại ở một nơi.
        if (!source.delete()) {
            Logger.appendLog("BM7501", "Không xoá được file chữ ký tạm: " + sourcePath);
        }

        return new Stored(dest.getAbsolutePath(), sha);
    }

    public static File directory(Context context, String documentUniqueId) {
        return new File(new File(context.getFilesDir(), "bm7501"), documentUniqueId);
    }

    /** Kiểm tra ảnh chữ ký còn nguyên vẹn so với checksum đã lưu. */
    public static boolean verify(String path, String expectedSha256) {
        if (path == null || expectedSha256 == null) return false;
        try {
            File f = new File(path);
            if (!f.exists()) return false;
            return expectedSha256.equals(BM7501Canonical.sha256Hex(readAll(f)));
        } catch (IOException ex) {
            Logger.appendLog("BM7501", "verify chữ ký lỗi: " + ex.getMessage());
            return false;
        }
    }

    private static void copy(File source, File dest) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            close(in);
            close(out);
        }
    }

    private static byte[] readAll(File file) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            close(in);
        }
    }

    private static void close(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // đóng file lỗi không được che mất lỗi chính ở trên
        }
    }
}
