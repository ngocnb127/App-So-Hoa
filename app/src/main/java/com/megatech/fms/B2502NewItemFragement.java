package com.megatech.fms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.megatech.fms.databinding.B2502NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.UserModel;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

public class B2502NewItemFragement extends DialogFragment {

    private static final String ARG_MODEL = "B2502_MODEL";
    private static final String STATE_MODEL = "B2502_STATE_MODEL";

    private int mYear, mMonth, mDay, mHour, mMinute;
    public B2502NewItemFragement() {
    }

    private TruckFuelModel createNewModel() {
        TruckFuelModel newModel = new TruckFuelModel();
        newModel.setUnit("Gallon");

        newModel.setWaterCheck(true);
        newModel.setOperatorId(FMSApplication.getApplication().getUser().getUserId());
        newModel.setTruckId(FMSApplication.getApplication().getTruckId());
        if (newModel.getQcNo() == null || newModel.getQcNo().trim().isEmpty()) {
            newModel.setQcNo(FMSApplication.getApplication().getQCNo());
        }
        return newModel;
    }

    public static B2502NewItemFragement newInstance(@Nullable TruckFuelModel model) {
        B2502NewItemFragement fragment = new B2502NewItemFragement();
        if (model != null) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_MODEL, model);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_MODEL)) {
            model = (TruckFuelModel) savedInstanceState.getSerializable(STATE_MODEL);
        } else if (getArguments() != null && getArguments().containsKey(ARG_MODEL)) {
            model = (TruckFuelModel) getArguments().getSerializable(ARG_MODEL);
        }

        if (model == null) {
            model = createNewModel();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_MODEL, model);
    }

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    TruckFuelModel model;
    private B2502Activity activity;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        dlg = super.onCreateDialog(savedInstanceState);
        return dlg;
    }

    public void findViews(View v) {
        try {
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    // recursively call this method
                    findViews(child);
                }
            } else if (v instanceof TextView) {

                v.setOnClickListener(this::onClick);
                //do whatever you want ...
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    View rootView;
    B2502NewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.b2502_new, container, false);
        binding.setMItem(this.model);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        activity = ((B2502Activity) getActivity());
        List<UserModel> userList = activity.userList;
        Spinner spn = view.findViewById(R.id.b2502_new_operator);
        ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<UserModel>(activity, R.layout.support_simple_spinner_dropdown_item, userList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spn.setAdapter(spinnerAdapter);
        if (model.getOperatorId() > 0) {
            for (int i = 0; i < userList.size(); i++)
                if (model.getOperatorId() == userList.get(i).getId()) {
                    spn.setSelection(i);
                    break;
                }
        }
        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                UserModel user = (UserModel) adapterView.getItemAtPosition(i);
                model.setOperatorId(user.getId());
                model.setOperatorName(user.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

    }


    @SuppressLint("NonConstantResourceId")
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.b2502_new_amount:
                m_Title = getString(R.string.update_fuel_amount);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER);
                break;

            case R.id.b2502_new_accumulatedRefuelAmount:
                m_Title = getString(R.string.update_fuel_accumulatedRefuelAmount);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER);
                break;

            case R.id.b2502_new_back:
                dlg.dismiss();

                break;
            case R.id.b2502_new_save:
                save();

                break;

            case R.id.b2502_new_tank_no:
                m_Title = getString(R.string.update_tank_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2502_new_ticket_no:
                m_Title = getString(R.string.update_ticket_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2502_new_maintenance_staff:
                m_Title = getString(R.string.update_maintenance_staff);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                break;
            case R.id.b2502_new_qc_no:
                m_Title = getString(R.string.update_qc_no);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2505_new_appearance_cb:
                dlg.findViewById(R.id.b2505_new_appearance).setVisibility(View.GONE);
                model.setAppearanceCheck("C&B");
                binding.invalidateAll();
                break;
            case R.id.b2505_new_appearance_other:
            case R.id.b2505_new_appearance:
                dlg.findViewById(R.id.b2505_new_appearance).setVisibility(View.VISIBLE);
                m_Title = getString(R.string.update_appearance_check);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2502_new_start_time:
            case R.id.b2502_new_end_time:
            case R.id.b2502_new_test_start_time:
            case R.id.b2502_new_test_end_time:
                showTimeDialog(id);
                break;
        }
    }

    private void save() {

        // 🔥 Bắt buộc QCNo
        if (model.getQcNo() == null || model.getQcNo().trim().isEmpty()) {
            activity.showErrorMessage(R.string.update_qc_no);
            return;
        }

        Date startTime = model.getStartTime();
        Date endTime = model.getEndTime();

        Date testStartTime = model.getTestStartTime();
        Date testEndTime = model.getTestEndTime();
        long tenMinutesMillis = 10 * 60 * 1000;
        if (startTime != null && endTime != null && endTime.getTime() <= startTime.getTime()) {
            activity.showErrorMessage(R.string.invalid_end_time); // ví dụ: "Giờ kết thúc phải lớn hơn giờ bắt đầu"
            return; // Dừng lại, không tiếp tục lưu
        }

        if (testStartTime != null && testEndTime != null && testEndTime.getTime() < (testStartTime.getTime() + tenMinutesMillis)) {
            activity.showErrorMessage(R.string.invalid_test_end_time); // "Thời gian bắt đầu test phải cách giờ kết thúc nạp ít nhất 10 phút"
            return;

        }
        if (endTime != null && testStartTime != null  && testStartTime.getTime() <= endTime.getTime()) {
            activity.showErrorMessage(R.string.invalid_test_start_time_end_time);
            return; // Dừng lại, không tiếp tục lưu
        }

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    DataHelper.postTruckFuel(model);
                    return true;
                } catch (Exception ex) {
                    Logger.appendLog("B2502", "Save failed: " + ex.getMessage());
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean saved) {
                super.onPostExecute(saved);
                if (!saved) {
                    activity.showErrorMessage(R.string.error_saving_data);
                    return;
                }

                if (model.getId() == 0) {
                    if (!model.getFullVolumn())
                        activity.currentApp.setInventory((float) Math.round(model.getAmount()), model.getQcNo());
                    else
                        activity.currentApp.setInventory((float) Math.round(model.getAmount()), model.getQcNo(), model.getFullVolumn());
                }
                activity.loaddata();
                dlg.dismiss();
            }


        }.execute();


    }

    private void showTimeDialog(int id) {
        final Date date = new Date();

        if (id == R.id.b2502_new_start_time )
            date.setTime(model.getStartTime().getTime());
        else if (id == R.id.b2502_new_end_time )
            date.setTime(model.getEndTime().getTime());
        else if (id == R.id.b2502_new_test_start_time )
            date.setTime(model.getTestStartTime().getTime());
        else if (id == R.id.b2502_new_test_end_time )
            date.setTime(model.getTestEndTime().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(),
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                c.set(Calendar.MINUTE, minute);
                                c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                updateTime(id, c);
                            }
                        }, mHour, mMinute, false);
                timePickerDialog.show();
            }
        }, mYear, mMonth, mDay);

        datePickerDialog.show();
    }


    private Calendar getCalendar(int id) {
        final Date date = new Date();
        if (id == R.id.b2502_new_start_time)
            date.setTime(this.model.getStartTime().getTime());
        else if (id == R.id.b2502_new_end_time)
            date.setTime(this.model.getEndTime().getTime());
        else if (id == R.id.b2502_new_test_start_time)
            date.setTime(this.model.getTestStartTime().getTime());
        else if (id == R.id.b2502_new_test_end_time)
            date.setTime(this.model.getTestEndTime().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c;
    }


    private void updateTime(int id, Calendar c) {

        if (id == R.id.b2502_new_start_time)
            this.model.setStartTime(c.getTime());
        else if (id == R.id.b2502_new_end_time)
            this.model.setEndTime(c.getTime());
        else if (id == R.id.b2502_new_test_start_time)
            this.model.setTestStartTime(c.getTime());
        else if (id == R.id.b2502_new_test_end_time)
            this.model.setTestEndTime(c.getTime());
        updateBinding();
    }
    private void updateBinding() {

        binding.invalidateAll();


    }
    private String m_Text = "";
    private String m_Title = "";

    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }

    private void showEditDialog(final int id, int inputType, String pattern) {
        showEditDialog(id, inputType, pattern, false);
    }

    private void showEditDialog(final int id, int inputType, boolean required) {
        showEditDialog(id, inputType, ".*", required);
    }

    private void showEditDialog(final int id, int inputType, String pattern, boolean required) {


        Context context = this.getActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(m_Title);
        final EditText input = new EditText(context);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);

        input.setText(((TextView) dlg.findViewById(id)).getText());


        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }


        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        if (!required) {
            builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        }
        final AlertDialog dialog = builder.create();// builder.show();
        dialog.setCancelable(!required);

        dialog.show();
        input.requestFocus();

        input.setSelection(0, input.getText().length());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doUpdateResult())
                    dialog.dismiss();
            }

            private boolean doUpdateResult() {

                m_Text = input.getText().toString().trim();
                if (required && m_Text.isEmpty()) {
                    return false;
                }
                Pattern regex = Pattern.compile(pattern);
                Matcher matcher = regex.matcher(m_Text);
                if (!matcher.find()) {
                    return false;
                }
                try {
                    switch (id) {

                        case R.id.b2502_new_amount:
                            double d = numberFormat.parse(m_Text).doubleValue();
                            if (d > activity.currentApp.getSetting().getCapacityLitter()) {
                                activity.showErrorMessage(R.string.amount_must_less_than_capacity);
                                return false;
                            }
                            model.setAmount(d);
                            break;
                        case R.id.b2502_new_accumulatedRefuelAmount:
                            double db = numberFormat.parse(m_Text).doubleValue();
                                model.setAccumulateRefuelAmount(db);
                            break;
                        case R.id.b2502_new_maintenance_staff:
                            model.setMaintenanceStaff(m_Text);
                            break;
                        case R.id.b2502_new_tank_no:
                            model.setTankNo(m_Text);
                            break;
                        case R.id.b2502_new_ticket_no:
                            model.setTicketNo(m_Text);
                            break;
                        case R.id.b2502_new_qc_no:
                            model.setQcNo(m_Text);
                            break;

                        case R.id.b2505_new_appearance_other:
                        case R.id.b2505_new_appearance:
                            model.setAppearanceCheck(m_Text);
                            break;

                    }
                } catch (Exception ex) {
                    return false;
                }
                binding.invalidateAll();
                return true;
            }
        });
    }
}
