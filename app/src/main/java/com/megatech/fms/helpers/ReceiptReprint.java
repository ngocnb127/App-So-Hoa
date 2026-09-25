package com.megatech.fms.helpers;

import android.graphics.BitmapFactory;
import android.util.Base64;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.model.ReceiptModel;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * In lại phiếu (máy in nhiệt) từ màn "Hoá đơn theo xe" — kể cả phiếu của XE KHÁC.
 *
 * <p>ZPL dựng từ {@link ReceiptModel} y như nút "Chỉ in lại phiếu" ở màn tra nạp. Khác ở chỗ
 * phiếu có thể không nằm trong máy này, nên phải lấy theo thứ tự:
 * <ol>
 *   <li>DB trong máy theo số phiếu — phiếu máy này đã in, chạy được cả khi mất mạng;</li>
 *   <li>server theo số phiếu ({@link ReceiptAPI#getByNumber}).</li>
 * </ol>
 *
 * <p>Ảnh chữ ký: tệp trong máy còn thì dùng, không thì tải
 * {@code {API}/receipts/{số phiếu}_BUYER.jpg} và {@code _SELLER.jpg} về thư mục cache.
 *
 * <p>Tải chữ ký hỏng thì đường dẫn chữ ký phải để {@code null}, KHÔNG được giữ đường dẫn tới
 * một tệp không có: ZPL vẫn gọi {@code ^XGE:BUYER.GRF} khi đường dẫn khác null, trong khi
 * {@code ZebraWorker} bỏ qua bước nạp ảnh vì tệp không tồn tại — máy in sẽ in ảnh còn lưu
 * trong bộ nhớ E: từ LẦN IN TRƯỚC, tức chữ ký của một khách khác.
 *
 * <p>Tệp tải về nằm trong {@code cacheDir}, không nằm trong thư mục Pictures: thư mục đó là
 * nơi {@link SignatureCache} và đường đẩy phiếu đọc chữ ký, không được để ảnh của phiếu khác
 * lọt vào.
 */
public final class ReceiptReprint {
    private ReceiptReprint() {}

    static final String CACHE_DIR = "reprint_signatures";

    public enum Source { LOCAL, SERVER }

    /** Kết quả chuẩn bị in lại. {@code model == null} nghĩa là không lấy được phiếu. */
    public static final class Prepared {
        public final ReceiptModel model;
        public final Source source;
        public final boolean buyerMissing;
        public final boolean sellerMissing;

        Prepared(ReceiptModel model, Source source, boolean buyerMissing, boolean sellerMissing) {
            this.model = model;
            this.source = source;
            this.buyerMissing = buyerMissing;
            this.sellerMissing = sellerMissing;
        }
    }

    /**
     * Lấy phiếu và chữ ký để in lại. Chạm DB và mạng — phải gọi ở luồng nền.
     *
     * <p>Thứ tự: bản trong máy trước (đúng tờ giấy máy này đã in, đủ Cert No./FHS/số đồng hồ,
     * và chạy cả khi mất mạng), rồi mới tới server — theo Id phiếu nếu hoá đơn mang theo, cuối
     * cùng là tra theo số phiếu.
     *
     * @param receiptId Id phiếu trên server, {@code 0} nếu hoá đơn không mang theo.
     */
    public static Prepared prepare(File cacheRoot, String receiptNumber, int receiptId) {
        String number = receiptNumber == null ? "" : receiptNumber.trim();
        if (number.isEmpty()) return new Prepared(null, null, false, false);

        Source source = Source.LOCAL;
        ReceiptModel model = null;
        try {
            model = DataHelper.getReceiptByNumber(number);
        } catch (Exception ex) {
            Logger.appendLog("REPRINT", "Đọc phiếu " + number + " trong máy lỗi: " + ex.getMessage());
        }
        if (model == null) {
            source = Source.SERVER;
            ReceiptAPI api = new ReceiptAPI();
            model = receiptId > 0 ? api.getById(receiptId) : null;
            if (model == null) model = api.getByNumber(number);
            if (model != null) normalizeForPrint(model);
        }
        if (model == null) {
            Logger.appendLog("REPRINT", "Không lấy được phiếu " + number + " (máy và server)");
            return new Prepared(null, null, false, false);
        }

        File dir = cacheRoot == null ? null : new File(cacheRoot, CACHE_DIR);
        clearOldDownloads(dir);
        model.setSignaturePath(resolveSignature(
                model.getSignaturePath(), model.getSignImageString(), dir, number, true));
        model.setSellerSignaturePath(resolveSignature(
                model.getSellerSignaturePath(), model.getSellerImageString(), dir, number, false));

        boolean buyerMissing = model.getSignaturePath() == null;
        boolean sellerMissing = model.getSellerSignaturePath() == null;
        Logger.appendLog("REPRINT", "Phiếu " + number + " nguồn=" + source
                + " chữ ký mua=" + (buyerMissing ? "THIẾU" : "có")
                + " bán=" + (sellerMissing ? "THIẾU" : "có"));
        return new Prepared(model, source, buyerMissing, sellerMissing);
    }

    /** URL ảnh chữ ký trên server, {@code null} khi không có số phiếu. */
    public static String signatureUrl(String baseUrl, String receiptNumber, boolean buyer) {
        String name = defaultSignatureFileName(receiptNumber, buyer);
        return name == null ? null : imageUrl(baseUrl, name);
    }

    /** {@code {base}/receipts/{tên tệp}} — {@code null} khi không có tên tệp. */
    static String imageUrl(String baseUrl, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/receipts/" + encodePathSegment(fileName.trim());
    }

    static String defaultSignatureFileName(String receiptNumber, boolean buyer) {
        if (receiptNumber == null || receiptNumber.trim().isEmpty()) return null;
        return receiptNumber.trim() + (buyer ? "_BUYER.jpg" : "_SELLER.jpg");
    }

    /**
     * Tên tệp để thử tải, theo thứ tự: tên server đang lưu (nếu phiếu mang theo), rồi tên
     * suy ra từ số phiếu.
     *
     * <p>Đoán tên chỉ đúng khi server đặt tên theo số phiếu. Phiếu trùng số (dùng lại số,
     * phiếu thay thế) hay chữ ký ký sau có thể mang tên khác, nên tên server trả về phải
     * được thử TRƯỚC. Đường dẫn trên máy (có dấu phân cách) thì lấy phần tên cuối.
     */
    static List<String> signatureFileNames(String storedPath, String receiptNumber, boolean buyer) {
        List<String> names = new ArrayList<>();
        String stored = lastSegment(storedPath);
        if (stored != null) names.add(stored);
        String fallback = defaultSignatureFileName(receiptNumber, buyer);
        if (fallback != null && !fallback.equalsIgnoreCase(stored)) names.add(fallback);
        return names;
    }

    private static String lastSegment(String path) {
        if (path == null) return null;
        String value = path.trim().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        return value.isEmpty() ? null : value;
    }

    /** Tệp chữ ký trong máy còn dùng được hay không. */
    static boolean isUsableFile(String path) {
        if (path == null || path.trim().isEmpty()) return false;
        File file = new File(path);
        return file.isFile() && file.length() > 0;
    }

    /**
     * Câu cảnh báo khi thiếu chữ ký, {@code null} khi đủ cả hai. Chỉ cảnh báo, không chặn:
     * phiếu "chờ ký" vốn không có chữ ký người mua.
     */
    public static String missingSignatureWarning(boolean buyerMissing, boolean sellerMissing) {
        if (!buyerMissing && !sellerMissing) return null;
        String who = buyerMissing && sellerMissing ? "người mua và người bán"
                : buyerMissing ? "người mua" : "người bán";
        return "Không có ảnh chữ ký " + who + " — phiếu in lại sẽ để trống ô ký đó.";
    }

    /**
     * Ba nguồn ảnh chữ ký, theo thứ tự rẻ và chắc dần: tệp trong máy, ảnh base64 đi kèm phiếu
     * (server trả trong {@code SignImageString}/{@code SellerImageString}), rồi tải tệp tĩnh.
     */
    private static String resolveSignature(String localPath, String base64, File dir,
                                           String number, boolean buyer) {
        if (isUsableFile(localPath)) return localPath;
        String fromModel = writeBase64(dir, base64, number, buyer);
        if (fromModel != null) return fromModel;
        for (String name : signatureFileNames(localPath, number, buyer)) {
            String path = download(dir, name, number, buyer);
            if (path != null) return path;
        }
        return null;
    }

    private static String writeBase64(File dir, String base64, String number, boolean buyer) {
        if (dir == null || base64 == null || base64.trim().isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64.trim(), Base64.DEFAULT);
            return store(dir, bytes, number, buyer);
        } catch (Exception ex) {
            Logger.appendLog("REPRINT", "Ảnh chữ ký kèm phiếu không đọc được: " + ex.getMessage());
            return null;
        }
    }

    private static String download(File dir, String fileName, String number, boolean buyer) {
        if (dir == null) return null;
        String url = imageUrl(BuildConfig.API_BASE_URL, fileName);
        try {
            byte[] bytes = new HttpClient().sendGETBytes(url, "image/*");
            String path = store(dir, bytes, number, buyer);
            if (path == null) Logger.appendLog("REPRINT", "Không tải được chữ ký " + url);
            return path;
        } catch (Exception ex) {
            Logger.appendLog("REPRINT", "Tải chữ ký lỗi " + url + ": " + ex.getMessage());
            return null;
        }
    }

    private static String store(File dir, byte[] bytes, String number, boolean buyer) {
        if (bytes == null || bytes.length == 0 || !isImage(bytes)) return null;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            File target = new File(dir, safeFileName(number) + (buyer ? "_BUYER.jpg" : "_SELLER.jpg"));
            try (FileOutputStream out = new FileOutputStream(target, false)) {
                out.write(bytes);
            }
            return target.getAbsolutePath();
        } catch (Exception ex) {
            Logger.appendLog("REPRINT", "Ghi ảnh chữ ký lỗi: " + ex.getMessage());
            return null;
        }
    }

    /** Server có thể trả trang lỗi kèm mã 200 — chỉ nhận thứ giải mã được thành ảnh. */
    private static boolean isImage(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return options.outWidth > 0 && options.outHeight > 0;
    }

    /** Mỗi lần in lại chỉ cần chữ ký của đúng phiếu đó; bỏ tệp của các lần trước. */
    private static void clearOldDownloads(File dir) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !file.delete())
                Logger.appendLog("REPRINT", "Không xoá được " + file.getName());
        }
    }

    /**
     * Phiếu từ server có thể thiếu vài trường chữ; ZPL ghép thẳng chuỗi nên {@code null} sẽ
     * in ra chữ "null", còn {@code customerName} null thì dựng ZPL văng NPE.
     */
    static void normalizeForPrint(ReceiptModel model) {
        if (model.getCustomerName() == null) model.setCustomerName("");
        if (model.getFlightCode() == null) model.setFlightCode("");
        if (model.getRouteName() == null) model.setRouteName("");
        if (model.getAircraftType() == null) model.setAircraftType("");
        if (model.getAircraftCode() == null) model.setAircraftCode("");
        if (model.getQualityNo() == null) model.setQualityNo("");
        if (model.getItems() == null) model.setItems(new java.util.ArrayList<>());
    }

    static String safeFileName(String number) {
        return number.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String encodePathSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception ex) {
            return value;
        }
    }
}
