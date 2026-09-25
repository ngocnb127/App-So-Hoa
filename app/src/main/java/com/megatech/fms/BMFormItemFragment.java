package com.megatech.fms;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.DialogFragment;

import com.megatech.fms.model.BaseModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Màn hình nhập dùng chung cho BM 25.06 / 25.09 (cùng cách nhập "chạm để sửa" như B2505NewItemFragement)
public abstract class BMFormItemFragment<T extends BaseModel> extends DialogFragment {

    protected final T model;
    protected ViewDataBinding binding;
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());

    protected BMFormItemFragment(T model) {
        this.model = model;
    }

    protected abstract int getLayoutRes();

    // xử lý khi chạm vào một ô trên biểu mẫu
    protected abstract void onFieldClick(int id);

    // lưu offline + đồng bộ (DataHelper.postBM25xx)
    protected abstract void saveModel();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            binding = DataBindingUtil.inflate(inflater, getLayoutRes(), container, false);
            binding.setVariable(BR.mItem, model);
            return binding.getRoot();
        } catch (Throwable t) {
            reportError("onCreateView", t);
            return null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            setClickListeners(view);
            bindViews(view);
        } catch (Throwable t) {
            reportError("onViewCreated", t);
        }
    }

    // gắn spinner... cho từng biểu mẫu
    protected void bindViews(View view) {
    }

    protected void reportError(String where, Throwable t) {
        if (getActivity() instanceof BMFormListActivity)
            ((BMFormListActivity<?>) getActivity()).showException(getClass().getSimpleName() + "." + where, t);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null)
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void setClickListeners(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++)
                setClickListeners(vg.getChildAt(i));
        } else if (v instanceof TextView && v.getId() != View.NO_ID) {
            v.setOnClickListener(this::onClick);
        }
    }

    private void onClick(View view) {
        int id = view.getId();
        try {
            if (id == R.id.bm_new_back)
                dismiss();
            else if (id == R.id.bm_new_save)
                save();
            else
                onFieldClick(id);
        } catch (Throwable t) {
            reportError("onClick", t);
        }
    }

    protected void refresh() {
        binding.invalidateAll();
    }

    private void save() {
        new AsyncTask<Void, Void, Void>() {
            private Throwable error;

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    saveModel();
                } catch (Throwable t) {
                    error = t;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (error != null) {
                    reportError("save", error);
                    return;
                }
                if (getActivity() instanceof BMFormListActivity)
                    ((BMFormListActivity<?>) getActivity()).loaddata();
                dismiss();
            }
        }.execute();
    }

    public interface OnValue<V> {
        void set(V value);
    }

    protected void editText(int titleRes, String current, OnValue<String> onValue) {
        showInput(titleRes, current, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, onValue);
    }

    protected void editNumber(int titleRes, double current, OnValue<Double> onValue) {
        showInput(titleRes, current == 0 ? "" : numberFormat.format(current),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                text -> {
                    try {
                        onValue.set(text.isEmpty() ? 0 : numberFormat.parse(text).doubleValue());
                    } catch (Exception ex) {
                        ((BaseActivity) requireActivity()).showErrorMessage(R.string.invalid_number_format);
                    }
                });
    }

    private void showInput(int titleRes, String current, int inputType, OnValue<String> onValue) {
        EditText input = new EditText(requireContext());
        input.setInputType(inputType);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("-0123456789,."));
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        input.setText(current);
        input.selectAll();

        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    onValue.set(input.getText().toString().trim());
                    refresh();
                })
                .setNegativeButton(R.string.back, (d, w) -> d.cancel())
                .show();
        input.requestFocus();
    }

    protected void pickTime(Date current, OnValue<Date> onValue) {
        Calendar c = Calendar.getInstance();
        if (current != null)
            c.setTime(current);
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            c.set(year, month, day);
            new TimePickerDialog(requireContext(), (v, hour, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                onValue.set(c.getTime());
                refresh();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    protected void selectFlight(OnValue<FlightModel> onValue) {
        List<FlightModel> flights = getActivity() instanceof BMFormListActivity
                ? ((BMFormListActivity<?>) getActivity()).flightList : null;
        if (flights == null)
            flights = new ArrayList<>();

        Dialog flightDlg = new Dialog(requireContext());
        flightDlg.setTitle(R.string.app_name);
        flightDlg.setContentView(R.layout.flight_select_dialog);
        ListView lvFlight = flightDlg.findViewById(R.id.list_airline);
        FlightArrayAdapter adapter = new FlightArrayAdapter(requireActivity(), flights);
        lvFlight.setAdapter(adapter);
        ((SearchView) flightDlg.findViewById(R.id.flight_dlg_search)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
        lvFlight.setOnItemClickListener((parent, view, position, id) -> {
            onValue.set((FlightModel) parent.getItemAtPosition(position));
            flightDlg.dismiss();
            refresh();
        });
        flightDlg.show();
    }

    // danh sách nhân viên (userList của màn hình danh sách)
    protected void bindUserSpinner(Spinner spn, int selectedId, OnValue<UserModel> onValue) {
        List<UserModel> users = getActivity() instanceof DateBaseActivity
                ? ((DateBaseActivity) getActivity()).userList : null;
        if (users == null)
            users = new ArrayList<>();
        ArrayAdapter<UserModel> adapter = new ArrayAdapter<>(requireContext(), R.layout.support_simple_spinner_dropdown_item, users);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spn.setAdapter(adapter);
        for (int i = 0; i < users.size(); i++)
            if (users.get(i).getId() == selectedId) {
                spn.setSelection(i);
                break;
            }
        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                onValue.set((UserModel) adapterView.getItemAtPosition(i));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }
}
