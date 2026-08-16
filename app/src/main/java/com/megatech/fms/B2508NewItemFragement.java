package com.megatech.fms;

import android.annotation.SuppressLint;
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
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.databinding.B2508NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.io.File;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Intent;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public class B2508NewItemFragement extends DialogFragment {

    private static final int REQUEST_AIRLINE_SIGN = 101;
    private static final int REQUEST_SKYPEC_SIGN = 102;

    private ImageView imgAirlineSign, imgSkypecSign;
    private String urlImageAirline, urlImageSkypec;

    public B2508NewItemFragement() {
        model = new BM2508Model();
        model.setTime(new Date());
        model.setDateCreated(new Date());
        model.setTruckId(FMSApplication.getApplication().getTruckId());
        model.setTruckNo(FMSApplication.getApplication().getTruckNo());
        model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
        model.setStaffId(FMSApplication.getApplication().getUser().getUserId());
        model.setStaffName(FMSApplication.getApplication().getUser().getUserName());
        airports = new ArrayList<>();
        Trucklst = new ArrayList<>();
        model.setTextAirlineSignature("Chạm để ký");
        model.setTextUserSkypecSignature("Chạm để ký");
    }


    public B2508NewItemFragement(BM2508Model model) {
        this.model = model;
        if (model.getFlightCode() == null || model.getFlightCode().isEmpty())
        {
            this.model.setFlightCode(model.getFlightNo()) ;
        }

        this.model.setTextAirlineSignature("Chạm để ký");
        this.model.setTextUserSkypecSignature("Chạm để ký");

        if (model.getFlightCode() == null || "".equals(model.getFlightCode()))
        {
            this.model.setFlightCode(model.getFlightNo()) ;
        }
    }

    public static B2508NewItemFragement newInstance(BM2508Model model) {

        B2508NewItemFragement frag = new B2508NewItemFragement(model);
        Bundle args = new Bundle();

        frag.setArguments(args);
        return frag;
    }

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    BM2508Model model;
    private List<FlightModel> flights;
    private B2508Activity activityb25;
    private List<AirportsModel> airports;
    private List<TruckModel> Trucklst;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        dlg = super.onCreateDialog(savedInstanceState);
        return dlg;
    }
    private void loadMasterDataAsync(Spinner spAirport, Spinner spTruck) {
        new AsyncTask<Void, Void, Void>() {
            List<AirportsModel> tempAirports = new ArrayList<>();
            List<TruckModel> tempTrucks = new ArrayList<>();

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    if (airports == null || airports.isEmpty()) {
                        List<AirportsModel> a = DataHelper.getAirports();
                        if (a != null) tempAirports = a;
                    }

                    if (Trucklst == null || Trucklst.isEmpty()) {
                        List<TruckModel> t = BuildConfig.FHS
                                ? DataHelper.getFHSTrucks()
                                : DataHelper.getTrucks();
                        if (t != null) tempTrucks = t;
                    }
                } catch (Exception e) {
                    Logger.appendLog("BM2508", "loadMasterDataAsync error: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (!isAdded()) return;

                if ((airports == null || airports.isEmpty()) && tempAirports != null) {
                    airports = tempAirports;
                }

                if ((Trucklst == null || Trucklst.isEmpty()) && tempTrucks != null) {
                    Trucklst = tempTrucks;
                }

                bindAirportAndTruck(spAirport, spTruck);
            }
        }.execute();
    }
    private void bindAirportAndTruck(Spinner spnairport, Spinner spntruck) {
        if (!isAdded() || spnairport == null || spntruck == null) return;

        ArrayAdapter<AirportsModel> spnairportAdapter =
                new ArrayAdapter<>(requireContext(),
                        R.layout.support_simple_spinner_dropdown_item,
                        airports != null ? airports : new ArrayList<>());
        spnairportAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spnairport.setAdapter(spnairportAdapter);

        if (model.getAirportId() != null && model.getAirportId() > 0 && airports != null) {
            for (int i = 0; i < airports.size(); i++) {
                if (model.getAirportId().equals(airports.get(i).getId())) {
                    spnairport.setSelection(i, false);
                    break;
                }
            }
        }

        spnairport.post(() -> {
            spnairport.setEnabled(false);
            spnairport.setClickable(false);
        });

        spnairport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                AirportsModel airport = (AirportsModel) adapterView.getItemAtPosition(i);
                if (airport != null) {
                    model.setAirportId(airport.getId());
                    model.setAirportName(airport.getName());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        ArrayAdapter<TruckModel> spntruckAdapter =
                new ArrayAdapter<>(requireContext(),
                        R.layout.support_simple_spinner_dropdown_item,
                        Trucklst != null ? Trucklst : new ArrayList<>());
        spntruckAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spntruck.setAdapter(spntruckAdapter);

        if (model.getTruckId() != null && model.getTruckId() > 0 && Trucklst != null) {
            for (int i = 0; i < Trucklst.size(); i++) {
                if (model.getTruckId().equals(Trucklst.get(i).getId())) {
                    spntruck.setSelection(i, false);
                    break;
                }
            }
        }

        spntruck.post(() -> {
            spntruck.setEnabled(false);
            spntruck.setClickable(false);
        });

        spntruck.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                TruckModel truck = (TruckModel) adapterView.getItemAtPosition(i);
                if (truck != null) {
                    model.setTruckId(truck.getId());
                    model.setTruckNo(truck.getTruckNo());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
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
    B2508NewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.b2508_new, container, false);
        binding.setMItem(this.model);


        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindFromArguments();
        readListsFromArguments();
        findViews(view);

        if (airports == null) airports = new ArrayList<>();
        if (Trucklst == null) Trucklst = new ArrayList<>();
        if (flights == null) flights = new ArrayList<>();

        Activity activity = getActivity();

        List<BM2508Model.ResultModel> resultModelList = new ArrayList<>();
        resultModelList.add(new BM2508Model.ResultModel("KGs"));
        resultModelList.add(new BM2508Model.ResultModel("LBs"));
        resultModelList.add(new BM2508Model.ResultModel("USG"));

        if (activity instanceof B2508Activity) {
            B2508Activity b2508Activity = (B2508Activity) activity;

            if (airports.isEmpty() && b2508Activity.airportslist != null) {
                airports = b2508Activity.airportslist;
            }

            if (Trucklst.isEmpty() && b2508Activity.Trucklist != null) {
                Trucklst = b2508Activity.Trucklist;
            }

            if (flights.isEmpty() && b2508Activity.flightList != null) {
                flights = b2508Activity.flightList;
            }

            activityb25 = b2508Activity;
        }

        syncFlightSelectionOnEdit();

        Spinner spnairport = view.findViewById(R.id.b2508_new_airport);
        Spinner spntruck = view.findViewById(R.id.b2508_new_truck_no);

        bindAirportAndTruck(spnairport, spntruck);

        if (airports.isEmpty() || Trucklst.isEmpty()) {
            loadMasterDataAsync(spnairport, spntruck);
        }

        Spinner spngc7 = view.findViewById(R.id.b2508_unit);
        ArrayAdapter<BM2508Model.ResultModel> spngcAdapter =
                new ArrayAdapter<>(requireContext(),
                        R.layout.support_simple_spinner_dropdown_item,
                        resultModelList);
        spngcAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc7.setAdapter(spngcAdapter);

        if (model.getUnit() != null) {
            for (int i = 0; i < resultModelList.size(); i++) {
                if (model.getUnit().equals(resultModelList.get(i).getName())) {
                    spngc7.setSelection(i);
                    break;
                }
            }
        }

        spngc7.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                BM2508Model.ResultModel result =
                        (BM2508Model.ResultModel) adapterView.getItemAtPosition(position);
                model.setUnit(result.getName());
                recalcFuelOnBoardGallonByUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        initDensityOnOpen();
        renderAirlineSignature();
        renderSkypecSignature();
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
            case R.id.b2508_new_back:
                dlg.dismiss();
                break;
            case R.id.b2508_new_save:
                save();
                break;
            case R.id.b2508_new_time:
                showTimeDialog();
                break;

            case R.id.b2508_new_DateCreated:
                showTimeDayDialog();
                break;

            case R.id.b2508_new_flight:
                openFlightSelect();
                break;

//            case R.id.b2508_new_flightNo:
//                m_Title = getString(R.string.flightNo);
//                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
//                break;

            case R.id.b2508_new_aircraftType:
                m_Title = "Loại tàu bay";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2508_new_fuelUplift:
                m_Title = "Lượng nhiên liệu tra nạp";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2508_new_beforeFueling:
                m_Title = "Lượng trước tra nạp";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2508_new_fuelOnBoard:
                m_Title = "Tổng nhiên liệu chuyến bay";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2508_new_fuelOnBoardGalon:
                m_Title = "Tổng nhiên liệu chuyến bay (Gallon)";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2508_note:
                m_Title = "Ghi chú";
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.tv_density:
                m_Title = getString(R.string.update_density);
                showDensityDialog();
                break;
            case R.id.b2508_signature_airline:
                openAirlineSignature();
                break;

            case R.id.b2508_signature_skypec:
                openSkypecSignature();
                break;



        }
    }



    private boolean saving;

    private void save() {
        if (saving) return;
        saving = true;

        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                View saveButton = getView() == null ? null : getView().findViewById(R.id.b2508_new_save);
                if (saveButton != null) saveButton.setEnabled(false);
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    DataHelper.postBM2508(model);
                    return true;
                } catch (Exception ex) {
                    Logger.appendLog("SAVE_BM2508", ex.toString());
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {

                if (getContext() == null) return;

                if (success) {

                    // ✅ Thông báo thành công
                    new AlertDialog.Builder(getContext())
                            .setTitle("Thành công")
                            .setMessage("Đã lưu phiếu BM2508")
                            .setPositiveButton("OK", (dialog, which) -> {

                                // reload nếu cần
                                Activity activity = getActivity();
                                if (activity instanceof B2508Activity) {
                                    ((B2508Activity) activity).loaddata();
                                }

                                // ✅ Đóng dialog
                                if (dlg != null) dlg.dismiss();
                            })
                            .setCancelable(false)
                            .show();

                } else {
                    saving = false;
                    View saveButton = getView() == null ? null : getView().findViewById(R.id.b2508_new_save);
                    if (saveButton != null) saveButton.setEnabled(true);

                    // ❌ Thông báo lỗi
                    new AlertDialog.Builder(getContext())
                            .setTitle("Lỗi")
                            .setMessage("Không thể lưu dữ liệu")
                            .setPositiveButton("OK", null)
                            .show();
                }
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
        lvAirline.setAdapter(new FlightArrayAdapter(getActivity(),
                flights != null ? flights : new ArrayList<>()));
        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                FlightModel flightModel = (FlightModel) parent.getItemAtPosition(position);
                model.setFlightCode(flightModel.getFlightCode());
                model.setFlightNo(flightModel.getFlightCode());
                model.setAircraftType(flightModel.getAircraftType());
                model.setFlightId(flightModel.getId());

                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter("");
                flightDlg.dismiss();

                binding.invalidateAll();
            }

        });
        flightDlg.show();
    }
    private void showTimeDayDialog() {
        final Date date = new Date();
        date.setTime(model.getDateCreated().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        int mYear = c.get(Calendar.YEAR);
        int mMonth = c.get(Calendar.MONTH);
        int mDay = c.get(Calendar.DAY_OF_MONTH);
        int mHour = c.get(Calendar.HOUR_OF_DAY);
        int mMinute = c.get(Calendar.MINUTE);
        DatePickerDialog datePickerDialog = new DatePickerDialog(this.getActivity(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                model.setDateCreated(c.getTime());
                binding.invalidateAll();
            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }
    private void showTimeDialog() {

        final Calendar c = Calendar.getInstance();
        c.setTime(model.getTime());
        TimePickerDialog datePickerDialog = new TimePickerDialog(this.getActivity(), new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker timePicker, int i, int i1) {
                c.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                c.set(Calendar.MINUTE, timePicker.getMinute());
                model.setTime(c.getTime());
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
//        if (id == R.id.a2307_new_Note7) {
//            if (((TextView) dlg.findViewById(id)).getText().toString().isEmpty()) {
//                    input.setText("Không móp méo,nứt vỡ,bất thường");
//            }
//        }
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
        if (id == R.id.b2505_new_density || id==R.id.b2505_new_density15)
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

                        case R.id.b2508_new_flightNo:
                            model.setFlightNo(m_Text);
                            break;
                        case R.id.b2508_new_aircraftType:
                            model.setAircraftType(m_Text);
                            break;

                        case R.id.tv_density:
                            d = Double.parseDouble(m_Text);
                            model.updateDensityFromUser(d); // ⭐ BẮT BUỘC
                            break;

                        case R.id.b2508_new_fuelUplift:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setFuelUplift(d);
                            break;

                        case R.id.b2508_new_beforeFueling:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setBeforeFueling(d);
                            break;

                        case R.id.b2508_new_fuelOnBoard:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setFuelOnBoard(d);
                            break;

                        case R.id.b2508_new_fuelOnBoardGalon:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setFuelOnBoardGallon(d);
                            break;

                        case R.id.b2508_note:
                            model.setNote(m_Text);
                            break;






                    }
                } catch (NumberFormatException ex) {
                if (activityb25 != null) {
                    activityb25.showErrorMessage(R.string.invalid_number_format);
                } else if (getContext() != null) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.app_name)
                            .setMessage(R.string.invalid_number_format)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                return false;
            }catch (Exception ex) {
                    return false;
                }
                binding.invalidateAll();
                return true;
            }
        });
    }
    private void recalcFuelOnBoardGallonByUI() {

        if (model == null || binding == null) return;

        String unit = model.getUnit();
        double fuelUplift = model.getFuelUplift();

        if (unit == null || fuelUplift <= 0) {
            model.setFuelOnBoardGallon(0);
            binding.invalidateAll();
            return;
        }

        if ("KGs".equalsIgnoreCase(unit)) {

            // ✅ CHỈ fallback nếu CHƯA CÓ density
            if (model.getDensity() <= 0) {
                model.applyDefaultDensity();
            }

            model.recalcFuelOnBoardGallon();

        } else if ("USG".equalsIgnoreCase(unit)) {

            model.setFuelOnBoardGallon(Math.round(fuelUplift));

        }

        binding.invalidateAll();
        Logger.appendLog(
                "DENSITY_UI",
                "density=" + model.getDensity()
                        + ", source=" + model.getDensitySource()
        );
    }

    private void openAirlineSignature() {
        startActivityForResult(
                new Intent(getActivity(), ReceiptSignActivity.class),
                REQUEST_AIRLINE_SIGN
        );
    }

    private void openSkypecSignature() {
        startActivityForResult(
                new Intent(getActivity(), ReceiptSignActivity.class),
                REQUEST_SKYPEC_SIGN
        );
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) return;

        String file = data.getStringExtra("signature_file");
        if (file == null || file.trim().isEmpty()) return;

        if (requestCode == REQUEST_AIRLINE_SIGN) {
            // local android path
            model.setAirlineSignaturePath(file);

            if (model.getTextAirlineSignature() == null || model.getTextAirlineSignature().trim().isEmpty()
                    || "Chạm để ký".equalsIgnoreCase(model.getTextAirlineSignature())) {
                model.setTextAirlineSignature("✔ Đã ký");
            }
            renderAirlineSignature();
        }
        else if (requestCode == REQUEST_SKYPEC_SIGN) {
            // local android path
            model.setUserSkypecSignaturePath(file);

            if (model.getTextUserSkypecSignature() == null || model.getTextUserSkypecSignature().trim().isEmpty()
                    || "Chạm để ký".equalsIgnoreCase(model.getTextUserSkypecSignature())) {
                model.setTextUserSkypecSignature("✔ Đã ký");
            }
            renderSkypecSignature();
        }

        if (binding != null) {
            binding.invalidateAll();
        }
    }

    private boolean isLocalFileExists(String path) {
        if (path == null || path.trim().isEmpty()) return false;

        // không coi http/https là local file
        String lower = path.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return false;
        }

        File f = new File(path);
        return f.exists() && f.isFile();
    }

    private String pickBestImageSource(String localPath, String apiUrl) {
        if (isLocalFileExists(localPath)) {
            return localPath; // ưu tiên local android
        }

        if (apiUrl != null && !apiUrl.trim().isEmpty()) {
            return apiUrl; // fallback api
        }

        return null;
    }
    private void renderAirlineSignature() {
        if (binding == null || binding.ImageAirlineSign == null) return;

        String source = pickBestImageSource(
                model.getAirlineSignaturePath(),
                model.getUrlImageAirline()
        );

        if (source == null || source.trim().isEmpty()) {
            binding.ImageAirlineSign.setVisibility(View.GONE);
            return;
        }

        if (source.startsWith("http://") || source.startsWith("https://")) {
            Glide.with(this)
                    .load(source)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(binding.ImageAirlineSign);
        } else {
            Glide.with(this)
                    .load(new File(source))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(binding.ImageAirlineSign);
        }

        binding.ImageAirlineSign.setVisibility(View.VISIBLE);
    }

    private void renderSkypecSignature() {
        if (binding == null || binding.ImageUserSkypecSign == null) return;

        String source = pickBestImageSource(
                model.getUserSkypecSignaturePath(),
                model.getUrlImageSkypec()
        );

        if (source == null || source.trim().isEmpty()) {
            binding.ImageUserSkypecSign.setVisibility(View.GONE);
            return;
        }

        if (source.startsWith("http://") || source.startsWith("https://")) {
            Glide.with(this)
                    .load(source)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(binding.ImageUserSkypecSign);
        } else {
            Glide.with(this)
                    .load(new File(source))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(binding.ImageUserSkypecSign);
        }

        binding.ImageUserSkypecSign.setVisibility(View.VISIBLE);
    }

    private void applyLatestDensityAsync() {

        DataRepository repo = DataRepository.getInstance(
                AppDatabase.getInstance(requireContext())
        );

        repo.loadLatestDensityAsync(density -> {

            // ❌ nếu user đã nhập → KHÔNG xử lý async nữa
            if (model.isUserEditedDensity()) {
                Logger.appendLog("DENSITY_UI", "skip async, user edited");
                return;
            }

            if (density != null) {
                model.applyLatestDensity(density);
            } else {
                model.applyDefaultDensity();
            }

            model.recalcFuelOnBoardGallon();
            binding.invalidateAll();
        });

    }

    private void loadLatestDensityFromBM2508() {

        new Thread(() -> {

            // ❌ nếu user đã sửa tay → KHÔNG override
            if (model.isUserEditedDensity()) return;

            int truckId = model.getTruckId();
            Date today = new Date();

            List<BM2508Model> list = DataHelper.getBM2508List(today);

            if (list != null && !list.isEmpty()) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    BM2508Model item = list.get(i);

                    if (item.getTruckId() != null
                            && item.getTruckId().equals(truckId)
                            && item != model) {

                        double d = item.getDensity();
                        if (d >= 0.72 && d <= 0.86) {
                            model.applyLatestDensity(d);
                            Logger.appendLog(
                                    "DENSITY_LOAD",
                                    "from BM2508, density=" + d
                            );
                            break;
                        }
                    }
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> binding.invalidateAll());
            }

        }).start();
    }


    // =========================
    // UNIT SPINNER
    // =========================
