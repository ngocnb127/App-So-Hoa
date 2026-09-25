package com.megatech.fms.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.megatech.fms.BuildConfig;
import com.megatech.fms.R;
import com.megatech.fms.data.entity.TruckInvoice;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TruckInvoiceAdapter extends RecyclerView.Adapter<TruckInvoiceAdapter.Holder> {
    public interface PdfClickListener { void onPdfClick(TruckInvoice invoice); }
    public interface ReprintClickListener { void onReprintClick(TruckInvoice invoice); }
    private final List<TruckInvoice> items = new ArrayList<>();
    private final PdfClickListener pdfClickListener;
    private final ReprintClickListener reprintClickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
    public TruckInvoiceAdapter(PdfClickListener pdfClickListener, ReprintClickListener reprintClickListener) {
        this.pdfClickListener = pdfClickListener; this.reprintClickListener = reprintClickListener;
    }
    public void setItems(List<TruckInvoice> values) { items.clear(); items.addAll(values); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.truck_invoice_item, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        TruckInvoice item = items.get(position);
        holder.date.setText(item.getBillDate() == null ? "" : dateFormat.format(item.getBillDate()));
        holder.flightCode.setText(value(item.getFlightCode()));
        holder.billNo.setText(value(item.getBillNo()));
        holder.number.setText(value(item.getInvoiceNumber()));
        holder.signNo.setText(value(item.getSignNo()));
        holder.status.setText(value(item.getStatus()));
        View pdfButton = holder.itemView.findViewById(R.id.btnInvoicePdf);
        boolean hasPdf = item.getElectronicInvoiceId() != null && !item.getElectronicInvoiceId().trim().isEmpty();
        pdfButton.setEnabled(hasPdf);
        pdfButton.setAlpha(hasPdf ? 1f : 0.35f);
        pdfButton.setOnClickListener(v -> pdfClickListener.onPdfClick(item));
        // In lại phiếu chỉ có ở bản máy in nhiệt (ZPL); số phiếu (BillNo) là khoá để lấy phiếu.
        View reprintButton = holder.itemView.findViewById(R.id.btnReceiptReprint);
        reprintButton.setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE);
        boolean hasReceipt = item.getBillNo() != null && !item.getBillNo().trim().isEmpty();
        reprintButton.setEnabled(hasReceipt);
        reprintButton.setAlpha(hasReceipt ? 1f : 0.35f);
        reprintButton.setOnClickListener(v -> reprintClickListener.onReprintClick(item));
    }
    private String value(String text) { return text == null ? "" : text; }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final TextView date, flightCode, billNo, number, signNo, status;
        Holder(View view) { super(view); date=view.findViewById(R.id.txtInvoiceDate); flightCode=view.findViewById(R.id.txtFlightCode); billNo=view.findViewById(R.id.txtBillNo); number=view.findViewById(R.id.txtInvoiceNumber); signNo=view.findViewById(R.id.txtSignNo); status=view.findViewById(R.id.txtInvoiceStatus); }
    }
}
