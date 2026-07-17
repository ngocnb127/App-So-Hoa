package com.megatech.fms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.megatech.fms.databinding.B2503NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2503NewItemFragment extends DialogFragment {

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    private B2503NewBinding binding;
    private BM2503Model model;

    // ==== DATA TỪ ACTIVITY ====
//    private List<FlightModel> flights;
//    private List<AirportsModel> airports;
//    private List<TruckModel> Trucklst;
//    private List<ProductModel> productList;

    private List<FlightModel> flights = new ArrayList<>();
    private List<AirportsModel> airports = new ArrayList<>();
    private List<TruckModel> Trucklst = new ArrayList<>();
    private List<ProductModel> productList = new ArrayList<>();


    private boolean airportUserSelected = false;
    private boolean truckUserSelected = false;



    private BM2503Main activity;

    // =========================
    // CONSTRUCTOR
    // =========================

    public B2503NewItemFragment() {
//        model = new BM2503Model();
//        model.setTruckId(FMSApplication.getApplication().getTruckId());
//        model.setTruckNo(FMSApplication.getApplication().getTruckNo());
//        model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
//        model.setDate(new Date());
//        model.setTime(new Date());
    }

//    public B2503NewItemFragment(BM2503Model model) {
//        this.model = model;
//    }

    // =========================
    // DIALOG
    // =========================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null && getArguments().containsKey("BM2503_MODEL")) {
            // EDIT
            model = (BM2503Model) getArguments().getSerializable("BM2503_MODEL");
        } else {
            // NEW
            model = new BM2503Model();

            Bundle args = getArguments();

            if (args != null) {

                model.setTruckId(args.getInt("TRUCK_ID", 0));
                model.setTruckNo(args.getString("TRUCK_NO", ""));

                model.setAirportId(args.getInt("AIRPORT_ID", 0));
                model.setAirportName(args.getString("AIRPORT_NAME", ""));

                model.setFlightId(args.getInt("FLIGHT_ID", 0));
                model.setFlightNo(args.getString("FLIGHT_CODE", ""));
                model.setAcReg(args.getString("AIRCRAFT_CODE", ""));
                model.setRouter(args.getString("ROUTE_NAME", ""));

            }

            model.setDate(new Date());
            model.setTime(new Date());
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

        binding = DataBindingUtil.inflate(inflater, R.layout.b2503_new, container, false);

        binding.setMItem(model);
        updateProductFromList();
        binding.invalidateAll();
        return binding.getRoot();
    }

    // =========================
    // VIEW CREATED
    // =========================

    @Override
    public void onResume() {
        super.onResume();
        updateProductFromList();
        binding.invalidateAll();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);

        view.findViewById(R.id.b2503_new_back).setOnClickListener(v -> dlg.dismiss());
        view.findViewById(R.id.b2503_new_save).setOnClickListener(v -> save());
        view.findViewById(R.id.b2503_new_flight).setOnClickListener(v -> openFlightSelect());

        setupUnitSpinner(view);
        setupSignature(view);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                flights = DataHelper.getFlights();
                airports = DataHelper.getAirports();
                Trucklst = DataHelper.getTrucks();
                productList = DataHelper.getProducts();
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (airports == null) airports = new ArrayList<>();
                if (Trucklst == null) Trucklst = new ArrayList<>();
                if (flights == null) flights = new ArrayList<>();
                if (productList == null) productList = new ArrayList<>();

                setupSpinners(view);
                ensureDefaultProduct();
                updateProductFromList();
                syncFlightSelectionOnLoad();
                ensureTruckNo();
                ensureAirportName();
                binding.invalidateAll();
            }
        }.execute();
    }
    private void setupSignature(View view) {
        ImageView signImage = view.findViewById(R.id.b2503_signature_image);

        if (model != null && model.getSignPictureUrl() != null && !model.getSignPictureUrl().isEmpty()) {
            File file = new File(model.getSignPictureUrl());

            Glide.with(this)
                    .load(file)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(binding.b2503SignatureImage);

            binding.b2503SignatureImage.setVisibility(View.VISIBLE);
            signImage.setVisibility(View.VISIBLE);
            binding.b2503Signature.setText("✔ Đã ký");
        } else {
            Glide.with(view.getContext()).clear(signImage);
            signImage.setVisibility(View.GONE);
            binding.b2503SignatureImage.setVisibility(View.GONE);
            binding.b2503Signature.setText("Chữ ký");
        }
    }
    private void setupUnitSpinner(View view) {
        Spinner spnUnit = view.findViewById(R.id.b2503_unit);

        ArrayAdapter<String> unitAdapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        UNIT_LABELS
                );
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnUnit.setAdapter(unitAdapter);

        if (model.getUnit() == null || model.getUnit() == 0) {
            model.setUnit(2);
        }

        for (int i = 0; i < UNIT_VALUES.length; i++) {
            if (UNIT_VALUES[i] == model.getUnit()) {
                spnUnit.setSelection(i, false);
                break;
            }
        }

        spnUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.setUnit(UNIT_VALUES[position]);
                binding.invalidateAll();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    private void ensureDefaultProduct() {
        if (model == null) return;
        if (productList == null || productList.isEmpty()) return;

        // nếu chưa có product thì tự chọn id = 1
        if (model.getProductId() == null || model.getProductId() == 0) {
            for (ProductModel p : productList) {
                if (p.getId() != null && p.getId() == 1) {
                    model.setProductId(p.getId());
                    model.setPCode(p.getCode());
                    model.setPName(p.getName());
                    model.setProductName(p.getName());
                    return;
                }
            }

            // fallback: nếu không có id=1 thì lấy phần tử đầu
            ProductModel p = productList.get(0);
            model.setProductId(p.getId());
            model.setPCode(p.getCode());
            model.setPName(p.getName());
            model.setProductName(p.getName());
        }
    }

    private void syncFlightSelectionOnLoad() {
        if (model == null || flights == null) return;

        if (model.getFlightId() != null && model.getFlightId() > 0) {
            for (FlightModel f : flights) {
                if (f.getId() == model.getFlightId()) {
                    model.setFlightNo(f.getFlightCode());
                    model.setAcReg(f.getAircraftCode());
                    model.setRouter(f.getRouteName());
                    break;
                }
            }
        }
    }
    private void setupSpinners(View view) {

        Spinner spnairport = view.findViewById(R.id.b2508_new_airport);
        ArrayAdapter<AirportsModel> adapter =
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        airports);
        spnairport.setAdapter(adapter);

        Spinner spntruck = view.findViewById(R.id.b2508_new_truck_no);
        ArrayAdapter<TruckModel> truckAdapter =
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        Trucklst);

        truckAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spntruck.setAdapter(truckAdapter);

        bindAirportAndTruck(spnairport, spntruck);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (dlg != null && dlg.getWindow() != null) {
            dlg.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }


    @SuppressLint("NonConstantResourceId")
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.b2503_new_back:
                dlg.dismiss();
                break;
            case R.id.b2503_new_save:
                save();
                break;
            case R.id.btnPrint:
                print();
                break;

            case R.id.b2503_time:
                showTimeDialog();
                break;



            case R.id.b2503_buyer:
                    showEditDialog(id, InputType.TYPE_CLASS_TEXT);
                break;

            case R.id.b2503_product:
                openProductSelect();
                break;




            case R.id.b2503_value:
                m_Title = "Số lượng";
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2503_signature:
                openSignature();
                break;
            case R.id.b2503_aircraft:
                m_Title = "Số hiệu tàu bay";
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.b2503_route:
                m_Title = "Đường bay";
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;






        }
    }
    PrintWorker printWorker = null;

    ZebraWorker zebra = null;
    private void print() {

        if (BuildConfig.THERMAL_PRINTER) {
            if (zebra == null) {
                zebra = new ZebraWorker(requireContext());
                zebra.setStateListener(new ZebraWorker.ZebraStateListener() {
                    @Override
                    public void onConnectionError() {
                        activity.showErrorMessage(R.string.printer_error);
                    }

                    @Override
                    public void onError() {

                    }

                    @Override
                    public void onSuccess() {
//                        model.setPrinted(true);
                        binding.invalidateAll();
                        activity.closeProgressDialog();
                    }
                });

            }
            activity.setProgressDialog();
            zebra.print2503(model);
        } else {
            activity.showInfoMessage(R.string.printer_nhiet);
        }
    }
    private void openProductSelect() {
        if (productList == null || productList.isEmpty()) return;

        List<String> names = new ArrayList<>();
        for (ProductModel p : productList) {
            names.add(p.getCode() + " - " + p.getName());
        }

        new AlertDialog.Builder(getActivity())
                .setTitle("Chọn nhiên liệu")
                .setSingleChoiceItems(
                        names.toArray(new String[0]),
                        -1,
                        (dialog, which) -> {
                            ProductModel p = productList.get(which);
                            model.setProductId(p.getId());
                            model.setPCode(p.getCode());
                            model.setPName(p.getName());
                            model.setProductName(p.getName());
                            binding.invalidateAll();
                            dialog.dismiss();
                        })
                .show();
    }


    private void updateProductFromList() {
        if (model.getProductId() != null
                && model.getProductId() > 0
                && productList != null) {

            for (ProductModel product : productList) {
                if (product.getId().equals(model.getProductId())) {
                    model.setPCode(product.getCode());
                    model.setPName(product.getName());
                    model.setProductName(product.getName());
                    break;
                }
            }
        }
    }
    private void setAirportSelection(Spinner spinner) {

        if (model == null || model.getAirportId() == null) return;
        if (airports == null || airports.isEmpty()) return;

        spinner.post(() -> {   // 🔥 BẮT BUỘC
            for (int i = 0; i < airports.size(); i++) {
                AirportsModel a = airports.get(i);
                if (a.getId() != null && a.getId().intValue() == model.getAirportId().intValue()) {
                    spinner.setSelection(i, false); // 🔥 không trigger listener
                    break;
                }
            }
        });
    }



    // =========================
    // FLIGHT SELECT (GIỐNG 2508)
    // =========================

