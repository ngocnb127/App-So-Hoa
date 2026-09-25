package com.megatech.fms.model;

import java.util.Date;

// BM 25.09/NLHK - Phiếu kiểm tra đối chứng nhiên liệu JET A-1 trên xe tra nạp
public class BM2509Model extends BaseModel {

    private int truckId;
    private String truckNo;             // Xe tra nạp
    private Date time;                  // Thời gian kiểm tra
    private int flightId;               // Chuyến bay / Tàu bay
    private String flightCode;
    private String aircraftCode;
    private String appearanceCheck = "C&B"; // 1. Kiểm tra ngoại quan
    private int operatorId;
    private String operatorName;

    // Chuyến tra nạp trước
    private String documentNo;          // [1] Số chứng từ
    private double lastDensity15;       // [2] KLR ở 15°C
    private double remainQuantity;      // [3] Lượng nhiên liệu còn lại trên xe
    // Bể cấp hàng gần nhất
    private String releaseCertNo;       // [4] Số CNXH
    private double tankDensity15;       // [5] KLR ở 15°C
    private double loadQuantity;        // [6] Lượng nhiên liệu cấp lên xe
    private double avgDensity;          // [7] KLR trung bình
    // Đo tại sân đỗ cho lần tra nạp này
    private double temperature;         // [8] Nhiệt độ quan sát
    private double density;             // [9] KLR quan sát
    private double density15;           // [10] KLR ở 15°C
    private double densityDiff;         // [11] Chênh lệch KLR

    // [7] = ([2]x[3] + [5]x[6]) / ([3] + [6]); [11] = [10] - [7]
    public void calculate() {
        double qty = remainQuantity + loadQuantity;
        avgDensity = qty > 0 ? (lastDensity15 * remainQuantity + tankDensity15 * loadQuantity) / qty : 0;
        densityDiff = avgDensity > 0 && density15 > 0 ? density15 - avgDensity : 0;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    public String getTruckNo() {
        return truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getAppearanceCheck() {
        return appearanceCheck;
    }

    public void setAppearanceCheck(String appearanceCheck) {
        this.appearanceCheck = appearanceCheck;
    }

    public int getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(int operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public double getLastDensity15() {
        return lastDensity15;
    }

    public void setLastDensity15(double lastDensity15) {
        this.lastDensity15 = lastDensity15;
    }

    public double getRemainQuantity() {
        return remainQuantity;
    }

    public void setRemainQuantity(double remainQuantity) {
        this.remainQuantity = remainQuantity;
    }

    public String getReleaseCertNo() {
        return releaseCertNo;
    }

    public void setReleaseCertNo(String releaseCertNo) {
        this.releaseCertNo = releaseCertNo;
    }

    public double getTankDensity15() {
        return tankDensity15;
    }

    public void setTankDensity15(double tankDensity15) {
        this.tankDensity15 = tankDensity15;
    }

    public double getLoadQuantity() {
        return loadQuantity;
    }

    public void setLoadQuantity(double loadQuantity) {
        this.loadQuantity = loadQuantity;
    }

    public double getAvgDensity() {
        return avgDensity;
    }

    public void setAvgDensity(double avgDensity) {
        this.avgDensity = avgDensity;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = density;
    }

    public double getDensity15() {
        return density15;
    }

    public void setDensity15(double density15) {
        this.density15 = density15;
    }

    public double getDensityDiff() {
        return densityDiff;
    }

    public void setDensityDiff(double densityDiff) {
        this.densityDiff = densityDiff;
    }
}
