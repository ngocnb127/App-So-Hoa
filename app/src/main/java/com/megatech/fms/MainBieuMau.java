package com.megatech.fms;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainBieuMau extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_bieu_mau);

        // ===== GÁN CLICK =====
        bindClick(R.id.btnBack);

        bindClick(R.id.card_2502);
        bindClick(R.id.card_2503);
        bindClick(R.id.card_2504);
        bindClick(R.id.card_2505);
        bindClick(R.id.card_2508);
        bindClick(R.id.card_2507a);
        bindClick(R.id.card_2507a);

    }

    private void bindClick(int id) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        switch (id) {

            case R.id.btnBack:
                finish();
                break;

            case R.id.card_2502:
                openBM2502();
                break;

            case R.id.card_2503:
                openBM2503();
                break;

            case R.id.card_2504:
                openBM2504();
                break;

            case R.id.card_2505:
                openBM2505();
                break;

            case R.id.card_2508:
                openBM2508();
                break;

            case R.id.card_2507a:
                openA2307();
                break;
        }
    }

    // ================= OPEN BIỂU MẪU =================

    private void openBM2502() {
        try {
            startActivity(new Intent(this, B2502Activity.class));
        } catch (Exception ex) {
            Log.e("BM2502", "Open error", ex);
        }
    }
    private void openBM2503() {
        try {
            startActivity(new Intent(this, BM2503Main.class));
        } catch (Exception ex) {
            Log.e("BM2503", "Open error", ex);
        }
    }
    private void openBM2504() {
        try {
            startActivity(new Intent(this, BM2504Main.class));
        } catch (Exception ex) {
            Log.e("BM2504", "Open error", ex);
        }
    }

    private void openBM2505() {
        startActivity(new Intent(this, B2505Activity.class));
    }

    private void openBM2508() {
        startActivity(new Intent(this, B2508Activity.class));
    }

    private void openA2307() {
        startActivity(new Intent(this, A2307Activity.class));
    }
}
