package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.http.SslCertificate;
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

import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.databinding.A2307NewBinding;
import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

public class A2307NewItemFragement extends DialogFragment {

    public A2307NewItemFragement() {
        this.model = new CheckTrucksModel();
        //this.model.OperatorId = 0;
        this.model.UserCreatedId = 0;
//        this.model.UserActivedId = 0;
//        this.model.UserActived2Id = 0;
//        this.model.UserActived3Id = 0;
        this.model.AirportId = 0;
        this.model.TruckId = 0;
        this.model.ShiftId = 0;
        this.model.setDateCreated(new Date());
        this.model.setHours(new Date());
        this.model.setUserCreatedId(FMSApplication.getApplication().getUser().getUserId());
        this.model.setUserCreatedName(FMSApplication.getApplication().getUser().getUserName());
       // this.model.setOperatorId(FMSApplication.getApplication().getUser().getUserId());
        this.model.setTruckId(FMSApplication.getApplication().getTruckId());
        this.model.setAirportId(FMSApplication.getApplication().getUser().getAirportId());
        this.model.HasNote2 = true;
        this.model.HasNote7 = true;
        this.model.HasNote8 = true;
        this.model.HasNote17 = true;
        this.model.HasNote23 = true;
        this.model.HasNote28 = true;
        this.model.HasNote32 = true;
        this.model.HasNote37 = true;
        this.model.HasNote45 = true;
        this.model.HasNote4 = true;


        this.model.setResult1("Đ");
        this.model.setResult2("Đ");
        this.model.setResult3("Đ");
        this.model.setResult4("Đ");
        this.model.setResult5("Đ");
        this.model.setResult6("Đ");
        this.model.setResult7("Đ");
        this.model.setResult8("Đ");
        this.model.setResult9("Đ");
        this.model.setResult10("Đ");
        this.model.setResult11("Đ");
        this.model.setResult12("Đ");
        this.model.setResult13("Đ");
        this.model.setResult14("Đ");
        this.model.setResult15("Đ");
        this.model.setResult16("Đ");
        this.model.setResult17("Đ");
        this.model.setResult18("Đ");
        this.model.setResult19("Đ");
        this.model.setResult20("Đ");
        this.model.setResult21("Đ");
        this.model.setResult22("Đ");
        this.model.setResult23("Đ");
        this.model.setResult24("Đ");
        this.model.setResult25("Đ");
        this.model.setResult26("Đ");
        this.model.setResult27("Đ");
        this.model.setResult28("Đ");
        this.model.setResult29("Đ");
        this.model.setResult30("Đ");
        this.model.setResult31("Đ");
        this.model.setResult32("Đ");
        this.model.setResult33("Đ");
        this.model.setResult34("Đ");
        this.model.setResult35("Đ");
        this.model.setResult36("Đ");
        this.model.setResult37("Đ");
        this.model.setResult38("Đ");
        this.model.setResult39("Đ");
        this.model.setResult40("Đ");
        this.model.setResult41("Đ");
        this.model.setResult42("Đ");
        this.model.setResult44("Đ");
        this.model.setResult45("Đ");
        this.model.setResult46("Đ");
        this.model.setResult47("Đ");
        this.model.setResult48("Đ");
        this.model.setResult49("Đ");
        this.model.setResult50("Đ");

        this.model.Note7 ="Không móp méo,nứt vỡ,bất thường";
        this.model.Note8 ="Bằng mắt lốp không xẹp,phồng rộp,nứt, mòn quá thời hạn";
        this.model.Note1 ="Kẹp không bị hư hỏng, dây không bị đứt";
        this.model.Note2 ="Không bị đứt,mờ số niêm";
        this.model.Note32 ="Kiểm tra lần lượt quay vòng các vị trí interlock trên xe tra nạp";
        this.model.Note44 ="Tình trạng dây đai an toàn,các bản lề,chốt khóa...tra mỡ nếu cần";
        this.model.Note17 ="Không bị mòn quá giới hạn và không có hư hỏng và bị rò chảy";
        this.model.Note4 ="Vòng đai không bị mòn quá 4cm và không có các hư hỏng bất thường";
        this.model.Note3 ="Kẹp không bị hư hỏng, dây không bị đứt";
        this.model.Note45 ="Có đủ";
        this.model.Note23 ="Có đủ dụng cụ hóa nghiệm";
        this.model.Note21 ="Vệ sinh sạch sẽ";
        this.model.Note50 ="Không có nước và cặn bẩn";
        this.model.Note12 ="Nằm trong mức quy định";
        this.model.Note13 ="Xả sạch nước";
        this.model.Note14 ="Nằm trong mức quy định";
        this.model.Note15 ="Nằm trong mức quy định";
        this.model.Note16 ="Nằm trong mức quy định";
        this.model.Note19 ="Nằm trong mức quy định";
        this.model.Note18 ="Không có hư hỏng bất thường,vị trí kết nối đảm bảo";
        this.model.Note5 ="C&B";
        this.model.Note6 ="C&B";
        this.model.Note20 ="Xả sạch nước";
        this.model.Note9 ="Không có hư hỏng bất thường";
        this.model.Note10 ="Không bị rò rỉ,có nắp đậy,niên chì";
        this.model.Note41 ="Đảm bảo tiếp xúc tốt với thân xe và mặt đất";
        this.model.Note11 ="Có 03 bình cứu hỏa,chỉ vạch xanh";
        this.model.Note40 ="Có đủ";
        this.model.Note46 ="Có thể hiện số trên màn hình LCR";
        this.model.Note47 ="Lên nguồn và sử dụng bình thường";
        this.model.Note48 ="In được hóa đơn bình thường";
        this.model.Note49 ="Sử dụng máy tính bảng kết nối được với LCR, Kết nối được với máy in, Kết nối được ineternet";
        this.model.Note25 ="Xem có bất thường không,nhiệt độ nước làm mát,áp suất dầu bôi trơn";
        this.model.Note26 ="Không bị rạn nứt,hoạt động bình thường";
        this.model.Note27 ="Không có hư hỏng bất thường";
        this.model.Note34 ="▲P =";
        this.model.Note35 ="Bộ đếm  hoạt động bình thường, có đủ niêm phong chì";
        this.model.Note36 ="Bơm hoạt động bình thường không bị dò rỉ";
        this.model.Note43 = "Xe hoạt động bình thường";

    }

