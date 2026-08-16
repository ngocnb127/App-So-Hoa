package com.megatech.fms;

import com.megatech.fms.model.BM2505Model;

/**
 * Activity nào mở B2505NewItemFragement thì implement interface này để tự quyết định
 * cách làm mới dữ liệu/giao diện sau khi phiếu được lưu thành công xuống local.
 */
public interface OnBM2505SavedListener {
    void onBM2505Saved(BM2505Model model);
}
