package com.megatech.fms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SearchView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.Product;
import com.megatech.fms.databinding.ActivityRefuelPreviewBinding;
import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.databinding.InvoicePreviewBinding;
import com.megatech.fms.databinding.PreviewExtractBinding;
import com.megatech.fms.databinding.RefuelBm2508Binding;
import com.megatech.fms.databinding.SelectUserBinding;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.enums.RETURN_UNIT;
import com.megatech.fms.exceptions.InvalidRefuelTimeException;
import com.megatech.fms.helpers.BM2505Factory;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.RefuelTimeValidator;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.AirlineArrayAdapter;
import com.megatech.fms.view.BM2505ArrayAdapter;
import com.megatech.fms.view.InvoiceItemAdapter;
import com.megatech.fms.view.TruckArrayAdapter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.megatech.fms.helpers.PrintWorker.PRINT_MODE;
import static com.megatech.fms.helpers.PrintWorker.PrintStateListener;
import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

public class RefuelPreviewActivity extends UserBaseActivity implements View.OnClickListener, OnBM2505SavedListener {

    private PrintWorker printWorker;
    private final int REFUEL_WINDOW = 1;
    private final int RECEIPT_WINDOW = 2;
    private final int INVOICE_WINDOW = 4;
    List<AirlineModel> airlines = null;
    public List<ProductModel> productList = null;

    /// bind data to view
    ArrayList<RefuelItemData> allItems = new ArrayList<RefuelItemData>();
    ArrayList<RefuelItemData> printItems = new ArrayList<RefuelItemData>();

    private boolean printTest = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle b = getIntent().getExtras();
        remoteId = b.getInt("REFUEL_ID", 0);
        localId = b.getInt("REFUEL_LOCAL_ID", 0);
        uniqueId = b.getString("REFUEL_UNIQUE_ID", UUID.randomUUID().toString());

        loadData();

        Drawable drawable = getResources().getDrawable(R.drawable.ic_edit, getTheme());
        drawable.setAlpha(90);

        printWorker = new PrintWorker(this);


