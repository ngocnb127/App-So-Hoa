package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.megatech.fms.data.DataRepository;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.UserInfo;
import com.megatech.fms.view.RefuelRecyclerView;
import com.megatech.fms.view.RefuelRecyclerViewAdapter;

import java.util.List;

public class ExtractActivity extends UserBaseActivity implements View.OnClickListener {
    private List<RefuelItemData> lstData;
    private UserBaseActivity activity;
    private RefuelRecyclerViewAdapter mAdapter;

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        setToolbar();
        activity = this;
        Button btnNewExtract = findViewById(R.id.btnNewExtract);
        btnNewExtract.setVisibility(View.INVISIBLE);
        ((Runnable) () -> {
            //HttpClient client = new HttpClient();
            //PermissionModel model = client.getPermission();
            //if (model == null || model.isAllowNewRefuel())
            //    btnRefuel.setVisibility(View.VISIBLE);

            if ((currentUser.getPermission() & UserInfo.USER_PERMISSION.CREATE_EXTRACT.getValue()) > 0)
                btnNewExtract.setVisibility(View.VISIBLE);

        }).run();

        loadData();


        //extract_list.setAdapter();

    }

    private void loadData() {
        RecyclerView extract_list = findViewById(R.id.extract_list);
        new Thread(new Runnable() {
            @Override
            public void run() {
                lstData = DataHelper.getRefuelList(true,1);
                if (activity != null)
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (lstData == null) {
                                Toast.makeText(activity, R.string.no_internet_error, Toast.LENGTH_LONG).show();
                                //this.getActivity().finishAffinity();
                            } else {
                                mAdapter = new RefuelRecyclerViewAdapter(activity, lstData);

                                extract_list.setLayoutManager(new GridLayoutManager(activity, 1));

                                extract_list.setAdapter(mAdapter);
                            }
                        }
                    });

            }
        }).start();
    }

    private void new_extract() {
        try {
            Intent intent = new Intent(this, NewRefuelActivity.class);
            intent.putExtra("EXTRACT", true);
            startActivity(intent);
        } catch (Exception ex) {
            Log.e("INVENTORY", ex.getMessage());
        }
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.btnBack)
            finish();
        if (v.getId() == R.id.btnNewExtract) {
            new_extract();
        }

    }
}
