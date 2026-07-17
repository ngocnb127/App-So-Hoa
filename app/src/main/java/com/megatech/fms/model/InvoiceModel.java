package com.megatech.fms.model;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.enums.RETURN_UNIT;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.NumberConvert;
import com.megatech.fms.helpers.VNCharacterUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

public class InvoiceModel extends BaseModel {

    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
    private String productName = "";

    private Date date;
    private Date startTime;
    private Date endTime;
    private int customerId = 0;
    private String customerName = "";
    private String customerCode = "";
    private String taxCode = "";
    private String customerAddress = "";
    private INVOICE_TYPE invoiceType;
    private String aircraftType;
    private String aircraftCode;
    private int flightId = 0;
    private int flightType = 0;
    private String flightCode;
    private String routeName;
    private double temperature;
    private double density;
    private double volume;
    private double weight;
    private double gallon;
    private double price;
    private RefuelItemData.CURRENCY currency;
    private int unit;
    private double amount;
    private double taxRate;
    private double vatAmount;
    private double saleAmount;
    private String inWords;
    private String invoiceNumber;
    private boolean hasReturn;

    private boolean printed = false;

    public boolean isPrinted() {
        return printed;
    }

    public void setPrinted(boolean printed) {
        this.printed = printed;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public int getUnit() {
        return unit;
    }

    public void setUnit(int unit) {
        this.unit = unit;
    }

    private double realAmount;

    public double getRealAmount() {
        return realAmount;
    }

    public void setRealAmount(double realAmount) {
        this.realAmount = realAmount;
    }

    private List<InvoiceItemModel> items;

    public List<InvoiceItemModel> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItemModel> items) {
        this.items = items;
    }

    private List<InvoiceItemModel> returnItems = new ArrayList<>();

    public List<InvoiceItemModel> getReturnItems() {
        return returnItems;
    }

    public void setReturnItems(List<InvoiceItemModel> returnItems) {
        this.returnItems = returnItems;
    }

    public InvoiceModel() {

    }