        printWorker.setPrintStateListener(new PrintStateListener() {
            @Override
            public void onConnectionError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showErrorMessage(R.string.printer_error);
                    }
                });
            }

            @Override
            public void onError() {
                if (refuelData != null)
                    refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showMessage(R.string.validate, R.string.print_error, R.drawable.ic_error, new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                m_Title = getString(R.string.invoice_number);
                                isSplit = true;
                                showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                                return null;
                            }
                        });
                    }
                });
            }

            @Override
            public void onSuccess() {

                if (!printTest) {
                    if (refuelData != null)
                        refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m_Title = getString(R.string.invoice_number);
                            isSplit = true;
                            showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                        }
                    });
                }

            }

        });

    }

    private int localId;
    private int remoteId;
    private String uniqueId;
    private boolean hasReview ;

    /** Kết quả lượt kéo lại mẻ của xe khác lúc mở màn hình; null khi chưa chạy xong. */
    private DataHelper.RefreshResult othersRefresh;

    //AlertDialog progressDialog;
    @SuppressLint("StaticFieldLeak")
    private void loadData() {

        setProgressDialog();
        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                Logger.appendLog("PRW", "Start loading");

                airlines = DataHelper.getAirlines();


                if (userList == null)
                    userList = DataHelper.getUsers();
                productList = DataHelper.getProducts();

                // Kéo lại mẻ của xe khác TRƯỚC khi dựng model: getRefuelItem đọc danh sách
                // others thẳng từ Room, nên nếu không làm mới ở đây thì màn hình đứng trên
                // bản đã tải từ lần đồng bộ trước, và hoá đơn gộp MIN/MAX trên dữ liệu cũ đó.
                othersRefresh = DataHelper.refreshOthers(uniqueId);

                //refuelData = DataHelper.getRefuelItem(remoteId, localId);
                refuelData = DataHelper.getRefuelItem(uniqueId);
                if (refuelData!=null)
                hasReview =  DataHelper.checkReview(refuelData.getFlightId(), refuelData.getFlightUniqueId());
                return refuelData;
            }

            @Override
            protected void onPostExecute(RefuelItemData itemData) {
                refuelData = itemData;
                bindData();
                warnStaleOthers();
                super.onPostExecute(itemData);

            }
        }.execute();


    }

    /**
     * Báo cho người dùng biết màn hình đang đứng trên dữ liệu cũ của xe khác.
     *
     * <p>Chỉ cảnh báo, chưa chặn gì: hiện trường vẫn phải in được khi sóng chập chờn. Nhưng
     * người dùng cần biết để đối chiếu lại các mẻ trước khi xuất hoá đơn, vì mẻ cũ kéo giờ
     * bắt đầu trên hoá đơn đi sai mà nhìn từng dòng vẫn thấy hợp lệ.
     */
    private void warnStaleOthers() {
        if (othersRefresh == null || !othersRefresh.hasFailure()) return;

        String message = "Chưa cập nhật được " + othersRefresh.failed + "/"
                + othersRefresh.total + " mẻ của xe khác. Vui lòng kiểm tra lại dữ liệu"
                + " các mẻ trước khi xuất hoá đơn.";

        Logger.appendLog(LOG_TAG, "Cảnh báo dữ liệu mẻ xe khác chưa cập nhật: "
                + othersRefresh.failed + "/" + othersRefresh.total);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    boolean isEditable = true;
    ActivityRefuelPreviewBinding binding;
    InvoicePreviewBinding previewBinding;
    PreviewExtractBinding extractBinding;
    TruckArrayAdapter truckArrayAdapter;
    RefuelItemData refuelData;


    InvoiceModel invoiceModel;
    BaseDialog printDialog;
    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);


    //validate data before print preview
    private boolean validate() {
        return validate(false);
    }

    private boolean validate(boolean isReturn) {
        if (printItems == null || printItems.size() <= 0) {
            showErrorMessage(R.string.preview_empty_list);
            return false;
        }

        if (printMode == PRINT_MODE.ONE_ITEM) {
            boolean valid = refuelData.getManualTemperature() > 0 && refuelData.getDensity() > 0;
            boolean validQC = refuelData.getQualityNo() != null && !refuelData.getQualityNo().isEmpty();
            if (!valid) {
                showErrorMessage(R.string.invalid_density_temperature);
            } else if (!validQC) {
                showErrorMessage(R.string.invalid_qc_no);
            }

            return valid && validQC;
        } else {

            boolean valid = true;
            boolean validQC = true;
            boolean hasReturn = false;
            for (int i = 0; i < printItems.size(); i++) {
                RefuelItemData item = printItems.get(i);
                valid = (item.getManualTemperature() > 0 && item.getDensity() > 0);

                validQC = item.getQualityNo() != null && !item.getQualityNo().isEmpty();

                hasReturn |= item.getReturnAmount() > 0;

                if (!valid || !validQC) {
                    truckArrayAdapter.setSelectedObject(item);
                    //ListView lv = findViewById(R.id.refuel_preview_truck_list);
                    //lv.setSelection(i);
                    refuelData = item;
                    binding.setMItem(refuelData);
                    isEditable = refuelData != null && refuelData.getTruckNo().equals(currentApp.getTruckNo()) && !refuelData.isExported();

                    break;
                }

            }
            if (!valid || !validQC) {
                showErrorMessage(!valid ? R.string.invalid_density_temperature : R.string.invalid_qc_no);
            }

            if (isReturn && !hasReturn) {
                showErrorMessage(R.string.no_return_amount);
                return false;
            }
            return valid && validQC;
        }

    }

    private boolean hasDensityWarning(List<RefuelItemData> items) {
        if (items == null || items.size() <= 1) return false;

        List<Integer> specialAirlineIds = Arrays.asList(1, 3, 476, 489, 497);

        boolean hasSpecialAirline = false;
        List<Double> densities = new ArrayList<>();

        for (RefuelItemData item : items) {
            if (item == null) continue;

            if (specialAirlineIds.contains(item.getAirlineId())) {
                hasSpecialAirline = true;
            }

            Double density = item.getDensity();
            if (density != null && density > 0) {
                // làm tròn 3 số để tránh lệch double nhỏ
                double normalized = Math.round(density * 1000.0) / 1000.0;
                densities.add(normalized);
            }
        }

        if (!hasSpecialAirline || densities.size() <= 1) return false;

        double first = densities.get(0);
        for (int i = 1; i < densities.size(); i++) {
            if (Double.compare(first, densities.get(i)) != 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Chặn cứng việc tạo phiếu khi có mẻ ghi giờ kết thúc sớm hơn giờ bắt đầu.
     *
     * <p>Khác cảnh báo mẻ quá dài: ở đây không có nút đi tiếp. Một mẻ âm thời gian là sai
     * chắc chắn, in ra thì hoá đơn mang số liệu không giải thích được.
     *
     * @return true nếu được phép đi tiếp.
     */
    private boolean blockReversedRefuelTime(List<RefuelItemData> items) {
        StringBuilder detail = new StringBuilder();

        if (items != null) {
            for (RefuelItemData item : items) {
                if (!RefuelTimeValidator.endsBeforeStart(item)) continue;

                String flightCode = item.getFlightCode();
                detail.append("\n• ")
                        .append(flightCode == null || flightCode.isEmpty()
                                ? "(chưa có số hiệu)" : flightCode)
                        .append(": bắt đầu ")
                        .append(DateUtils.formatDate(item.getStartTime(), "dd/MM HH:mm"))
                        .append(", kết thúc ")
                        .append(DateUtils.formatDate(item.getEndTime(), "dd/MM HH:mm"));
            }
        }

        if (detail.length() == 0) return true;

        Logger.appendLog(LOG_TAG, "CHẶN tạo phiếu, giờ kết thúc sớm hơn giờ bắt đầu:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Không tạo được phiếu")
                .setMessage("Giờ kết thúc sớm hơn giờ bắt đầu:" + detail
                        + "\n\nPhải sửa lại giờ tra nạp trước khi tạo phiếu.")
                .setPositiveButton("Đã hiểu", null)
                .setCancelable(false)
                .show();

        return false;
    }

    /**
     * Cảnh báo mẻ có thời gian tra nạp quá dài rồi chạy tiếp {@code onContinue}.
     *
     * <p>Chỉ cảnh báo, không chặn: hoá đơn vẫn in được sau khi người dùng bấm Tiếp tục.
     * Ghi log cả lúc cảnh báo lẫn lúc người dùng chọn in tiếp, để sau ca đối chiếu được
     * hoá đơn nào đã in với thời gian bất thường.
     */
    private void warnLongRefuelThen(List<RefuelItemData> items, Runnable onContinue) {
        List<RefuelItemData> longItems = new ArrayList<>();

        if (items != null) {
            for (RefuelItemData item : items) {
                if (RefuelTimeValidator.exceedsMaxDuration(item)) longItems.add(item);
            }
        }

        if (longItems.isEmpty()) {
            if (onContinue != null) onContinue.run();
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (RefuelItemData item : longItems) {
            String flightCode = item.getFlightCode();
            detail.append("\n• ")
                    .append(flightCode == null || flightCode.isEmpty()
                            ? "(chưa có số hiệu)" : flightCode)
                    .append(": ")
                    .append(RefuelTimeValidator.durationMinutes(item))
                    .append(" phút");
        }

        Logger.appendLog(LOG_TAG, "Cảnh báo thời gian tra nạp quá dài trước khi tạo receipt:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo thời gian tra nạp")
                .setMessage("Thời gian tra nạp quá dài (trên "
                        + RefuelTimeValidator.MAX_DURATION_MS / 60000 + " phút):"
                        + detail + "\n\nBạn có muốn tiếp tục in không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    Logger.appendLog(LOG_TAG, "Người dùng tiếp tục in dù thời gian tra nạp"
                            + " quá dài:" + detail.toString().replace('\n', ' '));
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    private void showDensityWarningDialog(Runnable onContinue) {
        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo (Chỉ áp dụng với VN, 0V, VN1)")
                .setMessage("Tỷ trọng giữa các lần tra nạp không giống nhau. Bạn có muốn tiếp tục lưu/in không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }
    private void bindData() {
        if (refuelData == null) {
            Logger.appendLog("null item, uniqueid : " + uniqueId);
            showErrorMessage(R.string.error_loading_item);
            finish();
            return;
        }
        Logger.appendLog("PRW","flight code : " + refuelData.getFlightCode());
        isEditable = BuildConfig.FHS || (!refuelData.isExported() && refuelData.getTruckNo().equals(currentApp.getTruckNo()));
        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL)
            setContentView(R.layout.activity_refuel_preview);
        else
            setContentView(R.layout.preview_extract);

        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
            //binding =  DataBindingUtil.setContentView(this, R.layout.activity_refuel_preview);
            binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.activity_refuel_preview, null, false);
            binding.setMItem(refuelData);
            setContentView(binding.getRoot());
            if (((Button)findViewById(R.id.refuel_preview_review)) != null) {
                ((Button)findViewById(R.id.refuel_preview_review)).setText(hasReview ? R.string.review_view : R.string.review_create);
            }
            //findViewById(R.id.refuel_preview_international).setEnabled(isEditable);
            ((CheckBox)findViewById(R.id.refuel_preview_international)).setOnTouchListener((view, motionEvent) -> {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    showConfirmMessage(R.string.change_route_type_confirm, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            view.performClick();
                            return null;
                        }
                    });
                    return true;
                } else {
                    return false;
                }
            });
        } else {

            if (refuelData.getProductId() > 0 && productList != null) {
                for (ProductModel product : productList) {
                    if (product.getId() == refuelData.getProductId()) {
                        refuelData.setPCode(product.getCode());
                        refuelData.setPName(product.getName());
                        refuelData.setProductName(product.getName());
                        break;
                    }
                }
            }

            //extractBinding = DataBindingUtil.setContentView(this, R.layout.preview_extract);
            extractBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.preview_extract, null, false);

            extractBinding.setMItem(refuelData);

            setContentView(extractBinding.getRoot());

            // Phiếu 75.01 nhập được ở mọi bản; riêng nút in nằm trong màn hình phiếu
            // và chỉ hiện ở chế độ in nhiệt (docs/PLAN-BM7501 §17).
            View btnOpen7501 = findViewById(R.id.btnOpen7501);
            if (btnOpen7501 != null) {
                btnOpen7501.setVisibility(View.VISIBLE);
            }
        }


        ArrayAdapter<AirlineModel> spinnerAdapter = new ArrayAdapter<AirlineModel>(this, R.layout.support_simple_spinner_dropdown_item, airlines);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        Spinner airline_spinner = findViewById(R.id.refuel_preview_airline_spinner);
        airline_spinner.setAdapter(spinnerAdapter);

        if (refuelData.getAirlineId() > 0) {
            for (int i = 0; i < airline_spinner.getCount(); i++) {
                AirlineModel item = (AirlineModel) airline_spinner.getItemAtPosition(i);
                if (refuelData.getAirlineId() == item.getId()) {
                    airline_spinner.setSelection(i);
                    ((TextView) findViewById(R.id.refuel_preview_airline)).setText(item.getName());
                    setAirline(refuelData, item);
                    break;
                }
            }

            airline_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    AirlineModel selected = (AirlineModel) parent.getItemAtPosition(position);
                    refuelData.setInvoiceNameCharter(null);
                    setAirline(selected);

                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            if (refuelData.getOthers().size() > 0) {
                for (int i = 0; i < refuelData.getOthers().size(); i++)
                    refuelData.getOthers().get(i).setAirlineModel(refuelData.getAirlineModel());
            }
        }
        if (refuelData.getProductId() > 0 && productList != null) {
            for (ProductModel product : productList) {
                if (product.getId() == refuelData.getProductId()) {
                    refuelData.setPCode(product.getCode());
                    refuelData.setPName(product.getName());
                    refuelData.setProductName(product.getName());
                    break;
                }
            }
        }

        allItems.clear();
        allItems.add(refuelData);

        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {

            //allItems.add(refuelData);
            allItems.addAll(refuelData.getOthers());

            allItems.sort(new Comparator<RefuelItemData>() {
                @Override
                public int compare(RefuelItemData o1, RefuelItemData o2) {
                    return o1.getEndTime().compareTo(o2.getEndTime());
                }
            });
            int selectedPos = 0;
            for (int i = 0; i < allItems.size(); i++)
                if (allItems.get(i).getId().equals(refuelData.getId()))
                    selectedPos = i;
            truckArrayAdapter = new TruckArrayAdapter(this, allItems);
            truckArrayAdapter.setSelected(selectedPos);
            ListView lv = findViewById(R.id.refuel_preview_truck_list);
            lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            lv.setAdapter(truckArrayAdapter);
            lv.setSelection(selectedPos);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    refuelData = (RefuelItemData) parent.getItemAtPosition(position);
                    binding.setMItem(refuelData);
                    isEditable = BuildConfig.FHS || (refuelData.getTruckNo().equals(currentApp.getTruckNo()) && !refuelData.isExported());

                    truckArrayAdapter.setSelectedObject(refuelData);


                }

            });


        }
        //Logger.appendLog("PRW","End binding");
        closeProgressDialog();

        if (BuildConfig.FHS) {
            findViewById(R.id.refuel_preview_check_form).setVisibility(View.GONE);
            findViewById(R.id.refuel_preview_print_all).setVisibility(View.GONE);
        }

        DataHelper.lockSync();
    }

    /** Mở phiếu BM 75.01 của ĐÚNG mẻ hút đang xem — phiếu và mẻ là quan hệ một-một. */
    private void openBM7501() {
        if (refuelData == null || refuelData.getUniqueId() == null) {
            showErrorMessage(R.string.bm7501_missing_refuel);
            return;
        }
        Intent intent = new Intent(this, B7501Activity.class);
        intent.putExtra(B7501Activity.EXTRA_REFUEL_UNIQUE_ID, refuelData.getUniqueId());
        startActivity(intent);
    }

    private boolean oldTemplate = true;
    InvoiceFormModel[] invoiceForms;
    //List<InvoiceFormModel> invoiceForms;
    //List<InvoiceFormModel> billForms;
    InvoiceFormModel defaultModel = null;

    private void openReceipt() {
        openReceipt(false);
    }

    private void openReceipt(boolean isReturn) {

        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();

        if (!isReturn) {

            if (validate() && blockReversedRefuelTime(printItems)) {

                boolean hasWarning = hasDensityWarning(printItems);

                Runnable continueAction = () -> {
                    String[] printedItems = checkPrintedItems();
                    if (printedItems.length > 0) {
                        showCancelReceiptInput(printedItems);
                    } else {
                        showReceiptPreview(true);
                    }
                };

                // Chặn cuối trước khi dựng receipt: mẻ dài bất thường vẫn in được, nhưng
                // người dùng phải thấy nó một lần. Nối SAU cảnh báo tỷ trọng để hai hộp
                // thoại không chồng lên nhau.
                Runnable checkDurationThenContinue =
                        () -> warnLongRefuelThen(printItems, continueAction);

                if (hasWarning) {
                    showDensityWarningDialog(checkDurationThenContinue);
                } else {
                    checkDurationThenContinue.run();
                }
            }

        } else {
            if (validate(isReturn)) {

                boolean hasWarning = hasDensityWarning(printItems);

                Runnable continueAction = () -> {
                    try {
                        ReceiptModel model = ReceiptModel.createReceipt(
                                printItems, null, true, null, true
                        );
                        Intent intent = new Intent(this, PrintReceiptActivity.class);
                        intent.putExtra("RECEIPT", model.toJson());
                        startActivity(intent);

                    } catch (InvalidRefuelTimeException ex) {
                        showBusinessError(ex.getMessage());
                    }
                };

                if (hasWarning) {
                    showDensityWarningDialog(continueAction);
                } else {
                    continueAction.run();
                }
            }
        }
    }

    private void showCancelReceiptInput(String[] printedItems) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 5, 0, 5);
        final RadioGroup radGroup = new RadioGroup(this);
        radGroup.setOrientation(RadioGroup.VERTICAL);

        final EditText reasonEditText = new EditText(this);
        reasonEditText.setVisibility(View.INVISIBLE);
        layout.addView(radGroup);
        int id = 0;
        for (String item : getResources().getStringArray(R.array.cancel_reason_array)
        ) {

            RadioButton radBtn = new RadioButton(this);
            radBtn.setText(item);
            radBtn.setPadding(5, 5, 5, 5);
            radBtn.setChecked(id == 0);
            radBtn.setId(id);
            radBtn.setTag(id++);

            radBtn.setOnClickListener(view -> {
                if ((int) view.getTag() == 3) {
                    reasonEditText.setVisibility(View.VISIBLE);
                    //reasonEditText.requestFocus();
                } else
                    reasonEditText.setVisibility(View.INVISIBLE);
            });
            radGroup.addView(radBtn);

        }





        layout.addView(reasonEditText);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_receipt_info)
                .setMessage(R.string.cancel_receipt_message)
                .setView(layout)
                .setPositiveButton(R.string.create_new_receipt, (dialog1, which) -> {
                    String reason = reasonEditText.getText().toString();
                    int selected = radGroup.getCheckedRadioButtonId();
                    if (selected < 3)
                        reason = getResources().getStringArray(R.array.cancel_reason_array)[selected];

                    if (reason.isEmpty())
                        showErrorMessage(R.string.cancel_reason_required);
                    else {
                        final String cancelReason = reason;
                        /*
                        showConfirmMessage(R.string.confirm_receip_cancel, () -> {
                            new Thread(() -> DataHelper.cancelReceipts(printedItems, cancelReason)).start();
                            showReceiptPreview();
                            return null;
                        });*/
                        //new Thread(() -> DataHelper.cancelReceipts(printedItems, cancelReason)).start();
                        showReceiptPreview(null,printedItems,true);
                        dialog1.dismiss();


                    }
                })
                .setNeutralButton(BuildConfig.THERMAL_PRINTER? R.string.re_print: R.string.back, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (BuildConfig.THERMAL_PRINTER)
                            reprintReceipt();
                        dialogInterface.dismiss();
                    }
                })
                .setNegativeButton(R.string.use_old_receipt, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showReceiptPreview(oldNumber,printedItems,false);
                        dialogInterface.dismiss();
                    }
                })
                .create();


        dialog.show();

    }
    private void showReceiptPreview( boolean createNew)
    {
        showReceiptPreview(null, null,createNew);
    }
    private void showReceiptPreview(String oldNumber, String[] replacedReceipts, boolean createNew) {
        //ReceiptModel model = ReceiptModel.createReceipt(printItems,oldNumber, createNew);
        try {
            ReceiptModel model = ReceiptModel.createReceipt(
                    printItems, replacedReceipts, false, oldNumber, createNew
            );
            Intent intent = new Intent(this, PrintReceiptActivity.class);
            intent.putExtra("RECEIPT", model.toJson());
            startActivityForResult(intent, RECEIPT_WINDOW);

        } catch (InvalidRefuelTimeException ex) {
            showBusinessError(ex.getMessage());
        }
    }

    private void reprintReceipt() {
        //ReceiptModel model = ReceiptModel.createReceipt(printItems,oldNumber, createNew);
        Intent intent = new Intent(this, PrintReceiptActivity.class);
        intent.putExtra("RECEIPT_ID", refuelData.getReceiptUniqueId());
        startActivityForResult(intent, RECEIPT_WINDOW);
    }
    private String oldNumber;
    private String[] checkPrintedItems() {
        ArrayList<String> stringArrayList = new ArrayList<String>();
        oldNumber = null;
        for (RefuelItemData item : printItems
        ) {
            if (item.getReceiptCount() >0 && item.getReceiptNumber() != null && !item.getReceiptNumber().isEmpty()) {
                stringArrayList.add(item.getReceiptUniqueId());
                oldNumber = item.getReceiptNumber();
            }
        }
        return stringArrayList.toArray(new String[0]);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECEIPT_WINDOW && resultCode == Activity.RESULT_OK) {
            String number = data.getStringExtra("number");
            String uniqueId = data.getStringExtra("uniqueId");
            double techlog = data.getDoubleExtra("techlog",0);
            updateAllReceipt(uniqueId, number,techlog);
            truckArrayAdapter.notifyDataSetChanged();
        } else if (requestCode == INVOICE_WINDOW && resultCode == Activity.RESULT_OK) {
            String number = data.getStringExtra("number");
            int formId = data.getIntExtra("formId", 0);
            double techlog = data.getDoubleExtra("techlog",0);
            INVOICE_TYPE printTemplate = (INVOICE_TYPE) data.getSerializableExtra("printTemplate");
            if (updateAllInvoice(number, formId, printTemplate,techlog))
                truckArrayAdapter.notifyDataSetChanged();
        }
        else if (requestCode == REVIEW_WINDOW && resultCode == RESULT_OK)
        {
            hasReview = true;
            ((Button)findViewById(R.id.refuel_preview_review)).setText(hasReview? R.string.review_view: R.string.review_create);

        }
    }

    private void preview() {
        try {
            SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
            oldTemplate = preferences.getBoolean("OLD_TEMPLATE", true);

            ListView lv = findViewById(R.id.refuel_preview_truck_list);
            printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();


            if (refuelData.getAirlineId() <= 0 || refuelData.getAirlineModel() == null) {
                showErrorMessage(R.string.invalid_airline_model);

                return;
            }
            if (refuelData.getDriverId() == 0 || refuelData.getOperatorId() == 0) {
                showErrorMessage(R.string.invalid_user);
                return;
            }
            if (!validate()) {

                return;
            }
            invoiceForms = FMSApplication.getApplication().getInvoiceForms();

            defaultModel = null;

            invoiceModel = InvoiceModel.fromRefuel(refuelData, printItems);
            setDefaultForm();

            printDialog = new BaseDialog(this);

            previewBinding = DataBindingUtil.inflate(printDialog.getLayoutInflater(), R.layout.invoice_preview, null, false);
            previewBinding.setInvoiceItem(invoiceModel);
            printDialog.setContentView(previewBinding.getRoot());
            RadioGroup radioGroup = printDialog.findViewById(R.id.radioTemplate);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int id = checkedId;

                invoiceModel.setInvoiceType(id == R.id.radInvoice ? INVOICE_TYPE.INVOICE : INVOICE_TYPE.BILL);
                setDefaultForm();
                previewBinding.invalidateAll();

            });
            int selectedIndex = !refuelData.isInternational() && !refuelData.getAirlineModel().isInternational() ? 1 : 0;


            ((RadioButton) radioGroup.getChildAt(selectedIndex)).setChecked(true);

            ((RadioButton) printDialog.findViewById(R.id.radOld)).setChecked(oldTemplate);


            InvoiceItemAdapter itemAdapter = new InvoiceItemAdapter(this, invoiceModel.getItems());
            ((ListView) printDialog.findViewById(R.id.invoice_preview_item_list)).setAdapter(itemAdapter);
            printDialog.findViewById(R.id.btnPrintInvoice).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    boolean invoice = ((RadioButton) radioGroup.getChildAt(0)).isChecked();
                    oldTemplate = ((RadioButton) printDialog.findViewById(R.id.radOld)).isChecked();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("OLD_TEMPLATE", oldTemplate);
                    editor.commit();
                    print(oldTemplate, invoice, false);

                    printDialog.findViewById(R.id.row_invoice_number).setVisibility(View.VISIBLE);
                }
            });

            printDialog.findViewById(R.id.btnPrintTest).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    boolean invoice = ((RadioButton) radioGroup.getChildAt(0)).isChecked();
                    oldTemplate = ((RadioButton) printDialog.findViewById(R.id.radOld)).isChecked();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("OLD_TEMPLATE", oldTemplate);
                    editor.apply();


                    print(oldTemplate, invoice, true);

                    //printDialog.findViewById(R.id.row_invoice_number).setVisibility(View.VISIBLE);
                }
            });
            printDialog.findViewById(R.id.btnPrintCancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    printDialog.dismiss();
                }
            });


            Objects.requireNonNull(printDialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            printDialog.show();
        } catch (Exception ex) {
            Logger.appendLog(ex.getMessage());
        }

    }

    private void openPrintInvoice() {
        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        printItems = ((TruckArrayAdapter) lv.getAdapter()).getCheckedItems();
        invoiceForms = FMSApplication.getApplication().getInvoiceForms();

        defaultModel = null;

        if (validate()) {

            warnInvoiceTimeThen(printItems, this::showInvoicePreview);

        }

    }

    /**
     * Cảnh báo giờ bất thường trước khi xuất hoá đơn rồi chạy tiếp {@code onContinue}.
     *
     * <p>Chỉ cảnh báo, chưa chặn: hiện trường có ca ngoại lệ thật và hoá đơn vẫn phải xuất được.
     * Nhưng hai giá trị sẽ IN LÊN hoá đơn phải hiện ra một lần — trước bản vá này không màn hình
     * nào cho người dùng thấy chúng, nên phiếu 2619EY0 in giờ bắt đầu 06:34 mà không ai biết cho
     * tới lúc cầm tờ giấy.
     */
    private void warnInvoiceTimeThen(List<RefuelItemData> items, Runnable onContinue) {
        List<String> issues = new ArrayList<>();

        if (items != null) {
            for (RefuelItemData item : items) {
                List<String> outside = RefuelTimeValidator.outsideApproachWindow(item);
                if (outside.isEmpty()) continue;

                String truckNo = item.getTruckNo();
                for (String error : outside)
                    issues.add((truckNo == null || truckNo.isEmpty() ? "(chưa rõ xe)" : truckNo)
                            + ": " + error);
            }
        }

        long span = RefuelTimeValidator.invoiceSpanMs(items);
        if (span > RefuelTimeValidator.MAX_INVOICE_SPAN_MS)
            issues.add("Toàn chuyến kéo dài " + span / 60000 + " phút, nhiều khả năng có mẻ mang"
                    + " giờ cũ chưa được cập nhật.");

        if (issues.isEmpty()) {
            if (onContinue != null) onContinue.run();
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (String issue : issues) detail.append("\n• ").append(issue);

        Logger.appendLog(LOG_TAG, "Cảnh báo giờ trước khi xuất HĐĐT:"
                + detail.toString().replace('\n', ' '));

        new AlertDialog.Builder(this)
                .setTitle("Kiểm tra lại giờ tra nạp")
                .setMessage("Giờ sẽ in lên hoá đơn:"
                        + "\n  Bắt đầu:  " + DateUtils.formatDate(invoiceStart(items), "dd/MM HH:mm")
                        + "\n  Kết thúc: " + DateUtils.formatDate(invoiceEnd(items), "dd/MM HH:mm")
                        + "\n" + detail
                        + "\n\nBạn có muốn tiếp tục xuất hoá đơn không?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    Logger.appendLog(LOG_TAG, "Người dùng tiếp tục xuất HĐĐT dù giờ bất thường:"
                            + detail.toString().replace('\n', ' '));
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton("Kiểm tra lại", null)
                .show();
    }

    /** Giờ bắt đầu sẽ in lên hoá đơn — cùng phép gộp với {@code InvoiceModel.fromRefuel}. */
    private Date invoiceStart(List<RefuelItemData> items) {
        Date min = null;
        if (items != null)
            for (RefuelItemData item : items) {
                Date start = item == null ? null : item.getStartTime();
                if (start != null && (min == null || start.before(min))) min = start;
            }
        return min;
    }

    /** Giờ kết thúc sẽ in lên hoá đơn — cùng phép gộp với {@code InvoiceModel.fromRefuel}. */
    private Date invoiceEnd(List<RefuelItemData> items) {
        Date max = null;
        if (items != null)
            for (RefuelItemData item : items) {
                Date end = item == null ? null : item.getEndTime();
                if (end != null && (max == null || end.after(max))) max = end;
            }
        return max;
    }

    private void showInvoicePreview() {
        InvoiceModel model = InvoiceModel.fromRefuel(refuelData, printItems);
        setDefaultForm(model);
        Intent intent = new Intent(this, PrintInvoiceActivity.class);
        intent.putExtra("INVOICE", model.toJson());
        startActivityForResult(intent, INVOICE_WINDOW);

    }

    private void print(boolean oldTemplate, boolean invoice) {
        print(oldTemplate, invoice, false);
    }

    private void print(boolean oldTemplate, boolean invoice, boolean test) {
        printTest = test;
        if (invoice)
            printInvoice(oldTemplate);
        else printBill(oldTemplate);
    }

    private void setDefaultForm() {
        setDefaultForm(invoiceModel);
    }

    private void setDefaultForm(InvoiceModel model) {
        if (model != null) {

            for (InvoiceFormModel item : invoiceForms) {

                if (item.isLocalDefault() && item.getPrintTemplate() == model.getInvoiceType()) {
                    defaultModel = item;
                    break;
                } else if (item.isDefault() && item.getPrintTemplate() == model.getInvoiceType())
                    defaultModel = item;

            }
            if (defaultModel != null) {
                model.setInvoiceFormId(defaultModel.getId());
                model.setFormNo(defaultModel.getFormNo());
                model.setSign(defaultModel.getSign());
                //setInvoiceForm(defaultModel);
            }
        }
    }

    private void printBill(boolean old) {
        /*if (!new PrintWorker(this).printItem(refuelData, printMode, INVOICE_TYPE.BILL)) {
            refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        }
        */
        if (!printWorker.printBill(invoiceModel, old)) {
            refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        }

//        setResult(RESULT_OK);
//        finish();

    }

    private void printInvoice(boolean old) {
        //boolean isOK = new PrintWorker(this).printItem(refuelData, printMode, INVOICE_TYPE.INVOICE);

        boolean isOK = printWorker.printInvoice(invoiceModel, old);

        if (!isOK) {
            refuelData.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.ERROR);
        } else {

            //showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        }

//        setResult(RESULT_OK);
//        finish();

    }

    private void checkAll(boolean checked) {
        ListView lv = findViewById(R.id.refuel_preview_truck_list);
        if (checked)
            ((TruckArrayAdapter) lv.getAdapter()).selectAll();
        else
            ((TruckArrayAdapter) lv.getAdapter()).selectNone();
    }

    private void updateFieldData(int id, String text) {
        Logger.appendLog("PRW", m_Title);
        String oldValue = "";
        switch (id) {
            case R.id.refuel_preview_charter_name:
                oldValue = refuelData.getInvoiceNameCharter();
                refuelData.setInvoiceNameCharter(text);
                break;
        }
        Logger.appendLog("PRW", oldValue + "-->" + text);
    }

    private int REVIEW_WINDOW = 8899;
    private void showReview(){
        Intent intent = new Intent(this, ReviewActivity.class);
        intent.putExtra("FLIGHT_ID", refuelData.getFlightId());
        intent.putExtra("FLIGHT_UUID", refuelData.getFlightUniqueId());
        startActivityForResult(intent, REVIEW_WINDOW);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);

        int id = v.getId();
        isSplit = false;
        switch (id) {
            case R.id.refuel_preview_review :
                showReview();
                break;
            case R.id.refuel_preview_Addbm_2505 :
                openNew();
                break;
            case R.id.refuel_preview_split:
                showSplit();
                break;
            case R.id.truck_item_select_all:
                checkAll(((CheckBox) v).isChecked());
                break;
            case R.id.refuel_preview_print_refuel:
                if (refuelData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                    doRefuel();
                }
                break;

            case R.id.refuel_preview_print_receipt:
                printMode = PRINT_MODE.ONE_ITEM;
                openReceipt();
                break;
            case R.id.refuel_preview_print_return:
                printMode = PRINT_MODE.ALL_ITEM;
                openReceipt(true);
                break;
            case R.id.refuel_preview_print_all:
                printMode = PRINT_MODE.ALL_ITEM;
                openPrintInvoice();
                //preview();
                break;
            case R.id.refuel_preview_new_item:
                createNewItem();
                break;
            case R.id.btnUpdate:
                //save();
                break;
            case R.id.refuel_preview_charter_name:
                showConfirmMessage(R.string.change_charter_confirm, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        m_Title = getString(R.string.update_charter_name);
                        showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                        return null;
                    }
                });

                break;
            case R.id.refuel_preview_aircraftCode:
                m_Title = getString(R.string.update_aircraftCode);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_aircraftType:
                m_Title = getString(R.string.update_aircraftType);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_realAmount:

                m_Title = getString(R.string.update_real_amount);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_weight:
                if (refuelData.getDensity() <= 0)
                    showErrorMessage(R.string.density_must_input);
                else {
                    m_Title = getString(R.string.update_real_amount_kg);
                    showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                }
                break;
            case R.id.refuel_preview_density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_Temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_qc_no:
                m_Title = getString(R.string.update_qc_no);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_parking:
                m_Title = getString(R.string.update_parking_lot);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_preview_vat:
                openVatSpinner();
                break;
