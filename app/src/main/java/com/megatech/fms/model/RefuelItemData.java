package com.megatech.fms.model;

import com.google.gson.annotations.SerializedName;
import com.megatech.fms.BuildConfig;
import com.megatech.fms.R;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.enums.RETURN_UNIT;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class RefuelItemData extends BaseModel implements Cloneable {

    public static double GALLON_TO_LITTER = 3.7854;
    private Integer flightId = 0;
    private String flightCode;
    private String aircraftType;
    private String aircraftCode;
    private String parkingLot;
    private Date refuelTime;
    private double estimateAmount;
    private double realAmount;
    private double temperature;
    private Date endTime = new Date();
    private Date startTime = new Date();
    private Date deviceStartTime = null;
    private Date deviceEndTime = null;
    private Date arrivalTime;
    private Date departureTime;
    private double startNumber;
    private double endNumber;
    private double manualTemperature;
    private Integer userId = 0;
    private Integer truckId = 0;
    private double density;
    private int airlineId = 0;
    private double price;

    //private double weight;
    private double volume;
    private int unit;
    private boolean isInternational;
    private String routeName;
    private AirlineModel airlineModel;
    private String productName;
    private String qualityNo;
    private double taxRate;
    private ITEM_PRINT_STATUS printStatus = ITEM_PRINT_STATUS.NONE;
    private ITEM_POST_STATUS postStatus = ITEM_POST_STATUS.SUCCESS;
    private double originalEndMeter;
    private REFUEL_ITEM_STATUS status;
    private boolean completed;
    private boolean printed;
    private double gallon;
    private String truckNo;
    private List<RefuelItemData> others = new ArrayList<>();
    private FLIGHT_STATUS flightStatus;
    private REFUEL_ITEM_TYPE refuelItemType;
    private String invoiceNumber;
    private String invoiceNameCharter;
    private String returnInvoiceNumber;
    private double returnAmount;
    private RETURN_UNIT returnUnit = RETURN_UNIT.KG;

    private String weightNote;
    private CURRENCY currency;
    private int driverId;
    private String driverName;
    private int operatorId;
    private String operatorName;
    private boolean isAlert = false;

    private INVOICE_TYPE printTemplate;
    private int changeFlag;
    private int invoiceFormId;
    private String formNo;
    private String sign;
    private Integer bM2508Result;
    private boolean BM2508BondingCable;
    private boolean BM2508FuelingHose;
    private boolean BM2508FuelingCap;
    private boolean BM2508Ladder;

    //private InvoiceModel invoiceModel;

    private String receiptUniqueId;

    public String getReceiptUniqueId() {
        return receiptUniqueId;
    }

    public void setReceiptUniqueId(String receiptUniqueId) {
        this.receiptUniqueId = receiptUniqueId;
    }

    private String receiptNumber;

    public String getReceiptNumber() {
        return receiptNumber;
    }




    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    private int receiptCount = 0;

    public int getReceiptCount() {
        return receiptCount;
    }

    public void setReceiptCount(int receiptCount) {
        this.receiptCount = receiptCount;
    }
    private boolean hasReview;

    public boolean hasReview() {
        return hasReview;
    }

    public void setHasReview(boolean hasReview) {
        this.hasReview = hasReview;
    }

    public int getInvoiceStatus()
    {
        if (invoiceNumber != null && !invoiceNumber.isEmpty())
            return 0;
        else if (receiptCount>0)
            return  1;
        else
            return  2;
    }

    public RefuelItemData() {
        Calendar c = Calendar.getInstance();

        refuelTime = new Date();
        c.setTime(refuelTime);
        c.add(Calendar.MINUTE, 15);

        departureTime = c.getTime();
        c.add(Calendar.MINUTE, -30);
        arrivalTime = c.getTime();
        status = REFUEL_ITEM_STATUS.NONE;
        uniqueId = UUID.randomUUID().toString();
    }

    public static RefuelItemData fromJson(String jsonData) {
        RefuelItemData item = gson.fromJson(jsonData, RefuelItemData.class);
        if (item.getUniqueId() == null || item.getUniqueId().isEmpty())
            item.setUniqueId( UUID.randomUUID().toString());
        return  item;
    }

    public RefuelItemData copy()
    {
        try {
            RefuelItemData itemData =  (RefuelItemData) clone();
            itemData.setId(0);
            itemData.setLocalId(0);
            itemData.setUniqueId( UUID.randomUUID().toString());
            itemData.setReceiptCount(0);
            itemData.setReceiptNumber(null);
            itemData.setInvoiceNumber(null);
            itemData.setStartNumber(0);
            itemData.setEndNumber(0);
            itemData.setStartTime(new Date());
            itemData.setEndTime(new Date());
            itemData.setReturnAmount(0);

            itemData.setPrinted(false);
            itemData.setPrintStatus(ITEM_PRINT_STATUS.NONE);

            itemData.setLocalModified(false);
            itemData.setRealAmount(0);
            itemData.setGallon(0);
            itemData.setVolume(0);

            return itemData;
        }
        catch (Exception ex)
        {
            return  null;
        }

    }
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isInternational() {
        return isInternational;
    }

    public void setInternational(boolean international) {
        isInternational = international;
    }

    public int getUnit() {
        return unit;
    }

    public void setUnit(int unit) {
        this.unit = unit;
    }

    public double getVolume() {
        if (BuildConfig.FHS)
            return volume;
        else
            return Math.round(Math.round(realAmount) * RefuelItemData.GALLON_TO_LITTER);
    }

    public void setVolume(double val)
    {
        volume = Math.round(val);
    }

    public double getWeight() {
        return Math.round(density * getVolume());
    }

    public int getAirlineId() {
        return airlineId;
    }

    public void setAirlineId(int airlineId) {
        this.airlineId = airlineId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
    }

    public double getVATAmount() {
        return getAmount() * getTaxRate();
    }

    public double getTotalAmount() {
        return getAmount() + getVATAmount();
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = density;
    }

    public double getManualTemperature() {
        return manualTemperature;
    }

    public void setManualTemperature(double manualTemparature) {
        this.manualTemperature = manualTemparature;
    }

    public ITEM_PRINT_STATUS getPrintStatus() {
        return printStatus;
    }

    public void setPrintStatus(ITEM_PRINT_STATUS printStatus) {
        this.printStatus = printStatus;
        this.printed = this.printStatus == ITEM_PRINT_STATUS.SUCCESS;
    }

    public ITEM_POST_STATUS getPostStatus() {
        return postStatus;
    }

    public void setPostStatus(ITEM_POST_STATUS postStatus) {
        this.postStatus = postStatus;
    }

    public double getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(double startNumber) {
        this.startNumber = startNumber;
    }

    public double getEndNumber() {
        return endNumber;
    }

    public void setEndNumber(double endNumber) {
        this.endNumber = endNumber;
    }

    public double getOriginalEndMeter() {
        return originalEndMeter;
    }

    public void setOriginalEndMeter(double originalEndMeter) {
        this.originalEndMeter = originalEndMeter;
    }

    public Integer getbM2508Result() {
        return bM2508Result;
    }

    public void setbM2508Result(Integer bM2508Result) {
        this.bM2508Result = bM2508Result;
    }

    public boolean isBM2508BondingCable() {
        return BM2508BondingCable;
    }

    public boolean isBM2508FuelingHose() {
        return BM2508FuelingHose;
    }

    public boolean isBM2508FuelingCap() {
        return BM2508FuelingCap;
    }

    public boolean isBM2508Ladder() {
        return BM2508Ladder;
    }

    public Date getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Date arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Date getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Date departureTime) {
        this.departureTime = departureTime;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /*public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }*/

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startTime);

        if (calendar.get(Calendar.YEAR) < 9999)
            this.startTime = startTime;
    }

    public REFUEL_ITEM_STATUS getStatus() {
        return status;
    }

    public void setStatus(REFUEL_ITEM_STATUS status) {
        this.status = status;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getParkingLot() {
        return parkingLot;
    }

    public void setParkingLot(String parkingLot) {
        this.parkingLot = parkingLot;
    }

    public Date getRefuelTime() {
        return refuelTime;
    }

    public void setRefuelTime(Date refuelTime) {
        this.refuelTime = refuelTime;
    }

    public double getEstimateAmount() {
        return estimateAmount;
    }

    public void setEstimateAmount(double estimateAmount) {
        this.estimateAmount = estimateAmount;
    }

    public double getRealAmount() {
        return realAmount;
    }

    public void setRealAmount(double realAmount) {
        this.realAmount = Math.round(realAmount);
        this.gallon = this.realAmount;
        if (!BuildConfig.FHS) {

            this.volume = Math.round(this.realAmount * GALLON_TO_LITTER);
        }
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(Integer flightId) {
        this.flightId = flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public AirlineModel getAirlineModel() {
        return airlineModel;
    }

    public void setAirlineModel(AirlineModel airlineModel) {
        this.airlineModel = airlineModel;
    }

    public String getProductName() {
        return this.productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getQualityNo() {
        return qualityNo;
    }

    public void setQualityNo(String qcNo) {
        this.qualityNo = qcNo;
    }

    public Date getDeviceStartTime() {
        return deviceStartTime;
    }

    public void setDeviceStartTime(Date deviceStartTime) {
        this.deviceStartTime = deviceStartTime;
    }

    public Date getDeviceEndTime() {
        return deviceEndTime;
    }

    public void setDeviceEndTime(Date deviceEndTime) {
        this.deviceEndTime = deviceEndTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isPrinted() {
        return printed;
    }

    public void setPrinted(boolean printed) {
        this.printed = printed;
        this.printStatus = this.printed ? ITEM_PRINT_STATUS.SUCCESS : ITEM_PRINT_STATUS.NONE;
    }

    public INVOICE_TYPE getPrintTemplate() {
        return printTemplate;
    }

    public void setPrintTemplate(INVOICE_TYPE printTemplate) {
        this.printTemplate = printTemplate;
    }

    public double getAmount() {
        int precise = this.currency == CURRENCY.VND ? 1 : 100;
        if (unit == 0)
            return (double) Math.round(getGallon() * getPrice() * precise) / precise;
        return (double) Math.round(getWeight() * getPrice() * precise) / precise;
    }

    public String getTruckNo() {
        return this.truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }

    public List<RefuelItemData> getOthers() {
        return others;
    }

    public void setOthers(List<RefuelItemData> others) {

        if (others != null)
            this.others = others;
        else this.others = new ArrayList<>();
    }

    public FLIGHT_STATUS getFlightStatus() {
        return flightStatus;
    }

    public void setFlightStatus(FLIGHT_STATUS flightStatus) {
        this.flightStatus = flightStatus;
    }

    public REFUEL_ITEM_TYPE getRefuelItemType() {
        return refuelItemType;
    }

    public void setRefuelItemType(REFUEL_ITEM_TYPE refuelItemType) {
        this.refuelItemType = refuelItemType;
    }

    public String getInvoiceNameCharter() {

        return invoiceNameCharter;
    }

    public void setInvoiceNameCharter(String invoiceNameCharter) {
        this.invoiceNameCharter = invoiceNameCharter;
    }

    public String getReturnInvoiceNumber() {
        return returnInvoiceNumber;
    }

    public void setReturnInvoiceNumber(String returnInvoiceNumber) {
        this.returnInvoiceNumber = returnInvoiceNumber;
    }

    public String getInvoiceNumber() {

            return invoiceNumber;


    }

    public void setInvoiceNumber(String invoiceNumber) {
        //if (invoiceNumber!=null && !invoiceNumber.isEmpty())
            this.invoiceNumber = invoiceNumber;
    }

    public double getReturnAmount() {
        return returnAmount;
    }

    public void setReturnAmount(double returnAmount) {
        this.returnAmount = returnAmount;
    }

    public RETURN_UNIT getReturnUnit() {
        return returnUnit;
    }

    public void setReturnUnit(RETURN_UNIT returnUnit) {
        this.returnUnit = returnUnit;
    }

    public String getWeightNote() {
        return weightNote;
    }

    public void setWeightNote(String weightNote) {
        this.weightNote = weightNote;
    }

    public CURRENCY getCurrency() {
        return currency;
    }

    public void setCurrency(CURRENCY currency) {
        this.currency = currency;
    }

    public int getDriverId() {
        return driverId;
    }

    public void setDriverId(int driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
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

    public boolean isAlert() {
        return isAlert;
    }

    public void setAlert(boolean alert) {
        isAlert = alert;
    }

    public double getGallon() {
        return Math.round(realAmount);
    }

    public void setGallon(double gallon) {
        this.gallon = gallon;
    }


    public String toJson() {
        return gson.toJson(this);
    }

    public int getChangeFlag() {
        return changeFlag;
    }

    public void setChangeFlag(int changeFlag) {
        this.changeFlag |= changeFlag;
    }

    public void removeChangeFlag(int changeFlag) {

        this.changeFlag &= ~changeFlag;
    }

    public void clearChangeFlag() {
        this.changeFlag = CHANGE_FLAG.NONE;
    }

    public int getInvoiceFormId() {
        return invoiceFormId;
    }

    public void setInvoiceFormId(int invoiceFormId) {
        this.invoiceFormId = invoiceFormId;
    }

    public String getFormNo() {
        return formNo;
    }

    public void setFormNo(String formNo) {
        this.formNo = formNo;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public Integer getBM2508Result() {
        return bM2508Result;
    }

    public void setBM2508Result(Integer bM2508Result) {
        this.bM2508Result = bM2508Result;
    }

    public boolean getBM2508BondingCable() {
        BM2508BondingCable = bM2508Result != null && (bM2508Result & 1) > 0;
        return BM2508BondingCable;
    }

    public void setBM2508BondingCable(boolean value) {
        if (bM2508Result == null)
            bM2508Result = 0;
        bM2508Result |= 1;
        if (!value)
            bM2508Result ^= 1;
    }

    public boolean getBM2508FuelingHose() {
        BM2508FuelingHose = bM2508Result != null && (bM2508Result & 2) > 0;
        return BM2508FuelingHose;
    }

    public void setBM2508FuelingHose(boolean value) {
        if (bM2508Result == null)
            bM2508Result = 0;
        bM2508Result |= 2;
        if (!value)
            bM2508Result ^= 2;
    }

    public boolean getBM2508FuelingCap() {
        BM2508FuelingCap = bM2508Result != null && (bM2508Result & 4) > 0;
        return BM2508FuelingCap;
    }

    public void setBM2508FuelingCap(boolean value) {
        if (bM2508Result == null)
            bM2508Result = 0;
        bM2508Result |= 4;
        if (!value)
            bM2508Result ^= 4;
    }

    public boolean getBM2508Ladder() {
        BM2508Ladder = bM2508Result != null && (bM2508Result & 8) > 0;
        return BM2508Ladder;
    }

    public void setBM2508Ladder(boolean value) {
        if (bM2508Result == null)
            bM2508Result = 0;
        bM2508Result |= 8;
        if (!value)
            bM2508Result ^= 8;
    }

    private boolean exported;

    public boolean isExported() {
        return exported;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }


    private boolean isSplit;

    public boolean isSplit() {
        return isSplit;
    }

    public void setSplit(boolean split) {
        isSplit = split;
    }

    private String flightUniqueId = UUID.randomUUID().toString();

    public String getFlightUniqueId() {
        return flightUniqueId;
    }

    public void setFlightUniqueId(String flightUniqueId) {
        this.flightUniqueId = flightUniqueId;
    }

    private double waterSensor;

    public double getWaterSensor() {
        return waterSensor;
    }

    public void setWaterSensor(double waterSensor) {
        this.waterSensor = waterSensor;
    }

    private double projectedCapacity;
    private double actualCapacity;

    public double getProjectedCapacity() {
        return projectedCapacity;
    }

    public void setProjectedCapacity(double projectedCapacity) {
        this.projectedCapacity = projectedCapacity;
    }

    public double getActualCapacity() {
        return actualCapacity;
    }

    public void setActualCapacity(double actualCapacity) {
        this.actualCapacity = actualCapacity;
    }

    public RefuelItemData split(double splitAmount)
    {
        RefuelItemData splitItem = gson.fromJson(this.toJson(), RefuelItemData.class);
        splitItem.setId(0);
        splitItem.setLocalId(0);
        splitItem.setUniqueId(UUID.randomUUID().toString());
        splitItem.setLocalModified(true);
        splitItem.setSplit(true);
        splitItem.setEndTime(new Date(splitItem.getEndTime().getTime()+1000*60));
        double vol = Math.round(splitAmount / getDensity());
        double gal = Math.round(vol / GALLON_TO_LITTER);
        //double newAmount = Math.round(Math.round(gal * GALLON_TO_LITTER) * getDensity());

        splitItem.setRealAmount(gal);
        splitItem.setGallon(gal);
        splitItem.setVolume(vol);
        splitItem.setReceiptNumber(null);
        splitItem.setReceiptCount(0);
        if (BuildConfig.FHS)
            splitItem.setEndNumber(this.getStartNumber()+vol);
        else
            splitItem.setEndNumber(this.getStartNumber()+gal);

        this.setRealAmount(this.getRealAmount() - gal);
        this.setVolume(Math.round(this.getRealAmount()* GALLON_TO_LITTER));
        this.setStartNumber(splitItem.getEndNumber());
        this.setGallon(this.getRealAmount());
        this.setLocalModified(true);

        return  splitItem;
    }

    public boolean validTime() {
        return startTime.before(endTime) && startTime.after(new Date(endTime.getTime() - 120 *60 *1000));

    }


    public enum ITEM_POST_STATUS {
        NONE,
        SUCCESS,
        ERROR
    }

    public enum ITEM_PRINT_STATUS {
        NONE,
        SUCCESS,
        ERROR
    }

    public enum FLIGHT_STATUS {
        @SerializedName("0") NONE(0),
        @SerializedName("1") ASSIGNED(1),
        @SerializedName("2") REFUELING(2),
        @SerializedName("3") REFUELED(3),
        @SerializedName("4") CANCELLED(4);
        private final int value;

        FLIGHT_STATUS(int i) {
            value = i;
        }

    }

    public enum REFUEL_ITEM_TYPE {
        @SerializedName("0") REFUEL(0),
        @SerializedName("1") EXTRACT(1),
        @SerializedName("2") TEST(2);


        private final int value;

        REFUEL_ITEM_TYPE(int i) {
            value = i;
        }
    }

    public enum CURRENCY {
        @SerializedName("0") VND(0),
        @SerializedName("1") USD(1),
        @SerializedName("2") TEST(2);

        private final int value;

        CURRENCY(int i) {
            value = i;
        }
    }

    public interface CHANGE_FLAG {
        int NONE = 0;
        int PRICE = 1;
        int GROSS_QTY = 2;
        int END_METER = 4;
        int INVOICE_NUMBER = 8;


    }
}
