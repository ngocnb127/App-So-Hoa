package com.megatech.fms.view;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.megatech.fms.A2307Activity;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.R;
import com.megatech.fms.databinding.A2307ItemBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.CheckTrucksModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BM2307ArrayAdapter extends ArrayAdapter<CheckTrucksModel> {
    private A2307Activity activitya2307;
    public BM2307ArrayAdapter(@NonNull Activity  activity, @NonNull List<CheckTrucksModel> objects) {
        super(activity, 0,  objects);
        allItems = objects;
        checked = new boolean[objects.size()] ;
        activitya2307 = (A2307Activity) activity;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.a2307_item, parent, false);
        }
        A2307ItemBinding binding =  DataBindingUtil.bind(convertView);

        CheckTrucksModel  item =getItem(position);
                binding.setMItem(item);

        CheckBox cb = convertView.findViewById(R.id.a2307_item_chk);

        if (cb != null) {
            cb.setChecked(checked[position]);

            cb.setOnClickListener(v -> {
                checked[position] = cb.isChecked();
                if (cb.isChecked())
                    checkedItems.add((Integer)item.getLocalId());
                else
                    checkedItems.remove((Integer)item.getLocalId());
            });
        }
        Button btnDriver = convertView.findViewById(R.id.a2307_driver2307);
        if (btnDriver != null){
            btnDriver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckTrucksModel model = getItem(position);
                    model.setUserActivedId(FMSApplication.getApplication().getUser().getUserId());
                    model.setIsActive(true);
                    model.setDateActived(new Date());
                    model.setUserActivedName(FMSApplication.getApplication().getUser().getUserName());
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            DataHelper.postCheckTrucks(model);
                           return null;
                        }
                        @Override
                        protected void onPostExecute(Void aVoid) {
                            super.onPostExecute(aVoid);
                                activitya2307.loaddata();
                        }
                    }.execute();
                }
            });
        }
        Button btnNVtranap = convertView.findViewById(R.id.a2307_nvtranap);
        if (btnNVtranap != null){
            btnNVtranap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckTrucksModel model = getItem(position);
                    model.setUserActived2Id(FMSApplication.getApplication().getUser().getUserId());
                    model.setIsActive2(true);
                    model.setDateActived2(new Date());
                    model.setUserActived2Name(FMSApplication.getApplication().getUser().getUserName());
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            DataHelper.postCheckTrucks(model);
                            return null;
                        }
                        @Override
                        protected void onPostExecute(Void aVoid) {
                            super.onPostExecute(aVoid);
                                activitya2307.loaddata();
                        }
                    }.execute();
                }
            });
        }
//        Button btnUser = convertView.findViewById(R.id.a2307_user);
//        if (btnUser != null){
//            btnUser.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    CheckTrucksModel model = getItem(position);
//                    model.setUserActived3Id(FMSApplication.getApplication().getUser().getUserId());
//                    model.setIsActive3(true);
//                    model.setDateActived3(new Date());
//                    model.setUserActived3Name(FMSApplication.getApplication().getUser().getUserName());
//                    new AsyncTask<Void, Void, Void>() {
//                        @Override
//                        protected Void doInBackground(Void... voids) {
//                            try {
//                                Thread.sleep(500);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                            DataHelper.postCheckTrucks(model);
//                            return null;
//                        }
//                        @Override
//                        protected void onPostExecute(Void aVoid) {
//                            super.onPostExecute(aVoid);
//                                activitya2307.loaddata();
//                        }
//                    }.execute();
//                }
//            });
//        }

        Button btnDetail = convertView.findViewById(R.id.a2307_detail2307);
        if (btnDetail != null){
            btnDetail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckTrucksModel model = getItem(position);
                    activitya2307.openDetail(model);
                }
            });
        }
        return  binding.getRoot();
    }

    boolean[] checked;
    List<CheckTrucksModel> allItems;
    List<Integer> checkedItems = new ArrayList<>();

    public List<Integer> getCheckedItems() {
        try {
            checkedItems.clear();
            for (int i = 0; i < allItems.size(); i++)
                if (checked[i])
                    checkedItems.add(allItems.get(i).getLocalId());
        }catch (Exception ex)
        {}
        return  checkedItems;
    }

    public void selectAll() {
        selectAll(true);
        notifyDataSetChanged();
    }

    public void selectNone() {
        selectAll(false);
        notifyDataSetChanged();
    }

    void selectAll(boolean selected) {
        for (int i = 0; i < checked.length; i++) {
            checked[i] = selected;
        }
    }
}
