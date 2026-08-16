package com.megatech.fms.helpers;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserInfo;

import java.util.Date;
import java.util.List;

/**
 * Nơi duy nhất khởi tạo BM2505 mới và xác định sân bay cho phiếu.
 * Mọi màn hình (B2505Activity, RefuelPreviewActivity, RefuelDetailActivity)
 * đều phải đi qua đây thay vì tự dựng BM2505Model.
 */
public final class BM2505Factory {

    public static final String ARG_MODEL = "BM2505_MODEL";
    public static final String ARG_TRUCK_ID = "TRUCK_ID";
    public static final String ARG_AIRPORT_ID = "AIRPORT_ID";
    public static final String ARG_AIRPORT_NAME = "AIRPORT_NAME";
    public static final String ARG_FLIGHT_ID = "FLIGHT_ID";
    public static final String ARG_FLIGHT_CODE = "FLIGHT_CODE";
    public static final String ARG_FLIGHT_NO = "FLIGHT_NO";
    public static final String ARG_AIRCRAFT_CODE = "AIRCRAFT_CODE";

    private BM2505Factory() {
    }

    /**
     * Giá trị mặc định chung cho một phiếu BM2505 mới.
     * Sân bay ở đây mới chỉ là mức "sân bay mặc định của tài khoản";
     * kết quả cuối cùng do {@link #resolveAirport} quyết định sau khi có master data.
     */
    public static BM2505Model createNew(@Nullable Bundle args) {
        BM2505Model model = new BM2505Model();

        model.setWaterCheck(true);
        model.setTime(new Date());
        model.setReportType(0);

        FMSApplication app = FMSApplication.getApplication();
        UserInfo user = app != null ? app.getUser() : null;

        if (user != null)
            model.setOperatorId(user.getUserId());

        if (app != null) {
            model.setTruckId(app.getTruckId());
            model.setRTCNo(app.getQCNo());
        }

        model.setAirportId(getAccountAirportId());

        applyContextArgs(model, args);

        return model;
    }

    /**
     * Chỉ ghi đè các trường mà Bundle thực sự có key tương ứng.
     */
    public static void applyContextArgs(BM2505Model model, @Nullable Bundle args) {
        if (model == null || args == null) return;

        if (args.containsKey(ARG_TRUCK_ID))
            model.setTruckId(args.getInt(ARG_TRUCK_ID, model.getTruckId()));

        if (args.containsKey(ARG_FLIGHT_ID))
            model.setFlightId(args.getInt(ARG_FLIGHT_ID, model.getFlightId()));

        if (args.containsKey(ARG_FLIGHT_CODE))
            model.setFlightCode(args.getString(ARG_FLIGHT_CODE, model.getFlightCode()));
        else if (args.containsKey(ARG_FLIGHT_NO))
            model.setFlightCode(args.getString(ARG_FLIGHT_NO, model.getFlightCode()));

        if (args.containsKey(ARG_AIRCRAFT_CODE))
            model.setAircraftCode(args.getString(ARG_AIRCRAFT_CODE, model.getAircraftCode()));

        if (args.containsKey(ARG_AIRPORT_ID)) {
            int airportId = args.getInt(ARG_AIRPORT_ID, 0);
            if (airportId > 0) model.setAirportId(airportId);
        }

        if (args.containsKey(ARG_AIRPORT_NAME)) {
            String airportName = args.getString(ARG_AIRPORT_NAME, model.getAirportName());
            if (airportName != null && !airportName.trim().isEmpty())
                model.setAirportName(airportName);
        }
    }

    public static int getAccountAirportId() {
        FMSApplication app = FMSApplication.getApplication();
        UserInfo user = app != null ? app.getUser() : null;
        return user != null ? user.getAirportId() : 0;
    }

    /**
     * Kết quả xác định sân bay.
     * - resolved: đã xác định được sân bay hợp lệ, không cần người dùng chọn.
     * - truckAirportNotAllowed: sân bay của xe nằm ngoài phạm vi phân quyền của tài khoản.
     */
    public static class AirportResolution {
        public Integer airportId;
        public String airportName;
        public boolean resolved;
        public boolean truckAirportNotAllowed;
    }

    /**
     * Thứ tự ưu tiên:
     * sân bay đã lưu của phiếu (khi sửa)
     * -> sân bay của xe tra nạp hiện tại
     * -> sân bay mặc định của tài khoản
     * -> sân bay duy nhất trong danh sách được phân quyền.
     * Không bao giờ mặc định về phần tử đầu danh sách.
     */
    public static AirportResolution resolveAirport(boolean isEdit,
                                                   @Nullable Integer savedAirportId,
                                                   @Nullable TruckModel currentTruck,
                                                   int accountAirportId,
                                                   @Nullable List<AirportsModel> allowedAirports) {

        AirportResolution result = new AirportResolution();

        // 1. Khi sửa: giữ nguyên sân bay đã lưu của phiếu, không ghi đè.
        if (isEdit && savedAirportId != null && savedAirportId > 0) {
            result.airportId = savedAirportId;
            result.airportName = findAirportName(allowedAirports, savedAirportId);
            result.resolved = true;
            return result;
        }

        // 2. Sân bay của xe tra nạp hiện tại.
        Integer truckAirportId = currentTruck != null ? currentTruck.getAirportId() : null;
        if (truckAirportId != null && truckAirportId > 0) {
            if (isAllowed(allowedAirports, truckAirportId)) {
                result.airportId = truckAirportId;
                result.airportName = findAirportName(allowedAirports, truckAirportId);
                if (result.airportName == null)
                    result.airportName = currentTruck.getAirportCode();
                result.resolved = true;
                return result;
            }
            // Sân bay của xe không thuộc phân quyền: không tự chọn sai.
            result.truckAirportNotAllowed = true;
            return result;
        }

        // 3. Sân bay mặc định của tài khoản.
        if (accountAirportId > 0 && isAllowed(allowedAirports, accountAirportId)) {
            result.airportId = accountAirportId;
            result.airportName = findAirportName(allowedAirports, accountAirportId);
            result.resolved = true;
            return result;
        }

        // 4. Tài khoản chỉ được phân quyền đúng một sân bay.
        if (allowedAirports != null && allowedAirports.size() == 1) {
            AirportsModel only = allowedAirports.get(0);
            if (only != null && only.getId() != null && only.getId() > 0) {
                result.airportId = only.getId();
                result.airportName = only.getName();
                result.resolved = true;
                return result;
            }
        }

        // Không xác định được: để người dùng chọn.
        return result;
    }

    /**
     * Danh sách rỗng/null nghĩa là chưa tải được phân quyền, khi đó không chặn giá trị nào.
     */
    private static boolean isAllowed(@Nullable List<AirportsModel> airports, int airportId) {
        if (airports == null || airports.isEmpty()) return true;
        for (AirportsModel a : airports) {
            if (a != null && a.getId() != null && a.getId() == airportId)
                return true;
        }
        return false;
    }

    @Nullable
    public static String findAirportName(@Nullable List<AirportsModel> airports, @Nullable Integer airportId) {
        if (airports == null || airportId == null || airportId <= 0) return null;
        for (AirportsModel a : airports) {
            if (a != null && a.getId() != null && a.getId().intValue() == airportId.intValue())
                return a.getName();
        }
        return null;
    }

    @Nullable
    public static TruckModel findTruck(@Nullable List<TruckModel> trucks, int truckId) {
        if (trucks == null || truckId <= 0) return null;
        for (TruckModel t : trucks) {
            if (t != null && t.getId() != null && t.getId() == truckId)
                return t;
        }
        return null;
    }
}
