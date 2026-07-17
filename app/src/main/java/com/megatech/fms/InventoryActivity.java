package com.megatech.fms;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.megatech.fms.model.RefuelItemData;

public class InventoryActivity extends UserBaseActivity implements View.OnClickListener {

    Button btn;
    TextView lblInventory;
    EditText edt;
    EditText edtQCNo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        lblInventory = findViewById(R.id.lblInventory);
        edtQCNo = findViewById(R.id.txtQualityControlNo);
        edt = findViewById(R.id.txtInventory);
        try {
            lblInventory.setText(String.format("%.0f", currentApp.getCurrentAmount()));
            edtQCNo.setText(currentApp.getQCNo());
        }
        catch (Exception e)
        {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        Button btn = findViewById(R.id.btnUpdate);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float amount = 0;
                try {
                    amount = Float.parseFloat(edt.getText().toString());
                    Spinner inventory_unit = findViewById(R.id.inventory_unit);
                    if (inventory_unit!=null)
                    {
                        if (inventory_unit.getSelectedItem().equals("LIT"))
                            amount = (float)Math.round(amount / RefuelItemData.GALLON_TO_LITTER);
                    }
                } catch (NumberFormatException ex) {
                }
                if (amount>0) {

                    currentApp.setInventory(amount, edtQCNo.getText().toString());
                    finish();
                }
                else
                {
                    showError();
                }
            }
        });
    }

    private void showError() {

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.invalid_amount)
                .setIcon(R.drawable.ic_error)
                .create().show();
    }

    private void save() {
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btnBack:
                finish();
                break;
        }
    }
}