    public A2307NewItemFragement(CheckTrucksModel model) {
        this.model = model;
    }

    public static A2307NewItemFragement newInstance(CheckTrucksModel model) {

        A2307NewItemFragement frag = new A2307NewItemFragement(model);
        Bundle args = new Bundle();

        frag.setArguments(args);
        return frag;
    }

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    CheckTrucksModel model;
    private List<AirportsModel> airports;
    private List<TruckModel> Trucklst;
    private List<ShiftModel> Shiftlst;
    private List<BM2505ContainerModel> containers;
    private A2307Activity activitya2307;

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
    A2307NewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.a2307_new, container, false);
        binding.setMItem(this.model);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        Activity activity = getActivity();
        List<UserModel> userList = null;
        List<CheckTrucksModel.ResultModel> resultModelList = new ArrayList<>();
        resultModelList.add(new CheckTrucksModel.ResultModel("Đ"));
        resultModelList.add(new CheckTrucksModel.ResultModel("YC"));
        resultModelList.add(new CheckTrucksModel.ResultModel("B"));
        resultModelList.add(new CheckTrucksModel.ResultModel("NA"));
        if(activity instanceof A2307Activity){
            A2307Activity a2307Activity = (A2307Activity) activity;
            userList = a2307Activity.userList;
            airports = a2307Activity.airportslist;
            Trucklst = a2307Activity.Trucklist;
            Shiftlst = a2307Activity.Shiftlist;
            activitya2307 =  a2307Activity;
        }
