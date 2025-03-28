package com.megatech.fms.model;



import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.R;
import com.megatech.fms.enums.INVOICE_TYPE;

import java.util.ArrayList;
import java.util.List;

public class InvoiceFormModel {

    private INVOICE_TYPE invoiceType;
    private String sign;
    private String formNo;
    private boolean isDefault;
    private int airportId;
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public INVOICE_TYPE getPrintTemplate() {
        return invoiceType;
    }

    public void setPrintTemplate(INVOICE_TYPE invoiceType) {
        this.invoiceType = invoiceType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getFormNo() {
        return formNo;
    }

    public void setFormNo(String formNo) {
        this.formNo = formNo;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public int getAirportId() {
        return airportId;
    }

    public void setAirportId(int airportId) {
        this.airportId = airportId;
    }



    private boolean localDefault;

    public boolean isLocalDefault() {
        return localDefault;
    }

    public void setLocalDefault(boolean localDefault) {
        this.localDefault = localDefault;
    }

    @NonNull
    @Override
    public String toString() {
        FMSApplication app = FMSApplication.getApplication();
        return app.getString( R.string.form_no) + ": " + formNo + "\t\t" + app.getString( R.string.sign) +": "+ sign;
    }

    public static List<InvoiceFormModel> getInvoiceForms(INVOICE_TYPE invoice_type)
    {
        InvoiceFormModel[] savedForms = FMSApplication.getApplication().getInvoiceForms();
        List<InvoiceFormModel> forms = new ArrayList<>();
        if (savedForms!=null)
        {
            for (InvoiceFormModel model: savedForms)
                if (model.invoiceType == invoice_type)
                    forms.add(model);
        }
        return forms;
    }
}
