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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

public class DataRepository {


    private final AppDatabase db;
    private static DataRepository sInstance;

    private DataRepository(final AppDatabase db) {
        this.db = db;
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

    public List<RefuelItem> getModifiedRefuel() {
        List<RefuelItem> modified = db.refuelItemDao().getModified();
        return modified;
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

        Date d= new Date();
        d = new Date(d.getTime() - 24*60*60*1000 *numDays);

        db.refuelItemDao().deleteByDate(d.getTime());
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

    public void insertTruckFuel(TruckFuel model) {
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

    public void insertInvoice(Invoice model) {
        Invoice item = db.invoiceDao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.invoiceDao().insert(model);
        } else {

            model.setLocalId(item.getLocalId());
            db.invoiceDao().update(model);
        }
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

    public void insertBM2505(BM2505 model) {

        BM2505 item = db.bm2505Dao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.bm2505Dao().insert(model);
        } else {


            model.setLocalId(item.getLocalId());
            db.bm2505Dao().update(model);
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
    public void insertCheckTrucks(CheckTrucks model) {

        CheckTrucks item = db.checkTrucksDao().get(model.getId(), model.getLocalId());

        if (item == null || (item.getId() == 0 &&  item.getLocalId() != model.getLocalId())) {
            db.checkTrucksDao().insert(model);
        } else {


            model.setLocalId(item.getLocalId());
            db.checkTrucksDao().update(model);
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
        SimpleSQLiteQuery query = new SimpleSQLiteQuery("Select SUM(CNT ) FROM (Select count(0) as CNT from RefuelItem where isLocalModified = 1 Union Select count(0) from Receipt where isLocalModified = 1)");
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
    public List<BM2504> getModifiedBM2504() {
        return db.bm2504Dao().getModified();
    }
    public void deleteBM2504(int[] ids) {
        db.bm2504Dao().delete(ids);
    }










}
