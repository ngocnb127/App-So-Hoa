package com.megatech.fms;

import android.app.Activity;
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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.AirlineArrayAdapter;
import com.megatech.fms.view.FlightArrayAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2505NewItemFragement extends DialogFragment {

    private int mYear, mMonth, mDay, mHour, mMinute;
    private List<UserModel> userList;

    public B2505NewItemFragement() {
        this.model = new BM2505Model();
        this.model.setWaterCheck(true);
        this.model.setTime(new Date());
        this.model.setOperatorId(FMSApplication.getApplication().getUser().getUserId());
        this.model.setTruckId(FMSApplication.getApplication().getTruckId());
        this.model.setRTCNo(FMSApplication.getApplication().getQCNo());

    }

    public B2505NewItemFragement(BM2505Model model) {
        this.model = model;
    }

    public static B2505NewItemFragement newInstance(BM2505Model model) {

        B2505NewItemFragement frag = new B2505NewItemFragement(model);
        Bundle args = new Bundle();

        frag.setArguments(args);
        return frag;
    }

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    BM2505Model model;
    private List<FlightModel> flights;
    private List<BM2505ContainerModel> containers;
    private B2505Activity activityb25;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        dlg = super.onCreateDialog(savedInstanceState);
        return dlg;
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        model.setFlightId(args.getInt("FLIGHT_ID", 0));

        String flightCode = args.getString("FLIGHT_CODE", "");
        if (flightCode == null || flightCode.trim().isEmpty()) {
            flightCode = args.getString("FLIGHT_NO", "");
        }
        model.setFlightCode(flightCode);

        model.setAircraftCode(args.getString("AIRCRAFT_CODE", ""));

        model.setTruckId(args.getInt("TRUCK_ID", model.getTruckId()));
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
    B2505NewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.b2505_new, container, false);
        binding.setMItem(this.model);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);

        Activity activity = getActivity();

        if (userList == null) userList = new ArrayList<>();
        if (flights == null) flights = new ArrayList<>();
        if (containers == null) containers = new ArrayList<>();

        Spinner containerSpinner = view.findViewById(R.id.bm2505_container_list);
        Spinner spn = view.findViewById(R.id.b2505_new_operator);

        ArrayAdapter<BM2505ContainerModel> containerAdapter =
                new ArrayAdapter<>(activity, R.layout.support_simple_spinner_dropdown_item, containers);
        containerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        containerSpinner.setAdapter(containerAdapter);

        ArrayAdapter<UserModel> spinnerAdapter =
                new ArrayAdapter<>(activity, R.layout.support_simple_spinner_dropdown_item, userList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spn.setAdapter(spinnerAdapter);

        containerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i >= 0 && i < containers.size()) {
                    BM2505ContainerModel container = (BM2505ContainerModel) adapterView.getItemAtPosition(i);
                    model.setContainerId(container.getId());
                    model.setContainerName(container.getName());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i >= 0 && i < userList.size()) {
                    UserModel user = (UserModel) adapterView.getItemAtPosition(i);
                    model.setOperatorId(user.getId());
                    model.setOperatorName(user.getName());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        if (activity instanceof B2505Activity) {
            B2505Activity b2505Activity = (B2505Activity) activity;
            activityb25 = b2505Activity;

            if (b2505Activity.userList != null) userList.addAll(b2505Activity.userList);
            if (b2505Activity.flightList != null) flights = b2505Activity.flightList;
            if (b2505Activity.containerList != null) containers.addAll(b2505Activity.containerList);

            spinnerAdapter.notifyDataSetChanged();
            containerAdapter.notifyDataSetChanged();

            syncSelectedOperator(spn);
            syncSelectedContainer(containerSpinner);

        } else if (activity instanceof RefuelPreviewActivity) {
            RefuelPreviewActivity refuelPreview = (RefuelPreviewActivity) activity;

            if (refuelPreview.userListbm2505 != null) userList.addAll(refuelPreview.userListbm2505);
            if (refuelPreview.flightList != null) flights = refuelPreview.flightList;
            if (refuelPreview.containerList != null) containers.addAll(refuelPreview.containerList);

            spinnerAdapter.notifyDataSetChanged();
            containerAdapter.notifyDataSetChanged();

            syncSelectedOperator(spn);
            syncSelectedContainer(containerSpinner);

        } else if (activity instanceof RefuelDetailActivity) {
            loadDataAsync(spinnerAdapter, containerAdapter, spn, containerSpinner);
        }
    }
    private void loadDataAsync(ArrayAdapter<UserModel> spinnerAdapter,
                               ArrayAdapter<BM2505ContainerModel> containerAdapter,
                               Spinner spn,
                               Spinner containerSpinner) {

        new AsyncTask<Void, Void, Void>() {
            List<UserModel> usersResult;
            List<FlightModel> flightsResult;
            List<BM2505ContainerModel> containersResult;

            @Override
            protected Void doInBackground(Void... voids) {
                usersResult = DataHelper.getUsers();
                flightsResult = DataHelper.getFlights();
                containersResult = DataHelper.getBM2505ContainerList();
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (!isAdded()) return;

                userList.clear();
                if (usersResult != null) userList.addAll(usersResult);

                flights = flightsResult != null ? flightsResult : new ArrayList<>();

                containers.clear();
                if (containersResult != null) containers.addAll(containersResult);

                spinnerAdapter.notifyDataSetChanged();
                containerAdapter.notifyDataSetChanged();

                syncSelectedOperator(spn);
                syncSelectedContainer(containerSpinner);
            }
        }.execute();
    }
    private void syncSelectedOperator(Spinner spn) {
        if (model.getOperatorId() > 0) {
            for (int i = 0; i < userList.size(); i++) {
                if (model.getOperatorId() == userList.get(i).getId()) {
                    spn.setSelection(i);
                    break;
                }
            }
        }
    }

    private void syncSelectedContainer(Spinner containerSpinner) {
        if (model.getContainerId() > 0) {
            for (int i = 0; i < containers.size(); i++) {
                if (model.getContainerId() == containers.get(i).getId()) {
                    containerSpinner.setSelection(i);
                    break;
                }
            }
        }
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
            case R.id.rad_flight:
                model.setReportType(0);
                binding.invalidateAll();
                break;
            case R.id.rad_other:
                model.setReportType(1);
                binding.invalidateAll();
                break;
            case R.id.b2505_new_back:
                dlg.dismiss();
                break;
            case R.id.b2505_new_save:
                save();
                break;
            case R.id.b2505_new_time:
                showTimeDialog(id);
                break;
            case R.id.b2505_new_flight:
                openFlightSelect();
                break;
            case R.id.bm2505_new_depot:
                m_Title = getString(R.string.update_depot);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_tank_no:
                m_Title = getString(R.string.update_tank_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_hose_pressure:
                m_Title = getString(R.string.update_hose_presure);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_pressure_diff:
                m_Title = getString(R.string.update_pressure_diff);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
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
            case R.id.b2505_new_rtc_no:
                m_Title = getString(R.string.update_rtc_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
                break;
            case R.id.b2505_new_density15:
                m_Title = getString(R.string.update_density15);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_density_diff:
                m_Title = getString(R.string.update_density_diff);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_note:
                m_Title = getString(R.string.update_note);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
            case R.id.b2505_new_max_flowrate:
                m_Title = getString( R.string.update_max_flowrate);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
        }
    }

    private void save() {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.postBM2505(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (activityb25 instanceof B2505Activity)
                {
                    activityb25.loaddata();
                }
                dlg.dismiss();
            }


        }.execute();


    }

    private void openFlightSelect() {
        Dialog flightDlg = new Dialog(getActivity());
        flightDlg.setTitle(R.string.app_name);
        flightDlg.setContentView(R.layout.flight_select_dialog);
        SearchView searchView = flightDlg.findViewById(R.id.flight_dlg_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(query);
                adapter.notifyDataSetChanged();
                //lvAirline.setAdapter(adapter);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(newText);
                adapter.notifyDataSetChanged();

                return false;
            }
        });
        ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
        lvAirline.setAdapter(new FlightArrayAdapter(getActivity(), flights));
        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                FlightModel flightModel = (FlightModel) parent.getItemAtPosition(position);
                model.setFlightCode(flightModel.getFlightCode());
                model.setAircraftCode(flightModel.getAircraftCode());
                model.setFlightId(flightModel.getId());
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter("");
                flightDlg.dismiss();

                binding.invalidateAll();
            }

        });
        flightDlg.show();
    }

    private void showTimeDialog(int id)  {

        final Date date = new Date();

        if (id == R.id.b2505_new_time )
            date.setTime(model.getTime().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
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
        if (id == R.id.b2505_new_time)
            date.setTime(this.model.getTime().getTime());


        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c;
    }
    private void updateTime(int id, Calendar c) {

        if (id == R.id.b2505_new_time)
            this.model.setTime(c.getTime());
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
        if ( id==R.id.b2505_new_density15)
            input.setSelection(2, input.getText().length());
        else
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
                    double d;
                    switch (id) {

                        case R.id.bm2505_new_depot:
                            model.setDepot(m_Text);
                            break;
                        case R.id.b2505_new_tank_no:
                            model.setTankNo(m_Text);
                            break;
                        case R.id.b2505_new_rtc_no:
                            model.setRTCNo(m_Text);
                            break;
                        case R.id.b2505_new_temperature:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setTemperature(d);
                            break;
                        case R.id.b2505_new_density:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setDensity(d);
                            break;
                        case R.id.b2505_new_density15:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setDensity15(d);
                            break;
                        case R.id.b2505_new_density_diff:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setDensityDiff(d);
                            break;
                        case R.id.b2505_new_appearance_other:
                        case R.id.b2505_new_appearance:
                            model.setAppearanceCheck(m_Text);
                            break;
                        case R.id.b2505_new_pressure_diff:
                            model.setPressureDiff(m_Text);
                            break;
                        case R.id.b2505_new_hose_pressure:
                            model.setHosePressure(m_Text);
                            break;
                        case R.id.b2505_new_note:
                            model.setNote(m_Text);
                            break;
                        case R.id.b2505_new_max_flowrate:
                            d = numberFormat.parse(m_Text).doubleValue();

                            model.setMaxFlowRate(d);
                            break;

                    }
                } catch (NumberFormatException ex) {
                    activityb25.showErrorMessage(R.string.invalid_number_format);
                    return false;
                } catch (Exception ex) {
                    return false;
                }
                binding.invalidateAll();
                return true;
            }
        });
    }
}
