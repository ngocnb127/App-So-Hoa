package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.gesture.GestureOverlayView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
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
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.megatech.fms.databinding.B2508FormBinding;
import com.megatech.fms.databinding.B2508NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2508FormItemFragement extends DialogFragment {

    public B2508FormItemFragement() {
        this.model = new BM2508Model();
        this.model.setTime(new Date());
        this.model.setDateCreated(new Date());
        this.model.setTruckId(FMSApplication.getApplication().getTruckId());
        this.model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
        this.model.setStaffId(FMSApplication.getApplication().getUser().getUserId());
        this.model.setStaffName(FMSApplication.getApplication().getUser().getUserName());
    }

    public B2508FormItemFragement(BM2508Model model) {
        this.model = model;
        this.model.setTextAirlineSignature("Chạm để ký");
        this.model.setTextUserSkypecSignature("Chạm để ký");
        if (model.getFlightCode() == null || model.getFlightCode() == "")
        {
            this.model.setFlightCode(model.getFlightNo()) ;
        }
    }

    public static B2508FormItemFragement newInstance(BM2508Model model) {

        B2508FormItemFragement frag = new B2508FormItemFragement(model);
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
    B2508FormBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.b2508_form, container, false);
        binding.setMItem(this.model);
        return binding.getRoot();
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        Activity activity = getActivity();
        List<BM2508Model.ResultModel> resultModelList = new ArrayList<>();
        resultModelList.add(new BM2508Model.ResultModel("LBs"));
        resultModelList.add(new BM2508Model.ResultModel("KGs"));
        resultModelList.add(new BM2508Model.ResultModel("USG"));
        List<UserModel> userList = null;
        if (activity instanceof B2508Activity){
            B2508Activity b2508Activity = (B2508Activity) activity;
            userList = b2508Activity.userList;
            airports = b2508Activity.airportslist;
            Trucklst = b2508Activity.Trucklist;
            flights = b2508Activity.flightList;
             activityb25 =  b2508Activity;
        }
        // Xử lý ảnh airline signature
        ImageView airlineSignatureImageView = view.findViewById(R.id.ImageAirlineSign);
        if (airlineSignatureImageView != null) {
            // Reset ImageView trước khi load ảnh mới
            airlineSignatureImageView.setImageDrawable(null);
            if (model.getAirlineSignaturePath() != null) {
                Glide.with(view.getContext())
                        .load(model.getAirlineSignaturePath())
                        .into(airlineSignatureImageView);
                airlineSignatureImageView.setVisibility(View.VISIBLE);
            } else {
                Glide.with(view.getContext()).clear(airlineSignatureImageView);
                airlineSignatureImageView.setImageDrawable(null); // Xóa ảnh cũ
                airlineSignatureImageView.setVisibility(View.GONE);
            }
        }

        // Xử lý ảnh user skypec signature
        ImageView skypecSignatureImageView = view.findViewById(R.id.ImageUserSkypecSign);
        if (skypecSignatureImageView != null) {
            // Reset ImageView trước khi load ảnh mới
            skypecSignatureImageView.setImageDrawable(null);
            if (model.getUserSkypecSignaturePath() != null) {
                Glide.with(view.getContext())
                        .load(model.getUserSkypecSignaturePath())
                        .into(skypecSignatureImageView);
                skypecSignatureImageView.setVisibility(View.VISIBLE);
            } else {
                Glide.with(view.getContext()).clear(skypecSignatureImageView);
                skypecSignatureImageView.setImageDrawable(null); // Xóa ảnh cũ
                skypecSignatureImageView.setVisibility(View.GONE);
            }
        }
//        activity = ((B2508Activity) getActivity());
//        List<UserModel> userList = activity.userList;
//        flights = activity.flightList;
//        containers = activity.containerList;

//        Spinner spnairport = view.findViewById(R.id.b2508_new_airport_form);
//        ArrayAdapter<AirportsModel> spnairportAdapter = new ArrayAdapter<AirportsModel>(activity, R.layout.support_simple_spinner_dropdown_item, airports);
//        spnairportAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spnairport.setAdapter(spnairportAdapter);
//        spnairport.setEnabled(false);
//        if (model.getAirportId() > 0) {
//            for (int i = 0; i < airports.size(); i++)
//                if (model.getAirportId().equals(airports.get(i).getId())) {
//                    spnairport.setSelection(i);
//                    break;
//                }
//        }
//        spnairport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                AirportsModel airport = (AirportsModel) adapterView.getItemAtPosition(i);
//                model.setAirportId(airport.getId());
//                model.setAirportName(airport.getName());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });
//
//        Spinner spntruck = view.findViewById(R.id.b2508_new_truck_no_form);
//        ArrayAdapter<TruckModel> spntruckAdapter = new ArrayAdapter<TruckModel>(activity, R.layout.support_simple_spinner_dropdown_item, Trucklst);
//        spntruckAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spntruck.setAdapter(spntruckAdapter);
//        spntruck.setEnabled(false);
//        if (model.getTruckId() > 0) {
//            for (int i = 0; i < Trucklst.size(); i++)
//                if (model.getTruckId().equals(Trucklst.get(i).getId())) {
//                    spntruck.setSelection(i);
//                    break;
//                }
//        }
//        spntruck.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                TruckModel truck = (TruckModel) adapterView.getItemAtPosition(i);
//                model.setTruckId(truck.getId());
//                model.setTruckNo(truck.getTruckNo());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });
//
//        Spinner spngc7 = view.findViewById(R.id.b2508_unit_form);
//        ArrayAdapter<BM2508Model.ResultModel> spngcAdapter = new ArrayAdapter<BM2508Model.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
//        spngcAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spngc7.setAdapter(spngcAdapter);
//        if (model.getUnit() != null) {
//            for (int i = 0; i < resultModelList.size(); i++)
//                if (model.getUnit().equals(resultModelList.get(i).getName())) {
//                    spngc7.setSelection(i);
//                    break;
//                }
//        }
//        spngc7.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                BM2508Model.ResultModel Result = (BM2508Model.ResultModel) adapterView.getItemAtPosition(i);
//                model.setUnit(Result.getName());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });


    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

    }
    private final int Airline = 445;
    private final int UserSkypec = 446;



    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.b2508_new_back_form:

                ImageView airlineSignatureImageView = view.findViewById(R.id.ImageAirlineSign);
                if (airlineSignatureImageView != null) {

                    // Nếu muốn xóa ảnh, thêm dòng sau:
                    airlineSignatureImageView.setImageDrawable(null);
                }
                dlg.dismiss();


                break;
            case R.id.b2508_new_save_form:
                save();
                break;
            case R.id.UserSkypecSign:
            case R.id.AirlineSign:
                activityb25.openSignature(id == R.id.AirlineSign);
                break;
        }
    }

    private void save() {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (activityb25.modelb2508.getUserSkypecSignaturePath() != null){
                    model.setUserSkypecSignaturePath(activityb25.modelb2508.getUserSkypecSignaturePath());
                }
                if (activityb25.modelb2508.getAirlineSignaturePath() != null){
                    model.setAirlineSignaturePath(activityb25.modelb2508.getAirlineSignaturePath());
                }
                DataHelper.postBM2508(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (activityb25 instanceof B2508Activity)
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ImageView airlineSignatureImageView = getView().findViewById(R.id.ImageAirlineSign);
        ImageView skypecSignatureImageView = getView().findViewById(R.id.ImageUserSkypecSign);
        if (airlineSignatureImageView != null) {
            Glide.with(getContext()).clear(airlineSignatureImageView);
            airlineSignatureImageView.setImageDrawable(null);
            airlineSignatureImageView.setVisibility(View.GONE);
        }
        if (skypecSignatureImageView != null) {
            Glide.with(getContext()).clear(skypecSignatureImageView);
            skypecSignatureImageView.setImageDrawable(null);
            skypecSignatureImageView.setVisibility(View.GONE);
        }
    }

}
