package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.TruckFuelArrayAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

public class B2502Activity extends UserBaseActivity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_b2502);

        loaddata();
    }

    public void loaddata() {
        setProgressDialog();
        //String mData = b.getString("REFUEL", "");
        new AsyncTask<Void, Void, List<TruckFuelModel>>() {
            @Override
            protected List<TruckFuelModel> doInBackground(Void... voids) {
                userList = DataHelper.getUsers();
                return DataHelper.getTruckFuels(selectedDate);
            }

            @Override
            protected void onPostExecute(List<TruckFuelModel> models) {
                truckFuelModels = models;
                bindData();
            }
        }.execute();
    }
    List<TruckFuelModel> truckFuelModels;
    public void bindData() {
        TruckFuelArrayAdapter adapter = new TruckFuelArrayAdapter(this, truckFuelModels);
        ((ListView)findViewById(R.id.b2502_list)).setAdapter(adapter);

        ((ListView)findViewById(R.id.b2502_list)).setOnItemClickListener((adapterView, view, i, l) -> {
            TruckFuelModel model = (TruckFuelModel)adapterView.getItemAtPosition(i);
            openEdit(model);
        });

        ((TextView)findViewById(R.id.b2502_truck_no)).setText(currentApp.getTruckNo());
        ((TextView)findViewById(R.id.b2502_capacity)).setText(String.format("%,.0f",currentApp.getSetting().getCapacity()));
        ((TextView)findViewById(R.id.b2502_date)).setText(DateUtils.formatDate(selectedDate,"dd/MM/yyyy"));

        closeProgressDialog();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id)
        {
            case R.id.btnBack:
                finish();
                break;
            case R.id.btnNew:
                openNew();
                break;
            case R.id.btnDelete:
                openDelete();
                break;

            case R.id.b2502_date:
                showDateDialog();
            default:
                break;
        }
    }

    private void openDelete() {
        ListView lv = (ListView)findViewById(R.id.b2502_list);
        List<Integer> list = ((TruckFuelArrayAdapter)lv.getAdapter()).getCheckedItems();
        if(list.size()>0) {
            int[] ids = new int[list.size()];
            for (int i=0;i<list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    setProgressDialog();
                    //String mData = b.getString("REFUEL", "");
                    new AsyncTask<Void, Void, List<TruckFuelModel>>() {
                        @Override
                        protected List<TruckFuelModel> doInBackground(Void... voids) {
                            DataHelper.deleteTruckFuels(ids);
                            List<TruckFuelModel> lst = DataHelper.getTruckFuels(selectedDate);
                            return lst;
                        }

                        @Override
                        protected void onPostExecute(List<TruckFuelModel> models) {
                            truckFuelModels = models;
                            bindData();
                        }
                    }.execute();
                    return  null;
                }
            });
        }
    }

    private final Context context = this;
    private int mHour;
    private int mMinute;
    private Date selectedDate = new Date();

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
                loaddata();

            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }

    private void openEdit( TruckFuelModel model) {

        FragmentManager fm = getSupportFragmentManager();
        B2502NewItemFragement newItemFragement = new B2502NewItemFragement(model);
        newItemFragement.show(fm, "fragment_edit_name");

    }

    private void openNew() {

        FragmentManager fm = getSupportFragmentManager();
        B2502NewItemFragement newItemFragement = new B2502NewItemFragement();
        newItemFragement.show(fm, "fragment_edit_name");

    }

    public List<UserModel> userList = null;



}
