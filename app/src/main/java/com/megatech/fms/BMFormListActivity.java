package com.megatech.fms;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.BaseModel;
import com.megatech.fms.model.FlightModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Màn hình danh sách dùng chung cho các biểu mẫu BM 25.06 / 25.09 (cùng luồng với B2505Activity)
public abstract class BMFormListActivity<T extends BaseModel> extends DateBaseActivity implements View.OnClickListener, UpdateSensitiveScreen {

    private static final int[] COLUMNS = {R.id.bm_col1, R.id.bm_col2, R.id.bm_col3, R.id.bm_col4, R.id.bm_col5};

    public List<FlightModel> flightList = null;
    private List<T> dataList;

    protected abstract int getTitleRes();

    protected abstract String[] getHeaders();

    protected abstract String[] getColumns(T item);

    protected abstract List<T> getList(Date date);

    protected abstract void delete(int[] ids);

    // model == null: tạo mới
    protected abstract DialogFragment createItemDialog(T model);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_bm_form);
            ((TextView) findViewById(R.id.bm_title)).setText(getTitleRes());
            View header = findViewById(R.id.bm_header);
            header.findViewById(R.id.bm_item_chk).setVisibility(View.INVISIBLE);
            setColumns(header, getHeaders());
            loaddata();
        } catch (Throwable t) {
            showException("onCreate", t);
        }
    }

    // Hiện lỗi ngay trên màn hình (thay vì văng app) để dễ chẩn đoán khi không có logcat
    protected void showException(String where, Throwable t) {
        Log.e(TAG, where, t);
        Logger.appendLog(TAG, where + ": " + Log.getStackTraceString(t));
        StringBuilder msg = new StringBuilder(where).append("\n").append(t);
        StackTraceElement[] stack = t.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 8); i++)
            msg.append("\n  at ").append(stack[i]);
        if (t.getCause() != null)
            msg.append("\nCaused by: ").append(t.getCause());
        try {
            closeProgressDialog();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(msg.toString())
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        try {
            if (id == R.id.btnBack)
                finish();
            else if (id == R.id.btnNew)
                createItemDialog(null).show(getSupportFragmentManager(), "bm_form_item");
            else if (id == R.id.btnDelete)
                openDelete();
            else if (id == R.id.bm_date)
                showDateDialog();
        } catch (Throwable t) {
            showException("onClick", t);
        }
    }

    private void openDelete() {
        ListView lv = findViewById(R.id.bm_list);
        List<Integer> list = ((ItemAdapter) lv.getAdapter()).getCheckedItems();
        if (list.size() > 0) {
            int[] ids = new int[list.size()];
            for (int i = 0; i < list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, () -> {
                setProgressDialog();
                new AsyncTask<Void, Void, List<T>>() {
                    @Override
                    protected List<T> doInBackground(Void... voids) {
                        delete(ids);
                        DataHelper.Synchronize();
                        return getList(selectedDate);
                    }

                    @Override
                    protected void onPostExecute(List<T> models) {
                        dataList = models;
                        bindData();
                    }
                }.execute();
                return null;
            });
        }
    }

    @Override
    public void loaddata() {
        setProgressDialog();
        new AsyncTask<Void, Void, List<T>>() {
            private Throwable error;

            @Override
            protected List<T> doInBackground(Void... voids) {
                try {
                    userList = DataHelper.getUsers();
                    flightList = DataHelper.getFlights();
                    return getList(selectedDate);
                } catch (Throwable t) {
                    error = t;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<T> models) {
                dataList = models;
                bindData();
                if (error != null)
                    showException("loaddata", error);
            }
        }.execute();
    }

    @Override
    public void bindData() {
        try {
            ListView lv = findViewById(R.id.bm_list);
            lv.setAdapter(new ItemAdapter(dataList != null ? dataList : new ArrayList<>()));
            lv.setOnItemClickListener((adapterView, view, i, l) -> {
                try {
                    T model = (T) adapterView.getItemAtPosition(i);
                    createItemDialog(model).show(getSupportFragmentManager(), "bm_form_item");
                } catch (Throwable t) {
                    showException("openItem", t);
                }
            });

            ((TextView) findViewById(R.id.bm_truck_no)).setText(currentApp.getTruckNo());
            ((TextView) findViewById(R.id.bm_date)).setText(DateUtils.formatDate(selectedDate, "dd/MM/yyyy"));
        } catch (Throwable t) {
            showException("bindData", t);
        }
        closeProgressDialog();
    }

    private static void setColumns(View row, String[] values) {
        for (int i = 0; i < COLUMNS.length; i++)
            ((TextView) row.findViewById(COLUMNS[i])).setText(i < values.length ? values[i] : "");
    }

    private class ItemAdapter extends ArrayAdapter<T> {
        private final boolean[] checked;

        ItemAdapter(List<T> objects) {
            super(BMFormListActivity.this, 0, objects);
            checked = new boolean[objects.size()];
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.bm_form_item, parent, false);
            setColumns(convertView, getColumns(getItem(position)));
            CheckBox cb = convertView.findViewById(R.id.bm_item_chk);
            cb.setChecked(checked[position]);
            cb.setOnClickListener(v -> checked[position] = cb.isChecked());
            return convertView;
        }

        List<Integer> getCheckedItems() {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < checked.length; i++)
                if (checked[i])
                    items.add(getItem(i).getLocalId());
            return items;
        }
    }
}
