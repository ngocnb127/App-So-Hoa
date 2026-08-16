package com.megatech.fms.helpers.update;

import java.io.File;

/**
 * Chứng chỉ cho biết một file APK đã qua toàn bộ kiểm tra của {@link ApkValidator}.
 *
 * Constructor là package-private nên chỉ ApkValidator tạo được instance, và
 * ApkInstaller/VersionUpdateActivity chỉ nhận kiểu này chứ không nhận File tùy ý.
 * Nhờ vậy "không có đường gọi install nào bỏ qua validate" được trình biên dịch
 * bảo đảm, không phụ thuộc vào việc người sửa code sau này có nhớ gọi hay không.
 */
public final class ValidatedApk {

    private final File file;
    private final long versionCode;
    private final String packageName;

    ValidatedApk(File file, long versionCode, String packageName) {
        this.file = file;
        this.versionCode = versionCode;
        this.packageName = packageName;
    }

    public File getFile() {
        return file;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public String getPackageName() {
        return packageName;
    }
}
