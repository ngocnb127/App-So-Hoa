package com.megatech.fms;

import android.app.AlertDialog;
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

    public B2502NewItemFragement() {
        this.model = new TruckFuelModel();
        this.model.setUnit("Gallon");
        //this.model.setTime(new Date());
        this.model.setOperatorId(FMSApplication.getApplication().getUser().getUserId());
        this.model.setTruckId(FMSApplication.getApplication().getTruckId());

    }

    public B2502NewItemFragement(TruckFuelModel model) {
        this.model = model;
    }

    public static B2502NewItemFragement newInstance(TruckFuelModel model) {

        B2502NewItemFragement frag = new B2502NewItemFragement(model);
        Bundle args = new Bundle();

        frag.setArguments(args);
        return frag;
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
            case R.id.b2502_new_start_time:
            case R.id.b2502_new_end_time:
            case R.id.b2502_new_test_start_time:
            case R.id.b2502_new_test_end_time:
                showTimeDialog(id);
                break;
        }
    }

    private void save() {
        if (model.getId() == 0)
            activity.currentApp.setInventory((float) Math.round(model.getAmount()), model.getQcNo());

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.postTruckFuel(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                activity.loaddata();
                dlg.dismiss();
            }


        }.execute();


    }

    private void showTimeDialog(int id) {

        final Calendar c = Calendar.getInstance();
        c.setTime(model.getTime());
        TimePickerDialog datePickerDialog = new TimePickerDialog(this.getActivity(), new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker timePicker, int i, int i1) {
                c.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                c.set(Calendar.MINUTE, timePicker.getMinute());
                Date t = c.getTime();
                switch (id) {
                    case R.id.b2502_new_end_time:
                        model.setEndTime(t);
                        break;
                    case R.id.b2502_new_start_time:
                        model.setStartTime(t);
                        break;
                    case R.id.b2502_new_test_start_time:
                        model.setTestStartTime(t);
                        break;
                    case R.id.b2502_new_test_end_time:
                        model.setTestEndTime(t);
                        break;
                }

                binding.invalidateAll();
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false);
        datePickerDialog.show();
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
                            } else
                                model.setAmount(d);
                            break;
                        case R.id.b2502_new_accumulatedRefuelAmount:
                            double db = numberFormat.parse(m_Text).doubleValue();
                                model.setAccumulatedRefuelAmount(db);
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
