package com.megatech.fms.data.entity;

import androidx.room.Entity;
import androidx.room.TypeConverter;

import com.google.gson.annotations.SerializedName;
import com.megatech.fms.model.RefuelItemData;

import java.util.Date;

import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

@Entity
public class RefuelItem extends BaseEntity {

    public RefuelItem() {

        status = REFUEL_ITEM_STATUS.NONE;
        flightStatus = FLIGHT_STATUS.NONE;
        refuelItemType = REFUEL_ITEM_TYPE.REFUEL;
    }


    public RefuelItemData toRefuelItemData()
    {
        RefuelItemData itemData =  gson.fromJson(getJsonData(),RefuelItemData.class);

        itemData.setLocalId(this.getLocalId());
        itemData.setLocalModified(this.isLocalModified());
        if (getUniqueId()!=null && !getUniqueId().isEmpty())
            itemData.setUniqueId(this.getUniqueId());
        return  itemData;
    }

    public static RefuelItem fromRefuelItemData(RefuelItemData itemData)
    {
        RefuelItem item = new RefuelItem();
        item.setId(itemData.getId());
        item.setTruckNo(itemData.getTruckNo());
        item.setTruckId(itemData.getTruckId());
        item.setRefuelTime(itemData.getRefuelTime());
        item.setStatus(REFUEL_ITEM_STATUS.getStatus(itemData.getStatus().getValue()));
        item.setJsonData(gson.toJson(itemData));
        item.setLocalId(itemData.getLocalId());
        item.setDateUpdated(itemData.getDateUpdated());
        item.setFlightId(itemData.getFlightId());
        item.setRefuelItemType(REFUEL_ITEM_TYPE.getValue(itemData.getRefuelItemType().ordinal()));
        if (itemData.getUniqueId() !=null)
            item.setUniqueId(itemData.getUniqueId());
        return  item;
    }

    public void updateData(RefuelItemData itemData) {

        RefuelItem item = this;
        RefuelItemData currentData = this.toRefuelItemData();
        if (currentData.isPrinted() && !itemData.isPrinted())
            return;

        item.setId(itemData.getId());
        item.setTruckNo(itemData.getTruckNo());
        item.setTruckId(itemData.getTruckId());
        item.setRefuelTime(itemData.getRefuelTime());
        item.setStatus(REFUEL_ITEM_STATUS.getStatus(itemData.getStatus().getValue()));

        item.setJsonData(gson.toJson(itemData));

        item.setDateUpdated(itemData.getDateUpdated());
        item.setFlightId(itemData.getFlightId());
        item.setRefuelItemType(REFUEL_ITEM_TYPE.getValue(itemData.getRefuelItemType().ordinal()));

    }

    private int flightId;
    private String flightCode;

    private String aircraftType;
    private String aircraftCode;
    private String parkingLot;
    private Date refuelTime;
    private double estimateAmount;
    private double realAmount;
    private double temperature;
    private Date endTime = new Date();
    private int userId;
    private int truckId;
    private Date startTime = new Date();
    private Date arrivalTime;
    private Date departureTime;
    private double startNumber;
    private double endNumber;
    private double manualTemperature;

    private double density;

    private int airlineId;

    private double weight;
    private double volume;

    private double price;

    private String routeName;


    private String productName;

    private String qualityNo;
    private double taxRate;

    public double getVolume() {
        return realAmount * GALLON_TO_LITTER;
    }

    public double getWeight() {
        return density * getVolume();
    }

    public int getAirlineId() {
        return airlineId;
    }

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return getWeight() * getPrice();
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