//    private void openFlightSelect() {
//        Dialog dlg = new Dialog(getActivity());
//        dlg.setContentView(R.layout.flight_select_dialog);
//
//        SearchView searchView = dlg.findViewById(R.id.flight_dlg_search);
//        ListView lv = dlg.findViewById(R.id.list_airline);
//
//        FlightArrayAdapter adapter =
//                new FlightArrayAdapter(getActivity(), flights);
//
//        lv.setAdapter(adapter);
//
//        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                adapter.getFilter().filter(query);
//                return false;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                adapter.getFilter().filter(newText);
//                return false;
//            }
//        });
//
//        lv.setOnItemClickListener((parent, view, position, id) -> {
//            FlightModel f = (FlightModel) parent.getItemAtPosition(position);
//            model.setFlightId(f.getId());
//            model.setFlightNo(f.getFlightCode());
//            model.setAcReg(f.getAircraftType());
//            binding.invalidateAll();
//            dlg.dismiss();
//        });
//
//        dlg.show();
//    }

    // =========================
    // SAVE
    // =========================

    private void save() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {


                DataHelper.postBM2503(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                if (activity != null) activity.loaddata();

                Toast.makeText(getContext(), "Lưu thành công", Toast.LENGTH_SHORT).show();

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
        if (flights == null) flights = new ArrayList<>();
        lvAirline.setAdapter(new FlightArrayAdapter(getActivity(), flights));

        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                FlightModel flightModel = (FlightModel) parent.getItemAtPosition(position);
                model.setFlightId(flightModel.getId());
                model.setFlightNo(flightModel.getFlightCode());
                model.setAcReg(flightModel.getAircraftCode());
                model.setRouter(flightModel.getRouteName());
                //model.setRouter(flightModel.get());

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
        date.setTime(model.getDate().getTime());

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
                model.setDate(c.getTime());
                binding.invalidateAll();
            }
        }, mYear, mMonth, mDay);
        datePickerDialog.show();
    }
    private void showTimeDialog() {

        final Calendar c = Calendar.getInstance();
        c.setTime(model.getDate());
        TimePickerDialog datePickerDialog = new TimePickerDialog(this.getActivity(), new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker timePicker, int i, int i1) {
                c.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                c.set(Calendar.MINUTE, timePicker.getMinute());
                model.setDate(c.getTime());
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

        //input.setText(((TextView) dlg.findViewById(id)).getText());
        TextView tv = binding.getRoot().findViewById(id);
        input.setText(tv.getText());

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
                            model.setAcReg(m_Text);
                            break;

                        case R.id.b2503_value:
                            d = numberFormat.parse(m_Text).doubleValue();
                            model.setValue(d);
                            break;
                        case R.id.b2503_buyer:
                            model.setBuyerName(m_Text);
                            break;


                    }
                } catch (NumberFormatException ex) {
                    //activityb25.showErrorMessage(R.string.invalid_number_format);
                    return false;
                } catch (Exception ex) {
                    return false;
                }
                binding.invalidateAll();
                return true;
            }
        });
    }
    public void findViews(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findViews(vg.getChildAt(i));
            }
        } else if (v instanceof TextView) {
            int id = v.getId();
            if (id == R.id.b2503_value
                    || id == R.id.b2503_buyer
                    || id == R.id.b2503_product
                    || id == R.id.b2503_signature
                    || id == R.id.b2503_aircraft
                    || id == R.id.b2503_route
                    || id == R.id.b2503_unit) {
                v.setOnClickListener(this::onClick);
            }
        }

    }

    public void onSignatureDone(String path) {
        model.setSignPictureUrl(path);
        renderSignature();
    }
    private void renderSignature() {
        if (model.getSignPictureUrl() == null) return;

        File file = new File(model.getSignPictureUrl());
        if (!file.exists()) return;

        Glide.with(this)
                .load(file)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(binding.b2503SignatureImage);

        binding.b2503SignatureImage.setVisibility(View.VISIBLE);
        binding.b2503Signature.setText("✔ Đã ký");
    }


    private static final int SIGN_2503 = 2503;
    private void openSignature() {
        Intent intent = new Intent(getActivity(), ReceiptSignActivity.class);
        startActivityForResult(intent, SIGN_2503);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SIGN_2503
                && resultCode == Activity.RESULT_OK
                && data != null
                && model != null) {

            String file = data.getExtras().getString("signature_file");

            if (file != null && !file.isEmpty()) {
                model.setSignPictureUrl(file);
                renderSignature();
                binding.invalidateAll();
            }
        }
    }
    private static final String[] UNIT_LABELS = {
            "Lít",
            "Kg",
            "Gallon"
    };

    private static final int[] UNIT_VALUES = {
            1, // Lít
            2, // Kg
            3  // Gallon
    };

    private void ensureTruckNo() {
        if (model == null) return;
        if (model.getTruckId() == null) return;

        // đã có truckNo thì không đụng
        if (model.getTruckNo() != null && !model.getTruckNo().isEmpty())
            return;

        if (Trucklst == null || Trucklst.isEmpty()) return;

        for (TruckModel t : Trucklst) {
            if (t.getId() != null && t.getId().intValue() == model.getTruckId().intValue()) {
                model.setTruckNo(t.getCode());   // hoặc getTruckNo()
                break;
            }
        }
    }
    private void ensureAirportName() {
        if (model == null) return;
        if (model.getAirportId() == null) return;

        // đã có airportName thì không đụng
        if (model.getAirportName() != null && !model.getAirportName().isEmpty())
            return;

        if (airports == null || airports.isEmpty()) return;

        for (AirportsModel a : airports) {
            if (a.getId() != null
                    && a.getId().intValue() == model.getAirportId().intValue()) {

                // tuỳ AirportsModel của bạn
                // thường là Code hoặc Name
                model.setAirportName(a.getName());
                // hoặc: a.getCode()

                break;
            }
        }
    }
    private void bindAirportAndTruck(Spinner spnairport, Spinner spntruck) {

        if (model == null) return;

        // ===== AIRPORT =====
        if (airports != null && !airports.isEmpty() && model.getAirportId() != null) {
            for (int i = 0; i < airports.size(); i++) {
                AirportsModel a = airports.get(i);
                if (a.getId() != null && a.getId().intValue() == model.getAirportId().intValue()) {
                    spnairport.setSelection(i, false);
                    break;
                }
            }
        }

        ensureAirportName();
        spnairport.setEnabled(false);

        // ===== TRUCK =====
        if (Trucklst != null && !Trucklst.isEmpty() && model.getTruckId() != null) {
            for (int i = 0; i < Trucklst.size(); i++) {
                TruckModel t = Trucklst.get(i);
                if (t.getId() != null && t.getId().intValue() == model.getTruckId().intValue()) {
                    spntruck.setSelection(i, false);
                    break;
                }
            }
        }

        ensureTruckNo();
        spntruck.setEnabled(false);
    }













}
