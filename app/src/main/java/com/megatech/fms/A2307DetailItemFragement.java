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

import com.megatech.fms.databinding.A2307DetailBinding;
import com.megatech.fms.databinding.A2307NewBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A2307DetailItemFragement extends DialogFragment {

    public A2307DetailItemFragement() {
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
        this.model.Note5 ="Ghi kết quả";
        this.model.Note6 ="Ghi kết quả";
        this.model.Note20 ="Xả sạch nước";
        this.model.Note9 ="Không có hư hỏng bất thường";
        this.model.Note10 ="Không bị rò rỉ,có nắp đậy,niên chì";
        this.model.Note41 ="Đảm bảo tiếp xúc tốt với thân xe và mặt đất";
        this.model.Note11 ="Có 02 cứu hỏa,chỉ vạch xanh";
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

    }

    public A2307DetailItemFragement(CheckTrucksModel model) {
        this.model = model;
    }

    public static A2307DetailItemFragement newInstance(CheckTrucksModel model) {

        A2307DetailItemFragement frag = new A2307DetailItemFragement(model);
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
    A2307DetailBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.a2307_detail, container, false);
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

//        Spinner spnairport = view.findViewById(R.id.a2307_new_airport);
//        ArrayAdapter<AirportsModel> spnairportAdapter = new ArrayAdapter<AirportsModel>(activity, R.layout.support_simple_spinner_dropdown_item, airports);
//        spnairportAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spnairport.setAdapter(spnairportAdapter);
//        if (model.getAirportId() > 0) {
//            for (int i = 0; i < airports.size(); i++)
//                if (model.getAirportId() == airports.get(i).getId()) {
//                    spnairport.setSelection(i);
//                    break;
//                }
//        }
//        spnairport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                AirportsModel airport = (AirportsModel) adapterView.getItemAtPosition(i);
//                model.setAirportId(airport.getId());
//                 model.setAirportName(airport.getName());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });
//
//
//        Spinner spntruck = view.findViewById(R.id.a2307_new_truck_no);
//        ArrayAdapter<TruckModel> spntruckAdapter = new ArrayAdapter<TruckModel>(activity, R.layout.support_simple_spinner_dropdown_item, Trucklst);
//        spntruckAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spntruck.setAdapter(spntruckAdapter);
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
//                 model.setTruckNo(truck.getTruckNo());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });
//
//
//        Spinner spnshift = view.findViewById(R.id.a2307_new_shift);
//        ArrayAdapter<ShiftModel> spnshiftAdapter = new ArrayAdapter<ShiftModel>(activity, R.layout.support_simple_spinner_dropdown_item, Shiftlst);
//        spnshiftAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
//        spnshift.setAdapter(spnshiftAdapter);
//        if (model.getShiftId() > 0) {
//            for (int i = 0; i < Trucklst.size(); i++)
//                if (model.getShiftId() == Trucklst.get(i).getId()) {
//                    spnshift.setSelection(i);
//                    break;
//                }
//        }
//        spnshift.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                ShiftModel shift = (ShiftModel) adapterView.getItemAtPosition(i);
//                model.setShiftId(shift.getId());
//                 model.setShiftName(shift.getName());
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


    public void onClick(View view) {
        int id = view.getId();
        switch (id) {

            case R.id.a2307_new_back:
                dlg.dismiss();
                break;

            case R.id.a2307_new_back2:
                dlg.dismiss();
                break;

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
