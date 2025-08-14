package com.megatech.fms.model;

import java.util.Date;

public class BM2508Model extends BaseModel {

    private Integer AirportId = 0;
    private Integer FlightId = 0;
    private Integer TruckId = 0;
    private Integer StaffId = 0;
    private String FlightNo;
    private String AircraftType;
    private String Unit;
    private String Note;
    private String AirlinesRepresentative;
    private Date DateCreated;
    private Date Time;
    public Boolean  Cover1;
    public Boolean  Cover2;
    public Boolean  Cover3;
    public Boolean  Selects;
    public Boolean  Automatic;
    public Boolean  Other;
    private double BeforeFueling;
    private double FuelOnBoard;
    private double FuelUplift;
    private double FuelOnBoardGallon;
    private String airportName;
    private String truckNo;
    private String StaffName;
    private String FlightCode;
    private boolean captured = false;
    private String pdfPath;
    private String UserSkypecSignaturePath;
    private String number;
    private String AirlineSignaturePath;
    private String TextAirlineSignature;
    private String TextUserSkypecSignature;



    private String UrlImageAirline;



    private String UrlImageSkypec;

    public String getUrlImageAirline() {
        return UrlImageAirline;
    }

    public void setUrlImageAirline(String urlImageAirline) {
        UrlImageAirline = urlImageAirline;
    }

    public String getUrlImageSkypec() {
        return UrlImageSkypec;
    }

    public void setUrlImageSkypec(String urlImageSkypec) {
        UrlImageSkypec = urlImageSkypec;
    }


    public String getTextUserSkypecSignature() {
        return TextUserSkypecSignature;
    }

    public void setTextUserSkypecSignature(String TextUserSkypecSignature) {
        this.TextUserSkypecSignature = TextUserSkypecSignature;
    }
    public String getTextAirlineSignature() {
        return TextAirlineSignature;
    }

    public void setTextAirlineSignature(String TextAirlineSignature) {
        this.TextAirlineSignature = TextAirlineSignature;
    }
    public String getAirlineSignaturePath() {
        return AirlineSignaturePath;
    }

    public void setAirlineSignaturePath(String AirlineSignaturePath) {
        this.AirlineSignaturePath = AirlineSignaturePath;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
    public String getUserSkypecSignaturePath() {
        return UserSkypecSignaturePath;
    }
    public void setUserSkypecSignaturePath(String UserSkypecSignaturePath) {
        this.UserSkypecSignaturePath = UserSkypecSignaturePath;
    }
    public String getPdfPath() {
        return pdfPath;
    }
    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }
    public double getFuelUplift() {
        return FuelUplift;
    }

    public void setFuelUplift(double FuelUplift) {
        this.FuelUplift = FuelUplift;
    }
    public String getStaffName() {
        return StaffName;
    }

    public void setStaffName(String StaffName) {
        this.StaffName = StaffName;
    }
    public static class ResultModel{
        public String Name;

        public ResultModel(String Name) {
            this.Name = Name; // Gán giá trị cho biến Name
        }

        // Getter and Setter methods for Name
        public String getName() {
            return Name;
        }

        public void setName(String name) {
            this.Name = name;
        }
        public String toString()
        {
            return this.Name;
        }
    }
    public boolean isCaptured() {
        return captured;
    }

    public void setCaptured(boolean captured) {
        this.captured = captured;
    }
    public String getTruckNo() {
        return truckNo;
    }
    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }
    public String getAirportName() {
        return airportName;
    }
    public void setAirportName(String airportName) {
        this.airportName = airportName;
    }
    public Integer getAirportId() {
        return AirportId;
    }
    public void setAirportId(Integer AirportId) {
        this.AirportId = AirportId;
    }
    public Integer getFlightId() {
        return FlightId;
    }
    public void setFlightId(Integer FlightId) {
        this.FlightId = FlightId;
    }
    public String getFlightCode() {
        return FlightCode;
    }
    public void setFlightCode(String FlightCode) {
        this.FlightCode = FlightCode;
    }
    public Date getDateCreated() {
        return DateCreated;
    }
    public void setDateCreated(Date DateCreated) {
        this.DateCreated = DateCreated;
    }

    public Date getTime() {
        return Time;
    }
    public void setTime(Date Time) {
        this.Time = Time;
    }
    public Integer getTruckId() {
        return TruckId;
    }
    public void setTruckId(Integer TruckId) {
        this.TruckId = TruckId;
    }
    public Integer getStaffId() {
        return StaffId;
    }
    public void setStaffId(Integer StaffId) {
        this.StaffId = StaffId;
    }

    public String getFlightNo() {
        return FlightNo;
    }

    public void setFlightNo(String FlightNo) {
        this.FlightNo = FlightNo;
    }

    public String getAircraftType() {
        return AircraftType;
    }
    public void setAircraftType(String AircraftType) {
        this.AircraftType = AircraftType;
    }
     public String getUnit() {
        return Unit;
    }
    public void setUnit(String Unit) {
        this.Unit = Unit;
    }
     public String getNote() {
        return Note;
    }
    public void setNote(String Note) {
        this.Note = Note;
    }
     public String getAirlinesRepresentative() {
        return AirlinesRepresentative;
    }
    public void setAirlinesRepresentative(String AirlinesRepresentative) {
        this.AirlinesRepresentative = AirlinesRepresentative;
    }

    public Boolean getCover1() {
        return Cover1;
    }
    public void setCover1(Boolean Cover1) {
        this.Cover1 = Cover1;
    }

    public Boolean getCover2() {
        return Cover2;
    }
    public void setCover2(Boolean Cover2) {
        this.Cover2 = Cover2;
    }
    public Boolean getCover3() {
        return Cover3;
    }
    public void setCover3(Boolean Cover3) {
        this.Cover3 = Cover3;
    }
    public Boolean getSelects() {
        return Selects;
    }
    public void setSelects(Boolean Selects) {
        this.Selects = Selects;
    }

    public Boolean getAutomatic() {
        return Automatic;
    }
    public void setAutomatic(Boolean Automatic) {
        this.Automatic = Automatic;
    }
    public Boolean getOther() {
        return Other;
    }
    public void setOther(Boolean Other) {
        this.Other = Other;
    }

    public double getBeforeFueling() {
        return BeforeFueling;
    }

    public void setBeforeFueling(double BeforeFueling) {
        this.BeforeFueling = BeforeFueling;
    }

    public double getFuelOnBoard() {
        return FuelOnBoard;
    }

    public void setFuelOnBoard(double FuelOnBoard) {
        this.FuelOnBoard = FuelOnBoard;
    }
    public double getFuelOnBoardGallon() {
        return FuelOnBoardGallon;
    }
    public void setFuelOnBoardGallon(double FuelOnBoardGallon) {
        this.FuelOnBoardGallon = FuelOnBoardGallon;
    }

    // Thêm đường dẫn lưu local
    private String airlineSignatureGalleryPath;
    private String userSkypecSignatureGalleryPath;

    // Getter và Setter
    public String getAirlineSignatureGalleryPath() {
        return airlineSignatureGalleryPath;
    }

    public void setAirlineSignatureGalleryPath(String airlineSignatureGalleryPath) {
        this.airlineSignatureGalleryPath = airlineSignatureGalleryPath;
    }

    public String getUserSkypecSignatureGalleryPath() {
        return userSkypecSignatureGalleryPath;
    }

    public void setUserSkypecSignatureGalleryPath(String userSkypecSignatureGalleryPath) {
        this.userSkypecSignatureGalleryPath = userSkypecSignatureGalleryPath;
    }


}
