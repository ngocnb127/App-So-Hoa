package com.megatech.fms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SearchView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.Product;
import com.megatech.fms.databinding.ActivityRefuelPreviewBinding;
import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.databinding.InvoicePreviewBinding;
import com.megatech.fms.databinding.PreviewExtractBinding;
import com.megatech.fms.databinding.RefuelBm2508Binding;
import com.megatech.fms.databinding.SelectUserBinding;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.enums.RETURN_UNIT;
import com.megatech.fms.exceptions.InvalidRefuelTimeException;
import com.megatech.fms.helpers.BM2505Factory;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.OthersFreshness;
import com.megatech.fms.helpers.RefuelFieldPatch;
import com.megatech.fms.helpers.RefuelTimeValidator;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.AirlineArrayAdapter;
import com.megatech.fms.view.BM2505ArrayAdapter;
import com.megatech.fms.view.InvoiceItemAdapter;
import com.megatech.fms.view.TruckArrayAdapter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.megatech.fms.helpers.PrintWorker.PRINT_MODE;
import static com.megatech.fms.helpers.PrintWorker.PrintStateListener;
import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

public class RefuelPreviewActivity extends UserBaseActivity implements View.OnClickListener, OnBM2505SavedListener {

    private PrintWorker printWorker;
    private final int REFUEL_WINDOW = 1;
    private final int RECEIPT_WINDOW = 2;
    private final int INVOICE_WINDOW = 4;
    List<AirlineModel> airlines = null;
    public List<ProductModel> productList = null;

    /// bind data to view
    ArrayList<RefuelItemData> allItems = new ArrayList<RefuelItemData>();
    ArrayList<RefuelItemData> printItems = new ArrayList<RefuelItemData>();

    private boolean printTest = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle b = getIntent().getExtras();
        remoteId = b.getInt("REFUEL_ID", 0);
        localId = b.getInt("REFUEL_LOCAL_ID", 0);
        uniqueId = b.getString("REFUEL_UNIQUE_ID", UUID.randomUUID().toString());

        loadData();

        Drawable drawable = getResources().getDrawable(R.drawable.ic_edit, getTheme());
        drawable.setAlpha(90);

        printWorker = new PrintWorker(this);


