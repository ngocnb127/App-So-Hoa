package com.megatech.fms.helpers.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import com.megatech.fms.BuildConfig;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Kiểm tra APK tải về TRƯỚC khi mở trình cài đặt.
 *
 * PackageInstaller của hệ điều hành vẫn là lớp kiểm tra thẩm quyền cuối cùng; mục đích
 * ở đây là từ chối sớm file không hợp lệ và đưa ra thông báo hiểu được, thay vì để người
 * dùng tải xong vài chục MB rồi nhận một mã lỗi không đọc nổi ở bước commit.
 */
public final class ApkValidator {

    private static final String LOG_TAG = "FMS_UPDATE";

    /** Lỗi kiểm tra — thông điệp đã ở dạng hiển thị được cho người dùng. */
    public static final class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    private ApkValidator() {
    }

    /**
     * @param expectedVersionCode versionCode mà metadata server công bố
     * @throws ValidationException nếu bất kỳ kiểm tra nào không đạt; file KHÔNG được cài
     */
    public static ValidatedApk validate(Context ctx, File apk, long expectedVersionCode)
            throws ValidationException {

        if (apk == null || !apk.exists()) {
            throw new ValidationException("Không tìm thấy tệp cài đặt");
        }
        if (apk.length() <= 0) {
            throw new ValidationException("Tệp cài đặt rỗng");
        }

        PackageManager pm = ctx.getPackageManager();
        PackageInfo archive = getArchiveInfo(pm, apk.getAbsolutePath());
        if (archive == null) {
            throw new ValidationException("Tệp tải về không phải APK hợp lệ");
        }

        // 1. Đúng ứng dụng. Chặn trường hợp kênh trỏ nhầm sang biến thể khác
        //    (ví dụ bản demo tải nhầm APK production, hoặc release tải nhầm APK thermal
        //    có applicationId .fhs).
        if (!BuildConfig.APPLICATION_ID.equals(archive.packageName)) {
            throw new ValidationException("APK sai ứng dụng: " + archive.packageName);
        }

        // 2. Phải thực sự mới hơn. Dùng ">" chứ không phải ">=": APK cùng versionCode
        //    không phải bản nâng cấp hợp lệ theo chính sách phát hành, dù Android có thể
        //    cho cài đè. Đây cũng là chỗ chặn downgrade trước khi tốn công mở installer.
        long apkVersionCode = versionCodeOf(archive);
        if (apkVersionCode <= BuildConfig.VERSION_CODE) {
            throw new ValidationException("APK không mới hơn bản đang chạy (" +
                    apkVersionCode + " ≤ " + BuildConfig.VERSION_CODE + ")");
        }

        // 3. Khớp metadata. Lệch nghĩa là server publish file và metadata không đồng bộ.
        if (expectedVersionCode > 0 && apkVersionCode != expectedVersionCode) {
            throw new ValidationException("APK (" + apkVersionCode +
                    ") không khớp phiên bản máy chủ công bố (" + expectedVersionCode + ")");
        }

        // 4. Cùng signer với bản đang chạy.
        if (!hasSameSigner(pm, apk.getAbsolutePath())) {
            throw new ValidationException("APK được ký bằng chứng thư khác — từ chối cài");
        }

        Log.d(LOG_TAG, "APK hợp lệ: pkg=" + archive.packageName + " versionCode=" + apkVersionCode);
        return new ValidatedApk(apk, apkVersionCode, archive.packageName);
    }

    private static PackageInfo getArchiveInfo(PackageManager pm, String path) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0));
            }
            //noinspection deprecation
            return pm.getPackageArchiveInfo(path, 0);
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Không đọc được PackageInfo của APK", t);
            return null;
        }
    }

    private static long versionCodeOf(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        //noinspection deprecation
        return info.versionCode;
    }

    /**
     * So khớp tập chứng thư ký hiện hành của APK với tập của ứng dụng đang cài.
     *
     * Đây là kiểm tra cho trường hợp KHÔNG xoay khóa — đúng với bản 104. Nếu sau này áp
     * dụng signing lineage (xoay khóa), APK mới sẽ mang chứng thư mà bản đang cài chưa
     * từng biết, và kiểm tra này sẽ từ chối nó; khi đó phải mở rộng để đọc
     * signing history của archive. Không nới lỏng chỗ này trước khi việc đó được
     * kiểm chứng bằng APK thật.
     */
    private static boolean hasSameSigner(PackageManager pm, String apkPath) {
        try {
            Set<String> installed = signerDigests(pm, BuildConfig.APPLICATION_ID, false, null);
            Set<String> candidate = signerDigests(pm, null, true, apkPath);
            if (installed.isEmpty() || candidate.isEmpty()) {
                Log.e(LOG_TAG, "Không đọc được chứng thư ký để đối chiếu");
                return false;
            }
            boolean same = installed.equals(candidate);
            if (!same) {
                Log.e(LOG_TAG, "Chứng thư ký khác nhau. Đang cài=" + installed + " APK=" + candidate);
            }
            return same;
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Lỗi khi đối chiếu chứng thư ký", t);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signerDigests(PackageManager pm, String packageName,
                                             boolean fromArchive, String apkPath) throws Exception {
        Set<String> digests = new HashSet<>();
        Signature[] signatures;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = fromArchive
                    ? pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
                    : pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info == null) return digests;
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) return digests;
            // Dùng signer HIỆN HÀNH của cả hai phía, không dùng lineage history:
            // bản 104 không xoay khóa, nên "signer hiện hành phải trùng nhau" là đúng
            // ngữ nghĩa và không phụ thuộc vào cách framework điền history cho archive.
            signatures = signingInfo.getApkContentsSigners();
        } else {
            PackageInfo info = fromArchive
                    ? pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
                    : pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (info == null) return digests;
            signatures = info.signatures;
        }

        if (signatures == null) return digests;
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            digests.add(toHex(sha256.digest(signature.toByteArray())));
        }
        return digests;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
