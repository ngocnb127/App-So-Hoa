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

        // jsonData có thể còn giữ identity/version cũ (bản ghi tạo offline, response
        // ghi cột nhưng không ghi lại json...). Cột của entity là nguồn chuẩn, và
        // version không bao giờ được giảm.
        if (this.getId() > 0)
            itemData.setId(this.getId());
        itemData.setClientSeq(Math.max(this.getClientSeq(), itemData.getClientSeq()));
        itemData.setServerRevision(Math.max(this.getServerRevision(), itemData.getServerRevision()));

        // Đóng dấu phiên bản của row tại thời điểm đọc: mọi màn hình giữ snapshot này
        // đều biết mình đang sửa trên nền phiên bản nào, và trên payload nền nào.
        itemData.setBaseClientSeq(itemData.getClientSeq());
        itemData.setBaseServerRevision(itemData.getServerRevision());
        // Vân tay tính từ chính jsonData đã lưu — ổn định với dữ liệu legacy thiếu trường,
        // vì không đi qua các giá trị mặc định của model.
        itemData.setBaseBusinessFingerprint(
                com.megatech.fms.helpers.RefuelSyncGuard.businessFingerprintOfJson(getJsonData()));
        // Nguyên văn payload lúc đọc: lúc lưu còn phân biệt được trường nào người dùng đã
        // sửa, trường nào chỉ server đổi — và ghép lại được sau một lần bị chặn.
        itemData.setBaseJson(getJsonData());

        // Trạng thái hàng đợi đồng bộ nằm ở CỘT, không nằm trong jsonData. Không chép sang
        // thì model giữ giá trị mặc định SUCCESS và màn hình báo "đã gửi" cho một row đang
        // kẹt lỗi.
        // Hai enum cùng thứ tự NONE/SUCCESS/ERROR nên ánh xạ thẳng theo ordinal.
        if (getPostStatus() != null)
            itemData.setPostStatus(
                    RefuelItemData.ITEM_POST_STATUS.values()[getPostStatus().ordinal()]);
        return  itemData;
    }

    public static RefuelItem fromRefuelItemData(RefuelItemData itemData)
    {
        RefuelItem item = new RefuelItem();
        item.setJsonData(gson.toJson(itemData));
        item.projectColumnsFrom(itemData);
        item.setLocalId(itemData.getLocalId());
        if (itemData.getUniqueId() !=null)
            item.setUniqueId(itemData.getUniqueId());
        return  item;
    }

    /**
     * Chiếu payload xuống CÁC CỘT của Room.
     *
     * <p>Trước đây chỉ vài cột được ghi (id, truckNo, truckId, refuelTime, status, flightId,
     * refuelItemType, version) còn toàn bộ số liệu nghiệp vụ nằm im ở giá trị mặc định. Đo
     * trên máy thật 17-08 với mẻ 843 GL: {@code jsonData} đúng hoàn toàn trong khi cột
     * {@code realAmount}, {@code startNumber}, {@code endNumber}, {@code density},
     * {@code manualTemperature}, {@code qualityNo}, {@code flightCode}, {@code parkingLot},
     * {@code price} đều là 0/null. Mọi màn hình hay truy vấn đọc theo cột sẽ thấy mẻ 0 GL.
     *
     * <p>Gọi ở MỌI đường ghi — {@link #fromRefuelItemData}, {@link #updateData} và đường
     * nhận bản server ({@code RefuelSyncGuard.applyRemote}) — để cột và {@code jsonData}
     * không bao giờ nói hai chuyện khác nhau.
     *
     * <p>{@code weight} và {@code volume} cố ý không gán: entity tính chúng từ
     * {@code realAmount}/{@code density} bằng getter.
     */
    public void projectColumnsFrom(RefuelItemData data) {
        setId(data.getId());
        setFlightId(data.getFlightId());
        setFlightCode(data.getFlightCode());
        setAircraftCode(data.getAircraftCode());
        setAircraftType(data.getAircraftType());
        setParkingLot(data.getParkingLot());
        setRouteName(data.getRouteName());
        setRefuelTime(data.getRefuelTime());
        setArrivalTime(data.getArrivalTime());
        setDepartureTime(data.getDepartureTime());
        setEstimateAmount(data.getEstimateAmount());
        setRealAmount(data.getRealAmount());
        setStartNumber(data.getStartNumber());
        setEndNumber(data.getEndNumber());
        setTemperature(data.getTemperature());
        setManualTemperature(data.getManualTemperature());
        setDensity(data.getDensity());
        setQualityNo(data.getQualityNo());
        setPrice(data.getPrice());
        setTaxRate(data.getTaxRate());
        setProductId(data.getProductId());
        setProductName(data.getProductName());
        setAirlineId(data.getAirlineId());
        setUserId(data.getUserId());
        setTruckId(data.getTruckId());
        setTruckNo(data.getTruckNo());
        if (data.getStartTime() != null) setStartTime(data.getStartTime());
        if (data.getEndTime() != null) setEndTime(data.getEndTime());
        setDateUpdated(data.getDateUpdated());
        setStatus(REFUEL_ITEM_STATUS.getStatus(data.getStatus().getValue()));
        // getStatus() trả null cho giá trị model không có trong enum này (CANCELLED=4).
        // Ghi null xuống rồi để converter nổ là cách hỏng tệ nhất; giữ giá trị cũ.
        FLIGHT_STATUS mappedFlight = data.getFlightStatus() == null
                ? FLIGHT_STATUS.NONE
                : FLIGHT_STATUS.getStatus(data.getFlightStatus().ordinal());
        if (mappedFlight != null) setFlightStatus(mappedFlight);
        setRefuelItemType(REFUEL_ITEM_TYPE.getValue(data.getRefuelItemType().ordinal()));
        ITEM_PRINT_STATUS mappedPrint = data.getPrintStatus() == null
                ? ITEM_PRINT_STATUS.NONE
                : ITEM_PRINT_STATUS.getStatus(data.getPrintStatus().ordinal());
        if (mappedPrint != null) setPrintStatus(mappedPrint);
        setClientSeq(data.getClientSeq());
        setServerRevision(data.getServerRevision());
    }

    public void updateData(RefuelItemData itemData) {

        RefuelItem item = this;
        RefuelItemData currentData = this.toRefuelItemData();

        // Bản đến không được hạ cờ đã in, nhưng cũng không vì thế mà bỏ luôn cả lần ghi:
        // trước đây return thẳng làm mọi sửa đổi của người dùng biến mất không dấu vết khi
        // snapshot trên màn hình lỡ thiếu cờ printed. Giữ lại cờ rồi ghi bình thường.
        if (currentData.isPrinted() && !itemData.isPrinted())
            itemData.setPrinted(true);

        // Cột và jsonData phải luôn nói cùng một chuyện — xem projectColumnsFrom.
        item.projectColumnsFrom(itemData);

        // GIỮ các khoá server gửi xuống mà model không biết (TechLog, Weight, Invoice...).
        // Thay nguyên jsonData bằng bản serialize từ model sẽ xoá sạch chúng ở mỗi lần lưu
        // local, rồi đẩy bản thiếu đó lên server ở lần POST kế tiếp.
        // itemData luôn là model ĐẦY ĐỦ ở đường này: nó đến từ toRefuelItemData hoặc từ
        // một màn hình đang giữ cả phiếu, nên trường vắng mặt đúng là người dùng đã xoá.
        item.setJsonData(com.megatech.fms.helpers.RefuelSyncGuard.mergePreservingUnknown(
                item.getJsonData(), gson.toJson(itemData),
                com.megatech.fms.helpers.RefuelSyncGuard.ModelPayloadSource.COMPLETE_MODEL));

        item.setDateUpdated(itemData.getDateUpdated());
        item.setFlightId(itemData.getFlightId());
        item.setRefuelItemType(REFUEL_ITEM_TYPE.getValue(itemData.getRefuelItemType().ordinal()));
        item.setClientSeq(itemData.getClientSeq());
        item.setServerRevision(itemData.getServerRevision());

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
    private int productId;

    private String qualityNo;
    private double taxRate;

    private long clientSeq = 0;
    private int serverRevision = 0;

    public long getClientSeq() {
        return clientSeq;
    }

    public void setClientSeq(long clientSeq) {
        this.clientSeq = clientSeq;
    }

    public int getServerRevision() {
        return serverRevision;
    }

    public void setServerRevision(int serverRevision) {
        this.serverRevision = serverRevision;
    }

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

    public void setProductId(Integer productId)  {
        this.productId = productId;
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
    public int getProductId() {
        return productId;
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
            // Không bao giờ để converter nổ: một NPE ở đây giết cả lượt sync.
            return status == null ? REFUEL.value : status.value;
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
            // Không bao giờ để converter nổ: một NPE ở đây giết cả lượt sync.
            return status == null ? NONE.value : status.value;
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
            // Không bao giờ để converter nổ: một NPE ở đây giết cả lượt sync.
            return status == null ? NONE.value : status.value;
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
            // Model có CANCELLED(4), enum này thì không, nên getStatus(4) trả null và
            // Room gọi converter với null. Đo trên xe thật 17-08: NPE ở đây làm HỎNG CẢ
            // LƯỢT SYNC ("SYNC core failed") mỗi 30 giây, suốt nhiều giờ.
            return status == null ? NONE.value : status.value;
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
            // Không bao giờ để converter nổ: một NPE ở đây giết cả lượt sync.
            return status == null ? NONE.value : status.value;
        }


    }

    public void setAirlineId(int airlineId) {
        this.airlineId = airlineId;
    }


}
