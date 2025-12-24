package com.megatech.fms.model;

import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.enums.RETURN_UNIT;
import com.megatech.fms.exceptions.InvalidRefuelTimeException;
import com.megatech.fms.helpers.DateUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

public class ReceiptModel extends BaseModel {

    public static ReceiptModel createReceipt(List<RefuelItemData> refuels) {
        return createReceipt(refuels,null, false, null,false);
    }
    public static ReceiptModel createReceipt(List<RefuelItemData> refuels, String oldNumber, boolean createNew) {
        return createReceipt(refuels, null, false,oldNumber, createNew);
    }

    public static ReceiptModel createReceipt(List<RefuelItemData> refuels, String[] replacedReceipts, boolean isReturn, String oldNumber, boolean createNew) {

        // =================================================
        // 🔴 VALIDATE TIME CHO TỪNG REFUEL ITEM (>= 3 PHÚT)
        // =================================================
        for (RefuelItemData item : refuels) {

            Date start = item.getStartTime();
            Date end   = item.getEndTime();

            if (start == null || end == null) {
                throw new InvalidRefuelTimeException(
                        "Thời gian bắt đầu và kết thúc không hợp lệ, vui lòng kiểm tra lại"
                );
            }

            long diffMs = end.getTime() - start.getTime();
            long diffMinutes = diffMs / (60 * 1000);

            if (diffMinutes < 3) {
                throw new InvalidRefuelTimeException(
                        "Thời gian bắt đầu và kết thúc không hợp lệ, vui lòng kiểm tra lại"
                );
            }
        }

        // =================================================
        // ⬇️ TỪ ĐÂY TRỞ ĐI MỚI ĐƯỢC PHÉP TẠO RECEIPT
        // =================================================


        ReceiptModel model = null;

        TruckModel  setting  = FMSApplication.getApplication().getSetting();

        String receiptCode = setting.getReceiptCode();
        int receiptCount = setting.getReceiptCount();
        receiptCount ++;




        if (refuels.size() > 0) {
            RefuelItemData refuel = refuels.get(0);
            //String number = refuel.getTruckNo().substring(0, 3) + refuel.getTruckNo().substring(refuel.getTruckNo().length() - 2);
            //number += DateUtils.formatDate(new Date(),"yyMMddHHmm");
            String number = refuel.getReceiptNumber();
            if (number == null || number.isEmpty() || (refuel.getReceiptCount() > 0 && createNew))
                number = receiptCode + String.format("%4s",Integer.toString(receiptCount, 36)).replace(" ","0").toUpperCase();


            String data = gson.toJson(refuel);
            model = gson.fromJson(data, ReceiptModel.class);
            model.uniqueId = UUID.randomUUID().toString();
            model.setId(0);
            model.setLocalId(0);
            if (oldNumber !=null && !createNew){
                model.setNumber(oldNumber);
                model.setReuse(true);

                //model.setReplaceNumber(oldNumber);
            }
            else {
                model.setNumber(number);

            }
            model.setReplaceNumber(oldNumber);
            model.setReplacedId(replacedReceipts);

            model.setGallon(0);
            model.setVolume(0);
            model.setWeight(0);
            model.isFHS = BuildConfig.FHS;
            model.isThermal = BuildConfig.THERMAL_PRINTER;
            model.setRefuelMethod(model.isFHS ? REFUEL_METHOD.FHS : REFUEL_METHOD.REFUELER);
            //model.setDate(new Date());
            model.customerId = refuel.getAirlineId();
            model.customerName = refuel.getInvoiceNameCharter().trim();
            if (model.customerName.isEmpty())
                model.customerName = refuel.getAirlineModel().getName().trim();
            model.customerCode = refuel.getAirlineModel().getCode().trim();
            model.customerAddress = refuel.getAirlineModel().getAddress().trim();
            model.taxCode = refuel.getAirlineModel().getTaxCode().trim();
            model.customerType = refuel.getAirlineModel().isInternational()?1:0;
            model.productName = refuel.getAirlineModel().getProductName();

            model.isReturn = isReturn;
            model.setFlightType(refuel.isInternational()?1:0);

            for (RefuelItemData item : refuels) {
                if (item.getProductId() == 6) {
                    model.setProductId(item.getProductId());
                    model.setPName(item.getPName());
                    model.setPCode(item.getPCode());
                    break; // chỉ cần lấy bản ghi đầu tiên có productId = 6
                }
            }

            //int receiptCount = 1;
            int id = 0;
            for (RefuelItemData itemData : refuels) {



                addItem(model, itemData);
                if (itemData.getStartTime().compareTo(model.getStartTime()) < 0) {
                    model.setStartTime(itemData.getStartTime());
                }

                if (itemData.getEndTime().compareTo(model.getEndTime()) > 0) {
                    model.setEndTime(itemData.getEndTime());
                }
//                if (itemData.getReceiptCount() > 0 && createNew)
//                    receiptCount = Math.max(receiptCount, itemData.getReceiptCount() + 1);

                if (itemData.getWeightNote() !=null && !itemData.getWeightNote().isEmpty())
                    model.techLog = Double.parseDouble(itemData.getWeightNote());
                id = Math.max(id, itemData.getId());
            }

            model.setDate(model.getEndTime());
            //number += DateUtils.formatDate(model.getDate(), "yyMMddHHmm") + receiptCount;

            //number += String.format()+ receiptCount;
            if (model.isReturn)
                number += "HT";

            model.setNumber(number);
//            model.setProductId(refuel.getProductId());
//            model.setPName(refuel.getPName());
//            model.setPCode(refuel.getPCode());
        }

        return model;
    }

