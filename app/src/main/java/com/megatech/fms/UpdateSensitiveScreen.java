package com.megatech.fms;

/**
 * Đánh dấu màn hình đang xử lý nghiệp vụ: lập chứng từ, tra nạp, ký nhận, in hóa đơn.
 *
 * FMSApplication không bao giờ tự bật hộp thoại nhắc cập nhật khi màn hình trên cùng
 * cài đặt interface này — một hộp thoại che ngang lúc lái xe đang ký nhận hoặc in hóa đơn
 * là gián đoạn nghiệp vụ thật.
 *
 * Người dùng vẫn mở được màn hình cập nhật thủ công; khi đó UpdateSafetyGuard mới là
 * thứ quyết định có cho cài hay không.
 *
 * Thêm màn hình mới vào danh sách bằng cách khai báo "implements UpdateSensitiveScreen"
 * trên chính lớp đó — cố ý không gom thành một danh sách instanceof tập trung, vì danh sách
 * kiểu đó luôn bị quên cập nhật khi có màn hình mới.
 */
public interface UpdateSensitiveScreen {
}
