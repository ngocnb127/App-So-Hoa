package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.view.ReceiptArrayAdapter;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReceiptActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        loadData();
    }

    private void loadData() {
        TextView txt = (TextView) findViewById(R.id.receip_date_picker);
        if (txt!=null)
            txt.setText(DateUtils.formatDate(selectedDate,"yyyy-MM-dd"));
        new AsyncTask<Void, Void, List<ReceiptModel>>() {
            @Override
            protected List<ReceiptModel> doInBackground(Void... voids) {
                List lst = DataHelper.getReceiptList(selectedDate);
                return lst;
            }

            @Override
            protected void onPostExecute(List<ReceiptModel> receipts) {
                super.onPostExecute(receipts);
                model = receipts;
                bindData();
            }
        }.execute();

    }

    private  List<ReceiptModel> model;
    private void bindData() {
        ListView lv = (ListView) findViewById(R.id.receipt_list);
        ReceiptArrayAdapter adapter = new ReceiptArrayAdapter(this, model);
        lv.setAdapter(adapter);
    }
    private Date selectedDate =  DateUtils.getCurrentDate();
    private void showDateDialog() {

        final Calendar c = Calendar.getInstance();
        c.setTime(selectedDate);
        int mYear = c.get(Calendar.YEAR);
        int mMonth = c.get(Calendar.MONTH);
        int mDay = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);

                selectedDate = c.getTime();
                loadData();

            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btnBack:
                finish();
                break;
            case R.id.receip_date_picker:
                showDateDialog();
                break;
        }
    }
}