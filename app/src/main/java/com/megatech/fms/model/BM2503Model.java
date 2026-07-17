package com.megatech.fms.model;



import com.google.gson.annotations.SerializedName;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.helpers.DateUtils;

import java.io.Serializable;
import java.util.Date;

public class BM2503Model extends BaseModel implements Serializable {

    @SerializedName("AirportId")
    private Integer airportId;
    private String airportName;   // ✅ TÊN SÂN BAY

    private Integer truckId;
    private String truckNo;       // ✅ SỐ XE

    private Integer flightId;

    private String flightNo;
    @SerializedName("ACReg")
    private String AcReg;
    private String router;

    private Integer productId;
    private Double value;

    /**
     * Đơn vị:
     * 1 = Lít
     * 2 = Kg
     * 3 = Gallon
     */
    private Integer unit;

    private String buyerName;

    /**
     * Base64 hoặc path ảnh chữ ký
     */
    private String signPicture;
    private String signPictureUrl;

    private Date date;

    // ================= GET / SET =================

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

    public String getAcReg() {
        return AcReg;
    }

    public void setAcReg(String AcReg) {
        this.AcReg = AcReg;
    }

    public String getRouter() {
        return router;
    }

    public void setRouter(String router) {
        this.router = router;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Integer getUnit() {
        return unit;
    }

    public void setUnit(Integer unit) {
        this.unit = unit;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getSignPicture() {
        return signPicture;
    }



    public void setSignPicture(String signPicture) {
        this.signPicture = signPicture;
    }
    public void setSignPictureUrl(String signPictureUrl) {
        this.signPictureUrl = signPictureUrl;
    }
    public String getSignPictureUrl() {
        return signPictureUrl;
    }
    private Date time;
    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }



    // ================= TIỆN ÍCH =================

    public String getUnitText() {
        if (unit == null) return "";
        switch (unit) {
            case 1:
                return "Lít";
            case 2:
                return "Kg";
            case 3:
                return "Gallon";
            default:
                return "";
        }
    }


    private String pCode;
    private String pName;
    private String productName;
    public String getPCode() {
        return pCode != null ? pCode : "";
    }

    public String getPName() {
        return pName != null ? pName : "";
    }

    public String getProductName() {
        return productName != null ? productName : "";
    }
    public void setPCode(String pCode) {
        this.pCode = pCode;
    }

    public void setPName(String pName) {
        this.pName = pName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    transient UserInfo user = FMSApplication.getApplication().getUser();
    transient TruckModel setting = FMSApplication.getApplication().getSetting();

    public String createThermalBM2503Text() {

        UserInfo user = FMSApplication.getApplication().getUser();
        TruckModel setting = FMSApplication.getApplication().getSetting();

        StringBuilder builder = new StringBuilder();
        int height = 80;

        String LEFT_INDENT = setting.getThermalPrinterType()
                == TruckModel.THERMAL_PRINTER_TYPE.ZQ520
                ? "^LH130,0\n"
                : "^LH000,0\n";

        builder.append("^XA");
        builder.append("^CWZ,E:OPENSANS-RE.TTF^FS\n");
        builder.append(LEFT_INDENT);
        builder.append("^CI28\n");

    /* =========================
       TITLE
       ========================= */

        builder.append("^CFZ,30\n");
        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FDPHIẾU YÊU CẦU TRA NẠP TRÊN CÁNH^FS\n");
        height += 42;

        builder.append("^CFZ,26\n");
        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FD(JET FUEL REQUEST FORM FOR OVERWING)^FS\n");
        height += 36;

    /* =========================
       TO / AIRPORT
       ========================= */

        builder.append("^CFZ,20\n");
        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FDTới / To: Chi nhánh^FS\n");
        height += 28;

        builder.append("^FO0," + height + "^GB700,1,3^FS\n");
        height += 12;

        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FDSân bay: " + airportName + "^FS\n");
        height += 28;

        builder.append("^FO0," + height + "^GB700,1,3^FS\n");
        height += 20;

    /* =========================
       FLIGHT INFO
       ========================= */

        builder.append("^CFZ,22\n");

        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDFlight No^FS\n");
        builder.append("^FO250," + height + "^FB310,1,0,R,0^FD" + flightNo + "^FS\n");
        height += 30;

        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDAircraft Regn.No^FS\n");
        builder.append("^FO250," + height + "^FB310,1,0,R,0^FD" + nvl(AcReg )+ "^FS\n");
        height += 30;

        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDRoute^FS\n");
        builder.append("^FO250," + height + "^FB310,1,0,R,0^FD" + router + "^FS\n");
        height += 30;

        builder.append("^FO0," + height + "^GB700,1,3^FS\n");
        height += 20;

    /* =========================
       REQUIREMENT PARAGRAPH (FIX)
       ========================= */

        builder.append("^CFZ,22\n");

// Dòng tiếng Việt
        builder.append(
                "^FO20," + height +
                        "^FB660,1,0,L,0^FDCác yêu cầu nạp nhiên liệu hàng không cho tàu bay^FS\n"
        );
        height += 32;
        builder.append(
                "^FO20," + height +
                        "^FB660,1,0,L,0^FD này như sau/The aviation Fuel requirements ^FS\n"
        );
        height += 32;

// Dòng tiếng Anh
        builder.append(
                "^FO20," + height +
                        "^FB660,1,0,L,0^FD for this aircraft are as follows:^FS\n"
        );
        height += 32;

    /* =========================
   FUEL REQUIREMENT TABLE (70% WIDTH, HEADER 250%)
   ========================= */

   /* =========================
   FUEL REQUIREMENT TABLE
   ========================= */

        int tableTop = height + 10;

// Base heights
        int rowHeight = 42;
        int headerHeight = (int)(rowHeight * 3);   // header 250%
        int contentRowHeight = rowHeight + 20;       // hàng content cao hơn (2 dòng)


// Widths
        int colWidth = 150;                          // cột thường (70%)
        int qtyColWidth = (int)(colWidth * 1.7);     // cột số lượng +30%

        int col1X = 10;
        int col2X = col1X + colWidth + 10;
        int col3X = col2X + colWidth + 10;

        int tableWidth = colWidth * 2 + qtyColWidth + 20;

// Value as INT
        int valueInt = (int) Math.round(value);

        /* ===== OUTER BORDER ===== */
        builder.append("^FO0," + tableTop +
                "^GB" + tableWidth + "," +
                (headerHeight + contentRowHeight) + ",2^FS\n");

        /* ===== VERTICAL LINES ===== */
        builder.append("^FO" + (col2X - 5) + "," + tableTop +
                "^GB2," + (headerHeight + contentRowHeight) + ",2^FS\n");

        builder.append("^FO" + (col3X - 5) + "," + tableTop +
                "^GB2," + (headerHeight + contentRowHeight) + ",2^FS\n");

        /* ===== HORIZONTAL LINES ===== */
        builder.append("^FO0," + (tableTop + headerHeight) +
                "^GB" + tableWidth + ",2,2^FS\n");

/* =========================
   HEADER
   ========================= */

        builder.append("^CFZ,22\n");

// Column 1
        builder.append("^FO" + col1X + "," + (tableTop + 10) +
                "^FB" + colWidth + ",3,6,C,0^FDYêu cầu nhiên liệu cấp^FS\n");
        builder.append("^FO" + col1X + "," + (tableTop + 70) +
                "^FB" + colWidth + ",1,0,C,0^FDFuel^FS\n");

// requirements
        builder.append("^FO" + col1X + "," + (tableTop + 88) +
                "^FB" + colWidth + ",1,0,C,0^FDrequirements^FS\n");

// Column 2
        builder.append("^FO" + col2X + "," + (tableTop + 10) +
                "^FB" + colWidth + ",3,6,C,0^FDLoại nhiên liệu^FS\n");
        builder.append("^FO" + col2X + "," + (tableTop + 70) +
                "^FB" + colWidth + ",1,0,C,0^FDFuel Grade^FS\n");

// Column 3 (wider)
        builder.append("^FO" + col3X + "," + (tableTop + 10) +
                "^FB" + qtyColWidth + ",3,6,C,0^FDSố lượng yêu cầu^FS\n");
        builder.append("^FO" + col3X + "," + (tableTop + 70) +
                "^FB" + qtyColWidth + ",1,0,C,0^FDQuantity required^FS\n");

/* =========================
   CONTENT
   ========================= */

// Product name (JET A-1)
        builder.append("^CFZ,28\n");
        builder.append("^FO" + col2X + "," + (tableTop + headerHeight + 10) +
                "^FB" + colWidth + ",1,0,C,0^FD" + productName + "^FS\n");

// Aviation turbine (line 1)
        builder.append("^CFZ,20\n");
        builder.append("^FO" + col2X + "," + (tableTop + headerHeight + 34) +
                "^FB" + colWidth + ",1,0,C,0^FDAviation turbine^FS\n");

// Kerosene (line 2)
        builder.append("^FO" + col2X + "," + (tableTop + headerHeight + 58) +
                "^FB" + colWidth + ",1,0,C,0^FDKerosene^FS\n");

// Quantity (align with multi-line content)
        builder.append("^CFZ,28\n");
        builder.append("^FO" + col3X + "," + (tableTop + headerHeight + 28) +
                "^FB" + (qtyColWidth - 30) + ",1,0,R,0^FD"
                + valueInt + " " + getUnitText()
                + "^FS\n");

        /* ===== UPDATE HEIGHT ===== */
        height = tableTop + headerHeight + contentRowHeight + 60;




    /* =========================
       SIGNATURE
       ========================= */
        builder.append("^FO0," + height + "^GB700,1,3^FS\n");
        height += 20;
        builder.append("^CFZ,30\n");
        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FDĐẠI DIỆN HÃNG^FS\n");
        height += 40;
        builder.append("^FO0," + height +
                "^FB600,1,0,C,0^FD"+nvl(buyerName)+"^FS\n");
        height += 60;



        if (signPictureUrl != null) {
            builder.append("^FO150," + height + "^XGE:BUYER.GRF,1,1^FS\n");
            height += 120;
        }

        /* =========================
   FOOTER - FORM INFO
   ========================= */
        height +=30;
        builder.append("^CFZ,18\n");

// Dòng 1
        builder.append("^FO20," + height +
                "^FB660,1,0,L,0^FDBM 25.03/NLHK^FS\n");
        height += 22;

// Dòng 2
        builder.append("^FO20," + height +
                "^FB660,1,0,L,0^FDBan hành/sửa đổi: 13/04^FS\n");
        height += 30;


        builder.append("^PQ1\n");
        builder.append("^LH0,0\n");
        builder.append("^XZ\n");

        // ⚠️ LABEL LENGTH DƯ AN TOÀN
        builder.insert(3, "^LL" + (height + 350) + "\n");

        return builder.toString();
    }
    private String nvl(String s) {
        return s == null ? "" : s;
    }




}
