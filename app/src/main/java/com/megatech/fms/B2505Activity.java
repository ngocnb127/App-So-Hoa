package com.megatech.fms;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.view.BM2505ArrayAdapter;

import java.util.List;
import java.util.concurrent.Callable;

public class B2505Activity extends DateBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_b2505);
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
            case R.id.b2505_date:
                showDateDialog();
            default:
                break;
        }
    }
    private void openEdit( BM2505Model model) {

        FragmentManager fm = getSupportFragmentManager();
        B2505NewItemFragement newItemFragement = new B2505NewItemFragement(model);
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }

    private void openNew() {

        FragmentManager fm = getSupportFragmentManager();
        B2505NewItemFragement newItemFragement = new B2505NewItemFragement();
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }


    private void openDelete() {
        ListView lv = (ListView)findViewById(R.id.b2505_list);
        List<Integer> list = ((BM2505ArrayAdapter)lv.getAdapter()).getCheckedItems();
        if(list.size()>0) {
            int[] ids = new int[list.size()];
            for (int i=0;i<list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    setProgressDialog();
                    //String mData = b.getString("REFUEL", "");
                    new AsyncTask<Void, Void, List<BM2505Model>>() {
                        @Override
                        protected List<BM2505Model> doInBackground(Void... voids) {
                            DataHelper.deleteBM2505(ids);
                            List<BM2505Model> lst = DataHelper.getBM2505List(selectedDate);
                            return lst;
                        }

                        @Override
                        protected void onPostExecute(List<BM2505Model> models) {
                            dataList = models;
                            bindData();
                        }
                    }.execute();
                    return  null;
                }
            });
        }
    }

    private List<BM2505Model> dataList;
    public List<FlightModel> flightList = null;

    public List<BM2505ContainerModel> containerList = null;
    @Override
    public void loaddata() {
        setProgressDialog();
        //String mData = b.getString("REFUEL", "");
        new AsyncTask<Void, Void, List<BM2505Model>>() {
            @Override
            protected List<BM2505Model> doInBackground(Void... voids) {
                userList = DataHelper.getUsers();
                flightList =  DataHelper.getFlights();
                containerList = DataHelper.getBM2505ContainerList();
                List<BM2505Model> lst = DataHelper.getBM2505List(selectedDate);
                return lst;
            }

            @Override
            protected void onPostExecute(List<BM2505Model> models) {
                dataList = models;
                bindData();
            }
        }.execute();
    }

    @Override
    public void bindData() {
        BM2505ArrayAdapter adapter = new BM2505ArrayAdapter(this, dataList);
        ((ListView)findViewById(R.id.b2505_list)).setAdapter(adapter);

        ((ListView)findViewById(R.id.b2505_list)).setOnItemClickListener((adapterView, view, i, l) -> {
            BM2505Model model = (BM2505Model)adapterView.getItemAtPosition(i);
            openEdit(model);
        });

        ((TextView)findViewById(R.id.b2505_truck_no)).setText(currentApp.getTruckNo());
        ((TextView)findViewById(R.id.b2505_capacity)).setText(String.format("%,.0f",currentApp.getSetting().getCapacity()));
        ((TextView)findViewById(R.id.b2505_date)).setText(DateUtils.formatDate(selectedDate,"dd/MM/yyyy"));

        closeProgressDialog();
    }
}
