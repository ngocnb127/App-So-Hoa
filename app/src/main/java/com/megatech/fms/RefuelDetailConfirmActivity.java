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
        else if(mItem.getRealAmount()<=0)
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
        else if (mItem.getDriverId() <=0 || mItem.getOperatorId() <=0)
            showErrorMessage(R.string.invalid_driver_operator);
        else {
            //sendScreenshot();

            // Các kiểm tra ở trên là chặn cứng. Giờ tra nạp bất thường thì chỉ cảnh báo,
            // vì hiện trường có ca dài hợp lệ thật — nhưng phải để người dùng nhận sai
            // một cách có ý thức và có log.
            confirmTimeWarningThenPost();
        }
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

    private final String LOG_TAG = "RFC";
    private void postData()
    {
        if (!BuildConfig.FHS) {
            if (mItem.getTruckId() != currentApp.getTruckId() && mItem.getReceiptNumber() !=null && !mItem.getReceiptNumber().isEmpty())
            {
                mItem.setReceiptNumber(null);
            }
            mItem.setTruckId(currentApp.getTruckId());
            mItem.setTruckNo(currentApp.getTruckNo());
        }
        setProgressDialog();
        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                try {
                    Logger.appendLog(LOG_TAG, "Post item " + mItem.getId() + " UniqueId: " + mItem.getUniqueId());
                    Logger.appendLog(LOG_TAG, formatMeterLog(mItem));

                    RefuelItemData saved = DataHelper.postRefuel(mItem, false);

                    // Lưu thường bị precondition từ chối: thử lại theo kiểu PATCH — đọc row
                    // mới nhất dưới khoá ghi rồi chỉ đắp đúng những trường màn hình này cho
                    // nhập. Nếu cùng một trường hai phía cùng đổi, hoặc mẻ đã được chốt bằng
                    // bộ số khác, patch sẽ tự chặn và trả CONFLICT.
                    if (saved != null
                            && saved.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                        Logger.appendLog(LOG_TAG, "Lưu bị chặn, thử lại bằng ConfirmFieldsPatch");
                        saved = DataHelper.saveConfirmFields(mItem);
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

        // Điều hướng CHỈ sau khi dữ liệu đã vào Room. null cũng là thất bại: đi tiếp là mất
        // trắng số liệu vừa nhập mà người dùng không hề biết.
        if (!RefuelItemData.isCommitted(saved)) {
            Logger.appendLog(LOG_TAG, "Chưa lưu được ("
                    + (saved == null ? "FAILED" : saved.getSaveOutcome())
                    + "), giữ nguyên màn hình xác nhận");
            showErrorMessage(saved != null
                    && saved.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT
                    ? R.string.error_refuel_save_conflict
                    : R.string.error_refuel_save_failed);
            return;
        }

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




        if (userList != null) {
            Dialog dialog = new Dialog(this);
            SelectUserBinding binding = DataBindingUtil.inflate(dialog.getLayoutInflater(), R.layout.select_user, null, false);
            binding.setRefuelItem(mItem);
            dialog.setContentView(binding.getRoot());
            Spinner spn = dialog.findViewById(R.id.select_user_driver);

            ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, userList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(mItem.getDriverId(), userList));

            spn = dialog.findViewById(R.id.select_user_operator);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(mItem.getOperatorId(), userList));

            dialog.show();

            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

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
