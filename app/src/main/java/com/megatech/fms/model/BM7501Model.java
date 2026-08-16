package com.megatech.fms.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * BM 75.01/NLHK — Phiếu yêu cầu hút nhiên liệu từ tàu bay
 * (Jet fuel defuel request form).
 *
 * <p>Model thuần nghiệp vụ, KHÔNG phụ thuộc Android để chạy được unit test trên JVM.
 * Phạm vi bước 1 theo phương án đã duyệt (docs/PLAN-BM7501-HUT-NHIEN-LIEU.md §16):
 * model + trạng thái + validation. Phần lưu trữ/migration bị chặn nên lớp này chưa
 * có entity/DAO đi kèm.
 *
 * <p>Quan hệ: <b>một phiếu ↔ một mẻ hút</b>, liên kết bằng
 * {@link #refuelItemUniqueId} ↔ {@code RefuelItem.uniqueId}
 * (KHÔNG phải {@code flightUniqueId} — trường đó dùng chung cho nhiều mẻ trên cùng chuyến).
 *
 * <p>Trạng thái pháp lý ({@link BusinessStatus}, {@link SyncStatus}, số phiếu, revision)
 * là <b>cột phẳng của entity</b>, không phải nội dung payload. Các trường đó có mặt ở đây
 * để tầng trên thao tác thuận tiện, nhưng khi lưu thì cột phẳng là nguồn chuẩn.
 */
public class BM7501Model extends BaseModel {

    private static final long serialVersionUID = 1L;

    /** Phiên bản schema lưu trữ cục bộ, độc lập với tên trường của backend. */
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion = SCHEMA_VERSION;

    // ---------------------------------------------------------------- danh tính

    /** Khóa của mẻ hút: {@code RefuelItem.uniqueId}. */
    private String refuelItemUniqueId;

    /** Số thứ tự revision của chứng từ cho cùng một mẻ (1, 2, 3...). */
    private int revisionNumber = 1;

    /** {@code uniqueId} của revision bị thay thế, null nếu là bản đầu. */
    private String supersedesUniqueId;

    /** Số chứng từ pháp lý do app sinh, bất biến sau khi in. */
    private String localNumber;

    /** Số tham chiếu kỹ thuật do server cấp, không thay thế {@link #localNumber}. */
    private String serverNumber;

    /** Bộ đếm CAS cho ghi cục bộ — khác {@link #revisionNumber} của chứng từ. */
    private int localRevision;

    private BusinessStatus businessStatus = BusinessStatus.DRAFT;
    private SyncStatus syncStatus = SyncStatus.NOT_READY;

    /** Nhân viên nhập hộ — tách khỏi {@link #customerRepName} (người khai). */
    private int enteredByUserId;
    private String enteredByUserName;

    private Date date;

    // ------------------------------------------------------- thông tin nền của mẻ

    private int airlineId;
    private String airlineName;
    private int airportId;
    private String airportName;
    private int truckId;

    // ------------------------------------------------------------------- MỤC A

    private String customerRepName;
    private String customerTitle;
    private String customerTel;
    private String customerFax;
    private String aircraftType;
    private String aircraftReg;

    private DefuelReason reason;
    private String reasonOther;

    /** A10 — đã xả tất cả thùng lấy mẫu KTCL trước khi hút. */
    private Boolean tankDrainSampled;

    /** A11 — hãng đã thực hiện kiểm tra vi sinh hay chưa (điều kiện, không bắt buộc kết quả). */
    private TriState customerMicrobialTestPerformed;
    private MicrobialKit customerMicrobialKit;
    private String customerMicrobialKitOther;
    private MicrobialResult customerMicrobialResult;

    /** A13 — có phụ gia / không sử dụng / không xác định được. */
    private AdditivePresence additivePresence;
    private List<Additive> additives = new ArrayList<>();

    private String prevLocation1;
    private String prevGrade1;
    private String prevLocation2;
    private String prevGrade2;

    // ------------------------------------------------------------------- MỤC B

    private QcCheck vac;
    private QcCheck cwd;
    private Double densityKgM3;

    private boolean conductivityRequired;
    private Double conductivityPsM;

    /** Nghi ngờ nhiễm vi sinh — ô nhập riêng, không suy diễn từ VAC/CWD. */
    private boolean contaminationSuspected;
    /** Khách hàng yêu cầu kiểm tra vi sinh — ô nhập riêng. */
    private boolean customerRequestedMicrobial;

    private MicrobialKit skypecMicrobialKit;
    private MicrobialResult skypecMicrobialResult;
    /** Lý do phải kiểm tra vi sinh, bắt buộc khi {@link #isSkypecMicrobialRequired()}. */
    private String microbialReason;

    // ------------------------------------------------------------------- MỤC C

    private String defuellerTruckNo;
    private Date startTime;
    private Date endTime;

    private DefuelMethod method;

    /** C4 — đã phổ biến/thống nhất hai tín hiệu chuẩn. */
    private boolean signalsBriefed;
    /** C4 — chỉ điền khi hai bên dùng phương thức tín hiệu khác. */
    private String otherSignal;

    private Double expectedKg;
    private Double actualKg;
    private Double actualTempC;
    private Double actualDensityKgM3;
    private Double gallon;
    private Double liter;

    /** C9 — nhiên liệu hút ra nạp lại được cho tàu bay cùng hãng, không cần kiểm tra bổ sung. */
    private Boolean refuellableWithoutTest;

    /** C10 — phương án xử lý. */
    private HandlingOption handling;
    private Date storageFrom;
    private Date storageTo;
    private String handlingNote;

    private String customerRepFinalName;
    private String skypecRepName;

    // ---------------------------------------------------------------- chữ ký (3)

    private String customerSectionASignaturePath;
    private String customerSectionASignatureSha256;
    private String customerFinalSignaturePath;
    private String customerFinalSignatureSha256;
    private String skypecSignaturePath;
    private String skypecSignatureSha256;

    // ------------------------------------------------------------------- audit

    private Date signedAt;
    private Date printedAt;
    private int reprintCount;
    private String signedSnapshotHash;

    private Date cancelledAt;
    private String cancelReason;

    private Date voidedAt;
    private String voidReason;
    private int voidedByUserId;

    // ==================================================== suy diễn nghiệp vụ

    /**
     * Mục B — vi sinh bắt buộc khi VAC/CWD không đạt, HOẶC nghi ngờ nhiễm vi sinh,
     * HOẶC khách hàng yêu cầu. Đúng ghi chú trang 2 của biểu mẫu.
     *
     * <p>Lưu ý: KHÔNG suy ra từ A10 — đó là lỗi của bản phương án v1.
     */
    public boolean isSkypecMicrobialRequired() {
        return vac == QcCheck.NOT_SATISFY
                || cwd == QcCheck.NOT_SATISFY
                || contaminationSuspected
                || customerRequestedMicrobial;
    }

    /**
     * Nhiên liệu bị coi là có vấn đề chất lượng khi VAC/CWD không đạt, hoặc kết quả
     * vi sinh của SKYPEC ở mức cảnh báo/nặng.
     */
    public boolean hasQualityIssue() {
        return vac == QcCheck.NOT_SATISFY
                || cwd == QcCheck.NOT_SATISFY
                || skypecMicrobialResult == MicrobialResult.WARNING
                || skypecMicrobialResult == MicrobialResult.ACTION;
    }

    /**
     * C10 bắt buộc khi KHÔNG nạp lại ngay được, HOẶC nhiên liệu có vấn đề chất lượng.
     * (v1 sai khi chỉ ràng buộc theo C9 = NO.)
     */
    public boolean isHandlingRequired() {
        return Boolean.FALSE.equals(refuellableWithoutTest) || hasQualityIssue();
    }

    /** Phiếu còn sửa được (autosave chỉ ghi khi true). */
    public boolean isEditable() {
        return businessStatus != null && businessStatus.isEditable();
    }

    public static BM7501Model fromJson(String json) {
        return gson.fromJson(json, BM7501Model.class);
    }

    // ======================================================== enum nghiệp vụ

    /** Trạng thái nghiệp vụ — tách hẳn khỏi {@link SyncStatus}. */
    public enum BusinessStatus {
        DRAFT,
        A_DONE,
        B_DONE,
        C_DONE,
        SIGNED,
        PRINTED,
        /** Hủy TRƯỚC khi ký — không trở thành chứng từ hoàn tất. */
        CANCELLED,
        /** Vô hiệu hóa SAU khi đã ký/in — bắt buộc lý do, người thực hiện, thời gian. */
        VOIDED;

        public boolean isEditable() {
            return this == DRAFT || this == A_DONE || this == B_DONE || this == C_DONE;
        }

        /** Đã trở thành chứng từ pháp lý, không được sửa nội dung. */
        public boolean isLegalDocument() {
            return this == SIGNED || this == PRINTED || this == VOIDED;
        }

        public boolean isTerminal() {
            return this == CANCELLED || this == VOIDED;
        }
    }

    /** Trạng thái đồng bộ — độc lập với {@link BusinessStatus}. */
    public enum SyncStatus {
        NOT_READY,
        PENDING,
        SYNCING,
        SYNCED,
        FAILED
    }

    /** A9 — lý do hút. */
    public enum DefuelReason {
        LOAD_ADJUSTMENT,
        MAINTENANCE,
        OTHER
    }

    public enum TriState {
        YES,
        NO,
        UNKNOWN
    }

    public enum MicrobialKit {
        HY_LITE,
        MICROB_MONITOR2,
        FUELSTAT,
        OTHER
    }

    public enum MicrobialResult {
        NORMAL,
        WARNING,
        ACTION
    }

    /** Kết quả kiểm tra ngoại quan / viên thử nước. */
    public enum QcCheck {
        SATISFY,
        NOT_SATISFY
    }

    /** A13 — nhóm phụ gia; PRESENT thì mới liệt kê {@link Additive}. */
    public enum AdditivePresence {
        PRESENT,
        NONE,
        UNDETERMINED
    }

    public enum Additive {
        FSII,
        BIOCIDE,
        AQUARIUS_WMA
    }

    /** C3 — phương thức hút. Biểu mẫu ghi rõ ưu tiên bơm của tàu bay. */
    public enum DefuelMethod {
        AIRCRAFT_PUMP,
        REFUELLER_PUMP,
        BOTH
    }

    /** C10 — phương án xử lý lượng nhiên liệu đã hút. */
    public enum HandlingOption {
        STORAGE,
        SAME_AIRCRAFT,
        OTHER_AIRCRAFT_SAME_AIRLINE,
        AUTHORIZE_SKYPEC,
        REFUEL_DESPITE_ISSUE
    }

    // ============================================================ getter/setter

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getRefuelItemUniqueId() { return refuelItemUniqueId; }
    public void setRefuelItemUniqueId(String refuelItemUniqueId) { this.refuelItemUniqueId = refuelItemUniqueId; }

    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }

    public String getSupersedesUniqueId() { return supersedesUniqueId; }
    public void setSupersedesUniqueId(String supersedesUniqueId) { this.supersedesUniqueId = supersedesUniqueId; }

    public String getLocalNumber() { return localNumber; }
    public void setLocalNumber(String localNumber) { this.localNumber = localNumber; }

    public String getServerNumber() { return serverNumber; }
    public void setServerNumber(String serverNumber) { this.serverNumber = serverNumber; }

    public int getLocalRevision() { return localRevision; }
    public void setLocalRevision(int localRevision) { this.localRevision = localRevision; }

    public BusinessStatus getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(BusinessStatus businessStatus) { this.businessStatus = businessStatus; }

    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }

    public int getEnteredByUserId() { return enteredByUserId; }
    public void setEnteredByUserId(int enteredByUserId) { this.enteredByUserId = enteredByUserId; }

    public String getEnteredByUserName() { return enteredByUserName; }
    public void setEnteredByUserName(String enteredByUserName) { this.enteredByUserName = enteredByUserName; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public int getAirlineId() { return airlineId; }
    public void setAirlineId(int airlineId) { this.airlineId = airlineId; }

    public String getAirlineName() { return airlineName; }
    public void setAirlineName(String airlineName) { this.airlineName = airlineName; }

    public int getAirportId() { return airportId; }
    public void setAirportId(int airportId) { this.airportId = airportId; }

    public String getAirportName() { return airportName; }
    public void setAirportName(String airportName) { this.airportName = airportName; }

    public int getTruckId() { return truckId; }
    public void setTruckId(int truckId) { this.truckId = truckId; }

    public String getCustomerRepName() { return customerRepName; }
    public void setCustomerRepName(String customerRepName) { this.customerRepName = customerRepName; }

    public String getCustomerTitle() { return customerTitle; }
    public void setCustomerTitle(String customerTitle) { this.customerTitle = customerTitle; }

    public String getCustomerTel() { return customerTel; }
    public void setCustomerTel(String customerTel) { this.customerTel = customerTel; }

    public String getCustomerFax() { return customerFax; }
    public void setCustomerFax(String customerFax) { this.customerFax = customerFax; }

    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String aircraftType) { this.aircraftType = aircraftType; }

    public String getAircraftReg() { return aircraftReg; }
    public void setAircraftReg(String aircraftReg) { this.aircraftReg = aircraftReg; }

    public DefuelReason getReason() { return reason; }
    public void setReason(DefuelReason reason) { this.reason = reason; }

    public String getReasonOther() { return reasonOther; }
    public void setReasonOther(String reasonOther) { this.reasonOther = reasonOther; }

    public Boolean getTankDrainSampled() { return tankDrainSampled; }
    public void setTankDrainSampled(Boolean tankDrainSampled) { this.tankDrainSampled = tankDrainSampled; }

    public TriState getCustomerMicrobialTestPerformed() { return customerMicrobialTestPerformed; }
    public void setCustomerMicrobialTestPerformed(TriState v) { this.customerMicrobialTestPerformed = v; }

    public MicrobialKit getCustomerMicrobialKit() { return customerMicrobialKit; }
    public void setCustomerMicrobialKit(MicrobialKit v) { this.customerMicrobialKit = v; }

    public String getCustomerMicrobialKitOther() { return customerMicrobialKitOther; }
    public void setCustomerMicrobialKitOther(String v) { this.customerMicrobialKitOther = v; }

    public MicrobialResult getCustomerMicrobialResult() { return customerMicrobialResult; }
    public void setCustomerMicrobialResult(MicrobialResult v) { this.customerMicrobialResult = v; }

    public AdditivePresence getAdditivePresence() { return additivePresence; }
    public void setAdditivePresence(AdditivePresence additivePresence) { this.additivePresence = additivePresence; }

    public List<Additive> getAdditives() { return additives; }
    public void setAdditives(List<Additive> additives) {
        this.additives = additives == null ? new ArrayList<Additive>() : additives;
    }

    public String getPrevLocation1() { return prevLocation1; }
    public void setPrevLocation1(String prevLocation1) { this.prevLocation1 = prevLocation1; }

    public String getPrevGrade1() { return prevGrade1; }
    public void setPrevGrade1(String prevGrade1) { this.prevGrade1 = prevGrade1; }

    public String getPrevLocation2() { return prevLocation2; }
    public void setPrevLocation2(String prevLocation2) { this.prevLocation2 = prevLocation2; }

    public String getPrevGrade2() { return prevGrade2; }
    public void setPrevGrade2(String prevGrade2) { this.prevGrade2 = prevGrade2; }

    public QcCheck getVac() { return vac; }
    public void setVac(QcCheck vac) { this.vac = vac; }

    public QcCheck getCwd() { return cwd; }
    public void setCwd(QcCheck cwd) { this.cwd = cwd; }

    public Double getDensityKgM3() { return densityKgM3; }
    public void setDensityKgM3(Double densityKgM3) { this.densityKgM3 = densityKgM3; }

    public boolean isConductivityRequired() { return conductivityRequired; }
    public void setConductivityRequired(boolean conductivityRequired) { this.conductivityRequired = conductivityRequired; }

    public Double getConductivityPsM() { return conductivityPsM; }
    public void setConductivityPsM(Double conductivityPsM) { this.conductivityPsM = conductivityPsM; }

    public boolean isContaminationSuspected() { return contaminationSuspected; }
    public void setContaminationSuspected(boolean v) { this.contaminationSuspected = v; }

    public boolean isCustomerRequestedMicrobial() { return customerRequestedMicrobial; }
    public void setCustomerRequestedMicrobial(boolean v) { this.customerRequestedMicrobial = v; }

    public MicrobialKit getSkypecMicrobialKit() { return skypecMicrobialKit; }
    public void setSkypecMicrobialKit(MicrobialKit v) { this.skypecMicrobialKit = v; }

    public MicrobialResult getSkypecMicrobialResult() { return skypecMicrobialResult; }
    public void setSkypecMicrobialResult(MicrobialResult v) { this.skypecMicrobialResult = v; }

    public String getMicrobialReason() { return microbialReason; }
    public void setMicrobialReason(String microbialReason) { this.microbialReason = microbialReason; }

    public String getDefuellerTruckNo() { return defuellerTruckNo; }
    public void setDefuellerTruckNo(String defuellerTruckNo) { this.defuellerTruckNo = defuellerTruckNo; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public DefuelMethod getMethod() { return method; }
    public void setMethod(DefuelMethod method) { this.method = method; }

    public boolean isSignalsBriefed() { return signalsBriefed; }
    public void setSignalsBriefed(boolean signalsBriefed) { this.signalsBriefed = signalsBriefed; }

    public String getOtherSignal() { return otherSignal; }
    public void setOtherSignal(String otherSignal) { this.otherSignal = otherSignal; }

    public Double getExpectedKg() { return expectedKg; }
    public void setExpectedKg(Double expectedKg) { this.expectedKg = expectedKg; }

    public Double getActualKg() { return actualKg; }
    public void setActualKg(Double actualKg) { this.actualKg = actualKg; }

    public Double getActualTempC() { return actualTempC; }
    public void setActualTempC(Double actualTempC) { this.actualTempC = actualTempC; }

    public Double getActualDensityKgM3() { return actualDensityKgM3; }
    public void setActualDensityKgM3(Double actualDensityKgM3) { this.actualDensityKgM3 = actualDensityKgM3; }

    public Double getGallon() { return gallon; }
    public void setGallon(Double gallon) { this.gallon = gallon; }

    public Double getLiter() { return liter; }
    public void setLiter(Double liter) { this.liter = liter; }

    public Boolean getRefuellableWithoutTest() { return refuellableWithoutTest; }
    public void setRefuellableWithoutTest(Boolean v) { this.refuellableWithoutTest = v; }

    public HandlingOption getHandling() { return handling; }
    public void setHandling(HandlingOption handling) { this.handling = handling; }

    public Date getStorageFrom() { return storageFrom; }
    public void setStorageFrom(Date storageFrom) { this.storageFrom = storageFrom; }

    public Date getStorageTo() { return storageTo; }
    public void setStorageTo(Date storageTo) { this.storageTo = storageTo; }

    public String getHandlingNote() { return handlingNote; }
    public void setHandlingNote(String handlingNote) { this.handlingNote = handlingNote; }

    public String getCustomerRepFinalName() { return customerRepFinalName; }
    public void setCustomerRepFinalName(String v) { this.customerRepFinalName = v; }

    public String getSkypecRepName() { return skypecRepName; }
    public void setSkypecRepName(String skypecRepName) { this.skypecRepName = skypecRepName; }

    public String getCustomerSectionASignaturePath() { return customerSectionASignaturePath; }
    public void setCustomerSectionASignaturePath(String v) { this.customerSectionASignaturePath = v; }

    public String getCustomerSectionASignatureSha256() { return customerSectionASignatureSha256; }
    public void setCustomerSectionASignatureSha256(String v) { this.customerSectionASignatureSha256 = v; }

    public String getCustomerFinalSignaturePath() { return customerFinalSignaturePath; }
    public void setCustomerFinalSignaturePath(String v) { this.customerFinalSignaturePath = v; }

    public String getCustomerFinalSignatureSha256() { return customerFinalSignatureSha256; }
    public void setCustomerFinalSignatureSha256(String v) { this.customerFinalSignatureSha256 = v; }

    public String getSkypecSignaturePath() { return skypecSignaturePath; }
    public void setSkypecSignaturePath(String v) { this.skypecSignaturePath = v; }

    public String getSkypecSignatureSha256() { return skypecSignatureSha256; }
    public void setSkypecSignatureSha256(String v) { this.skypecSignatureSha256 = v; }

    public Date getSignedAt() { return signedAt; }
    public void setSignedAt(Date signedAt) { this.signedAt = signedAt; }

    public Date getPrintedAt() { return printedAt; }
    public void setPrintedAt(Date printedAt) { this.printedAt = printedAt; }

    public int getReprintCount() { return reprintCount; }
    public void setReprintCount(int reprintCount) { this.reprintCount = reprintCount; }

    public String getSignedSnapshotHash() { return signedSnapshotHash; }
    public void setSignedSnapshotHash(String signedSnapshotHash) { this.signedSnapshotHash = signedSnapshotHash; }

    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public Date getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Date voidedAt) { this.voidedAt = voidedAt; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public int getVoidedByUserId() { return voidedByUserId; }
    public void setVoidedByUserId(int voidedByUserId) { this.voidedByUserId = voidedByUserId; }
}