    private static void addItem(ReceiptModel model, RefuelItemData itemData) {
        if (model.items == null)
            model.items = new ArrayList<>();

        if (!model.isReturn || itemData.getReturnAmount() > 0) {
            ReceiptItemModel itemModel = gson.fromJson(itemData.toJson(), ReceiptItemModel.class);
            itemModel.setRefuelItemId(itemData.getUniqueId());
            itemModel.setRefuelId(itemData.getId());
            itemModel.setTemperature(itemData.getManualTemperature());
            itemModel.setQualityNo(itemData.getQualityNo());
            itemModel.setWeight(itemData.getWeight());
            itemModel.setDriverId(itemData.getDriverId());
            itemModel.setOperatorId(itemData.getOperatorId());
            if (itemData.getReturnAmount() > 0) {

                double returnA = itemData.getReturnAmount();
                double returnV = Math.round(returnA / itemData.getDensity());
                double returnG = Math.round(returnV / RefuelItemData.GALLON_TO_LITTER);
                RETURN_UNIT unit = itemData.getReturnUnit();
                if (unit == RETURN_UNIT.GALLON)
                {
                    returnG = itemData.getReturnAmount();
                }
                returnV = Math.round(returnG * GALLON_TO_LITTER);
                returnA = Math.round(returnV * itemData.getDensity());


                if (model.isReturn) {
                    itemModel.setGallon(returnG);
                    itemModel.setVolume(returnV);
                    //itemModel.setWeight(returnA);
                    itemModel.setEndNumber(itemModel.getStartNumber() + itemModel.getGallon());
                    model.setReturnAmount(model.getReturnAmount() + returnA);
                } else {
                    itemModel.setGallon(itemModel.getGallon() - returnG);
                    itemModel.setVolume(itemModel.getVolume() - returnV);
                    //itemModel.setWeight(itemModel.getWeight() - returnA);
                    itemModel.setStartNumber(itemModel.getStartNumber() + returnG);
                }

            }

            model.setGallon(model.getGallon() + itemModel.getGallon());
            model.setVolume(model.getVolume() + itemModel.getVolume());
            model.setWeight(model.getWeight() + itemModel.getWeight());
            //model.setReturnAmount(model.getReturnAmount() + itemModel.getReturnAmount());


            model.items.add(itemModel);
        }
    }

