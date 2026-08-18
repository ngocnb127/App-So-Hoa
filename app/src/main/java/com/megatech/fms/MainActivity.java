package com.megatech.fms;

import static com.megatech.fms.BuildConfig.API_BASE_URL;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;
import com.megatech.fms.databinding.DialogShiftBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.VersionCheckManager;
import com.megatech.fms.helpers.LCRReader;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.UserInfo;
import com.megatech.fms.view.PageAdapter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

public class MainActivity extends UserBaseActivity implements RefuelListFragment.OnFragmentInteractionListener {
    private boolean mTwoPane;
    Button btnSync;
    Button toolbarRefuelButton;
    Button toolbarExtractButton;

    DialogShiftBinding shiftBinding;
    private PageAdapter pageAdapter;
    private static final String LOG_TAG = "FMS_UPDATE_CHECK";
    /** Huy hiệu "có bản mới" trên thanh công cụ: biểu tượng app nhấp nháy + nhãn. */
    private View txtUpdateAvailable;
    private ImageView imgUpdateAvailable;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SETTING_CODE) {
                TabLayout tbl = findViewById(R.id.main_tablayout);
                tbl.getTabAt(0).setText(currentApp.getTruckNo());
            }

//            ViewPager viewPager = findViewById(R.id.main_viewpager);
//            ((PageAdapter) viewPager.getAdapter()).updateLists();
            updateRefuelList();

        }
    }

    @Override
    protected void onResume() {

        super.onResume();
        TabLayout tbl = findViewById(R.id.main_tablayout);
        tbl.getTabAt(0).setText(currentApp.getTruckNo()   );
        if (btnSync != null)
            btnSync.setVisibility(View.VISIBLE);
        showShiftInfo();
        updateRefuelList();
        requestSync();


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtUpdateAvailable = findViewById(R.id.txtUpdateAvailable);
        imgUpdateAvailable = findViewById(R.id.imgUpdateAvailable);
        // Huy hieu chi co trong layout ngang. Man hinh doc khong co view nay nen moi
        // lan dung deu phai kiem tra null, khong duoc goi thang.
        if (txtUpdateAvailable != null)
            txtUpdateAvailable.setVisibility(View.GONE); // mac dinh an, cho ket qua check

        // Badge chi phan anh trang thai cua VersionCheckManager. Truoc day MainActivity
        // tu goi server voi endpoint khac (version.txt) so voi hop thoai nhac
        // (versionUpdate.txt) nen hai noi co the ket luan trai nguoc nhau.
        VersionCheckManager.addListener(updateStatusListener);

        // Click vao label -> mo man hinh VersionUpdateActivity
        if (txtUpdateAvailable != null)
            txtUpdateAvailable.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, VersionUpdateActivity.class)));

        updateCurrentAmount();
        setToolbar();

        toolbarRefuelButton = findViewById(R.id.btnRefuel);
        toolbarExtractButton = findViewById(R.id.btnExtract);

        // Initialize ViewPager with adapter first (with empty/default data)
        setTabData();

        //toolbarRefuelButton.setVisibility(View.GONE);

        btnSync = findViewById(R.id.btnSync);
        if (btnSync != null)
            btnSync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    updateRefuelList();
                    new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... voids) {
                            boolean hasModified = DataHelper.checkLocalModified();
                            return  hasModified;
                        }

                        @Override
                        protected void onPostExecute(Boolean hasModified) {
                            super.onPostExecute(hasModified);
                            Button btn = (Button) findViewById(R.id.btnSync);
                            if (btn!=null ) {
                                if (hasModified)
                                    btn.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_not_sync), null);
                                else
                                    btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);

                            }
                        }
                    }.execute();
                }
            });

        Button btnRefuel = findViewById(R.id.btnNewRefuel);
        if (btnRefuel != null) {
            btnRefuel.setVisibility(View.INVISIBLE);
            ((Runnable) () -> {
                //HttpClient client = new HttpClient();
                //PermissionModel model = client.getPermission();
                //if (model == null || model.isAllowNewRefuel())
                //    btnRefuel.setVisibility(View.VISIBLE);

                if ((currentUser.getPermission() & UserInfo.USER_PERMISSION.CREATE_REFUEL.getValue()) > 0)
                    btnRefuel.setVisibility(View.VISIBLE);

            }).run();
            btnRefuel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new_refuel();
                }
            });

            checkInternet();


        }


        prepareSearchBox();

        checkIncompleteItem();

        if (!currentApp.isFirstUse())
            initReader();


        // Danh sách kế hoạch đọc từ Room chứ không gọi thẳng API, nên nếu không nghe
        // SYNC_BROADCAST thì lần cài mới màn hình đứng rỗng cho tới lần refresh sau.
        registerSyncReceiver();
    }

    private boolean syncReceiverRegistered = false;

    private void registerSyncReceiver() {
        if (syncReceiverRegistered)
            return;
        IntentFilter filter = new IntentFilter(UserBaseActivity.SYNC_BROADCAST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(mMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(mMessageReceiver, filter);
        syncReceiverRegistered = true;
    }

    /**
     * Kéo dữ liệu về ngay khi vào màn hình chính. Bộ lập lịch nền chạy 30 giây một lần và
     * lần chạy đầu tiên của nó rơi vào lúc chưa chọn xe (cài mới), nên nếu chỉ trông vào
     * nó thì kế hoạch tra nạp phải chờ hết một chu kỳ mới hiện.
     */
    private void requestSync() {
        if (currentApp.isFirstUse())
            return;
        new Thread(() -> {
            try {
                DataHelper.Synchronize();
            } catch (Exception ex) {
                Logger.appendLog("MAIN", "requestSync failed: " + ex.getMessage());
            }
        }).start();
    }


    private FMSReceiver mMessageReceiver = new FMSReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            updateRefuelList();
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    boolean hasModified = DataHelper.checkLocalModified();
                    return  hasModified;
                }

                @Override
                protected void onPostExecute(Boolean hasModified) {
                    super.onPostExecute(hasModified);
                    Button btn = (Button) findViewById(R.id.btnSync);
                    if (btn!=null ) {
                        if (hasModified)
                            btn.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_not_sync), null);
                        else
                            btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);

                    }
                }
            }.execute();
        }
    };

    private void initReader() {
        reader = LCRReader.create(this, currentApp.getDeviceIP(), 10001, true);
        reader.setConnectionListener(new LCRReader.LCRConnectionListener() {
            @Override
            public void onConnected() {

                reader.requestDateFormat();
                //reader.doDisconnectDevice();
                //reader.destroy();
                //reader.destroy();
            }

            @Override
            public void onError() {

            }

            @Override
            public void onDeviceAdded(boolean failed) {

            }

            @Override
            public void onDisconnected() {

            }

            @Override
            public void onCommandError(LCR_COMMAND command) {

            }

            @Override
            public void onConnectionStateChange(LCR_DEVICE_CONNECTION_STATE state) {

            }
        });

        reader.setFieldDataListener(new LCRReader.LCRDataListener() {
            @Override
            public void onDataChanged(LCRDataModel dataModel, LCRReader.FIELD_CHANGE field_change) {
                //reader.doDisconnectDevice();
                //reader.destroy();
                //reader.doDisconnectDevice();
                //reader.destroy();
            }

            @Override
            public void onErrorMessage(String errorMsg) {

            }

            @Override
            public void onFieldAddSucess(String field_name) {

            }
        });
    }

    private void prepareSearchBox() {

        SearchView sb = (SearchView) findViewById(R.id.search_bar);
        if (sb != null) {
            sb.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    ViewPager viewPager = findViewById(R.id.main_viewpager);
                    PageAdapter adapter = (PageAdapter) viewPager.getAdapter();
                    if (adapter != null) {
                        adapter.filter(query);
                    }
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String query) {
                    ViewPager viewPager = findViewById(R.id.main_viewpager);
                    PageAdapter adapter = (PageAdapter) viewPager.getAdapter();
                    if (adapter != null) {
                        adapter.filter(query);
                    }
                    return false;
                }
            });

            sb.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    ViewPager viewPager = findViewById(R.id.main_viewpager);
                    PageAdapter adapter = (PageAdapter) viewPager.getAdapter();
                    if (adapter != null) {
                        adapter.filter("");
                    }
                    return false;
                }
            });
        }
    }

    private void checkIncompleteItem() {

        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                Logger.appendLog("MAIN", "Check incomplete item");
                RefuelItemData postRefuel = DataHelper.getImcomplete();
                return postRefuel;
            }

            @Override
            protected void onPostExecute(RefuelItemData itemData) {
                if (itemData != null) {
                    showConfirmMessage(R.string.incomplete_continue, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            openIncompleItem(itemData);
                            return null;
                        }
                    });
                }

                super.onPostExecute(itemData);

            }
        }.execute();
    }

    private void openIncompleItem(RefuelItemData itemData) {

        Intent intent = new Intent(this, RefuelDetailActivity.class);
        com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, itemData);
        startActivity(intent);
    }

    private void showShiftInfo() {
        TextView lblShift = findViewById(R.id.lblShiftInfo);
        if (lblShift != null) {
            ShiftModel model = currentApp.getShift();
            renderShiftInfo(lblShift, model);

            Date d = new Date();
            if (model == null || d.compareTo(model.getStartTime()) < 0 || d.compareTo(model.getEndTime()) > 0) {
                // Gọi API ca làm việc dưới nền: trước đây chạy thẳng trên main thread nên
                // mỗi lần mở màn hình chính phải đứng chờ hết request rồi mới vẽ danh sách.
                new Thread(() -> {
                    ShiftModel remote = new HttpClient().getShift();
                    if (remote == null)
                        return;
                    currentApp.saveShift(remote);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed())
                            return;
                        renderShiftInfo(findViewById(R.id.lblShiftInfo), remote);
                        updateRefuelList();
                    });
                }).start();
            }
        }

        Button btnShift = findViewById(R.id.btn_shift);
        if (btnShift != null)
            btnShift.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showShiftDialog();
                }
            });
    }

    private void renderShiftInfo(TextView lblShift, ShiftModel model) {
        if (lblShift == null || model == null)
            return;
        try {
            ShiftModel cModel = model.isSelected() ? model : model.getPrevShift().isSelected() ? model.getPrevShift() : model.getNextShift();
            if (cModel.getStartTime() == null || cModel.getName() == null) {
                model.setSelected(true);
                cModel = model;
            }

            lblShift.setText(String.format("%s: %s ", getString(model.isSelected() ? R.string.current_shift : model.getPrevShift().isSelected() ? R.string.prev_shift : R.string.next_shift), cModel.toString()));
        } catch (Exception ex) {
            Logger.appendLog("MAIN", "renderShiftInfo failed: " + ex.getMessage());
        }
    }

    private void showShiftDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.shift_select);
        shiftBinding = DataBindingUtil.inflate(this.getLayoutInflater(), R.layout.dialog_shift, null, false);
        builder.setView(shiftBinding.getRoot());
        ShiftModel model = currentApp.getShift();

        shiftBinding.setShift(model);

        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ShiftModel model = shiftBinding.getShift();


                currentApp.saveShift(model);
                updateRefuelList();
                showShiftInfo();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        Dialog dlg = builder.create();
        dlg.show();
        dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        dlg.findViewById(R.id.radNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioClick(v);
            }
        });
        dlg.findViewById(R.id.radCurrent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioClick(v);
            }
        });
        dlg.findViewById(R.id.radPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioClick(v);
            }
        });


    }

    private void updateRefuelList() {
        ViewPager viewPager = findViewById(R.id.main_viewpager);
        PageAdapter adapter = (PageAdapter) viewPager.getAdapter();
        if (adapter != null) {
            adapter.updateLists();
        }
    }

    private void radioClick(View v) {
        View dlg = v.getRootView();
        if (dlg != null) {
            switch (v.getId()) {
                case R.id.radCurrent:
                    ((RadioButton) dlg.findViewById(R.id.radPrev)).setChecked(false);
                    ((RadioButton) dlg.findViewById(R.id.radNext)).setChecked(false);
                    break;
                case R.id.radNext:
                    ((RadioButton) dlg.findViewById(R.id.radPrev)).setChecked(false);
                    ((RadioButton) dlg.findViewById(R.id.radCurrent)).setChecked(false);
                    break;
                case R.id.radPrev:
                    ((RadioButton) dlg.findViewById(R.id.radCurrent)).setChecked(false);
                    ((RadioButton) dlg.findViewById(R.id.radNext)).setChecked(false);
                    break;
            }

            shiftBinding.invalidateAll();

        }

    }


    private void showInvalidTruckError() {

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.wrong_truck_connect)
                .setIcon(R.drawable.ic_warning)
                .create()
                .show();
    }

    private void updateCurrentAmount() {
        //currentApp.initCurrentAmount( new HttpClient().getTruckAmount(currentApp.getTruckNo()));

    }


    private void new_refuel() {
        try {
            Intent intent = new Intent(this, NewRefuelActivity.class);
            intent.putExtra("EXTRACT", false);
            startActivity(intent);
        } catch (Exception ex) {
            Log.e("INVENTORY", ex.getMessage());
        }
    }

    private void setTabData() {
        ViewPager viewPager = findViewById(R.id.main_viewpager);
        pageAdapter = new PageAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pageAdapter);
        TabLayout tabLayout = findViewById(R.id.main_tablayout);
        tabLayout.setupWithViewPager(viewPager);
    }

    LCRReader reader;


    @Override
    protected void onDestroy() {
        if (syncReceiverRegistered) {
            try {
                unregisterReceiver(mMessageReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            syncReceiverRegistered = false;
        }
        VersionCheckManager.removeListener(updateStatusListener);
        stopUpdateBlink();
        super.onDestroy();

    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    private void checkInternet() {
        Timer tmr = new Timer();
        tmr.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    showInternetIcon(!hostAvailable("skypec.com.vn", 80));
                });


            }
        }, 10, 60 * 1000);

    }

    public boolean hostAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            // Either we have a timeout or unreachable host or failed DNS lookup
            System.out.println(e);
            return false;
        }
    }

    private void showInternetIcon(boolean show) {
        if (optionMenu == null) return;
        MenuItem v = optionMenu.findItem(R.id.action_status);
        if (v != null) {
            Drawable icon = getDrawable(R.drawable.ic_no_connection);

            //v.setVisibility(show ? View.VISIBLE : View.INVISIBLE);

            if (!show) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                SpannableString s = new SpannableString("WIFI:" + ssid.replace("\"", ""));
                s.setSpan(new ForegroundColorSpan(Color.WHITE), 0, s.length(), 0);

                v.setTitle(s);
                v.setIcon(null);

            } else {
                v.setIcon(icon);
            }
        }

    }

    private final VersionCheckManager.Listener updateStatusListener = status -> {
        if (isFinishing() || isDestroyed()) return;
        if (txtUpdateAvailable == null) return;
        // Chi hien badge khi that su co ban moi. CHECK_FAILED khong bao gio duoc
        // hieu la "khong co ban moi", nhung cung khong hien badge vi chua biet chac.
        boolean hasUpdate = status.hasUpdate();
        txtUpdateAvailable.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        if (hasUpdate) startUpdateBlink(); else stopUpdateBlink();
    };

    /** Nhip nhay cua bieu tuong app khi co ban moi. */
    private android.view.animation.Animation updateBlink;

    /**
     * Bao co ban moi bang bieu tuong app nhap nhay, thay cho hop thoai cat ngang cong viec.
     *
     * <p>Nhip cham va chi mo di chu khong tat han: du de mat nguoi dung bat duoc khi luot
     * qua man hinh chinh, nhung khong nhay giat lam phien nguoi dang nhap lieu.
     */
    private void startUpdateBlink() {
        if (imgUpdateAvailable == null) return;
        if (updateBlink == null) {
            android.view.animation.AlphaAnimation blink =
                    new android.view.animation.AlphaAnimation(1f, 0.25f);
            blink.setDuration(700);
            blink.setRepeatMode(android.view.animation.Animation.REVERSE);
            blink.setRepeatCount(android.view.animation.Animation.INFINITE);
            updateBlink = blink;
        }
        // Dang chay roi thi khong khoi dong lai: moi lan kiem tra phien ban se lam nhip
        // nhay giat lai tu dau.
        if (imgUpdateAvailable.getAnimation() != updateBlink) {
            imgUpdateAvailable.startAnimation(updateBlink);
        }
    }

    private void stopUpdateBlink() {
        if (imgUpdateAvailable == null) return;
        imgUpdateAvailable.clearAnimation();
        imgUpdateAvailable.setAlpha(1f);
    }
}