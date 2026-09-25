package com.megatech.fms;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.megatech.fms.data.entity.TruckInvoice;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.ReceiptReprint;
import com.megatech.fms.helpers.TruckInvoiceAPI;
import com.megatech.fms.helpers.TruckInvoiceSync;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.view.TruckInvoiceAdapter;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hoá đơn theo xe. Mặc định là xe đang đăng ký trên máy; chọn được cả xe khác.
 *
 * <p>Xe mình: đồng bộ vào bảng {@code TruckInvoice} rồi đọc từ đó, nên mất mạng vẫn xem được
 * bản đã lưu. Xe khác: tải thẳng từ server, KHÔNG lưu vào bảng — bảng đó là bản lưu của xe
 * mình, trộn xe khác vào thì màn xe mình hiện cả hoá đơn xe khác.
 */
public class TruckInvoiceActivity extends AppCompatActivity {
    private final Calendar fromDate = Calendar.getInstance();
    private final Calendar toDate = Calendar.getInstance();
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    /** Danh sách xe và in lại: không xếp hàng sau lượt tải hoá đơn đang chờ mạng. */
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
    /** Đổi xe liên tục thì chỉ kết quả của lượt tải cuối được hiện. */
    private final AtomicInteger loadSeq = new AtomicInteger();
    private TruckInvoiceAdapter adapter;
    private Button btnFromDate, btnToDate;
    private TextView warning, empty;
    private ProgressBar progress;
    private Spinner truckSpinner;
    private final List<TruckModel> trucks = new ArrayList<>();
    private int currentTruckId;
    private int selectedTruckId;
    private String selectedTruckLabel;
    private boolean reprintBusy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_truck_invoice);
        normalize(fromDate); normalize(toDate);
        // Mặc định là CẢ khoảng còn giữ được (3 ngày), không phải mỗi hôm nay: xe chưa xuất
        // hoá đơn nào trong ngày thì màn hình mở ra trống trơn, trông y như lỗi tải dữ liệu.
        fromDate.setTimeInMillis(TruckInvoiceSync.threeDayCutoff());
        FMSApplication app = FMSApplication.getApplication();
        currentTruckId = app.getTruckId();
        selectedTruckId = currentTruckId;
        selectedTruckLabel = app.getTruckNo();
        btnFromDate = findViewById(R.id.btnFromDate); btnToDate = findViewById(R.id.btnToDate);
        warning = findViewById(R.id.txtThreeDayWarning); empty = findViewById(R.id.txtEmptyInvoices);
        progress = findViewById(R.id.progressInvoices);
        truckSpinner = findViewById(R.id.spinnerInvoiceTruck);
        findViewById(R.id.txtReprintHeader).setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE);
        RecyclerView list = findViewById(R.id.listTruckInvoices);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TruckInvoiceAdapter(this::openPdf, this::reprintReceipt); list.setAdapter(adapter);
        btnFromDate.setOnClickListener(v -> pickDate(fromDate, btnFromDate));
        btnToDate.setOnClickListener(v -> pickDate(toDate, btnToDate));
        findViewById(R.id.btnFilter).setOnClickListener(v -> loadData(true));
        updateDateLabels();
        setupTruckSpinner();
        loadData(true);
    }

    @Override protected void onDestroy() {
        loadExecutor.shutdownNow();
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    public void back(View view) { finish(); }

    // ------------------------------------------------------------------ chọn xe

    private void setupTruckSpinner() {
        // Hiện ngay xe hiện tại để không phải chờ danh sách xe mới biết đang xem xe nào.
        trucks.clear();
        trucks.add(new TruckModel(selectedTruckLabel, currentTruckId));
        bindTrucks();
        truckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= trucks.size()) return;
                TruckModel truck = trucks.get(position);
                if (truck.getTruckId() == selectedTruckId) return;
                selectedTruckId = truck.getTruckId();
                selectedTruckLabel = label(truck);
                loadData(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        backgroundExecutor.execute(() -> {
            List<TruckModel> values = null;
            try {
                values = DataHelper.getLocalTrucks();
                // Máy mới cài chưa có danh sách xe thì mới gọi mạng.
                if (values == null || values.isEmpty()) values = DataHelper.getTrucks();
            } catch (Exception ex) {
                Logger.appendLog("TruckInvoice", "Tải danh sách xe lỗi: " + ex.getMessage());
            }
            List<TruckModel> ordered = orderTrucks(values);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                trucks.clear();
                trucks.addAll(ordered);
                bindTrucks();
            });
        });
    }

    /** Xe hiện tại đứng đầu, các xe còn lại theo số xe. */
    private List<TruckModel> orderTrucks(List<TruckModel> values) {
        List<TruckModel> others = new ArrayList<>();
        TruckModel own = null;
        if (values != null) {
            for (TruckModel truck : values) {
                if (truck == null || truck.getTruckId() <= 0) continue;
                if (truck.getTruckId() == currentTruckId) own = truck;
                else others.add(truck);
            }
        }
        Collections.sort(others, (a, b) -> label(a).compareToIgnoreCase(label(b)));
        List<TruckModel> ordered = new ArrayList<>();
        ordered.add(own != null ? own : new TruckModel(FMSApplication.getApplication().getTruckNo(), currentTruckId));
        ordered.addAll(others);
        return ordered;
    }

    private void bindTrucks() {
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < trucks.size(); i++) {
            TruckModel truck = trucks.get(i);
            labels.add(label(truck) + (truck.getTruckId() == currentTruckId ? " (xe này)" : ""));
            if (truck.getTruckId() == selectedTruckId) selected = i;
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        truckSpinner.setAdapter(spinnerAdapter);
        truckSpinner.setSelection(selected, false);
    }

    private static String label(TruckModel truck) {
        String value = truck.getTruckNo();
        if (value == null || value.trim().isEmpty()) value = truck.toString();
        return value == null ? "" : value.trim();
    }

    // ------------------------------------------------------------------ danh sách

    private void openPdf(TruckInvoice invoice) {
        if (invoice.getElectronicInvoiceId() == null || invoice.getElectronicInvoiceId().trim().isEmpty()) {
            Toast.makeText(this, "Hóa đơn chưa có dữ liệu PDF", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, InvoicePdfActivity.class);
        intent.putExtra(InvoicePdfActivity.EXTRA_TAX_CODE, invoice.getLoginTaxCode());
        intent.putExtra(InvoicePdfActivity.EXTRA_INVOICE_ID, invoice.getElectronicInvoiceId());
        intent.putExtra(InvoicePdfActivity.EXTRA_INVOICE_NUMBER, invoice.getInvoiceNumber());
        startActivity(intent);
    }

    private void pickDate(Calendar target, Button button) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            target.set(year, month, day); normalize(target); button.setText(displayFormat.format(target.getTime()));
        }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadData(boolean syncFirst) {
        long cutoff = TruckInvoiceSync.threeDayCutoff();
        warning.setVisibility(fromDate.getTimeInMillis() < cutoff ? View.VISIBLE : View.GONE);
        long effectiveFrom = Math.max(fromDate.getTimeInMillis(), cutoff);
        Calendar endExclusive = (Calendar) toDate.clone(); endExclusive.add(Calendar.DAY_OF_YEAR, 1);
        long to = endExclusive.getTimeInMillis();
        int truckId = selectedTruckId;
        String truckLabel = selectedTruckLabel;
        boolean ownTruck = truckId == currentTruckId;
        int seq = loadSeq.incrementAndGet();
        // Máy chưa chọn xe trong Thiết lập thì mọi lời gọi đều trả rỗng, và màn hình trống
        // trông y như mất mạng. Nói thẳng ra nguyên nhân.
        if (truckId <= 0) {
            Logger.appendLog("TruckInvoice", "Không tải hoá đơn: máy chưa cài số xe");
            progress.setVisibility(View.GONE);
            empty.setText("Máy chưa cài số xe. Vào Thiết lập chọn xe rồi quay lại.");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        adapter.setItems(Collections.emptyList());
        empty.setVisibility(View.GONE);
        loadExecutor.execute(() -> {
            List<TruckInvoice> values;
            boolean failed = false;
            if (ownTruck) {
                // Bản vừa tải về còn mang ReceiptId, bản trong Room thì không (trường @Ignore).
                // Tải được thì phải dùng bản vừa tải, nếu không thì in lại phiếu của chính xe
                // mình sẽ hỏng trong khi xe khác lại in được — đúng lỗi gặp trên tablet 16-09.
                List<TruckInvoice> fresh = syncFirst ? TruckInvoiceSync.synchronize() : null;
                values = fresh != null
                        ? TruckInvoiceSync.filterRange(fresh, effectiveFrom, to)
                        : FMSApplication.getApplication().getDatabase().truckInvoiceDao()
                                .getBetween(effectiveFrom, to);
            } else {
                SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                List<TruckInvoice> fetched = new TruckInvoiceAPI().fetchByTruck(truckId,
                        apiFormat.format(effectiveFrom), apiFormat.format(toDate.getTimeInMillis()));
                failed = fetched == null;
                values = TruckInvoiceSync.filterRange(fetched, effectiveFrom, to);
            }
            boolean loadFailed = failed;
            // Dòng log này là thứ duy nhất phân biệt được "server không có hoá đơn nào trong
            // khoảng đã chọn" với "gọi API hỏng" khi đứng trước màn hình trống trên xe.
            Logger.appendLog("TruckInvoice", String.format(Locale.US,
                    "Tải hoá đơn: xe=%d (%s) từ=%s đến=%s -> %d dòng%s",
                    truckId, ownTruck ? "xe này, đọc DB sau khi đồng bộ" : "xe khác, đọc thẳng API",
                    displayFormat.format(effectiveFrom), displayFormat.format(toDate.getTime()),
                    values.size(), loadFailed ? " (GỌI API HỎNG)" : ""));
            runOnUiThread(() -> {
                if (seq != loadSeq.get() || isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                adapter.setItems(values);
                empty.setText(loadFailed
                        ? "Không tải được hóa đơn của xe " + truckLabel + ". Kiểm tra mạng rồi bấm Lọc."
                        : "Không có dữ liệu hóa đơn");
                empty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ------------------------------------------------------------------ in lại phiếu

    private void reprintReceipt(TruckInvoice invoice) {
        if (!BuildConfig.THERMAL_PRINTER || reprintBusy) return;
        String number = invoice.getBillNo() == null ? "" : invoice.getBillNo().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Hóa đơn chưa có số phiếu", Toast.LENGTH_LONG).show();
            return;
        }
        reprintBusy = true;
        progress.setVisibility(View.VISIBLE);
        File cacheRoot = getCacheDir();
        int receiptId = invoice.getReceiptId();
        backgroundExecutor.execute(() -> {
            ReceiptReprint.Prepared prepared = ReceiptReprint.prepare(cacheRoot, number, receiptId);
            runOnUiThread(() -> {
                reprintBusy = false;
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                onReprintPrepared(number, prepared);
            });
        });
    }

    private void onReprintPrepared(String number, ReceiptReprint.Prepared prepared) {
        if (prepared.model == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Không lấy được phiếu " + number)
                    .setMessage("Máy này không lưu phiếu này và không tải được từ server."
                            + " Bấm Lọc để tải lại danh sách (cần mạng) rồi in lại.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String warningText = ReceiptReprint.missingSignatureWarning(prepared.buyerMissing, prepared.sellerMissing);
        if (warningText == null) {
            openReprint(prepared.model);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("In lại phiếu " + number)
                .setMessage(warningText)
                .setPositiveButton("Vẫn in lại", (d, w) -> openReprint(prepared.model))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openReprint(ReceiptModel model) {
        Intent intent = new Intent(this, PrintReceiptActivity.class);
        intent.putExtra(PrintReceiptActivity.EXTRA_REPRINT_RECEIPT, model.toJson());
        startActivity(intent);
    }

    private void updateDateLabels() { btnFromDate.setText(displayFormat.format(fromDate.getTime())); btnToDate.setText(displayFormat.format(toDate.getTime())); }
    private void normalize(Calendar value) { value.set(Calendar.HOUR_OF_DAY, 0); value.set(Calendar.MINUTE, 0); value.set(Calendar.SECOND, 0); value.set(Calendar.MILLISECOND, 0); }
}
