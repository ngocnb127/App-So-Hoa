package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.data.entity.Receipt;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class DataRetentionTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private Context context;
    private AppDatabase db;
    private long cutoff;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        cutoff = DataRetention.cutoffMillis();
    }

    @After
    public void tearDown() {
        db.close();
        File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (folder != null && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    /** Mốc cắt phải là đầu ngày, lùi đúng 3 ngày — không trôi theo giờ trong ca. */
    @Test
    public void cutoffIsStartOfDayThreeDaysBack() {
        assertEquals(3, DataRetention.RETENTION_DAYS);

        long now = System.currentTimeMillis();
        assertTrue(cutoff < now - 2 * DAY);
        assertTrue(cutoff > now - 5 * DAY);
    }

    @Test
    public void syncedRowsOlderThanCutoffAreDeleted() {
        db.refuelItemDao().insert(refuel("uid-cu", cutoff - DAY, false, 5001));
        db.receiptDao().insert(receipt("R-cu", cutoff - DAY, false, 6001));

        DataRetention.Result result = DataRetention.purge(context, db, cutoff);

        assertTrue(result.rowsDeleted >= 2);
        assertEquals(0, db.refuelItemDao().getAll().size());
    }

    /**
     * Điều kiện quan trọng nhất của cả cơ chế: dữ liệu CHƯA gửi lên server không bao giờ
     * bị xoá theo tuổi. Máy mất mạng nhiều ngày vẫn phải giữ nguyên phiếu chờ gửi.
     */
    @Test
    public void unsyncedRowsAreKeptRegardlessOfAge() {
        db.refuelItemDao().insert(refuel("uid-cho-gui", cutoff - 30 * DAY, true, 5002));
        db.receiptDao().insert(receipt("R-cho-gui", cutoff - 30 * DAY, true, 6002));

        DataRetention.purge(context, db, cutoff);

        assertEquals(1, db.refuelItemDao().getAll().size());
        assertEquals(1, db.receiptDao().getModified().size());
    }

    /** Phiếu chưa từng được server cấp id cũng là dữ liệu chỉ có ở máy này. */
    @Test
    public void rowsWithoutServerIdAreKept() {
        RefuelItem row = refuel("uid-offline", cutoff - 10 * DAY, false, 0);
        db.refuelItemDao().insert(row);

        DataRetention.purge(context, db, cutoff);

        assertEquals(1, db.refuelItemDao().getAll().size());
    }

    @Test
    public void recentRowsAreKept() {
        db.refuelItemDao().insert(refuel("uid-moi", System.currentTimeMillis(), false, 5003));

        DataRetention.purge(context, db, cutoff);

        assertEquals(1, db.refuelItemDao().getAll().size());
    }

    /** Chuyến bay còn phiếu trỏ tới thì không được xoá, dù đã quá hạn. */
    @Test
    public void flightStillReferencedIsKept() {
        Flight flight = new Flight();
        flight.setId(777);
        flight.setCode("VN170");
        flight.setRefuelScheduledTime(new Date(cutoff - 10 * DAY));
        db.flightDao().insert(flight);

        RefuelItem row = refuel("uid-giu-chuyen", cutoff - 10 * DAY, true, 0);
        row.setFlightId(777);
        db.refuelItemDao().insert(row);

        DataRetention.purge(context, db, cutoff);

        assertEquals(1, db.flightDao().getAll(0, Long.MAX_VALUE).size());
    }

    @Test
    public void orphanFlightOlderThanCutoffIsDeleted() {
        Flight flight = new Flight();
        flight.setId(888);
        flight.setCode("VN999");
        flight.setRefuelScheduledTime(new Date(cutoff - 10 * DAY));
        db.flightDao().insert(flight);

        DataRetention.purge(context, db, cutoff);

        assertEquals(0, db.flightDao().getAll(0, Long.MAX_VALUE).size());
    }

    // =========================================================================
    // File ảnh / chữ ký
    // =========================================================================

    @Test
    public void oldFilesAreDeletedAndRecentOnesKept() throws Exception {
        File old = writeFile("screenshot_cu.jpg", cutoff - DAY);
        File fresh = writeFile("screenshot_moi.jpg", System.currentTimeMillis());

        DataRetention.Result result = DataRetention.purge(context, db, cutoff);

        assertFalse(old.exists());
        assertTrue(fresh.exists());
        assertEquals(1, result.filesDeleted);
    }

    /**
     * File đang được một bản ghi CHƯA gửi tham chiếu thì giữ lại bất kể tuổi. Xoá nhầm sẽ
     * làm hàng đợi gửi ảnh dừng vĩnh viễn vì không còn file để gửi.
     */
    @Test
    public void fileReferencedByUnsyncedReceiptIsKept() throws Exception {
        File signature = writeFile("JPEG_Signature123.jpg", cutoff - 10 * DAY);

        // Dựng jsonData thẳng bằng chuỗi: constructor của ReceiptModel đọc setting và
        // khởi tạo Crashlytics nên không chạy được trong môi trường test.
        Receipt row = new Receipt();
        row.setNumber("R-cho-gui");
        row.setDate(new Date(cutoff - 10 * DAY));
        row.setJsonData("{\"Number\":\"R-cho-gui\",\"SignaturePath\":\""
                + signature.getAbsolutePath().replace("\\", "\\\\") + "\"}");
        row.setLocalModified(true);
        db.receiptDao().insert(row);

        DataRetention.Result result = DataRetention.purge(context, db, cutoff);

        assertTrue(signature.exists());
        assertEquals(1, result.filesKept);
    }

    /** Ảnh chụp của phiếu chưa gửi được nhận diện qua quy ước đặt tên theo uniqueId. */
    @Test
    public void screenshotOfUnsyncedRefuelIsKept() throws Exception {
        File shot = writeFile("screenshot_uid-cho-gui.jpg", cutoff - 10 * DAY);
        db.refuelItemDao().insert(refuel("uid-cho-gui", cutoff - 10 * DAY, true, 0));

        DataRetention.purge(context, db, cutoff);

        assertTrue(shot.exists());
    }

    // =========================================================================

    private File writeFile(String name, long lastModified) throws Exception {
        File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        folder.mkdirs();
        File file = new File(folder, name);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(new byte[]{1, 2, 3});
        }
        assertTrue(file.setLastModified(lastModified));
        return file;
    }

    private RefuelItem refuel(String uniqueId, long refuelTime, boolean localModified, int id) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uniqueId);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setRefuelTime(new Date(refuelTime));

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(localModified);
        return row;
    }

    private Receipt receipt(String number, long date, boolean localModified, int id) {
        Receipt row = new Receipt();
        row.setId(id);
        row.setNumber(number);
        row.setDate(new Date(date));
        row.setJsonData("{\"Number\":\"" + number + "\"}");
        row.setLocalModified(localModified);
        return row;
    }
}