//        Spinner spn = view.findViewById(R.id.a2307_new_operator);
//        ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<UserModel>(activity, R.layout.support_simple_spinner_dropdown_item, userList);
//        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spn.setAdapter(spinnerAdapter);
//        if (model.getOperatorId() > 0) {
//            for (int i = 0; i < userList.size(); i++)
//                if (model.getOperatorId() == userList.get(i).getId()) {
//                    spn.setSelection(i);
//                    break;
//                }
//        }
//        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                UserModel user = (UserModel) adapterView.getItemAtPosition(i);
//                model.setOperatorId(user.getId());
//                model.setOperatorName(user.getName());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });

        Spinner spnairport = view.findViewById(R.id.a2307_new_airport);
        ArrayAdapter<AirportsModel> spnairportAdapter = new ArrayAdapter<AirportsModel>(activity, R.layout.support_simple_spinner_dropdown_item, airports);
        spnairportAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spnairport.setAdapter(spnairportAdapter);
        spnairport.setEnabled(false);
        if (model.getAirportId() > 0) {
            for (int i = 0; i < airports.size(); i++)
                if (model.getAirportId().equals(airports.get(i).getId())) {
                    spnairport.setSelection(i);
                    break;
                }
        }
        spnairport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                AirportsModel airport = (AirportsModel) adapterView.getItemAtPosition(i);
                model.setAirportId(airport.getId());
                 model.setAirportName(airport.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        Spinner spntruck = view.findViewById(R.id.a2307_new_truck_no);
        ArrayAdapter<TruckModel> spntruckAdapter = new ArrayAdapter<TruckModel>(activity, R.layout.support_simple_spinner_dropdown_item, Trucklst);
        spntruckAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spntruck.setAdapter(spntruckAdapter);
        spntruck.setEnabled(false);
        if (model.getTruckId() > 0) {
            for (int i = 0; i < Trucklst.size(); i++)
                if (model.getTruckId().equals(Trucklst.get(i).getId())) {
                    spntruck.setSelection(i);
                    break;
                }
        }
        spntruck.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                TruckModel truck = (TruckModel) adapterView.getItemAtPosition(i);
                model.setTruckId(truck.getId());
                 model.setTruckNo(truck.getTruckNo());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        Spinner spnshift = view.findViewById(R.id.a2307_new_shift);
        ArrayAdapter<ShiftModel> spnshiftAdapter = new ArrayAdapter<ShiftModel>(activity, R.layout.support_simple_spinner_dropdown_item, Shiftlst);
        spnshiftAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spnshift.setAdapter(spnshiftAdapter);
        if (model.getShiftId() > 0) {
            for (int i = 0; i < Shiftlst.size(); i++)
                if (model.getShiftId().equals(Shiftlst.get(i).getId())) {
                    spnshift.setSelection(i);
                    break;
                }
        }
        spnshift.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                ShiftModel shift = (ShiftModel) adapterView.getItemAtPosition(i);
                model.setShiftId(shift.getId());
                 model.setShiftName(shift.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });



        Spinner spngc7 = view.findViewById(R.id.a2307_new_Result7);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngcAdapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngcAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc7.setAdapter(spngcAdapter);
        if (model.getResult7() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult7().equals(resultModelList.get(i).getName())) {
                    spngc7.setSelection(i);
                    break;
                }
        }
        spngc7.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult7(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc8 = view.findViewById(R.id.a2307_new_Result8);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc8Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc8Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc8.setAdapter(spngc8Adapter);
        if (model.getResult8() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult8().equals(resultModelList.get(i).getName())) {
                    spngc8.setSelection(i);
                    break;
                }
        }
        spngc8.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult8(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc1 = view.findViewById(R.id.a2307_new_Result1);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc1Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc1Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc1.setAdapter(spngc1Adapter);
        if (model.getResult1() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult1().equals(resultModelList.get(i).getName())) {
                    spngc1.setSelection(i);
                    break;
                }
        }
        spngc1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult1(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc2 = view.findViewById(R.id.a2307_new_Result2);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc2Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc2Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc2.setAdapter(spngc2Adapter);
        if (model.getResult2() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult2().equals(resultModelList.get(i).getName())) {
                    spngc2.setSelection(i);
                    break;
                }
        }
        spngc2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult2(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc32 = view.findViewById(R.id.a2307_new_Result32);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc32Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc32Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc32.setAdapter(spngc32Adapter);
        if (model.getResult32() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult32().equals(resultModelList.get(i).getName())) {
                    spngc32.setSelection(i);
                    break;
                }
        }
        spngc32.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult32(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc44 = view.findViewById(R.id.a2307_new_Result44);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc44Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc44Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc44.setAdapter(spngc44Adapter);
        if (model.getResult44() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult44().equals(resultModelList.get(i).getName())) {
                    spngc44.setSelection(i);
                    break;
                }
        }
        spngc44.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult44(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc28 = view.findViewById(R.id.a2307_new_Result28);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc28Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc28Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc28.setAdapter(spngc28Adapter);
        if (model.getResult28() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult28().equals(resultModelList.get(i).getName())) {
                    spngc28.setSelection(i);
                    break;
                }
        }
        spngc28.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult28(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc17 = view.findViewById(R.id.a2307_new_Result17);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc17Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc17Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc17.setAdapter(spngc17Adapter);
        if (model.getResult17() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult17().equals(resultModelList.get(i).getName())) {
                    spngc17.setSelection(i);
                    break;
                }
        }
        spngc17.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult17(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc4 = view.findViewById(R.id.a2307_new_Result4);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc4Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc4Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc4.setAdapter(spngc4Adapter);
        if (model.getResult4() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult4().equals(resultModelList.get(i).getName())) {
                    spngc4.setSelection(i);
                    break;
                }
        }
        spngc4.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult4(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc3 = view.findViewById(R.id.a2307_new_Result3);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc3Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc3Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc3.setAdapter(spngc3Adapter);
        if (model.getResult3() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult3().equals(resultModelList.get(i).getName())) {
                    spngc3.setSelection(i);
                    break;
                }
        }
        spngc3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult3(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc45 = view.findViewById(R.id.a2307_new_Result45);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc45Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc45Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc45.setAdapter(spngc45Adapter);
        if (model.getResult45() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult45().equals(resultModelList.get(i).getName())) {
                    spngc45.setSelection(i);
                    break;
                }
        }
        spngc45.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult45(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc37 = view.findViewById(R.id.a2307_new_Result37);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc37Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc37Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc37.setAdapter(spngc37Adapter);
        if (model.getResult37() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult37().equals(resultModelList.get(i).getName())) {
                    spngc37.setSelection(i);
                    break;
                }
        }
        spngc37.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult37(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc23 = view.findViewById(R.id.a2307_new_Result23);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc23Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc23Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc23.setAdapter(spngc23Adapter);
        if (model.getResult23() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult23().equals(resultModelList.get(i).getName())) {
                    spngc23.setSelection(i);
                    break;
                }
        }
        spngc23.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult23(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc21 = view.findViewById(R.id.a2307_new_Result21);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc21Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc21Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc21.setAdapter(spngc21Adapter);
        if (model.getResult21() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult21().equals(resultModelList.get(i).getName())) {
                    spngc21.setSelection(i);
                    break;
                }
        }
        spngc21.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult21(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc50 = view.findViewById(R.id.a2307_new_Result50);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc50Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc50Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc50.setAdapter(spngc50Adapter);
        if (model.getResult50() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult50().equals(resultModelList.get(i).getName())) {
                    spngc50.setSelection(i);
                    break;
                }
        }
        spngc50.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult50(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc12 = view.findViewById(R.id.a2307_new_Result12);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc12Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc12Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc12.setAdapter(spngc12Adapter);
        if (model.getResult12() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult12().equals(resultModelList.get(i).getName())) {
                    spngc12.setSelection(i);
                    break;
                }
        }
        spngc12.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult12(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc13 = view.findViewById(R.id.a2307_new_Result13);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc13Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc13Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc13.setAdapter(spngc13Adapter);
        if (model.getResult13() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult13().equals(resultModelList.get(i).getName())) {
                    spngc13.setSelection(i);
                    break;
                }
        }
        spngc13.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult13(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc14 = view.findViewById(R.id.a2307_new_Result14);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc14Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc14Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc14.setAdapter(spngc14Adapter);
        if (model.getResult14() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult14().equals(resultModelList.get(i).getName())) {
                    spngc14.setSelection(i);
                    break;
                }
        }
        spngc14.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult14(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc15 = view.findViewById(R.id.a2307_new_Result15);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc15Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc15Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc15.setAdapter(spngc15Adapter);
        if (model.getResult15() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult15().equals(resultModelList.get(i).getName())) {
                    spngc15.setSelection(i);
                    break;
                }
        }
        spngc15.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult15(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc16 = view.findViewById(R.id.a2307_new_Result16);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc16Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc16Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc16.setAdapter(spngc16Adapter);
        if (model.getResult16() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult16().equals(resultModelList.get(i).getName())) {
                    spngc16.setSelection(i);
                    break;
                }
        }
        spngc16.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult16(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc19 = view.findViewById(R.id.a2307_new_Result19);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc19Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc19Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc19.setAdapter(spngc19Adapter);
        if (model.getResult19() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult19().equals(resultModelList.get(i).getName())) {
                    spngc19.setSelection(i);
                    break;
                }
        }
        spngc19.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult19(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc18 = view.findViewById(R.id.a2307_new_Result18);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc18Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc18Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc18.setAdapter(spngc18Adapter);
        if (model.getResult18() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult18().equals(resultModelList.get(i).getName())) {
                    spngc18.setSelection(i);
                    break;
                }
        }
        spngc18.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult18(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc5 = view.findViewById(R.id.a2307_new_Result5);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc5Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc5Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc5.setAdapter(spngc5Adapter);
        if (model.getResult5() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult5().equals(resultModelList.get(i).getName())) {
                    spngc5.setSelection(i);
                    break;
                }
        }
        spngc5.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult5(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc6= view.findViewById(R.id.a2307_new_Result6);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc6Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc6Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc6.setAdapter(spngc6Adapter);
        if (model.getResult6() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult6().equals(resultModelList.get(i).getName())) {
                    spngc6.setSelection(i);
                    break;
                }
        }
        spngc6.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult6(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc20 = view.findViewById(R.id.a2307_new_Result20);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc20Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc20Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc20.setAdapter(spngc20Adapter);
        if (model.getResult20() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult20().equals(resultModelList.get(i).getName())) {
                    spngc20.setSelection(i);
                    break;
                }
        }
        spngc20.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult20(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc9 = view.findViewById(R.id.a2307_new_Result9);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc9Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc9Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc9.setAdapter(spngc9Adapter);
        if (model.getResult9() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult9().equals(resultModelList.get(i).getName())) {
                    spngc9.setSelection(i);
                    break;
                }
        }
        spngc9.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult9(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc10 = view.findViewById(R.id.a2307_new_Result10);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc10Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc10Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc10.setAdapter(spngc10Adapter);
        if (model.getResult10() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult10().equals(resultModelList.get(i).getName())) {
                    spngc10.setSelection(i);
                    break;
                }
        }
        spngc10.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult10(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc41 = view.findViewById(R.id.a2307_new_Result41);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc41Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc41Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc41.setAdapter(spngc41Adapter);
        if (model.getResult41() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult41().equals(resultModelList.get(i).getName())) {
                    spngc41.setSelection(i);
                    break;
                }
        }
        spngc41.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult41(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc11 = view.findViewById(R.id.a2307_new_Result11);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc11Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc11Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc11.setAdapter(spngc11Adapter);
        if (model.getResult11() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult11().equals(resultModelList.get(i).getName())) {
                    spngc11.setSelection(i);
                    break;
                }
        }
        spngc11.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult11(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc40 = view.findViewById(R.id.a2307_new_Result40);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc40Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc40Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc40.setAdapter(spngc40Adapter);
        if (model.getResult40() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult40().equals(resultModelList.get(i).getName())) {
                    spngc40.setSelection(i);
                    break;
                }
        }
        spngc40.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult40(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc46 = view.findViewById(R.id.a2307_new_Result46);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc46Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc46Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc46.setAdapter(spngc46Adapter);
        if (model.getResult46() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult46().equals(resultModelList.get(i).getName())) {
                    spngc46.setSelection(i);
                    break;
                }
        }
        spngc46.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult46(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc47 = view.findViewById(R.id.a2307_new_Result47);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc47Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc47Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc47.setAdapter(spngc47Adapter);
        if (model.getResult47() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult47().equals(resultModelList.get(i).getName())) {
                    spngc47.setSelection(i);
                    break;
                }
        }
        spngc47.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult47(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc48 = view.findViewById(R.id.a2307_new_Result48);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc48Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc48Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc48.setAdapter(spngc48Adapter);
        if (model.getResult48() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult48().equals(resultModelList.get(i).getName())) {
                    spngc48.setSelection(i);
                    break;
                }
        }
        spngc48.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult48(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc49 = view.findViewById(R.id.a2307_new_Result49);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc49Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc49Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc49.setAdapter(spngc49Adapter);
        if (model.getResult49() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult49().equals(resultModelList.get(i).getName())) {
                    spngc49.setSelection(i);
                    break;
                }
        }
        spngc49.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult49(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc25 = view.findViewById(R.id.a2307_new_Result25);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc25Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc25Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc25.setAdapter(spngc25Adapter);
        if (model.getResult25() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult25().equals(resultModelList.get(i).getName())) {
                    spngc25.setSelection(i);
                    break;
                }
        }
        spngc25.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult25(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc26 = view.findViewById(R.id.a2307_new_Result26);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc26Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc26Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc26.setAdapter(spngc26Adapter);
        if (model.getResult26() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult26().equals(resultModelList.get(i).getName())) {
                    spngc26.setSelection(i);
                    break;
                }
        }
        spngc26.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult26(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc27 = view.findViewById(R.id.a2307_new_Result27);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc27Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc27Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc27.setAdapter(spngc27Adapter);
        if (model.getResult27() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult27().equals(resultModelList.get(i).getName())) {
                    spngc27.setSelection(i);
                    break;
                }
        }
        spngc27.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult27(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc29 = view.findViewById(R.id.a2307_new_Result29);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc29Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc29Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc29.setAdapter(spngc29Adapter);
        if (model.getResult29() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult29().equals(resultModelList.get(i).getName())) {
                    spngc29.setSelection(i);
                    break;
                }
        }
        spngc29.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult29(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc30 = view.findViewById(R.id.a2307_new_Result30);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc30Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc30Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc30.setAdapter(spngc30Adapter);
        if (model.getResult30() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult30().equals(resultModelList.get(i).getName())) {
                    spngc30.setSelection(i);
                    break;
                }
        }
        spngc30.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult30(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc31 = view.findViewById(R.id.a2307_new_Result31);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc31Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc31Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc31.setAdapter(spngc31Adapter);
        if (model.getResult31() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult31().equals(resultModelList.get(i).getName())) {
                    spngc31.setSelection(i);
                    break;
                }
        }
        spngc31.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult31(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc33 = view.findViewById(R.id.a2307_new_Result33);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc33Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc33Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc33.setAdapter(spngc33Adapter);
        if (model.getResult33() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult33().equals(resultModelList.get(i).getName())) {
                    spngc33.setSelection(i);
                    break;
                }
        }
        spngc33.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult33(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spngc34 = view.findViewById(R.id.a2307_new_Result34);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc34Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc34Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc34.setAdapter(spngc34Adapter);
        if (model.getResult34() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult34().equals(resultModelList.get(i).getName())) {
                    spngc34.setSelection(i);
                    break;
                }
        }
        spngc34.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult34(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        Spinner spngc35 = view.findViewById(R.id.a2307_new_Result35);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc35Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc35Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc35.setAdapter(spngc35Adapter);
        if (model.getResult35() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult35().equals(resultModelList.get(i).getName())) {
                    spngc35.setSelection(i);
                    break;
                }
        }
        spngc35.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult35(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        Spinner spngc36 = view.findViewById(R.id.a2307_new_Result36);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc36Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc36Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc36.setAdapter(spngc36Adapter);
        if (model.getResult36() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult36().equals(resultModelList.get(i).getName())) {
                    spngc36.setSelection(i);
                    break;
                }
        }
        spngc36.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult36(Result.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        Spinner spngc42 = view.findViewById(R.id.a2307_new_Result42);
        ArrayAdapter<CheckTrucksModel.ResultModel> spngc42Adapter = new ArrayAdapter<CheckTrucksModel.ResultModel>(activity, R.layout.support_simple_spinner_dropdown_item, resultModelList);
        spngc42Adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spngc42.setAdapter(spngc42Adapter);
        if (model.getResult42() != null) {
            for (int i = 0; i < resultModelList.size(); i++)
                if (model.getResult42().equals(resultModelList.get(i).getName())) {
                    spngc42.setSelection(i);
                    break;
                }
        }
        spngc42.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                CheckTrucksModel.ResultModel Result = (CheckTrucksModel.ResultModel) adapterView.getItemAtPosition(i);
                model.setResult42(Result.getName());
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
//            case R.id.a2307rad_flight:
//                model.setReportType(0);
//                binding.invalidateAll();
//                break;

//            case R.id.a2307rad_other:
//                model.setReportType(1);
//                binding.invalidateAll();
//                break;

            case R.id.a2307_new_back:
                dlg.dismiss();
                break;

            case R.id.a2307_new_back2:
                dlg.dismiss();
                break;

            case R.id.a2307_new_save:
                save();
                break;

            case R.id.a2307_new_save2:
                save();
                break;

            case R.id.a2307_new_time:
                showTimeDialog();
                break;

            case R.id.a2307_new_DateCreated:
                showTimeDayDialog();
                break;

//            case R.id.a2307_new_depot:
//                m_Title = getString(R.string.update_depot);
//                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
//                break;

            case R.id.a2307_new_km_number:
                m_Title = getString(R.string.hour_km);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note7:
                m_Title = getString(R.string.Result7);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note8:
                m_Title = getString(R.string.Result8);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note1:
                m_Title = getString(R.string.Result1);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note2:
                m_Title = getString(R.string.Result2);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note32:
                m_Title = getString(R.string.Result32);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note44:
                m_Title = getString(R.string.Result44);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note28:
                m_Title = getString(R.string.Result28);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note17:
                m_Title = getString(R.string.Result17);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note4:
                m_Title = getString(R.string.Result4);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note3:
                m_Title = getString(R.string.Result3);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note45:
                m_Title = getString(R.string.Result45);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note37:
                m_Title = getString(R.string.Result37);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note23:
                m_Title = getString(R.string.Result23);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note21:
                m_Title = getString(R.string.Result21);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note50:
                m_Title = getString(R.string.Result50);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note12:
                m_Title = getString(R.string.Result12);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note13:
                m_Title = getString(R.string.Result13);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note14:
                m_Title = getString(R.string.Result14);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note15:
                m_Title = getString(R.string.Result15);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note16:
                m_Title = getString(R.string.Result16);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note19:
                m_Title = getString(R.string.Result19);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note18:
                m_Title = getString(R.string.Result18);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note5:
                m_Title = getString(R.string.Result5);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note6:
                m_Title = getString(R.string.Result6);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note20:
                m_Title = getString(R.string.Result20);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note9:
                m_Title = getString(R.string.Result9);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note10:
                m_Title = getString(R.string.Result10);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note41:
                m_Title = getString(R.string.Result41);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note11:
                m_Title = getString(R.string.Result11);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note40:
                m_Title = getString(R.string.Result40);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note46:
                m_Title = getString(R.string.Result46);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note47:
                m_Title = getString(R.string.Result47);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note48:
                m_Title = getString(R.string.Result48);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note49:
                m_Title = getString(R.string.Result49);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note25:
                m_Title = getString(R.string.Result25);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note26:
                m_Title = getString(R.string.Result26);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note27:
                m_Title = getString(R.string.Result27);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note29:
                m_Title = getString(R.string.Result29);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note30:
                m_Title = getString(R.string.Result30);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note31:
                m_Title = getString(R.string.Result31);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note33:
                m_Title = getString(R.string.Result33);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note34:
                m_Title = getString(R.string.Result34);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note35:
                m_Title = getString(R.string.Result35);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note36:
                m_Title = getString(R.string.Result36);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_Note42:
                m_Title = getString(R.string.Result42);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;

            case R.id.a2307_new_note:
                m_Title = getString(R.string.update_note);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
//            case R.id.a2307_new_max_flowrate:
//                m_Title = getString( R.string.update_max_flowrate);
//                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
//                break;
        }
    }

    private void save() {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.postCheckTrucks(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (activitya2307 instanceof A2307Activity)
                {
                    activitya2307.loaddata();
                }
                dlg.dismiss();
            }


        }.execute();


    }

    private void showTimeDialog() {

        final Calendar c = Calendar.getInstance();
        c.setTime(model.getHours());
        TimePickerDialog datePickerDialog = new TimePickerDialog(this.getActivity(), new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker timePicker, int i, int i1) {
                c.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                c.set(Calendar.MINUTE, timePicker.getMinute());
                model.setHours(c.getTime());
                binding.invalidateAll();
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false);
        datePickerDialog.show();
    }
    private final Context context = this.getActivity();
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

