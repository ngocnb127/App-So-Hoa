package com.megatech.fms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.LogEntryModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.ParseException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;


public class BaseActivity extends AppCompatActivity implements View.OnClickListener {
    protected FMSApplication currentApp;
    protected UserInfo currentUser;
    protected final String TAG = this.getClass().getName();
    protected boolean isActive;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //if (checkStoragePermission())
        //    checkUpdate();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
        currentApp = (FMSApplication) this.getApplication();
        currentUser = currentApp.getUser();

        //Logger.appendLog("============================ " + TAG + " ======================== ");
    }

    public TruckModel getSetting()
    {
        return FMSApplication.getApplication().getSetting();
    }

    @Override
    public void onBackPressed() {
        return;
    }

    AlertDialog progressDialog;

    public void setProgressDialog() {
        if (!isActive)
            return;
        int llPadding = 30;
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(llPadding, llPadding, llPadding, llPadding);
        ll.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llParam = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        ll.setLayoutParams(llParam);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, llPadding, 0);
        progressBar.setLayoutParams(llParam);

        llParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        TextView tvText = new TextView(this);
        tvText.setText("Loading ...");
        tvText.setTextColor(Color.parseColor("#000000"));
        tvText.setTextSize(20);
        tvText.setLayoutParams(llParam);

        ll.addView(progressBar);
        ll.addView(tvText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setView(ll);

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
        progressDialog = dialog;
    }

    public void closeProgressDialog() {
        if (!isActive)
            return;
        try {
            if (progressDialog != null && !isFinishing() && !isDestroyed())
                progressDialog.dismiss();
        } catch (Exception ex) {

        }
    }

    public void showConfirmMessage(int messageId, Callable<Void> positiveClick) {
        showMessage(R.string.confirm, messageId, R.drawable.ic_question, positiveClick);

    }

    public void showConfirmMessage(int messageId, Callable<Void> positiveClick, Callable<Void> negativeClick) {
        showMessage(R.string.confirm, messageId, R.drawable.ic_question, positiveClick, negativeClick);

    }
    public void showInfoMessage(int messageId)
    {
        showInfoMessage(R.string.info, messageId, R.drawable.ic_info, null);
    }
    public void showInfoMessage(int messageId, Callable<Void> positiveClick)
    {
        showInfoMessage(R.string.info, messageId, R.drawable.ic_info, positiveClick);
    }
    public void showInfoMessage(int titleId,int messageId, int iconId, Callable<Void> positiveClick) {
        if (!isActive)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(titleId)
                .setMessage(messageId)
                .setIcon(iconId)
                .setCancelable(false);

        builder.setPositiveButton(R.string.accept, (dialog1, which) -> {
            try {
                if (positiveClick != null)
                    positiveClick.call();

            } catch (Exception e) {
                e.printStackTrace();
            }
            dialog1.dismiss();
        });
        builder.create().show();
    }

    public void showMessage(int titleId, int messageId, int iconId, Callable<Void> positiveClick) {
        showMessage(titleId, messageId, iconId, positiveClick, null);
    }

    public void showMessage(int titleId, int messageId, int iconId, Callable<Void> positiveClick, Callable<Void> negativeClick) {
        if (!isActive)
            return;

        Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "show message " + getString(messageId),getPackageName());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(titleId)
                .setMessage(messageId)
                .setIcon(iconId)
                .setNeutralButton(R.string.back, (dialog1, which) -> {
                    Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "back click", getPackageName());
                    dialog1.dismiss();
                });
        if (positiveClick != null)
            builder.setPositiveButton(R.string.accept, (dialog1, which) -> {
                try {
                    Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "positive click", getPackageName());
                    positiveClick.call();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                dialog1.dismiss();
            });
        if (negativeClick != null)
            builder.setNegativeButton(R.string.cancel, (dialog1, which) -> {
                if (negativeClick != null) {
                    try {
                        Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "negative click", getPackageName());
                        negativeClick.call();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                dialog1.dismiss();
            });


        builder.create().show();

    }

    public void showWarningMessage(String message) {
        showErrorMessage(R.string.info, message, R.drawable.ic_warning);

    }

    public void showWarningMessage(int messageId) {
        showErrorMessage(R.string.info, messageId, R.drawable.ic_warning);

    }

    public void showErrorMessage(int messageId) {
        showErrorMessage(R.string.error, messageId, R.drawable.ic_error);

    }

    public void showErrorMessage(int titleId, int messageId) {
        showErrorMessage(titleId, messageId, R.drawable.ic_error);

    }

    public void showErrorMessage(int titleId, int messageId, int iconId) {
        if (isActive)
            new AlertDialog.Builder(this)
                    .setTitle(titleId)
                    .setMessage(messageId)
                    .setIcon(iconId)
                    .setPositiveButton("OK", (dialog1, which) -> {
                        dialog1.dismiss();
                    })
                    .create()
                    .show();
    }

    public void showErrorMessage(int titleId, String message, int iconId) {
        if (isActive)

            new AlertDialog.Builder(this)
                    .setTitle(titleId)
                    .setMessage(message)
                    .setIcon(iconId)
                    .setPositiveButton("OK", (dialog1, which) -> {
                        dialog1.dismiss();
                    })
                    .create()
                    .show();
    }

    public void showInputData(int titleId, String defaultText, int inputType, String pattern, boolean required, OnInputCompleted onComplete) {
        Context context = this;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(titleId);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 5, 10, 5);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        input.setTypeface(Typeface.DEFAULT);

        input.setText(defaultText.trim());

        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        layout.addView(input);
        builder.setView(layout);

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
                if (input.getText().toString().isEmpty())
                    showErrorMessage(R.string.empty_required_field);
                else {
                    if (onComplete != null && onComplete.onOK(input.getText().toString()))
                        dialog.dismiss();
                    onComplete.onCompleted();
                }

            }

        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (onComplete != null) {
                    try {
                        onComplete.onCancel();
                    } catch (Exception ex) {
                    }
                }
                dialog.dismiss();
            }
        });
    }

    public void saveLog(LogEntryModel.LOG_TYPE logType, String logText)
    {
        Logger.saveLog(logType, logText, this.getClass().getName());
    }

    @Override
    public void onClick(View view) {
        String log;
        if (view.getId() == View.NO_ID) log = "no-id";
        else log = view.getResources().getResourceName(view.getId());
        Logger.appendLog("Click: " + log);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.appendLog("============================ END " + TAG + " ==================== ");
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onStart() {
        Logger.appendLog("============================ START " + TAG + " ==================== ");
        super.onStart();
        isActive = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public interface OnInputCompleted {
        boolean onOK(String text) ;

        void onCancel();

        void onCompleted();
    }
}
