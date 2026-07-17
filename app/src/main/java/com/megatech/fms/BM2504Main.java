package com.megatech.fms;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2504Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.BM2504ArrayAdapter;

import java.util.List;
import java.util.concurrent.Callable;

public class BM2504Main extends DateBaseActivity implements View.OnClickListener {

    private List<BM2504Model> dataList;

    public List<FlightModel> flightList;
    public List<AirportsModel> airports;
    public List<TruckModel> truckList;
    public List<UserModel> userList;
    public List<ProductModel> productList;

    // =========================
    // LIFECYCLE
    // =========================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 👉 có thể đổi layout riêng: activity_bm2504_main
        setContentView(R.layout.activity_bm2504_main);

        loaddata();
    }

    // =========================
    // CLICK
    // =========================

    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.btnBack:
                finish();
                break;

            case R.id.btnNew:
                openNew();
                break;

            case R.id.btnDelete:
                openDelete();
                break;

            case R.id.b2504_date:
                showDateDialog();
                break;
        }
    }

    // =========================
    // OPEN NEW / EDIT
    // =========================

    private static final String TAG_B2504_FORM = "B2504_FORM";

    private void openNew() {
        FragmentManager fm = getSupportFragmentManager();
        B2504NewItemFragment fragment = new B2504NewItemFragment();
        fragment.show(fm, TAG_B2504_FORM);
    }

    private void openEdit(BM2504Model model) {
        FragmentManager fm = getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putSerializable("BM2504_MODEL", model);

        B2504NewItemFragment fragment = new B2504NewItemFragment();
        fragment.setArguments(args);
        fragment.show(fm, TAG_B2504_FORM);
    }

    // =========================
    // DELETE
    // =========================

    private void openDelete() {

        ListView lv = findViewById(R.id.b2504_list);
        BM2504ArrayAdapter adapter = (BM2504ArrayAdapter) lv.getAdapter();
        List<Integer> checkedIds = adapter.getCheckedItems();

        if (checkedIds == null || checkedIds.isEmpty())
            return;

        int[] ids = new int[checkedIds.size()];
        for (int i = 0; i < checkedIds.size(); i++)
            ids[i] = checkedIds.get(i);

        showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
            @Override
            public Void call() {

                setProgressDialog();

                new AsyncTask<Void, Void, List<BM2504Model>>() {
                    @Override
                    protected List<BM2504Model> doInBackground(Void... voids) {
                        DataHelper.deleteBM2504(ids);
                        return DataHelper.getBM2504List(selectedDate);
                    }

                    @Override
                    protected void onPostExecute(List<BM2504Model> models) {
                        dataList = models;
                        bindData();
                    }
                }.execute();

                return null;
            }
        });
    }

    // =========================
    // LOAD DATA
    // =========================

    @Override
    public void loaddata() {

        setProgressDialog();

        new AsyncTask<Void, Void, List<BM2504Model>>() {

            @Override
            protected List<BM2504Model> doInBackground(Void... voids) {

                userList   = DataHelper.getUsers();
                flightList = DataHelper.getFlights();
                airports   = DataHelper.getAirports();
                truckList  = DataHelper.getTrucks();
                productList = DataHelper.getProducts();

                return DataHelper.getBM2504List(selectedDate);
            }

            @Override
            protected void onPostExecute(List<BM2504Model> models) {
                dataList = models;
                bindData();
            }

        }.execute();
    }

    // =========================
    // BIND DATA
    // =========================

    @Override
    public void bindData() {

        BM2504ArrayAdapter adapter =
                new BM2504ArrayAdapter(this, dataList);

        ListView lv = findViewById(R.id.b2504_list);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            BM2504Model model =
                    (BM2504Model) parent.getItemAtPosition(position);
            openEdit(model);
        });

        // header info
        ((TextView) findViewById(R.id.b2504_truck_no))
                .setText(currentApp.getTruckNo());

        ((TextView) findViewById(R.id.b2504_date))
                .setText(DateUtils.formatDate(selectedDate, "dd/MM/yyyy"));

        closeProgressDialog();
    }

    // =========================
    // SIGNATURE (nếu cần)
    // =========================

    public static final int REQ_SIGN_2504 = 2504;

    private String lastSignPath;

    public void openSignature() {
        Intent intent = new Intent(this, ReceiptSignActivity.class);
        startActivityForResult(intent, REQ_SIGN_2504);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SIGN_2504
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