//    private void setupUnitSpinner(View view) {
//
//        Spinner spnUnit = view.findViewById(R.id.b2508_unit);
//
//        List<BM2508Model.ResultModel> units = Arrays.asList(
//                new BM2508Model.ResultModel("KGs"),
//                new BM2508Model.ResultModel("USG"),
//                new BM2508Model.ResultModel("LBs")
//        );
//
//        ArrayAdapter<BM2508Model.ResultModel> adapter =
//                new ArrayAdapter<>(getActivity(),
//                        R.layout.support_simple_spinner_dropdown_item,
//                        units);
//
//        spnUnit.setAdapter(adapter);
//
//        if (model.getUnit() != null) {
//            for (int i = 0; i < units.size(); i++) {
//                if (units.get(i).getName().equals(model.getUnit())) {
//                    spnUnit.setSelection(i);
//                    break;
//                }
//            }
//        }
//
//        spnUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                model.setUnit(units.get(position).getName());
//                model.recalcFuelOnBoardGallon();
//                binding.invalidateAll();
//            }
//
//            @Override public void onNothingSelected(AdapterView<?> parent) {}
//        });
//    }

    // =========================
    // DENSITY DIALOG (USER INPUT)
    // =========================
    private void showDensityDialog() {

        Context context = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Nhập tỷ trọng (0.72 – 0.86)");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 0);

        EditText input = new EditText(context);

        // Không ép quá cứng theo keyboard locale
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setRawInputType(InputType.TYPE_CLASS_TEXT);
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));

        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);

        input.setText(String.format(Locale.US, "%.4f", model.getDensity()));
        input.setSelectAllOnFocus(true);

        Button btnDot = new Button(context);
        btnDot.setText("Thêm dấu thập phân .");

        btnDot.setOnClickListener(v -> {
            String text = input.getText().toString();

            // Chỉ cho 1 dấu thập phân
            if (!text.contains(".") && !text.contains(",")) {
                int start = Math.max(input.getSelectionStart(), 0);
                int end = Math.max(input.getSelectionEnd(), 0);

                input.getText().replace(
                        Math.min(start, end),
                        Math.max(start, end),
                        ".",
                        0,
                        1
                );
            }
        });

        layout.addView(input);
        layout.addView(btnDot);

        builder.setView(layout);
        builder.setPositiveButton("Lưu", null);
        builder.setNegativeButton("Hủy", (d, w) -> d.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        input.requestFocus();
        input.setSelection(0, input.getText().length());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            String text = input.getText().toString().trim();

            if (text.isEmpty()) {
                input.setError("Vui lòng nhập tỷ trọng");
                return;
            }

            // Chuẩn hóa dấu phẩy thành dấu chấm
            text = text.replace(",", ".");

            // Không cho nhập nhiều dấu .
            if (text.indexOf(".") != text.lastIndexOf(".")) {
                input.setError("Chỉ được nhập một dấu thập phân");
                return;
            }

            try {
                double d = Double.parseDouble(text);

                if (d < 0.72 || d > 0.86) {
                    input.setError("Tỷ trọng phải trong khoảng 0.72 – 0.86");
                    return;
                }

                model.updateDensityFromUser(d);
                model.recalcFuelOnBoardGallon();

                binding.invalidateAll();
                dialog.dismiss();

                Logger.appendLog(
                        "DENSITY_USER",
                        "density=" + model.getDensity()
                                + ", source=" + model.getDensitySource()
                );

            } catch (NumberFormatException e) {
                input.setError("Định dạng số không hợp lệ");
            }
        });
    }

    // =========================
    // SAVE
    // =========================
    private void initDensityOnOpen() {

        // 1️⃣ Nếu model đã có density → giữ nguyên
        if (model.getDensity() > 0) {
            Logger.appendLog("DENSITY_INIT", "keep existing density=" + model.getDensity());
            return;
        }

        // 2️⃣ Ưu tiên lấy BM2508 gần nhất theo xe
        loadLatestDensityFromBM2508WithFallback();
    }
    private void loadLatestDensityFromBM2508WithFallback() {

        new Thread(() -> {

            // ❌ User đã nhập tay → không override
            if (model.isUserEditedDensity()) {
                Logger.appendLog("DENSITY_INIT", "skip - user edited");
                return;
            }

            int truckId = model.getTruckId();
            Date today = new Date();
            boolean found = false;

            List<BM2508Model> list = DataHelper.getBM2508List(today);

            if (list != null && !list.isEmpty()) {

                // 🔥 SORT THEO DATECREATED DESC → GẦN NHẤT TRƯỚC
                Collections.sort(list, new Comparator<BM2508Model>() {
                    @Override
                    public int compare(BM2508Model a, BM2508Model b) {

                        Date da = a.getDateCreated();
                        Date db = b.getDateCreated();

                        if (da == null && db == null) return 0;
                        if (da == null) return 1;
                        if (db == null) return -1;

                        return db.compareTo(da); // DESC
                    }
                });

                // 🔍 DUYỆT BẢN GHI GẦN NHẤT
                for (BM2508Model item : list) {

                    if (item == null) continue;

                    if (item.getTruckId() != null
                            && item.getTruckId().equals(truckId)
                            && item != model) {

                        double d = item.getDensity();

                        Logger.appendLog(
                                "DENSITY_DEBUG",
                                "truck=" + item.getTruckId()
                                        + ", density=" + d
                                        + ", time=" + item.getDateCreated()
                        );

                        // ✅ Validate nghiệp vụ
                        if (d >= 0.72 && d <= 0.86) {

                            model.applyLatestDensity(d);
                            found = true;

                            Logger.appendLog(
                                    "DENSITY_INIT",
                                    "LATEST BM2508 density=" + d
                                            + " | time=" + item.getDateCreated()
                            );
                            break;
                        }
                    }
                }
            }

            // 3️⃣ FALLBACK → DEFAULT 0.79
            if (!found) {
                model.applyDefaultDensity();
                Logger.appendLog(
                        "DENSITY_INIT",
                        "fallback default density=0.79"
                );
            }

            // 4️⃣ RECALC
            model.recalcFuelOnBoardGallon();

            // 5️⃣ UPDATE UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> binding.invalidateAll());
            }

        }).start();
    }

    private void bindFromArguments() {
        Bundle args = getArguments();
        if (args == null || model == null) return;

        if ((model.getAirportId() == null || model.getAirportId() == 0) && args.containsKey("AIRPORT_ID")) {
            model.setAirportId(args.getInt("AIRPORT_ID", 0));
        }

        if (isNullOrEmpty(model.getAirportName())) {
            model.setAirportName(args.getString("AIRPORT_NAME", ""));
        }

        if ((model.getTruckId() == null || model.getTruckId() == 0) && args.containsKey("TRUCK_ID")) {
            model.setTruckId(args.getInt("TRUCK_ID", 0));
        }

        if (isNullOrEmpty(model.getTruckNo())) {
            model.setTruckNo(args.getString("TRUCK_NO", ""));
        }

        if ((model.getFlightId() == null || model.getFlightId() == 0) && args.containsKey("FLIGHT_ID")) {
            model.setFlightId(args.getInt("FLIGHT_ID", 0));
        }

        if (isNullOrEmpty(model.getFlightCode())) {
            String flightCode = args.getString("FLIGHT_CODE", "");
            model.setFlightCode(flightCode);
            model.setFlightNo(flightCode);
        }

        if (isNullOrEmpty(model.getAircraftType())) {
            model.setAircraftType(args.getString("AIRCRAFT_TYPE", ""));
        }

        if (binding != null) {
            binding.invalidateAll();
        }
    }
    @SuppressWarnings("unchecked")
    private void readListsFromArguments() {
        Bundle args = getArguments();
        if (args == null) return;

        Serializable airportData = args.getSerializable("AIRPORT_LIST");
        if (airportData instanceof ArrayList) {
            airports = (ArrayList<AirportsModel>) airportData;
        }

        Serializable truckData = args.getSerializable("TRUCK_LIST");
        if (truckData instanceof ArrayList) {
            Trucklst = (ArrayList<TruckModel>) truckData;
        }

        if (airports == null) airports = new ArrayList<>();
        if (Trucklst == null) Trucklst = new ArrayList<>();
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
    private void syncFlightSelectionOnEdit() {
        if (model == null || flights == null || flights.isEmpty()) return;

        FlightModel matched = null;

        Integer modelFlightId = model.getFlightId();
        String modelFlightCode = model.getFlightCode();
        String modelFlightNo = model.getFlightNo();

        if (modelFlightId != null && modelFlightId > 0) {
            for (FlightModel f : flights) {
                if (f != null && f.getId() != null && modelFlightId.equals(f.getId())) {
                    matched = f;
                    break;
                }
            }
        }

        if (matched == null && modelFlightCode != null && !modelFlightCode.trim().isEmpty()) {
            for (FlightModel f : flights) {
                if (f != null && f.getFlightCode() != null
                        && modelFlightCode.trim().equalsIgnoreCase(f.getFlightCode().trim())) {
                    matched = f;
                    break;
                }
            }
        }

        if (matched == null && modelFlightNo != null && !modelFlightNo.trim().isEmpty()) {
            for (FlightModel f : flights) {
                if (f != null && f.getFlightCode() != null
                        && modelFlightNo.trim().equalsIgnoreCase(f.getFlightCode().trim())) {
                    matched = f;
                    break;
                }
            }
        }

        if (matched != null) {
            model.setFlightId(matched.getId());
            model.setFlightCode(matched.getFlightCode());
            model.setFlightNo(matched.getFlightCode());

            if (isNullOrEmpty(model.getAircraftType()) && !isNullOrEmpty(matched.getAircraftType())) {
                model.setAircraftType(matched.getAircraftType());
            }
        }

        if (binding != null) {
            binding.invalidateAll();
        }
    }






}