//                        case R.id.a2307_new_time:
//                            model.setHours(m_Text);
//                            break;
                        case R.id.a2307_new_km_number:
                            model.setKmNumber(m_Text);
                            break;
                        case R.id.a2307_new_Note1:
                            model.setNote1(m_Text);
                            break;

                        case R.id.a2307_new_Note2:
                            model.setNote2(m_Text);
                            break;

                        case R.id.a2307_new_Note3:
                            model.setNote3(m_Text);
                            break;

                        case R.id.a2307_new_Note4:
                            model.setNote4(m_Text);
                            break;

                        case R.id.a2307_new_Note5:
                            model.setNote5(m_Text);
                            break;

                        case R.id.a2307_new_Note6:
                            model.setNote6(m_Text);
                            break;

                        case R.id.a2307_new_Note7:
                            model.setNote7(m_Text);
                            break;

                        case R.id.a2307_new_Note8:
                            model.setNote8(m_Text);
                            break;

                        case R.id.a2307_new_Note9:
                            model.setNote9(m_Text);
                            break;

                        case R.id.a2307_new_Note10:
                            model.setNote10(m_Text);
                            break;

                        case R.id.a2307_new_Note11:
                            model.setNote11(m_Text);
                            break;

                        case R.id.a2307_new_Note12:
                            model.setNote12(m_Text);
                            break;

                        case R.id.a2307_new_Note13:
                            model.setNote13(m_Text);
                            break;

                        case R.id.a2307_new_Note14:
                            model.setNote14(m_Text);
                            break;

                        case R.id.a2307_new_Note15:
                            model.setNote15(m_Text);
                            break;

                        case R.id.a2307_new_Note16:
                            model.setNote16(m_Text);
                            break;

                        case R.id.a2307_new_Note17:
                            model.setNote17(m_Text);
                            break;

                        case R.id.a2307_new_Note18:
                            model.setNote18(m_Text);
                            break;

                        case R.id.a2307_new_Note19:
                            model.setNote19(m_Text);
                            break;

                        case R.id.a2307_new_Note20:
                            model.setNote20(m_Text);
                            break;

                        case R.id.a2307_new_Note21:
                            model.setNote21(m_Text);
                            break;

                        case R.id.a2307_new_Note23:
                            model.setNote23(m_Text);
                            break;

                        case R.id.a2307_new_Note25:
                            model.setNote25(m_Text);
                            break;

                        case R.id.a2307_new_Note26:
                            model.setNote26(m_Text);
                            break;

                        case R.id.a2307_new_Note27:
                            model.setNote27(m_Text);
                            break;

                        case R.id.a2307_new_Note28:
                            model.setNote28(m_Text);
                            break;

                        case R.id.a2307_new_Note29:
                            model.setNote29(m_Text);
                            break;

                        case R.id.a2307_new_Note30:
                            model.setNote30(m_Text);
                            break;

                        case R.id.a2307_new_Note31:
                            model.setNote31(m_Text);
                            break;

                        case R.id.a2307_new_Note32:
                            model.setNote32(m_Text);
                            break;

                        case R.id.a2307_new_Note33:
                            model.setNote33(m_Text);
                            break;

                        case R.id.a2307_new_Note34:
                            model.setNote34(m_Text);
                            break;

                        case R.id.a2307_new_Note35:
                            model.setNote35(m_Text);
                            break;

                        case R.id.a2307_new_Note36:
                            model.setNote36(m_Text);
                            break;

                        case R.id.a2307_new_Note37:
                            model.setNote37(m_Text);
                            break;

                        case R.id.a2307_new_Note40:
                            model.setNote40(m_Text);
                            break;

                        case R.id.a2307_new_Note41:
                            model.setNote41(m_Text);
                            break;

                        case R.id.a2307_new_Note42:
                            model.setNote42(m_Text);
                            break;

                        case R.id.a2307_new_Note44:
                            model.setNote44(m_Text);
                            break;

                        case R.id.a2307_new_Note45:
                            model.setNote45(m_Text);
                            break;

                        case R.id.a2307_new_Note46:
                            model.setNote46(m_Text);
                            break;

                        case R.id.a2307_new_Note47:
                            model.setNote47(m_Text);
                            break;

                        case R.id.a2307_new_Note48:
                            model.setNote48(m_Text);
                            break;

                        case R.id.a2307_new_Note49:
                            model.setNote49(m_Text);
                            break;

                        case R.id.a2307_new_Note50:
                            model.setNote50(m_Text);
                            break;

                        case R.id.a2307_new_note:
                            model.setNote43(m_Text);
                            break;
//                        case R.id.a2307_new_max_flowrate:
//                            d = numberFormat.parse(m_Text).doubleValue();


                    }
                } catch (NumberFormatException ex) {
                    activitya2307.showErrorMessage(R.string.invalid_number_format);
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
