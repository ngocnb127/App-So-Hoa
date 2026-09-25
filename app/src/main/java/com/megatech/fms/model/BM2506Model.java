package com.megatech.fms.model;

import java.util.Date;

// BM 25.06/NLHK - Biên bản lấy và giao nhận mẫu (API_DOC_AppSoHoa mục 4.5, tên trường theo BM2506Model của API)
// Id, UniqueId, IsDeleted nằm ở BaseModel. POST là ghi đè toàn bộ phiếu -> luôn gửi đủ các trường.
public class BM2506Model extends BaseModel {

    public static final String[] SAMPLE_TYPES = {"Mẫu lưu", "Mẫu giao khách hàng", "Mẫu thử nghiệm"};

    private Integer airportId;          // thiếu -> server tự suy theo xe/chuyến bay
    private Integer truckId;
    private Integer flightId;
    private String sampleNo;            // 1. Mẫu số
    private Date date;                  // 2. Thời gian lấy mẫu
    private String takenBy;             // 3. Người lấy mẫu
    private String place;               // 4. Địa điểm lấy mẫu
    private String location;            // 5. Vị trí / khoang xe
    private String sampleType;          // 6. Loại mẫu
    private String grade = "JET A-1";   // 7. Chủng loại nhiên liệu
    private String flightNo;            // 8. Số chuyến bay (rỗng + có FlightId -> server lấy Flight.Code)
    private String retentionSealNo;     // 9. Số niêm phong mẫu lưu
    private String deliveringSealNo;    // 10. Số niêm phong mẫu giao khách hàng
    private String delivererName;       // Người giao mẫu
    private String recipientName;       // Người nhận mẫu

    // chỉ đọc (server trả về)
    private String truckCode;
    private String flightCode;

    // chỉ dùng trên app: Message của lần đồng bộ lỗi gần nhất (400/403/404)
    private String syncError;

    public static BM2506Model fromJson(String json) {
        return gson.fromJson(json, BM2506Model.class);
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

    public String getSampleNo() {
        return sampleNo;
    }

    public void setSampleNo(String sampleNo) {
        this.sampleNo = sampleNo;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getTakenBy() {
        return takenBy;
    }

    public void setTakenBy(String takenBy) {
        this.takenBy = takenBy;
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

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public String getRetentionSealNo() {
        return retentionSealNo;
    }

    public void setRetentionSealNo(String retentionSealNo) {
        this.retentionSealNo = retentionSealNo;
    }

    public String getDeliveringSealNo() {
        return deliveringSealNo;
    }

    public void setDeliveringSealNo(String deliveringSealNo) {
        this.deliveringSealNo = deliveringSealNo;
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
