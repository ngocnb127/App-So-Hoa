package com.megatech.fms;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.BM2503ArrayAdapter;

import java.util.List;
import java.util.concurrent.Callable;

public class BM2503Main extends DateBaseActivity implements View.OnClickListener {


    private List<BM2503Model> dataList;
    public List<FlightModel> flightList = null;

    public List<AirportsModel> airports;
    public List<TruckModel> truckList;
    public List<UserModel> userList;
    public List<ProductModel> productList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bm2503_main);
        loaddata();
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btnBack:
                finish();
                break;
            case R.id.btnNew:
                openNew();
                break;
            case R.id.btnDelete:
                openDelete();
                break;
            case R.id.b2503_date:
                showDateDialog();
                break;
            default:
                break;
        }
    }

    /* =========================
       OPEN NEW / EDIT
     ========================= */

    private static final String TAG_B2503_FORM = "B2503_FORM";

    private void openNew() {
        FragmentManager fm = getSupportFragmentManager();
        B2503NewItemFragment fragment = new B2503NewItemFragment();
        fragment.show(fm, TAG_B2503_FORM);
    }

    private void openEdit(BM2503Model model) {
        FragmentManager fm = getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putSerializable("BM2503_MODEL", model); // hoặc Parcelable

        B2503NewItemFragment fragment = new B2503NewItemFragment();
        fragment.setArguments(args);

        fragment.show(fm, TAG_B2503_FORM);
    }

    /* =========================
       DELETE
     ========================= */

    private void openDelete() {
        ListView lv = findViewById(R.id.b2503_list);
        List<Integer> list = ((BM2503ArrayAdapter) lv.getAdapter()).getCheckedItems();

        if (list.size() > 0) {
            int[] ids = new int[list.size()];
            for (int i = 0; i < list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
                @Override
                public Void call() {
                    setProgressDialog();
                    new AsyncTask<Void, Void, List<BM2503Model>>() {
                        @Override
                        protected List<BM2503Model> doInBackground(Void... voids) {
                            DataHelper.deleteBM2503(ids);
                            return DataHelper.getBM2503List(selectedDate);
                        }

                        @Override
                        protected void onPostExecute(List<BM2503Model> models) {
                            dataList = models;
                            bindData();
                        }
                    }.execute();
                    return null;
                }
            });
        }
    }

    /* =========================
       LOAD DATA
     ========================= */

    @Override
    public void loaddata() {
        setProgressDialog();
        new AsyncTask<Void, Void, List<BM2503Model>>() {
            @Override
            protected List<BM2503Model> doInBackground(Void... voids) {
                userList = DataHelper.getUsers();
                flightList = DataHelper.getFlights();
                airports = DataHelper.getAirports();
                truckList = DataHelper.getTrucks();
                productList = DataHelper.getProducts();

                return DataHelper.getBM2503List(selectedDate);
            }

            @Override
            protected void onPostExecute(List<BM2503Model> models) {
                dataList = models;
                bindData();
            }
        }.execute();
    }

    /* =========================
       BIND DATA
     ========================= */

    @Override
    public void bindData() {
        BM2503ArrayAdapter adapter = new BM2503ArrayAdapter(this, dataList);

        ListView lv = findViewById(R.id.b2503_list);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            BM2503Model model = (BM2503Model) parent.getItemAtPosition(position);
            openEdit(model);
        });

        ((TextView) findViewById(R.id.b2503_truck_no))
                .setText(currentApp.getTruckNo());

        ((TextView) findViewById(R.id.b2503_date))
                .setText(DateUtils.formatDate(selectedDate, "dd/MM/yyyy"));

        closeProgressDialog();
    }

    public static final int REQ_SIGN_2503 = 2503;

    public void openSignature() {
        Intent intent = new Intent(this, ReceiptSignActivity.class);
        startActivityForResult(intent, REQ_SIGN_2503);
    }


    private String lastSignPath;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SIGN_2503
                && resultCode == Activity.RESULT_OK
                && data != null) {

            lastSignPath = data.getStringExtra("SIGN_PATH");
        }
    }

    public String consumeLastSignPath() {
        String p = lastSignPath;
        lastSignPath = null;
        return p;
    }







}
