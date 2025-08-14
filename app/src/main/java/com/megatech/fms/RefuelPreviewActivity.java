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
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.Logger;
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

public class RefuelPreviewActivity extends UserBaseActivity implements View.OnClickListener {

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
                super.onPostExecute(itemData);

            }
        }.execute();


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
            if (((Button)findViewById(R.id.refuel_preview_review)) !=null)
            ((Button)findViewById(R.id.refuel_preview_review)).setText(hasReview? R.string.review_view: R.string.review_create);
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
               }
               else
                   return false;
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

            if (validate()) {
                String[] printedItems = checkPrintedItems();
                if (printedItems.length > 0) {
                    showCancelReceiptInput(printedItems);
                } else {
                    showReceiptPreview(true);
                }
            }
        } else {
            if (validate(isReturn)) {
                ReceiptModel model = ReceiptModel.createReceipt(printItems,null, true,null, true);
                Intent intent = new Intent(this, PrintReceiptActivity.class);
                intent.putExtra("RECEIPT", model.toJson());
                //startActivityForResult(intent, RECEIPT_WINDOW);
                startActivity(intent);
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
        ReceiptModel model = ReceiptModel.createReceipt(printItems,replacedReceipts,false, oldNumber, createNew);
        Intent intent = new Intent(this, PrintReceiptActivity.class);
        intent.putExtra("RECEIPT", model.toJson());
        startActivityForResult(intent, RECEIPT_WINDOW);
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

            showInvoicePreview();

        }

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
        }

    }

    private void exit() {
        DataHelper.unlockSync();
        finish();
    }

    public List<FlightModel> flightList = null;
    private List<BM2505Model> dataList;
    public List<BM2505ContainerModel> containerList;
    public List<UserModel> userListbm2505 = null;
    boolean isSplit = false;
    public Date selectedDate = new Date();
    private void openNew() {
        FragmentManager fm = getSupportFragmentManager();
        BM2505Model model = new BM2505Model();
        model.flightCode = refuelData.getFlightCode();
        model.aircraftCode = refuelData.getAircraftCode();
        model.setFlightId(refuelData.getFlightId());
        model.setTime(new Date());
        model.setOperatorId(FMSApplication.getApplication().getUser().getUserId());
        model.setTruckId(FMSApplication.getApplication().getTruckId());
        B2505NewItemFragement newItemFragement = new B2505NewItemFragement(model);
        newItemFragement.show(fm, "fragment_edit_name");
        // setProgressDialog();
        //String mData = b.getString("REFUEL", "");
        new AsyncTask<Void, Void, List<BM2505Model>>() {
            @Override
            protected List<BM2505Model> doInBackground(Void... voids) {
                userListbm2505 = DataHelper.getUsers();
                flightList =  DataHelper.getFlights();
                containerList = DataHelper.getBM2505ContainerList();
                List<BM2505Model> lst = DataHelper.getBM2505List(selectedDate);
                return lst;
            }
        }.execute();
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
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

            String data = gson.toJson(refuelData);

            Intent intent = new Intent(this, RefuelDetailActivity.class);
            intent.putExtra("REFUEL", data);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    private List<UserModel> userList = null;

    private void showSelectUser() {

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

    private void updateBinding() {
        updateBinding(true);
    }

    private void updateBinding(boolean updateAll) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.appendLog(LOG_TAG, "post all refuels");
                if (updateAll)
                    DataHelper.postRefuels(allItems, true);
                else
                    DataHelper.postRefuel(refuelData, true);

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
        if (!isEditable) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }
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
        if (!isEditable) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }

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

        if (!isEditable && !isSplit) {
            Toast.makeText(this, R.string.edit_not_allow, Toast.LENGTH_LONG).show();
            return;
        }

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
                                if (BuildConfig.FHS) {
                                    double vol = refuelData.getRealAmount() * GALLON_TO_LITTER;
                                    refuelData.setVolume(vol);
                                    refuelData.setStartNumber(refuelData.getEndNumber() - vol);
                                } else
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
        new Thread(() -> DataHelper.postRefuels(printItems, false)).start();
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

        for (RefuelItemData item : printItems) {
            item.setReceiptNumber(receiptNumber);
            item.setReceiptUniqueId(uniqueId);
            item.setWeightNote(String.format("%.0f",techlog));
            item.setReceiptCount(item.getReceiptCount() + 1);
            item.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);

        }

        new Thread(() -> DataHelper.postRefuels(printItems, true)).start();
        truckArrayAdapter.notifyDataSetChanged();
        binding.invalidateAll();
        return true;
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

        for (RefuelItemData item : printItems) {
            item.setInvoiceNumber(invoiceNumber);
            item.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);

            item.setPrice(refuelData.getPrice());
            item.setTaxRate(refuelData.getTaxRate());
            item.setPrintTemplate(printTemplate);
            item.setInvoiceFormId(formId);

            item.setWeightNote(String.format("%.0f",techlog));
            item.setLocalModified(true);

            //item.setInvoiceModel(invoiceModel);

        }


        new Thread(() -> {

            DataHelper.postRefuels(printItems, true);
        }).start();
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
                                if (id == R.id.refuel_preview_starttime)
                                    refuelData.setStartTime(c.getTime());
                                else if (id == R.id.refuel_preview_endtime)
                                    refuelData.setEndTime(c.getTime());

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
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

            String data = gson.toJson(itemData);

            Intent intent = new Intent(this, RefuelDetailActivity.class);
            intent.putExtra("REFUEL", data);
            startActivityForResult(intent, REFUEL_WINDOW);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refuelData = null;
        Runtime.getRuntime().gc();
    }
}
