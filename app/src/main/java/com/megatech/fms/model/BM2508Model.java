package com.megatech.fms.model;

import java.util.Date;

public class BM2508Model extends BaseModel {
    public static final double GALLON_TO_LITTER = 3.7854;
    // ===== Density (NGHIỆP VỤ CHUẨN) =====
    private double density;   // giá trị density đang dùng
    private DensitySource densitySource;

    // ===== Enum =====
    public enum DensitySource {
        LATEST,        // lấy từ LCR / cấu hình mới nhất
        DEFAULT_08     // fallback 0.8
    }

    // ===== Getter =====
    public double getDensity() {
        return density;
    }

    public DensitySource getDensitySource() {
        return densitySource;
    }

    // ===== Setter NGHIỆP VỤ =====
    public void applyLatestDensity(double latestDensity) {

        // ✅ Density Jet A-1 hợp lệ ~0.75–0.85
        if (latestDensity >= 0.7 && latestDensity <= 0.9) {
            this.density = latestDensity;
            this.densitySource = DensitySource.LATEST;
        } else {
            applyDefaultDensity();
        }
    }



    public void applyDefaultDensity() {
        this.density = 0.8;
        this.densitySource = DensitySource.DEFAULT_08;
    }
    // ===== Enum =====

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

    // 🔹 Bổ sung mới (mục 6)
    private Boolean Section6_1;
    private Boolean Section6_2;
    private Boolean Section6_3;
    private Boolean Section6_4;
    private Boolean Section6_5;
    private Boolean Section6_6;

    private String UrlImageAirline;



    private String UrlImageSkypec;


    // --- Getter & Setter ---
    public Boolean getSection6_1() { return Section6_1; }
    public void setSection6_1(Boolean section6_1) { Section6_1 = section6_1; }

    public Boolean getSection6_2() { return Section6_2; }
    public void setSection6_2(Boolean section6_2) { Section6_2 = section6_2; }

    public Boolean getSection6_3() { return Section6_3; }
    public void setSection6_3(Boolean section6_3) { Section6_3 = section6_3; }

    public Boolean getSection6_4() { return Section6_4; }
    public void setSection6_4(Boolean section6_4) { Section6_4 = section6_4; }

    public Boolean getSection6_5() { return Section6_5; }
    public void setSection6_5(Boolean section6_5) { Section6_5 = section6_5; }

    public Boolean getSection6_6() { return Section6_6; }
    public void setSection6_6(Boolean section6_6) { Section6_6 = section6_6; }

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
        recalcFuelOnBoardGallon();
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

    public void recalcFuelOnBoardGallon() {

        if (!"KGs".equalsIgnoreCase(Unit)) {
            return;
        }

        if (FuelUplift <= 0) {
            FuelOnBoardGallon = 0;
            return;
        }

        // ⚠️ chỉ fallback khi density CHƯA BAO GIỜ được set
        if (density <= 0 && densitySource == null) {
            applyDefaultDensity();
        }

        double volumeLiter = Math.round(FuelUplift / density);
        FuelOnBoardGallon = Math.round(volumeLiter / GALLON_TO_LITTER);
    }


    // ===== UI helper cho DataBinding =====
    public boolean isUseLatestDensity() {
        return densitySource == DensitySource.LATEST;
    }

    public boolean isUseDefaultDensity() {
        return densitySource == DensitySource.DEFAULT_08;
    }
    // ===== UI helper cho DataBinding =====
    public String getDensityDisplayText() {

        if (densitySource == DensitySource.LATEST) {
            return String.format(
                    "Đang áp dụng tỷ trọng mới nhất (%.3f)",
                    density
            );
        }

        return "Đang áp dụng tỷ trọng mặc định (0.800)";
    }




}
