package com.megatech.fms.helpers;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.entity.TruckInvoice;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TruckInvoiceSync {
    private TruckInvoiceSync() {}

    public static long threeDayCutoff() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, -2);
        return calendar.getTimeInMillis();
    }

    /**
     * Lọc hoá đơn tải thẳng từ server (xe khác — không có bản lưu trong máy) theo đúng khoảng
     * mà DAO dùng cho xe mình: {@code from <= billDate < toExclusive}, mới nhất lên đầu.
     */
    public static List<TruckInvoice> filterRange(List<TruckInvoice> invoices, long from, long toExclusive) {
        List<TruckInvoice> result = new ArrayList<>();
        if (invoices == null) return result;
        for (TruckInvoice invoice : invoices) {
            if (invoice == null || invoice.getBillDate() == null) continue;
            long time = invoice.getBillDate().getTime();
            if (time >= from && time < toExclusive) result.add(invoice);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.getBillDate().getTime(), a.getBillDate().getTime()));
        return result;
    }

    /**
     * Đồng bộ hoá đơn của xe hiện tại vào bảng lưu tạm.
     *
     * @return danh sách VỪA TẢI VỀ, hoặc {@code null} khi không tải được (mất mạng, chưa cài
     *         xe). Danh sách trả về còn mang {@code ReceiptId} — bản ghi trong Room thì KHÔNG,
     *         vì trường đó là {@code @Ignore} để không phải đổi schema. Màn hình phải hiển thị
     *         danh sách này khi có, nếu không thì mất đường in lại phiếu của chính xe mình.
     */
    public static List<TruckInvoice> synchronize() {
        FMSApplication app = FMSApplication.getApplication();
        if (app == null) return null;
        AppDatabase db = app.getDatabase();
        long cutoff = threeDayCutoff();
        db.truckInvoiceDao().deleteOlderThan(cutoff);
        int truckId = app.getTruckId();
        if (truckId <= 0) return null;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String from = formatter.format(cutoff);
        String to = formatter.format(System.currentTimeMillis());
        List<TruckInvoice> invoices = new TruckInvoiceAPI().fetchByTruck(truckId, from, to);
        if (invoices == null) return null;
        if (!invoices.isEmpty()) db.truckInvoiceDao().insertOnly(invoices);
        return invoices;
    }
}