    public String createPrintText() {


        String LS_18 = new String(new char[]{27, 51, 18});
        String LS_24 = new String(new char[]{27, 51, 24});
        String LS_DEFAULT = new String(new char[]{27, 50});
        StringBuilder builder = new StringBuilder();
        builder.append("The Seller: " + user.getInvoiceName() + "\n");
        builder.append("Tax code: " + user.getTaxCode() + "\n");
        builder.append("Address: " + user.getAddress() + "\n");
        builder.append("------------------------------------------------------------------\n");
        builder.append("                    FUEL DELIVERY RECEIPT                         \n");
        builder.append("                    Phiếu Giao Nhiên Liệu                         \n");
        builder.append("------------------------------------------------------------------\n");
        builder.append(String.format("No.: %-15s            (%s)\n", this.number, DateUtils.formatDate(this.date, "dd/MM/yyyy")));
        builder.append(LS_18);
        builder.append(String.format("Buyer: %s\n", this.customerName));
        builder.append(LS_DEFAULT);
        builder.append("\n");
        builder.append(String.format("A/C Type         : %-16s A/C reg     : %s\n", this.aircraftType, this.aircraftCode));
        builder.append(String.format("Flight No.       : %-16s Route       : %s\n", this.flightCode, this.routeName));
        builder.append(String.format("Cert No.         : %-16s Product Name: %s\n", this.qualityNo, (this.pCode == null ? "JET A-1" : this.pCode)));
        builder.append(String.format("Start Time       : %-16s End Time    : %s\n", DateUtils.formatDate(this.startTime, "HH:mm dd/MM/yyyy"), DateUtils.formatDate(this.endTime, "HH:mm dd/MM/yyyy")));
        builder.append("Refueling Method : " + (isFHS ? "FHS" : "Refueler") + "\n");
        builder.append("------------------------------------------------------------------\n");
        builder.append("| # |  Refueler No.     |   Temp.   |   USG  |  Lit |    Kg   |\n");
        builder.append(LS_18);
        builder.append("|   | Start/End Meter   | Density   |        |         |         |\n");
        builder.append(LS_DEFAULT);
        builder.append("------------------------------------------------------------------\n");
        int i = 1;
        for (ReceiptItemModel itemModel : this.items) {
            builder.append(String.format("|%2d |%-19s|%8.2f oC|%8.0f|%9.0f|%9.0f|\n", i++, itemModel.getTruckNo(), itemModel.getTemperature(), itemModel.getGallon(), itemModel.getVolume(), itemModel.getWeight()));
            builder.append(LS_18);
            builder.append(String.format("|   |%9.0f/%-9.0f|%6.4f kg/l|        |         |         |\n", itemModel.getStartNumber(), itemModel.getEndNumber(), itemModel.getDensity()));
            builder.append(LS_DEFAULT);
            builder.append("------------------------------------------------------------------\n");
        }
        builder.append(LS_18);
        builder.append(String.format("|   | Total                         |%8.0f|%9.0f|%9.0f|\n", this.gallon, this.volume, this.weight));
        builder.append(LS_DEFAULT);
        builder.append("------------------------------------------------------------------\n");
        builder.append("           Buyer                            Seller        \n");
        builder.append("  (Signature and full name)      (Signature and full name)     \n");
        return builder.toString();

    }

