package com.megatech.fms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.gson.Gson;
import com.megatech.fms.databinding.B2504NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.*;
import com.megatech.fms.view.FlightArrayAdapter;

import java.io.File;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2504NewItemFragment extends DialogFragment {

    private Dialog dlg;
    private B2504NewBinding binding;
    private BM2504Model model;

    private BM2504Main activity;

    private List<FlightModel> flights = new ArrayList<>();
    private List<AirportsModel> airports = new ArrayList<>();
    private List<TruckModel> trucks = new ArrayList<>();

    private final Locale locale = Locale.getDefault();
    private final NumberFormat numberFormat = NumberFormat.getInstance(locale);

    private final String[] reasonTexts = {
            "Tra nạp/hút nhiên liệu khi hành khách đang lên, xuống hoặc ở trên tàu bay",
            "Trường hợp khác"
    };

    private final int[] reasonValues = {1, 2};



    // =========================
    // CONSTRUCTOR
    // =========================

    public B2504NewItemFragment() {
        model = new BM2504Model();
        model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
        model.setTruckId(FMSApplication.getApplication().getTruckId());
        model.setTruckNo(FMSApplication.getApplication().getTruckNo());
        model.setDate(new Date());
        model.setTime(new Date());
    }

    // =========================
    // LIFECYCLE
    // =========================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        Date now = new Date();

        model = new BM2504Model();
        model.setTruckId(FMSApplication.getApplication().getTruckId());
        model.setTruckNo(FMSApplication.getApplication().getTruckNo());
        model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
        model.setDate(now);
        model.setTime(now);

        if (args != null) {
            if (args.containsKey("BM2504_ID")) {
                model.setId(args.getInt("BM2504_ID", 0));
            }

            if (args.containsKey("AIRPORT_ID")) {
                model.setAirportId(args.getInt("AIRPORT_ID", model.getAirportId()));
            }

            if (args.containsKey("TRUCK_ID")) {
                model.setTruckId(args.getInt("TRUCK_ID", model.getTruckId()));
            }

            String truckNo = args.getString("TRUCK_NO");
            if (truckNo != null && !truckNo.trim().isEmpty()) {
                model.setTruckNo(truckNo);
            }

            if (args.containsKey("FLIGHT_ID")) {
                model.setFlightId(args.getInt("FLIGHT_ID", 0));
            }

            String flightCode = args.getString("FLIGHT_CODE");
            if (flightCode != null && !flightCode.trim().isEmpty()) {
                model.setFlightNo(flightCode);
                if (flightCode.length() >= 2) {
                    model.setAirlineName(flightCode.substring(0, 2));
                }
            }

            String aircraftCode = args.getString("AIRCRAFT_CODE");
            if (aircraftCode != null && !aircraftCode.trim().isEmpty()) {
                model.setAcReg(aircraftCode);
            }

            if (args.containsKey("AIRLINE_ID")) {
                model.setAirlineId(args.getInt("AIRLINE_ID", 0));
            }

            String parkingLot = args.getString("PARKING_LOT");
            if (parkingLot != null && !parkingLot.trim().isEmpty()) {
                if (parkingLot.startsWith("Vị trí:")) {
                    model.setPacking(parkingLot);
                } else {
                    model.setPacking("Vị trí: " + parkingLot);
                }
            }

            String airlineName = args.getString("AIRLINE_NAME");
            if (airlineName != null && !airlineName.trim().isEmpty()) {
                model.setAirlineName(airlineName);
            }

            String chooseNote = args.getString("CHOOSE_NOTE");
            if (chooseNote != null) {
                model.setChooseNote(chooseNote);
            }

            String buyerName = args.getString("BUYER_NAME");
            if (buyerName != null) {
                model.setBuyerName(buyerName);
            }

            String buyerPosition = args.getString("BUYER_POSITION");
            if (buyerPosition != null) {
                model.setBuyerPosition(buyerPosition);
            }

            String signPictureUrl = args.getString("SIGN_PICTURE_URL");
            if (signPictureUrl != null) {
                model.setSignPictureUrl(signPictureUrl);
            }

            long dateMillis = args.getLong("DATE_MILLIS", -1);
            if (dateMillis > 0) {
                Date d = new Date(dateMillis);
                model.setDate(d);
                model.setTime(d);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        dlg = super.onCreateDialog(savedInstanceState);
        return dlg;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = DataBindingUtil.inflate(inflater, R.layout.b2504_new, container, false);
        binding.setMItem(model);
        binding.invalidateAll();
        return binding.getRoot();
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        if (getActivity() instanceof BM2504Main) {
            activity = (BM2504Main) getActivity();

            if (flights == null || flights.isEmpty()) {
                flights = activity.flightList != null ? activity.flightList : new ArrayList<>();
            }
            if (airports == null || airports.isEmpty()) {
                airports = activity.airports != null ? activity.airports : new ArrayList<>();
            }
            if (trucks == null || trucks.isEmpty()) {
                trucks = activity.truckList != null ? activity.truckList : new ArrayList<>();
            }
        }

        if (airports == null) airports = new ArrayList<>();
        if (trucks == null) trucks = new ArrayList<>();
        if (flights == null) flights = new ArrayList<>();

        bindAirportAndTruck(
                view.findViewById(R.id.b2504_airport),
                view.findViewById(R.id.b2504_truck)
        );

        renderSignature();

        view.findViewById(R.id.b2504_new_back).setOnClickListener(v -> dlg.dismiss());
        view.findViewById(R.id.b2504_new_save).setOnClickListener(v -> save());
        view.findViewById(R.id.b2504_time).setOnClickListener(v -> showTimeDialog());

        view.findViewById(R.id.b2504_signature).setOnClickListener(v -> openSignature());
        view.findViewById(R.id.b2504_packing).setOnClickListener(v ->
                showEditDialog("Nhập vị trí", model.getPacking(), text -> {
                    model.setPacking(text);
                    binding.invalidateAll();
                }));

        view.findViewById(R.id.b2504_airline).setOnClickListener(v ->
                showEditDialog("Nhập hãng", model.getAirlineName(), text -> {
                    model.setAirlineName(text);
                    binding.invalidateAll();
                }));

        view.findViewById(R.id.b2504_choose_note).setOnClickListener(v ->
                showEditDialog("Nhập nội dung khác", model.getChooseNote(), text -> {
                    model.setChooseNote(text);
                    binding.invalidateAll();
                }));

        view.findViewById(R.id.b2504_representative).setOnClickListener(v ->
                showEditDialog("Nhập tên người đại diện", model.getBuyerName(), text -> {
                    model.setBuyerName(text);
                    binding.invalidateAll();
                }));

        view.findViewById(R.id.b2504_position).setOnClickListener(v ->
                showEditDialog("Nhập chức vụ", model.getBuyerPosition(), text -> {
                    model.setBuyerPosition(text);
                    binding.invalidateAll();
                }));

        view.findViewById(R.id.b2504_new_flight).setOnClickListener(v -> openFlightSelect());

        Spinner spReason = view.findViewById(R.id.b2504_choose_level);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                reasonTexts
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spReason.setAdapter(adapter);

        if (model.getChooseLevel() != null) {
            for (int i = 0; i < reasonValues.length; i++) {
                if (reasonValues[i] == model.getChooseLevel()) {
                    spReason.setSelection(i, false);
                    break;
                }
            }
        } else {
            spReason.setSelection(0, false);
            model.setChooseLevel(1);
        }

        spReason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.setChooseLevel(reasonValues[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // fallback: chỉ load nền khi chưa có data
        if (airports.isEmpty() || trucks.isEmpty()) {
            loadMasterDataAsync(
                    view.findViewById(R.id.b2504_airport),
                    view.findViewById(R.id.b2504_truck)
            );
        }
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

                    if (trucks == null || trucks.isEmpty()) {
                        List<TruckModel> t = BuildConfig.FHS
                                ? DataHelper.getFHSTrucks()
                                : DataHelper.getTrucks();
                        if (t != null) tempTrucks = t;
                    }
                } catch (Exception e) {
                    Log.e("B2504", "loadMasterDataAsync error", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (!isAdded()) return;

                if ((airports == null || airports.isEmpty()) && tempAirports != null) {
                    airports = tempAirports;
                }
                if ((trucks == null || trucks.isEmpty()) && tempTrucks != null) {
                    trucks = tempTrucks;
                }

                bindAirportAndTruck(spAirport, spTruck);
            }
        }.execute();
    }

    // =========================
    // SAVE
    // =========================

    private void save() {
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... voids) {
                try {
                    DataHelper.postBM2504(model);
                    return null;
                } catch (Exception e) {
                    Log.e("B2504", "save error", e);
                    return e;
                }
            }

            @Override
            protected void onPostExecute(Exception error) {
                if (!isAdded()) return;

                if (error == null) {
                    Toast.makeText(getActivity(), "Lưu thành công", Toast.LENGTH_SHORT).show();
                    if (activity != null) activity.loaddata();
                    dlg.dismiss();
                } else {
                    Toast.makeText(getActivity(), "Lưu thất bại: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    // =========================
    // TIME
    // =========================

    private void showTimeDialog() {
        Calendar c = Calendar.getInstance();
        c.setTime(model.getDate());

        new TimePickerDialog(
                getActivity(),
                (view, hour, minute) -> {
                    c.set(Calendar.HOUR_OF_DAY, hour);
                    c.set(Calendar.MINUTE, minute);
                    model.setDate(c.getTime());
                    binding.invalidateAll();
                },
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                true
        ).show();
    }

    // =========================
    // SIGNATURE
    // =========================

    private static final int SIGN_2504 = 2504;

    private void openSignature() {
        startActivityForResult(
                new Intent(getActivity(), ReceiptSignActivity.class),
                SIGN_2504
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SIGN_2504 && resultCode == Activity.RESULT_OK && data != null) {
            String file = data.getStringExtra("signature_file");
            model.setSignPictureUrl(file);
            renderSignature();
        }
    }

    private void renderSignature() {
        if (model.getSignPictureUrl() == null) return;

        File f = new File(model.getSignPictureUrl());
        if (!f.exists()) return;

        Glide.with(this)
                .load(f)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(binding.b2504SignatureImage);

        binding.b2504SignatureImage.setVisibility(View.VISIBLE);
        binding.b2504Signature.setText("✔ Đã ký");
    }

    // =========================
    // AIRPORT / TRUCK (AUTO)
    // =========================

    private void bindAirportAndTruck(Spinner spAirport, Spinner spTruck) {
        if (!isAdded() || spAirport == null || spTruck == null) return;

        ArrayAdapter<AirportsModel> apAdapter =
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        airports != null ? airports : new ArrayList<>());
        apAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAirport.setAdapter(apAdapter);

        if (airports != null) {
            for (int i = 0; i < airports.size(); i++) {
                if (Objects.equals(airports.get(i).getId(), model.getAirportId())) {
                    spAirport.setSelection(i, false);
                    break;
                }
            }
        }

        spAirport.post(() -> {
            spAirport.setEnabled(false);
            spAirport.setClickable(false);
        });

        ArrayAdapter<TruckModel> trAdapter =
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        trucks != null ? trucks : new ArrayList<>());
        trAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTruck.setAdapter(trAdapter);

        if (trucks != null) {
            for (int i = 0; i < trucks.size(); i++) {
                if (Objects.equals(trucks.get(i).getId(), model.getTruckId())) {
                    spTruck.setSelection(i, false);
                    break;
                }
            }
        }

        spTruck.post(() -> {
            spTruck.setEnabled(false);
            spTruck.setClickable(false);
        });
    }

    private void openFlightSelect() {

        Dialog flightDlg = new Dialog(getActivity());
        flightDlg.setContentView(R.layout.flight_select_dialog);

        SearchView searchView = flightDlg.findViewById(R.id.flight_dlg_search);
        ListView lv = flightDlg.findViewById(R.id.list_airline);

        FlightArrayAdapter adapter =
                new FlightArrayAdapter(getActivity(), flights);

        lv.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        lv.setOnItemClickListener((parent, view, position, id) -> {

            FlightModel f = (FlightModel) parent.getItemAtPosition(position);

            Log.d("B2504_FLIGHT_FULL", new Gson().toJson(f));

            // ===== GÁN CHUYẾN BAY =====
            model.setFlightId(f.getId());
            model.setFlightNo(f.getFlightCode());
            model.setAcReg(f.getAircraftCode());
            model.setAirlineId(f.getAirlineId());
            model.setPacking("Vị trí: "+ f.getParkingLot());


            // ===== HÃNG BAY (tách từ FlightCode) =====
            if (f.getFlightCode() != null && f.getFlightCode().length() >= 2) {
                model.setAirlineName(f.getFlightCode().substring(0, 2));

            }

            // ===== PARKING AUTO =====


            binding.invalidateAll();
            flightDlg.dismiss();
        });

        flightDlg.show();
    }

    private void showEditDialog(String title, String value, OnValueChanged cb) {
        final EditText edt = new EditText(getActivity());
        edt.setText(value);
        edt.setSelection(edt.getText().length());

        new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setView(edt)
                .setPositiveButton("OK", (d, w) -> cb.onChanged(edt.getText().toString()))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    interface OnValueChanged {
        void onChanged(String text);
    }


}
