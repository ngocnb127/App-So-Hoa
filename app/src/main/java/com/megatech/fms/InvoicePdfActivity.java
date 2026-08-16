package com.megatech.fms;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.barteksc.pdfviewer.PDFView;
import com.megatech.fms.helpers.TruckInvoiceAPI;
import java.util.concurrent.Executors;

public class InvoicePdfActivity extends AppCompatActivity {
    public static final String EXTRA_TAX_CODE = "taxCode";
    public static final String EXTRA_INVOICE_ID = "electronicInvoiceId";
    public static final String EXTRA_INVOICE_NUMBER = "invoiceNumber";
    private byte[] pdfData;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_invoice_pdf);
        String taxCode = getIntent().getStringExtra(EXTRA_TAX_CODE);
        String invoiceId = getIntent().getStringExtra(EXTRA_INVOICE_ID);
        String number = getIntent().getStringExtra(EXTRA_INVOICE_NUMBER);
        ((TextView) findViewById(R.id.txtPdfTitle)).setText("HÓA ĐƠN " + (number == null ? "" : number));
        PDFView pdfView = findViewById(R.id.pdfView);
        ProgressBar progress = findViewById(R.id.pdfProgress);
        TextView error = findViewById(R.id.txtPdfError);
        Executors.newSingleThreadExecutor().execute(() -> {
            pdfData = new TruckInvoiceAPI().getPdf(taxCode, invoiceId);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (pdfData == null || pdfData.length == 0) { error.setVisibility(View.VISIBLE); return; }
                pdfView.fromBytes(pdfData).enableSwipe(true).swipeHorizontal(false)
                        .enableDoubletap(true).spacing(8).onError(ex -> error.setVisibility(View.VISIBLE)).load();
            });
        });
    }

    public void back(View view) { finish(); }
    @Override protected void onDestroy() { pdfData = null; super.onDestroy(); }
}
