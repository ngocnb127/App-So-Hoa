package com.megatech.fms.data;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.CloseGuard;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.CheckTrucks;
import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.data.entity.Invoice;
import com.megatech.fms.data.entity.LogEntry;
import com.megatech.fms.data.entity.ParkingLot;
import com.megatech.fms.data.entity.Product;
import com.megatech.fms.data.entity.Receipt;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.data.entity.Review;
import com.megatech.fms.data.entity.Shift;
import com.megatech.fms.data.entity.Truck;
import com.megatech.fms.data.entity.TruckFuel;
import com.megatech.fms.data.entity.User;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM2504Model;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.LogEntryModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.ReviewModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class DataRepository {


    private final AppDatabase db;
    private static DataRepository sInstance;

    private DataRepository(final AppDatabase db) {
        this.db = db;
    }

    /**
     * Chỉ dành cho test: tạo repository trên một AppDatabase riêng (thường là in-memory)
     * mà không đụng tới singleton dùng trong production.
     */
    @androidx.annotation.VisibleForTesting
    public static DataRepository forTesting(AppDatabase db) {
        return new DataRepository(db);
    }

    public static DataRepository getInstance(AppDatabase db) {
        if (sInstance == null) {
            synchronized (DataRepository.class) {
                if (sInstance == null) {
                    sInstance = new DataRepository(db);
                }
            }
        }
        return sInstance;
    }

    public List<ParkingLot> getParkingLot() {
        List<ParkingLot> lst = db.parkingLotDao().getAll();
        if (lst.size() == 0) {
            HttpClient client = new HttpClient();
            List<ParkingLot> parkingLots = client.getParking();
            db.parkingLotDao().insertAll(parkingLots);
        }
        return lst;
    }

    public List<ParkingLot> getParkingLot(int airportId) {
        return db.parkingLotDao().getAll(airportId);
    }

    public List<TruckModel> getTrucks() {
        List<Truck>  lst = db.truckDao().getAll();
        List<TruckModel> lstModel = new ArrayList<>();
        for (Truck item: lst)
        {
            lstModel.add(item.toTruckModel());
        }
        return lstModel;
    }
    public List<TruckModel> getFHSTrucks() {
        List<Truck>  lst = db.truckDao().getFHS();
        List<TruckModel> lstModel = new ArrayList<>();
        for (Truck item: lst)
        {
            lstModel.add(item.toTruckModel());
        }
        return lstModel;
    }
    public void insertTruck(Truck truckModel) {
        Truck item = db.truckDao().get(truckModel.getId());
        if (item == null)
        {
            db.truckDao().insert(truckModel);
        }
        else {
            item.setJsonData(truckModel.getJsonData());
            db.truckDao().update(item);
        }
    }
    public void insertAirports(Airports AirportsModel) {
        Airports item = db.AirportsDao().get(AirportsModel.getId());
        if (item == null)
        {
            db.AirportsDao().insert(AirportsModel);
        }
        else {
            item.setJsonData(AirportsModel.getJsonData());
            db.AirportsDao().update(item);
        }
    }

    public void deleteOudateTrucks(int[] ids) {
        db.truckDao().deleteNotIds(ids);
    }

    public void deleteOudateAirports(int[] ids) {
        db.AirportsDao().deleteNotIds(ids);
    }

    public void deleteOudateShift(int[] ids) {
        db.shiftDao().deleteNotIds(ids);
    }


    public List<RefuelItemData> getRefuelList(String truckNo, int truckId, boolean self, int type, long start, long end) {
        List<RefuelItem> localList;
        if (self)
            localList = db.refuelItemDao().getByTruckNo(truckNo, start, end, type);
        else
            localList = db.refuelItemDao().getOthers(truckNo, start, end);

        return toRefuelList(localList);
    }

    public List<RefuelItemData> getRefuelList(String truckNo, int truckId, boolean self, int type) {
        List<RefuelItem> localList;
        if (self)
            localList = db.refuelItemDao().getByTruckNo(truckNo);
        else
            localList = db.refuelItemDao().getOthers(truckNo);
        return toRefuelList(localList);
    }

    /**
     * Danh sách phiếu của xe đọc THẲNG từ Room, không đi qua mạng và không lọc theo ca.
     *
     * <p>{@link #getRefuelList} bản tương ứng ở {@code DataHelper} gọi HTTP khi không phải
     * bản debug, nên không dùng được cho các kiểm tra phải chạy được cả khi mất sóng.
     * {@code approachTime}/{@code leaveTime} chỉ nằm trong {@code jsonData} chứ không phải
     * cột Room, nên lọc theo hai trường đó phải làm ở tầng Java sau khi giải mã.
     */
    public List<RefuelItemData> getLocalRefuelList(String truckNo) {
        return toRefuelList(db.refuelItemDao().getByTruckNo(truckNo));
    }

    private List<RefuelItemData> toRefuelList(List<RefuelItem> localList) {

        List<RefuelItemData> returnList = new ArrayList();
        for (RefuelItem item: localList) {
            returnList.add(item.toRefuelItemData());
        }
        return returnList;
    }

    public void insertRefuel(RefuelItem localItem) {

        RefuelItem item = getRefuel(localItem.getId(), localItem.getLocalId());
        if (item == null) {

            localItem.setLocalId((int) db.refuelItemDao().insert(localItem));
        } else {

            //item.setJsonData(localItem.getJsonData());
            //item.setLocalModified(true);
            localItem.setLocalId(item.getLocalId());
            db.refuelItemDao().update(localItem);

        }
    }

    /** Cho phép tầng điều phối gom nhiều thao tác repository vào một transaction Room. */
    public void runInTransaction(Runnable action) {
        db.runInTransaction(action);
    }

    /** Thay membership Others mà không làm row thành localModified. */
    public boolean replaceRemoteOthersMembership(String rootUniqueId, List<String> childUids) {
        if (rootUniqueId == null || rootUniqueId.trim().isEmpty() || childUids == null)
            return false;

        JSONArray json = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (String uid : childUids) {
            if (uid == null || uid.trim().isEmpty() || !seen.add(uid)) return false;
            json.put(uid);
        }
        return db.refuelItemDao().updateRemoteOthersMembership(
                rootUniqueId, json.toString()) == 1;
    }

    /** Null = chưa có snapshot membership; list rỗng là một snapshot rỗng hợp lệ. */
    @Nullable
    public List<String> getRemoteOthersMembership(String rootUniqueId) {
        RefuelItem root = getRefuel(rootUniqueId);
        if (root == null || root.getRemoteOthersUidsJson() == null) return null;
        try {
            JSONArray json = new JSONArray(root.getRemoteOthersUidsJson());
            List<String> result = new ArrayList<>(json.length());
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < json.length(); i++) {
                String uid = json.optString(i, null);
                if (uid == null || uid.trim().isEmpty() || !seen.add(uid)) return null;
                result.add(uid);
            }
            return result;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Lưu nguyên một snapshot các mẻ do XE KHÁC thực hiện.
     *
     * <p>Máy này không sở hữu và không được sửa các mẻ đó, vì vậy không được dùng đường
     * merge dành cho mẻ của chính máy. Mỗi row được thay bằng nguyên payload server, chỉ giữ
     * lại {@code localId} của Room. Toàn bộ danh sách được kiểm tra trước rồi ghi trong cùng
     * một transaction để màn hình không nhìn thấy nửa snapshot cũ, nửa snapshot mới.
     *
     * <p>{@link RefuelItemData#getRawJson()} được ưu tiên để không làm mất những trường server
     * biết nhưng model của app chưa biết. Response cũ hơn theo {@code ServerRevision} không
     * được phép kéo một bản sao mới hơn lùi lại. Row mới hơn được giữ, các row còn lại được
     * cập nhật trong cùng transaction để UI luôn thấy một collection nhất quán theo từng UID.
     *
     * @return số snapshot đã xử lý (ghi mới/thay thế hoặc chủ ý giữ bản local mới hơn)
     * @throws IllegalArgumentException nếu response thiếu định danh hoặc có UID trùng nhau;
     *                                  khi đó transaction chưa ghi bất kỳ item nào
     */
    public static final class RemoteSnapshotResult {
        public final int applied;
        public final int keptNewer;

        RemoteSnapshotResult(int applied, int keptNewer) {
            this.applied = applied;
            this.keptNewer = keptNewer;
        }

        public int handled() {
            return applied + keptNewer;
        }
    }

    /**
     * Bản tương thích cho các caller chỉ cần biết toàn bộ batch đã được xử lý hay chưa.
     * Caller cần quyết định có được dùng metadata đi kèm (Flight/Others) phải gọi bản
     * detailed bên dưới: một snapshot cũ được giữ lại là "handled", nhưng tuyệt đối không
     * được lấy Flight hay membership từ chính payload cũ đó.
     */
    public int replaceRemoteRefuelSnapshots(List<RefuelItemData> snapshots) {
        return replaceRemoteRefuelSnapshotsDetailed(snapshots).handled();
    }

    public RemoteSnapshotResult replaceRemoteRefuelSnapshotsDetailed(
            List<RefuelItemData> snapshots) {
        if (snapshots == null || snapshots.isEmpty())
            return new RemoteSnapshotResult(0, 0);

        Set<String> uniqueIds = new HashSet<>();
        Set<Integer> serverIds = new HashSet<>();
        for (RefuelItemData snapshot : snapshots) {
            if (snapshot == null
                    || snapshot.getUniqueId() == null
                    || snapshot.getUniqueId().trim().isEmpty()) {
                throw new IllegalArgumentException("Remote refuel snapshot thiếu UniqueId");
            }
            if (snapshot.getStatus() == null || snapshot.getRefuelItemType() == null) {
                throw new IllegalArgumentException(
                        "Remote refuel snapshot thiếu trạng thái/loại: "
                                + snapshot.getUniqueId());
            }
            if (snapshot.getRawJson() == null || snapshot.getRawJson().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Remote refuel snapshot thiếu raw JSON: " + snapshot.getUniqueId());
            }
            try {
                JSONObject raw = new JSONObject(snapshot.getRawJson());
                String rawUid = raw.optString("UniqueId", null);
                if (rawUid == null || !snapshot.getUniqueId().equals(rawUid)) {
                    throw new IllegalArgumentException(
                            "Remote refuel raw JSON sai UniqueId: " + snapshot.getUniqueId());
                }
                if (raw.has("Id") && !raw.isNull("Id") && snapshot.getId() > 0
                        && raw.optInt("Id", 0) != snapshot.getId()) {
                    throw new IllegalArgumentException(
                            "Remote refuel raw JSON sai Id: " + snapshot.getUniqueId());
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Remote refuel raw JSON không hợp lệ: " + snapshot.getUniqueId(), ex);
            }
            if (!uniqueIds.add(snapshot.getUniqueId())) {
                throw new IllegalArgumentException(
                        "Remote refuel snapshot trùng UniqueId: " + snapshot.getUniqueId());
            }
            if (snapshot.getId() > 0 && !serverIds.add(snapshot.getId())) {
                throw new IllegalArgumentException(
                        "Remote refuel snapshot trùng Id: " + snapshot.getId());
            }
        }

        final int[] applied = {0};
        final int[] keptNewer = {0};
        db.runInTransaction(() -> {
            List<RefuelItem> currentRows = new ArrayList<>(snapshots.size());
            List<Boolean> keepCurrentRows = new ArrayList<>(snapshots.size());

            // Preflight TOÀN BỘ trước khi ghi. Identity lỗi làm transaction fail; response
            // cũ theo từng UID chỉ giữ row hiện có, không được kéo nó lùi lại.
            for (RefuelItemData snapshot : snapshots) {
                RefuelItem byUid = db.refuelItemDao().get(snapshot.getUniqueId());
                RefuelItem byId = snapshot.getId() > 0
                        ? db.refuelItemDao().get(snapshot.getId()) : null;

                // Luôn kiểm cả hai chiều, kể cả đã tìm thấy UID. Nếu không, payload A mang
                // nhầm Id của B sẽ update A thành cùng Id với B và các lookup sau có thể lấy
                // ngẫu nhiên sai row (cột server Id không có UNIQUE constraint).
                if (byId != null
                        && !snapshot.getUniqueId().equals(byId.getUniqueId())) {
                    throw new IllegalArgumentException(
                            "Remote refuel Id trùng UID khác: id=" + snapshot.getId());
                }
                if (byUid != null && snapshot.getId() > 0 && byUid.getId() > 0
                        && byUid.getId() != snapshot.getId()) {
                    throw new IllegalArgumentException(
                            "Remote refuel UID đổi Id: uid=" + snapshot.getUniqueId()
                                    + " oldId=" + byUid.getId()
                                    + " newId=" + snapshot.getId());
                }
                if (byUid != null && byId != null
                        && byUid.getLocalId() != byId.getLocalId()) {
                    throw new IllegalArgumentException(
                            "Remote refuel UID/Id trỏ hai row khác nhau: uid="
                                    + snapshot.getUniqueId());
                }
                RefuelItem current = byUid != null ? byUid : byId;
                currentRows.add(current);

                // Một GET có thể đi qua replica/cache cũ. Chỉ dùng revision khi cả hai phía
                // thực sự có giá trị; 0 trên server legacy nghĩa là "không có thông tin".
                boolean staleRevision = current != null
                        && current.getServerRevision() > 0
                        && snapshot.getServerRevision() > 0
                        && snapshot.getServerRevision() < current.getServerRevision();

                // Revision 0 trên server legacy nghĩa là "không có thông tin". Trong ca
                // đó DateUpdated là dấu thứ tự duy nhất còn lại để một modified-list trả
                // chậm không phủ ngược lên GET chi tiết vừa lưu. Revision tăng thật vẫn
                // thắng DateUpdated để chịu được lệch đồng hồ phía server.
            // Revision dương là version có thẩm quyền và phải thắng row legacy revision 0,
            // kể cả DateUpdated của server bị lệch/cũ hơn.
            boolean higherRevision = current != null
                    && snapshot.getServerRevision() > 0
                    && snapshot.getServerRevision() > current.getServerRevision();
                boolean staleUpdatedAt = current != null && !higherRevision
                        && current.getDateUpdated() != null
                        && snapshot.getDateUpdated() != null
                        && snapshot.getDateUpdated().before(current.getDateUpdated());

                // Không để một response đứng yên/lùi revision mở lại mẻ đã chốt. Khi server
                // có revision cao hơn thì đó là thay đổi có chủ ý và được nhận nguyên bản.
                boolean staleDoneDowngrade = current != null
                        && current.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                        && snapshot.getStatus() != com.megatech.fms.model.REFUEL_ITEM_STATUS.DONE
                        && snapshot.getServerRevision() <= current.getServerRevision();
                keepCurrentRows.add(staleRevision || staleUpdatedAt || staleDoneDowngrade);
            }

            for (int i = 0; i < snapshots.size(); i++) {
                RefuelItemData snapshot = snapshots.get(i);
                RefuelItem current = currentRows.get(i);
                if (keepCurrentRows.get(i)) {
                    keptNewer[0]++;
                    continue;
                }
                RefuelItem replacement = RefuelItem.fromRefuelItemData(snapshot);
                replacement.setJsonData(snapshot.getRawJson());
                // Full replace phải truyền được cả null; projectColumnsFrom cố ý bỏ qua null
                // cho đường merge của xe hiện tại nên cần đóng dấu lại ở đường replica này.
                replacement.setStartTime(snapshot.getStartTime());
                replacement.setEndTime(snapshot.getEndTime());
                replacement.setDeleted(snapshot.isDeleted());

                if (current != null) {
                    replacement.setLocalId(current.getLocalId());
                    // Membership là metadata cache của endpoint root trên chính UID này,
                    // không nằm trong payload child nên full-replace không được làm mất.
                    replacement.setRemoteOthersUidsJson(current.getRemoteOthersUidsJson());
                    if (replacement.getId() <= 0) replacement.setId(current.getId());
                }

                // Server legacy dùng 0 cho version "không có thông tin". Giữ dấu mốc kỹ
                // thuật đã quan sát ở cột Room để lần GET cũ sau còn bị nhận diện; payload
                // nghiệp vụ raw JSON vẫn là nguyên bản server.
                if (current != null && snapshot.getClientSeq() <= 0)
                    replacement.setClientSeq(current.getClientSeq());
                if (current != null && snapshot.getServerRevision() <= 0)
                    replacement.setServerRevision(current.getServerRevision());

                // Remote replica không bao giờ được lọt vào hàng đợi POST của máy này.
                replacement.setLocalModified(false);
                replacement.setSynced(true);
                replacement.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                replacement.setRemoteReplica(true);

                if (current == null) {
                    // localId thuộc riêng DB trên tablet. Payload server/JSON không có quyền
                    // chọn primary key; giữ nó có thể khiến INSERT(REPLACE) xoá nhầm một row
                    // hoàn toàn khác đang dùng cùng localId.
                    replacement.setLocalId(0);
                    replacement.setLocalId((int) db.refuelItemDao().insert(replacement));
                } else {
                    db.refuelItemDao().update(replacement);
                }
                applied[0]++;
            }
        });
        return new RemoteSnapshotResult(applied[0], keptNewer[0]);
    }

    public RefuelItem getRefuel(String uniqueId) {
        RefuelItem item = null;

        if (uniqueId != null && !uniqueId.isEmpty())
            item = db.refuelItemDao().get(uniqueId);
        return item;
    }
    public RefuelItem getRefuel(Integer id, int localId) {
        RefuelItem item = null;

        if (id !=0)
            item = db.refuelItemDao().get(id);
        if (item == null && localId != 0)
            item = db.refuelItemDao().getLocal(localId);
        return  item;
    }

    public int[] getNotChangedRefuels() {
        return  db.refuelItemDao().getNotChanges();
    }

    public void removeDeletedRefuels(int[] ids) {
        db.refuelItemDao().removeDeleted(ids);
    }

    public boolean removeRemoteDeletedRefuel(RefuelItem item) {
        return item != null
                && db.refuelItemDao().removeRemoteDeletedLocal(item.getLocalId()) == 1;
    }

    public List<RefuelItem> getModifiedRefuel() {
        List<RefuelItem> modified = db.refuelItemDao().getModifiedForSync();
        return modified;
    }

    /** Toàn bộ row còn thay đổi chưa gửi, kể cả row đang giữ conflict — dùng để hiển thị. */
    public List<RefuelItem> getAllModifiedRefuel() {
        return db.refuelItemDao().getModified();
    }

    /** Toàn bộ phiếu — chỉ dùng cho bảo trì một lần khi nâng cấp (chiếu lại cột từ JSON). */
    public List<RefuelItem> getAllRefuels() {
        return db.refuelItemDao().getAll();
    }

    public List<AirlineModel> getAirlines() {
        List<Airline> localList = db.airlineDao().getAll();
        List<AirlineModel> returnList = new ArrayList();
        for (Airline item : localList) {
            returnList.add(item.toAirlineModel());
        }
        return returnList;
    }

    public void insertAirline(Airline model) {
        Airline item = db.airlineDao().get(model.getId());
        if (item == null) {
            db.airlineDao().insert(model);
        } else {
            //item.setJsonData(truckModel.getJsonData());
            model.setLocalId(item.getLocalId());
            db.airlineDao().update(model);
        }
    }

    //Users
    public List<UserModel> getUsers() {
        List<User> localList = db.userDao().getAll();
        List<UserModel> returnList = new ArrayList();
        for (User item : localList) {
            returnList.add(item.toUserModel());
        }
        return returnList;
    }

    public void insertUser(User model) {
        User item = db.userDao().get(model.getId());
        if (item == null) {
            db.userDao().insert(model);
        } else {
            //item.setJsonData(truckModel.getJsonData());
            model.setLocalId(item.getLocalId());
            db.userDao().update(model);
        }
    }

    //Shift
    public List<ShiftModel> getShifts() {
        List<Shift> localList = db.shiftDao().getAll();
        List<ShiftModel> returnList = new ArrayList();
        for (Shift item : localList) {
            returnList.add(item.toShiftModel());
        }
        return returnList;
    }

    public void insertShift(Shift model) {
        Shift item = db.shiftDao().get(model.getId());
        if (item == null) {
            db.shiftDao().insert(model);
        } else {
            //item.setJsonData(truckModel.getJsonData());
            model.setLocalId(item.getLocalId());
            db.shiftDao().update(model);
        }
    }


    public Date getLastModifiedRefuel() {
        return db.refuelItemDao().getLastModifiedDate();
    }

    public List<RefuelItem> getOthers(int localId) {

        return db.refuelItemDao().getOthers(localId);

    }
    public List<RefuelItem> getOthers(String uniqueId) {

        return db.refuelItemDao().getOtherItems(uniqueId);

    }
    public void deleteOldRefuels(int numDays) {

        // 24L: phép nhân phải làm ở kiểu long. Với int, 24*60*60*1000*numDays TRÀN từ
        // numDays = 25 trở lên và đổi dấu — mốc cắt nhảy sang tương lai và câu lệnh xoá
        // gần như toàn bộ phiếu. Đổi 10 thành 30 với ý "giữ lâu hơn" là mất sạch dữ liệu.
        Date d = new Date();
        d = new Date(d.getTime() - 24L * 60 * 60 * 1000 * numDays);

        db.refuelItemDao().deleteByDate(d.getTime());
    }

    /**
     * Đưa các phiếu đang kẹt ở trạng thái conflict trở lại hàng đợi đồng bộ.
     *
     * @return số phiếu được đưa trở lại
     */
    public int resumeConflictedRefuels() {
        return db.refuelItemDao().resumeConflictedRows();
    }

    public RefuelItemData getIncomplete(String truckNo) {
        Date d = new Date();
        RefuelItem item =  db.refuelItemDao().getIncomplete(truckNo, d.getTime() - 1000 * 60 * 60 * 24);
        if (item!=null)
            return item.toRefuelItemData();
        else return  null;
    }

    public List<TruckFuelModel> getTruckFuels(Date date) {
        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE,1);
        long end = cal.getTime().getTime();
        List<TruckFuel> localList = db.truckFuelDao().getAll(start, end);
        List<TruckFuelModel> returnList = new ArrayList();
        for (TruckFuel item : localList) {
            returnList.add(item.toTruckFuelModel());
        }
        return returnList;
    }

    /** Toàn bộ phiếu 2502 còn trong máy, không giới hạn ngày (dùng để tìm phiếu mới nhất). */
    public List<TruckFuelModel> getAllTruckFuels() {
        List<TruckFuelModel> returnList = new ArrayList();
        for (TruckFuel item : db.truckFuelDao().getAll()) {
            returnList.add(item.toTruckFuelModel());
        }
        return returnList;
    }

    public void insertTruckFuel(TruckFuel model) {
        // Đọc rồi ghi trong một giao dịch: nếu worker đồng bộ vừa gắn id server vào dòng
        // giữa hai bước, bản lưu này không được ghi id = 0 đè lại (sẽ POST tạo phiếu trùng).
        db.runInTransaction(() -> {
            TruckFuel item = db.truckFuelDao().get(model.getId(), model.getLocalId());

            if (item == null || (item.getId() == 0 && item.getLocalId() != model.getLocalId())) {
                db.truckFuelDao().insert(model);
            } else {
                model.setLocalId(item.getLocalId());
                if (model.getId() == 0) {
                    model.setId(item.getId());
                }
                db.truckFuelDao().update(model);
            }
        });
    }

    /**
     * Ghi kết quả POST phiếu 2502 mà không đè bản người dùng lưu trong lúc gói còn đang gửi.
     *
     * <p>Trước đây worker ghi lại nguyên bản chụp đã gửi và xoá cờ chờ gửi: sửa số phiếu hoá
     * nghiệm đúng lúc đó thì bản sửa mất khỏi máy và không bao giờ lên server.
     *
     * @param posted bản chụp đúng như đã gửi lên server
     * @return true nếu dòng vẫn là bản đã gửi (đã xoá cờ chờ gửi); false nếu có bản mới hơn
     *         (chỉ gắn id server, giữ cờ để lượt sau gửi tiếp) hoặc dòng không còn
     */
    public boolean markTruckFuelSynced(TruckFuel posted, int serverId) {
        return db.runInTransaction(() -> {
            TruckFuel current = db.truckFuelDao().get(0, posted.getLocalId());
            if (current == null)
                return false;
            if (serverId > 0)
                current.setId(serverId);

            boolean unchanged = java.util.Objects.equals(current.getJsonData(), posted.getJsonData())
                    && current.isDeleted() == posted.isDeleted();
            if (unchanged) {
                TruckFuelModel synced = current.toTruckFuelModel();
                current.setJsonData(synced.toJson());
                current.setLocalModified(false);
            }
            db.truckFuelDao().update(current);
            return unchanged;
        });
    }

    public void mergeRemoteTruckFuel(TruckFuel remote) {
        // Kiểm tra cờ chờ gửi và ghi phải liền một khối, không để lần lưu của người dùng chen giữa.
        db.runInTransaction(() -> {
            TruckFuel local = db.truckFuelDao().get(remote.getId(), remote.getLocalId());
            if (local == null) {
                db.truckFuelDao().insert(remote);
            } else if (!local.isLocalModified()) {
                remote.setLocalId(local.getLocalId());
                db.truckFuelDao().update(remote);
            }
        });
    }


    public List<TruckFuel> getModifiedTruckFuel() {

        List<TruckFuel> modified = db.truckFuelDao().getModified();
        return modified;
    }

    public List<Invoice> getModifiedInvoice() {

        List<Invoice> modified = db.invoiceDao().getModified();
        return modified;
    }

    public void deleteTruckFuels(int[] ids) {
        db.truckFuelDao().delete(ids);
    }


    public <T> List<T> getModified()
    {
        return null;
    }

    /**
     * Ghi hoá đơn xuống Room và GHI NGƯỢC localId Room vừa sinh vào object.
     * Trước đây hàm trả void và chỉ tra theo (id, localId): sau khi POST xong, object Java vẫn
     * mang localId = 0 nên lượt ghi thứ hai (id = idServer) không khớp hàng cũ (id = 0) và ĐẺ
     * THÊM MỘT HÀNG; hàng cũ vẫn isLocalModified nên vòng Synchronize POST lại ⇒ trùng hoá đơn.
     * Thứ tự tra: localId (chắc chắn nhất) → uniqueId (định danh hàng) → (id, localId) như cũ.
     * Lưu ý: uniqueId sinh mới mỗi lần dựng InvoiceModel nên CHỈ dùng làm định danh hàng trong
     * Room, KHÔNG phải khoá idempotency xuyên nhiều lần dựng model.
     */
    public int insertInvoice(Invoice model) {
        final int[] rowId = new int[1];
        db.runInTransaction(() -> {
            Invoice item = null;
            if (model.getLocalId() > 0)
                item = db.invoiceDao().getByLocalId(model.getLocalId());
            if (item == null) {
                String uid = model.getUniqueId();
                if (uid != null && !uid.trim().isEmpty())
                    item = db.invoiceDao().getByUniqueId(uid);
            }
            if (item == null)
                item = db.invoiceDao().get(model.getId(), model.getLocalId());

            if (item == null) {
                model.setLocalId((int) db.invoiceDao().insert(model));
            } else {
                model.setLocalId(item.getLocalId());
                if (model.getId() == 0)
                    model.setId(item.getId());
                db.invoiceDao().update(model);
            }
            rowId[0] = model.getLocalId();
        });
        return rowId[0];
    }

    public List<BM2505Model> getBM2505List(Date date) {

        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE,1);
        long end = cal.getTime().getTime();
        List<BM2505> localList = db.bm2505Dao().getAll(start, end);
        List<BM2505Model> returnList = new ArrayList();
        for (BM2505 item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }

    public List<BM2508Model> getBM2508List(Date date) {

        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE,1);
        long end = cal.getTime().getTime();
        List<BM2508> localList = db.bm2508Dao().getAll(start, end);
        List<BM2508Model> returnList = new ArrayList();
        for (BM2508 item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }

    public List<CheckTrucksModel> getCheckTrucksList(Date date) {

        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE,1);
        long end = cal.getTime().getTime();
        List<CheckTrucks> localList = db.checkTrucksDao().getAll(start, end);
        List<BM2505> localListbm25 = db.bm2505Dao().getAll(start, end);
        List<CheckTrucksModel> returnList = new ArrayList();
        for (CheckTrucks item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }

    public void insertFlight(Flight model) {
        Flight item = db.flightDao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.flightDao().insert(model);
        } else {

            model.setLocalId(item.getLocalId());
            db.flightDao().update(model);
        }
    }

    /**
     * @return localId của bản ghi sau khi ghi (dùng để cập nhật lại model, tránh insert trùng
     * khi người dùng lưu nhiều lần trên cùng một phiếu).
     */
    public int insertBM2505(BM2505 model) {

        BM2505 item = db.bm2505Dao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            long newLocalId = db.bm2505Dao().insert(model);
            if (newLocalId > 0)
                model.setLocalId((int) newLocalId);
        } else {


            model.setLocalId(item.getLocalId());
            db.bm2505Dao().update(model);
        }
        return model.getLocalId();
    }
    public void mergeRemoteBM2505(BM2505 remote) {
        BM2505 local = db.bm2505Dao().get(remote.getId(), remote.getLocalId());
        if (local == null) {
            db.bm2505Dao().insert(remote);
        } else if (!local.isLocalModified()) {
            remote.setLocalId(local.getLocalId());
            db.bm2505Dao().update(remote);
        }
    }
    public void insertBM2508(BM2508 model) {

        BM2508 item = db.bm2508Dao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.bm2508Dao().insert(model);
        } else {


            model.setLocalId(item.getLocalId());
            db.bm2508Dao().update(model);
        }
    }
    public void mergeRemoteBM2508(BM2508 remote) {
        BM2508 local = db.bm2508Dao().get(remote.getId(), remote.getLocalId());
        if (local == null) {
            db.bm2508Dao().insert(remote);
        } else if (!local.isLocalModified() && !local.isAttachmentPending()) {
            remote.setLocalId(local.getLocalId());
            db.bm2508Dao().update(remote);
        }
    }
    public void insertCheckTrucks(CheckTrucks model) {

        CheckTrucks item = db.checkTrucksDao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.checkTrucksDao().insert(model);
        } else {


            model.setLocalId(item.getLocalId());
            db.checkTrucksDao().update(model);
        }
    }
    public void mergeRemoteCheckTrucks(CheckTrucks remote) {
        CheckTrucks local = db.checkTrucksDao().get(remote.getId(), remote.getLocalId());
        if (local == null) {
            db.checkTrucksDao().insert(remote);
        } else if (!local.isLocalModified()) {
            remote.setLocalId(local.getLocalId());
            db.checkTrucksDao().update(remote);
        }
    }

    public List<FlightModel> getFlights(Date date) {
        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // ✅ Lùi lại 1 ngày
        cal.add(Calendar.DATE, -1);
        long start = cal.getTimeInMillis();

        // ✅ Tiếp tục cộng thêm 2 ngày để lấy đến hết ngày hiện tại
        cal.add(Calendar.DATE, 2);
        long end = cal.getTimeInMillis();
        List<Flight> localList = db.flightDao().getAll(start, end);
        List<FlightModel> returnList = new ArrayList();
        for (Flight item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }

    public List<AirportsModel> getAirports() {
        List<Airports> localList = db.AirportsDao().getAll();
        List<AirportsModel> returnList = new ArrayList();
        for (Airports item : localList) {
            returnList.add(item.toAirportsModel());
        }
        return returnList;
    }

    public void deleteBM2505(int[] ids) {
        db.bm2505Dao().delete(ids);
    }
    public void deleteBM2508(int[] ids) {
        db.bm2508Dao().delete(ids);
    }

    public void deleteCheckTrucks(int[] ids) {
        db.checkTrucksDao().delete(ids);
    }

    public List<BM2505> getModifiedBM2505() {
        List<BM2505> modified = db.bm2505Dao().getModified();
        return modified;
    }
    public List<BM2508> getModifiedBM2508() {
        List<BM2508> modified = db.bm2508Dao().getModified();
        return modified;
    }

    public List<BM2508> getPendingBM2508Attachments() {
        return db.bm2508Dao().getPendingAttachments();
    }

    public List<CheckTrucks> getModifiedCheckTrucks() {
        List<CheckTrucks> modified = db.checkTrucksDao().getModified();
        return modified;
    }

    public int insertReceipt(Receipt model) {
        Receipt item = db.receiptDao().get(model.getNumber());

        if (item == null ) {
            return (int)db.receiptDao().insert(model);
        } else {

            model.setLocalId(item.getLocalId());
            model.setCancelled(item.isCancelled());
            model.setCancelReason(item.getCancelReason());
            return db.receiptDao().update(model);
        }
    }

    public boolean receiptNumberExists(String number) {
        return number != null && db.receiptDao().existsByNumber(number);
    }

    public List<Receipt> getModifiedReceipt() {

        List<Receipt> modified = db.receiptDao().getModified();
        return modified;
    }

    public void cancelReceipts(String[] printedItems, String reason ) {
        db.receiptDao().cancel(printedItems, reason);
    }

    public RefuelItem getRefuelByFlightAndTruck(Integer flightId, int truckId) {
        return db.refuelItemDao().getByFlightAndTruck(flightId,truckId);
    }

    public boolean getLocalModified() {
        SimpleSQLiteQuery query = new SimpleSQLiteQuery(
                "SELECT SUM(CNT) FROM (" +
                        "SELECT count(0) AS CNT FROM RefuelItem WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM Receipt WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM TruckFuel WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM Invoice WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM BM2503 WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM BM2504 WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM BM2505 WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM BM2508 WHERE isLocalModified = 1 UNION ALL " +
                        "SELECT count(0) FROM CheckTrucks WHERE isLocalModified = 1" +
                        ")");
        Cursor cs = db.query(query);
        if (cs.getCount()>0) {
            cs.moveToFirst();
            int c = cs.getInt(0);
            return c > 0;
        }
        else return false;
    }

    public List<Receipt> getReceiptList(Date date) {

        Date next = DateUtils.getNextDate(date);

        List<Receipt> modified = db.receiptDao().getAll(date.getTime(), next.getTime());
        return modified;
    }

    public void postLog(LogEntryModel model) {

        LogEntry entry = LogEntry.fromModel(model);
        //db.logEntryDao().insert(entry);
    }

    public List<LogEntry> getLogList(int limit) {
        return db.logEntryDao().getModified(limit);
    }

    public void deleteLogs(int[] ids) {
        db.logEntryDao().deleteLogs(ids);
    }

    public Review getReview(int id) {
        return db.reviewDao().get(id);
    }
    public Review getReview(String uniqueId) {
        return db.reviewDao().get(uniqueId);
    }
    public void postReview(Review model) {
        Review item = db.reviewDao().get(model.getUniqueId());


        model.setDateUpdated(new Date());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.reviewDao().insert(model);
        } else {
            //item.setJsonData(truckModel.getJsonData());

            model.setLocalId(item.getLocalId());
            model.setId(item.getId());

            db.reviewDao().update(model);
        }
    }

    public ReviewModel getReviewByFlight(int flightId) {

        Review item = db.reviewDao().getByFlight(flightId);
        if (item!= null)
            return item.toModel();
        else
            return null;
    }
    public ReviewModel getReviewByFlight(String flightId) {

        Review item = db.reviewDao().getByFlight(flightId);
        if (item!= null)
            return item.toModel();
        else
            return null;
    }
    public boolean checkReview(int flightId,String flightUniqueId) {
        return db.reviewDao().checkReview(flightId,flightUniqueId);
    }

    public List<Review> getModifiedReview() {
        return db.reviewDao().getModified();
    }

    public Receipt getReceipt(String uniqueId) {
        return db.receiptDao().getByUniqueId(uniqueId);
    }

    public Receipt getReceiptByNumber(String number) {
        return db.receiptDao().get(number);
    }

    public List<BM2505ContainerModel> getBM2505ContainerList() {

        List<BM2505Container> localList = db.bm2505Dao().getContainers();
        List<BM2505ContainerModel> returnList = new ArrayList();
        for (BM2505Container item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }

    public void insertBM2505Container(BM2505Container model) {
        BM2505Container item = db.bm2505Dao().getContainer(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.bm2505Dao().insertContainer(model);
        } else {


            model.setLocalId(item.getLocalId());
            db.bm2505Dao().updateContainer(model);
        }
    }
    public List<ProductModel> getProductList() {
        List<Product> localList = db.ProductDao().getAll();
        List<ProductModel> returnList = new ArrayList<>();
        for (Product item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }
    public void insertProduct(Product model) {
        Product item = db.ProductDao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 && item.getLocalId() != model.getLocalId())) {
            db.ProductDao().insert(model);
        } else {
            model.setLocalId(item.getLocalId());
            db.ProductDao().update(model);
        }
    }

    public RefuelItemData getLatestRefuelItemByEndTime() {

        RefuelItem item = db
                .refuelItemDao()
                .getLatestByEndTime();

        if (item != null) {
            return item.toRefuelItemData();
        }

        return null;
    }


    public Double getLatestDensityFromLocal() {

        try {
            String json = db.refuelItemDao().getLatestRefuelItemJson();

            // 🔍 DEBUG 1: kiểm tra raw json
            android.util.Log.d("DENSITY_SQL", "raw json = " + json);

            if (json == null || json.isEmpty()) {
                android.util.Log.d("DENSITY_SQL", "json is NULL or EMPTY");
                return null;
            }

            JSONObject obj = new JSONObject(json);

            // 🔍 DEBUG 2: log toàn bộ keys
            android.util.Log.d("DENSITY_SQL", "json keys = " + obj.names());

            double density = obj.optDouble("Density", -1);

            // 🔍 DEBUG 3: log density parse ra
            android.util.Log.d("DENSITY_SQL", "parsed Density = " + density);

            // validate nghiệp vụ Jet A-1
            if (density > 0.7 && density < 0.9) {
                return density;
            }

            android.util.Log.d("DENSITY_SQL", "density out of range, fallback");
            return null;

        } catch (Exception e) {
            android.util.Log.e("DENSITY_SQL", "error", e);
            return null;
        }
    }
    public interface DensityCallback {
        void onResult(@Nullable Double density);
    }

    public void loadLatestDensityAsync(DensityCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Double result = null;

            try {
                String json = db.refuelItemDao().getLatestRefuelItemJson();

                Logger.appendLog("DENSITY_SQL", "raw json=" + json);

                if (json != null) {
                    JSONObject obj = new JSONObject(json);
                    double d = obj.optDouble("Density", -1);

                    Logger.appendLog("DENSITY_SQL", "parsed density=" + d);

                    if (d > 0.7 && d < 0.9) {
                        result = d;
                    }
                }

            } catch (Exception e) {
                Logger.appendLog("DENSITY_SQL", "error=" + e.getMessage());
            }

            Double finalResult = result;

            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(finalResult);
            });
        });
    }
    //2503
    public List<BM2503Model> getBM2503List(Date date) {

        Calendar cal =  Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE,1);
        long end = cal.getTime().getTime();
        List<BM2503> localList = db.bm2503Dao().getAll(start, end);
        List<BM2503Model> returnList = new ArrayList();
        for (BM2503 item : localList) {
            returnList.add(item.toModel());
        }
        return returnList;
    }
    public void insertBM2503(BM2503 model) {

        BM2503 item = db.bm2503Dao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 && item.getLocalId() != model.getLocalId())) {
            db.bm2503Dao().insert(model);
        } else {
            model.setLocalId(item.getLocalId());
            db.bm2503Dao().update(model);
        }
    }
    public void mergeRemoteBM2503(BM2503 remote) {
        BM2503 local = db.bm2503Dao().get(remote.getId(), remote.getLocalId());
        if (local == null) {
            db.bm2503Dao().insert(remote);
        } else if (!local.isLocalModified()) {
            remote.setLocalId(local.getLocalId());
            db.bm2503Dao().update(remote);
        }
    }
    public List<BM2503> getModifiedBM2503() {
        return db.bm2503Dao().getModified();
    }
    public void deleteBM2503(int[] ids) {
        db.bm2503Dao().delete(ids);
    }



    public List<BM2504Model> getBM2504List(Date date) {

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long start = cal.getTime().getTime();
        cal.add(Calendar.DATE, 1);
        long end = cal.getTime().getTime();

        List<BM2504> localList = db.bm2504Dao().getAll(start, end);
        List<BM2504Model> returnList = new ArrayList<>();

        for (BM2504 item : localList) {
            returnList.add(item.toModel());
        }

        return returnList;
    }


    public void insertBM2504(BM2504 model) {

        BM2504 item = db.bm2504Dao().get(model.getId(), model.getLocalId());

        if (item == null
                || (item.getId() == 0 && item.getLocalId() != model.getLocalId())) {

            db.bm2504Dao().insert(model);

        } else {
            model.setLocalId(item.getLocalId());
            db.bm2504Dao().update(model);
        }
    }
    public void mergeRemoteBM2504(BM2504 remote) {
        BM2504 local = db.bm2504Dao().get(remote.getId(), remote.getLocalId());
        if (local == null) {
            db.bm2504Dao().insert(remote);
        } else if (!local.isLocalModified()) {
            remote.setLocalId(local.getLocalId());
            db.bm2504Dao().update(remote);
        }
    }
    public List<BM2504> getModifiedBM2504() {
        return db.bm2504Dao().getModified();
    }
    public void deleteBM2504(int[] ids) {
        db.bm2504Dao().delete(ids);
    }










}
