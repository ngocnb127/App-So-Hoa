package com.megatech.fms;


import androidx.annotation.RequiresApi;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AsyncNotedAppOp;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.DigitsKeyListener;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;


import com.megatech.fms.databinding.ActivityRefuelDetailConfirmBinding;
import com.megatech.fms.databinding.SelectUserBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.ImageUtil;
import com.megatech.fms.helpers.LCRWorker;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.RefuelTimeValidator;
import com.megatech.fms.helpers.ScreenshotAPI;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.UserModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Ref;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RefuelDetailConfirmActivity extends UserBaseActivity implements View.OnClickListener, UpdateSensitiveScreen {
    private LCRWorker lcrWorker ;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refuel_detail_confirm);


        lcrWorker = new LCRWorker(FMSApplication.getApplication().getDeviceIP());
        lcrWorker.setLcrWorkerListener(new LCRWorker.LCRWorkerListener() {


            @Override
            public void onConnected() {
                lcrWorker.requestField(LCRWorker.LCR_FIELD.PREV_METER);
                lcrWorker.requestField(LCRWorker.LCR_FIELD.GROSS_QTY);
                lcrWorker.requestField(LCRWorker.LCR_FIELD.GROSS_METER);
            }

            @Override
            public void onSent() {

            }

            @Override
            public void onReceived(LCRWorker.LCR_FIELD field, Object data) {
                try {
                    double d = Double.parseDouble(data.toString());
                    if (d > 0) {
                        if (field == LCRWorker.LCR_FIELD.PREV_METER)
                            mItem.setStartNumber(d);
                        else if (field == LCRWorker.LCR_FIELD.GROSS_METER)
                            mItem.setEndNumber(d);
                        else if (field == LCRWorker.LCR_FIELD.GROSS_QTY)
                            mItem.setRealAmount(d);
                    }
                } catch (Exception ex) {
                }
            }

            @Override
            public void onCompleted() {

                //postData();
                runOnUiThread(() -> {
                    if (!isFinishing())
                        bindData();
                });

            }
        });

        loaddata();
    }

    private void loaddata() {
        setProgressDialog();
        new Thread(() -> {
            if (userList == null)
                userList = DataHelper.getUsers();

            Bundle b = getIntent().getExtras();
            String mData = b.getString("REFUEL", "");

            RefuelItemData itemData = null;
            if (mData != null && !mData.equals("")) {
                itemData = RefuelItemData.fromJson(mData);
                com.megatech.fms.helpers.RefuelIntent.restoreBaseline(itemData, b);
            }

            if (itemData != null ) {
                mItem = itemData;


                if (mItem!=null && (mItem.getQualityNo() == null || mItem.getQualityNo() .isEmpty()))
                    mItem.setQualityNo(currentApp.getQCNo());
                Logger.appendLog(LOG_TAG, "Start confirm Flight Code: " + mItem.getFlightCode());
                runOnUiThread(() -> {
                    //if (!isFinishing())
                        bindData();
                });

                //lcrWorker.connect();
            }
            else {
                runOnUiThread(() -> {
                    showErrorMessage(R.string.error_data);
                    finish();
                });

            }



        }).start();

    }
    ActivityRefuelDetailConfirmBinding binding;
    private void bindData() {
        if (!isFinishing()) {
            //binding = DataBindingUtil.setContentView(this, R.layout.activity_refuel_detail_confirm);
            binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.activity_refuel_detail_confirm, null, false);
            if (binding != null && mItem != null) {
                binding.setMItem(mItem);
                setContentView(binding.getRoot());
                binding.invalidateAll();
                showTimeContextNotes();
                showTimeWarningPopup();
            }
        }
        closeProgressDialog();
    }

    RefuelItemData mItem;
    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }
    private void showEditDialog(final int id, int inputType, String pattern){
        TextView view = findViewById(id);
        if (view!=null)
            showEditDialog(view, inputType, pattern);
    }

    String m_Title, m_Text;
    private void showEditDialog(final TextView view, int inputType, String pattern) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m_Title);
        Logger.appendLog(LOG_TAG, m_Title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);
        input.setText(view.getText());
        Logger.appendLog(LOG_TAG, "Old value: "+ view.getText().toString());
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL )>0)
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

        builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });


        final AlertDialog dialog = builder.create();// builder.show();

        dialog.show();
        input.requestFocus();
        if (view.getId() == R.id.refuel_confirm_Density)
            input.setSelection(2,input.getText().length());
        else
            input.setSelection(0,input.getText().length());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doUpdateResult())
                    dialog.dismiss();
            }

            private boolean doUpdateResult() {
                try {
                    m_Text = input.getText().toString();
                    Logger.appendLog(LOG_TAG, "New value: "+ m_Text);
                    Pattern regex = Pattern.compile(pattern);
                    Matcher matcher = regex.matcher(m_Text);
                    if (!matcher.find()) {
                        Toast.makeText(context, getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return updateDialogResult(view.getId(), m_Text);
                } catch (Exception ex) {
                    Toast.makeText(context,R.string.invalid_number_format, Toast.LENGTH_LONG).show();
                    return false;

                }

            }
        });
    }

    private boolean updateDialogResult(int id, String m_text) {
        try {
            switch (id) {

                case R.id.refuel_confirm_Density:
                    double d = numberFormat.parse(m_Text).doubleValue();
                    if (d < 0.72 || d > 0.86) {
                        this.showErrorMessage(R.string.error_data, R.string.invalid_density, R.drawable.ic_error);

                        return false;
                    }else
                    mItem.setDensity(d);
                    break;
                case R.id.refuel_confirm_Temperature:
                    mItem.setManualTemperature(numberFormat.parse(m_Text).doubleValue());
                    break;
                case R.id.refuel_confirm_qc_no:
                    mItem.setQualityNo(m_Text);
                    break;
                case R.id.refuel_confirm_start_meter:
                    //mItem.setStartNumber(numberFormat.parse(m_Text).doubleValue());
                    break;
                case R.id.refuel_confirm_end_meter:
                    mItem.setEndNumber(numberFormat.parse(m_Text).doubleValue());
                    // Đồng hồ đo gallon: số đồng hồ trừ theo gallon, không trừ theo lít.
                    // mItem.setStartNumber(mItem.getEndNumber() - (BuildConfig.FHS ? mItem.getVolume() : mItem.getRealAmount()));
                    mItem.setStartNumber(mItem.getEndNumber() - mItem.getRealAmount());
                    break;
                case R.id.refuel_confirm_real_amount:
                    double amount = numberFormat.parse(m_Text).doubleValue();
                    // Nhánh nhập tay theo LÍT — không còn xe nào dùng đồng hồ lít.
                    // if (BuildConfig.FHS) {
                    //     double gal = Math.round(amount / RefuelItemData.GALLON_TO_LITTER);
                    //     mItem.setRealAmount(gal);
                    //     mItem.setGallon(gal);
                    //     mItem.setVolume(amount);
                    // }
                    mItem.setRealAmount(amount);
                    mItem.setStartNumber(mItem.getEndNumber() - amount);
                    break;
                case R.id.refuel_confirm_return:
                    calculateReturnAmount(numberFormat.parse(m_Text).doubleValue());
                    break;
                case R.id.refuel_confirm_weight_note:
                    mItem.setWeightNote(m_Text);
                    break;
            }
            updateBinding();
        } catch (ParseException ex) {
            Toast.makeText(this, R.string.invalid_number_format, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    private void calculateReturnAmount(double returnAmount) {

        if (mItem.getDensity()>0) {
            double vol = Math.round(returnAmount / mItem.getDensity());
            double gal = Math.round(vol / RefuelItemData.GALLON_TO_LITTER);
            double newAmount = Math.round(Math.round(gal * RefuelItemData.GALLON_TO_LITTER) * mItem.getDensity());
            if (gal > mItem.getRealAmount()) {
                showWarningMessage(R.string.return_amount_greater_warning );
            } else if (newAmount != returnAmount) {
                showWarningMessage(getString(R.string.new_return_amount_value) + " " + newAmount + " KG");

            }

            mItem.setReturnAmount(newAmount);
        }
    }
    private void updateBinding() {
        if (binding!=null)
            binding.invalidateAll();
    }

    /**
     * Ghi chú ngữ cảnh về giờ tra nạp, hiển thị TẠI CHỖ ngay đầu bảng dữ liệu.
     *
     * <p>Hai ca "chưa có giờ tiếp cận" và "mẻ 0 phút" xảy ra thường xuyên do chính thiết kế
     * hiện tại, nên KHÔNG được đưa vào popup: người dùng sẽ quen tay bấm bỏ qua và bỏ qua
     * luôn cảnh báo lỗi thật. Ở đây chỉ hiện một dòng chữ, không chặn, không hỏi.
     */
    private void showTimeContextNotes() {
        TextView view = findViewById(R.id.txtTimeContextNotes);
        if (view == null) return;

        String notes = RefuelTimeValidator.describeContextNotes(mItem);
        if (notes.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }

        view.setText(notes);
        view.setVisibility(View.VISIBLE);
        Logger.appendLog(LOG_TAG, "Ghi chú giờ tra nạp: " + flatten(notes));
    }

    /**
     * Popup liệt kê các điểm bất thường của giờ tra nạp, hiện ngay khi mở màn xác nhận.
     *
     * <p>Chỉ nhắc, không chặn: người dùng đóng popup rồi chạm thẳng vào ô giờ để sửa. Nếu
     * bỏ qua, {@link #confirmTimeWarningThenPost()} sẽ hỏi lại một lần nữa lúc bấm Xác nhận.
     */
    private void showTimeWarningPopup() {
        if (isFinishing()) return;

        String message = RefuelTimeValidator.describe(mItem);
        if (message.isEmpty()) return;

        Logger.appendLog(LOG_TAG, "Cảnh báo giờ tra nạp: " + flatten(message));

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(message + "\n\nChạm vào ô giờ bắt đầu / giờ kết thúc để nhập lại.")
                .setPositiveButton("Đã hiểu", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    /**
     * Chốt phiếu, nhưng nếu giờ tra nạp vẫn còn bất thường thì hỏi lại một lần nữa.
     *
     * <p>Lần hỏi thứ hai này là chỗ người dùng chủ động nhận sai số liệu, nên cả hai nhánh
     * đều ghi log: sau ca còn truy được ai đã chấp nhận và chấp nhận điều gì.
     */
    private void confirmTimeWarningThenPost() {
        String message = RefuelTimeValidator.describe(mItem);

        if (message.isEmpty()) {
            post();
            return;
        }

        Logger.appendLog(LOG_TAG, "Bấm Xác nhận khi giờ tra nạp còn bất thường: "
                + flatten(message));

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(message + "\n\nGiờ tra nạp vẫn chưa được sửa. Bạn vẫn xác nhận phiếu?")
                .setPositiveButton("Vẫn xác nhận", (dialog, which) -> {
                    dialog.dismiss();
                    Logger.appendLog(LOG_TAG, "NGƯỜI DÙNG CHẤP NHẬN giờ tra nạp bất thường"
                            + " (flight=" + mItem.getFlightCode()
                            + ", uid=" + mItem.getUniqueId() + "): " + flatten(message));
                    post();
                })
                .setNegativeButton("Sửa lại", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void post() {
        Logger.appendLog(LOG_TAG, formatMeterLog(mItem));
        postData();
    }

    /** Log ghi theo dòng, nên gộp popup nhiều dòng lại thành một dòng. */
    private static String flatten(String message) {
        return message.replace('\n', ' ').replaceAll(" +", " ");
    }

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Context context= this;
    @Override
    public void onClick(View view) {
        super.onClick(view);
        int id = view.getId();
        switch (id)
        {
            case R.id.refuel_confirm_real_amount:

                    m_Title = getString(R.string.update_real_amount);
                    showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                                break;
            case R.id.refuel_confirm_Density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_confirm_Temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_confirm_qc_no:
                m_Title = getString(R.string.update_qc_no);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".+");
                break;
            case R.id.refuel_confirm_return:
                if (mItem.getDensity()<=0)
                    showErrorMessage(R.string.density_must_input);
                else {
                    m_Title = getString(R.string.update_return_amount);
                    showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                }
                break;

            case R.id.refuel_confirm_weight_note:
                m_Title = getString(R.string.update_weight_note);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,"(\\d+)?");
                break;
            case R.id.refuel_confirm_start_meter:
                m_Title = getString(R.string.update_start_meter);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_confirm_end_meter:
                m_Title = getString(R.string.update_end_meter);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;

            case R.id.refuel_confirm_start_time:
            case R.id.refuel_confirm_end_time:
                showTimeDialog(id);
                break;
            case R.id.refuel_confirm_driver:
            case R.id.refuel_confirm_operator:
                showSelectUser();
                break;
            case R.id.btnConfirm:
                save();
            default:
                break;
        }
    }

    private void save() {
        if (mItem.getDensity()< 0.72 ||  mItem.getDensity() >0.86)
            showErrorMessage(R.string.invalid_density);
        else if(mItem.getManualTemperature()<=0)
            showErrorMessage(R.string.invalid_temperature);
        // Mẻ 0 lít KHÔNG còn bị chặn ở đường GHI NHẬN. Xe đã ra hiện trường, đã tiếp cận
        // nhưng không nạp (chuyến huỷ, khách không lấy) là sự kiện có thật cần ghi lại; chặn
        // ở đây chỉ dồn người dùng vào chỗ BỊA SỐ hoặc khởi động lại app. Đường XUẤT PHIẾU
        // vẫn chặn mẻ 0 lít như cũ (RefuelPreviewActivity / OthersFreshness / InvoiceModel),
        // nên không có nguy cơ phát hành chứng từ rỗng.
        else if(mItem.getRealAmount()<0)
            showErrorMessage(R.string.invalid_real_amount);
        else if(mItem.getStartNumber()<=0 || mItem.getEndNumber()<=0 || mItem.getEndNumber() != mItem.getStartNumber() + ( BuildConfig.FHS? mItem.getVolume(): mItem.getRealAmount()))
            showErrorMessage(R.string.invalid_start_end_meter);
        else if(!mItem.validTime())
            showErrorMessage(R.string.invalid_start_end_time);
        else if (mItem.getQualityNo().trim().isEmpty())
        {
            showErrorMessage(R.string.invalid_qc_no);
        }
        else if (mItem.getRealAmount()>11000 & !BuildConfig.FHS)
        {
            showErrorMessage(R.string.real_amount_too_big);
        }
        else if (mItem.getDriverId() <=0 || mItem.getOperatorId() <=0) {
            // Bắt buộc chọn lái xe / nhân viên là đúng — NHƯNG chỉ khi có gì để chọn. Danh
            // mục tải theo mạng; mất mạng thì hộp chọn không mở được và người dùng kẹt hẳn
            // ở màn xác nhận với một mẻ đã bơm xong. Khi đó cho đi tiếp kèm cảnh báo và log.
            if (userList == null || userList.isEmpty())
                confirmWithoutUserCatalog();
            else
                showErrorMessage(R.string.invalid_driver_operator);
        }
        else {
            //sendScreenshot();

            // Mẻ 0 lít ghi nhận được, nhưng người dùng phải biết trước là nó KHÔNG xuất được
            // phiếu — nếu không họ sẽ đi tới màn xuất rồi mới ngạc nhiên vì bị chặn ở đó.
            // Cảnh báo dạng thông báo ngắn, không hộp thoại, không chặn.
            if (mItem.getRealAmount() == 0) {
                Logger.appendLog(LOG_TAG, "Ghi nhận mẻ 0 lít uid=" + mItem.getUniqueId());
                Logger.appendRefuelAnomaly("event=ZERO_AMOUNT_REFUEL_CONFIRMED uid="
                        + mItem.getUniqueId());
                android.widget.Toast.makeText(this, R.string.warn_zero_amount_no_receipt,
                        android.widget.Toast.LENGTH_LONG).show();
            }

            // Các kiểm tra ở trên là chặn cứng. Giờ tra nạp bất thường thì chỉ cảnh báo,
            // vì hiện trường có ca dài hợp lệ thật — nhưng phải để người dùng nhận sai
            // một cách có ý thức và có log.
            confirmTimeWarningThenPost();
        }
    }


    /**
     * Cho xác nhận phiếu khi CHƯA tải được danh mục nhân viên.
     *
     * <p>Hai nút, không nút nào là ngõ cụt, và lựa chọn đi tiếp được ghi vào nhật ký bất
     * thường để bổ sung lái xe / nhân viên sau ca.
     */
    private void confirmWithoutUserCatalog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.warn_confirm_without_user)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    dialog.dismiss();
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=CONFIRM_WITHOUT_USER_CATALOG uid=%s flight=%s",
                            mItem == null ? "null" : mItem.getUniqueId(),
                            mItem == null ? "null" : mItem.getFlightCode()));
                    confirmTimeWarningThenPost();
                })
                .setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private int mHour, mMinute, mYear, mMonth, mDay;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private void showTimeDialog(int id) {
        final Date date = new Date();
        if (id == R.id.refuel_confirm_start_time) {
            date.setTime(mItem.getStartTime().getTime());
            Logger.appendLog(LOG_TAG, "Update start time " );
        }
        else {
            date.setTime(mItem.getEndTime().getTime());
            Logger.appendLog(LOG_TAG, "Update end time " );
        }
        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        Logger.appendLog(LOG_TAG, "Old value: " + dateFormat.format(date) );
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay,
                                                  int minute) {
                                c.set(Calendar.MINUTE, minute);
                                c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                updateTime(id,c);

                                Logger.appendLog(LOG_TAG, "New value: " + dateFormat.format(c.getTime()) );
                            }
                        }, mHour, mMinute, false);
                timePickerDialog.show();

            }
        }, mYear, mMonth, mDay);
        // Không cho chọn ngày mai: giờ tra nạp ở tương lai là dữ liệu sai chắc chắn.
        // setMaxDate NÉM IllegalArgumentException nếu giá trị đang hiển thị đã ở tương lai,
        // nên vừa kẹp điều kiện vừa bọc try/catch — hỏng cái chặn còn hơn hỏng màn hình.
        try {
            long nowMs = System.currentTimeMillis();
            if (date.getTime() <= nowMs)
                datePickerDialog.getDatePicker().setMaxDate(nowMs);
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Không đặt được giới hạn ngày: " + ex);
        }
        datePickerDialog.show();
    }

    private void updateTime(int id, Calendar c) {

        if (id == R.id.refuel_confirm_start_time)
            mItem.setStartTime(c.getTime());
        else if (id == R.id.refuel_confirm_end_time)
            mItem.setEndTime(c.getTime());
        updateBinding();
    }

    /**
     * Số đồng hồ là {@code double} và vượt 10^7, nên nối chuỗi thẳng sẽ ra ký hiệu khoa học
     * ({@code 1.585646E7} thay vì {@code 15856460}) — đọc log rất dễ tưởng dữ liệu bị sai.
     * Dùng cùng định dạng với log của DataHelper để hai nơi đối chiếu được với nhau.
     */
    private static String formatMeterLog(com.megatech.fms.model.RefuelItemData item) {
        if (item == null) return "(null item)";
        return String.format(java.util.Locale.US,
                "StartNumber: %.0f EndNumber: %.0f RealAmount: %.0f Temperature: %.1f Density: %.4f",
                item.getStartNumber(), item.getEndNumber(), item.getRealAmount(),
                item.getManualTemperature(), item.getDensity());
    }

    /**
     * Báo cho người dùng biết mẻ đang xác nhận thuộc chuyến chưa phân công cho xe này.
     *
     * <p>Toast, KHÔNG phải dialog: người vận hành đang đứng tại tàu bay, mọi thứ chặn luồng
     * ở bước này đều tệ hơn việc ghi nhầm chủ sở hữu. Dữ liệu vẫn được ghi bình thường.
     */
    private void warnUnassignedTakeover() {
        Logger.appendLog(LOG_TAG, "Xác nhận mẻ của chuyến chưa phân công cho xe, uid="
                + (mItem == null ? "null" : mItem.getUniqueId()));
        runOnUiThread(() -> android.widget.Toast.makeText(this,
                R.string.warn_confirm_unassigned_flight,
                android.widget.Toast.LENGTH_LONG).show());
    }

    private final String LOG_TAG = "RFC";
    private void postData()
    {
        setProgressDialog();
        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                try {
                    Logger.appendLog(LOG_TAG, "Post item " + mItem.getId() + " UniqueId: " + mItem.getUniqueId());
                    Logger.appendLog(LOG_TAG, formatMeterLog(mItem));

                    // PHẢI hỏi ở LUỒNG NỀN. isForeignTruckRefuel() đọc Room để biết mẻ này
                    // đang mang dấu xe nào, mà Room chặn cứng mọi truy vấn trên luồng giao
                    // diện (assertNotMainThread) — gọi ở ngoài là VĂNG APP ngay khi bấm Xác
                    // nhận, cho MỌI mẻ và cả hai cách kết thúc. Đo trên xe thật 06-09-2026,
                    // bốn lần liên tiếp cùng một stack:
                    //   IllegalStateException: Cannot access database on the main thread
                    //     at DataHelper.isForeignTruckRefuel(DataHelper.java:3847)
                    //     at RefuelDetailConfirmActivity.postData(:681)
                    // StrictMode.permitAll() ở BaseActivity KHÔNG che được chốt này: đó là
                    // chốt riêng của Room, không phải của StrictMode.
                    //
                    // Và phải hỏi TRƯỚC khi đóng dấu lại số xe ở ngay dưới, nếu không thì mọi
                    // mẻ đều trông như của xe hiện tại và cảnh báo không bao giờ hiện.
                    final boolean takeover = DataHelper.isForeignTruckRefuel(mItem);
                    if (takeover) warnUnassignedTakeover();

                    if (!BuildConfig.FHS) {
                        if (mItem.getTruckId() != currentApp.getTruckId() && mItem.getReceiptNumber() !=null && !mItem.getReceiptNumber().isEmpty())
                        {
                            mItem.setReceiptNumber(null);
                        }
                        mItem.setTruckId(currentApp.getTruckId());
                        mItem.setTruckNo(currentApp.getTruckNo());
                    }

                    // Chuyến chưa phân công cho xe mà người này vừa bơm hộ: VẪN GHI. Chặn ở
                    // đây là mất trắng số liệu của một mẻ có thật — đúng ca T1-07. Việc bất
                    // thường đã được ghi anomaly trong DataHelper để đối soát sau ca.
                    RefuelItemData saved = takeover
                            ? DataHelper.postRefuelFromRefuelScreen(mItem, false)
                            : DataHelper.postRefuel(mItem, false);

                    // Lưu thường bị precondition từ chối: thử lại theo kiểu PATCH — đọc row
                    // mới nhất dưới khoá ghi rồi chỉ đắp đúng những trường màn hình này cho
                    // nhập. Nếu cùng một trường hai phía cùng đổi, hoặc mẻ đã được chốt bằng
                    // bộ số khác, patch sẽ tự chặn và trả CONFLICT.
                    if (saved != null
                            && saved.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                        Logger.appendLog(LOG_TAG, "Lưu bị chặn, thử lại bằng ConfirmFieldsPatch");
                        saved = takeover
                                ? DataHelper.saveConfirmFieldsFromRefuelScreen(mItem)
                                : DataHelper.saveConfirmFields(mItem);
                    }

                    // Chỉ khi dữ liệu THỰC SỰ vào Room mới được gán bản ghi trả về lên mItem.
                    // Gán lúc bị chặn là xoá sạch số liệu mẻ và nhiệt độ/tỉ trọng vừa nhập —
                    // đúng cách màn hình này từng hiện về 0 GL.
                    if (RefuelItemData.isCommitted(saved))
                        mItem = saved;
                    return saved;
                } catch (Throwable ex) {
                    Logger.appendLog(LOG_TAG, "Lưu lỗi: " + ex);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(RefuelItemData itemData) {
                Logger.appendLog(LOG_TAG, "Post item completed");
                postRefuelCompleted(itemData);
                super.onPostExecute(itemData);

            }
        }.execute();
    }

    private  void sendScreenshot()
    {
        Bitmap b = takeScreenshot();
        File f = saveBitmap(b);
        Logger.appendLog(LOG_TAG, "screenshot file " + f.getName());
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    if (new  ScreenshotAPI().postScreenshot(f))
                        f.delete();

                } catch (Exception e) {
                    Logger.appendLog(LOG_TAG,"Send screenshot failed");
                }
                return null;
            }
        }.execute();
    }
    private Bitmap takeScreenshot() {
        View rootView = findViewById(android.R.id.content).getRootView();
        rootView.setDrawingCacheEnabled(true);
        return rootView.getDrawingCache();
    }

    private File saveBitmap(Bitmap bitmap) {
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ;
        File imagePath = null;
        try {
            imagePath = new File(folder,"screenshot_"+ mItem.getUniqueId()+".jpg");
            imagePath.createNewFile();
            // File.createTempFile("screenshot_"+ mItem.getUniqueId(),".jpg",folder);
        } catch (Exception e) {
            e.printStackTrace();
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imagePath);
            Bitmap scaledBitmap = ImageUtil.resize(bitmap, 800);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            bitmap.recycle();
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {

        } catch (IOException e) {

        } catch (Exception ex)
        {

        }
        return imagePath;
    }
    private void postRefuelCompleted(RefuelItemData saved) {
        closeProgressDialog();

        // Chưa vào được Room thì PHẢI báo — nhưng KHÔNG chặn đường sang màn hình sau. Người
        // dùng còn phải soát lại toàn bộ số liệu ở màn xem trước trước khi xuất phiếu; giữ họ
        // đứng lại đây khi lỗi dai dẳng là làm chết cả quy trình chứ không cứu được dữ liệu.
        if (!RefuelItemData.isCommitted(saved)) {
            Logger.appendLog(LOG_TAG, "Chưa lưu được ("
                    + (saved == null ? "FAILED" : saved.getSaveOutcome())
                    + "), cảnh báo và để người dùng chọn Thử lại hoặc Tiếp tục");
            showSaveFailedButAllowContinue(saved);
            return;
        }

        openPreview();

    }

    /**
     * Báo lưu hỏng, KHÔNG chặn quy trình.
     *
     * <p>"Thử lại" ghi lại, "Tiếp tục" sang màn xem trước kèm cảnh báo phải soát lại số liệu,
     * huỷ hộp thoại thì ở lại đây sửa tiếp. Không lối nào là ngõ cụt.
     */
    private void showSaveFailedButAllowContinue(RefuelItemData saved) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(saved != null
                        && saved.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT
                        ? R.string.error_refuel_save_conflict
                        : R.string.error_refuel_save_failed)
                .setPositiveButton(R.string.retry, (dialog, which) -> post())
                .setNegativeButton(R.string.refuel_end_save_failed_continue,
                        (dialog, which) -> continueWithoutSave(saved))
                .setCancelable(true)
                .show();
    }

    /**
     * Sang màn xem trước dù chưa lưu được.
     *
     * <p>KHÔNG nhận {@code saved} lên {@code mItem}: kết quả bị chặn chỉ là bản đọc lại row
     * đang có trong máy, gán vào là xoá đúng những gì người dùng vừa nhập.
     */
    private void continueWithoutSave(RefuelItemData saved) {
        Logger.appendLog(LOG_TAG, "Người dùng chọn đi tiếp khi phiếu chưa lưu được ("
                + (saved == null ? "FAILED" : saved.getSaveOutcome())
                + "), uid=" + (mItem == null ? "null" : mItem.getUniqueId()));
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=CONFIRM_SAVE_FAILED_CONTINUED uid=%s outcome=%s flight=%s amount=%.0f",
                mItem == null ? "null" : mItem.getUniqueId(),
                saved == null ? "FAILED" : saved.getSaveOutcome(),
                mItem == null ? "null" : mItem.getFlightCode(),
                mItem == null ? 0d : mItem.getRealAmount()));

        Toast.makeText(this, R.string.warn_refuel_save_failed_continued,
                Toast.LENGTH_LONG).show();
        openPreview();
    }

    private void openPreview() {
        if (mItem != null) {

            Intent intent = new Intent(this, RefuelPreviewActivity.class);
            intent.putExtra("REFUEL_ID", mItem.getId());
            intent.putExtra("REFUEL_LOCAL_ID", mItem.getLocalId());
            intent.putExtra("REFUEL_UNIQUE_ID", mItem.getUniqueId());
            //int confirm_OPEN = 1;
            startActivity(intent);
        }

        finish();
    }
    private List<UserModel> userList = null;

    private void showSelectUser() {




        // Danh mục nhân viên tải theo mạng. Trước đây chỉ có nhánh `if (userList != null)`:
        // danh mục chưa về thì bấm vào ô lái xe / nhân viên KHÔNG xảy ra gì cả, người dùng
        // không biết vì sao. Danh mục rỗng-mà-không-null thì findUser() duyệt list rỗng và
        // màn hình đứng lại ở lựa chọn -1.
        if (userList == null || userList.isEmpty()) {
            Logger.appendLog(LOG_TAG, "Danh mục nhân viên rỗng, không mở được hộp chọn");
            showWarningMessage(R.string.warn_user_list_empty);
            return;
        }

        {
            Dialog dialog = new Dialog(this);
            SelectUserBinding binding = DataBindingUtil.inflate(dialog.getLayoutInflater(), R.layout.select_user, null, false);
            binding.setRefuelItem(mItem);
            dialog.setContentView(binding.getRoot());
            Spinner spn = dialog.findViewById(R.id.select_user_driver);

            ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, userList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

            // findUser trả -1 khi phiếu đang mang một nhân viên không còn trong danh mục.
            // Đặt selection = -1 thì getSelectedItem() trả null và nút Chọn ném NPE.
            spn.setAdapter(spinnerAdapter);
            spn.setSelection(Math.max(0, findUser(mItem.getDriverId(), userList)));

            spn = dialog.findViewById(R.id.select_user_operator);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(Math.max(0, findUser(mItem.getOperatorId(), userList)));

            dialog.show();

            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

                    if (driver == null || operator == null) {
                        // Danh mục vừa bị làm rỗng giữa chừng: đóng hộp thay vì ném NPE.
                        Logger.appendLog(LOG_TAG, "Hộp chọn nhân viên không có lựa chọn hợp lệ");
                        dialog.dismiss();
                        return;
                    }

                    if (driver.getId() == operator.getId()) {
                        new AlertDialog.Builder(dialog.getContext())
                                .setTitle(R.string.select_user)
                                .setMessage(R.string.error_same_user)
                                .setIcon(R.drawable.ic_error)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                })
                                .show();
                        return;
                    }

                    if (driver != null) {
                        mItem.setDriverId(driver.getId());
                        mItem.setDriverName(driver.getName());
                    }

                    if (operator != null) {
                        mItem.setOperatorId(operator.getId());
                        mItem.setOperatorName(operator.getName());
                    }

                    updateBinding();
                    dialog.dismiss();
                }
            });

            dialog.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
    }
    private int findUser(int userId, List<UserModel> userList) {
        int pos = 0;
        for (UserModel item : userList) {
            if (item.getId() == userId)
                return pos;
            pos++;
        }
        return -1;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Runtime.getRuntime().gc();
    }
}