    public static InvoiceModel fromJson(String json)
    {
        return gson.fromJson(json, InvoiceModel.class);
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCustomerName() {
        return customerName;
    }

    private boolean isVNA = false;
    public boolean isVNA()
    {
        return isVNA;
    }
    public  void setVNA(boolean value)
    {
        isVNA = value;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getTaxCode() {
        return taxCode == null ? "" : taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getAircraftType() {
        return aircraftType == null? "" : aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getAircraftCode() {
        return aircraftCode == null ? "" : aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public int getFlightType() {
        return flightType;
    }

    public void setFlightType(int flightType) {
        this.flightType = flightType;
    }

    public String getFlightCode() {
        return flightCode == null ? "" : flightCode;
    }

    public String getFlightCodePrint()
    {
        return  VNCharacterUtils.removeAccent(flightCode);
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    public String getRouteName() {
        return routeName == null ? "" : routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = density;
    }

    public double getVolume() {
        return Math.round(this.realAmount * RefuelItemData.GALLON_TO_LITTER);
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getGallon() {
        return gallon;
    }

    public void setGallon(double gallon) {
        this.gallon = gallon;
    }

    public double getWeight() {
        return Math.round(density * getVolume());
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    private double totalVolume;
    public double getTotalVolume()
    {
        return totalVolume;
    }
    private double totalGallon;
    public double getTotalGallon()
    {
        return totalGallon;
    }

    private double totalWeight;
    public double getTotalWeight()
    {
        return totalWeight;
    }
    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public RefuelItemData.CURRENCY getCurrency() {
        return currency;
    }

    public void setCurrency(RefuelItemData.CURRENCY currency) {
        this.currency = currency;
    }

    public double getSaleAmount() {
        return saleAmount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
    }

    public double getVatAmount() {
        return (double)Math.round(getSaleAmount()* getTaxRate()*100)/100;
    }

    public void setVatAmount(double vatAmount) {
        this.vatAmount = vatAmount;
    }

    public double getTotalAmount() {
        return getSaleAmount() + getVatAmount();
    }

    public void setSaleAmount(double totalAmount) {
        this.saleAmount = totalAmount;
    }

    public String getInWords() {
        return inWords;
    }

    public void setInWords(String inWords) {
        this.inWords = inWords;
    }

    private String qualityNo;

    public String getQualityNo() {
        return qualityNo;
    }

    public void setQualityNo(String qualityNo) {
        this.qualityNo = qualityNo;
    }

    public INVOICE_TYPE getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(INVOICE_TYPE invoiceType) {
        this.invoiceType = invoiceType;
    }


    public Date getInvoiceDate(){
        return endTime;
    }

    private int invoiceFormId;
    private String formNo;
    private String sign;

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

    private double techLog;

    public double getTechLog() {
        return techLog;
    }

    public void setTechLog(double techLog) {
        this.techLog = techLog;
    }

    public static InvoiceModel fromRefuel(RefuelItemData refuel, ArrayList<RefuelItemData> allItems) {
        @SuppressWarnings("UnusedAssignment") InvoiceModel model = new InvoiceModel();
        if (allItems != null) {
            allItems.removeIf(itemData -> itemData.getRealAmount()<=0);
            Collections.sort(allItems, new Comparator<RefuelItemData>() {
                @Override
                public int compare(RefuelItemData o1, RefuelItemData o2) {
                    return o2.getEndTime().compareTo(o1.getEndTime());
                }
            });
            refuel = allItems.get(0);
        }


        String data = gson.toJson(refuel);
        model = gson.fromJson(data, InvoiceModel.class);
        model.printed = false;
        model.customerId = refuel.getAirlineId();
        model.customerName = refuel.getInvoiceNameCharter().trim();
        if (model.customerName.isEmpty())
            model.customerName = refuel.getAirlineModel().getName().trim();
        model.customerAddress = refuel.getAirlineModel().getAddress().trim();
        model.customerCode = refuel.getAirlineModel().getCode();
        model.taxCode = refuel.getAirlineModel().getTaxCode().trim();
        model.productName = refuel.getAirlineModel().getProductName();
        model.flightType = refuel.isInternational()?1:0;

        model.temperature = refuel.getManualTemperature();
        model.density = refuel.getDensity();
        //model.amount = refuel.getAmount();

        model.date = refuel.getEndTime();

        model.invoiceType = !refuel.isInternational() && !refuel.getAirlineModel().isInternational() ? INVOICE_TYPE.BILL : INVOICE_TYPE.INVOICE;
        model.isVNA = refuel.getAirlineModel().getCode().equals("VN");
        // create invoice item list
        //addInvoiceItem(model, refuel);

        double totalW = 0;//model.getWeight();
        double totalV = 0;//model.getVolume();
        int largestItemIdx = 0;
        double largestAmount = 0;
        if (allItems != null) {

            for (int i = 0; i < allItems.size(); i++) {
                RefuelItemData item = allItems.get(i);
                String itemData = gson.toJson(item);
                addInvoiceItem(model, item);

                //totalW += item.getWeight();
                //totalV += item.getVolume();

                if (model.startTime.compareTo(item.getStartTime())>0)
                    model.setStartTime(item.getStartTime());
                if (model.endTime.compareTo(item.getEndTime())<0)
                    model.setEndTime(item.getEndTime());
                if (item.getWeightNote()!=null && !item.getWeightNote().isEmpty())
                    model.techLog = Double.parseDouble(item.getWeightNote());
            }

            for (InvoiceItemModel item: model.items)
            {
                totalV += item.getVolume();
                totalW += item.getWeight();

                if (item.getVolume() > largestAmount)
                {
                    largestItemIdx = model.items.indexOf(item);
                    largestAmount = item.getVolume();
                }
            }
            /// comment out to change medthod
            /*
            if (!model.isVNA || !model.hasReturn) {
                model.totalVolume = Math.round(model.totalGallon * GALLON_TO_LITTER);
                model.totalWeight = Math.round(model.density * model.totalVolume);
            }

             */


            if (model.currency == RefuelItemData.CURRENCY.VND)
                model.saleAmount = Math.round(model.saleAmount);
            else model.saleAmount = (double)Math.round(model.saleAmount * 100)/100;
            model.vatAmount = model.saleAmount * model.taxRate;
            if (model.currency == RefuelItemData.CURRENCY.VND)
                model.vatAmount = Math.round(model.vatAmount);
            else model.vatAmount = (double)Math.round(model.vatAmount * 100)/100;


            if (totalV!= model.totalVolume)
            {
                model.items.get(largestItemIdx).setVolume(model.items.get(largestItemIdx).getVolume() + model.totalVolume - totalV);
            }

            //Recalculate weight
            double totalWeight = Math.round(totalV * model.density);
            if (totalWeight != totalW)
            {
                model.items.get(largestItemIdx).setWeight(totalWeight-totalW + model.items.get(largestItemIdx).getWeight());
            }
            model.totalWeight = totalWeight;


            model.saleAmount = (model.unit == 0? model.totalGallon: model.totalWeight) * model.price;
            //Change calculation by OMEGA Formula
            /*if (allItems.size()>1) {
                model.density = totalW / totalV;
                model.temperature = totalP / totalV;
            }*/
        }

        model.inWords = NumberConvert.NumberToSentence(model.currency == RefuelItemData.CURRENCY.USD ? model.getTotalAmount() : Math.round(model.getTotalAmount()), false, model.currency == RefuelItemData.CURRENCY.USD ? "USD" : "VND");

        return model;
    }
    private static void addItem(InvoiceModel model,InvoiceItemModel invItem){
        if (model.items == null) {
            model.items = new ArrayList<>();
            model.totalGallon = 0; model.totalVolume = 0;
        }
        model.items.add(invItem);
        model.totalVolume += invItem.getVolume();
        model.totalGallon += invItem.getRealAmount();
        model.totalWeight += invItem.getWeight();

    }
    private static void addInvoiceItem(InvoiceModel model, RefuelItemData refuel) {

        String data = gson.toJson(refuel);
        if (refuel.getReturnAmount() > 0) {

            RETURN_UNIT unit = refuel.getReturnUnit();
            double returnAmount = refuel.getReturnAmount();
            double returnVolume = Math.round(refuel.getDensity() > 0 ? (returnAmount / refuel.getDensity()) : 0);
            double returnGallon = Math.round(returnVolume / GALLON_TO_LITTER);

            if (unit == RETURN_UNIT.GALLON)
            {
                returnGallon = refuel.getReturnAmount();
            }
            returnVolume = Math.round(returnGallon * GALLON_TO_LITTER);
            returnAmount = Math.round(returnVolume * refuel.getDensity());

            double endNumber = Math.round(refuel.getStartNumber() + returnGallon);

            InvoiceItemModel invItem = gson.fromJson(data, InvoiceItemModel.class);
            invItem.setRefuelItemId(refuel.getId());
            invItem.setRefuelUniqueId(refuel.getUniqueId());
            invItem.setEndNumber(endNumber);
            invItem.setVolume(returnVolume);
            invItem.setRealAmount(returnGallon);
            invItem.setWeight(returnAmount);
            invItem.setTemperature(refuel.getManualTemperature());
            invItem.setReturn(true);
            if (model.isVNA)
                addItem(model, invItem);
            else
                model.returnItems.add(invItem);

            InvoiceItemModel invItem2 = gson.fromJson(data, InvoiceItemModel.class);
            invItem2.setStartNumber(endNumber);
            invItem2.setEndNumber(refuel.getEndNumber());
            //invItem2.setEndNumber(refuel.getStartNumber() + refuel.getRealAmount());
            //invItem2.setVolume(refuel.getVolume() - returnVolume);
            invItem2.setRealAmount(refuel.getRealAmount() - returnGallon);
            invItem2.setVolume(Math.round(invItem2.getRealAmount()*GALLON_TO_LITTER));
            invItem2.setWeight(Math.round(invItem2.getVolume()*model.getDensity()));
            invItem2.setTemperature(refuel.getManualTemperature());
            invItem2.setReturn(false);
            invItem2.setRefuelItemId(refuel.getId());
            invItem2.setRefuelUniqueId(refuel.getUniqueId());
            if (invItem2.getGallon()>0)
            addItem(model, invItem2);

            model.hasReturn = true;
        } else {
            InvoiceItemModel invItem = gson.fromJson(data, InvoiceItemModel.class);
            invItem.setRealAmount(refuel.getRealAmount());

            invItem.setVolume(refuel.getVolume());
            invItem.setWeight(refuel.getWeight());
            invItem.setEndNumber(refuel.getStartNumber() + refuel.getRealAmount());
            invItem.setRefuelItemId(refuel.getId());
            invItem.setRefuelUniqueId(refuel.getUniqueId());
            invItem.setTemperature(refuel.getManualTemperature());
            addItem(model, invItem);
        }
    }

    private String[] split(String line, int length) {
        String[] lines = new String[2];
        //get character at length

        lines[0] = line;
        lines[1] = "";
        if (line.length() > length) {
            String seperators = " ,\n\r";
            char ch = line.charAt(length);
            if (seperators.indexOf(ch) < 0) {
                int i = length - 1;
                boolean found = false;
                while (i >= 0 && !found) {
                    ch = line.charAt(i);
                    found = seperators.indexOf(ch) >= 0;
                    if (!found) i--;

                }
                if (i > 0) {
                    lines[0] = line.substring(0, i + 1).trim();
                    lines[1] = line.substring(i + 1).trim();
                }

            } else {
                lines[0] = line.substring(0, length + 1).trim();
                lines[1] = line.substring(length + 1).trim();
            }
        }
        return lines;
    }

    public Queue<String> createBillTextOld() {
        try {
            Locale locale = new Locale("vi", "VN");
            Locale.setDefault(locale);
            String truckInfo = "";
            if (this.items.size() > 3) {
                int i = 0;
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += String.format("%12s  ", mItem.getTruckNo());
                    if (i == 2)
                        truckInfo += String.format("%,12.0f\n\n\n", this.getVolume());
                    else if (i % 3 == 2)
                        truckInfo += "\n\n\n";
                    i++;
                }

                truckInfo += new String(new char[]{27, 100, (char) (10 - (int) Math.ceil(4 - this.items.size() / 2) * 3)});

                //truckInfo += new String(new char[(int)Math.ceil(4-this.items.size() / 2)]).replace('\0','\n');
            } else {
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += (truckInfo != "" ? "\n\n" : "") + String.format("%15s %,15.0f / %,-15.0f   %,10.0f %s\n", mItem.getTruckNo(), mItem.getStartNumber(), mItem.getEndNumber(), mItem.getVolume(), mItem.isReturn() ? "HT" : " ");

                }
                if (this.items.size() < 3)
                    truckInfo += new String(new char[]{27, 100, (char) (9 - this.items.size() * 3)});

            }

            double realAmount = getRealAmount();
            double saleAmount = getTotalAmount();
            double volume = getTotalVolume();// getVolume();
            double weight = getTotalWeight();// getWeight();
            double taxRate = getTaxRate();
            double vatAmount = getVatAmount();
            double totalSaleAmount = getTotalAmount();
            double price = getPrice();
            double temperature = getTemperature();
            double density = getDensity();

            double totalT = volume;
            double totalP = temperature * volume;
            //String[] addresses = split(VNCharacterUtils.removeAccent(this.getAddress()), 50);
            //String[] names = split(VNCharacterUtils.removeAccent(this.getCustomerName()), 45);
            String[] addresses = split(this.getCustomerAddress(), 50);
            String[] names = split(this.getCustomerName(), 45);
            byte b = 16;

            if (addresses[1]!="" && names[1]!="")
                b = 7;
            else if (addresses[1]!="" || names[1]!="")
                b = 10;

            FMSApplication app = FMSApplication.getApplication();
            SimpleDateFormat format = new SimpleDateFormat("         dd         MM        yyyy\n");
            SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
            Queue<String> dataToPrint;
            dataToPrint = new LinkedList<>(Arrays.asList(
                    new String(new char[]{27, 51, 50}),
                    "\n",
                    new String(new char[]{27, 51, 65}),
                    format.format(getInvoiceDate()),
                    new String(new char[]{27, 51, 10}),

                    String.format("                            %s\n", qualityNo == null ? "" : qualityNo),

                    String.format("                      %s %s                      %s\n\n", timeformat.format((this.getStartTime())), timeformat.format(this.getEndTime()), VNCharacterUtils.removeAccent(this.getProductName())),

                    new String(new char[]{27, 51, 40}),
                    "\n",
                    new String(new char[]{27, 51, 16}),
                    //String.format("    %s           %.0f    %.0f                    %.2f\n",mItem.getTruckNo()  ,mItem.getStartNumber(), mItem.getEndNumber(), mItem.getVolume()),
                    truckInfo,
                    new String(new char[]{27, 51, 4}),
                    "\n",
                    new String(new char[]{27, 51,10}),
                    String.format("                   %s\n", names[0]) + (names[1]!=""?String.format("\n                   %s\n",names[1]):"\n\n"),
                    //new String(new char[]{27, 51,(char)b}),
                    String.format("                   %s\n", getTaxCode()),
                    String.format("             %s\n", addresses[0]) + (addresses[1]!=""?String.format("\n             %s\n", addresses[1]):"\n\n"),
                    new String(new char[]{27, 51, 16}),
                    String.format("%30s%34s\n", this.getAircraftType(), this.getAircraftCode()),
                    String.format("%30s%34s\n", this.getFlightCodePrint(), this.getRouteName()),
                    String.format("%,30.0f%,34.0f\n", this.getTotalGallon(), this.getTotalVolume()),
                    String.format("%30.2f%34.4f\n", this.getTemperature(), this.getDensity()),
                    String.format("%,30.0f                                \n", this.getTotalWeight())

            ));

            return dataToPrint;
        } catch (Exception ex) {
            Logger.appendLog("Error creating bill text: " + ex.getMessage());
            return null;
        }
    }

    public Queue<String> createInvoiceTextOld() {
        try {
            Locale locale = new Locale("vi", "VN");
            Locale.setDefault(locale);
            String truckInfo = "";

            if (this.items.size() > 3) {
                int i = 0;
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += String.format("%12s  ", mItem.getTruckNo());
                    if (i == 1)
                        truckInfo += String.format("%,12.0f\n\n", this.getVolume());
                    else if (i % 2 == 1)
                        truckInfo += "\n";
                    i++;
                }
                truckInfo += new String(new char[(int) Math.ceil(4 - this.items.size() / 2)]).replace('\0', '\n');
            } else {
                for (InvoiceItemModel mItem : this.items) {
                    truckInfo += (truckInfo != "" ? "\n" : "") + String.format("%12s %,15.0f / %,-15.0f %s  %,12.0f\n", mItem.getTruckNo(), mItem.getStartNumber(), mItem.getEndNumber(), mItem.isReturn() ? "HT" : " ", mItem.getVolume());

                }

                if (this.items.size() < 3)
                    truckInfo += new String(new char[]{27, 100, (char) (7 - this.items.size() * 2)});

            }
            double realAmount = this.getTotalGallon();
            double saleAmount = this.getSaleAmount();
            double volume = this.getTotalVolume();
            double weight = this.getTotalWeight();
            double taxRate = this.getTaxRate();
            double vatAmount = this.getVatAmount();
            double totalSaleAmount = this.getTotalAmount();
            double price = this.getPrice();
            double temperature = this.getTemperature();
            double density = this.getDensity();

            double totalT = volume;
            double totalP = temperature * volume;



            String[] addresses = split(this.getCustomerAddress(), 50);
            String[] names = split(this.getCustomerName(), 45);
            String[] words = split(inWords, 45);

            byte b = 13;

            if (addresses[1]!="" && names[1]!="")
                b = 7;
            else if (addresses[1]!="" || names[1]!="")
                b = 10;

            Queue<String> dataToPrint;

            FMSApplication app = FMSApplication.getApplication();
            SimpleDateFormat format = new SimpleDateFormat("         dd           MM         yyyy\n");
            SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
            dataToPrint = new LinkedList<>(Arrays.asList(
                    new String(new char[]{27, 51, 40}),
                    "\n",
                    new String(new char[]{27, 51, 72}),
                    format.format(this.getInvoiceDate()),
                    new String(new char[]{27, 51, 14}),

                    String.format("                         %s\n", this.qualityNo == null ? "" : this.qualityNo),
                    String.format("                       %s  %s                       %s\n", timeformat.format((this.getStartTime())), timeformat.format(this.getEndTime()), VNCharacterUtils.removeAccent(this.getProductName())),

                    new String(new char[]{27, 51, 9}),
                    "\n\n\n\n\n",
                    new String(new char[]{27, 51, 16}),
                    //String.format("    %s           %.0f    %.0f                    %.2f\n",mItem.getTruckNo()  ,mItem.getStartNumber(), mItem.getEndNumber(), mItem.getVolume()),
                    truckInfo,
                    //new String(new char[]{27,51,1}),
                    new String(new char[]{27, 51, 6}),
                    "\n",
                    new String(new char[]{27, 51, 13}),
                    String.format("                   %s\n", names[0]) + (names[1]!=""?String.format("\n                   %s\n", names[1]):""),
                    new String(new char[]{27, 51, (char)b}),
                    String.format("                   %s\n", this.taxCode == null ? "" : this.taxCode),
                    String.format("             %s\n", addresses[0])  + (addresses[1]!=""?String.format("\n             %s\n", addresses[1]):""),
                    new String(new char[]{27, 51, 13}),
                    String.format("%32s%32s\n", this.getAircraftType(), this.getAircraftCode()),
                    String.format("%32s%32s\n", this.getFlightCodePrint(), this.getRouteName()),
                    String.format("%32.2f%32.4f\n", temperature, density),
                    String.format("%,32.0f%,32.0f\n", volume, weight),
                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            (unit==0?"%,32.0f%,24.0f VND/%s\n":"%,32.0f%,25.0f VND/%s\n")
                            : (unit==0?"%,32.0f%,24.2f USD/%s\n":"%,32.0f%,25.2f USD/%s\n"), realAmount, price, unit == 0?"USG":"KG"),
                    new String(new char[]{27, 51, 14}),
                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,50.0f VND\n"
                            : "%,50.2f USD\n", this.saleAmount),
                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,31.0f%%%,28.0f VND\n"
                            : "%,31.0f%%%,28.2f USD\n", taxRate * 100, vatAmount),

                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,50.0f VND\n"
                            : " %,50.2f USD\n", totalSaleAmount),
                    String.format("                    %s\n", words[0]),
                    String.format("       %s\n", words[1])


            ));

            return dataToPrint;
        } catch (Exception ex) {
            Logger.appendLog("Error creating invoice text: " + ex.getMessage());
            return null;
        }
    }

    public Queue<String> createBillText() {
        try {
            Locale locale = new Locale("vi", "VN");
            Locale.setDefault(locale);
            String truckInfo = "";
            if (this.items.size() > 5) {
                int i = 0;
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += String.format("%12s  ", mItem.getTruckNo());
                    if (i == 2)
                        truckInfo += String.format("%,12.0f\n\n\n", this.getVolume());
                    else if (i % 3 == 2)
                        truckInfo += "\n\n\n";
                    i++;
                }

                truckInfo += new String(new char[]{27, 100, (char) (10 - (int) Math.ceil(4 - this.items.size() / 2) * 3)});

                //truckInfo += new String(new char[(int)Math.ceil(4-this.items.size() / 2)]).replace('\0','\n');
            } else {
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += (truckInfo != "" ? "\n" : "") + String.format("%12s %,15.0f / %,-15.0f   %,10.0f %s\n", mItem.getTruckNo(), mItem.getStartNumber(), mItem.getEndNumber(), mItem.getVolume(), mItem.isReturn() ? "HT" : " ");

                }
                if (this.items.size() < 6) {
                    truckInfo += new String(new char[]{27, 51, (char) (85 - this.items.size() * 11)}) + "\n";

                }

            }

            double realAmount = getRealAmount();
            double saleAmount = getSaleAmount();
            double volume = getVolume();
            double weight = getWeight();
            double taxRate = getTaxRate();
            double vatAmount = getVatAmount();
            double totalSaleAmount = getTotalAmount();
            double price = getPrice();
            double temperature = getTemperature();
            double density = getDensity();

            double totalT = volume;
            double totalP = temperature * volume;

            //String customerName = VNCharacterUtils.removeAccent(this.getCustomerName());
            //String address = VNCharacterUtils.removeAccent(this.getAddress());
            String customerName = this.getCustomerName();
            String address = this.getCustomerAddress();
            String[] names = split(customerName, 48);
            String[] addresses = split(address, 52);

            FMSApplication app = FMSApplication.getApplication();
            SimpleDateFormat format = new SimpleDateFormat("        dd         MM        yyyy\n");
            SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
            Queue<String> dataToPrint;
            dataToPrint = new LinkedList<>(Arrays.asList(
                    new String(new char[]{27, 51, 48}),
                    "\n",
                    new String(new char[]{27, 51, 72}),
                    format.format(getInvoiceDate()),
                    new String(new char[]{27, 51, 14}),
                    String.format("                            %s\n", qualityNo == null ? "" : qualityNo),
                    new String(new char[]{27, 51, 22}),
                    String.format("                       %s / %s                    %s\n\n", timeformat.format((this.getStartTime())), timeformat.format(this.getEndTime()), VNCharacterUtils.removeAccent(this.getProductName())),

                    new String(new char[]{27, 51, 12}),
                    truckInfo,

                    new String(new char[]{27, 51, 11}),
                    String.format("                %s\n", names[0]),
                    new String(new char[]{27, 51, 12}),
                    String.format(" %s\n", names[1]),
                    String.format("                %s\n", taxCode == null ? "" : taxCode),

                    String.format("           %s\n", addresses[0]),
                    new String(new char[]{27, 51, 13}),
                    String.format(" %s\n", addresses[1]),
                    new String(new char[]{27, 51, 16}),
                    String.format("%30s%34s\n", this.getAircraftType(), this.getAircraftCode()),

                    String.format("%30s%34s\n", this.getFlightCodePrint(), this.getRouteName()),
                    new String(new char[]{27, 51, 16}),
                    String.format("%,30.0f%,34.0f\n", this.getTotalGallon(), this.getTotalVolume()),

                    String.format("%,30.2f%,34.4f\n", this.getTemperature(), this.getDensity()),

                    String.format("%,30.0f\n", this.getTotalWeight())

            ));

            return dataToPrint;
        } catch (Exception ex) {
            Logger.appendLog("Error creating bill text: " + ex.getMessage());
            return null;
        }
    }

    public Queue<String> createInvoiceText() {
        try {
            Locale locale = new Locale("vi", "VN");
            Locale.setDefault(locale);
            String truckInfo = "";

            if (this.items.size() > 5) {
                int i = 0;
                for (InvoiceItemModel mItem : this.items) {

                    truckInfo += String.format("%12s  ", mItem.getTruckNo());
                    if (i == 1)
                        truckInfo += String.format("%,12.0f\n\n", this.getVolume());
                    else if (i % 2 == 1)
                        truckInfo += "\n";
                    i++;
                }
                truckInfo += new String(new char[(int) Math.ceil(4 - this.items.size() / 2)]).replace('\0', '\n');
            } else {
                for (InvoiceItemModel mItem : this.items) {
                    truckInfo += (truckInfo != "" ? "\n" : "") + String.format("%12s %,15.0f / %,-15.0f %s  %,12.0f\n", mItem.getTruckNo(), mItem.getStartNumber(), mItem.getEndNumber(), mItem.isReturn() ? "HT" : " ", mItem.getVolume());

                }

                if (this.items.size() < 6)
                    truckInfo += new String(new char[]{27, 51, (char) (72 - this.items.size() * 12)}) + "\n";// new String(new char[]{27, 100, (char) (12 - this.items.size() * 3)});

            }
            double realAmount = this.getTotalGallon();
            double saleAmount = this.getSaleAmount();
            double volume = this.getTotalVolume();
            double weight = this.getTotalWeight();
            double taxRate = this.getTaxRate();
            double vatAmount = this.getVatAmount();
            double totalSaleAmount = this.getTotalAmount();
            double price = this.getPrice();
            double temperature = this.getTemperature();
            double density = this.getDensity();

            double totalT = volume;
            double totalP = temperature * volume;

            //String customerName = VNCharacterUtils.removeAccent(this.getCustomerName());
            //String address = VNCharacterUtils.removeAccent(this.getAddress());
            //String inWords = VNCharacterUtils.removeAccent(this.getInWords());

            String customerName = this.getCustomerName();
            String address = this.getCustomerAddress();
            String inWords = this.getInWords();

            String[] names = split(customerName, 48);
            String[] addresses = split(address, 52);

            String[] words = split(inWords, 48);

            Queue<String> dataToPrint;


            FMSApplication app = FMSApplication.getApplication();
            SimpleDateFormat format = new SimpleDateFormat("       dd           MM         yyyy\n");
            SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
            dataToPrint = new LinkedList<String>(Arrays.asList(
                    new String(new char[]{27, 51, 40}),
                    "\n",
                    new String(new char[]{27, 51, 49}),
                    format.format(this.getInvoiceDate()) + "\n",
                    new String(new char[]{27, 51, 15}),
                    String.format("                         %s\n", this.qualityNo == null ? "" : this.qualityNo),
                    new String(new char[]{27, 51, 34}),
                    String.format("                       %s / %s                     %s\n", timeformat.format((this.getStartTime())), timeformat.format(this.getEndTime()), VNCharacterUtils.removeAccent(this.getProductName())),
                    new String(new char[]{27, 51, 12}),
                    truckInfo,
                    new String(new char[]{27, 51, 11}),
                    String.format("                 %s\n", names[0]),
                    new String(new char[]{27, 51, 11}),
                    String.format(" %s\n", names[1]),
                    new String(new char[]{27, 51, 12}),
                    String.format("                 %s\n", this.taxCode == null ? "" : this.taxCode),
                    new String(new char[]{27, 51, 11}),
                    String.format("            %s\n", addresses[0]),
                    new String(new char[]{27, 51, 11}),
                    String.format(" %s\n", addresses[1]),
                    new String(new char[]{27, 51, 14}),
                    String.format("%31s%34s\n", this.getAircraftType(), this.getAircraftCode()),
                    String.format("%31s%34s\n", this.getFlightCodePrint(), this.getRouteName()),
                    new String(new char[]{27, 51, 14}),
                    String.format("%31.2f%34.4f\n", temperature, density),

                    String.format("%,31.0f%,34.0f\n", volume, weight),


                    String.format(currency == RefuelItemData.CURRENCY.VND  ?
                            (unit ==0? "%,31.0f%,26.0f VND/%s\n": "%,31.0f%,27.0f VND/%s\n")
                            : (unit ==0?"%,31.0f%,26.2f USD/%s\n":"%,31.0f%,27.2f USD/%s\n")
                            , realAmount, price, unit==0?"USG":"KG"),
                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,34.0f VND\n"
                            : "%,34.2f USD\n", this.getSaleAmount()),
                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,20.0f%%%,40.0f VND\n"
                            : "%,20.0f%%%,40.2f USD\n", taxRate * 100, vatAmount),

                    String.format(currency == RefuelItemData.CURRENCY.VND ?
                            "%,34.0f VND                     \n"
                            : "%,34.2f USD                    \n", totalSaleAmount),
                    new String(new char[]{27, 51, 11}),
                    String.format("                  %s\n", words[0]),
                    String.format(" %s\n", words[1])


            ));

            return dataToPrint;
        } catch (Exception ex) {
            Logger.appendLog("Error creating invoice text: " + ex.getMessage());
            return null;
        }
    }


}
