package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.TruckFuelModel;

import java.util.Date;
@Entity
public class Invoice extends BaseEntity {

    public static Invoice fromModel(InvoiceModel model)
    {
        Invoice inv = gson.fromJson(gson.toJson(model),Invoice.class);
        inv.setJsonData(gson.toJson(model));
        inv.setId(model.getId());
        return  inv;
    }

    public InvoiceModel toModel() {
        InvoiceModel model = gson.fromJson(this.getJsonData(), InvoiceModel.class);
        model.setLocalId(this.getId());
        model.setDeleted(this.isDeleted());
        model.setLocalModified(this.isLocalModified());
        return model;
    }
    private Date date;
    //private INVOICE_TYPE printTemplate;
    private int invoiceFormId;
    private String formNo;
    private String sign;
    private String invoiceNumber;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    /*public INVOICE_TYPE getPrintTemplate() {
        return printTemplate;
    }

    public void setPrintTemplate(INVOICE_TYPE printTemplate) {
        this.printTemplate = printTemplate;
    }
*/
    public int getInvoiceFormId() {
        return invoiceFormId;
    }

    public void setInvoiceFormId(int invoiceFormId) {
        this.invoiceFormId = invoiceFormId;
    }

    public String getFormNo() {
        return formNo;
    }

    public void setFormNo(String formNo) {
        this.formNo = formNo;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }


}
