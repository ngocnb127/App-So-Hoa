package com.megatech.fms.rut;

import android.content.Context;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chạy ĐÚNG đường code sản phẩm trên router thật rồi in kết luận ra logcat.
 *
 * <p>Khác {@link RutProbeInstrumentedTest}: bài kia chụp response thô để biết router nói gì;
 * bài này kiểm chứng phần dịch — cùng một chiếc router, popup sẽ hiện ra cái gì. Không có
 * bước này thì mọi sửa parser đều chỉ là suy luận trên giấy.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RutEndToEndProbeTest {

    private static final String TAG = "RUT_E2E";

    @Test
    public void loadStatusAgainstTheRealRouterAndPrintWhatThePopupWouldShow() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DefaultRutRepository repository = new DefaultRutRepository(context);

        AtomicReference<RutStatusUiModel> result = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        repository.loadStatus(new RutRepository.Callback<RutStatusUiModel>() {
            @Override
            public void onResult(RutStatusUiModel value) {
                result.set(value);
                done.countDown();
            }

            @Override
            public void onError(String message) {
                failure.set(message);
                done.countDown();
            }
        });

        boolean answered = done.await(60, TimeUnit.SECONDS);
        Log.i(TAG, "===== KẾT QUẢ ĐƯỜNG SẢN PHẨM =====");
        if (!answered) {
            Log.i(TAG, "HỎNG: quá 60 giây không có callback — popup sẽ quay vòng mãi");
            return;
        }
        if (failure.get() != null) {
            Log.i(TAG, "onError: " + failure.get());
            return;
        }

        RutStatusUiModel model = result.get();
        Log.i(TAG, "connectionState = " + model.connectionState);
        Log.i(TAG, "wifiSsid        = " + model.wifiSsid);
        Log.i(TAG, "routerModel     = " + model.routerModel);
        Log.i(TAG, "routerSerial    = " + model.routerSerial);
        Log.i(TAG, "modemId         = " + model.modemId);
        Log.i(TAG, "mobileConnected = " + model.mobileConnected);
        Log.i(TAG, "networkType     = " + model.networkType);
        Log.i(TAG, "operatorName    = " + model.operatorName);
        Log.i(TAG, "activeSimSlot   = " + model.activeSimSlot);
        Log.i(TAG, "simPresent      = " + model.simPresent);
        Log.i(TAG, "simState        = " + model.simState);
        Log.i(TAG, "rsrp/rsrq/sinr  = " + model.rsrpDbm + " / " + model.rsrqDb + " / " + model.sinrDb);
        Log.i(TAG, "rssi            = " + model.rssiDbm);
        Log.i(TAG, "signalRating    = " + model.signalRating());
        Log.i(TAG, "wifiConnected   = " + model.wifiConnectedToRut);
        Log.i(TAG, "identityOk      = " + model.routerIdentityVerified);
        Log.i(TAG, "wrongRouter     = " + model.wrongRouter);
        Log.i(TAG, "internetQuaRut  = " + model.internetThroughRouter);
        Log.i(TAG, "===== HẾT =====");

        repository.close();
    }
}
