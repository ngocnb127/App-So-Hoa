package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

/**
 * Chốt chặn phân loại quyền sở hữu: không trường nghiệp vụ nào được rơi vào mặc định.
 */
public class RefuelFieldOwnershipTest {

    /**
     * Yêu cầu quan trọng nhất của bảng phân loại: THÊM TRƯỜNG MỚI mà quên phân loại thì
     * test này đổ ngay. Không có mặc định "coi như của client" — mặc định đó sinh conflict
     * giả; cũng không có mặc định "coi như của server" — mặc định đó bỏ lọt xung đột thật.
     */
    @Test
    public void everyModelFieldIsClassified() {
        assertEquals("Trường model chưa phân loại ownership: "
                        + RefuelFieldOwnership.unclassifiedModelKeys(),
                0, RefuelFieldOwnership.unclassifiedModelKeys().size());
    }

    /** Mọi khoá Gson thực sự sinh ra đều phải có phân loại. */
    @Test
    public void everySerializedKeyIsClassified() {
        RefuelItemData data = new RefuelItemData();
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(com.megatech.fms.model.REFUEL_ITEM_STATUS.DONE);

        com.google.gson.JsonObject written =
                com.google.gson.JsonParser.parseString(data.toJson()).getAsJsonObject();

        for (String key : written.keySet())
            assertNotNull("chưa phân loại: " + key, RefuelFieldOwnership.of(key));
    }

    /** Khoá lạ của server không có phân loại, và KHÔNG được coi là của client. */
    @Test
    public void unknownServerKeyIsNotClientOwned() {
        for (String key : new String[]{"TechLog", "Weight", "Invoice", "BondingCable",
                "AirportId", "AirlineType", "FlightType"}) {
            assertTrue("khoá lạ không được vào vân tay client",
                    !RefuelFieldOwnership.isClientFingerprintKey(key));
        }
    }

    /** Số liệu mẻ phải thuộc client, kế hoạch bay phải thuộc server. */
    @Test
    public void coreFieldsAreClassifiedAsExpected() {
        for (String key : new String[]{"RealAmount", "StartNumber", "EndNumber", "Status",
                "ManualTemperature", "Density", "QualityNo"})
            assertEquals(key, RefuelFieldOwnership.Ownership.CLIENT,
                    RefuelFieldOwnership.of(key));

        for (String key : new String[]{"FlightId", "FlightCode", "FlightStatus", "RefuelTime"})
            assertEquals(key, RefuelFieldOwnership.Ownership.SERVER,
                    RefuelFieldOwnership.of(key));

        for (String key : new String[]{"ParkingLot", "AircraftCode", "Price", "ProductName"})
            assertEquals(key, RefuelFieldOwnership.Ownership.SHARED,
                    RefuelFieldOwnership.of(key));
    }
}
