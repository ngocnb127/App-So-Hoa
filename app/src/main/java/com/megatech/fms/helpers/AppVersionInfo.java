package com.megatech.fms.helpers;

import com.megatech.fms.BuildConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the deployed version format: versionCode-yyyyMMdd.patch. */
public final class AppVersionInfo {
    private static final Pattern FORMAT = Pattern.compile("^(\\d+)-(\\d{8})(?:\\.(\\d+))?$");
    public final long versionCode;
    public final long buildDate;
    public final long patch;
    public final String raw;

    private AppVersionInfo(long versionCode, long buildDate, long patch, String raw) {
        this.versionCode = versionCode; this.buildDate = buildDate; this.patch = patch; this.raw = raw;
    }

    public static AppVersionInfo parse(String value) {
        if (value == null) return null;
        // B\u1ECF BOM TR\u01AF\u1EDAC khi trim: U+FEFF kh\u00F4ng ph\u1EA3i kho\u1EA3ng tr\u1EAFng n\u00EAn trim() kh\u00F4ng x\u00F3a n\u00F3,
        // v\u00E0 m\u1ED9t BOM \u0111\u1EE9ng tr\u01B0\u1EDBc d\u1EA5u c\u00E1ch s\u1EBD khi\u1EBFn ph\u1EA7n c\u00F2n l\u1EA1i kh\u00F4ng kh\u1EDBp m\u1EABu.
        String clean = value.replace("\uFEFF", "").trim();
        Matcher matcher = FORMAT.matcher(clean);
        if (!matcher.matches()) return null;
        try {
            return new AppVersionInfo(Long.parseLong(matcher.group(1)),
                    Long.parseLong(matcher.group(2)), matcher.group(3) == null ? 0 : Long.parseLong(matcher.group(3)), clean);
        } catch (NumberFormatException ex) { return null; }
    }

    /**
     * So sánh theo THỨ TỰ TỪ ĐIỂN của bộ ba (versionCode, buildDate, patch): trường nào
     * khác nhau trước thì trường đó quyết định, các trường sau không được xét tới.
     *
     * <p>Nhờ vậy nâng bất kỳ trường nào trong ba trường cũng phát hành được bản mới, mà vẫn
     * giữ được tính chất an toàn: <b>không bao giờ đề nghị hạ cấp</b>. Tuyệt đối không gộp ba
     * trường bằng OR — cách đó kết luận "có bản mới" cả khi versionCode của server thấp hơn,
     * máy sẽ tải về một APK Android từ chối cài (INSTALL_FAILED_VERSION_DOWNGRADE) và thông
     * báo lặp mãi không tắt được.
     *
     * <p>Lưu ý khi phát hành: nâng riêng buildDate/patch mà giữ nguyên versionCode thì APK vẫn
     * cài đè được (Android chỉ chặn versionCode NHỎ hơn, bằng nhau vẫn là cài đè hợp lệ). Đổi
     * lại, các bản đó không phân biệt được với nhau bằng versionCode, nên vẫn nên nâng
     * versionCode cho mỗi lần phát hành có thay đổi mã nguồn.
     */
    public boolean isNewerThanCurrent() {
        if (versionCode != BuildConfig.VERSION_CODE)
            return versionCode > BuildConfig.VERSION_CODE;
        if (buildDate != BuildConfig.BUILD_DATE)
            return buildDate > BuildConfig.BUILD_DATE;
        return patch > BuildConfig.PATCH_NUMBER;
    }
}
