package com.megatech.fms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.CheckTrucks;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.view.BM2307ArrayAdapter;
import com.megatech.fms.view.BM2505ArrayAdapter;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

public class A2307Activity extends DateBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_a2307);
        loaddata();
    }

    @Override
    public void onClick(View view) {
        int id=view.getId();
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
            case R.id.a2307_date:
                showDateDialog();
            default:
                break;
        }
    }
    private void openEdit(CheckTrucksModel model) {

        FragmentManager fm = getSupportFragmentManager();
        A2307NewItemFragement newItemFragement = new A2307NewItemFragement(model);
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }


    private void openNew() {

        FragmentManager fm = getSupportFragmentManager();
        A2307NewItemFragement newItemFragement = new A2307NewItemFragement();
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }
    public void openDetail(CheckTrucksModel model) {

        FragmentManager fm = getSupportFragmentManager();
        A2307DetailItemFragement detailItemFragement = new A2307DetailItemFragement(model);
        detailItemFragement.show(fm, "fragment_detail_name");
        loaddata();
    }

    private void openDelete() {
        ListView lv = (ListView)findViewById(R.id.a2307_list);
        List<Integer> list = ((BM2307ArrayAdapter)lv.getAdapter()).getCheckedItems();
        if(list.size()>0) {
            int[] ids = new int[list.size()];
            for (int i=0;i<list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    setProgressDialog();
                    //String mData = b.getString("REFUEL", "");
                    new AsyncTask<Void, Void, List<CheckTrucksModel>>() {
                        @Override
                        protected List<CheckTrucksModel> doInBackground(Void... voids) {
                            DataHelper.deleteCheckTrucks(ids);
                            List<CheckTrucksModel> lst = DataHelper.getCheckTrucksList(selectedDate);
                            return lst;
                        }

                        @Override
                        protected void onPostExecute(List<CheckTrucksModel> models) {
                            dataCheckTrucksList = models;
                            bindData();
                        }
                    }.execute();
                    return  null;
                }
            });
        }
    }

    private List<BM2505Model> dataList;
    private List<CheckTrucksModel> dataCheckTrucksList;
    public List<FlightModel> flightList = null;
    public List<AirportsModel> airportslist = null;
    public List<TruckModel> Trucklist = null;
    public List<ShiftModel> Shiftlist = null;

    public List<BM2505ContainerModel> containerList = null;
    @Override
    public void loaddata() {
        setProgressDialog();
        //String mData = b.getString("REFUEL", "");
        new AsyncTask<Void, Void, List<CheckTrucksModel>>() {
            @Override
            protected List<CheckTrucksModel> doInBackground(Void... voids) {
                userList = DataHelper.getUsers();
                airportslist =  DataHelper.getAirports();
                Trucklist =  DataHelper.getTrucks();
                Shiftlist =  DataHelper.getShifts();
                List<CheckTrucksModel> lst = DataHelper.getCheckTrucksList(selectedDate);
                return lst;
            }

            @Override
            protected void onPostExecute(List<CheckTrucksModel> models) {
                dataCheckTrucksList = models;
                bindData();
            }
        }.execute();
    }

    @Override
    public void bindData() {
        BM2307ArrayAdapter adapter = new BM2307ArrayAdapter(this, dataCheckTrucksList);
        ((ListView)findViewById(R.id.a2307_list)).setAdapter(adapter);

        ((ListView)findViewById(R.id.a2307_list)).setOnItemClickListener((adapterView, view, i, l) -> {
            CheckTrucksModel model = (CheckTrucksModel)adapterView.getItemAtPosition(i);
            openEdit(model);
        });

        ((TextView)findViewById(R.id.a2307_date)).setText(DateUtils.formatDate(selectedDate,"dd/MM/yyyy"));

        closeProgressDialog();
    }
}
