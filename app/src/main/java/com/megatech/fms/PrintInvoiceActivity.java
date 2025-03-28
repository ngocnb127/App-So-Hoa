package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.databinding.DataBindingUtil;

import com.megatech.fms.databinding.ActivityPrintInvoiceBinding;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.view.InvoiceItemAdapter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class PrintInvoiceActivity extends UserBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_print_invoice);

        printWorker = new PrintWorker();
        printWorker.setPrintStateListener(new PrintWorker.PrintStateListener() {
            @Override
            public void onConnectionError() {
                runOnUiThread(() -> showErrorMessage(R.string.printer_error));
            }

            @Override
            public void onError() {


                runOnUiThread(() -> showMessage(R.string.validate, R.string.print_error, R.drawable.ic_error, () -> {

                    showInputData(R.string.return_invoice_number, "", InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".*", true, new OnInputCompleted() {
                        @Override
                        public boolean onOK(String text) {
                            model.setInvoiceNumber(text);
                            isOK = true;
                            return true;
                        }

                        @Override
                        public void onCancel() {

                        }

                        @Override
                        public void onCompleted() {
                            save();
                        }
                    });
                    return null;
                }));
            }

            @Override
            public void onSuccess() {

                if (!printTest) {
                    model.setPrinted(true);
                    runOnUiThread(()->{
                        findViewById(R.id.btnComplete).setEnabled(true);
                        showInfoMessage(R.string.print_completed, R.string.print_completed_message,R.drawable.ic_info, null);
                    });
                    binding.invalidateAll();

                }



            }

        });

        loadData();
    }

    private void save() {

            if (isOK) {

                setProgressDialog();
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        DataHelper.postInvoice(model);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void response) {
                        postCompleted();
                        super.onPostExecute(response);
                    }
                }.execute();

            }

    }

    boolean printTest = false;
    boolean isOK = false;
    InvoiceModel model;
    private void loadData() {
        setProgressDialog();
        Bundle b = getIntent().getExtras();
        String data = b.getString("INVOICE");
        model = InvoiceModel.fromJson(data);
        Logger.appendLog("INV", "loading invoice " + model.getFlightCode() );
        bindData();

    }
    ActivityPrintInvoiceBinding binding;
    private void bindData()
    {
        if (model != null) {
            invoiceForms = FMSApplication.getApplication().getInvoiceForms();
            binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.activity_print_invoice, null, false);
            binding.setInvoiceItem(model);
            setContentView(binding.getRoot());

            ListView lv = findViewById(R.id.invoice_preview_item_list);
            lv.setAdapter(new InvoiceItemAdapter(this, model.getItems()));

            RadioGroup radioGroup = findViewById(R.id.radioTemplate);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int id = checkedId;

                model.setInvoiceType(id == R.id.radInvoice ? INVOICE_TYPE.INVOICE : INVOICE_TYPE.BILL);
                setDefaultForm(model);
                binding.invalidateAll();

            });
            int selectedIndex = model.getInvoiceType() == INVOICE_TYPE.INVOICE ? 0 : 1;


            ((RadioButton) radioGroup.getChildAt(selectedIndex)).setChecked(true);

        }
        closeProgressDialog();
    }
    InvoiceFormModel[] invoiceForms;
    InvoiceFormModel defaultModel;


    private void showFormNoSpinner() {
        //Spinner spn = printDialog.findViewById(R.id.preview_spinner_form_no);
        ArrayAdapter<InvoiceFormModel> adapter;
        if (model.getInvoiceType() == INVOICE_TYPE.INVOICE)
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
                        model.setInvoiceFormId(checkedItem.getId());
                        model.setFormNo(checkedItem.getFormNo());
                        model.setSign(checkedItem.getSign());
                        binding.invalidateAll();
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
    @Override
    public void onClick(View view) {
        super.onClick(view);
        switch (view.getId())
        {
            case R.id.btnBack:
                exit();
                break;

            case R.id.btnPrint:
                print(false);
                break;
            case R.id.btnPrintTest:
                print(true);
                break;
            case R.id.preview_sign:
            case R.id.preview_form_no:
                showFormNoSpinner();
                break;
            case R.id.btnComplete:
                inputInvoiceNumber();
                break;

            case R.id.btnTechlog:
                showInputData(R.string.input_techlog, String.format("%,.0f", model.getTechLog()), InputType.TYPE_NUMBER_FLAG_DECIMAL, ".*", false, new OnInputCompleted() {
                    @Override
                    public boolean onOK(String text)  {
                        try {
                            Locale locale = Locale.getDefault();
                            NumberFormat numberFormat = NumberFormat.getInstance(locale);
                            double d = numberFormat.parse(text).doubleValue();
                            model.setTechLog(d);
                            return true;
                        }
                        catch (ParseException ex)
                        {
                            return false;
                        }
                    }

                    @Override
                    public void onCancel() {

                    }

                    @Override
                    public void onCompleted() {
                        binding.invalidateAll();
                    }
                });
                break;
        }
    }

    private void exit() {
        if (model.isPrinted()) {
            showConfirmMessage(R.string.confirm_invoice_exit, () -> {
                Logger.appendLog("User comfirmed exit without invoice number");
                finish();
                return null;
            });
        }
        else finish();
    }

    private void inputInvoiceNumber() {
        showInputData(R.string.invoice_number, "", InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".*", true, new OnInputCompleted() {
            @Override
            public boolean onOK(String text) {

                model.setInvoiceNumber(text);

                Logger.appendLog("INV", "input invoice number " + text);
                isOK = true;

                return true;
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onCompleted() {
                save();
            }
        });
    }

    private void print(boolean test) {
        boolean oldTemplate = ((RadioButton)findViewById(R.id.radOld)).isChecked();
        boolean invoice = ((RadioButton)findViewById(R.id.radInvoice)).isChecked();
        printTest = test;
        if (invoice)
            printInvoice(oldTemplate);
        else printBill(oldTemplate);
    }

    private void postCompleted()
    {

        Intent returnIntent = new Intent();
        returnIntent.putExtra("number",model.getInvoiceNumber());
        returnIntent.putExtra("formId",model.getInvoiceFormId());
        returnIntent.putExtra("printTemplate",model.getInvoiceType());
        returnIntent.putExtra("techlog", model.getTechLog());
        setResult(Activity.RESULT_OK,returnIntent);
        finish();
    }


    private void printBill(boolean old) {

        if (!printWorker.printBill(model, old)) {
        }


    }
    private PrintWorker printWorker;

    private void printInvoice(boolean old) {

        boolean isOK = printWorker.printInvoice(model, old);



    }

}