//            case R.id.refuel_preview_product:
//                openProductSpinner();
//                break;
            case R.id.refuel_preview_airline:
                showConfirmMessage(R.string.change_airline_confirm, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        openAirlineDialog();
                        return null;
                    }
                });

                break;
            case R.id.refuel_preview_return:
                if (refuelData.getDensity() <= 0)
                    showErrorMessage(R.string.density_must_input);
                else {
                    //m_Title = getString(R.string.update_return_amount);
                    //showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    showReturnInput(refuelData.getReturnAmount(), refuelData.getReturnUnit());
                }
                break;
            case R.id.refuel_preview_weight_note:
                m_Title = getString(R.string.update_weight_note);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                break;
            case R.id.refuel_preview_routeName:
                m_Title = getString(R.string.update_route_name);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuel_preview_return_invoice_number:
                m_Title = getString(R.string.update_return_invoice_number);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                break;
            case R.id.refuel_preview_price:
                m_Title = getString(R.string.update_price);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_preview_international:

                updatePrice();

                break;
            case R.id.refuel_preview_driver:
            case R.id.refuel_preview_operator:
                showSelectUser();
                break;
            case R.id.refuel_preview_starttime:
            case R.id.refuel_preview_endtime:
                showTimeDialog(id);
                break;

            case R.id.preview_form_no:
            case R.id.preview_sign:
                showFormNoSpinner();
                break;

            case R.id.preview_invoice_number:
                m_Title = getString(R.string.invoice_number);
                isSplit = true;
                showEditDialog(R.id.refuel_preview_invoice_number, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                break;
            case R.id.btnOpen7501:
                openBM7501();
                break;

            case R.id.btnBack:
                exit();
                break;

            case R.id.refuel_preview_refresh:
                loadData();
                break;
            case R.id.refuel_preview_check_form:
                openCheckForm();

                break;
            case R.id.refuel_preview_start_meter:
                m_Title = getString(R.string.update_start_meter);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
                break;
            case R.id.refuel_preview_end_meter:
                m_Title = getString(R.string.update_end_meter);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);

                break;

            case R.id.btnLeave:
                Button btnLeave = (Button) v;
                // Chỉ khoá nút trong lúc chờ; ẩn nút và hiện nhãn sau khi biết đã lưu được.
                btnLeave.setEnabled(false);

                // Chỉ patch LeaveTime trên bản ghi mới nhất trong Room. Trước đây chỗ này
                // gọi updateBinding() -> postRefuels(allItems), tức POST lại toàn bộ snapshot
                // đang giữ trên màn hình và ghi đè số liệu đồng hồ vừa chốt.
                saveLeaveTime(new Date(), btnLeave);
                break;
        }

    }

    private void exit() {
        DataHelper.unlockSync();
        // Chứng từ là kết quả cuối cùng của mẻ tra nạp: đẩy ngay, không đợi lượt sync định kỳ.
        DataHelper.pushPendingInBackground();
        finish();
    }


    boolean isSplit = false;
    public Date selectedDate = new Date();

    private void openNew() {
        // Ngữ cảnh của phiếu; master data (sân bay, nhân viên, chuyến bay, loại bồn)
        // do B2505NewItemFragement tự tải nên không còn phụ thuộc list bất đồng bộ của activity.
        Bundle args = new Bundle();
        args.putInt(BM2505Factory.ARG_TRUCK_ID, FMSApplication.getApplication().getTruckId());
        args.putInt(BM2505Factory.ARG_AIRPORT_ID, BM2505Factory.getAccountAirportId());
        args.putInt(BM2505Factory.ARG_FLIGHT_ID, refuelData.getFlightId());
        args.putString(BM2505Factory.ARG_FLIGHT_CODE, refuelData.getFlightCode());
        args.putString(BM2505Factory.ARG_AIRCRAFT_CODE, refuelData.getAircraftCode());

        FragmentManager fm = getSupportFragmentManager();
        B2505NewItemFragement.newInstance(args).show(fm, "fragment_edit_name");
    }

    @Override
    public void onBM2505Saved(BM2505Model model) {
        // Màn hình xem trước không hiển thị danh sách BM2505 nên không cần tải lại dữ liệu.
        // Thông báo kết quả lưu đã do fragment đảm nhiệm.
    }
    private void showSplit() {
        isSplit = true;
        m_Title = getString(R.string.input_split_amount);
        m_Text = "0";
        showEditDialog(R.id.refuel_preview_split, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    RefuelBm2508Binding checkFormBinding;
    B2505NewBinding b2505binding;
    boolean isNew = false;

    private void openCheckForm() {

        Dialog checkFormDlg = new BaseDialog(this);

        if (refuelData.getBM2508Result() == null) {
            refuelData.setBM2508Result(15);
            isNew = true;
        }
        checkFormBinding = DataBindingUtil.inflate(checkFormDlg.getLayoutInflater(), R.layout.refuel_bm_2508, null, false);
        checkFormBinding.setMItem(refuelData);
        checkFormDlg.setContentView(checkFormBinding.getRoot());
        checkFormDlg.setCanceledOnTouchOutside(false);
        checkFormDlg.setCancelable(false);

        checkFormDlg.findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isNew)
                    refuelData.setBM2508Result(null);
                checkFormDlg.dismiss();
            }
        });
        checkFormDlg.findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConfirmMessage(R.string.check_form_delete, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        refuelData.setBM2508Result(null);
                        updateBinding();
                        checkFormDlg.dismiss();
                        return null;
                    }
                });

            }
        });
        checkFormDlg.findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                refuelData.setBM2508BondingCable(((CheckBox) checkFormDlg.findViewById(R.id.chk_bonding_cable)).isChecked());
                refuelData.setBM2508FuelingCap(((CheckBox) checkFormDlg.findViewById(R.id.chk_fueling_cap)).isChecked());
                refuelData.setBM2508FuelingHose(((CheckBox) checkFormDlg.findViewById(R.id.chk_fueling_hose)).isChecked());
                refuelData.setBM2508Ladder(((CheckBox) checkFormDlg.findViewById(R.id.chk_ladder)).isChecked());

                updateBinding();

                checkFormDlg.dismiss();
            }
        });


        checkFormDlg.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        checkFormDlg.show();
    }

    private void showFormNoSpinner() {
        //Spinner spn = printDialog.findViewById(R.id.preview_spinner_form_no);
        ArrayAdapter<InvoiceFormModel> adapter;
        if (invoiceModel.getInvoiceType() == INVOICE_TYPE.INVOICE)
            adapter = new ArrayAdapter<InvoiceFormModel>(this, android.R.layout.simple_list_item_single_choice, Arrays.stream(invoiceForms).filter(x -> x.getPrintTemplate() == INVOICE_TYPE.INVOICE).collect(Collectors.toList()));
        else
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, Arrays.stream(invoiceForms).filter(x -> x.getPrintTemplate() == INVOICE_TYPE.BILL).collect(Collectors.toList()));
        //adapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);
        int position = defaultModel != null ? adapter.getPosition(defaultModel) : -1;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setSingleChoiceItems(adapter, position,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ListView lw = ((AlertDialog) dialog).getListView();
                        InvoiceFormModel checkedItem = adapter.getItem(which);
                        invoiceModel.setInvoiceFormId(checkedItem.getId());
                        invoiceModel.setFormNo(checkedItem.getFormNo());
                        invoiceModel.setSign(checkedItem.getSign());
                        previewBinding.invalidateAll();
                        //setInvoiceForm(checkedItem);
                        defaultModel = checkedItem;
                        saveDefaultForm(checkedItem);
                        dialog.dismiss();
                    }
                });
        b.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });
        Dialog d = b.create();
        d.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        d.show();
    }

    private void saveDefaultForm(InvoiceFormModel checkedItem) {
        SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if (checkedItem.getPrintTemplate() == INVOICE_TYPE.BILL)
            editor.putInt("DEFAULT_FORM_BILL", checkedItem.getId());
        else
            editor.putInt("DEFAULT_FORM_INVOICE", checkedItem.getId());
        editor.commit();
    }

    private void setInvoiceForm(InvoiceFormModel checkedItem) {
        if (printItems != null && printItems.size() > 0) {
            for (RefuelItemData item : printItems) {
                item.setInvoiceFormId(checkedItem.getId());
                item.setFormNo(checkedItem.getFormNo());
                item.setSign(checkedItem.getSign());
            }


        }
    }


    private void updatePrice() {

        if (refuelData.getAirlineModel() != null) {
            if (refuelData.getAirlineModel().isInternational() && refuelData.isInternational()) {
                setAll(R.id.refuel_preview_price, refuelData.getAirlineModel().getPrice());
            } else
                setAll(R.id.refuel_preview_price, refuelData.getAirlineModel().getPrice01());

            setAll(R.id.refuel_preview_vat, refuelData.getAirlineModel().isInternational() && !refuelData.isInternational() ? 0.1 : 0);
            setAll(R.id.refuel_preview_international, refuelData.isInternational() );

            refuelData.setPrintTemplate(!refuelData.getAirlineModel().isInternational() && !refuelData.isInternational() ? INVOICE_TYPE.BILL : INVOICE_TYPE.INVOICE);
        }
        updateBinding();
    }
    private void updateProduct() {
        if (refuelData.getProductId() > 0 && productList != null) {
            for (ProductModel product : productList) {
                if (product.getId() == refuelData.getProductId()) {
                    refuelData.setPCode(product.getCode());
                    refuelData.setPName(product.getName());
                    refuelData.setProductName(product.getName());
                    break;
                }
            }
        }

        // Cập nhật giao diện
        if (binding != null)
            binding.invalidateAll();
    }


    private void doRefuel() {
        if (refuelData != null) {
            Intent intent = new Intent(this, RefuelDetailActivity.class);
            com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, refuelData);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    /**
     * Mẻ đã xuất hoá đơn hoặc đã kết xuất dữ liệu thì khoá sửa trên máy tính bảng, vì server
     * đóng băng nhóm sản lượng của những mẻ này (chỉ còn nhận số đồng hồ): nếu vẫn cho sửa thì
     * người dùng tưởng đã sửa xong trong khi hệ thống không nhận, và bản ghi trên server còn
     * thành mâu thuẫn giữa chỉ số đồng hồ với sản lượng.
     *
     * <p>Chỉ xét {@code invoiceNumber} và {@code exported}. Ba dấu hiệu còn lại đều KHÔNG phải
     * bằng chứng đã xuất chứng từ:
     * <ul>
     *   <li>{@code printed} bật ngay khi máy in báo xong, trước lúc nhập số chứng từ — in thử,
     *       in hỏng hay huỷ hộp thoại đều để lại cờ này;</li>
     *   <li>{@code receiptNumber} do server cấp sẵn cho mẻ, có từ khi mẻ còn chưa tra nạp xong
     *       (đối chiếu dữ liệu máy DEMO-03 ngày 11/08: mẻ Status=0 đã mang số 26194DV);</li>
     *   <li>{@code receiptCount} về 0 sau khi đồng bộ nên không phản ánh được số lần đã in.</li>
     * </ul>
     */
    private boolean isDocumentIssued() {
        if (refuelData == null) return false;
        return !isNullOrEmpty(refuelData.getInvoiceNumber())
                || refuelData.isExported();
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * @return true nếu thao tác sửa bị chặn (đã hiện thông báo tương ứng cho người dùng).
     */
    private boolean blockEditIfLocked() {
        if (isDocumentIssued()) {
            showErrorMessage(R.string.edit_locked_after_print);
            return true;
        }
        if (!isEditable) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    private List<UserModel> userList = null;

    private void showSelectUser() {

        if (isDocumentIssued()) {
            showErrorMessage(R.string.edit_locked_after_print);
            return;
        }
        if (!isEditable && !BuildConfig.FHS) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }


        if (userList != null) {
            Dialog dialog = new Dialog(this);
            SelectUserBinding binding = DataBindingUtil.inflate(dialog.getLayoutInflater(), R.layout.select_user, null, false);
            binding.setRefuelItem(refuelData);
            dialog.setContentView(binding.getRoot());
            Spinner spn = dialog.findViewById(R.id.select_user_driver);

            ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, userList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(refuelData.getDriverId(), userList));

            spn = dialog.findViewById(R.id.select_user_operator);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(refuelData.getOperatorId(), userList));

            dialog.show();

            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

                    if (driver.getId() == operator.getId()) {
                        new AlertDialog.Builder(dialog.getContext())
                                .setTitle(R.string.select_user)
                                .setMessage(R.string.error_same_user)
                                .setIcon(R.drawable.ic_error)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                })
                                .show();
                        return;
                    }

                    if (driver != null) {
                        refuelData.setDriverId(driver.getId());
                        refuelData.setDriverName(driver.getName());
                    }

                    if (operator != null) {
                        refuelData.setOperatorId(operator.getId());
                        refuelData.setOperatorName(operator.getName());
                    }

                    updateBinding(false);
                    dialog.dismiss();
                }
            });

            dialog.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
    }

    private int findUser(int userId, List<UserModel> userList) {
        int pos = 0;
        for (UserModel item : userList) {
            if (item.getId() == userId)
                return pos;
            pos++;
        }
        return -1;
    }

    private final String LOG_TAG = "PRW";

    /**
     * Lưu giờ rời đi mà không đụng tới business payload của phiếu.
     * Giao diện chỉ đổi sau khi biết kết quả ghi, thất bại thì trả nút về trạng thái cũ.
     */
    private void saveLeaveTime(final Date leaveTime, final Button btnLeave) {
        final String uniqueId = refuelData != null ? refuelData.getUniqueId() : null;
        if (uniqueId == null || uniqueId.isEmpty()) {
            btnLeave.setEnabled(true);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final DataHelper.PatchResult result = DataHelper.patchRefuel(uniqueId,
                        latest -> latest.setLeaveTime(leaveTime));

                Logger.appendLog(LOG_TAG, "patch LeaveTime uid=" + uniqueId
                        + " applied=" + result.applied
                        + (result.applied ? "" : " reason=" + result.reason));

                // Bấm -> ghi local -> đồng bộ ngay. patchRefuel có gọi Synchronize(), nhưng
                // màn hình này đang giữ khoá sync nên lượt đó bị nuốt tới lúc rời màn hình.
                // Đẩy thẳng, không đi qua khoá.
                if (result.applied) DataHelper.pushPendingInBackground();

                runOnUiThread(() -> {
                    if (!result.applied) {
                        btnLeave.setEnabled(true);
                        showErrorMessage(R.string.save_leave_time_failed);
                        return;
                    }

                    if (result.data != null)
                        refuelData = result.data;

                    // Layout tự quyết định nút và nhãn theo mItem:
                    //   visibility = mItem.leaveTime == null ? VISIBLE : GONE
                    // nên phải ĐƯA BẢN MỚI vào binding. Trước đây chỗ này ẩn nút bằng tay
                    // rồi gọi invalidateAll(), mà mItem vẫn là object CŨ chưa có leaveTime —
                    // biểu thức tính lại ra VISIBLE và ghi đè lệnh ẩn, còn setEnabled(false)
                    // lúc bấm thì không ai gỡ. Kết quả: nút hiện lại và bị xám, nhìn như treo.
                    btnLeave.setEnabled(true);

                    if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
                        binding.setMItem(refuelData);
                        binding.invalidateAll();
                        truckArrayAdapter.notifyDataSetChanged();
                    } else {
                        extractBinding.setMItem(refuelData);
                        extractBinding.invalidateAll();
                    }
                });
            }
        }).start();
    }

    private void updateBinding() {
        updateBinding(true);
    }

    private void updateBinding(boolean updateAll) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.appendLog(LOG_TAG, "post all refuels");
                boolean committed = updateAll
                        ? DataHelper.postRefuels(allItems, true)
                        : RefuelItemData.isCommitted(DataHelper.postRefuel(refuelData, true));

                // Lưu bị chặn mà màn hình vẫn vẽ lại như cũ thì người dùng tin là đã sửa
                // xong, trong khi Room giữ nguyên giá trị cũ.
                if (!committed) {
                    Logger.appendLog(LOG_TAG, "Sửa phiếu chưa lưu được");
                    runOnUiThread(() -> {
                        if (!isFinishing())
                            showErrorMessage(R.string.error_refuel_save_failed);
                    });
                }
            }
        }).start();
        if (refuelData.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
            binding.invalidateAll();
            truckArrayAdapter.notifyDataSetChanged();
        } else
            extractBinding.invalidateAll();


    }

    private final boolean vatFirstClick = true;
    private final boolean airlineFirstClick = true;

    private PRINT_MODE printMode = PRINT_MODE.ALL_ITEM;

    private void openVatSpinner() {
        if (blockEditIfLocked()) return;
        String[] vat_array = getResources().getStringArray(R.array.vat_array);
        int pos = Arrays.asList(vat_array).indexOf(String.format("%.0f%%", refuelData.getTaxRate() * 100));
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.update_tax_rate);

        b.setSingleChoiceItems(R.array.vat_array, pos, (dialog, which) -> {
            NumberFormat format = NumberFormat.getPercentInstance();
            try {
                setAll(R.id.refuel_preview_vat, format.parse(vat_array[which]).doubleValue());
                dialog.dismiss();
                updateBinding();
            } catch (ParseException e) {

            }

        });
        b.create().show();

    }

    private void openProductSpinner() {
        if (blockEditIfLocked()) return;

        if (productList == null || productList.isEmpty()) {
            Toast.makeText(this, R.string.no_product_found, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> productNames = new ArrayList<>();
        for (ProductModel p : productList) {
            productNames.add(p.getCode()); // Đảm bảo getName() không null
        }

        String currentProductName = refuelData.getProductName();
        int currentIndex = productNames.indexOf(currentProductName);

        new AlertDialog.Builder(this)
                .setTitle(R.string.update_product)
                .setSingleChoiceItems(
                        productNames.toArray(new String[0]),
                        currentIndex,
                        (dialog, which) -> {
                            ProductModel selected = productList.get(which);
                            refuelData.setProductId(selected.getId());
                            refuelData.setPCode(selected.getCode());
                            refuelData.setPName(selected.getName());
                            refuelData.setProductName(selected.getCode());
                            updateBinding();
                            dialog.dismiss();
                        })
                .create()
                .show();
    }



    private void openAirlineDialog() {
        Dialog airlineDlg = new Dialog(this);
        airlineDlg.setTitle(R.string.app_name);
        airlineDlg.setContentView(R.layout.airline_select_dialog);
        SearchView searchView = airlineDlg.findViewById(R.id.airline_dlg_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(query);
                adapter.notifyDataSetChanged();
                //lvAirline.setAdapter(adapter);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(newText);
                adapter.notifyDataSetChanged();

                return false;
            }
        });
        ListView lvAirline = airlineDlg.findViewById(R.id.list_airline);
        lvAirline.setAdapter(new AirlineArrayAdapter(this, airlines));
        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                AirlineModel airline = (AirlineModel) parent.getItemAtPosition(position);
                refuelData.setInvoiceNameCharter(null);
                setAirline(airline);


                AirlineArrayAdapter adapter = (AirlineArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter("");
                airlineDlg.dismiss();
//                for (int j = 0; j < parent.getChildCount(); j++) {
//                    if (j==position)
//                    {
//                        parent.getChildAt(j).setBackgroundColor( Color.LTGRAY);
//                        ((CheckedTextView) parent.getChildAt(j).findViewById(R.id.airline_item_check)).setChecked(true);
//                    }
//                    else {
//                        parent.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);
//                        ((CheckedTextView) parent.getChildAt(j).findViewById(R.id.airline_item_check)).setChecked(j == position);
//                    }
//                }

                // change the background color of the selected element
                //view.setBackgroundColor(Color.LTGRAY);
                //((CheckedTextView) view.findViewById(R.id.airline_item_check)).setChecked(true);

            }

        });
        airlineDlg.show();
    }

    private void setAirline(RefuelItemData item, AirlineModel selected) {
        setAirline(item, selected, false);
    }

    private void setAirline(RefuelItemData item, AirlineModel selected, boolean updatePrice) {

        item.setAirlineId(selected.getId());
        //item.setPrice(selected.getPrice());
        /*if (item.isInternational() && item.isInternational())
            item.setPrice(selected.getPrice());
        else
            item.setPrice(selected.getPrice01());*/
        item.setCurrency(selected.getCurrency());
        item.setUnit(selected.getUnit());
        item.setProductName(selected.getProductName());
        //if (selected.getId() != item.getAirlineId())
        item.setTaxRate(!refuelData.isInternational() && selected.isInternational() ? BuildConfig.TAX_RATE : 0);

        item.setAirlineModel(selected);

        //((TextView) findViewById(R.id.refuel_preview_airline)).setText(selected.getName());

        if (item.getInvoiceNameCharter() == null || item.getInvoiceNameCharter().isEmpty() || updatePrice)
            item.setInvoiceNameCharter(selected.getName());

        if (updatePrice) {
            if (item.isInternational())
                item.setPrice(selected.getPrice());
            else
                item.setPrice(selected.getPrice01());

            if (!item.isInternational() && selected.isInternational())
                item.setTaxRate(BuildConfig.TAX_RATE);
            else
                item.setTaxRate(0);
        }
    }

    private void setAirline(AirlineModel selected) {

        for (RefuelItemData itemData : allItems)
            setAirline(itemData, selected, true);


        updateBinding();
    }

    private void setAll(int id, String text) {
        setAll(allItems, id, text);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, String text) {
        for (RefuelItemData item : items) {
            switch (id) {
                case R.id.refuel_preview_aircraftCode:
                    item.setAircraftCode(text);
                    break;
                case R.id.refuel_preview_aircraftType:
                    item.setAircraftType(text);
                    break;
                case R.id.refuel_preview_parking:
                    item.setParkingLot(text);
                    break;
                case R.id.refuel_preview_charter_name:
                    item.setInvoiceNameCharter(text);
                    break;

                case R.id.refuel_preview_routeName:
                    item.setRouteName(text);
                    break;
//                case R.id.refuel_preview_weight_note:
//                    item.setWeightNote(text);
//                    break;

            }
        }
    }

    private void setAll(int id, double val) {
        setAll(allItems, id, val);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, double val) {
        for (RefuelItemData item : items) {
            switch (id) {
                case R.id.refuel_preview_price:
                    item.setPrice(val);
                    //item.setChangeFlag(RefuelItemData.CHANGE_FLAG.PRICE);
                    break;
                case R.id.refuel_preview_vat:
                    item.setTaxRate(val);
                    break;
            }
        }

    }

    private void setAll(int id, boolean val) {
        setAll(allItems, id, val);
    }

    private void setAll(ArrayList<RefuelItemData> items, int id, boolean val) {
        for (RefuelItemData item : items) {
            switch (id) {
                case R.id.refuel_preview_international:
                    item.setInternational(val);
                    break;


            }
        }
    }

    private String m_Text = "";
    private String m_Title = "";

    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }

    private void showEditDialog(final int id, int inputType, String pattern) {
        showEditDialog(id, inputType, pattern, false);
    }

    private void showEditDialog(final int id, int inputType, boolean required) {
        showEditDialog(id, inputType, ".*", required);
    }

    private void showEditDialog(final int id, int inputType, String pattern, boolean required) {

        // Tách phiếu tạo ra mẻ mới nên vẫn cho nhập; các trường hợp còn lại theo khoá chung.
        if (!isSplit && blockEditIfLocked()) return;

        Context context = this;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m_Title);
        Logger.appendLog("PRW", m_Title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);
        String oldVal = ((TextView) findViewById(id)).getText().toString();
        input.setText(oldVal);
        Logger.appendLog("PRW", "Old value: " + oldVal);
        if (id == R.id.refuel_preview_routeName) {
            if (((TextView) findViewById(id)).getText().toString().isEmpty()) {
                SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
                String airport = preferences.getString("AIRPORT", "");
                if (airport != null && !airport.isEmpty())
                    input.setText(airport + "-");
            }


        } else if (id == R.id.refuel_preview_split)
            input.setText("0");

        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }


        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        if (!required) {
            builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        }
        final AlertDialog dialog = builder.create();// builder.show();
        dialog.setCancelable(!required);
        if (!isFinishing()) {
            dialog.show();
            input.requestFocus();
            if (id == R.id.refuel_preview_density)
                input.setSelection(2, input.getText().length());
            else
                input.setSelection(0, input.getText().length());

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (doUpdateResult())
                        dialog.dismiss();
                }

                private boolean doUpdateResult() {

                    m_Text = input.getText().toString().trim();
                    Logger.appendLog("PRW", "New value: " + m_Text);
                    if (required && m_Text.isEmpty()) {
                        showErrorMessage(R.string.empty_required_field);
                        return false;
                    }
                    Pattern regex = Pattern.compile(pattern);
                    Matcher matcher = regex.matcher(m_Text);
                    if (!matcher.find()) {
                        Toast.makeText(getBaseContext(), getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    try {
                        switch (id) {
                            case R.id.refuel_preview_aircraftCode:

                            case R.id.refuel_preview_charter_name:

                            case R.id.refuel_preview_aircraftType:
                                //case R.id.refuel_preview_weight_note:

                            case R.id.refuel_preview_routeName:

                            case R.id.refuel_preview_parking:
                                setAll(id, m_Text);
                                break;
                            case R.id.refuel_preview_weight_note:
                                refuelData.setWeightNote(m_Text);
                                break;
                            case R.id.refuel_preview_invoice_number:
                            case R.id.preview_invoice_number:
                                //refuelData.setInvoiceNumber(m_Text);
                                if (!updateAllInvoice(m_Text)) {
                                    showErrorMessage(R.string.duplicate_invoice_number);
                                    return false;
                                }
                                break;

                            case R.id.refuel_preview_density:
                                double d = numberFormat.parse(m_Text).doubleValue();
                                if (d < 0.72 || d > 0.86) {
                                    new AlertDialog.Builder(context)
                                            .setTitle(R.string.error_data)
                                            .setMessage(R.string.invalid_density)
                                            .setIcon(R.drawable.ic_error)
                                            .setPositiveButton("OK", (dialog1, which) -> {
                                                dialog1.dismiss();
                                            })
                                            .create()
                                            .show();
                                    return false;
                                }
                                refuelData.setDensity(d);
                                //((TextView)findViewById(R.id.refuel_preview_Density)).setText(String.format("%.2f",refuelData.getDensity()));
                                break;
                            case R.id.refuel_preview_Temperature:
                                double t = numberFormat.parse(m_Text).doubleValue();
                                refuelData.setManualTemperature(t);
                                //((TextView)findViewById(R.id.refuel_preview_Temperature)).setText(String.format("%.2f",refuelData.getManualTemperature()));

                                break;
                            case R.id.refuel_preview_realAmount:
                                double realAmount = numberFormat.parse(m_Text).doubleValue();
                                refuelData.setRealAmount(realAmount);
                                // Nhánh đồng hồ lít — không còn xe nào dùng; số đồng hồ trừ
                                // theo gallon. setRealAmount đã tự tính lại số lít.
                                // if (BuildConfig.FHS) {
                                //     double vol = refuelData.getRealAmount() * GALLON_TO_LITTER;
                                //     refuelData.setVolume(vol);
                                //     refuelData.setStartNumber(refuelData.getEndNumber() - vol);
                                // } else
                                refuelData.setStartNumber(refuelData.getEndNumber() - realAmount);
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.GROSS_QTY);


                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;

                            case R.id.refuel_preview_weight:
                                double weight = numberFormat.parse(m_Text).doubleValue();
                                double gallon = refuelData.getDensity() == 0 ? 0 : Math.round(Math.round(weight / refuelData.getDensity()) / GALLON_TO_LITTER);
                                refuelData.setRealAmount(gallon);
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.GROSS_QTY);
                                break;
                            case R.id.refuel_preview_qc_no:
                                refuelData.setQualityNo(m_Text);
                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;
                            case R.id.refuel_preview_price:
                                setAll(id, numberFormat.parse(m_Text).doubleValue());
                                refuelData.setChangeFlag(RefuelItemData.CHANGE_FLAG.PRICE);
                                //((TextView)findViewById(R.id.refuel_preview_realAmount)).setText(String.format("%.2f",refuelData.getRealAmount()));

                                break;
                            case R.id.refuel_preview_return:
                                double returnAmount = numberFormat.parse(m_Text).doubleValue();
                                calculateReturnAmount(returnAmount);
                                break;

                            case R.id.refuel_preview_return_invoice_number:

                                refuelData.setReturnInvoiceNumber(m_Text);
                                break;

                            case R.id.refuel_preview_split:
                                double split = numberFormat.parse(m_Text).doubleValue();
                                if (split > refuelData.getWeight()) {
                                    showErrorMessage(R.string.split_amount_too_big);
                                    return false;
                                } else {

                                    showConfirmMessage(R.string.split_confirm, () -> {
                                        RefuelItemData splitItem = refuelData.split(split);
                                        allItems.add(splitItem);
                                        //truckArrayAdapter.add(splitItem);
                                        updateBinding();
                                        //loadData();
                                        return null;
                                    });

                                }
                                break;

                        }
                    } catch (ParseException ex) {
                        Toast.makeText(getBaseContext(), R.string.invalid_number_format, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    if (!isSplit)
                        updateBinding(false);

                    return true;
                }
            });
        }
    }

    private boolean calculateReturnAmount(double returnAmount) {
        return calculateReturnAmount(returnAmount, RETURN_UNIT.KG);
    }
    private void updateAllReview()
    {
        for (RefuelItemData item : allItems) {
            item.setHasReview(true);


        }
        new Thread(() -> {
            if (!DataHelper.postRefuels(printItems, false)) {
                Logger.appendLog(LOG_TAG, "Ghi nhận đánh giá chưa lưu được");
                runOnUiThread(() -> {
                    if (!isFinishing())
                        showErrorMessage(R.string.error_refuel_save_failed);
                });
            }
        }).start();
        binding.invalidateAll();
    }
    private boolean calculateReturnAmount(double returnAmount, RETURN_UNIT unit) {

        if (refuelData.getDensity() > 0) {
            double vol = unit == RETURN_UNIT.KG ? Math.round(returnAmount / refuelData.getDensity()) : Math.round(returnAmount * GALLON_TO_LITTER);
            double gal = unit == RETURN_UNIT.KG ? Math.round(vol / GALLON_TO_LITTER) : returnAmount;
            double newAmount = Math.round(Math.round(gal * GALLON_TO_LITTER) * refuelData.getDensity());
            if (gal > refuelData.getRealAmount()) {
                showWarningMessage(getString(R.string.return_amount_greater_warning));
                return false;
            } else if (unit == RETURN_UNIT.KG && newAmount != returnAmount) {
                showWarningMessage(getString(R.string.new_return_amount_value) + " " + newAmount + " KG");

            }

            refuelData.setReturnAmount(unit == RETURN_UNIT.KG ? newAmount : returnAmount);
            refuelData.setReturnUnit(unit);
            updateBinding(false);
        }
        return true;
    }
    private boolean updateAllReceipt(String receiptNumber)
    {
        return updateAllReceipt(UUID.randomUUID().toString(), receiptNumber, 0);
    }
    private boolean updateAllReceipt(String uniqueId, String receiptNumber, double techlog) {
        /*for (RefuelItemData item : printItems) {
            if (item.getReceiptNumber() != null && item.getReceiptNumber().equals(receiptNumber)) {
                return false;
            }
        }*/

        // CHỈ ghi các trường của receipt lên bản ghi MỚI NHẤT trong Room.
        // Trước đây chỗ này POST lại toàn bộ snapshot đang giữ trên màn hình: nếu snapshot
        // đã cũ (ví dụ bản server kéo về lúc mở Preview) thì số đồng hồ vừa chốt bị ghi đè —
        // đúng sự cố 4940 bị kéo về 4922 trong log ngày 29/07.
        new Thread(() -> patchAllPrintItems("receipt", latest -> {
            latest.setReceiptNumber(receiptNumber);
            latest.setReceiptUniqueId(uniqueId);
            latest.setWeightNote(String.format("%.0f", techlog));
            latest.setReceiptCount(latest.getReceiptCount() + 1);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
        })).start();

        truckArrayAdapter.notifyDataSetChanged();
        binding.invalidateAll();
        return true;
    }

    /**
     * Patch cùng một nhóm trường metadata lên tất cả phiếu đang in.
     *
     * <p>Mỗi phiếu được đọc lại từ Room rồi mới sửa, nên không có snapshot cũ nào của màn
     * hình chen vào ghi đè dữ liệu nghiệp vụ. Kết quả được đưa ngược về object trên màn hình
     * để lần thao tác sau đứng trên đúng phiên bản.
     */
    private void patchAllPrintItems(String what, DataHelper.RefuelPatch patch) {
        for (RefuelItemData item : printItems) {
            String uniqueId = item.getUniqueId();
            DataHelper.PatchResult result = DataHelper.patchRefuel(uniqueId, patch);

            Logger.appendLog(LOG_TAG, "patch " + what + " uid=" + uniqueId
                    + " applied=" + result.applied
                    + (result.applied ? "" : " reason=" + result.reason));

            if (result.applied && result.data != null)
                syncScreenCopy(result.data);
            else
                runOnUiThread(() -> showErrorMessage(R.string.save_print_info_failed));
        }
        DataHelper.Synchronize();
    }

    /** Đưa bản ghi vừa lưu về các object đang hiển thị để chúng không còn đứng trên bản cũ. */
    private void syncScreenCopy(RefuelItemData saved) {
        if (refuelData != null && saved.getUniqueId() != null
                && saved.getUniqueId().equals(refuelData.getUniqueId()))
            refuelData = saved;

        for (int i = 0; i < allItems.size(); i++) {
            if (saved.getUniqueId() != null
                    && saved.getUniqueId().equals(allItems.get(i).getUniqueId()))
                allItems.set(i, saved);
        }
        for (int i = 0; i < printItems.size(); i++) {
            if (saved.getUniqueId() != null
                    && saved.getUniqueId().equals(printItems.get(i).getUniqueId()))
                printItems.set(i, saved);
        }
        runOnUiThread(() -> {
            truckArrayAdapter.notifyDataSetChanged();
            binding.invalidateAll();
        });
    }

    private boolean updateAllInvoice(String invoiceNumber) {
        return updateAllInvoice(invoiceNumber, invoiceModel.getInvoiceFormId(), invoiceModel.getInvoiceType(), invoiceModel.getTechLog());
    }

    private boolean updateAllInvoice(String invoiceNumber, int formId, INVOICE_TYPE printTemplate,double techlog) {
        for (RefuelItemData item : printItems) {
            if (item.getInvoiceNumber() != null && item.getInvoiceNumber().equals(invoiceNumber)) {
                return false;
            }
        }

        final double price = refuelData.getPrice();
        final double taxRate = refuelData.getTaxRate();

        // Cùng lý do với receipt: chỉ ghi các trường của hoá đơn lên bản ghi mới nhất.
        new Thread(() -> patchAllPrintItems("invoice", latest -> {
            latest.setInvoiceNumber(invoiceNumber);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
            latest.setPrice(price);
            latest.setTaxRate(taxRate);
            latest.setPrintTemplate(printTemplate);
            latest.setInvoiceFormId(formId);
            latest.setWeightNote(String.format("%.0f", techlog));
        })).start();
        if (printDialog != null)
            printDialog.dismiss();
        truckArrayAdapter.notifyDataSetChanged();
        binding.invalidateAll();
        return true;
    }


    private final Context context = this;
    private int mHour;
    private int mMinute;
    private int mDay;

    private void showTimeDialog(int id) {
        if (!isEditable) {

            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }
        final Date date = new Date();
        if (id == R.id.refuel_preview_starttime)
            date.setTime(refuelData.getStartTime().getTime());
        else
            date.setTime(refuelData.getEndTime().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        int mYear = c.get(Calendar.YEAR);
        int mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay,
                                                  int minute) {
                                c.set(Calendar.MINUTE, minute);
                                c.set(Calendar.HOUR_OF_DAY, hourOfDay);

                                // Đường sửa giờ trước đây KHÔNG ghi vết nào: log chỉ có cú
                                // chạm vào ô, không có giá trị cũ, giá trị mới, hay việc sửa
                                // có ăn hay không. Nhật ký xe HAN3-20-7005 ngày 25-08-2026 cho
                                // thấy người dùng chạm ô giờ rồi bấm làm mới bốn lần trong 90
                                // giây — không cách nào biết họ đã sửa gì.
                                Date oldValue = id == R.id.refuel_preview_starttime
                                        ? refuelData.getStartTime() : refuelData.getEndTime();

                                if (id == R.id.refuel_preview_starttime)
                                    refuelData.setStartTime(c.getTime());
                                else if (id == R.id.refuel_preview_endtime)
                                    refuelData.setEndTime(c.getTime());

                                Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                                        "Sửa tay %s uid=%s: %s -> %s",
                                        id == R.id.refuel_preview_starttime
                                                ? "giờ bắt đầu" : "giờ kết thúc",
                                        refuelData.getUniqueId(),
                                        DateUtils.formatDate(oldValue, "dd/MM HH:mm:ss"),
                                        DateUtils.formatDate(c.getTime(), "dd/MM HH:mm:ss")));

                                updateBinding(false);
                            }
                        }, mHour, mMinute, false);
                timePickerDialog.show();

            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }

    private void showReturnInput(double amount, RETURN_UNIT unit) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 5, 0, 5);


        final EditText returnAmountEditText = new EditText(this);
        returnAmountEditText.setText(String.format("%.0f", amount));
        layout.addView(returnAmountEditText);

        final RadioGroup radGroup = new RadioGroup(this);
        radGroup.setOrientation(RadioGroup.HORIZONTAL);
        int RAD_UNIT = 3435248;

        RadioButton radBtn = new RadioButton(this);
        radBtn.setText(RETURN_UNIT.KG.toString());
        radBtn.setPadding(5, 5, 5, 5);
        radBtn.setChecked(unit == RETURN_UNIT.KG);
        radBtn.setId(RAD_UNIT);
        radGroup.addView(radBtn);
        radBtn = new RadioButton(this);
        radBtn.setText(RETURN_UNIT.GALLON.toString());
        radBtn.setPadding(5, 5, 5, 5);
        radBtn.setChecked(unit == RETURN_UNIT.GALLON);
        //radBtn.setId(1);
        radGroup.addView(radBtn);

        layout.addView(radGroup);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.update_return_amount);

        builder.setView(layout);


        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {


            }
        });
        builder.setNegativeButton(R.string.back, null);

        AlertDialog returnDlg = builder.create();
        returnDlg.show();
        returnDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    double amount = numberFormat.parse(returnAmountEditText.getText().toString()).doubleValue();
                    RETURN_UNIT unit = radGroup.getCheckedRadioButtonId() == RAD_UNIT ? RETURN_UNIT.KG : RETURN_UNIT.GALLON;
                    if (calculateReturnAmount(amount, unit))
                        returnDlg.dismiss();
                } catch (Exception ex) {
                    showErrorMessage(R.string.invalid_number_format);
                }
            }
        });

        returnAmountEditText.requestFocus();
    }

    private final boolean isEditing = false;

    @SuppressLint("StaticFieldLeak")
    private void createNewItem() {

        try {
            final RefuelItemData itemData = refuelData.copy();
            itemData.setTruckId(currentApp.getSetting().getTruckId());
            itemData.setTruckNo(currentApp.getTruckNo());

            new AsyncTask<Void, Void, RefuelItemData>() {
                @Override
                protected RefuelItemData doInBackground(Void... voids) {
                    //RefuelItemData response = DataHelper.postRefuel(itemData);
                    return itemData;
                }

                @Override
                protected void onPostExecute(RefuelItemData response) {
                    postRefuelCompleted(response);
                    super.onPostExecute(response);
                }
            }.execute();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void postRefuelCompleted(RefuelItemData itemData) {
        if (itemData != null) {
            Intent intent = new Intent(this, RefuelDetailActivity.class);
            com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, itemData);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // Khoá sync phải được mở dù người dùng rời màn hình bằng đường nào. Trước đây chỉ
        // exit() mở khoá, nên bấm Back / về Home / hệ thống thu hồi activity là khoá treo
        // lại và TOÀN BỘ đồng bộ chết tới khi khởi động lại app — dữ liệu vẫn vào Room đủ
        // nhưng không bao giờ lên tới server.
        DataHelper.unlockSync();
        super.onDestroy();
        refuelData = null;
        Runtime.getRuntime().gc();
    }
}
