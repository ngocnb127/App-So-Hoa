package com.megatech.fms;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.megatech.fms.data.entity.TruckInvoice;
import com.megatech.fms.helpers.TruckInvoiceSync;
import com.megatech.fms.view.TruckInvoiceAdapter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TruckInvoiceActivity extends AppCompatActivity {
    private final Calendar fromDate = Calendar.getInstance();
    private final Calendar toDate = Calendar.getInstance();
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    private TruckInvoiceAdapter adapter;
    private Button btnFromDate, btnToDate;
    private TextView warning, empty;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_truck_invoice);
        normalize(fromDate); normalize(toDate);
        btnFromDate = findViewById(R.id.btnFromDate); btnToDate = findViewById(R.id.btnToDate);
        warning = findViewById(R.id.txtThreeDayWarning); empty = findViewById(R.id.txtEmptyInvoices);
        progress = findViewById(R.id.progressInvoices);
        RecyclerView list = findViewById(R.id.listTruckInvoices);
        list.setLayoutManager(new LinearLayoutManager(this)); adapter = new TruckInvoiceAdapter(this::openPdf); list.setAdapter(adapter);
        btnFromDate.setOnClickListener(v -> pickDate(fromDate, btnFromDate));
        btnToDate.setOnClickListener(v -> pickDate(toDate, btnToDate));
        findViewById(R.id.btnFilter).setOnClickListener(v -> loadData(true));
        updateDateLabels(); loadData(true);
    }

    public void back(View view) { finish(); }

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
        progress.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            if (syncFirst) TruckInvoiceSync.synchronize();
            List<TruckInvoice> values = FMSApplication.getApplication().getDatabase().truckInvoiceDao()
                    .getBetween(effectiveFrom, endExclusive.getTimeInMillis());
            runOnUiThread(() -> { progress.setVisibility(View.GONE); adapter.setItems(values);
                empty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE); });
        });
    }

    private void updateDateLabels() { btnFromDate.setText(displayFormat.format(fromDate.getTime())); btnToDate.setText(displayFormat.format(toDate.getTime())); }
    private void normalize(Calendar value) { value.set(Calendar.HOUR_OF_DAY, 0); value.set(Calendar.MINUTE, 0); value.set(Calendar.SECOND, 0); value.set(Calendar.MILLISECOND, 0); }
}
