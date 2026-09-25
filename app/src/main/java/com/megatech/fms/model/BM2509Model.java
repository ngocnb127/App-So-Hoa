package com.megatech.fms.model;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Date;

// BM 25.09/NLHK - Phiếu kiểm tra đối chứng nhiên liệu JET A-1 trên xe tra nạp
// (API_DOC_AppSoHoa mục 4.6, tên trường theo BM2509Model của API). Số để null = trống.
public class BM2509Model extends BaseModel {

    private Integer airportId;
    private Integer truckId;
    private Integer flightId;
    private String flightNo;                // rỗng + có FlightId -> server lấy Flight.Code
    @SerializedName("ACReg")
    private String acReg;                   // rỗng + có FlightId -> server lấy Flight.AircraftCode
    private Date time;                      // Thời gian kiểm tra
    private Boolean appearanceCheck = true; // true = Trong và sáng (C&B), false = Khác
    private String appearanceOther;

    // Chuyến tra nạp trước
    private String documentNo;              // [1]
    private Double lastDensity15;           // [2]
    private Double remainQuantity;          // [3]
    // Bể cấp hàng gần nhất
    private String releaseCertNo;           // [4]
    private Double tankDensity15;           // [5]
    private Double loadQuantity;            // [6]
    private Double averageDensity;          // [7] server tính lại, giá trị gửi lên bị bỏ qua
    // Đo tại sân đỗ
    private Double obsTemperature;          // [8]
    private Double obsDensity;              // [9]
    private Double density15;               // [10]
    private Double densityDiff;             // [11] server tính lại
    private String note;

    // chỉ đọc (server trả về)
    private String truckCode;
    private String flightCode;

    // chỉ dùng trên app: Message của lần đồng bộ lỗi gần nhất (400/403/404)
    private String syncError;

    /**
     * Tính [7], [11] để hiển thị trước khi đồng bộ — cùng công thức BM2509.Calculate() của server
     * (mục 4.6.3): làm tròn banker's (HALF_EVEN) 4 chữ số lẻ. Giá trị cuối cùng lấy từ response.
     */
    public void calculate() {
        BigDecimal remain = decimal(remainQuantity, true);
        BigDecimal load = decimal(loadQuantity, true);
        BigDecimal total = remain.add(load);
        BigDecimal avg = null;
        if (total.signum() > 0)
            avg = decimal(lastDensity15, true).multiply(remain)
                    .add(decimal(tankDensity15, true).multiply(load))
                    .divide(total, MathContext.DECIMAL128)
                    .setScale(4, RoundingMode.HALF_EVEN);
        averageDensity = avg == null ? null : avg.doubleValue();
        densityDiff = avg == null || density15 == null ? null
                : decimal(density15, false).subtract(avg).doubleValue();
    }

    // hiển thị số: bỏ số 0 thừa (server trả 795.2000), trống khi null
    public static String num(Double value) {
        return value == null ? "" : new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }

    public static BM2509Model fromJson(String json) {
        return gson.fromJson(json, BM2509Model.class);
    }

    private static BigDecimal decimal(Double value, boolean zeroIfNull) {
        if (value == null)
            return zeroIfNull ? BigDecimal.ZERO : null;
        return new BigDecimal(Double.toString(value));
    }

    public Integer getAirportId() {
        return airportId;
    }

    public void setAirportId(Integer airportId) {
        this.airportId = airportId;
    }

    public Integer getTruckId() {
        return truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public Integer getFlightId() {
        return flightId;
    }

    public void setFlightId(Integer flightId) {
        this.flightId = flightId;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public String getAcReg() {
        return acReg;
    }

    public void setAcReg(String acReg) {
        this.acReg = acReg;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public Boolean getAppearanceCheck() {
        return appearanceCheck;
    }

    public void setAppearanceCheck(Boolean appearanceCheck) {
        this.appearanceCheck = appearanceCheck;
    }

    public String getAppearanceOther() {
        return appearanceOther;
    }

    public void setAppearanceOther(String appearanceOther) {
        this.appearanceOther = appearanceOther;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public Double getLastDensity15() {
        return lastDensity15;
    }

    public void setLastDensity15(Double lastDensity15) {
        this.lastDensity15 = lastDensity15;
    }

    public Double getRemainQuantity() {
        return remainQuantity;
    }

    public void setRemainQuantity(Double remainQuantity) {
        this.remainQuantity = remainQuantity;
    }

    public String getReleaseCertNo() {
        return releaseCertNo;
    }

    public void setReleaseCertNo(String releaseCertNo) {
        this.releaseCertNo = releaseCertNo;
    }

    public Double getTankDensity15() {
        return tankDensity15;
    }

    public void setTankDensity15(Double tankDensity15) {
        this.tankDensity15 = tankDensity15;
    }

    public Double getLoadQuantity() {
        return loadQuantity;
    }

    public void setLoadQuantity(Double loadQuantity) {
        this.loadQuantity = loadQuantity;
    }

    public Double getAverageDensity() {
        return averageDensity;
    }

    public Double getObsTemperature() {
        return obsTemperature;
    }

    public void setObsTemperature(Double obsTemperature) {
        this.obsTemperature = obsTemperature;
    }

    public Double getObsDensity() {
        return obsDensity;
    }

    public void setObsDensity(Double obsDensity) {
        this.obsDensity = obsDensity;
    }

    public Double getDensity15() {
        return density15;
    }

    public void setDensity15(Double density15) {
        this.density15 = density15;
    }

    public Double getDensityDiff() {
        return densityDiff;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getTruckCode() {
        return truckCode;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }
}