    UserInfo user = FMSApplication.getApplication().getUser();
    TruckModel setting = FMSApplication.getApplication().getSetting();
    public String createThermalText() {
        StringBuilder builder = new StringBuilder();
        int height = 80;
        String LEFT_INDENT =setting.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ520? "^LH130,0\n": "^LH000,0\n";
        builder.append("^XA");
        builder.append("^CWZ,E:OPENSANS-RE.TTF^FS  \n" +
                LEFT_INDENT +
                "^CI28");
        builder.append("^CFZ,25\n" +
                "^FO0," + height + "^FB600,2,0,C,0^FD" + user.getInvoiceName().toUpperCase() + "^FS\n" +
                "^CFZ,40\n" +
                "^FO0," + (height + 50) + "^FB600,1,0,C,0^FDFUEL DELIVERY RECEIPT^FS\n" +
                "^FO0," + (height + 90) + "^FB600,1,0,C,0^FD(PHIẾU GIAO NHIÊN LIỆU)^FS");
        height += 140;
        builder.append("^CFZ,20\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDReceipt No. : " + this.number + "^FS\n" +
                "^FO0," + (height + 20)+ "^FB600,1,0,C,0^FD" + DateUtils.formatDate(this.date, "dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");
        builder.append("^CFZ,27");
        height += 50;
        int nameLines = (int) Math.ceil(this.customerName.length() *1.0 / 18);
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDBuyer ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320," + nameLines + ",0,L,0^FD" + this.customerName + "^FS");

        height += nameLines * 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDFlight No.        ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + flightCode + "^FS\n" +
                "^FO0," + (height + 30) + "^FB250,1,0,L,0^FDRoute        ^FS\n" +
                "^FO240," + (height + 30) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 30) + "^FB320,1,0,R,0^FD" + routeName + "^FS\n" +
                "^FO0," + (height + 60) + "^FB250,1,0,L,0^FDA/C Type        ^FS\n" +
                "^FO240," + (height + 60) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 60) + "^FB320,1,0,R,0^FD" + aircraftType + "^FS\n" +
                "^FO0," + (height + 90) + "^FB250,1,0,L,0^FDA/C Reg        ^FS\n" +
                "^FO240," + (height + 90) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 90) + "^FB320,1,0,R,0^FD" + aircraftCode + "^FS\n" +
                "^FO0," + (height + 120) + "^FB250,1,0,L,0^FDCert No.        ^FS\n" +
                "^FO240," + (height + 120) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 120) + "^FB320,1,0,R,0^FD" + qualityNo + "^FS\n" +
                "^FO0," + (height + 150) + "^FB250,1,0,L,0^FDStart Time        ^FS\n" +
                "^FO240," + (height + 150) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 150) + "^FB320,1,0,R,0^FD" + DateUtils.formatDate(startTime, "HH:mm dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 180) + "^FB250,1,0,L,0^FDEnd Time        ^FS\n" +
                "^FO240," + (height + 180) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 180) + "^FB320,1,0,R,0^FD" + DateUtils.formatDate(endTime, "HH:mm dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 210) + "^FB250,1,0,L,0^FDProduct Name        ^FS\n" +
                "^FO240," + (height + 210) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 210) + "^FB320,1,0,R,0^FD"+(pCode == null ? "JET A-1" : pCode)+"^FS\n" +
                "^FO0," + (height + 240) + "^FB250,1,0,L,0^FDRefueling Method^FS\n" +
                "^FO240," + (height + 240) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 240) + "^FB320,1,0,R,0^FD" + (isFHS ? "FHS" : "Refueler") + "^FS  \n" +
                "^FO0," + (height + 270) + "^GB700,1,3^FS");
        height = height + 270 + 10;
        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDDETAIL^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");

        height = height + 50;
        builder.append("^CFZ,30");
        int i = 1;

        for (ReceiptItemModel item : items) {
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FD#        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + i + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDRefueler No.        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + item.getTruckNo() + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDStart Meter        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getStartNumber()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDEnd Meter        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getEndNumber()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDTemp.(°C)       ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.2f", item.getTemperature()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDDensity(kg/l)      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.4f", item.getDensity()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDUSG      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getGallon()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDLiter      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getVolume()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDKg      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getWeight()) + "^FS");
            height += 30;
            builder.append(" \"^FO0," + height + "^GB700,1,3^FS\"");
            height += 10;
            i++;

        }


        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDTOTAL^FS");
        height += 40;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        builder.append("^CFZ,30\n");
        height += 10;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDUSG      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", gallon) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDLiter      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", volume) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDKg      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", weight) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 10;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FDBuyer^FS");
        //print signature
        if (signaturePath != null) {
            height += 20;
            builder.append("^FO150," + height + "^XGE:BUYER.GRF,1,1^FS");
        }
        height += 200;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FDSeller^FS");

        if (sellerSignaturePath != null) {
            height += 20;
            builder.append("^FO150," + height + "^XGE:SELLER.GRF,1,1^FS");
        }

