package com.megatech.fms.helpers;

import com.megatech.fms.data.DataRepository;

/**
 * Cầu nối để bộ test luồng ở gói {@code com.megatech.fms.flow} cài được phụ thuộc kiểm thử
 * của {@link DataHelper}.
 *
 * <p>{@code DataHelper.installTestDependencies} là package-private có chủ ý — không mở rộng
 * phạm vi của mã sản phẩm chỉ để chiều một bộ test. Lớp này nằm hoàn toàn trong nguồn test.
 */
public final class RefuelFlowOtherTruckTestBridge {

    private RefuelFlowOtherTruckTestBridge() {
    }

    public static void install(DataRepository repo, HttpClient httpClient) {
        DataHelper.installTestDependencies(repo, httpClient);
    }

    public static void reset() {
        DataHelper.resetTestDependencies();
    }
}