        printWorker.setPrintStateListener(new PrintStateListener() {
            @Override
            public void onConnectionError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showErrorMessage(R.string.printer_error);
                    }
                });
            }

            @Override
            public void onError() {
                setCurrentPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showMessage(R.string.validate, R.string.print_error, R.drawable.ic_error, new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                m_Title = getString(R.string.invoice_number);
                                isSplit = true;
                                showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                                return null;
                            }
                        });
                    }
                });
            }

            @Override
            public void onSuccess() {

                if (!printTest) {
                    setCurrentPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m_Title = getString(R.string.invoice_number);
                            isSplit = true;
                            showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                        }
                    });
                }

            }

        });

    }

    private int localId;
    private int remoteId;
    private String uniqueId;
    private boolean hasReview ;

    /** Kết quả lượt kéo lại mẻ của xe khác lúc mở màn hình; null khi chưa chạy xong. */
    private DataHelper.RefreshResult othersRefresh;
    private int loadGeneration;

    //AlertDialog progressDialog;
    @SuppressLint("StaticFieldLeak")
    private void loadData() {

        final int requestGeneration = ++loadGeneration;
        setProgressDialog();
        new AsyncTask<Void, Void, DataHelper.PreviewLoadResult>() {
            private boolean loadedHasReview;

            @Override
            protected DataHelper.PreviewLoadResult doInBackground(Void... voids) {
                Logger.appendLog("PRW", "Start loading");

                airlines = DataHelper.getAirlines();


                if (userList == null)
                    userList = DataHelper.getUsers();
                productList = DataHelper.getProducts();

                // Một root GET vừa dựng phiếu hiện tại vừa nhận collection Others. Không gọi
                // endpoint hai lần: response thứ hai lỗi/cũ từng có thể thay membership chính
                // xác vừa nhận bằng cache Room và làm hoá đơn gộp thêm một mẻ đã stale.
                DataHelper.PreviewLoadResult loaded =
                        DataHelper.loadRefuelForPreview(uniqueId);
                if (loaded.item != null)
                    loadedHasReview = DataHelper.checkReview(
                            loaded.item.getFlightId(), loaded.item.getFlightUniqueId());
                return loaded;
            }

            @Override
            protected void onPostExecute(DataHelper.PreviewLoadResult loaded) {
                // Người dùng có thể bấm CẬP NHẬT liên tiếp. Kết quả cũ không được phép
                // phủ UI/membership mới hơn dù HTTP của nó về sau.
                if (requestGeneration != loadGeneration || loaded == null
                        || loaded.superseded) {
                    if (requestGeneration == loadGeneration) closeProgressDialog();
                    return;
                }
                refuelData = loaded.item;
                othersRefresh = loaded.others;
                hasReview = loadedHasReview;
                bindData();
                warnStaleOthers();
                super.onPostExecute(loaded);

            }
        }.execute();


    }

    /**
     * Báo cho người dùng biết màn hình đang đứng trên dữ liệu cũ của xe khác.
     *
     * <p>Chỉ cảnh báo, chưa chặn gì: hiện trường vẫn phải in được khi sóng chập chờn. Nhưng
     * người dùng cần biết để đối chiếu lại các mẻ trước khi xuất hoá đơn, vì mẻ cũ kéo giờ
     * bắt đầu trên hoá đơn đi sai mà nhìn từng dòng vẫn thấy hợp lệ.
     */
    private void warnStaleOthers() {
        if (othersRefresh == null || !othersRefresh.hasFailure()) return;

        String message;
        if (!othersRefresh.collectionComplete && othersRefresh.failed == 0) {
            message = "Server chưa xác nhận danh sách đầy đủ các mẻ của xe khác. "
                    + "Dữ liệu đang hiển thị là bản đã lưu trên xe; vui lòng kiểm tra trước "
                    + "khi xuất hoá đơn.";
        } else if (!othersRefresh.collectionComplete) {
            message = "Server chưa xác nhận danh sách đầy đủ và chưa cập nhật được "
                    + othersRefresh.failed + "/" + othersRefresh.total
                    + " mẻ đã biết của xe khác. Vui lòng kiểm tra trước khi xuất hoá đơn.";
        } else {
            message = "Chưa cập nhật được " + othersRefresh.failed + "/"
                    + othersRefresh.total + " mẻ của xe khác. Vui lòng kiểm tra lại dữ liệu"
                    + " các mẻ trước khi xuất hoá đơn.";
        }

        Logger.appendLog(LOG_TAG, "Cảnh báo dữ liệu mẻ xe khác chưa cập nhật: "
                + othersRefresh.failed + "/" + othersRefresh.total
                + " collectionComplete=" + othersRefresh.collectionComplete);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Mốc lần làm mới dữ liệu mẻ xe khác gần nhất, theo {@code SystemClock.elapsedRealtime()}.
     * Dùng đồng hồ trôi chứ không phải {@code System.currentTimeMillis()}: một lần đồng bộ NTP
     * nhảy tiến sẽ làm mọi phép tính "đã bao lâu" mất tác dụng.
     */
    private long othersRefreshedAt = 0L;

    /**
     * MỤC 9 — kéo lại dữ liệu mẻ của xe khác NGAY TRƯỚC khi dựng chứng từ gộp, rồi chạy tiếp.
     *
     * <p>Xe chốt là xe cuối cùng xuất hàng, nên số liệu gộp trên phiếu/hoá đơn phải đứng trên
     * bản mới nhất của các xe khác. Màn hình này khoá đồng bộ nền từ lúc {@code bindData()} nên
     * nếu không kéo lại ở đây, dữ liệu xe khác đứng yên từ lúc mở màn hình.
     *
     * <p><b>Không bao giờ chặn.</b> Refresh lỗi (mất sóng) chỉ hiện cảnh báo rồi đi tiếp bằng
     * dữ liệu đang có; trạng thái Others cũ KHÔNG bị hạ cấp nên các đường kiểm tra sẵn có
     * không kích hoạt thêm.
     *
     * <p>Hai điểm dễ sai, đã xử lý:
     * <ul>
     *   <li>BARRIER: rút cạn hàng đợi ghi của màn hình TRƯỚC khi gọi server. {@code
     *       loadRefuelForPreview} chỉ đẩy được thứ đã nằm trong Room; lần ghi còn trong hàng
     *       đợi sẽ bị bản server đè mất — giá, tỉ trọng, giờ vừa gõ biến khỏi chứng từ sắp in.</li>
     *   <li>TÍNH LẠI {@code printItems} SAU {@code bindData()}: bindData dựng lại adapter với
     *       toàn bộ checkbox về false, giữ danh sách cũ là in đúng các object đã lỗi thời.</li>
     * </ul>
     */
    private void refreshOthersBeforeDocument(Runnable onContinue) {
        if (onContinue == null) return;
        if (uniqueId == null || uniqueId.isEmpty() || !isCombinedDocument()
                || !OthersFreshness.needsRefresh(
                        othersRefreshedAt, SystemClock.elapsedRealtime())) {
            onContinue.run();
            return;
        }

        final List<String> checkedUidsBefore = uniqueIdsOf(printItems);
        final List<String> knownUidsBefore = uniqueIdsOf(allItems);
        final boolean previousHasFailure = othersRefresh == null || othersRefresh.hasFailure();

        setProgressDialog();
        new Thread(() -> {
            DataHelper.PreviewLoadResult loaded = null;
            String failure = null;
            try {
                drainPreviewSaveQueue();
                loaded = DataHelper.loadRefuelForPreview(uniqueId);
            } catch (Throwable ex) {
                failure = ex.getMessage();
            }
            final DataHelper.PreviewLoadResult result = loaded;
            final String error = failure;
            runOnUiThread(() -> {
                closeProgressDialog();
                if (isFinishing()) return;
                applyOthersRefresh(result, error, previousHasFailure,
                        checkedUidsBefore, knownUidsBefore, onContinue);
            });
        }).start();
    }

    /**
     * Rút cạn hàng đợi ghi một-luồng của màn hình. Hàng đợi là FIFO nên một tác vụ rỗng nộp
     * bây giờ chỉ xong sau khi mọi lần lưu đang chờ đã xong.
     *
     * <p>Mọi lỗi ở đây đều chỉ ghi log: không rút cạn được thì vẫn phải cho người dùng in.
     */
    private void drainPreviewSaveQueue() {
        if (previewSaveExecutor.isShutdown()) return;
        try {
            previewSaveExecutor.submit(() -> {
            }).get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ex) {
            Logger.appendLog(LOG_TAG, "Không rút cạn được hàng đợi ghi trước khi làm mới: "
                    + ex.getMessage());
        }
    }

    private List<String> uniqueIdsOf(List<RefuelItemData> items) {
        List<String> result = new ArrayList<>();
        if (items == null) return result;
        for (RefuelItemData item : items) {
            String uid = item == null ? null : item.getUniqueId();
            if (uid != null && !uid.trim().isEmpty() && !result.contains(uid))
                result.add(uid);
        }
        return result;
    }

    /** Nhận kết quả làm mới rồi chạy tiếp — mọi nhánh đều kết thúc bằng {@code onContinue}. */
    private void applyOthersRefresh(DataHelper.PreviewLoadResult result, String error,
                                    boolean previousHasFailure,
                                    List<String> checkedUidsBefore,
                                    List<String> knownUidsBefore,
                                    Runnable onContinue) {
        if (result == null || result.item == null || result.superseded) {
            Logger.appendLog(LOG_TAG, "Làm mới mẻ xe khác KHÔNG thành công ("
                    + (error == null ? "không có dữ liệu" : error)
                    + "); vẫn cho in bằng dữ liệu đang có");
            Toast.makeText(this, R.string.warn_others_refresh_failed, Toast.LENGTH_LONG).show();
            onContinue.run();
            return;
        }

        boolean newHasFailure = result.others == null || result.others.hasFailure();
        if (!OthersFreshness.adoptRefreshResult(previousHasFailure, true, newHasFailure)) {
            // Kết quả mới xấu hơn trạng thái đang có. Nhận vào là biến một màn hình đang in
            // được thành không in được — đúng cái đường chặn mới mà bản vá này phải tránh.
            //
            // Và PHẢI bỏ luôn cả result.item, không chỉ giữ cờ othersRefresh cũ: bindData()
            // dựng lại allItems từ chính payload xấu đó, nên mẻ xe khác bị thiếu sẽ rụng khỏi
            // printItems trong khi cờ cũ vẫn nói "đủ dữ liệu" — không đường chặn nào kêu, và
            // phiếu/hoá đơn gộp in ra THIẾU MẺ, tổng nhỏ hơn thực tế. Giữ nguyên dữ liệu đang
            // có là lựa chọn an toàn duy nhất; vẫn cho in như trước lượt làm mới.
            Logger.appendLog(LOG_TAG, "Giữ nguyên dữ liệu Others cũ: lượt làm mới cho kết"
                    + " quả xấu hơn (failed=" + (result.others == null ? -1 : result.others.failed)
                    + ")");
            Toast.makeText(this, R.string.warn_others_refresh_failed, Toast.LENGTH_LONG).show();
            onContinue.run();
            return;
        }
        othersRefresh = result.others;

        refuelData = result.item;
        bindData();
        othersRefreshedAt = SystemClock.elapsedRealtime();

        // Tính lại danh sách in trên chính các object VỪA nạp.
        int restored = truckArrayAdapter == null
                ? 0 : truckArrayAdapter.restoreChecked(checkedUidsBefore);
        printItems = truckArrayAdapter == null
                ? new ArrayList<>() : truckArrayAdapter.getCheckedItems();
        if (restored < checkedUidsBefore.size()) {
            Logger.appendLog(LOG_TAG, "Sau khi làm mới chỉ khôi phục được " + restored + "/"
                    + checkedUidsBefore.size() + " mẻ đã tích");
            Toast.makeText(this, R.string.warn_others_refresh_selection_changed,
                    Toast.LENGTH_LONG).show();
        }

        List<String> appeared = OthersFreshness.newlyAppearedUniqueIds(
                knownUidsBefore, uniqueIdsOf(allItems));
        if (appeared.isEmpty()) {
            onContinue.run();
            return;
        }

        // Mẻ mới xuất hiện thì HỎI, không tự tích: tích thêm một mẻ là đổi số liệu sẽ in ra
        // giấy. Cả hai nút đều đi tiếp, không nút nào chặn.
        Logger.appendLog(LOG_TAG, "Sau khi làm mới có " + appeared.size() + " mẻ mới");
        new AlertDialog.Builder(this)
                .setTitle("Có mẻ mới của xe khác")
                .setMessage("Server vừa trả thêm " + appeared.size()
                        + " mẻ chưa có trên màn hình. Bạn có muốn đưa các mẻ này vào chứng"
                        + " từ không?")
                .setPositiveButton("Thêm vào", (dialog, which) -> {
                    truckArrayAdapter.restoreChecked(appeared);
                    printItems = truckArrayAdapter.getCheckedItems();
                    onContinue.run();
                })
                .setNegativeButton("Bỏ qua", (dialog, which) -> onContinue.run())
                .setCancelable(false)
                .show();
    }

    /**
     * Chứng từ sắp dựng có gộp nhiều mẻ hay không.
     *
     * <p>Quyết định theo DỮ LIỆU sắp in, không theo enum của nút đã bấm: nút "IN PHIẾU" vẫn
     * có thể đang tích nhiều dòng.
     */
    private boolean isCombinedDocument() {
        // FB-1: bỏ vế `printMode == ALL_ITEM`. Nút XUẤT HOÁ ĐƠN đặt ALL_ITEM vô điều kiện
        // (:1614) nên vế đó làm MỌI hoá đơn bị coi là chứng từ gộp, kể cả hoá đơn đúng một
        // mẻ của chính xe mình — lúc đó dữ liệu xe khác không tham gia vào chứng từ, không
        // có gì để mà thiếu. Nay hàm khớp lại với chính javadoc của nó: quyết định theo
        // DỮ LIỆU sắp in.
        return printItems != null && printItems.size() > 1;
    }

    /**
     * Kiểm tra dữ liệu sẽ đưa vào chứng từ (nhóm C — GIỮ NGUYÊN mức chặn).
     *
     * <p>FB-1: nhánh "chưa nhận đủ dữ liệu xe khác" trước đây CHẶN CỨNG ở đây, nghĩa là mất
     * mạng thì không xuất được phiếu/hoá đơn — sai với yêu cầu nghiệp vụ. Nhánh đó đã chuyển
     * thành {@link #warnIncompleteOthersThen(Runnable)}: cảnh báo hai nút, không chặn.
     */
    private boolean blockIncompleteOthersIfCombined() {
        return blockInvalidSelectedDocumentItems();
    }

    /**
     * FB-1 — MẤT MẠNG CHỈ CẢNH BÁO, KHÔNG CHẶN XUẤT CHỨNG TỪ.
     *
     * <p>Trước bản vá này, dữ liệu mẻ xe khác chưa toàn vẹn ⇒ chặn cứng một nút. Ngoài hiện
     * trường mất sóng là chuyện thường, và chặn không làm dữ liệu đúng hơn: người dùng vẫn
     * phải giao hàng, chỉ là không có chứng từ. Nay hai nút, theo đúng khuôn
     * {@link #warnReversedTimeThen(List, Runnable)}: "Vẫn xuất" / "Kiểm tra lại".
     *
     * <p>Hộp thoại phải nói bằng CON SỐ — số mẻ và tổng lít/kg sẽ lên chứng từ — vì đó là thứ
     * duy nhất người dùng đối chiếu được với thực tế chuyến. Bấm "Vẫn xuất" ghi anomaly để
     * sau ca còn đối chiếu được.
     *
     * <p>Gọi SAU {@link #blockInvalidSelectedDocumentItems()} để tổng in ra là tổng cuối cùng:
     * bước đó có thể vừa loại bớt mẻ xe khác chưa đủ điều kiện khỏi {@link #printItems}.
     */
    private void warnIncompleteOthersThen(Runnable onContinue) {
        if (onContinue == null) return;
        OthersFreshness.IncompleteOthersGate gate = OthersFreshness.gateForIncompleteOthers(
                othersRefresh != null, othersRefresh != null && othersRefresh.hasFailure());
        if (gate == OthersFreshness.IncompleteOthersGate.CONTINUE) {
            onContinue.run();
            return;
        }

        int failed = othersRefresh == null ? 0 : othersRefresh.failed;
        int total = othersRefresh == null ? 0 : othersRefresh.total;
        OthersFreshness.DocumentTotals totals = OthersFreshness.documentTotals(printItems);

        Logger.appendLog(LOG_TAG, "Cảnh báo (KHÔNG chặn) xuất chứng từ khi dữ liệu xe khác"
                + " chưa toàn vẹn " + failed + "/" + total + "; sẽ dựng trên "
                + totals.count + " mẻ");

        String message = String.format(locale,
                "Đang ở trạng thái không có mạng hoặc chưa nhận đủ dữ liệu mẻ của xe khác"
                        + " (%d/%d mẻ chưa cập nhật được).\n\n"
                        + "Chứng từ sẽ dựng trên %d mẻ đang có, tổng %,.0f lít / %,.0f kg.\n\n"
                        + "Lưu ý kiểm tra dữ liệu trước khi xuất: nếu chuyến còn mẻ của xe"
                        + " khác chưa về máy thì chứng từ sẽ thiếu.",
                failed, total, totals.count, totals.litres, totals.kilos);

        new AlertDialog.Builder(this)
                .setTitle("Có thể chưa nhận đủ dữ liệu xe khác")
                .setMessage(message)
                .setPositiveButton("Vẫn xuất", (dialog, which) -> {
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=DOCUMENT_INCOMPLETE_OTHERS_ACCEPTED items=%d litres=%.0f"
                                    + " kilos=%.0f failed=%d total=%d",
                            totals.count, totals.litres, totals.kilos, failed, total));
                    onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    /**
     * HỎI trước khi bỏ các mẻ của XE KHÁC chưa đủ điều kiện ra khỏi {@link #printItems}.
     *
     * <p>Trước đây máy tự loại rồi chỉ hiện một Toast. Chủ dự án bác cách đó ngày 06-09-2026:
     * chứng từ in ra khi ấy KHÁC với những gì người dùng đã tích chọn, mà Toast thì trôi mất
     * trong vài giây và không ai phải trả lời nó. Đây là lựa chọn làm ĐỔI SỐ TIỀN trên chứng
     * từ, nên phải là một câu hỏi có người trả lời.
     *
     * <p>Hộp thoại nói bằng CHỮ ĐỎ IN ĐẬM, kể rõ từng mẻ: xe nào, bao nhiêu Kg, thiếu đúng
     * trường gì — vì đó là thứ người dùng cần để quyết nên gọi xe kia bổ sung hay chấp nhận
     * xuất thiếu. "Kiểm tra lại" KHÔNG loại mẻ nào và dừng ở màn hình này.
     *
     * <p>Vẫn KHÔNG chặn: người dùng bấm đồng ý là đi tiếp ngay. Không đụng mẻ của chính xe
     * này (người dùng sửa được chúng, các đường kiểm tra sẵn có phải tiếp tục báo lỗi), không
     * làm gì khi chỉ có một mẻ, và không làm gì khi loại xong sẽ không còn gì để in.
     */
    private void confirmExcludedForeignPrintItemsThen(Runnable onContinue) {
        if (onContinue == null) return;
        if (printItems == null || printItems.size() <= 1) {
            onContinue.run();
            return;
        }

        List<Boolean> foreign = new ArrayList<>(printItems.size());
        for (RefuelItemData item : printItems) foreign.add(!isCurrentTruckItem(item));

        OthersFreshness.Partition partition =
                OthersFreshness.excludeIneligibleForeignItems(printItems, foreign);
        if (!partition.hasExclusion()) {
            onContinue.run();
            return;
        }

        String trucks = android.text.TextUtils.join(", ", partition.excludedTruckNumbers());
        Logger.appendLog(LOG_TAG, "Hỏi trước khi loại " + partition.excluded.size()
                + " mẻ xe khác chưa đủ điều kiện khỏi chứng từ gộp: " + trucks);

        StringBuilder html = new StringBuilder();
        html.append("<b><font color=\"#D32F2F\">")
                .append(getString(R.string.warn_excluded_items_headline))
                .append("</font></b><br/><br/>");
        for (String line : partition.describeExcluded())
            html.append("<b><font color=\"#D32F2F\">• ")
                    .append(android.text.TextUtils.htmlEncode(line))
                    .append("</font></b><br/>");
        html.append("<br/>")
                .append(getString(R.string.warn_excluded_items_total,
                        partition.excludedVolume()))
                .append("<br/><br/>")
                .append(getString(R.string.warn_excluded_items_question));

        new AlertDialog.Builder(this)
                .setTitle(R.string.warn_excluded_items_title)
                .setMessage(android.text.Html.fromHtml(html.toString()))
                // KHÔNG huỷ bằng cách bấm ra ngoài: đây là lựa chọn làm đổi số tiền trên
                // chứng từ, phải là một cái bấm có ý thức.
                .setCancelable(false)
                .setPositiveButton(R.string.warn_excluded_items_accept, (dialog, which) -> {
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=COMBINED_DOCUMENT_EXCLUDED_FOREIGN_ACCEPTED count=%d"
                                    + " trucks=%s litres=%.0f",
                            partition.excluded.size(), trucks, partition.excludedVolume()));
                    printItems = new ArrayList<>(partition.kept);
                    onContinue.run();
                })
                .setNegativeButton(R.string.warn_excluded_items_recheck, (dialog, which) ->
                        Logger.appendLog(LOG_TAG, "Người dùng chọn kiểm tra lại,"
                                + " KHÔNG loại mẻ nào khỏi chứng từ"))
                .show();
    }

    /**
     * Cảnh báo khi lượt thử lại bằng patch sẽ BỎ IM LẶNG một số trường người dùng vừa sửa.
     *
     * <p>Lưu cả gói bị chặn thì máy tự thử lại bằng patch theo {@code Scope.PREVIEW}. Patch
     * báo thành công, nhưng trường nào không nằm trong scope thì không hề được ghi. Bốn ô
     * kiểm tra thiết bị của BM2508 và mã sản phẩm đang rơi vào đúng ca này — người dùng tích
     * xong, máy báo lưu xong, mở lại thấy trống.
     *
     * <p>CHỈ CẢNH BÁO, không chặn (chủ dự án chốt 06-09-2026): thời gian trên sân rất gấp,
     * chặn cả nghiệp vụ vì một trường phụ là tệ hơn nhiều so với để người dùng chủ động nhập
     * lại. Cũng không tự thêm khoá vào scope — thêm nhầm khoá làm patch chặn MỌI lần sửa,
     * đã đo trên máy thật (xem ghi chú ở {@code RefuelFieldPatch.Scope.PREVIEW}).
     */
    private void warnFieldsDroppedByPatch(RefuelItemData item) {
        if (item == null) return;
        List<String> dropped = RefuelFieldPatch.droppedKeysOutsideScope(
                RefuelFieldPatch.Scope.PREVIEW, item.getBaseJson(), item);
        if (dropped.isEmpty()) return;

        String fields = android.text.TextUtils.join(", ", dropped);
        Logger.appendLog(LOG_TAG, "Patch bỏ qua trường ngoài scope uid="
                + item.getUniqueId() + " fields=" + fields);
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=PREVIEW_PATCH_DROPPED_FIELDS uid=%s fields=%s",
                item.getUniqueId(), fields));

        runOnUiThread(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.warn_fields_not_saved_title)
                    .setMessage(getString(R.string.warn_fields_not_saved, fields))
                    .setPositiveButton(R.string.accept, null)
                    .show();
        });
    }

    /** Kiểm tra dữ liệu thực sự sẽ đưa vào chứng từ, áp dụng cả phiếu đơn và phiếu gộp. */
    private boolean blockInvalidSelectedDocumentItems() {
        RefuelItemData header = documentHeader();
        if (header != null && header.getAirlineModel() == null) {
            Logger.appendLog(LOG_TAG, "CHẶN xuất chứng từ: thiếu lookup hãng bay của xe hiện tại"
                    + " uid=" + header.getUniqueId());
            showBusinessError("Chưa có đủ thông tin hãng bay để tạo chứng từ. "
                    + "Hãy bấm CẬP NHẬT rồi thử lại.");
            return true;
        }

        // MỤC 9: mẻ của XE KHÁC chưa đủ điều kiện — kể cả thiếu tỉ trọng / nhiệt độ đo tay /
        // số QC, vốn đang bị validate() CHẶN CỨNG — thì loại khỏi chứng từ gộp thay vì chặn
        // nút. Máy này chỉ đọc mẻ của xe khác nên chặn ở đó là bắt người dùng chờ vô hạn một
        // thứ họ không sửa được — và lượt làm mới vừa chạy có thể vừa kéo về đúng một mẻ như
        // vậy, biến "hôm nay in được" thành "mai không in được".
        //
        // Lượt loại đó KHÔNG còn nằm ở đây: nó đã được hỏi và người dùng đã trả lời ở
        // confirmExcludedForeignPrintItemsThen(), chạy TRƯỚC hàm này. Đừng gọi lại ở đây,
        // nếu không mẻ sẽ bị loại lần hai mà không ai được hỏi.

        // Collection đầy đủ chỉ chứng minh không thiếu UID. Từng mẻ vẫn có thể đang
        // PROCESSING hoặc payload server thiếu trường bắt buộc; không tự suy dữ liệu.
        if (printItems != null) {
            for (RefuelItemData item : printItems) {
                if (item == null || item.getStartTime() == null || item.getEndTime() == null
                        || item.getStatus() != REFUEL_ITEM_STATUS.DONE
                        || item.getRealAmount() <= 0
                        || (header != null && header.getAirlineId() > 0
                        && item != null && item.getAirlineId() != header.getAirlineId())) {
                    String truckNo = item == null || item.getTruckNo() == null
                            ? "(chưa rõ xe)" : item.getTruckNo();
                    Logger.appendLog(LOG_TAG, "CHẶN xuất phiếu gộp: mẻ thiếu trường bắt buộc xe="
                            + truckNo + " uid="
                            + (item == null ? "null" : item.getUniqueId()));
                    showBusinessError("Mẻ của xe " + truckNo
                            + " chưa hoàn tất hoặc thiếu giờ, sản lượng hay thông tin chuyến bay. "
                            + "Hãy bấm CẬP NHẬT rồi thử lại.");
                    return true;
                }
            }
        }
        return false;
    }

    boolean isEditable = true;
    ActivityRefuelPreviewBinding binding;
    InvoicePreviewBinding previewBinding;
    PreviewExtractBinding extractBinding;
    TruckArrayAdapter truckArrayAdapter;
    RefuelItemData refuelData;


    InvoiceModel invoiceModel;
    BaseDialog printDialog;
    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);


    //validate data before print preview
    private boolean validate() {
        return validate(false);
    }

    private boolean validate(boolean isReturn) {
        if (printItems == null || printItems.size() <= 0) {
            showErrorMessage(R.string.preview_empty_list);
            return false;
        }

        // Luôn kiểm chính các checkbox sẽ in. `refuelData` chỉ là dòng đang xem và có thể
        // là một replica không nằm trong tập được chọn, kể cả khi nút đang ở ONE_ITEM.
        //
        // MỨC CHẶN Ở ĐÂY GIỮ NGUYÊN — nó nhắm vào mẻ của CHÍNH xe này, thứ người dùng gõ
        // được ngay tại chỗ. Mẻ của XE KHÁC thiếu tỉ trọng / QC đã bị
        // excludeIneligibleForeignPrintItems() loại khỏi chứng từ gộp kèm cảnh báo TRƯỚC khi
        // vào đây (mọi đường in đều gọi blockIncompleteOthersIfCombined trước validate),
        // nên vòng lặp này không còn chặn vì một thứ người dùng không sửa được.
        boolean valid = true;
        boolean validQC = true;
        boolean hasReturn = false;
        for (RefuelItemData item : printItems) {
            valid = item != null
                    && item.getManualTemperature() > 0 && item.getDensity() > 0;
            validQC = item != null && item.getQualityNo() != null
                    && !item.getQualityNo().isEmpty();
            hasReturn |= item != null && item.getReturnAmount() > 0;

            if (!valid || !validQC) {
                if (item != null) {
                    truckArrayAdapter.setSelectedObject(item);
                    refuelData = item;
                    binding.setMItem(refuelData);
                    isEditable = isCurrentTruckItem(refuelData) && !refuelData.isExported();
                }
                break;
            }
        }
        if (!valid || !validQC)
            showErrorMessage(!valid ? R.string.invalid_density_temperature : R.string.invalid_qc_no);

        if (isReturn && !hasReturn) {
            showErrorMessage(R.string.no_return_amount);
            return false;
        }
        return valid && validQC;
    }

    private boolean hasDensityWarning(List<RefuelItemData> items) {
        if (items == null || items.size() <= 1) return false;

        List<Integer> specialAirlineIds = Arrays.asList(1, 3, 476, 489, 497);

        boolean hasSpecialAirline = false;
        List<Double> densities = new ArrayList<>();

        for (RefuelItemData item : items) {
            if (item == null) continue;

            if (specialAirlineIds.contains(item.getAirlineId())) {
                hasSpecialAirline = true;
            }

            Double density = item.getDensity();
            if (density != null && density > 0) {
                // làm tròn 3 số để tránh lệch double nhỏ
                double normalized = Math.round(density * 1000.0) / 1000.0;
                densities.add(normalized);
            }
        }

        if (!hasSpecialAirline || densities.size() <= 1) return false;

        double first = densities.get(0);
        for (int i = 1; i < densities.size(); i++) {
            if (Double.compare(first, densities.get(i)) != 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Chặn cứng việc tạo phiếu khi có mẻ ghi giờ kết thúc sớm hơn giờ bắt đầu.
     *
     * <p>Khác cảnh báo mẻ quá dài: ở đây không có nút đi tiếp. Một mẻ âm thời gian là sai
     * chắc chắn, in ra thì hoá đơn mang số liệu không giải thích được.
     *
     * @return true nếu được phép đi tiếp.
     */
    private boolean blockReversedRefuelTime(List<RefuelItemData> items) {
        StringBuilder detail = new StringBuilder();

        if (items != null) {
            for (RefuelItemData item : items) {
                if (!RefuelTimeValidator.endsBeforeStart(item)) continue;

                String flightCode = item.getFlightCode();
                detail.append("\n• ")
                        .append(flightCode == null || flightCode.isEmpty()
                                ? "(chưa có số hiệu)" : flightCode)
                        .append(": bắt đầu ")
                        .append(DateUtils.formatDate(item.getStartTime(), "dd/MM HH:mm"))
                        .append(", kết thúc ")
                        .append(DateUtils.formatDate(item.getEndTime(), "dd/MM HH:mm"));
            }
        }

        if (detail.length() == 0) return true;

        Logger.appendLog(LOG_TAG, "CHẶN tạo phiếu, giờ kết thúc sớm hơn giờ bắt đầu:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Không tạo được phiếu")
                .setMessage("Giờ kết thúc sớm hơn giờ bắt đầu:" + detail
                        + "\n\nPhải sửa lại giờ tra nạp trước khi tạo phiếu.")
                .setPositiveButton("Đã hiểu", null)
                .setCancelable(false)
                .show();

        return false;
    }

    /**
     * Cảnh báo mẻ có thời gian tra nạp quá dài rồi chạy tiếp {@code onContinue}.
     *
     * <p>Chỉ cảnh báo, không chặn: hoá đơn vẫn in được sau khi người dùng bấm Tiếp tục.
     * Ghi log cả lúc cảnh báo lẫn lúc người dùng chọn in tiếp, để sau ca đối chiếu được
     * hoá đơn nào đã in với thời gian bất thường.
     */
    private void warnLongRefuelThen(List<RefuelItemData> items, Runnable onContinue) {
        List<RefuelItemData> longItems = new ArrayList<>();

        if (items != null) {
            for (RefuelItemData item : items) {
                if (RefuelTimeValidator.exceedsMaxDuration(item)) longItems.add(item);
            }
        }

        if (longItems.isEmpty()) {
            if (onContinue != null) onContinue.run();
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (RefuelItemData item : longItems) {
            String flightCode = item.getFlightCode();
            detail.append("\n• ")
                    .append(flightCode == null || flightCode.isEmpty()
                            ? "(chưa có số hiệu)" : flightCode)
                    .append(": ")
                    .append(RefuelTimeValidator.durationMinutes(item))
                    .append(" phút");
        }

        Logger.appendLog(LOG_TAG, "Cảnh báo thời gian tra nạp quá dài trước khi tạo receipt:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo thời gian tra nạp")
                .setMessage("Thời gian tra nạp quá dài (trên "
                        + RefuelTimeValidator.MAX_DURATION_MS / 60000 + " phút):"
                        + detail + "\n\nBạn có muốn tiếp tục in không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    Logger.appendLog(LOG_TAG, "Người dùng tiếp tục in dù thời gian tra nạp"
                            + " quá dài:" + detail.toString().replace('\n', ' '));
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    private void showDensityWarningDialog(Runnable onContinue) {
        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo (Chỉ áp dụng với VN, 0V, VN1)")
                .setMessage("Tỷ trọng giữa các lần tra nạp không giống nhau. Bạn có muốn tiếp tục lưu/in không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }
    private void bindData() {
        if (refuelData == null) {
            Logger.appendLog("null item, uniqueid : " + uniqueId);
            showErrorMessage(R.string.error_loading_item);
            finish();
            return;
        }
        Logger.appendLog("PRW","flight code : " + refuelData.getFlightCode());
        isEditable = isCurrentTruckItem(refuelData) && !refuelData.isExported();
        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL)
            setContentView(R.layout.activity_refuel_preview);
        else
            setContentView(R.layout.preview_extract);

        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
            //binding =  DataBindingUtil.setContentView(this, R.layout.activity_refuel_preview);
            binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.activity_refuel_preview, null, false);
            binding.setMItem(refuelData);
            setContentView(binding.getRoot());
            if (((Button)findViewById(R.id.refuel_preview_review)) != null) {
                ((Button)findViewById(R.id.refuel_preview_review)).setText(hasReview ? R.string.review_view : R.string.review_create);
            }
            //findViewById(R.id.refuel_preview_international).setEnabled(isEditable);
            ((CheckBox)findViewById(R.id.refuel_preview_international)).setOnTouchListener((view, motionEvent) -> {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    showConfirmMessage(R.string.change_route_type_confirm, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            view.performClick();
                            return null;
                        }
                    });
                    return true;
                } else {
                    return false;
                }
            });
        } else {

            if (isCurrentTruckItem(refuelData)
                    && refuelData.getProductId() > 0 && productList != null) {
                for (ProductModel product : productList) {
                    if (product.getId() == refuelData.getProductId()) {
                        refuelData.setPCode(product.getCode());
                        refuelData.setPName(product.getName());
                        refuelData.setProductName(product.getName());
                        break;
                    }
                }
            }

            //extractBinding = DataBindingUtil.setContentView(this, R.layout.preview_extract);
            extractBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.preview_extract, null, false);

            extractBinding.setMItem(refuelData);

            setContentView(extractBinding.getRoot());

            // Phiếu 75.01 nhập được ở mọi bản; riêng nút in nằm trong màn hình phiếu
            // và chỉ hiện ở chế độ in nhiệt (docs/PLAN-BM7501 §17).
            View btnOpen7501 = findViewById(R.id.btnOpen7501);
            if (btnOpen7501 != null) {
                btnOpen7501.setVisibility(View.VISIBLE);
            }
        }


        ArrayAdapter<AirlineModel> spinnerAdapter = new ArrayAdapter<AirlineModel>(this, R.layout.support_simple_spinner_dropdown_item, airlines);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        Spinner airline_spinner = findViewById(R.id.refuel_preview_airline_spinner);
        airline_spinner.setAdapter(spinnerAdapter);

        if (refuelData.getAirlineId() > 0) {
            for (int i = 0; i < airline_spinner.getCount(); i++) {
                AirlineModel item = (AirlineModel) airline_spinner.getItemAtPosition(i);
                if (refuelData.getAirlineId() == item.getId()) {
                    airline_spinner.setSelection(i);
                    ((TextView) findViewById(R.id.refuel_preview_airline)).setText(item.getName());
                    if (isCurrentTruckItem(refuelData)) setAirline(refuelData, item);
                    break;
                }
            }

            final boolean[] initialAirlineCallback = {true};
            airline_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    AirlineModel selected = (AirlineModel) parent.getItemAtPosition(position);
                    if (!isCurrentTruckItem(refuelData)) return;
                    // Spinner có thể tự phát callback sau bind. Nếu vẫn đúng hãng đang có,
                    // đó không phải thao tác người dùng và không được xoá tên/đổi giá.
                    if (initialAirlineCallback[0]) {
                        initialAirlineCallback[0] = false;
                        if (selected != null
                                && selected.getId() == refuelData.getAirlineId()) return;
                    }
                    refuelData.setInvoiceNameCharter(null);
                    setAirline(selected);

                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            // Mẻ xe khác giữ nguyên snapshot trong RAM. Chỉ enrich một mẻ khác nhưng vẫn
            // thuộc CHÍNH xe này (tách mẻ/nạp thêm), để nó có thể làm header khi người dùng
            // chỉ chọn mẻ đó. Replica xe khác tuyệt đối không bị mutate.
            if (refuelData.getOthers() != null) {
                for (RefuelItemData other : refuelData.getOthers()) {
                    if (!isCurrentTruckItem(other) || other.getAirlineModel() != null) continue;
                    AirlineModel lookup = null;
                    if (airlines != null) {
                        for (AirlineModel airline : airlines) {
                            if (airline != null && airline.getId() == other.getAirlineId()) {
                                lookup = airline;
                                break;
                            }
                        }
                    }
                    if (lookup != null) other.setAirlineModel(lookup);
                }
            }
        }
        if (isCurrentTruckItem(refuelData)
                && refuelData.getProductId() > 0 && productList != null) {
            for (ProductModel product : productList) {
                if (product.getId() == refuelData.getProductId()) {
                    refuelData.setPCode(product.getCode());
                    refuelData.setPName(product.getName());
                    refuelData.setProductName(product.getName());
                    break;
                }
            }
        }

        allItems.clear();
        allItems.add(refuelData);

        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {

            //allItems.add(refuelData);
            allItems.addAll(refuelData.getOthers());

            allItems.sort(new Comparator<RefuelItemData>() {
                @Override
                public int compare(RefuelItemData o1, RefuelItemData o2) {
                    Date left = o1 == null ? null : o1.getEndTime();
                    Date right = o2 == null ? null : o2.getEndTime();
                    if (left == right) return 0;
                    if (left == null) return 1;
                    if (right == null) return -1;
                    return left.compareTo(right);
                }
            });
            int selectedPos = 0;
            for (int i = 0; i < allItems.size(); i++)
                if (allItems.get(i).getId().equals(refuelData.getId()))
                    selectedPos = i;
            truckArrayAdapter = new TruckArrayAdapter(this, allItems);
            truckArrayAdapter.setSelected(selectedPos);
            ListView lv = findViewById(R.id.refuel_preview_truck_list);
            lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            lv.setAdapter(truckArrayAdapter);
            lv.setSelection(selectedPos);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    refuelData = (RefuelItemData) parent.getItemAtPosition(position);
                    binding.setMItem(refuelData);
                    isEditable = isCurrentTruckItem(refuelData) && !refuelData.isExported();

                    truckArrayAdapter.setSelectedObject(refuelData);


                }

            });


        }
        //Logger.appendLog("PRW","End binding");
        closeProgressDialog();

        if (BuildConfig.FHS) {
            findViewById(R.id.refuel_preview_check_form).setVisibility(View.GONE);
            findViewById(R.id.refuel_preview_print_all).setVisibility(View.GONE);
        }

        DataHelper.lockSync();
    }

    /** Mở phiếu BM 75.01 của ĐÚNG mẻ hút đang xem — phiếu và mẻ là quan hệ một-một. */
    private void openBM7501() {
        if (refuelData == null || refuelData.getUniqueId() == null) {
            showErrorMessage(R.string.bm7501_missing_refuel);
            return;
        }
        Intent intent = new Intent(this, B7501Activity.class);
        intent.putExtra(B7501Activity.EXTRA_REFUEL_UNIQUE_ID, refuelData.getUniqueId());
        startActivity(intent);
    }

    private boolean oldTemplate = true;
    InvoiceFormModel[] invoiceForms;
    //List<InvoiceFormModel> invoiceForms;
    //List<InvoiceFormModel> billForms;
    InvoiceFormModel defaultModel = null;

    private void openReceipt() {
        openReceipt(false);
    }

    private void openReceipt(boolean isReturn) {

        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();

        if (blockDocumentWithoutCurrentTruck()) return;
        // Mục 9: kéo lại mẻ xe khác NGAY TRƯỚC khi dựng chứng từ gộp; lỗi refresh chỉ cảnh
        // báo, không chặn. Phần còn lại của hàm chạy tiếp trong callback.
        refreshOthersBeforeDocument(() -> continueOpenReceipt(isReturn));
    }

    private void continueOpenReceipt(boolean isReturn) {

        // Nút "IN PHIẾU" vẫn có thể mang nhiều checkbox. Quyết định theo
        // dữ liệu thực sự sẽ in, không chỉ theo enum của nút đã bấm.
        confirmExcludedForeignPrintItemsThen(() -> {
            if (blockIncompleteOthersIfCombined()) return;

            // FB-1: mất mạng chỉ CẢNH BÁO. Áp cho cả phiếu thường và phiếu hoàn.
            warnIncompleteOthersThen(() -> continueOpenReceiptChecked(isReturn));
        });
    }

    private void continueOpenReceiptChecked(boolean isReturn) {

        if (!isReturn) {

            if (validate() && blockReversedRefuelTime(printItems)) {

                boolean hasWarning = hasDensityWarning(printItems);

                Runnable continueAction = () -> {
                    String[] printedItems = checkPrintedItems();
                    if (printedItems.length > 0) {
                        showCancelReceiptInput(printedItems);
                    } else {
                        showReceiptPreview(true);
                    }
                };

                // Chặn cuối trước khi dựng receipt: mẻ dài bất thường vẫn in được, nhưng
                // người dùng phải thấy nó một lần. Nối SAU cảnh báo tỷ trọng để hai hộp
                // thoại không chồng lên nhau.
                Runnable checkDurationThenContinue =
                        () -> warnLongRefuelThen(printItems, continueAction);

                if (hasWarning) {
                    showDensityWarningDialog(checkDurationThenContinue);
                } else {
                    checkDurationThenContinue.run();
                }
            }

        } else {
            if (validate(isReturn)) {

                boolean hasWarning = hasDensityWarning(printItems);

                Runnable continueAction = () -> {
                    try {
                        ReceiptModel model = ReceiptModel.createReceipt(
                                documentItemsCurrentFirst(), null, true, null, true
                        );
                        Intent intent = new Intent(this, PrintReceiptActivity.class);
                        intent.putExtra("RECEIPT", model.toJson());
                        startActivity(intent);

                    } catch (InvalidRefuelTimeException ex) {
                        showBusinessError(ex.getMessage());
                    }
                };

                if (hasWarning) {
                    showDensityWarningDialog(continueAction);
                } else {
                    continueAction.run();
                }
            }
        }
    }

    private void showCancelReceiptInput(String[] printedItems) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 5, 0, 5);
        final RadioGroup radGroup = new RadioGroup(this);
        radGroup.setOrientation(RadioGroup.VERTICAL);

        final EditText reasonEditText = new EditText(this);
        reasonEditText.setVisibility(View.INVISIBLE);
        layout.addView(radGroup);
        int id = 0;
        for (String item : getResources().getStringArray(R.array.cancel_reason_array)
        ) {

            RadioButton radBtn = new RadioButton(this);
            radBtn.setText(item);
            radBtn.setPadding(5, 5, 5, 5);
            radBtn.setChecked(id == 0);
            radBtn.setId(id);
            radBtn.setTag(id++);

            radBtn.setOnClickListener(view -> {
                if ((int) view.getTag() == 3) {
                    reasonEditText.setVisibility(View.VISIBLE);
                    //reasonEditText.requestFocus();
                } else
                    reasonEditText.setVisibility(View.INVISIBLE);
            });
            radGroup.addView(radBtn);

        }





        layout.addView(reasonEditText);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_receipt_info)
                .setMessage(R.string.cancel_receipt_message)
                .setView(layout)
                .setPositiveButton(R.string.create_new_receipt, (dialog1, which) -> {
                    String reason = reasonEditText.getText().toString();
                    int selected = radGroup.getCheckedRadioButtonId();
                    if (selected < 3)
                        reason = getResources().getStringArray(R.array.cancel_reason_array)[selected];

                    if (reason.isEmpty())
                        showErrorMessage(R.string.cancel_reason_required);
                    else {
                        final String cancelReason = reason;
                        /*
                        showConfirmMessage(R.string.confirm_receip_cancel, () -> {
                            new Thread(() -> DataHelper.cancelReceipts(printedItems, cancelReason)).start();
                            showReceiptPreview();
                            return null;
                        });*/
                        //new Thread(() -> DataHelper.cancelReceipts(printedItems, cancelReason)).start();
                        showReceiptPreview(null,printedItems,true);
                        dialog1.dismiss();


                    }
                })
                .setNeutralButton(BuildConfig.THERMAL_PRINTER? R.string.re_print: R.string.back, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (BuildConfig.THERMAL_PRINTER)
                            reprintReceipt(resolveReceiptForReprint(printedItems));
                        dialogInterface.dismiss();
                    }
                })
                .setNegativeButton(R.string.use_old_receipt, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showReceiptPreview(oldNumber,printedItems,false);
                        dialogInterface.dismiss();
                    }
                })
                .create();


        dialog.show();

    }
    private void showReceiptPreview( boolean createNew)
    {
        showReceiptPreview(null, null,createNew);
    }
    private void showReceiptPreview(String oldNumber, String[] replacedReceipts, boolean createNew) {
        //ReceiptModel model = ReceiptModel.createReceipt(printItems,oldNumber, createNew);
        try {
            if (blockDocumentWithoutCurrentTruck()) return;
            ReceiptModel model = ReceiptModel.createReceipt(
                    documentItemsCurrentFirst(), replacedReceipts, false, oldNumber, createNew
            );
            Intent intent = new Intent(this, PrintReceiptActivity.class);
            intent.putExtra("RECEIPT", model.toJson());
            startActivityForResult(intent, RECEIPT_WINDOW);

        } catch (InvalidRefuelTimeException ex) {
            showBusinessError(ex.getMessage());
        }
    }

    private String resolveReceiptForReprint(String[] printedItems) {
        RefuelItemData current = documentHeader();
        String preferred = current == null ? null : current.getReceiptUniqueId();
        if (printedItems != null && preferred != null) {
            for (String receiptUid : printedItems)
                if (preferred.equals(receiptUid)) return preferred;
        }
        if (printedItems != null) {
            for (String receiptUid : printedItems)
                if (receiptUid != null && !receiptUid.isEmpty()) return receiptUid;
        }
        return null;
    }

    private void reprintReceipt(String receiptUniqueId) {
        //ReceiptModel model = ReceiptModel.createReceipt(printItems,oldNumber, createNew);
        if (receiptUniqueId == null || receiptUniqueId.isEmpty()) {
            showBusinessError("Không xác định được phiếu cần in lại");
            return;
        }
        Intent intent = new Intent(this, PrintReceiptActivity.class);
        intent.putExtra("RECEIPT_ID", receiptUniqueId);
        startActivityForResult(intent, RECEIPT_WINDOW);
    }
    private String oldNumber;
    private String[] checkPrintedItems() {
        ArrayList<String> stringArrayList = new ArrayList<String>();
        oldNumber = null;
        for (RefuelItemData item : printItems
        ) {
            if (item.getReceiptCount() >0 && item.getReceiptNumber() != null && !item.getReceiptNumber().isEmpty()) {
                stringArrayList.add(item.getReceiptUniqueId());
                oldNumber = item.getReceiptNumber();
            }
        }
        return stringArrayList.toArray(new String[0]);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECEIPT_WINDOW && resultCode == Activity.RESULT_OK) {
            String number = data.getStringExtra("number");
            String uniqueId = data.getStringExtra("uniqueId");
            double techlog = data.getDoubleExtra("techlog",0);
            updateAllReceipt(uniqueId, number,techlog);
            truckArrayAdapter.notifyDataSetChanged();
        } else if (requestCode == INVOICE_WINDOW && resultCode == Activity.RESULT_OK) {
            String number = data.getStringExtra("number");
            int formId = data.getIntExtra("formId", 0);
            double techlog = data.getDoubleExtra("techlog",0);
            INVOICE_TYPE printTemplate = (INVOICE_TYPE) data.getSerializableExtra("printTemplate");
            if (updateAllInvoice(number, formId, printTemplate,techlog))
                truckArrayAdapter.notifyDataSetChanged();
        }
        else if (requestCode == REVIEW_WINDOW && resultCode == RESULT_OK)
        {
            hasReview = true;
            ((Button)findViewById(R.id.refuel_preview_review)).setText(hasReview? R.string.review_view: R.string.review_create);

        }
    }

    private void preview() {
        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();
        if (blockDocumentWithoutCurrentTruck()) return;
        // Mục 9: đường xem trước cũng dựng chứng từ gộp nên cũng phải đứng trên dữ liệu mới
        // nhất của xe khác. Lỗi refresh chỉ cảnh báo, không chặn.
        refreshOthersBeforeDocument(this::continuePreview);
    }

    private void continuePreview() {
        confirmExcludedForeignPrintItemsThen(() -> {
            if (blockIncompleteOthersIfCombined()) return;
            // FB-1: mất mạng chỉ CẢNH BÁO, không chặn đường xem trước chứng từ.
            warnIncompleteOthersThen(this::continuePreviewChecked);
        });
    }

    private void continuePreviewChecked() {
        try {
            SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
            oldTemplate = preferences.getBoolean("OLD_TEMPLATE", true);

            RefuelItemData documentHeader = documentHeader();
            if (documentHeader == null) return;
            if (documentHeader.getAirlineId() <= 0
                    || documentHeader.getAirlineModel() == null) {
                showErrorMessage(R.string.invalid_airline_model);

                return;
            }
            if (documentHeader.getDriverId() == 0 || documentHeader.getOperatorId() == 0) {
                showErrorMessage(R.string.invalid_user);
                return;
            }
            if (!validate()) {

                return;
            }
            invoiceForms = FMSApplication.getApplication().getInvoiceForms();

            defaultModel = null;

            invoiceModel = InvoiceModel.fromRefuel(
                    documentHeader(), new ArrayList<>(printItems));
            setDefaultForm();

            printDialog = new BaseDialog(this);

            previewBinding = DataBindingUtil.inflate(printDialog.getLayoutInflater(), R.layout.invoice_preview, null, false);
            previewBinding.setInvoiceItem(invoiceModel);
            printDialog.setContentView(previewBinding.getRoot());
            RadioGroup radioGroup = printDialog.findViewById(R.id.radioTemplate);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int id = checkedId;

                invoiceModel.setInvoiceType(id == R.id.radInvoice ? INVOICE_TYPE.INVOICE : INVOICE_TYPE.BILL);
                setDefaultForm();
                previewBinding.invalidateAll();

            });
            int selectedIndex = !documentHeader.isInternational()
                    && !documentHeader.getAirlineModel().isInternational() ? 1 : 0;


            ((RadioButton) radioGroup.getChildAt(selectedIndex)).setChecked(true);

            ((RadioButton) printDialog.findViewById(R.id.radOld)).setChecked(oldTemplate);


            InvoiceItemAdapter itemAdapter = new InvoiceItemAdapter(this, invoiceModel.getItems());
            ((ListView) printDialog.findViewById(R.id.invoice_preview_item_list)).setAdapter(itemAdapter);
            printDialog.findViewById(R.id.btnPrintInvoice).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    boolean invoice = ((RadioButton) radioGroup.getChildAt(0)).isChecked();
                    oldTemplate = ((RadioButton) printDialog.findViewById(R.id.radOld)).isChecked();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("OLD_TEMPLATE", oldTemplate);
                    editor.commit();
                    print(oldTemplate, invoice, false);

                    printDialog.findViewById(R.id.row_invoice_number).setVisibility(View.VISIBLE);
                }
            });

            printDialog.findViewById(R.id.btnPrintTest).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    boolean invoice = ((RadioButton) radioGroup.getChildAt(0)).isChecked();
                    oldTemplate = ((RadioButton) printDialog.findViewById(R.id.radOld)).isChecked();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("OLD_TEMPLATE", oldTemplate);
                    editor.apply();


                    print(oldTemplate, invoice, true);

                    //printDialog.findViewById(R.id.row_invoice_number).setVisibility(View.VISIBLE);
                }
            });
            printDialog.findViewById(R.id.btnPrintCancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    printDialog.dismiss();
                }
            });


            Objects.requireNonNull(printDialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            printDialog.show();
        } catch (Exception ex) {
            Logger.appendLog(ex.getMessage());
        }

    }

    private void openPrintInvoice() {
        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();
        if (blockDocumentWithoutCurrentTruck()) return;
        // Mục 9: kéo lại mẻ xe khác NGAY TRƯỚC khi dựng chứng từ gộp. Lỗi refresh không
        // chặn — chỉ cảnh báo rồi đi tiếp bằng dữ liệu đang có.
        refreshOthersBeforeDocument(this::continueOpenPrintInvoice);
    }

    private void continueOpenPrintInvoice() {
        confirmExcludedForeignPrintItemsThen(() -> {
            if (blockIncompleteOthersIfCombined()) return;
            // FB-1: mất mạng chỉ CẢNH BÁO, không chặn xuất hoá đơn.
            warnIncompleteOthersThen(this::continueOpenPrintInvoiceChecked);
        });
    }

    private void continueOpenPrintInvoiceChecked() {
        invoiceForms = FMSApplication.getApplication().getInvoiceForms();

        defaultModel = null;

        if (validate()) {

            // Giờ đảo ngược: CẢNH BÁO hai nút, không chặn cứng như đường phiếu. Đường phiếu
            // giữ nguyên blockReversedRefuelTime, không đụng.
            warnReversedTimeThen(printItems,
                    () -> warnInvoiceTimeThen(printItems, this::showInvoicePreview));

        }

    }

    /**
     * Cảnh báo mẻ có giờ kết thúc sớm hơn giờ bắt đầu trước khi XUẤT HOÁ ĐƠN.
     *
     * <p>Khác đường tạo phiếu (chặn cứng, không có nút đi tiếp): hoá đơn điện tử có ca ngoại
     * lệ thật ngoài hiện trường và mẻ sai giờ có thể thuộc XE KHÁC — máy này chỉ đọc, người
     * dùng không sửa được, chặn cứng ở đây là khoá luôn việc xuất hoá đơn của cả chuyến.
     * Vì vậy hai nút: "Vẫn xuất" và "Kiểm tra lại", mặc định không tự đi tiếp.
     */
    private void warnReversedTimeThen(List<RefuelItemData> items, Runnable onContinue) {
        List<String> issues = RefuelTimeValidator.reversedTimeItems(items);
        if (issues.isEmpty()) {
            if (onContinue != null) onContinue.run();
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (String issue : issues) detail.append("\n• ").append(issue);
        String flat = detail.toString().replace('\n', ' ');

        Logger.appendLog(LOG_TAG, "Cảnh báo giờ đảo ngược trước khi xuất HĐĐT:" + flat);

        new AlertDialog.Builder(this)
                .setTitle("Giờ kết thúc sớm hơn giờ bắt đầu")
                .setMessage("Các mẻ sau có giờ kết thúc sớm hơn giờ bắt đầu:" + detail
                        + "\n\nHoá đơn sẽ mang giờ này. Nếu mẻ thuộc xe khác, hãy báo xe đó"
                        + " sửa lại trước khi xuất.")
                .setPositiveButton("Vẫn xuất", (dialog, which) -> {
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=INVOICE_REVERSED_TIME_ACCEPTED items=%d detail=%s",
                            issues.size(), flat));
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    /**
     * Cảnh báo giờ bất thường trước khi xuất hoá đơn rồi chạy tiếp {@code onContinue}.
     *
     * <p>Chỉ cảnh báo, chưa chặn: hiện trường có ca ngoại lệ thật và hoá đơn vẫn phải xuất được.
     * Nhưng hai giá trị sẽ IN LÊN hoá đơn phải hiện ra một lần — trước bản vá này không màn hình
     * nào cho người dùng thấy chúng, nên phiếu 2619EY0 in giờ bắt đầu 06:34 mà không ai biết cho
     * tới lúc cầm tờ giấy.
     */
    private void warnInvoiceTimeThen(List<RefuelItemData> items, Runnable onContinue) {
        List<String> issues = new ArrayList<>();

        if (items != null) {
            for (RefuelItemData item : items) {
                List<String> outside = RefuelTimeValidator.outsideApproachWindow(item);
                if (outside.isEmpty()) continue;

                String truckNo = item.getTruckNo();
                for (String error : outside)
                    issues.add((truckNo == null || truckNo.isEmpty() ? "(chưa rõ xe)" : truckNo)
                            + ": " + error);
            }
        }

        long span = RefuelTimeValidator.invoiceSpanMs(items);
        if (span > RefuelTimeValidator.MAX_INVOICE_SPAN_MS)
            issues.add("Toàn chuyến kéo dài " + span / 60000 + " phút, nhiều khả năng có mẻ mang"
                    + " giờ cũ chưa được cập nhật.");

        if (issues.isEmpty()) {
            if (onContinue != null) onContinue.run();
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (String issue : issues) detail.append("\n• ").append(issue);

        Logger.appendLog(LOG_TAG, "Cảnh báo giờ trước khi xuất HĐĐT:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Kiểm tra lại giờ tra nạp")
                .setMessage("Giờ sẽ in lên hoá đơn:"
                        + "\n  Bắt đầu:  " + DateUtils.formatDate(invoiceStart(items), "dd/MM HH:mm")
                        + "\n  Kết thúc: " + DateUtils.formatDate(invoiceEnd(items), "dd/MM HH:mm")
                        + "\n" + detail
                        + "\n\nBạn có muốn tiếp tục xuất hoá đơn không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    Logger.appendLog(LOG_TAG, "Người dùng tiếp tục xuất HĐĐT dù giờ bất thường:"
                            + detail.toString().replace('\n', ' '));
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    /** Giờ bắt đầu sẽ in lên hoá đơn — cùng phép gộp với {@code InvoiceModel.fromRefuel}. */
    private Date invoiceStart(List<RefuelItemData> items) {
        Date min = null;
        if (items != null)
            for (RefuelItemData item : items) {
                Date start = item == null ? null : item.getStartTime();
                if (start != null && (min == null || start.before(min))) min = start;
            }
        return min;
    }

    /** Giờ kết thúc sẽ in lên hoá đơn — cùng phép gộp với {@code InvoiceModel.fromRefuel}. */
    private Date invoiceEnd(List<RefuelItemData> items) {
        Date max = null;
        if (items != null)
            for (RefuelItemData item : items) {
                Date end = item == null ? null : item.getEndTime();
                if (end != null && (max == null || end.after(max))) max = end;
            }
        return max;
    }

    private void showInvoicePreview() {
        try {
            InvoiceModel model = InvoiceModel.fromRefuel(
                    documentHeader(), new ArrayList<>(printItems));
            setDefaultForm(model);
            Intent intent = new Intent(this, PrintInvoiceActivity.class);
            intent.putExtra("INVOICE", model.toJson());
            startActivityForResult(intent, INVOICE_WINDOW);
        } catch (IllegalArgumentException ex) {
            showBusinessError(ex.getMessage());
        }

    }

    private void print(boolean oldTemplate, boolean invoice) {
        print(oldTemplate, invoice, false);
    }

    private void print(boolean oldTemplate, boolean invoice, boolean test) {
        printTest = test;
        if (invoice)
            printInvoice(oldTemplate);
        else printBill(oldTemplate);
    }

    private void setDefaultForm() {
        setDefaultForm(invoiceModel);
    }

    private void setDefaultForm(InvoiceModel model) {
        if (model != null) {

            for (InvoiceFormModel item : invoiceForms) {

                if (item.isLocalDefault() && item.getPrintTemplate() == model.getInvoiceType()) {
                    defaultModel = item;
                    break;
                } else if (item.isDefault() && item.getPrintTemplate() == model.getInvoiceType())
                    defaultModel = item;

            }
            if (defaultModel != null) {
                model.setInvoiceFormId(defaultModel.getId());
                model.setFormNo(defaultModel.getFormNo());
                model.setSign(defaultModel.getSign());
                //setInvoiceForm(defaultModel);
            }
        }
    }

    private void printBill(boolean old) {
        /*if (!new PrintWorker(this).printItem(refuelData, printMode, INVOICE_TYPE.BILL)) {
            refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        }
        */
        if (!printWorker.printBill(invoiceModel, old)) {
            setCurrentPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        }

//        setResult(RESULT_OK);
//        finish();

    }

    private void printInvoice(boolean old) {
        //boolean isOK = new PrintWorker(this).printItem(refuelData, printMode, INVOICE_TYPE.INVOICE);

        boolean isOK = printWorker.printInvoice(invoiceModel, old);

        if (!isOK) {
            setCurrentPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        } else {

            //showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        }

//        setResult(RESULT_OK);
//        finish();

    }

    private void checkAll(boolean checked) {
        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        if (checked)
            ((TruckArrayAdapter) lv.getAdapter()).selectAll();
        else
            ((TruckArrayAdapter) lv.getAdapter()).selectNone();
    }

    private void updateFieldData(int id, String text) {
        Logger.appendLog("PRW", m_Title);
        String oldValue = "";
        switch (id) {
            case R.id.refuel_preview_charter_name:
                oldValue = refuelData.getInvoiceNameCharter();
                refuelData.setInvoiceNameCharter(text);
                break;
        }
        Logger.appendLog("PRW", oldValue + "-->" + text);
    }

    private int REVIEW_WINDOW = 8899;
    private void showReview(){
        Intent intent = new Intent(this, ReviewActivity.class);
        intent.putExtra("FLIGHT_ID", refuelData.getFlightId());
        intent.putExtra("FLIGHT_UUID", refuelData.getFlightUniqueId());
        startActivityForResult(intent, REVIEW_WINDOW);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);

        int id = v.getId();
        isSplit = false;
        switch (id) {
            case R.id.refuel_preview_review :
                showReview();
                break;
            case R.id.refuel_preview_Addbm_2505 :
                openNew();
                break;
            case R.id.refuel_preview_split:
                showSplit();
                break;
            case R.id.truck_item_select_all:
                checkAll(((CheckBox) v).isChecked());
                break;
            case R.id.refuel_preview_print_refuel:
                if (refuelData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                    doRefuel();
                }
                break;

            case R.id.refuel_preview_print_receipt:
                printMode = PRINT_MODE.ONE_ITEM;
                openReceipt();
                break;
            case R.id.refuel_preview_print_return:
                printMode = PRINT_MODE.ALL_ITEM;
                openReceipt(true);
                break;
            case R.id.refuel_preview_print_all:
                printMode = PRINT_MODE.ALL_ITEM;
                openPrintInvoice();
                //preview();
                break;
            case R.id.refuel_preview_new_item:
                createNewItem();
                break;
            case R.id.btnUpdate:
                //save();
                break;
            case R.id.refuel_preview_charter_name:
                showConfirmMessage(R.string.change_charter_confirm, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        m_Title = getString(R.string.update_charter_name);
                        showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                        return null;
                    }
                });

                break;
            case R.id.refuel_preview_aircraftCode:
                m_Title = getString(R.string.update_aircraftCode);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_aircraftType:
                m_Title = getString(R.string.update_aircraftType);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_realAmount:

                m_Title = getString(R.string.update_real_amount);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_weight:
                if (refuelData.getDensity() <= 0)
                    showErrorMessage(R.string.density_must_input);
                else {
                    m_Title = getString(R.string.update_real_amount_kg);
                    showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                }
                break;
            case R.id.refuel_preview_density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_Temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_qc_no:
                m_Title = getString(R.string.update_qc_no);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_parking:
                m_Title = getString(R.string.update_parking_lot);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_vat:
                openVatSpinner();
                break;
//            case R.id.refuel_preview_product:
//                openProductSpinner();
//                break;
            case R.id.refuel_preview_airline:
                showConfirmMessage(R.string.change_airline_confirm, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        openAirlineDialog();
                        return null;
                    }
                });

                break;
            case R.id.refuel_preview_return:
                if (refuelData.getDensity() <= 0)
                    showErrorMessage(R.string.density_must_input);
                else {
                    //m_Title = getString(R.string.update_return_amount);
                    //showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    showReturnInput(refuelData.getReturnAmount(), refuelData.getReturnUnit());
                }
                break;
            case R.id.refuel_preview_weight_note:
                m_Title = getString(R.string.update_weight_note);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                break;
            case R.id.refuel_preview_routeName:
                m_Title = getString(R.string.update_route_name);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuel_preview_return_invoice_number:
                m_Title = getString(R.string.update_return_invoice_number);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                break;
            case R.id.refuel_preview_price:
                m_Title = getString(R.string.update_price);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_international:

                updatePrice();

                break;
            case R.id.refuel_preview_driver:
            case R.id.refuel_preview_operator:
                showSelectUser();
                break;
            case R.id.refuel_preview_starttime:
            case R.id.refuel_preview_endtime:
                showTimeDialog(id);
                break;

            case R.id.preview_form_no:
            case R.id.preview_sign:
                showFormNoSpinner();
                break;

            case R.id.preview_invoice_number:
                m_Title = getString(R.string.invoice_number);
                isSplit = true;
                showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                break;
            case R.id.btnOpen7501:
                openBM7501();
                break;

            case R.id.btnBack:
                exit();
                break;

            case R.id.refuel_preview_refresh:
                loadData();
                break;
            case R.id.refuel_preview_check_form:
                openCheckForm();

                break;
            case R.id.refuel_preview_start_meter:
                m_Title = getString(R.string.update_start_meter);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                break;
            case R.id.refuel_preview_end_meter:
                m_Title = getString(R.string.update_end_meter);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);

                break;

            case R.id.lblLeaveTime:
                // Sửa lại mốc giờ đã ghi. Cùng ranh giới quyền ghi như nút Rời đi.
                if (!isCurrentTruckItem(refuelData)) {
                    Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
                    break;
                }
                showApproachLeaveDialog();
                break;

            case R.id.btnLeave:
                // LeaveTime thuộc chính mẻ của xe thực hiện. Vẫn cho xe hiện tại ghi sau khi
                // đã phát hành chứng từ, nhưng tuyệt đối không patch mẻ replica của xe khác.
                if (!isCurrentTruckItem(refuelData)) {
                    Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
                    break;
                }
                Button btnLeave = (Button) v;
                // Chỉ khoá nút trong lúc chờ; ẩn nút và hiện nhãn sau khi biết đã lưu được.
                btnLeave.setEnabled(false);

                // Chỉ patch LeaveTime trên bản ghi mới nhất trong Room. Trước đây chỗ này
                // gọi updateBinding() -> postRefuels(allItems), tức POST lại toàn bộ snapshot
                // đang giữ trên màn hình và ghi đè số liệu đồng hồ vừa chốt.
                saveLeaveTime(new Date(), btnLeave);
                break;
        }

    }

    private void exit() {
        DataHelper.unlockSync();
        // Chứng từ là kết quả cuối cùng của mẻ tra nạp: đẩy ngay, không đợi lượt sync định kỳ.
        DataHelper.pushPendingInBackground();
        finish();
    }


    boolean isSplit = false;
    public Date selectedDate = new Date();

    private void openNew() {
        // Ngữ cảnh của phiếu; master data (sân bay, nhân viên, chuyến bay, loại bồn)
        // do B2505NewItemFragement tự tải nên không còn phụ thuộc list bất đồng bộ của activity.
        Bundle args = new Bundle();
        args.putInt(BM2505Factory.ARG_TRUCK_ID, FMSApplication.getApplication().getTruckId());
        args.putInt(BM2505Factory.ARG_AIRPORT_ID, BM2505Factory.getAccountAirportId());
        args.putInt(BM2505Factory.ARG_FLIGHT_ID, refuelData.getFlightId());
        args.putString(BM2505Factory.ARG_FLIGHT_CODE, refuelData.getFlightCode());
        args.putString(BM2505Factory.ARG_AIRCRAFT_CODE, refuelData.getAircraftCode());

        FragmentManager fm = getSupportFragmentManager();
        B2505NewItemFragement.newInstance(args).show(fm, "fragment_edit_name");
    }

    @Override
    public void onBM2505Saved(BM2505Model model) {
        // Màn hình xem trước không hiển thị danh sách BM2505 nên không cần tải lại dữ liệu.
        // Thông báo kết quả lưu đã do fragment đảm nhiệm.
    }
    private void showSplit() {
        if (blockEditIfLocked()) return;
        isSplit = true;
        m_Title = getString(R.string.input_split_amount);
        m_Text = "0";
        showEditDialog(R.id.refuel_preview_split, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    RefuelBm2508Binding checkFormBinding;
    B2505NewBinding b2505binding;
    boolean isNew = false;

    private void openCheckForm() {
        if (blockEditIfLocked()) return;

        Dialog checkFormDlg = new BaseDialog(this);

        if (refuelData.getBM2508Result() == null) {
            refuelData.setBM2508Result(15);
            isNew = true;
        }
        checkFormBinding = DataBindingUtil.inflate(checkFormDlg.getLayoutInflater(), R.layout.refuel_bm_2508, null, false);
        checkFormBinding.setMItem(refuelData);
        checkFormDlg.setContentView(checkFormBinding.getRoot());
        checkFormDlg.setCanceledOnTouchOutside(false);
        checkFormDlg.setCancelable(false);

        checkFormDlg.findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isNew)
                    refuelData.setBM2508Result(null);
                checkFormDlg.dismiss();
            }
        });
        checkFormDlg.findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConfirmMessage(R.string.check_form_delete, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        refuelData.setBM2508Result(null);
                        updateBinding();
                        checkFormDlg.dismiss();
                        return null;
                    }
                });

            }
        });
        checkFormDlg.findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                refuelData.setBM2508BondingCable(((CheckBox) checkFormDlg.findViewById(R.id.chk_bonding_cable)).isChecked());
                refuelData.setBM2508FuelingCap(((CheckBox) checkFormDlg.findViewById(R.id.chk_fueling_cap)).isChecked());
                refuelData.setBM2508FuelingHose(((CheckBox) checkFormDlg.findViewById(R.id.chk_fueling_hose)).isChecked());
                refuelData.setBM2508Ladder(((CheckBox) checkFormDlg.findViewById(R.id.chk_ladder)).isChecked());

                updateBinding();

                checkFormDlg.dismiss();
            }
        });


        checkFormDlg.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        checkFormDlg.show();
    }

    private void showFormNoSpinner() {
        //Spinner spn = printDialog.findViewById(R.id.preview_spinner_form_no);
        ArrayAdapter<InvoiceFormModel> adapter;
        if (invoiceModel.getInvoiceType() == INVOICE_TYPE.INVOICE)
            adapter = new ArrayAdapter<InvoiceFormModel>(this, android.R.layout.simple_list_item_single_choice, Arrays.stream(invoiceForms).filter(x -> x.getPrintTemplate() == INVOICE_TYPE.INVOICE).collect(Collectors.toList()));
        else
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, Arrays.stream(invoiceForms).filter(x -> x.getPrintTemplate() == INVOICE_TYPE.BILL).collect(Collectors.toList()));
        //adapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);
        int position = defaultModel != null ? adapter.getPosition(defaultModel) : -1;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setSingleChoiceItems(adapter, position,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ListView lw = ((AlertDialog) dialog).getListView();
                        InvoiceFormModel checkedItem = adapter.getItem(which);
                        invoiceModel.setInvoiceFormId(checkedItem.getId());
                        invoiceModel.setFormNo(checkedItem.getFormNo());
                        invoiceModel.setSign(checkedItem.getSign());
                        previewBinding.invalidateAll();
                        //setInvoiceForm(checkedItem);
                        defaultModel = checkedItem;
                        saveDefaultForm(checkedItem);
                        dialog.dismiss();
                    }
                });
        b.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });
        Dialog d = b.create();
        d.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        d.show();
    }

    private void saveDefaultForm(InvoiceFormModel checkedItem) {
        SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if (checkedItem.getPrintTemplate() == INVOICE_TYPE.BILL)
            editor.putInt("DEFAULT_FORM_BILL", checkedItem.getId());
        else
            editor.putInt("DEFAULT_FORM_INVOICE", checkedItem.getId());
        editor.commit();
    }

    private void setInvoiceForm(InvoiceFormModel checkedItem) {
        if (printItems != null && printItems.size() > 0) {
            for (RefuelItemData item : printItems) {
                if (!isCurrentTruckItem(item)) continue;
                item.setInvoiceFormId(checkedItem.getId());
                item.setFormNo(checkedItem.getFormNo());
                item.setSign(checkedItem.getSign());
            }


        }
    }


    private void updatePrice() {
        if (blockEditIfLocked()) return;

        if (refuelData.getAirlineModel() != null) {
            if (refuelData.getAirlineModel().isInternational() && refuelData.isInternational()) {
                setAll(R.id.refuel_preview_price, refuelData.getAirlineModel().getPrice());
            } else
                setAll(R.id.refuel_preview_price, refuelData.getAirlineModel().getPrice01());

            setAll(R.id.refuel_preview_vat, refuelData.getAirlineModel().isInternational() && !refuelData.isInternational() ? 0.1 : 0);
            setAll(R.id.refuel_preview_international, refuelData.isInternational() );

            refuelData.setPrintTemplate(!refuelData.getAirlineModel().isInternational() && !refuelData.isInternational() ? INVOICE_TYPE.BILL : INVOICE_TYPE.INVOICE);
        }
        updateBinding();
    }
    private void updateProduct() {
        if (!isCurrentTruckItem(refuelData)) return;
        if (refuelData.getProductId() > 0 && productList != null) {
            for (ProductModel product : productList) {
                if (product.getId() == refuelData.getProductId()) {
                    refuelData.setPCode(product.getCode());
                    refuelData.setPName(product.getName());
                    refuelData.setProductName(product.getName());
                    break;
                }
            }
        }

        // Cập nhật giao diện
        if (binding != null)
            binding.invalidateAll();
    }


    private void doRefuel() {
        // Màn hình chi tiết có thể thay đổi số đồng hồ/thời gian tra nạp. Replica của xe
        // khác chỉ được tải về để ghép chứng từ, nên không được chuyển sang luồng sửa này.
        if (blockEditIfLocked()) return;
        if (refuelData != null) {
            Intent intent = new Intent(this, RefuelDetailActivity.class);
            com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, refuelData);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    /**
     * Mẻ đã xuất hoá đơn hoặc đã kết xuất dữ liệu thì khoá sửa trên máy tính bảng, vì server
     * đóng băng nhóm sản lượng của những mẻ này (chỉ còn nhận số đồng hồ): nếu vẫn cho sửa thì
     * người dùng tưởng đã sửa xong trong khi hệ thống không nhận, và bản ghi trên server còn
     * thành mâu thuẫn giữa chỉ số đồng hồ với sản lượng.
     *
     * <p>Chỉ xét {@code invoiceNumber} và {@code exported}. Ba dấu hiệu còn lại đều KHÔNG phải
     * bằng chứng đã xuất chứng từ:
     * <ul>
     *   <li>{@code printed} bật ngay khi máy in báo xong, trước lúc nhập số chứng từ — in thử,
     *       in hỏng hay huỷ hộp thoại đều để lại cờ này;</li>
     *   <li>{@code receiptNumber} do server cấp sẵn cho mẻ, có từ khi mẻ còn chưa tra nạp xong
     *       (đối chiếu dữ liệu máy DEMO-03 ngày 11/08: mẻ Status=0 đã mang số 26194DV);</li>
     *   <li>{@code receiptCount} về 0 sau khi đồng bộ nên không phản ánh được số lần đã in.</li>
     * </ul>
     */
    private boolean isDocumentIssued() {
        if (refuelData == null) return false;
        return !isNullOrEmpty(refuelData.getInvoiceNumber())
                || refuelData.isExported();
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Xe khác luôn read-only, kể cả bản FHS/debug. */
    private boolean isCurrentTruckItem(RefuelItemData item) {
        if (item == null || currentApp == null) return false;

        String ownNo = currentApp.getTruckNo() == null ? "" : currentApp.getTruckNo().trim();
        String itemNo = item.getTruckNo() == null ? "" : item.getTruckNo().trim();
        Boolean numberMatches = !ownNo.isEmpty() && !itemNo.isEmpty()
                ? ownNo.equalsIgnoreCase(itemNo) : null;

        int ownId = currentApp.getTruckId();
        Boolean idMatches = ownId > 0 && item.getTruckId() > 0
                ? ownId == item.getTruckId() : null;

        if (numberMatches != null && idMatches != null
                && !numberMatches.equals(idMatches)) return false;
        Boolean match = numberMatches != null ? numberMatches : idMatches;
        return match != null && match;
    }

    /** Mẻ của xe hiện tại là nguồn metadata/identity của chứng từ. */
    private RefuelItemData currentTruckPrintTarget() {
        if (printItems == null) return null;
        RefuelItemData fallback = null;
        for (RefuelItemData item : printItems) {
            if (!isCurrentTruckItem(item)) continue;
            if (uniqueId != null && uniqueId.equals(item.getUniqueId())) return item;
            if (fallback == null) fallback = item;
        }
        return fallback;
    }

    /**
     * Lọc ngay tại biên UI trước khi gọi tầng ghi. DataHelper vẫn kiểm tra lại ownership,
     * nhưng Activity cũng không nên chuyển replica xe khác vào một API có khả năng ghi.
     */
    private ArrayList<RefuelItemData> writableCurrentItems(List<RefuelItemData> source) {
        ArrayList<RefuelItemData> result = new ArrayList<>();
        if (source == null) return result;
        for (RefuelItemData item : source) {
            if (isCurrentTruckItem(item)) result.add(item);
        }
        return result;
    }

    private void setCurrentPrintStatus(RefuelItemData.ITEM_PRINT_STATUS status) {
        RefuelItemData target = documentHeader();
        if (target == null) target = refuelData;
        if (target != null) target.setPrintStatus(status);
    }

    /** Giữ nguyên tập dòng in nhưng đặt nguồn header của xe hiện tại ở vị trí đầu. */
    private ArrayList<RefuelItemData> documentItemsCurrentFirst() {
        ArrayList<RefuelItemData> result = printItems == null
                ? new ArrayList<>() : new ArrayList<>(printItems);
        RefuelItemData source = currentTruckPrintTarget();
        if (source != null) {
            result.remove(source);
            result.add(0, source);
        }
        return result;
    }

    private boolean hasCurrentTruckPrintTarget() {
        return currentTruckPrintTarget() != null;
    }

    /**
     * Nguồn header của chứng từ: mẻ của xe này nếu có, nếu không thì mẻ đầu tiên được chọn.
     *
     * <p>Fallback chính là chỗ cho phép IN HỘ: máy in của xe kia hỏng, xe này in giúp phiếu
     * cho mẻ của họ. Khi đó header (hãng bay, giá, tài xế) phải lấy từ chính mẻ được in, chứ
     * không có mẻ nào của xe này để mà lấy.
     */
    private RefuelItemData documentHeader() {
        RefuelItemData own = currentTruckPrintTarget();
        if (own != null) return own;
        if (printItems == null) return null;
        for (RefuelItemData item : printItems)
            if (item != null) return item;
        return null;
    }

    /**
     * In hộ xe khác: CẢNH BÁO một lần, không chặn.
     *
     * <p>Trước đây đây là chặn cứng "phải chọn ít nhất một mẻ của xe hiện tại". Nghiệp vụ có
     * thật là máy in của một xe hỏng và xe bên cạnh in hộ; chặn ở đây nghĩa là chuyến đó
     * không có phiếu. Số phiếu vẫn theo đúng luật cũ: giữ số mà mẻ đang mang, chỉ sinh số mới
     * khi mẻ chưa có số hoặc số đó đã tồn tại trong máy này.
     *
     * @return luôn false — không có lối chặn nào ở đây nữa.
     */
    private boolean blockDocumentWithoutCurrentTruck() {
        if (printItems == null || printItems.isEmpty() || hasCurrentTruckPrintTarget())
            return false;

        RefuelItemData header = documentHeader();
        Logger.appendLog(LOG_TAG, "IN HỘ: danh sách chỉ có mẻ xe khác, truck="
                + (header == null ? "null" : header.getTruckNo())
                + " uid=" + (header == null ? "null" : header.getUniqueId()));
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=DOCUMENT_PRINTED_FOR_OTHER_TRUCK ownTruck=%s headerTruck=%s uid=%s items=%d",
                currentApp == null ? "null" : currentApp.getTruckNo(),
                header == null ? "null" : header.getTruckNo(),
                header == null ? "null" : header.getUniqueId(),
                printItems.size()));

        Toast.makeText(this, R.string.warn_print_for_other_truck, Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * @return true nếu thao tác sửa bị chặn (đã hiện thông báo tương ứng cho người dùng).
     */
    private boolean blockEditIfLocked() {
        if (isDocumentIssued()) {
            showErrorMessage(R.string.edit_locked_after_print);
            return true;
        }
        if (!isEditable) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    private List<UserModel> userList = null;

    private void showSelectUser() {

        if (isDocumentIssued()) {
            showErrorMessage(R.string.edit_locked_after_print);
            return;
        }
        if (!isEditable) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }


        if (userList != null) {
            Dialog dialog = new Dialog(this);
            SelectUserBinding binding = DataBindingUtil.inflate(dialog.getLayoutInflater(), R.layout.select_user, null, false);
            binding.setRefuelItem(refuelData);
            dialog.setContentView(binding.getRoot());
            Spinner spn = dialog.findViewById(R.id.select_user_driver);

            ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, userList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(refuelData.getDriverId(), userList));

            spn = dialog.findViewById(R.id.select_user_operator);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(refuelData.getOperatorId(), userList));

            dialog.show();

            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

                    if (driver.getId() == operator.getId()) {
                        new AlertDialog.Builder(dialog.getContext())
                                .setTitle(R.string.select_user)
                                .setMessage(R.string.error_same_user)
                                .setIcon(R.drawable.ic_error)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                })
                                .show();
                        return;
                    }

                    Logger.appendLog(LOG_TAG, "Cập nhật nhân viên");
                    Logger.appendLog(LOG_TAG, "Old value: "
                            + refuelData.getDriverName() + " / "
                            + refuelData.getOperatorName());
                    Logger.appendLog(LOG_TAG, "New value: "
                            + (driver == null ? "" : driver.getName()) + " / "
                            + (operator == null ? "" : operator.getName()));

                    if (driver != null) {
                        refuelData.setDriverId(driver.getId());
                        refuelData.setDriverName(driver.getName());
                    }

                    if (operator != null) {
                        refuelData.setOperatorId(operator.getId());
                        refuelData.setOperatorName(operator.getName());
                    }

                    updateBinding(false);
                    dialog.dismiss();
                }
            });

            dialog.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
    }

    private int findUser(int userId, List<UserModel> userList) {
        int pos = 0;
        for (UserModel item : userList) {
            if (item.getId() == userId)
                return pos;
            pos++;
        }
        return -1;
    }

    private final String LOG_TAG = "PRW";

    /**
     * Sửa lại GIỜ TIẾP CẬN và GIỜ RỜI ĐI của mẻ này.
     *
     * <p>Vì sao cần: nút "Rời đi" ghi mốc giờ ngay khi bấm, và trước đây không có đường nào
     * sửa lại. Bấm nhầm — hoặc bấm muộn vì còn dở tay ở tàu bay — là hỏng mốc giờ của mẻ
     * vĩnh viễn. Đây là hai mốc thời gian tác nghiệp, không phải số liệu chốt của mẻ.
     *
     * <p>KHÔNG khoá theo "đã xuất hoá đơn" như giờ tra nạp: chính nút Rời đi cũng cho ghi sau
     * khi đã phát hành chứng từ (xem nhánh {@code R.id.btnLeave}), nên khoá ở đây là không
     * nhất quán. Ranh giới duy nhất vẫn là quyền ghi theo xe — mẻ của xe khác chỉ đọc.
     *
     * <p>Ghi bằng {@code patchRefuel}: nó đọc bản mới nhất dưới khoá rồi chỉ đắp hai trường
     * này, nên không kéo theo snapshot cũ của màn hình. Hai trường này KHÔNG nằm trong
     * {@code Scope.PREVIEW}, nên tuyệt đối không được lưu qua đường {@code updateBinding()} —
     * patch theo scope sẽ bỏ im lặng cả hai.
     */
    private void showApproachLeaveDialog() {
        if (refuelData == null) return;

        final Date[] approach = {refuelData.getApproachTime()};
        final Date[] leave = {refuelData.getLeaveTime()};

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final TextView approachView = addTimeRow(layout, getString(R.string.approach_time));
        final TextView leaveView = addTimeRow(layout, getString(R.string.leave_time));
        renderTimeRow(approachView, approach[0]);
        renderTimeRow(leaveView, leave[0]);

        approachView.setOnClickListener(v -> pickDateTime(approach[0], picked -> {
            approach[0] = picked;
            renderTimeRow(approachView, picked);
        }));
        leaveView.setOnClickListener(v -> pickDateTime(leave[0], picked -> {
            leave[0] = picked;
            renderTimeRow(leaveView, picked);
        }));

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_approach_leave_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        confirmThenSaveApproachLeave(approach[0], leave[0]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Một dòng "nhãn — giá trị bấm được" trong hộp thoại sửa giờ. */
    private TextView addTimeRow(LinearLayout parent, String label) {
        TextView title = new TextView(this);
        title.setText(label);
        parent.addView(title);

        TextView value = new TextView(this);
        value.setTextSize(18);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        value.setPadding(0, pad, 0, pad * 2);
        value.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_edit, 0, 0, 0);
        parent.addView(value);
        return value;
    }

    private void renderTimeRow(TextView view, Date value) {
        view.setText(value == null
                ? getString(R.string.not_recorded_touch_to_set)
                : DateUtils.formatDate(value, "dd/MM/yyyy HH:mm"));
    }

    /** Chọn ngày rồi chọn giờ, trả về mốc đã chọn. {@code initial} null thì lấy giờ hiện tại. */
    private void pickDateTime(Date initial, androidx.core.util.Consumer<Date> onPicked) {
        final Calendar c = Calendar.getInstance();
        if (initial != null) c.setTime(initial);

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            c.set(year, month, dayOfMonth);
            new TimePickerDialog(context, (timeView, hourOfDay, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                onPicked.accept(c.getTime());
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Rời đi sớm hơn tiếp cận là vô lý — nhưng CHỈ CẢNH BÁO, không chặn.
     *
     * <p>Người dùng đang đứng ngoài sân, và một cặp giờ ngược vẫn tốt hơn là không sửa được
     * gì. Cho họ nhận sai một cách có ý thức, và ghi anomaly để đối soát sau ca.
     */
    private void confirmThenSaveApproachLeave(Date approach, Date leave) {
        boolean reversed = approach != null && leave != null && leave.before(approach);
        if (!reversed) {
            saveApproachLeave(approach, leave);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.warn_leave_before_approach)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=APPROACH_LEAVE_REVERSED_ACCEPTED uid=%s approach=%s leave=%s",
                            refuelData == null ? "null" : refuelData.getUniqueId(),
                            DateUtils.formatDate(approach, "dd/MM HH:mm"),
                            DateUtils.formatDate(leave, "dd/MM HH:mm")));
                    saveApproachLeave(approach, leave);
                })
                .setNegativeButton(R.string.back, null)
                .show();
    }

    /** Ghi hai mốc giờ tác nghiệp, không đụng business payload của phiếu. */
    private void saveApproachLeave(final Date approach, final Date leave) {
        final String uniqueId = refuelData != null ? refuelData.getUniqueId() : null;
        if (uniqueId == null || uniqueId.isEmpty()) return;

        final Date oldApproach = refuelData.getApproachTime();
        final Date oldLeave = refuelData.getLeaveTime();

        new Thread(() -> {
            final DataHelper.PatchResult result = DataHelper.patchRefuel(uniqueId, latest -> {
                latest.setApproachTime(approach);
                latest.setLeaveTime(leave);
            });

            Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                    "Sửa tay mốc giờ uid=%s: tiếp cận %s -> %s, rời đi %s -> %s, applied=%s%s",
                    uniqueId,
                    DateUtils.formatDate(oldApproach, "dd/MM HH:mm"),
                    DateUtils.formatDate(approach, "dd/MM HH:mm"),
                    DateUtils.formatDate(oldLeave, "dd/MM HH:mm"),
                    DateUtils.formatDate(leave, "dd/MM HH:mm"),
                    result.applied, result.applied ? "" : " reason=" + result.reason));

            // Cùng lý do như saveLeaveTime(): màn hình này giữ khoá sync nên lượt
            // Synchronize() bên trong patchRefuel bị nuốt tới lúc rời màn hình.
            if (result.applied) DataHelper.pushPendingInBackground();

            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!result.applied) {
                    showErrorMessage(R.string.save_leave_time_failed);
                    return;
                }
                if (result.data != null) refuelData = result.data;
                if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
                    binding.setMItem(refuelData);
                    binding.invalidateAll();
                    truckArrayAdapter.notifyDataSetChanged();
                } else {
                    extractBinding.setMItem(refuelData);
                    extractBinding.invalidateAll();
                }
            });
        }).start();
    }

    /**
     * Lưu giờ rời đi mà không đụng tới business payload của phiếu.
     * Giao diện chỉ đổi sau khi biết kết quả ghi, thất bại thì trả nút về trạng thái cũ.
     */
    private void saveLeaveTime(final Date leaveTime, final Button btnLeave) {
        final String uniqueId = refuelData != null ? refuelData.getUniqueId() : null;
        if (uniqueId == null || uniqueId.isEmpty()) {
            btnLeave.setEnabled(true);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final DataHelper.PatchResult result = DataHelper.patchRefuel(uniqueId,
                        latest -> latest.setLeaveTime(leaveTime));

                Logger.appendLog(LOG_TAG, "patch LeaveTime uid=" + uniqueId
                        + " applied=" + result.applied
                        + (result.applied ? "" : " reason=" + result.reason));

                // Bấm -> ghi local -> đồng bộ ngay. patchRefuel có gọi Synchronize(), nhưng
                // màn hình này đang giữ khoá sync nên lượt đó bị nuốt tới lúc rời màn hình.
                // Đẩy thẳng, không đi qua khoá.
                if (result.applied) DataHelper.pushPendingInBackground();

                runOnUiThread(() -> {
                    if (!result.applied) {
                        btnLeave.setEnabled(true);
                        showErrorMessage(R.string.save_leave_time_failed);
                        return;
                    }

                    if (result.data != null)
                        refuelData = result.data;

                    // Layout tự quyết định nút và nhãn theo mItem:
                    //   visibility = mItem.leaveTime == null ? VISIBLE : GONE
                    // nên phải ĐƯA BẢN MỚI vào binding. Trước đây chỗ này ẩn nút bằng tay
                    // rồi gọi invalidateAll(), mà mItem vẫn là object CŨ chưa có leaveTime —
                    // biểu thức tính lại ra VISIBLE và ghi đè lệnh ẩn, còn setEnabled(false)
                    // lúc bấm thì không ai gỡ. Kết quả: nút hiện lại và bị xám, nhìn như treo.
                    btnLeave.setEnabled(true);

                    if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
                        binding.setMItem(refuelData);
                        binding.invalidateAll();
                        truckArrayAdapter.notifyDataSetChanged();
                    } else {
                        extractBinding.setMItem(refuelData);
                        extractBinding.invalidateAll();
                    }
                });
            }
        }).start();
    }

    /**
     * Hàng đợi ghi của màn hình xem trước: MỘT luồng, đúng thứ tự người dùng thao tác.
     *
     * <p>Trước đây mỗi lần sửa spawn một {@code Thread} riêng, nên hai hộp thoại liên tiếp
     * chạy song song trên cùng baseline và lần sau tự chuốc CONFLICT.
     */
    private final java.util.concurrent.ExecutorService previewSaveExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void updateBinding() {
        updateBinding(true);
    }

    private void updateBinding(boolean updateAll) {
        if (!updateAll && !isCurrentTruckItem(refuelData)) {
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=PREVIEW_WRITE_REJECTED uid=%s reason=FOREIGN_OR_UNKNOWN",
                    refuelData == null ? "null" : refuelData.getUniqueId()));
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }
        final ArrayList<RefuelItemData> writableItems = updateAll
                ? writableCurrentItems(allItems) : null;
        // Đối tượng của màn hình có thể bị onDestroy đặt null trong lúc tác vụ còn chạy.
        final RefuelItemData target = refuelData;
        // Mỗi hộp thoại TRƯỚC ĐÂY spawn một Thread riêng gửi full snapshot. Sửa nhiệt độ rồi
        // tỉ trọng liên tiếp là hai luồng cùng đứng trên một baseline, luồng sau chắc chắn
        // bị CONFLICT. Xếp hàng một luồng thì lần sau luôn thấy ClientSeq của lần trước.
        if (previewSaveExecutor.isShutdown()) {
            Logger.appendLog(LOG_TAG, "Bỏ lần lưu vì hàng đợi xem trước đã đóng");
            return;
        }
        previewSaveExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Logger.appendLog(LOG_TAG, "post all refuels");
                boolean committed;
                if (updateAll) {
                    committed = DataHelper.postRefuels(writableItems, true);

                    // Cùng lý do như nhánh một phiếu bên dưới, và đây mới là nhánh hay gặp:
                    // số hiệu tàu bay, loại tàu bay, đường bay, bãi đỗ, tên charter, đơn giá
                    // đều đi qua setAll() nên luôn rơi vào đây. Đường in vừa chạy xong đã đắp
                    // metadata phiếu lên row và làm ClientSeq tiến lên, trong khi các đối
                    // tượng của màn hình vẫn giữ baseline từ trước lúc in — sửa ngay sau khi
                    // in là chắc chắn bị chặn.
                    if (!committed && writableItems != null) {
                        Logger.appendLog(LOG_TAG,
                                "Sửa hàng loạt bị chặn, thử lại bằng PreviewFieldsPatch");
                        boolean recovered = true;
                        for (RefuelItemData item : writableItems) {
                            warnFieldsDroppedByPatch(item);
                            if (!RefuelItemData.isCommitted(
                                    DataHelper.savePreviewFields(item)))
                                recovered = false;
                        }
                        committed = recovered;
                    }
                } else {
                    RefuelItemData result = DataHelper.postRefuel(target, true);
                    committed = RefuelItemData.isCommitted(result);

                    // Baseline dịch vì lượt pull nền hoặc một hộp thoại khác vừa ghi. Gõ lại
                    // là việc của máy, không phải của người dùng: đắp đúng nhóm trường màn
                    // hình này được phép nhập lên row mới nhất.
                    if (!committed && result != null
                            && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                        Logger.appendLog(LOG_TAG,
                                "Sửa phiếu bị chặn, thử lại bằng PreviewFieldsPatch");
                        warnFieldsDroppedByPatch(target);
                        committed = RefuelItemData.isCommitted(
                                DataHelper.savePreviewFields(target));
                    }
                }

                // Lưu bị chặn mà màn hình vẫn vẽ lại như cũ thì người dùng tin là đã sửa
                // xong, trong khi Room giữ nguyên giá trị cũ.
                if (!committed) {
                    Logger.appendLog(LOG_TAG, "Sửa phiếu chưa lưu được");
                    runOnUiThread(() -> {
                        if (!isFinishing())
                            showErrorMessage(R.string.error_refuel_save_failed);
                    });
                }
            }
        });
        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
            binding.invalidateAll();
            truckArrayAdapter.notifyDataSetChanged();
        } else
            extractBinding.invalidateAll();


    }

    private final boolean vatFirstClick = true;
    private final boolean airlineFirstClick = true;

    private PRINT_MODE printMode = PRINT_MODE.ALL_ITEM;

    private void openVatSpinner() {
        if (blockEditIfLocked()) return;
        String[] vat_array = getResources().getStringArray(R.array.vat_array);
        int pos = Arrays.asList(vat_array).indexOf(String.format("%.0f%%", refuelData.getTaxRate() * 100));
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.update_tax_rate);

        final String oldVat = String.format(java.util.Locale.US, "%.0f%%",
                refuelData.getTaxRate() * 100);
        b.setSingleChoiceItems(R.array.vat_array, pos, (dialog, which) -> {
            NumberFormat format = NumberFormat.getPercentInstance();
            try {
                Logger.appendLog(LOG_TAG, "Cập nhật thuế suất");
                Logger.appendLog(LOG_TAG, "Old value: " + oldVat);
                Logger.appendLog(LOG_TAG, "New value: " + vat_array[which]);
                setAll(R.id.refuel_preview_vat, format.parse(vat_array[which]).doubleValue());
                dialog.dismiss();
                updateBinding();
            } catch (ParseException e) {

            }

        });
        b.create().show();

    }

    private void openProductSpinner() {
        if (blockEditIfLocked()) return;

        if (productList == null || productList.isEmpty()) {
            Toast.makeText(this, R.string.no_product_found, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> productNames = new ArrayList<>();
        for (ProductModel p : productList) {
            productNames.add(p.getCode()); // Đảm bảo getName() không null
        }

        String currentProductName = refuelData.getProductName();
        int currentIndex = productNames.indexOf(currentProductName);

        new AlertDialog.Builder(this)
                .setTitle(R.string.update_product)
                .setSingleChoiceItems(
                        productNames.toArray(new String[0]),
                        currentIndex,
                        (dialog, which) -> {
                            ProductModel selected = productList.get(which);
                            refuelData.setProductId(selected.getId());
                            refuelData.setPCode(selected.getCode());
                            refuelData.setPName(selected.getName());
                            refuelData.setProductName(selected.getCode());
                            updateBinding();
                            dialog.dismiss();
                        })
                .create()
                .show();
    }



    private void openAirlineDialog() {
        if (blockEditIfLocked()) return;
        Dialog airlineDlg = new Dialog(this);
        airlineDlg.setTitle(R.string.app_name);
        airlineDlg.setContentView(R.layout.airline_select_dialog);
        SearchView searchView = airlineDlg.findViewById(R.id.airline_dlg_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(query);
                adapter.notifyDataSetChanged();
                //lvAirline.setAdapter(adapter);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(newText);
                adapter.notifyDataSetChanged();

                return false;
            }
        });
        ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
        lvAirline.setAdapter(new AirlineArrayAdapter(this, airlines));
        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                AirlineModel airline = (AirlineModel) parent.getItemAtPosition(position);
                refuelData.setInvoiceNameCharter(null);
                setAirline(airline);


                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter("");
                airlineDlg.dismiss();
//                for (int j = 0; j < parent.getChildCount(); j++) {
//                    if (j==position)
//                    {
//                        parent.getChildAt(j).setBackgroundColor( Color.LTGRAY);
//                        ((CheckedTextView) parent.getChildAt(j).findViewById(R.id.airline_item_check)).setChecked(true);
//                    }
//                    else {
//                        parent.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);
//                        ((CheckedTextView) parent.getChildAt(j).findViewById(R.id.airline_item_check)).setChecked(j == position);
//                    }
//                }

                // change the background color of the selected element
                //view.setBackgroundColor(Color.LTGRAY);
                //((CheckedTextView) view.findViewById(R.id.airline_item_check)).setChecked(true);

            }

        });
        airlineDlg.show();
    }

    private void setAirline(RefuelItemData item, AirlineModel selected) {
        setAirline(item, selected, false);
    }

    private void setAirline(RefuelItemData item, AirlineModel selected, boolean updatePrice) {

        item.setAirlineId(selected.getId());
        //item.setPrice(selected.getPrice());
        /*if (item.isInternational() && item.isInternational())
            item.setPrice(selected.getPrice());
        else
            item.setPrice(selected.getPrice01());*/
        item.setCurrency(selected.getCurrency());
        item.setUnit(selected.getUnit());
        item.setProductName(selected.getProductName());
        //if (selected.getId() != item.getAirlineId())
        item.setTaxRate(!item.isInternational() && selected.isInternational()
                ? BuildConfig.TAX_RATE : 0);

        item.setAirlineModel(selected);

        //((TextView) findViewById(R.id.refuel_preview_airline)).setText(selected.getName());

        if (item.getInvoiceNameCharter() == null || item.getInvoiceNameCharter().isEmpty() || updatePrice)
            item.setInvoiceNameCharter(selected.getName());

        if (updatePrice) {
            if (item.isInternational())
                item.setPrice(selected.getPrice());
            else
                item.setPrice(selected.getPrice01());

            if (!item.isInternational() && selected.isInternational())
                item.setTaxRate(BuildConfig.TAX_RATE);
            else
                item.setTaxRate(0);
        }
    }

    private void setAirline(AirlineModel selected) {
        Logger.appendLog(LOG_TAG, "Cập nhật hãng bay");
        Logger.appendLog(LOG_TAG, "Old value: "
                + (refuelData == null || refuelData.getAirlineModel() == null
                ? "" : refuelData.getAirlineModel().getCode()));
        Logger.appendLog(LOG_TAG, "New value: "
                + (selected == null ? "" : selected.getCode()));

        for (RefuelItemData itemData : allItems) {
            if (isCurrentTruckItem(itemData))
                setAirline(itemData, selected, true);
        }


        updateBinding();
    }

    private void setAll(int id, String text) {
        setAll(allItems, id, text);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, String text) {
        for (RefuelItemData item : items) {
            if (!isCurrentTruckItem(item)) continue;
            switch (id) {
                case R.id.refuel_preview_aircraftCode:
                    item.setAircraftCode(text);
                    break;
                case R.id.refuel_preview_aircraftType:
                    item.setAircraftType(text);
                    break;
                case R.id.refuel_preview_parking:
                    item.setParkingLot(text);
                    break;
                case R.id.refuel_preview_charter_name:
                    item.setInvoiceNameCharter(text);
                    break;

                case R.id.refuel_preview_routeName:
                    item.setRouteName(text);
                    break;
//                case R.id.refuel_preview_weight_note:
//                    item.setWeightNote(text);
//                    break;

            }
        }
    }

    private void setAll(int id, double val) {
        setAll(allItems, id, val);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, double val) {
        for (RefuelItemData item : items) {
            if (!isCurrentTruckItem(item)) continue;
            switch (id) {
                case R.id.refuel_preview_price:
                    item.setPrice(val);
                    //item.setChangeFlag(RefuelItemData.CHANGE_FLAG.PRICE);
                    break;
                case R.id.refuel_preview_vat:
                    item.setTaxRate(val);
                    break;
            }
        }

    }

    private void setAll(int id, boolean val) {
        setAll(allItems, id, val);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, boolean val) {
        for (RefuelItemData item : items) {
            if (!isCurrentTruckItem(item)) continue;
            switch (id) {
                case R.id.refuel_preview_international:
                    item.setInternational(val);
                    break;


            }
        }
    }

    private String m_Text = "";
    private String m_Title = "";

    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }

    private void showEditDialog(final int id, int inputType, String pattern) {
        showEditDialog(id, inputType, pattern, false);
    }

    private void showEditDialog(final int id, int inputType, boolean required) {
        showEditDialog(id, inputType, ".*", required);
    }

    private void showEditDialog(final int id, int inputType, String pattern, boolean required) {

        // Số hoá đơn được nhập sau callback in nên vẫn phải cho xe hiện tại ghi khi chứng từ
        // vừa phát hành. Với phiếu gộp, quyền nằm ở printItems chứ không phải dòng đang chọn.
        if (id == R.id.refuel_preview_invoice_number) {
            if (printItems == null || printItems.isEmpty()) {
                Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
                return;
            }
        } else if (blockEditIfLocked()) {
            return;
        }

        Context context = this;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m_Title);
        Logger.appendLog("PRW", m_Title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);
        String oldVal = ((TextView) findViewById(id)).getText().toString();
        input.setText(oldVal);
        Logger.appendLog("PRW", "Old value: " + oldVal);
        if (id == R.id.refuel_preview_routeName) {
            if (((TextView) findViewById(id)).getText().toString().isEmpty()) {
                SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
                String airport = preferences.getString("AIRPORT", "");
                if (airport != null && !airport.isEmpty())
                    input.setText(airport + "-");
            }


        } else if (id == R.id.refuel_preview_split)
            input.setText("0");

        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }


        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        if (!required) {
            builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        }
        final AlertDialog dialog = builder.create();// builder.show();
        dialog.setCancelable(!required);
        if (!isFinishing()) {
            dialog.show();
            input.requestFocus();
            if (id == R.id.refuel_preview_density)
                input.setSelection(2, input.getText().length());
            else
                input.setSelection(0, input.getText().length());

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (doUpdateResult())
                        dialog.dismiss();
                }

                private boolean doUpdateResult() {

                    m_Text = input.getText().toString().trim();
                    Logger.appendLog("PRW", "New value: " + m_Text);
                    if (required && m_Text.isEmpty()) {
                        showErrorMessage(R.string.empty_required_field);
                        return false;
                    }
                    Pattern regex = Pattern.compile(pattern);
                    Matcher matcher = regex.matcher(m_Text);
                    if (!matcher.find()) {
                        Toast.makeText(getBaseContext(), getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    try {
                        switch (id) {
                            case R.id.refuel_preview_aircraftCode:

                            case R.id.refuel_preview_charter_name:

                            case R.id.refuel_preview_aircraftType:
                                //case R.id.refuel_preview_weight_note:

                            case R.id.refuel_preview_routeName:

                            case R.id.refuel_preview_parking:
                                setAll(id, m_Text);
                                break;
                            case R.id.refuel_preview_weight_note:
                                refuelData.setWeightNote(m_Text);
                                break;
                            case R.id.refuel_preview_invoice_number:
                            case R.id.preview_invoice_number:
                                //refuelData.setInvoiceNumber(m_Text);
                                if (!updateAllInvoice(m_Text)) {
                                    showErrorMessage(R.string.duplicate_invoice_number);
                                    return false;
                                }
                                break;

                            case R.id.refuel_preview_density:
                                double d = numberFormat.parse(m_Text).doubleValue();
                                if (d < 0.72 || d > 0.86) {
                                    new AlertDialog.Builder(context)
                                            .setTitle(R.string.error_data)
                                            .setMessage(R.string.invalid_density)
                                            .setIcon(R.drawable.ic_error)
                                            .setPositiveButton("OK", (dialog1, which) -> {
                                                dialog1.dismiss();
                                            })
                                            .create()
                                            .show();
                                    return false;
                                }
                                refuelData.setDensity(d);
                                //((TextView)findViewById(R.id.refuel_preview_Density)).setText(String.format("%.2f",refuelData.getDensity()));
                                break;
                            case R.id.refuel_preview_Temperature:
                                double t = numberFormat.parse(m_Text).doubleValue();
                                refuelData.setManualTemperature(t);
                                //((TextView)findViewById(R.id.refuel_preview_Temperature)).setText(String.format("%.2f",refuelData.getManualTemperature()));

                                break;
                            case R.id.refuel_preview_realAmount:
                                double realAmount = numberFormat.parse(m_Text).doubleValue();
                                refuelData.setRealAmount(realAmount);
                                // Nhánh đồng hồ lít — không còn xe nào dùng; số đồng hồ trừ
                                // theo gallon. setRealAmount đã tự tính lại số lít.
                                // if (BuildConfig.FHS) {
                                //     double vol = refuelData.getRealAmount() * GALLON_TO_LITTER;
                                //     refuelData.setVolume(vol);
                                //     refuelData.setStartNumber(refuelData.getEndNumber() - vol);
                                // } else
                                refuelData.setStartNumber(refuelData.getEndNumber() - realAmount);
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.GROSS_QTY);


                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;

                            case R.id.refuel_preview_weight:
                                double weight = numberFormat.parse(m_Text).doubleValue();
                                double gallon = refuelData.getDensity() == 0 ? 0 : Math.round(Math.round(weight / refuelData.getDensity()) / GALLON_TO_LITTER);
                                refuelData.setRealAmount(gallon);
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.GROSS_QTY);
                                break;
                            case R.id.refuel_preview_qc_no:
                                refuelData.setQualityNo(m_Text);
                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;
                            case R.id.refuel_preview_price:
                                setAll(id, numberFormat.parse(m_Text).doubleValue());
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.PRICE);
                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;
                            case R.id.refuel_preview_return:
                                double returnAmount = numberFormat.parse(m_Text).doubleValue();
                                calculateReturnAmount(returnAmount);
                                break;

                            case R.id.refuel_preview_return_invoice_number:

                                refuelData.setReturnInvoiceNumber(m_Text);
                                break;

                            case R.id.refuel_preview_split:
                                double split = numberFormat.parse(m_Text).doubleValue();
                                if (split > refuelData.getWeight()) {
                                    showErrorMessage(R.string.split_amount_too_big);
                                    return false;
                                } else {

                                    showConfirmMessage(R.string.split_confirm, () -> {
                                        RefuelItemData splitItem = refuelData.split(split);
                                        allItems.add(splitItem);
                                        //truckArrayAdapter.add(splitItem);
                                        updateBinding();
                                        //loadData();
                                        return null;
                                    });

                                }
                                break;

                        }
                    } catch (ParseException ex) {
                        Toast.makeText(getBaseContext(), R.string.invalid_number_format, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    if (!isSplit)
                        updateBinding(false);

                    return true;
                }
            });
        }
    }

    private boolean calculateReturnAmount(double returnAmount) {
        return calculateReturnAmount(returnAmount, RETURN_UNIT.KG);
    }
    private void updateAllReview()
    {
        for (RefuelItemData item : allItems) {
            if (isCurrentTruckItem(item)) item.setHasReview(true);


        }
        final ArrayList<RefuelItemData> writableItems = writableCurrentItems(printItems);
        new Thread(() -> {
            if (!DataHelper.postRefuels(writableItems, false)) {
                Logger.appendLog(LOG_TAG, "Ghi nhận đánh giá chưa lưu được");
                runOnUiThread(() -> {
                    if (!isFinishing())
                        showErrorMessage(R.string.error_refuel_save_failed);
                });
            }
        }).start();
        binding.invalidateAll();
    }
    private boolean calculateReturnAmount(double returnAmount, RETURN_UNIT unit) {
        if (blockEditIfLocked()) return false;

        if (refuelData.getDensity() > 0) {
            double vol = unit == RETURN_UNIT.KG ? Math.round(returnAmount / refuelData.getDensity()) : Math.round(returnAmount * GALLON_TO_LITTER);
            double gal = unit == RETURN_UNIT.KG ? Math.round(vol / GALLON_TO_LITTER) : returnAmount;
            double newAmount = Math.round(Math.round(gal * GALLON_TO_LITTER) * refuelData.getDensity());
            if (gal > refuelData.getRealAmount()) {
                showWarningMessage(getString(R.string.return_amount_greater_warning));
                return false;
            } else if (unit == RETURN_UNIT.KG && newAmount != returnAmount) {
                showWarningMessage(getString(R.string.new_return_amount_value) + " " + newAmount + " KG");

            }

            refuelData.setReturnAmount(unit == RETURN_UNIT.KG ? newAmount : returnAmount);
            refuelData.setReturnUnit(unit);
            updateBinding(false);
        }
        return true;
    }
    private boolean updateAllReceipt(String receiptNumber)
    {
        return updateAllReceipt(UUID.randomUUID().toString(), receiptNumber, 0);
    }
    private boolean updateAllReceipt(String uniqueId, String receiptNumber, double techlog) {
        /*for (RefuelItemData item : printItems) {
            if (item.getReceiptNumber() != null && item.getReceiptNumber().equals(receiptNumber)) {
                return false;
            }
        }*/

        if (printItems == null || printItems.isEmpty()) {
            Logger.appendLog(LOG_TAG, "Không cập nhật phiếu: danh sách in rỗng");
            showErrorMessage(R.string.save_print_info_failed);
            return false;
        }

        // CHỈ ghi các trường của receipt lên bản ghi MỚI NHẤT trong Room.
        // Trước đây chỗ này POST lại toàn bộ snapshot đang giữ trên màn hình: nếu snapshot
        // đã cũ (ví dụ bản server kéo về lúc mở Preview) thì số đồng hồ vừa chốt bị ghi đè —
        // đúng sự cố 4940 bị kéo về 4922 trong log ngày 29/07.
        new Thread(() -> patchAllPrintItems("receipt", latest -> {
            latest.setReceiptNumber(receiptNumber);
            latest.setReceiptUniqueId(uniqueId);
            latest.setWeightNote(String.format("%.0f", techlog));
            latest.setReceiptCount(latest.getReceiptCount() + 1);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
        })).start();

        truckArrayAdapter.notifyDataSetChanged();
        binding.invalidateAll();
        return true;
    }

    /**
     * Patch cùng một nhóm trường metadata lên tất cả phiếu đang in.
     *
     * <p>Mỗi phiếu được đọc lại từ Room rồi mới sửa, nên không có snapshot cũ nào của màn
     * hình chen vào ghi đè dữ liệu nghiệp vụ. Kết quả được đưa ngược về object trên màn hình
     * để lần thao tác sau đứng trên đúng phiên bản.
     */
    private void patchAllPrintItems(String what, DataHelper.RefuelPatch patch) {
        for (RefuelItemData item : printItems) {
            String uniqueId = item.getUniqueId();
            // Mẻ của xe khác cũng phải nhận dấu đã in — nếu không, xe kia và chính máy này
            // đều thấy mẻ là chưa in và in lần hai bằng một số phiếu khác. Cửa
            // patchRefuelDocument chỉ cho đúng nhóm trường chứng từ đi qua.
            DataHelper.PatchResult result = DataHelper.patchRefuelDocument(uniqueId, patch);

            Logger.appendLog(LOG_TAG, "patch " + what + " uid=" + uniqueId
                    + " own=" + isCurrentTruckItem(item)
                    + " applied=" + result.applied
                    + (result.applied ? "" : " reason=" + result.reason));

            if (result.applied && result.data != null)
                syncScreenCopy(result.data);
            else
                runOnUiThread(() -> showErrorMessage(R.string.save_print_info_failed));
        }
        DataHelper.Synchronize();
    }

    /** Đưa bản ghi vừa lưu về các object đang hiển thị để chúng không còn đứng trên bản cũ. */
    private void syncScreenCopy(RefuelItemData saved) {
        if (refuelData != null && saved.getUniqueId() != null
                && saved.getUniqueId().equals(refuelData.getUniqueId()))
            refuelData = saved;

        for (int i = 0; i < allItems.size(); i++) {
            if (saved.getUniqueId() != null
                    && saved.getUniqueId().equals(allItems.get(i).getUniqueId()))
                allItems.set(i, saved);
        }
        for (int i = 0; i < printItems.size(); i++) {
            if (saved.getUniqueId() != null
                    && saved.getUniqueId().equals(printItems.get(i).getUniqueId()))
                printItems.set(i, saved);
        }
        runOnUiThread(() -> {
            truckArrayAdapter.notifyDataSetChanged();
            binding.invalidateAll();
        });
    }

    private boolean updateAllInvoice(String invoiceNumber) {
        return updateAllInvoice(invoiceNumber, invoiceModel.getInvoiceFormId(), invoiceModel.getInvoiceType(), invoiceModel.getTechLog());
    }

    private boolean updateAllInvoice(String invoiceNumber, int formId, INVOICE_TYPE printTemplate,double techlog) {
        // onActivityResult có thể gọi thẳng vào đây, không đi qua dialog guard. Danh sách
        // rỗng thì không có gì để ghi; danh sách chỉ có mẻ xe khác VẪN ghi được — đó là ca
        // in hộ, và số hoá đơn phải bám vào đúng những mẻ vừa in.
        if (printItems == null || printItems.isEmpty()) {
            Logger.appendLog(LOG_TAG, "Không cập nhật hoá đơn: danh sách in rỗng");
            return false;
        }
        for (RefuelItemData item : printItems) {
            if (item.getInvoiceNumber() != null && item.getInvoiceNumber().equals(invoiceNumber)) {
                return false;
            }
        }

        RefuelItemData documentSource = documentHeader();
        if (documentSource == null) return false;
        // Dòng đang được chọn trên UI có thể không phải nguồn header. Giá/thuế phải lấy đúng
        // từ mẻ đã dùng làm header của chứng từ, không phải từ dòng đang xem.
        final double price = documentSource.getPrice();
        final double taxRate = documentSource.getTaxRate();

        // Cùng lý do với receipt: chỉ ghi các trường của hoá đơn lên bản ghi mới nhất.
        new Thread(() -> patchAllPrintItems("invoice", latest -> {
            latest.setInvoiceNumber(invoiceNumber);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
            latest.setPrice(price);
            latest.setTaxRate(taxRate);
            latest.setPrintTemplate(printTemplate);
            latest.setInvoiceFormId(formId);
            latest.setWeightNote(String.format("%.0f", techlog));
        })).start();
        if (printDialog != null)
            printDialog.dismiss();
        truckArrayAdapter.notifyDataSetChanged();
        binding.invalidateAll();
        return true;
    }


    private final Context context = this;
    private int mHour;
    private int mMinute;
    private int mDay;

    private void showTimeDialog(int id) {
        if (blockEditIfLocked()) return;

        // Giờ tra nạp chỉ bị khoá khi ĐÃ XUẤT HOÁ ĐƠN. Trước mốc đó vẫn phải cho sửa: mẻ
        // vừa bấm kết thúc đã là DONE, nhưng chưa chốt sổ.
        if (refuelData != null && refuelData.isMeasuredTimeLockedOnServer()) {
            Logger.appendLog(LOG_TAG, "CHẶN sửa giờ: phiếu đã xuất hoá đơn uid="
                    + refuelData.getUniqueId()
                    + " invoiceNumber=" + refuelData.getInvoiceNumber());
            showBusinessError("Phiếu đã xuất hoá đơn " + refuelData.getInvoiceNumber()
                    + " nên giờ tra nạp không sửa được nữa.");
            return;
        }
        final Date date = new Date();
        Date savedTime = id == R.id.refuel_preview_starttime
                ? refuelData.getStartTime() : refuelData.getEndTime();
        if (savedTime != null) date.setTime(savedTime.getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        int mYear = c.get(Calendar.YEAR);
        int mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay,
                                                  int minute) {
                                c.set(Calendar.MINUTE, minute);
                                c.set(Calendar.HOUR_OF_DAY, hourOfDay);

                                // Đường sửa giờ trước đây KHÔNG ghi vết nào: log chỉ có cú
                                // chạm vào ô, không có giá trị cũ, giá trị mới, hay việc sửa
                                // có ăn hay không. Nhật ký xe HAN3-20-7005 ngày 25-08-2026 cho
                                // thấy người dùng chạm ô giờ rồi bấm làm mới bốn lần trong 90
                                // giây — không cách nào biết họ đã sửa gì.
                                Date oldValue = id == R.id.refuel_preview_starttime
                                        ? refuelData.getStartTime() : refuelData.getEndTime();

                                if (id == R.id.refuel_preview_starttime)
                                    refuelData.setStartTime(c.getTime());
                                else if (id == R.id.refuel_preview_endtime)
                                    refuelData.setEndTime(c.getTime());

                                Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                                        "Sửa tay %s uid=%s: %s -> %s",
                                        id == R.id.refuel_preview_starttime
                                                ? "giờ bắt đầu" : "giờ kết thúc",
                                        refuelData.getUniqueId(),
                                        DateUtils.formatDate(oldValue, "dd/MM HH:mm:ss"),
                                        DateUtils.formatDate(c.getTime(), "dd/MM HH:mm:ss")));

                                updateBinding(false);
                            }
                        }, mHour, mMinute, false);
                timePickerDialog.show();

            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }

    private void showReturnInput(double amount, RETURN_UNIT unit) {
        if (blockEditIfLocked()) return;
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 5, 0, 5);


        final EditText returnAmountEditText = new EditText(this);
        returnAmountEditText.setText(String.format("%.0f", amount));
        layout.addView(returnAmountEditText);

        final RadioGroup radGroup = new RadioGroup(this);
        radGroup.setOrientation(RadioGroup.HORIZONTAL);
        int RAD_UNIT = 3435248;

        RadioButton radBtn = new RadioButton(this);
        radBtn.setText(RETURN_UNIT.KG.toString());
        radBtn.setPadding(5, 5, 5, 5);
        radBtn.setChecked(unit == RETURN_UNIT.KG);
        radBtn.setId(RAD_UNIT);
        radGroup.addView(radBtn);
        radBtn = new RadioButton(this);
        radBtn.setText(RETURN_UNIT.GALLON.toString());
        radBtn.setPadding(5, 5, 5, 5);
        radBtn.setChecked(unit == RETURN_UNIT.GALLON);
        //radBtn.setId(1);
        radGroup.addView(radBtn);

        layout.addView(radGroup);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.update_return_amount);

        builder.setView(layout);


        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {


            }
        });
        builder.setNegativeButton(R.string.back, null);

        AlertDialog returnDlg = builder.create();
        returnDlg.show();
        returnDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    double amount = numberFormat.parse(returnAmountEditText.getText().toString()).doubleValue();
                    RETURN_UNIT unit = radGroup.getCheckedRadioButtonId() == RAD_UNIT ? RETURN_UNIT.KG : RETURN_UNIT.GALLON;
                    if (calculateReturnAmount(amount, unit))
                        returnDlg.dismiss();
                } catch (Exception ex) {
                    showErrorMessage(R.string.invalid_number_format);
                }
            }
        });

        returnAmountEditText.requestFocus();
    }

    private final boolean isEditing = false;

    @SuppressLint("StaticFieldLeak")
    /**
     * "Nạp thêm": tạo một MẺ MỚI CỦA XE NÀY trên cùng chuyến với dòng đang xem.
     *
     * <p>Được phép cả khi dòng đang xem là của XE KHÁC — đó là nghiệp vụ có thật: nhìn thấy
     * xe bạn đã nạp cho chuyến này, xe mình nạp tiếp phần còn lại. Bản vá trước chặn bằng
     * {@code blockEditIfLocked()} nên đã làm chết tính năng; cái cần chặn không phải thao tác
     * mà là việc bản sao mang theo định danh của phiếu nguồn.
     *
     * <p>{@link RefuelItemData#copy()} đã cấp UniqueId mới và xoá Id/LocalId/số đồng hồ/số
     * chứng từ. Nhưng nó {@code clone()} nên các trường KHÔNG thuộc nghiệp vụ vẫn còn nguyên:
     * {@code rawJson} vẫn là JSON server của phiếu nguồn (kèm UniqueId, Id, TruckNo của xe
     * kia), và {@code baseJson}/{@code baseClientSeq} vẫn là baseline của row kia. Để nguyên
     * thì đúng như lo ngại cũ: dữ liệu replica có đường lên server dưới danh nghĩa xe này.
     */
    private void createNewItem() {
        try {
            final RefuelItemData itemData = refuelData.copyForNewBatch(
                    currentApp.getSetting().getTruckId(), currentApp.getTruckNo());
            if (itemData == null) {
                Toast.makeText(this, R.string.error_refuel_save_failed, Toast.LENGTH_LONG).show();
                return;
            }

            new AsyncTask<Void, Void, RefuelItemData>() {
                @Override
                protected RefuelItemData doInBackground(Void... voids) {
                    //RefuelItemData response = DataHelper.postRefuel(itemData);
                    return itemData;
                }

                @Override
                protected void onPostExecute(RefuelItemData response) {
                    postRefuelCompleted(response);
                    super.onPostExecute(response);
                }
            }.execute();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void postRefuelCompleted(RefuelItemData itemData) {
        if (itemData != null) {
            Intent intent = new Intent(this, RefuelDetailActivity.class);
            com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, itemData);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // Khoá sync phải được mở dù người dùng rời màn hình bằng đường nào. Trước đây chỉ
        // exit() mở khoá, nên bấm Back / về Home / hệ thống thu hồi activity là khoá treo
        // lại và TOÀN BỘ đồng bộ chết tới khi khởi động lại app — dữ liệu vẫn vào Room đủ
        // nhưng không bao giờ lên tới server.
        DataHelper.unlockSync();
        // shutdown() chứ KHÔNG phải shutdownNow(): các lần sửa đã xếp hàng vẫn phải được ghi
        // xuống Room. Huỷ chúng ở đây là làm mất đúng thao tác người dùng vừa thực hiện.
        previewSaveExecutor.shutdown();
        super.onDestroy();
        refuelData = null;
        Runtime.getRuntime().gc();
    }
}
