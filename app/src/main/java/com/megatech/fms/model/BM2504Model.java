package com.megatech.fms.model;

import com.google.gson.annotations.SerializedName;
import com.megatech.fms.FMSApplication;

import java.io.Serializable;
import java.util.Date;

public class BM2504Model extends BaseModel implements Serializable {

    /* ================= BASIC ================= */

    @SerializedName("AirportId")
    private Integer airportId;
    private String airportName;

    private Integer truckId;
    private String truckNo;

    private Integer flightId;
    private String flightNo;

    private Integer airlineId;
    private String airlineName;

    @SerializedName("ACReg")
    private String acReg;

    private String router;
    private String packing;

    private String buyerName;
    private String buyerPosition;

    /* ================= SIGN ================= */

    private String signPicture;     // base64 / local
    private String signPictureUrl;  // server path

    /* ================= TIME ================= */

    private Date date;
    private Date time;

    /* ================= BUSINESS ================= */

    /**
     * 1 = Tra nạp khi hành khách đang trên tàu bay
     * 2 = Trường hợp khác
     */
    private Integer chooseLevel;

    private String chooseNote;

    /* ================= GET / SET ================= */

    public Integer getAirportId() {
        return airportId;
    }

    public void setAirportId(Integer airportId) {
        this.airportId = airportId;
    }

    public String getAirportName() {
        return airportName;
    }

    public void setAirportName(String airportName) {
        this.airportName = airportName;
    }

    public Integer getTruckId() {
        return truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public String getTruckNo() {
        return truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
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

    public Integer getAirlineId() {
        return airlineId;
    }

    public void setAirlineId(Integer airlineId) {
        this.airlineId = airlineId;
    }

    public String getAirlineName() {
        return airlineName;
    }

    public void setAirlineName(String airlineName) {
        this.airlineName = airlineName;
    }

    public String getAcReg() {
        return acReg;
    }

    public void setAcReg(String acReg) {
        this.acReg = acReg;
    }

    public String getRouter() {
        return router;
    }

    public void setRouter(String router) {
        this.router = router;
    }

    public String getPacking() {
        return packing;
    }

    public void setPacking(String packing) {
        this.packing = packing;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerPosition() {
        return buyerPosition;
    }

    public void setBuyerPosition(String buyerPosition) {
        this.buyerPosition = buyerPosition;
    }

    public String getSignPicture() {
        return signPicture;
    }

    public void setSignPicture(String signPicture) {
        this.signPicture = signPicture;
    }

    public String getSignPictureUrl() {
        return signPictureUrl;
    }

    public void setSignPictureUrl(String signPictureUrl) {
        this.signPictureUrl = signPictureUrl;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public Integer getChooseLevel() {
        return chooseLevel;
    }

    public void setChooseLevel(Integer chooseLevel) {
        this.chooseLevel = chooseLevel;
    }

    public String getChooseNote() {
        return chooseNote;
    }

    public void setChooseNote(String chooseNote) {
        this.chooseNote = chooseNote;
    }

    /* ================= TIỆN ÍCH ================= */

    public String getChooseLevelText() {
        if (chooseLevel == null) return "";
        return chooseLevel == 1
                ? "Tra nạp khi hành khách đang trên tàu bay"
                : "Trường hợp khác";
    }

    /* ================= CONTEXT ================= */

    transient UserInfo user =
            FMSApplication.getApplication().getUser();

    transient TruckModel setting =
            FMSApplication.getApplication().getSetting();
}
