package com.megatech.fms.helpers;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.entity.TruckInvoice;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

    public static void synchronize() {
        FMSApplication app = FMSApplication.getApplication();
        if (app == null) return;
        AppDatabase db = app.getDatabase();
        long cutoff = threeDayCutoff();
        db.truckInvoiceDao().deleteOlderThan(cutoff);
        int truckId = app.getTruckId();
        if (truckId <= 0) return;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String from = formatter.format(cutoff);
        String to = formatter.format(System.currentTimeMillis());
        List<TruckInvoice> invoices = new TruckInvoiceAPI().getByTruck(truckId, from, to);
        if (!invoices.isEmpty()) db.truckInvoiceDao().insertOnly(invoices);
    }
}
