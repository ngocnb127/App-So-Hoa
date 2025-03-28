package com.megatech.fms.model;

import androidx.annotation.NonNull;

import com.google.type.DateTime;

import java.util.Date;

public class CheckTrucksModel extends BaseModel {
    public Date DateCreated;
    public Date DateDeleted;
    public Integer UserDeletedId;
    public Integer UserCreatedId;
    public Integer UserUpdatedId;
    public Date DateActived;
    public Boolean  IsActive;
    public Integer UserActivedId;

    public Date DateActived2;
    public Boolean  IsActive2;
    public Integer UserActived2Id ;

    public Date DateActived3;
    public Boolean  IsActive3;
    public Integer UserActived3Id;
    public Date Hours;
    public String KmNumber;

    public Integer AirportId;

    public Integer TruckId;

    public Integer ShiftId;

    public Integer DriverId;
    public Integer OperatorId;

    public String Result1;
    public Boolean  HasNote1;
    public String Note1;

    public String Result2;
    public Boolean  HasNote2;
    public String Note2;

    public String Result3;
    public Boolean  HasNote3;
    public String Note3;

    public String Result4;
    public Boolean  HasNote4;
    public String Note4;

    public String Result5;
    public Boolean  HasNote5;
    public String Note5;

    public String Result6;
    public Boolean  HasNote6;
    public String Note6;

    public String Result7;
    public Boolean  HasNote7;
    public String Note7;

    public String Result8;
    public Boolean  HasNote8;
    public String Note8;

    public String Result9;
    public Boolean  HasNote9;
    public String Note9;

    public String Result10;
    public Boolean  HasNote10;
    public String Note10;

    public String Result11;
    public Boolean  HasNote11;
    public String Note11;

    public String Result12;
    public Boolean  HasNote12;
    public String Note12;

    public String Result13;
    public Boolean  HasNote13;
    public String Note13;

    public String Result14;
    public Boolean  HasNote14;
    public String Note14;

    public String Result15;
    public Boolean  HasNote15;
    public String Note15;

    public String Result16;
    public Boolean  HasNote16;
    public String Note16;

    public String Result17;
    public Boolean  HasNote17;
    public String Note17;

    public String Result18;
    public Boolean  HasNote18;
    public String Note18;

    public String Result19;
    public Boolean  HasNote19;
    public String Note19;

    public String Result20;
    public Boolean  HasNote20;
    public String Note20;

    public String Result21;
    public Boolean  HasNote21;
    public String Note21;

    public String Result22;
    public Boolean  HasNote22 ;
    public String Note22;

    public String Result23;
    public Boolean  HasNote23;
    public String Note23;

    public String Result24 ;
    public Boolean  HasNote24;
    public String Note24;

    public String Result25;
    public Boolean  HasNote25;
    public String Note25;

    public String Result26;
    public Boolean  HasNote26;
    public String Note26;

    public String Result27;
    public Boolean  HasNote27;
    public String Note27;

    public String Result28;
    public Boolean  HasNote28;
    public String Note28;

    public String Result29;
    public Boolean  HasNote29;
    public String Note29;

    public String Result30;
    public Boolean  HasNote30;
    public String Note30;

    public String Result32;
    public Boolean  HasNote32;
    public String Note32;

    public String Result31;
    public Boolean  HasNote31;
    public String Note31;

    public String Result33;
    public Boolean  HasNote33;
    public String Note33;

    public String Result34;
    public Boolean  HasNote34;
    public String Note34;

    public String Result35;
    public Boolean  HasNote35;
    public String Note35;

    public String Result36;
    public Boolean  HasNote36;
    public String Note36;

    public String Result37;
    public Boolean  HasNote37;
    public String Note37;

    public String Result38;
    public Boolean  HasNote38;
    public String Note38;

    public String Result39;
    public Boolean  HasNote39;
    public String Note39;

    public String Result40;
    public Boolean  HasNote40;
    public String Note40;

    public String Result41;
    public Boolean  HasNote41;
    public String Note41;

    public String Result42;
    public Boolean  HasNote42;
    public String Note42;

    public String Note43;

    public String Result44;
    public Boolean  HasNote44;
    public String Note44;

    public String Result45;
    public Boolean  HasNote45;
    public String Note45;

    public String Result46;
    public Boolean  HasNote46;
    public String Note46;

    public String Result47;
    public Boolean  HasNote47;
    public String Note47;

