package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.TruckInvoice;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TruckInvoiceAPI extends BaseAPI {
    public List<TruckInvoice> getByTruck(int truckId, String fromDate, String toDate) {
        List<TruckInvoice> result = fetchByTruck(truckId, fromDate, toDate);
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * Như {@link #getByTruck} nhưng trả {@code null} khi không tải được — xem hoá đơn xe khác
     * không có bản lưu trong máy, nên phải nói được "mất mạng" khác với "không có hoá đơn".
     */
    public List<TruckInvoice> fetchByTruck(int truckId, String fromDate, String toDate) {
        if (truckId <= 0) return Collections.emptyList();
        try {
            String requestUrl = BASE_URL + "/api/invoices/by-truck/" + truckId
                    + "?fromDate=" + fromDate + "&toDate=" + toDate;
            HttpResponse response = httpClient.sendGET(requestUrl);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                TruckInvoice[] result = gson.fromJson(response.getData(), TruckInvoice[].class);
                Logger.appendLog("TruckInvoiceAPI", "GET " + requestUrl + " -> "
                        + (result == null ? 0 : result.length) + " hoá đơn");
                return result == null ? Collections.emptyList() : Arrays.asList(result);
            }
            Logger.appendLog("TruckInvoiceAPI", "GET " + requestUrl + " thất bại: HTTP "
                    + response.getResponseCode());
        } catch (Exception ex) {
            Logger.appendLog("TruckInvoiceAPI", "GET failed: " + ex.getMessage());
        }
        return null;
    }

    public byte[] getPdf(String loginTaxCode, String electronicInvoiceId) {
        if (loginTaxCode == null || loginTaxCode.trim().isEmpty()
                || electronicInvoiceId == null || electronicInvoiceId.trim().isEmpty()) return null;
        try {
            String requestUrl = BASE_URL + "/api/invoices/pdf?LoginTaxCode="
                    + URLEncoder.encode(loginTaxCode, "UTF-8") + "&hoadon68_id="
                    + URLEncoder.encode(electronicInvoiceId, "UTF-8") + "&inChuyenDoi=false";
            return httpClient.sendGETBytes(requestUrl, "application/pdf");
        } catch (Exception ex) {
            Logger.appendLog("TruckInvoiceAPI", "PDF failed: " + ex.getMessage());
            return null;
        }
    }
}