    public void setPrice(double price) {
        this.price = price;
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

    private ITEM_PRINT_STATUS printStatus = ITEM_PRINT_STATUS.NONE;

    public ITEM_PRINT_STATUS getPrintStatus() {
        return printStatus;
    }

    public void setPrintStatus(ITEM_PRINT_STATUS printStatus) {
        this.printStatus = printStatus;
    }

    private ITEM_POST_STATUS postStatus = ITEM_POST_STATUS.SUCCESS;

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


    public void setFlightId(Integer flightId) {
        this.flightId = flightId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    private REFUEL_ITEM_STATUS status;

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
        this.realAmount = realAmount;
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

    public int getFlightId() {
        return flightId;
    }

    public int getUserId() {
        return userId;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
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

    private String truckNo;

    public String getTruckNo() {
        return this.truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }

    private REFUEL_ITEM_TYPE refuelItemType;
    public void setRefuelItemType(REFUEL_ITEM_TYPE refuelItemType) {
        this.refuelItemType = refuelItemType;
    }

    public REFUEL_ITEM_TYPE getRefuelItemType() {
        return refuelItemType;
    }


    public enum REFUEL_ITEM_TYPE {
        @SerializedName("0") REFUEL(0),
        @SerializedName("1") EXTRACT(1),
        @SerializedName("2") TEST(2);


        private final int value;

        REFUEL_ITEM_TYPE(int i) {
            value = i;
        }

        @TypeConverter
        public static REFUEL_ITEM_TYPE getValue(int numeral) {
            for (REFUEL_ITEM_TYPE ds : values()) {
                if (ds.value == numeral) {
                    return ds;
                }
            }
            return null;
        }

        @TypeConverter
        public static Integer getInt(REFUEL_ITEM_TYPE status) {
            return status.value;
        }
    }

    public enum ITEM_PRINT_STATUS {

        @SerializedName("0") NONE(0),
        @SerializedName("1") SUCCESS(1),
        @SerializedName("2") ERROR(2);

        private final int value;

        ITEM_PRINT_STATUS(int i) {
            value = i;
        }

        @TypeConverter
        public static ITEM_PRINT_STATUS getStatus(int numeral) {
            for (ITEM_PRINT_STATUS ds : values()) {
                if (ds.value == numeral) {
                    return ds;
                }
            }
            return null;
        }

        @TypeConverter
        public static Integer getInt(ITEM_PRINT_STATUS status) {
            return status.value;
        }
    }

    public enum ITEM_POST_STATUS {

        @SerializedName("0") NONE(0),
        @SerializedName("1") SUCCESS(1),
        @SerializedName("2") ERROR(2);

        private final int value;

        ITEM_POST_STATUS(int i) {
            value = i;
        }

        @TypeConverter
        public static ITEM_POST_STATUS getStatus(int numeral) {
            for (ITEM_POST_STATUS ds : values()) {
                if (ds.value == numeral) {
                    return ds;
                }
            }
            return null;
        }

        @TypeConverter
        public static Integer getInt(ITEM_POST_STATUS status) {
            return status.value;
        }
    }

    private FLIGHT_STATUS flightStatus;

    public FLIGHT_STATUS getFlightStatus() {
        return flightStatus;
    }

    public void setFlightStatus(FLIGHT_STATUS flightStatus) {
        this.flightStatus = flightStatus;
    }

    public enum FLIGHT_STATUS {
        @SerializedName("0") NONE(0),
        @SerializedName("1") ASSIGNED(1),
        @SerializedName("2") REFUELING(2),
        @SerializedName("3") REFUELED(3);

        private final int value;

        FLIGHT_STATUS(int i) {
            value = i;
        }

        @TypeConverter
        public static FLIGHT_STATUS getStatus(int numeral) {
            for (FLIGHT_STATUS ds : values()) {
                if (ds.value == numeral) {
                    return ds;
                }
            }
            return null;
        }

        @TypeConverter
        public static Integer getInt(FLIGHT_STATUS status) {
            return status.value;
        }
    }

    public enum REFUEL_ITEM_STATUS {
        @SerializedName("0") NONE(0),
        @SerializedName("1") PROCESSING(1),
        @SerializedName("2") PAUSED(2),
        @SerializedName("3") DONE(3),
        @SerializedName("2") ERROR(4);
        private final int value;

        REFUEL_ITEM_STATUS(int i) {
            value = i;
        }

        @TypeConverter
        public static REFUEL_ITEM_STATUS getStatus(int numeral) {
            for (REFUEL_ITEM_STATUS ds : values()) {
                if (ds.value == numeral) {
                    return ds;
                }
            }
            return null;
        }

        @TypeConverter
        public static Integer getInt(REFUEL_ITEM_STATUS status) {
            return status.value;
        }


    }

    public void setAirlineId(int airlineId) {
        this.airlineId = airlineId;
    }


}
