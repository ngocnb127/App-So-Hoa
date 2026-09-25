package com.megatech.fms.model;

import java.util.Date;

// BM 25.06/NLHK - Biên bản lấy và giao nhận mẫu
public class BM2506Model extends BaseModel {

    private int truckId;
    private String sampleNo;            // 1. Mẫu số
    private Date time;                  // 2. Thời gian lấy mẫu
    private int operatorId;             // 3. Người lấy mẫu
    private String operatorName;
    private String place;               // 4. Địa điểm lấy mẫu
    private String location;            // 5. Vị trí (ngăn xe / vị trí)
    private String sampleType;          // 6. Loại mẫu
    private String grade = "JET A-1";   // 7. Chủng loại nhiên liệu
    private int flightId;               // 8. Số chuyến bay phục vụ
    private String flightCode;
    private String aircraftCode;
    private String retentionSealNo;     // 9. Số niêm phong mẫu lưu
    private String deliverySealNo;      // 10. Số niêm phong mẫu giao cho khách hàng
    private String delivererName;       // Người giao mẫu
    private String recipientName;       // Người nhận mẫu

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    public String getSampleNo() {
        return sampleNo;
    }

    public void setSampleNo(String sampleNo) {
        this.sampleNo = sampleNo;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
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

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
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

    public String getRetentionSealNo() {
        return retentionSealNo;
    }

    public void setRetentionSealNo(String retentionSealNo) {
        this.retentionSealNo = retentionSealNo;
    }

    public String getDeliverySealNo() {
        return deliverySealNo;
    }

    public void setDeliverySealNo(String deliverySealNo) {
        this.deliverySealNo = deliverySealNo;
    }

    public String getDelivererName() {
        return delivererName;
    }

    public void setDelivererName(String delivererName) {
        this.delivererName = delivererName;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }
}