    public String Result48;
    public Boolean  HasNote48;
    public String Note48;

    public String Result49;
    public Boolean  HasNote49;
    public String Note49;

    public String Result50;
    public Boolean  HasNote50;
    public String Note50;

    private String operatorName;
    private String userCreatedName;
    private String userActivedName;

    private String userActived2Name;

    private String userActived3Name;

    private String airportName;
    private String truckNo;
    private String shiftName;
    public static class ResultModel{
        public String Name;

        public ResultModel(String Name) {
            this.Name = Name; // Gán giá trị cho biến Name
        }

        // Getter and Setter methods for Name
        public String getName() {
            return Name;
        }

        public void setName(String name) {
            this.Name = name;
        }
        public String toString()
        {
            return this.Name;
        }
    }
    public Date getDateCreated() {
        return DateCreated;
    }

    public void setDateCreated(Date DateCreated) {
        this.DateCreated = DateCreated;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getUserCreatedName() {
        return userCreatedName;
    }

    public void setUserCreatedName(String userCreatedName) {
        this.userCreatedName = userCreatedName;
    }

    public String getUserActivedName() {
        return userActivedName;
    }

    public void setUserActivedName(String userActivedName) {
        this.userActivedName = userActivedName;
    }
    public String getUserActived2Name() {
        return userActived2Name;
    }

    public void setUserActived2Name(String userActived2Name) {
        this.userActived2Name = userActived2Name;
    }
    public String getUserActived3Name() {
        return userActived3Name;
    }

    public void setUserActived3Name(String userActived3Name) {
        this.userActived3Name = userActived3Name;
    }


    public String getAirportName() {
        return airportName;
    }

    public void setAirportName(String airportName) {
        this.airportName = airportName;
    }

    public String getTruckNo() {
        return truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }
    public Date getDateDeleted() {
        return DateDeleted;
    }

    public void setDateDeleted(Date DateDeleted) {
        this.DateDeleted = DateDeleted;
    }

    public Integer getUserDeletedId() {
        return UserDeletedId;
    }

    public void setUserDeletedId(Integer UserDeletedId) {
        this.UserDeletedId = UserDeletedId;
    }

    public Integer getUserCreatedId() {
        return UserCreatedId;
    }

    public void setUserCreatedId(Integer UserCreatedId) {
        this.UserCreatedId = UserCreatedId;
    }

    public Integer getUserUpdatedId() {
        return UserUpdatedId;
    }

    public void setUserUpdatedId(Integer UserUpdatedId) {
        this.UserUpdatedId = UserUpdatedId;
    }

    public Date getDateActived() {
        return DateActived;
    }

    public void setDateActived(Date DateActived) {
        this.DateActived = DateActived;
    }

    public Boolean getIsActive() {
        return IsActive;
    }

    public void setIsActive(Boolean IsActive) {
        this.IsActive = IsActive;
    }

    public Integer getUserActivedId() {
        return UserActivedId;
    }

    public void setUserActivedId(Integer UserActivedId) {
        this.UserActivedId = UserActivedId;
    }

    public Date getDateActived2() {
        return DateActived2;
    }

    public void setDateActived2(Date DateActived2) {
        this.DateActived2 = DateActived2;
    }

    public Boolean getIsActive2() {
        return IsActive2;
    }

    public void setIsActive2(Boolean IsActive2) {
        this.IsActive2 = IsActive2;
    }

    public Integer getUserActived2Id() {
        return UserActived2Id;
    }

    public void setUserActived2Id(Integer UserActived2Id) {
        this.UserActived2Id = UserActived2Id;
    }

    public Date getDateActived3() {
        return DateActived3;
    }

    public void setDateActived3(Date DateActived3) {
        this.DateActived3 = DateActived3;
    }

    public Boolean getIsActive3() {
        return IsActive3;
    }

    public void setIsActive3(Boolean IsActive3) {
        this.IsActive3 = IsActive3;
    }

    public Integer getUserActived3Id() {
        return UserActived3Id;
    }

    public void setUserActived3Id(Integer UserActived3Id) {
        this.UserActived3Id = UserActived3Id;
    }

    public Date getHours() {
        return Hours;
    }

    public void setHours(Date Hours) {
        this.Hours = Hours;
    }

    public String getKmNumber() {
        return KmNumber;
    }

    public void setKmNumber(String KmNumber) {
        this.KmNumber = KmNumber;
    }

    public Integer getAirportId() {
        return AirportId;
    }

    public void setAirportId(Integer AirportId) {
        this.AirportId = AirportId;
    }

    public Integer getTruckId() {
        return TruckId;
    }

    public void setTruckId(Integer TruckId) {
        this.TruckId = TruckId;
    }

    public Integer getShiftId() {
        return ShiftId;
    }

    public void setShiftId(Integer ShiftId) {
        this.ShiftId = ShiftId;
    }

    public Integer getDriverId() {
        return DriverId;
    }

    public void setDriverId(Integer DriverId) {
        this.DriverId = DriverId;
    }

    public Integer getOperatorId() {
        return OperatorId;
    }

    public void setOperatorId(Integer OperatorId) {
        this.OperatorId = OperatorId;
    }

    public String getResult1() {
        return Result1;
    }

    public void setResult1(String Result1) {
        this.Result1 = Result1;
    }

    public Boolean getHasNote1() {
        return HasNote1;
    }

    public void setHasNote1(Boolean HasNote1) {
        this.HasNote1 = HasNote1;
    }

    public String getNote1() {
        return Note1;
    }

    public void setNote1(String Note1) {
        this.Note1 = Note1;
    }

    public String getResult2() {
        return Result2;
    }

    public void setResult2(String Result2) {
        this.Result2 = Result2;
    }

    public Boolean getHasNote2() {
        return HasNote2;
    }

    public void setHasNote2(Boolean HasNote2) {
        this.HasNote2 = HasNote2;
    }

    public String getNote2() {
        return Note2;
    }

    public void setNote2(String Note2) {
        this.Note2 = Note2;
    }

    public String getResult3() {
        return Result3;
    }

    public void setResult3(String Result3) {
        this.Result3 = Result3;
    }

    public Boolean getHasNote3() {
        return HasNote3;
    }

    public void setHasNote3(Boolean HasNote3) {
        this.HasNote3 = HasNote3;
    }

    public String getNote3() {
        return Note3;
    }

    public void setNote3(String Note3) {
        this.Note3 = Note3;
    }

    public String getResult4() {
        return Result4;
    }

    public void setResult4(String Result4) {
        this.Result4 = Result4;
    }

    public Boolean getHasNote4() {
        return HasNote4;
    }

    public void setHasNote4(Boolean HasNote4) {
        this.HasNote4 = HasNote4;
    }

    public String getNote4() {
        return Note4;
    }

    public void setNote4(String Note4) {
        this.Note4 = Note4;
    }

    public String getResult5() {
        return Result5;
    }

    public void setResult5(String Result5) {
        this.Result5 = Result5;
    }

    public Boolean getHasNote5() {
        return HasNote5;
    }

    public void setHasNote5(Boolean HasNote5) {
        this.HasNote5 = HasNote5;
    }

    public String getNote5() {
        return Note5;
    }

    public void setNote5(String Note5) {
        this.Note5 = Note5;
    }

    public String getResult6() {
        return Result6;
    }

    public void setResult6(String Result6) {
        this.Result6 = Result6;
    }

    public Boolean getHasNote6() {
        return HasNote6;
    }

    public void setHasNote6(Boolean HasNote6) {
        this.HasNote6 = HasNote6;
    }

    public String getNote6() {
        return Note6;
    }

    public void setNote6(String Note6) {
        this.Note6 = Note6;
    }

    public String getResult7() {
        return Result7;
    }

    public void setResult7(String Result7) {
        this.Result7 = Result7;
    }

    public Boolean getHasNote7() {
        return HasNote7;
    }

    public void setHasNote7(Boolean HasNote7) {
        this.HasNote7 = HasNote7;
    }

    public String getNote7() {
        return Note7;
    }

    public void setNote7(String Note7) {
        this.Note7 = Note7;
    }

    public String getResult8() {
        return Result8;
    }

    public void setResult8(String Result8) {
        this.Result8 = Result8;
    }

    public Boolean getHasNote8() {
        return HasNote8;
    }

    public void setHasNote8(Boolean HasNote8) {
        this.HasNote8 = HasNote8;
    }

    public String getNote8() {
        return Note8;
    }

    public void setNote8(String Note8) {
        this.Note8 = Note8;
    }

    public String getResult9() {
        return Result9;
    }

    public void setResult9(String Result9) {
        this.Result9 = Result9;
    }

    public Boolean getHasNote9() {
        return HasNote9;
    }

    public void setHasNote9(Boolean HasNote9) {
        this.HasNote9 = HasNote9;
    }

    public String getNote9() {
        return Note9;
    }

    public void setNote9(String Note9) {
        this.Note9 = Note9;
    }

    public String getResult10() {
        return Result10;
    }

    public void setResult10(String Result10) {
        this.Result10 = Result10;
    }

    public Boolean getHasNote10() {
        return HasNote10;
    }

    public void setHasNote10(Boolean HasNote10) {
        this.HasNote10 = HasNote10;
    }

    public String getNote10() {
        return Note10;
    }

    public void setNote10(String Note10) {
        this.Note10 = Note10;
    }

    public String getResult11() {
        return Result11;
    }

    public void setResult11(String Result11) {
        this.Result11 = Result11;
    }

    public Boolean getHasNote11() {
        return HasNote11;
    }

    public void setHasNote11(Boolean HasNote11) {
        this.HasNote11 = HasNote11;
    }

    public String getNote11() {
        return Note11;
    }

    public void setNote11(String Note11) {
        this.Note11 = Note11;
    }

    public String getResult12() {
        return Result12;
    }

    public void setResult12(String Result12) {
        this.Result12 = Result12;
    }

    public Boolean getHasNote12() {
        return HasNote12;
    }

    public void setHasNote12(Boolean HasNote12) {
        this.HasNote12 = HasNote12;
    }

    public String getNote12() {
        return Note12;
    }

    public void setNote12(String Note12) {
        this.Note12 = Note12;
    }

    public String getResult13() {
        return Result13;
    }

    public void setResult13(String Result13) {
        this.Result13 = Result13;
    }

    public Boolean getHasNote13() {
        return HasNote13;
    }

    public void setHasNote13(Boolean HasNote13) {
        this.HasNote13 = HasNote13;
    }

    public String getNote13() {
        return Note13;
    }

    public void setNote13(String Note13) {
        this.Note13 = Note13;
    }

    public String getResult14() {
        return Result14;
    }

    public void setResult14(String Result14) {
        this.Result14 = Result14;
    }

    public Boolean getHasNote14() {
        return HasNote14;
    }

    public void setHasNote14(Boolean HasNote14) {
        this.HasNote14 = HasNote14;
    }

    public String getNote14() {
        return Note14;
    }

    public void setNote14(String Note14) {
        this.Note14 = Note14;
    }

    public String getResult15() {
        return Result15;
    }

    public void setResult15(String Result15) {
        this.Result15 = Result15;
    }

    public Boolean getHasNote15() {
        return HasNote15;
    }

    public void setHasNote15(Boolean HasNote15) {
        this.HasNote15 = HasNote15;
    }

    public String getNote15() {
        return Note15;
    }

    public void setNote15(String Note15) {
        this.Note15 = Note15;
    }

    public String getResult16() {
        return Result16;
    }

    public void setResult16(String Result16) {
        this.Result16 = Result16;
    }

    public Boolean getHasNote16() {
        return HasNote16;
    }

    public void setHasNote16(Boolean HasNote16) {
        this.HasNote16 = HasNote16;
    }

    public String getNote16() {
        return Note16;
    }

    public void setNote16(String Note16) {
        this.Note16 = Note16;
    }

    public String getResult17() {
        return Result17;
    }

    public void setResult17(String Result17) {
        this.Result17 = Result17;
    }

    public Boolean getHasNote17() {
        return HasNote17;
    }

    public void setHasNote17(Boolean HasNote17) {
        this.HasNote17 = HasNote17;
    }

    public String getNote17() {
        return Note17;
    }

    public void setNote17(String Note17) {
        this.Note17 = Note17;
    }

    public String getResult18() {
        return Result18;
    }

    public void setResult18(String Result18) {
        this.Result18 = Result18;
    }

    public Boolean getHasNote18() {
        return HasNote18;
    }

    public void setHasNote18(Boolean HasNote18) {
        this.HasNote18 = HasNote18;
    }

    public String getNote18() {
        return Note18;
    }

    public void setNote18(String Note18) {
        this.Note18 = Note18;
    }

    public String getResult19() {
        return Result19;
    }

    public void setResult19(String Result19) {
        this.Result19 = Result19;
    }

    public Boolean getHasNote19() {
        return HasNote19;
    }

    public void setHasNote19(Boolean HasNote19) {
        this.HasNote19 = HasNote19;
    }

    public String getNote19() {
        return Note19;
    }

    public void setNote19(String Note19) {
        this.Note19 = Note19;
    }

    public String getResult20() {
        return Result20;
    }

    public void setResult20(String Result20) {
        this.Result20 = Result20;
    }

    public Boolean getHasNote20() {
        return HasNote20;
    }

    public void setHasNote20(Boolean HasNote20) {
        this.HasNote20 = HasNote20;
    }

    public String getNote20() {
        return Note20;
    }

    public void setNote20(String Note20) {
        this.Note20 = Note20;
    }

    public String getResult21() {
        return Result21;
    }

    public void setResult21(String Result21) {
        this.Result21 = Result21;
    }

    public Boolean getHasNote21() {
        return HasNote21;
    }

    public void setHasNote21(Boolean HasNote21) {
        this.HasNote21 = HasNote21;
    }

    public String getNote21() {
        return Note21;
    }

    public void setNote21(String Note21) {
        this.Note21 = Note21;
    }

    public String getResult22() {
        return Result22;
    }

    public void setResult22(String Result22) {
        this.Result22 = Result22;
    }

    public Boolean getHasNote22() {
        return HasNote22;
    }

    public void setHasNote22(Boolean HasNote22) {
        this.HasNote22 = HasNote22;
    }

    public String getNote22() {
        return Note22;
    }

    public void setNote22(String Note22) {
        this.Note22 = Note22;
    }

    public String getResult23() {
        return Result23;
    }

    public void setResult23(String Result23) {
        this.Result23 = Result23;
    }

    public Boolean getHasNote23() {
        return HasNote23;
    }

    public void setHasNote23(Boolean HasNote23) {
        this.HasNote23 = HasNote23;
    }

    public String getNote23() {
        return Note23;
    }

    public void setNote23(String Note23) {
        this.Note23 = Note23;
    }

    public String getResult24() {
        return Result24;
    }

    public void setResult24(String Result24) {
        this.Result24 = Result24;
    }

    public Boolean getHasNote24() {
        return HasNote24;
    }

    public void setHasNote24(Boolean HasNote24) {
        this.HasNote24 = HasNote24;
    }

    public String getNote24() {
        return Note24;
    }

    public void setNote24(String Note24) {
        this.Note24 = Note24;
    }

    public String getResult25() {
        return Result25;
    }

    public void setResult25(String Result25) {
        this.Result25 = Result25;
    }

    public Boolean getHasNote25() {
        return HasNote25;
    }

    public void setHasNote25(Boolean HasNote25) {
        this.HasNote25 = HasNote25;
    }

    public String getNote25() {
        return Note25;
    }

    public void setNote25(String Note25) {
        this.Note25 = Note25;
    }

    public String getResult26() {
        return Result26;
    }

    public void setResult26(String Result26) {
        this.Result26 = Result26;
    }

    public Boolean getHasNote26() {
        return HasNote26;
    }

    public void setHasNote26(Boolean HasNote26) {
        this.HasNote26 = HasNote26;
    }

    public String getNote26() {
        return Note26;
    }

    public void setNote26(String Note26) {
        this.Note26 = Note26;
    }

    public String getResult27() {
        return Result27;
    }

    public void setResult27(String Result27) {
        this.Result27 = Result27;
    }

    public Boolean getHasNote27() {
        return HasNote27;
    }

    public void setHasNote27(Boolean HasNote27) {
        this.HasNote27 = HasNote27;
    }

    public String getNote27() {
        return Note27;
    }

    public void setNote27(String Note27) {
        this.Note27 = Note27;
    }

    public String getResult28() {
        return Result28;
    }

    public void setResult28(String Result28) {
        this.Result28 = Result28;
    }

    public Boolean getHasNote28() {
        return HasNote28;
    }

    public void setHasNote28(Boolean HasNote28) {
        this.HasNote28 = HasNote28;
    }

    public String getNote28() {
        return Note28;
    }

    public void setNote28(String Note28) {
        this.Note28 = Note28;
    }

    public String getResult29() {
        return Result29;
    }

    public void setResult29(String Result29) {
        this.Result29 = Result29;
    }

    public Boolean getHasNote29() {
        return HasNote29;
    }

    public void setHasNote29(Boolean HasNote29) {
        this.HasNote29 = HasNote29;
    }

    public String getNote29() {
        return Note29;
    }

    public void setNote29(String Note29) {
        this.Note29 = Note29;
    }

    public String getResult30() {
        return Result30;
    }

    public void setResult30(String Result30) {
        this.Result30 = Result30;
    }

    public Boolean getHasNote30() {
        return HasNote30;
    }

    public void setHasNote30(Boolean HasNote30) {
        this.HasNote30 = HasNote30;
    }

    public String getNote30() {
        return Note30;
    }

    public void setNote30(String Note30) {
        this.Note30 = Note30;
    }

    public String getResult31() {
        return Result31;
    }

    public void setResult31(String Result31) {
        this.Result31 = Result31;
    }

    public Boolean getHasNote31() {
        return HasNote31;
    }

    public void setHasNote31(Boolean HasNote31) {
        this.HasNote31 = HasNote31;
    }

    public String getNote31() {
        return Note31;
    }

    public void setNote31(String Note31) {
        this.Note31 = Note31;
    }

    public String getResult32() {
        return Result32;
    }

    public void setResult32(String Result32) {
        this.Result32 = Result32;
    }

    public Boolean getHasNote32() {
        return HasNote32;
    }

    public void setHasNote32(Boolean HasNote32) {
        this.HasNote32 = HasNote32;
    }

    public String getNote32() {
        return Note32;
    }

    public void setNote32(String Note32) {
        this.Note32 = Note32;
    }

    public String getResult33() {
        return Result33;
    }

    public void setResult33(String Result33) {
        this.Result33 = Result33;
    }

    public Boolean getHasNote33() {
        return HasNote33;
    }

    public void setHasNote33(Boolean HasNote33) {
        this.HasNote33 = HasNote33;
    }

    public String getNote33() {
        return Note33;
    }

    public void setNote33(String Note33) {
        this.Note33 = Note33;
    }

    public String getResult34() {
        return Result34;
    }

    public void setResult34(String Result34) {
        this.Result34 = Result34;
    }

    public Boolean getHasNote34() {
        return HasNote34;
    }

    public void setHasNote34(Boolean HasNote34) {
        this.HasNote34 = HasNote34;
    }

    public String getNote34() {
        return Note34;
    }

    public void setNote34(String Note34) {
        this.Note34 = Note34;
    }

    public String getResult35() {
        return Result35;
    }

    public void setResult35(String Result35) {
        this.Result35 = Result35;
    }

    public Boolean getHasNote35() {
        return HasNote35;
    }

    public void setHasNote35(Boolean HasNote35) {
        this.HasNote35 = HasNote35;
    }

    public String getNote35() {
        return Note35;
    }

    public void setNote35(String Note35) {
        this.Note35 = Note35;
    }

    public String getResult36() {
        return Result36;
    }

    public void setResult36(String Result36) {
        this.Result36 = Result36;
    }

    public Boolean getHasNote36() {
        return HasNote36;
    }

    public void setHasNote36(Boolean HasNote36) {
        this.HasNote36 = HasNote36;
    }

    public String getNote36() {
        return Note36;
    }

    public void setNote36(String Note36) {
        this.Note36 = Note36;
    }

    public String getResult37() {
        return Result37;
    }

    public void setResult37(String Result37) {
        this.Result37 = Result37;
    }

    public Boolean getHasNote37() {
        return HasNote37;
    }

    public void setHasNote37(Boolean HasNote37) {
        this.HasNote37 = HasNote37;
    }

    public String getNote37() {
        return Note37;
    }

    public void setNote37(String Note37) {
        this.Note37 = Note37;
    }

    public String getResult38() {
        return Result38;
    }

    public void setResult38(String Result38) {
        this.Result38 = Result38;
    }

    public Boolean getHasNote38() {
        return HasNote38;
    }

    public void setHasNote38(Boolean HasNote38) {
        this.HasNote38 = HasNote38;
    }

    public String getNote38() {
        return Note38;
    }

    public void setNote38(String Note38) {
        this.Note38 = Note38;
    }

    public String getResult39() {
        return Result39;
    }

    public void setResult39(String Result39) {
        this.Result39 = Result39;
    }

    public Boolean getHasNote39() {
        return HasNote39;
    }

    public void setHasNote39(Boolean HasNote39) {
        this.HasNote39 = HasNote39;
    }

    public String getNote39() {
        return Note39;
    }

    public void setNote39(String Note39) {
        this.Note39 = Note39;
    }

    public String getResult40() {
        return Result40;
    }

    public void setResult40(String Result40) {
        this.Result40 = Result40;
    }

    public Boolean getHasNote40() {
        return HasNote40;
    }

    public void setHasNote40(Boolean HasNote40) {
        this.HasNote40 = HasNote40;
    }

    public String getNote40() {
        return Note40;
    }

    public void setNote40(String Note40) {
        this.Note40 = Note40;
    }

    public String getResult41() {
        return Result41;
    }

    public void setResult41(String Result41) {
        this.Result41 = Result41;
    }

    public Boolean getHasNote41() {
        return HasNote41;
    }

    public void setHasNote41(Boolean HasNote41) {
        this.HasNote41 = HasNote41;
    }

    public String getNote41() {
        return Note41;
    }

    public void setNote41(String Note41) {
        this.Note41 = Note41;
    }

    public String getResult42() {
        return Result42;
    }

    public void setResult42(String Result42) {
        this.Result42 = Result42;
    }

    public Boolean getHasNote42() {
        return HasNote42;
    }

    public void setHasNote42(Boolean HasNote42) {
        this.HasNote42 = HasNote42;
    }

    public String getNote42() {
        return Note42;
    }

    public void setNote42(String Note42) {
        this.Note42 = Note42;
    }

    public String getNote43() {
        return Note43;
    }

    public void setNote43(String Note43) {
        this.Note43 = Note43;
    }

    public String getResult44() {
        return Result44;
    }

    public void setResult44(String Result44) {
        this.Result44 = Result44;
    }

    public Boolean getHasNote44() {
        return HasNote44;
    }

    public void setHasNote44(Boolean HasNote44) {
        this.HasNote44 = HasNote44;
    }

    public String getNote44() {
        return Note44;
    }

    public void setNote44(String Note44) {
        this.Note44 = Note44;
    }

    public String getResult45() {
        return Result45;
    }

    public void setResult45(String Result45) {
        this.Result45 = Result45;
    }

    public Boolean getHasNote45() {
        return HasNote45;
    }

    public void setHasNote45(Boolean HasNote45) {
        this.HasNote45 = HasNote45;
    }

    public String getNote45() {
        return Note45;
    }

    public void setNote45(String Note45) {
        this.Note45 = Note45;
    }

    public String getResult46() {
        return Result46;
    }

    public void setResult46(String Result46) {
        this.Result46 = Result46;
    }

    public Boolean getHasNote46() {
        return HasNote46;
    }

    public void setHasNote46(Boolean HasNote46) {
        this.HasNote46 = HasNote46;
    }

    public String getNote46() {
        return Note46;
    }

    public void setNote46(String Note46) {
        this.Note46 = Note46;
    }

    public String getResult47() {
        return Result47;
    }

    public void setResult47(String Result47) {
        this.Result47 = Result47;
    }

    public Boolean getHasNote47() {
        return HasNote47;
    }

    public void setHasNote47(Boolean HasNote47) {
        this.HasNote47 = HasNote47;
    }

    public String getNote47() {
        return Note47;
    }

    public void setNote47(String Note47) {
        this.Note47 = Note47;
    }

    public String getResult48() {
        return Result48;
    }

    public void setResult48(String Result48) {
        this.Result48 = Result48;
    }

    public Boolean getHasNote48() {
        return HasNote48;
    }

    public void setHasNote48(Boolean HasNote48) {
        this.HasNote48 = HasNote48;
    }

    public String getNote48() {
        return Note48;
    }

    public void setNote48(String Note48) {
        this.Note48 = Note48;
    }

    public String getResult49() {
        return Result49;
    }

    public void setResult49(String Result49) {
        this.Result49 = Result49;
    }

    public Boolean getHasNote49() {
        return HasNote49;
    }

    public void setHasNote49(Boolean HasNote49) {
        this.HasNote49 = HasNote49;
    }

    public String getNote49() {
        return Note49;
    }

    public void setNote49(String Note49) {
        this.Note49 = Note49;
    }

    public String getResult50() {
        return Result50;
    }

    public void setResult50(String Result50) {
        this.Result50 = Result50;
    }

    public Boolean getHasNote50() {
        return HasNote50;
    }

    public void setHasNote50(Boolean HasNote50) {
        this.HasNote50 = HasNote50;
    }

    public String getNote50() {
        return Note50;
    }

    public void setNote50(String Note50) {
        this.Note50 = Note50;
    }
}