//        String qrData = String.format(Locale.US,
//                "{\"number\":\"%s\",\"gallon\":%.0f,\"volume\":%.0f,\"weight\":%.0f}",
//                this.number, gallon, volume, weight);
//
//// Tăng khoảng cách trước khi đặt QR
//        height += 40;
//
//// ^BQN: QR Code (Model 2), module size (ví dụ 6–8)
//// ^FDLA: L = error correction thấp, A = auto-encode
//        int qrModuleSize = 7; // tuỳ máy in, 6–10 là đẹp
//        builder.append("^FO0,").append(String.valueOf(height))
//                .append("^BQN,2,").append(String.valueOf(qrModuleSize)).append("\n")
//                .append("^FDLA,").append(qrData).append("^FS\n");
//
//// Tăng chiều cao label cho đủ chỗ QR
//        height += 200;

        builder.append("^PQ1");
        builder.append("^LH0,0\n" );
        builder.append("^XZ");



        builder.insert(3,"^LL"+(height+200));
        return builder.toString();

    }

    public String createReturnThermalText() {
        StringBuilder builder = new StringBuilder();
        int height = 80;
        String LEFT_INDENT =setting.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ520? "^LH130,0\n": "^LH000,0\n";
        builder.append("^XA");
        builder.append("^CWZ,E:OPENSANS-RE.TTF^FS  \n" +
               LEFT_INDENT +
                "^CI28");
        builder.append("^CFZ,25\n" +
                "^FO0," + height + "^FB600,2,0,C,0^FD" + user.getInvoiceName().toUpperCase() + "^FS\n" +
                "^CFZ,40\n" +
                "^FO0," + (height + 50) + "^FB600,1,0,C,0^FDFUEL RETURNING FORM^FS\n" +
                "^FO0," + (height + 90) + "^FB600,1,0,C,0^FD(PHIẾU HOÀN TRẢ NHIÊN LIỆU)^FS");
        height += 140;
        builder.append("^CFZ,20\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDReceipt No. : " + this.number + "^FS\n" +
                "^FO0," + (height + 20)+ "^FB600,1,0,C,0^FD" + DateUtils.formatDate(this.date, "dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");
        builder.append("^CFZ,30");
        height += 50;
        int nameLines = (int) Math.ceil(this.customerName.length() *1.0 / 18);
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDBuyer ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320," + nameLines + ",0,L,0^FD" + this.customerName + "^FS");

        height += nameLines * 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDFlight No.        ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + flightCode + "^FS\n" +
                "^FO0," + (height + 30) + "^FB250,1,0,L,0^FDRoute        ^FS\n" +
                "^FO240," + (height + 30) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 30) + "^FB320,1,0,R,0^FD" + routeName + "^FS\n" +
                "^FO0," + (height + 60) + "^FB250,1,0,L,0^FDA/C Type        ^FS\n" +
                "^FO240," + (height + 60) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 60) + "^FB320,1,0,R,0^FD" + aircraftType + "^FS\n" +
                "^FO0," + (height + 90) + "^FB250,1,0,L,0^FDA/C Reg        ^FS\n" +
                "^FO240," + (height + 90) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 90) + "^FB320,1,0,R,0^FD" + aircraftCode + "^FS\n" +
                "^FO0," + (height + 120) + "^FB250,1,0,L,0^FDCert No.        ^FS\n" +
                "^FO240," + (height + 120) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 120) + "^FB320,1,0,R,0^FD" + qualityNo + "^FS\n" +
                "^FO0," + (height + 150) + "^FB250,1,0,L,0^FDStart Time        ^FS\n" +
                "^FO240," + (height + 150) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 150) + "^FB320,1,0,R,0^FD" + DateUtils.formatDate(startTime, "HH:mm dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 180) + "^FB250,1,0,L,0^FDEnd Time        ^FS\n" +
                "^FO240," + (height + 180) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 180) + "^FB320,1,0,R,0^FD" + DateUtils.formatDate(endTime, "HH:mm dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 210) + "^FB250,1,0,L,0^FDProduct Name        ^FS\n" +
                "^FO240," + (height + 210) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 210) + "^FB320,1,0,R,0^FD"+(pCode == null ? "JET A-1" : pCode)+"^FS\n" +
                "^FO0," + (height + 240) + "^FB250,1,0,L,0^FDRefueling Method^FS\n" +
                "^FO240," + (height + 240) + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + (height + 240) + "^FB320,1,0,R,0^FD" + (isFHS ? "FHS" : "Refueler") + "^FS  \n" +
                "^FO0," + (height + 270) + "^GB700,1,3^FS");
        height = height + 270 + 10;
        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDDETAIL^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");

        height = height + 50;
        builder.append("^CFZ,30");
        int i = 1;

        for (ReceiptItemModel item : items) {
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FD#        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + i + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDRefueler No.        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + item.getTruckNo() + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDStart Meter        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getStartNumber()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDEnd Meter        ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getEndNumber()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDTemp.(°C)       ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.2f", item.getTemperature()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDDensity(kg/l)      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.4f", item.getDensity()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDUSG      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getGallon()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDLiter      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getVolume()) + "^FS");
            height += 30;
            builder.append("^FO0," + height + "^FB250,1,0,L,0^FDKg      ^FS\n" +
                    "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                    "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", item.getWeight()) + "^FS");
            height += 30;
            builder.append(" \"^FO0," + height + "^GB700,1,3^FS\"");
            height += 10;
            i++;

        }


        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDTOTAL^FS");
        height += 40;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        builder.append("^CFZ,30\n");
        height += 10;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDUSG      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", gallon) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDLiter      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", volume) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDKg      ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320,1,0,R,0^FD" + String.format("%.0f", weight) + "^FS");
        height += 30;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 10;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FDBuyer^FS");
        //print signature
        if (signaturePath != null) {
            height += 20;
            builder.append("^FO150," + height + "^XGE:BUYER.GRF,1,1^FS");
        }
        height += 200;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FDSeller^FS");

        if (sellerSignaturePath != null) {
            height += 20;
            builder.append("^FO150," + height + "^XGE:SELLER.GRF,1,1^FS");
        }
        builder.append("^PQ1");
        builder.append("^LH0,0\n" );
        builder.append("^XZ");
        builder.insert(3,"^LL"+(height+200));
        return builder.toString();

    }

    public String createReturnText() {


        String LS_18 = new String(new char[]{27, 51, 18});
        String LS_24 = new String(new char[]{27, 51, 24});
        String LS_DEFAULT = new String(new char[]{27, 50});
        StringBuilder builder = new StringBuilder();
        builder.append("The Seller: " + user.getInvoiceName() + "\n");
        builder.append("Tax code: " + user.getTaxCode() + "\n");
        builder.append("Address: " + user.getAddress() + "\n");
        builder.append("------------------------------------------------------------------\n");
        builder.append("                    FUEL RETURNING FORM                           \n");
        builder.append("                    Phiếu Hoàn Trả Nhiên Liệu                     \n");
        builder.append("------------------------------------------------------------------\n");
        builder.append(String.format("No.: %-15s            (%s)\n", this.number, DateUtils.formatDate(this.date, "dd/MM/yyyy")));
        builder.append(LS_18);
        builder.append(String.format("Buyer: %s\n", this.customerName));
        builder.append(LS_DEFAULT);
        builder.append("\n");
        builder.append(String.format("A/C Type         : %-16s A/C reg     : %s\n", this.aircraftType, this.aircraftCode));
        builder.append(String.format("Flight No.       : %-16s Route       : %s\n", this.flightCode, this.routeName));
        builder.append(String.format("Cert No.         : %-16s Product Name: %s\n", this.qualityNo, (this.pCode == null ? "JET A-1" : this.pCode)));
        builder.append(String.format("Start Time       : %-16s End Time    : %s\n", DateUtils.formatDate(this.startTime, "HH:mm dd/MM/yyyy"), DateUtils.formatDate(this.endTime, "HH:mm dd/MM/yyyy")));
        builder.append("Refueling Method : " + (isFHS ? "FHS" : "Refueler") + "\n");
        builder.append("------------------------------------------------------------------\n");
        builder.append("| # |  Refueler No.     |   Temp.   |   USG  |  Lit |    Kg   |\n");
        builder.append(LS_18);
        builder.append("|   | Start/End Meter   | Density   |        |         |         |\n");
        builder.append(LS_DEFAULT);
        builder.append("------------------------------------------------------------------\n");
        int i = 1;
        for (ReceiptItemModel itemModel : this.items) {
            builder.append(String.format("|%2d |%-19s|%8.2f oC|%8.0f|%9.0f|%9.0f|\n", i++, itemModel.getTruckNo(), itemModel.getTemperature(), itemModel.getGallon(), itemModel.getVolume(), itemModel.getWeight()));
            builder.append(LS_18);
            builder.append(String.format("|   |%9.0f/%-9.0f|%6.4f kg/l|        |         |         |\n", itemModel.getStartNumber(), itemModel.getEndNumber(), itemModel.getDensity()));
            builder.append(LS_DEFAULT);
            builder.append("------------------------------------------------------------------\n");
        }
        builder.append(LS_18);
        builder.append(String.format("|   | Total                         |%8.0f|%9.0f|%9.0f|\n", this.gallon, this.volume, this.weight));
        builder.append(LS_DEFAULT);
        builder.append("------------------------------------------------------------------\n");
        builder.append("           Buyer                            Seller        \n");
        builder.append("  (Signature and full name)      (Signature and full name)     \n");
        return builder.toString();
    }

    private String number;
    private Date date;

    private int customerId;
    private String customerName;
    private String customerCode;
    private String customerAddress;
    private String taxCode;
    private String productName;
    private int productId;
    private String pName;
    private String pCode;


    private int flightId;
    private String flightCode;
    private String aircraftCode;
    private String aircraftType;
    private String routeName;

    private String qualityNo;
    private Date startTime;
    private Date endTime;


    private double gallon;
    private double volume;
    private double weight;

    private double returnAmount;
    private String defuelingNo;

    private boolean invoiceSplit;
    private double splitAmount;

    private String replaceNumber;

    public String getReplaceNumber() {
        return replaceNumber;
    }

    public void setReplaceNumber(String replaceNumber) {
        this.replaceNumber = replaceNumber;
    }

    List<ReceiptItemModel> items;

    public static ReceiptModel fromJson(String data) {

        return gson.fromJson(data, ReceiptModel.class);
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    private int customerType;

    public int getCustomerType() {
        return customerType;
    }

    public void setCustomerType(int customerType) {
        this.customerType = customerType;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getPName() {
        return pName;
    }

    public void setPName(String pName) {
        this.pName = pName;
    }

    public String getPCode() {
        return pCode;
    }

    public void setPCode(String pCode) {
        this.pCode = pCode;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    private int flightType;

    public int getFlightType() {
        return flightType;
    }

    public void setFlightType(int flightType) {
        this.flightType = flightType;
    }

    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getQualityNo() {
        return qualityNo;
    }

    public void setQualityNo(String qualityNo) {
        this.qualityNo = qualityNo;
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

    public double getGallon() {
        return gallon;
    }

    public void setGallon(double gallon) {
        this.gallon = gallon;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getReturnAmount() {
        return returnAmount;
    }

    public void setReturnAmount(double returnAmount) {
        this.returnAmount = returnAmount;
    }

    public String getDefuelingNo() {
        return defuelingNo;
    }

    public void setDefuelingNo(String defuelingNo) {
        this.defuelingNo = defuelingNo;
    }

    public boolean isInvoiceSplit() {
        return invoiceSplit;
    }

    public void setInvoiceSplit(boolean invoiceSplit) {
        this.invoiceSplit = invoiceSplit;
    }

    public double getSplitAmount() {
        return splitAmount;
    }

    public void setSplitAmount(double splitAmount) {
        this.splitAmount = splitAmount;
    }

    public List<ReceiptItemModel> getItems() {
        return items;
    }

    public void setItems(List<ReceiptItemModel> items) {
        this.items = items;
    }

    private String pdfImageString;

    public String getPdfImageString() {
        return pdfImageString;
    }

    public void setPdfImageString(String pdfImageString) {
        this.pdfImageString = pdfImageString;
    }

    private String pdfPath;
    private String signaturePath;
    private String sellerSignaturePath;

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getSignaturePath() {
        return signaturePath;
    }

    public void setSignaturePath(String signaturePath) {
        this.signaturePath = signaturePath;
    }

    public String getSellerSignaturePath() {
        return sellerSignaturePath;
    }

    public void setSellerSignaturePath(String sellerSignaturePath) {
        this.sellerSignaturePath = sellerSignaturePath;
    }

    private String signImageString;

    public String getSignImageString() {
        return signImageString;
    }

    public void setSignImageString(String signImageString) {
        this.signImageString = signImageString;
    }
    private String sellerImageString;

    public String getSellerImageString() {
        return sellerImageString;
    }

    public void setSellerImageString(String sellerImageString) {
        this.sellerImageString = sellerImageString;
    }

    private boolean printed = false;

    private boolean captured = false;

    public boolean isPrinted() {
        return printed;
    }

    public void setPrinted(boolean printed) {
        this.printed = printed;
    }

    public boolean isCaptured() {
        return captured;
    }

    public void setCaptured(boolean captured) {
        this.captured = captured;
    }

    private REFUEL_METHOD refuelMethod;

    public REFUEL_METHOD getRefuelMethod() {
        return refuelMethod;
    }

    public void setRefuelMethod(REFUEL_METHOD refuelMethod) {
        this.refuelMethod = refuelMethod;
    }

    public enum REFUEL_METHOD {
        REFUELER,
        FHS
    }

    private boolean isThermal;

    public boolean isThermal() {
        return isThermal;
    }

    public void setThermal(boolean thermal) {
        isThermal = thermal;
    }

    private boolean isFHS;

    public boolean isFHS() {
        return isFHS;
    }

    public void setFHS(boolean FHS) {
        isFHS = FHS;
    }

    private boolean isReturn;

    public boolean isReturn() {
        return isReturn;
    }

    public void setReturn(boolean aReturn) {
        isReturn = aReturn;
    }

    private boolean isCancelled;
    private String cancelReason;

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    private double techLog;

    public double getTechLog() {
        return techLog;
    }

    public void setTechLog(double techLog) {
        this.techLog = techLog;
    }

    private boolean isReuse;

    public boolean isReuse() {
        return isReuse;
    }

    public void setReuse(boolean reuse) {
        isReuse = reuse;
    }

    private String[] replacedId;

    public String[] getReplacedId() {
        return replacedId;
    }

    public void setReplacedId(String[] replacedId) {
        this.replacedId = replacedId;
    }
}
