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

    private long clientSeq = 0;
    private int serverRevision = 0;

    /**
     * Cờ tường minh của server: gói vừa POST có được ghi hay không.
     *
     * <p>{@code null} nghĩa là server CHƯA hỗ trợ trường này — khi đó phải suy đoán bằng
     * cách đối chiếu giá trị chốt (xem {@link com.megatech.fms.helpers.RefuelSyncGuard#describeAck}).
     * Không được coi {@code null} là {@code false}: nhầm như vậy sẽ đẩy toàn bộ phiếu vào
     * trạng thái conflict trên mọi bản server cũ.
     */
    private Boolean applied;

    /** Lý do server từ chối, chỉ có nghĩa khi {@link #applied} là {@code false}. */
    private String rejectReason;

    /**
     * JSON NGUYÊN BẢN của bản ghi do server trả về, gắn thủ công tại tầng HTTP.
     *
     * <p>Cần thiết cho việc trộn theo quyền sở hữu trường: chỉ chuỗi thô mới phân biệt được
     * "server không gửi trường này" (khoá vắng mặt) với "server xoá trường này" (khoá có mặt,
     * giá trị null). Đi qua model là mất phân biệt đó, vì trường vắng mặt biến thành giá trị
     * mặc định — đúng cơ chế đã làm FlightId về 0 và phiếu rơi khỏi chuyến bay.
     *
     * <p>{@code transient}: Gson bỏ qua ở cả hai chiều nên không bao giờ lọt vào jsonData
     * lưu trong Room hay payload gửi lên server.
     */
    private transient String rawJson;

    public Boolean getApplied() {
        return applied;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    /**
     * Phiên bản của bản ghi tại thời điểm snapshot này được đọc ra khỏi Room.
     * Dùng làm precondition khi lưu: nếu row đã đổi so với lúc màn hình mở thì
     * đây là conflict, không được lặng lẽ ghi đè.
     *
     * <p>{@code transient} — không serialize vào jsonData cũng như payload HTTP;
     * giá trị luôn được đóng dấu lại từ cột entity mỗi lần dựng model.
     * {@link #VERSION_UNKNOWN} nghĩa là snapshot không đến từ một row đã lưu.
     */
    public static final long VERSION_UNKNOWN = -1;

    private transient long baseClientSeq = VERSION_UNKNOWN;
    private transient int baseServerRevision = (int) VERSION_UNKNOWN;

    /**
     * Vân tay của payload nghiệp vụ tại thời điểm snapshot được đọc.
     *
     * <p>Chỉ có version là không đủ: Web/GET có thể sửa payload và tăng ServerRevision
     * mà giữ nguyên ClientSeq. Fingerprint cho phép phân biệt "server chỉ cấp thêm
     * metadata" (được rebase) với "dữ liệu nền đã bị đổi" (phải conflict).
     *
     * <p>{@code transient} — không serialize vào jsonData lẫn payload HTTP.
     */
    private transient String baseBusinessFingerprint;

    /** Kết quả của lần lưu vừa rồi. {@code null} cũng như {@link #FAILED}: KHÔNG thành công. */
    public enum SAVE_OUTCOME {
        /** Dữ liệu đã nằm trong Room. Chỉ trạng thái này mới được phép điều hướng tiếp. */
        COMMITTED,
        /** Precondition từ chối: dữ liệu người dùng chưa được ghi, phải giữ lại trên màn hình. */
        CONFLICT,
        /** Lỗi ngoài dự kiến. */
        FAILED
    }

    /**
     * Kết quả lần lưu vừa rồi.
     *
     * <p>Trước đây "đã lưu" và "bị chặn" cùng trả về bản ghi đang có trong Room nên không
     * phân biệt được: màn hình xác nhận gán đè kết quả lên dữ liệu người dùng vừa nhập rồi
     * đi tiếp — số liệu mẻ biến mất và hiện về 0.
     *
     * <p>{@code transient} — không serialize vào jsonData lẫn payload HTTP.
     */
    private transient SAVE_OUTCOME saveOutcome;

    public SAVE_OUTCOME getSaveOutcome() {
        return saveOutcome;
    }

    public void setSaveOutcome(SAVE_OUTCOME saveOutcome) {
        this.saveOutcome = saveOutcome;
    }

    /** Chỉ đúng khi dữ liệu THỰC SỰ đã vào Room. Mọi điều hướng phải đi qua đây. */
    public static boolean isCommitted(RefuelItemData result) {
        return result != null && result.saveOutcome == SAVE_OUTCOME.COMMITTED;
    }

    /**
     * Lần lưu này chính là lần đưa mẻ từ chưa xong sang {@code DONE}.
     *
     * <p>Tồn xe chỉ được trừ đúng một lần, tại lần chuyển trạng thái đó. Nút End bấm lại
     * hay callback thiết bị lặp không được trừ lần hai.
     */
    private transient boolean transitionedToDone;

    public boolean isTransitionedToDone() {
        return transitionedToDone;
    }

    public void setTransitionedToDone(boolean transitionedToDone) {
        this.transitionedToDone = transitionedToDone;
    }

    /**
     * Nguyên văn {@code jsonData} của row tại thời điểm snapshot được đọc.
     *
     * <p>Chiều thứ ba của mọi phép trộn khi lưu. Vân tay là mã băm nên chỉ nói "có đổi",
     * không nói "đổi ở đâu" — không đủ để phân biệt "người dùng vừa sửa bãi đỗ" với "server
     * vừa đổi chuyến", cũng không đủ để ghép lại dữ liệu sau một lần bị chặn.
     *
     * <p>{@code transient} — không serialize vào jsonData lẫn payload HTTP.
     */
    private transient String baseJson;

    /**
     * Bản sao ĐỘC LẬP để đưa vào hàng đợi ghi, kèm nguyên baseline.
     *
     * <p>Xếp hàng bằng tham chiếu là sai: đối tượng trên màn hình còn bị luồng đọc đồng hồ
     * và nút End sửa tiếp sau khi task đã xếp hàng. Hệ quả thật đã gặp — task autosave của
     * số đo `400` chạy sau khi End đã đặt `DONE` lên cùng object, nên chính autosave thực
     * hiện việc chuyển trạng thái; cờ {@code transitionedToDone} rơi vào kết quả bị bỏ đi,
     * còn lần ghi của End thấy row đã DONE nên báo false và TỒN XE KHÔNG BAO GIỜ ĐƯỢC TRỪ.
     *
     * <p>Baseline là {@code transient} nên không đi theo JSON, phải chép tay.
     */
    public RefuelItemData snapshotForSave() {
        RefuelItemData copy = gson.fromJson(this.toJson(), RefuelItemData.class);
        copy.setBaseClientSeq(baseClientSeq);
        copy.setBaseServerRevision(baseServerRevision);
        copy.setBaseBusinessFingerprint(baseBusinessFingerprint);
        copy.setBaseJson(baseJson);
        return copy;
    }

    /**
     * Nhận lại danh tính và baseline mà một lần ghi vừa xác lập.
     *
     * <p>Vì hàng đợi ghi thao tác trên bản sao, đối tượng của màn hình không tự biết phiên
     * bản mới. Không chép lại thì lần lưu kế tiếp đứng trên baseline cũ và bị precondition
     * hiểu nhầm là snapshot lỗi thời. CHỈ chép nhóm danh tính/phiên bản — dữ liệu nghiệp vụ
     * của màn hình có thể đã mới hơn.
     */
    public void adoptSaveState(RefuelItemData saved) {
        if (saved == null) return;

        if (saved.getId() != null && saved.getId() > 0) setId(saved.getId());
        if (saved.getLocalId() > 0) setLocalId(saved.getLocalId());
        if (saved.getUniqueId() != null && !saved.getUniqueId().isEmpty())
            setUniqueId(saved.getUniqueId());
        setClientSeq(Math.max(clientSeq, saved.getClientSeq()));
        setServerRevision(Math.max(serverRevision, saved.getServerRevision()));

        setBaseClientSeq(saved.getBaseClientSeq());
        setBaseServerRevision(saved.getBaseServerRevision());
        setBaseBusinessFingerprint(saved.getBaseBusinessFingerprint());
        setBaseJson(saved.getBaseJson());
    }

    public String getBaseJson() {
        return baseJson;
    }

    public void setBaseJson(String baseJson) {
        this.baseJson = baseJson;
    }

    public String getBaseBusinessFingerprint() {
        return baseBusinessFingerprint;
    }

    public void setBaseBusinessFingerprint(String baseBusinessFingerprint) {
        this.baseBusinessFingerprint = baseBusinessFingerprint;
    }

    public long getBaseClientSeq() {
        return baseClientSeq;
    }

    public void setBaseClientSeq(long baseClientSeq) {
        this.baseClientSeq = baseClientSeq;
    }

    public int getBaseServerRevision() {
        return baseServerRevision;
    }

    public void setBaseServerRevision(int baseServerRevision) {
        this.baseServerRevision = baseServerRevision;
    }

    public boolean hasBaseVersion() {
        return baseClientSeq != VERSION_UNKNOWN;
    }

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

    /**
     * Dựng lại model từ JSON.
     *
     * <p>Baseline (version + vân tay payload nền) KHÔNG nằm trong JSON và cũng không được
     * suy ra ở đây: vân tay tính lúc parse là vân tay của payload người dùng đang sửa, không
     * phải của bản nền đã đọc từ Room. Baseline phải đi kèm riêng — xem {@code RefuelIntent}.
     */
    public static RefuelItemData fromJson(String jsonData) {
        RefuelItemData item = gson.fromJson(jsonData, RefuelItemData.class);
        if (item.getUniqueId() == null || item.getUniqueId().isEmpty())
            item.setUniqueId( UUID.randomUUID().toString());

        // KHÔNG đóng dấu baseline ở đây: fingerprint tính tại thời điểm parse là vân tay của
        // payload NGƯỜI DÙNG ĐANG SỬA, không phải của bản nền đã đọc từ Room. Baseline phải
        // được truyền riêng (xem RefuelIntent) để snapshot qua Intent vẫn lưu được.
        return  item;
    }

    /**
     * Bản sao thuần từ JSON, KHÔNG đóng dấu baseline — dùng cho việc chuẩn hoá/so sánh.
     * Nếu dùng {@link #fromJson(String)} ở đó sẽ thành đệ quy vô hạn, vì fromJson lại
     * đi tính fingerprint.
     */
    public RefuelItemData deepCopyRaw() {
        return gson.fromJson(this.toJson(), RefuelItemData.class);
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

            itemData.setApproachTime(null);
            itemData.setLeaveTime(null);
            itemData.setClientSeq(0);
            itemData.setServerRevision(0);


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

    /**
     * Số lít, LUÔN suy từ số gallon.
     *
     * <p>Toàn đội xe dùng đồng hồ đo gallon; nhánh đồng hồ đo lít đã bỏ. Nhánh cũ trả thẳng
     * field {@code volume} chính là nguồn lỗi số lít sai ở gói chốt mẻ (24-08-2026, mẻ 2118503
     * và 2118695): Gson serialize FIELD chứ không gọi getter, nên chỉ cần một đường ghi đổi
     * gallon mà quên field là gói tin mang số lít của lần cập nhật trước.
     */
    public double getVolume() {
        // Nhánh đồng hồ lít — không còn xe nào dùng, giữ lại để đối chiếu khi tra sử.
        // if (BuildConfig.FHS)
        //     return volume;
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

        // Tính lại số lít cho MỌI bản dựng, không riêng bản không-FHS.
        //
        // Gson serialize FIELD chứ không gọi getter, nên gói tin mang thẳng `volume`. Nhánh
        // FHS trước đây bỏ qua bước này, `getVolume()` thì tính lại nên màn hình vẫn đúng —
        // chỉ gói tin gửi lên là mang số lít của lần cập nhật trước. Đo được ngày 24-08-2026
        // trên hai mẻ ở hai sân bay: gói chốt mẻ 2118503 gửi 5.306 gallon kèm 17.190 lít,
        // trong khi 17.190 lít là số của 4.541 gallon ở gói ngay trước đó.
        //
        // Các đường nhập tay số lít gọi setVolume() NGAY SAU hàm này nên vẫn ghi đè được.
        this.volume = Math.round(this.realAmount * GALLON_TO_LITTER);
    }

    /** Lệch quá ngần này lít thì coi là dữ liệu trong bộ nhớ đang mâu thuẫn, không phải làm tròn. */
    public static final double VOLUME_TOLERANCE_LITTER = 2d;

    /**
     * Ép lại bất biến {@code Volume = round(Gallon × 3.7854)} ngay trước khi gửi.
     *
     * <p>Chốt chặn cấu trúc, không phải chỗ sửa lỗi: nếu một đường ghi nào đó đổi
     * {@code Gallon} mà quên số lít, gói tin vẫn đi đúng và để lại dấu vết để tìm ra đường đó.
     *
     * @return mô tả chênh lệch đã sửa, hoặc null nếu vốn đã khớp.
     */
    public String reconcileVolume() {
        double expected = Math.round(Math.round(realAmount) * GALLON_TO_LITTER);
        if (Math.abs(volume - expected) <= VOLUME_TOLERANCE_LITTER) return null;

        double before = volume;
        volume = expected;

        return String.format(java.util.Locale.US,
                "gallon=%.0f volume_in=%.0f volume_calc=%.0f", realAmount, before, expected);
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

    /**
     * KHÔNG khởi tạo bằng UUID ngẫu nhiên: JSON cũ thiếu FlightUniqueId sẽ nhận một giá trị
     * khác nhau ở mỗi lần đọc, làm vân tay payload mất ổn định (cùng một row bị nhận nhầm là
     * CONFLICT_PAYLOAD_CHANGED) và còn ghi UUID rác xuống Room. UUID chỉ được cấp ở luồng
     * tạo phiếu mới có chủ đích (NewRefuelActivity).
     */
    private String flightUniqueId;

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
    private int productId;



    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    private ProductModel productModel;

    public ProductModel getProductModel() {
        return productModel;
    }

    public void setProductModel(ProductModel productModel) {
        this.productModel = productModel;
    }


    private String pCode;
    private String pName;
    private int sortOrder;

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPCode() {
        return pCode;
    }

    public void setPCode(String pCode) {
        this.pCode = pCode;
    }

    public String getPName() {
        return pName;
    }

    public void setPName(String pName) {
        this.pName = pName;
    }

    private double ProjectedCapacity;
    private double ActualCapacity;

//cập nhật tiếp cận và rời đi
    private Date approachTime = null;
    private Date leaveTime = null;

    public Date getApproachTime() {
        return approachTime;
    }

    public void setApproachTime(Date approachTime) {
        this.approachTime = approachTime;
    }

    public Date getLeaveTime() {
        return leaveTime;
    }

    public void setLeaveTime(Date leaveTime) {
        this.leaveTime = leaveTime;
    }
    //--cập nhật tiếp cận và rời đi

    public double getProjectedCapacity() {
        return ProjectedCapacity;
    }

    public void setProjectedCapacity(double ProjectedCapacity) {
        this.ProjectedCapacity = ProjectedCapacity;
    }

    public double getActualCapacity() {
        return ActualCapacity;
    }

    /**
     * Hãng đã báo tải dầu THỰC TẾ chưa.
     *
     * <p>0 nghĩa là CHƯA CÓ, không phải "tải dầu bằng không" — cả hai trường đều là số
     * nguyên thuỷ nên không phân biệt được bằng null. Coi 0 là số thật sẽ thay số dự kiến
     * đang đúng bằng một con số 0 vô nghĩa ngay trên màn hình tra nạp.
     */
    public boolean hasActualCapacity() {
        return ActualCapacity > 0;
    }

    /**
     * Số tải dầu ĐƯỢC HIỂN THỊ: thực tế khi đã có, còn không thì dự kiến.
     *
     * <p>Hai số không bao giờ cùng có nghĩa một lúc; dự kiến chỉ là con số chờ.
     */
    public double getCapacityToShow() {
        return hasActualCapacity() ? ActualCapacity : ProjectedCapacity;
    }

    public void setActualCapacity(double ActualCapacity) {
        this.ActualCapacity = ActualCapacity;
    }

    public RefuelItemData split(double splitAmount)
    {
        RefuelItemData splitItem = gson.fromJson(this.toJson(), RefuelItemData.class);
        splitItem.setId(0);
        splitItem.setLocalId(0);
        splitItem.setUniqueId(UUID.randomUUID().toString());
        splitItem.setLocalModified(true);
        splitItem.setClientSeq(0);
        splitItem.setServerRevision(0);

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
        // Đồng hồ đo gallon: số đồng hồ cộng theo gallon, không cộng theo lít.
        // if (BuildConfig.FHS)
        //     splitItem.setEndNumber(this.getStartNumber()+vol);
        splitItem.setEndNumber(this.getStartNumber() + gal);

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

    // Thêm 2 field
    /** Số bán hàng: SALENUMBER của LCR, hoặc số ticket của TCS. */
    private String saleNumber = "";

    /**
     * Số ticket đọc từ đồng hồ. LCR có cả SALENUMBER và TICKETNUMBER và chúng KHÁC nhau;
     * TCS chỉ có một số nên hai trường bằng nhau. Lưu vào từng mẻ để in lại được mà không
     * cần hỏi lại thiết bị.
     */
    private String ticketNumber = "";

    // Thêm Getters & Setters
    public String getSaleNumber() { return saleNumber; }
    public void setSaleNumber(String saleNumber) { this.saleNumber = saleNumber; }

    public String getTicketNumber() { return ticketNumber == null ? "" : ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }


}
