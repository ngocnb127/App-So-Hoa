package com.megatech.fms.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

@Entity(indices = {@Index(value = "invoiceId", unique = true), @Index("billDate")})
public class TruckInvoice {
    @PrimaryKey(autoGenerate = true)
    private long localId;
    @SerializedName("InvoiceId") private long invoiceId;
    @SerializedName("TruckId") private int truckId;
    @SerializedName("FlightCode") private String flightCode;
    @SerializedName("BillNo") private String billNo;
    @SerializedName("BillDate") private Date billDate;
    @SerializedName("InvoiceNumber") private String invoiceNumber;
    @SerializedName("SignNo") private String signNo;
    @SerializedName("LoginTaxCode") private String loginTaxCode;
    @SerializedName("FlightId") private long flightId;
    @SerializedName("hoadon68_id") private String electronicInvoiceId;
    @SerializedName("tthai") private String status;

    public long getLocalId() { return localId; }
    public void setLocalId(long localId) { this.localId = localId; }
    public long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(long invoiceId) { this.invoiceId = invoiceId; }
    public int getTruckId() { return truckId; }
    public void setTruckId(int truckId) { this.truckId = truckId; }
    public String getFlightCode() { return flightCode; }
    public void setFlightCode(String flightCode) { this.flightCode = flightCode; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public Date getBillDate() { return billDate; }
    public void setBillDate(Date billDate) { this.billDate = billDate; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getSignNo() { return signNo; }
    public void setSignNo(String signNo) { this.signNo = signNo; }
    public String getLoginTaxCode() { return loginTaxCode; }
    public void setLoginTaxCode(String loginTaxCode) { this.loginTaxCode = loginTaxCode; }
    public long getFlightId() { return flightId; }
    public void setFlightId(long flightId) { this.flightId = flightId; }
    public String getElectronicInvoiceId() { return electronicInvoiceId; }
    public void setElectronicInvoiceId(String electronicInvoiceId) { this.electronicInvoiceId = electronicInvoiceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
