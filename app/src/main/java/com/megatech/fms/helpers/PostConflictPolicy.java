package com.megatech.fms.helpers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chính sách xử lý các mã HTTP "xung đột" khi POST phiếu tra nạp: 409 (conflict),
 * 412 (precondition failed), 423 (locked).
 *
 * <p>Trước đây mọi mã khác 200 đều rơi vào một nhánh else chỉ ghi log rồi trả null; row vẫn
 * dirty nên vòng đồng bộ POST LẠI MÃI, mỗi lượt một lần, không bao giờ dừng và không ai biết
 * server từ chối vì lý do gì.
 *
 * <p>Hai ràng buộc bắt buộc của lớp này:
 * <ul>
 *   <li><b>KHÔNG bao giờ đẩy row sang {@code postStatus = ERROR}</b> — làm vậy row rơi khỏi
 *       hàng đợi tự động và dữ liệu người dùng đứng lại vĩnh viễn ở máy.</li>
 *   <li><b>Vòng thử lại PHẢI HỘI TỤ</b>: sau {@link #MAX_AUTO_RETRIES} lần cho cùng một phiên
 *       bản (clientSeq), chính sách chuyển sang {@link Action#WAIT_FOR_USER} và DỪNG thử lại
 *       tự động. Row vẫn nằm trong hàng đợi, vẫn dirty, vẫn có dấu vết. Người dùng sửa tiếp
 *       (clientSeq đổi) hoặc khởi động lại app là bắt đầu lại từ đầu.</li>
 * </ul>
 *
 * <p>Thuần Java, đồng hồ tiêm qua tham số {@code nowMs} để test tất định.
 */
public final class PostConflictPolicy {

    /** Số lượt thử lại tự động cho MỖI phiên bản của một phiếu. */
    public static final int MAX_AUTO_RETRIES = 3;

    /** Backoff luỹ tiến theo số lần đã xung đột. */
    static final long[] BACKOFF_MS = {60_000L, 5 * 60_000L, 15 * 60_000L};

    public enum Action {
        /** Không phải mã xung đột — để các nhánh cũ xử lý như trước. */
        NONE,
        /** Còn lượt: ghi vết, hoãn theo backoff rồi đối chiếu lại bằng GET. */
        VERIFY_AND_BACKOFF,
        /** Hết lượt: dừng thử lại tự động, giữ row trong hàng đợi và chờ người dùng. */
        WAIT_FOR_USER
    }

    private static final PostConflictPolicy SHARED = new PostConflictPolicy();

    public static PostConflictPolicy shared() {
        return SHARED;
    }

    /** uniqueId -> {số lần xung đột, mốc được thử lại, clientSeq, đã dừng (1/0)}. */
    private final Map<String, long[]> state = new ConcurrentHashMap<>();

    public static boolean isConflictCode(int httpCode) {
        return httpCode == 409 || httpCode == 412 || httpCode == 423;
    }

    /**
     * Ghi nhận một lượt bị từ chối và quyết định bước tiếp theo.
     * Phiên bản mới của cùng phiếu (clientSeq khác) luôn được bắt đầu lại từ đầu: nó chưa
     * hề thất bại lần nào, hoãn nó là hoãn oan.
     */
    public Action onConflict(String uniqueId, long clientSeq, int httpCode, long nowMs) {
        if (!isConflictCode(httpCode)) return Action.NONE;
        if (uniqueId == null || uniqueId.trim().isEmpty()) return Action.NONE;

        long[] cur = state.get(uniqueId);
        long attempts = (cur != null && cur[2] == clientSeq) ? cur[0] + 1 : 1;

        if (attempts > MAX_AUTO_RETRIES) {
            // Điểm dừng rõ ràng: không đặt mốc thử lại nữa.
            state.put(uniqueId, new long[]{attempts, Long.MAX_VALUE, clientSeq, 1});
            return Action.WAIT_FOR_USER;
        }

        int idx = (int) Math.min(attempts - 1, BACKOFF_MS.length - 1);
        state.put(uniqueId, new long[]{attempts, nowMs + BACKOFF_MS[idx], clientSeq, 0});
        return Action.VERIFY_AND_BACKOFF;
    }

    /**
     * Lượt đồng bộ này có phải bỏ qua phiếu không.
     * Trả về true khi đang trong thời gian hoãn HOẶC đã dừng thử lại tự động — nhưng chỉ với
     * đúng phiên bản đã thất bại.
     */
    public boolean shouldDefer(String uniqueId, long clientSeq, long nowMs) {
        if (uniqueId == null || uniqueId.trim().isEmpty()) return false;
        long[] cur = state.get(uniqueId);
        if (cur == null) return false;
        if (cur[2] != clientSeq) {
            state.remove(uniqueId);
            return false;
        }
        if (cur[3] == 1) return true;      // đã dừng, chờ người dùng
        return nowMs < cur[1];
    }

    /** Đã dừng thử lại tự động cho phiên bản này chưa (dùng để không ghi vết lặp lại). */
    public boolean isWaitingForUser(String uniqueId, long clientSeq) {
        long[] cur = state.get(uniqueId);
        return cur != null && cur[2] == clientSeq && cur[3] == 1;
    }

    public int attempts(String uniqueId) {
        long[] cur = state.get(uniqueId);
        return cur == null ? 0 : (int) cur[0];
    }

    /** POST thành công hoặc phiếu không còn thuộc xe này: quên trạng thái đi. */
    public void clear(String uniqueId) {
        if (uniqueId != null) state.remove(uniqueId);
    }

    public void clearAll() {
        state.clear();
    }
}
