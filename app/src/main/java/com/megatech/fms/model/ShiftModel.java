package com.megatech.fms.model;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ShiftModel extends BaseModel {
    private String name;
    private Date startTime;
    private Date endTime;
    private int airportId;
    private Date preStart;
    private Date nextEnd;
    private ShiftModel prevShift;
    private boolean selected;
    private ShiftModel nextShift;

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public ShiftModel getPrevShift() {
        return prevShift;
    }

    public void setPrevShift(ShiftModel prevShift) {
        this.prevShift = prevShift;
    }

    public ShiftModel getNextShift() {
        return nextShift;
    }

    public void setNextShift(ShiftModel nextShift) {
        this.nextShift = nextShift;
    }

    public Date getPreStart() {
        return preStart;
    }

    public void setPreStart(Date preStart) {
        this.preStart = preStart;
    }

    public Date getNextEnd() {
        return nextEnd;
    }

    public void setNextEnd(Date nextEnd) {
        this.nextEnd = nextEnd;
    }

    public ShiftModel() {
        name = "N/A";
        Calendar c = Calendar.getInstance();
        c.set(0, 0, 0, 0, 0);
        startTime = c.getTime();
        c.set(0, 0, 0, 23, 59);
        endTime = c.getTime();

    }

    public static ShiftModel fromJson(String shiftJson) {
        return gson.fromJson(shiftJson, ShiftModel.class);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getAirportId() {
        return airportId;
    }

    public void setAirportId(int airportId) {
        this.airportId = airportId;
    }

    @NonNull
    @Override
    public String toString() {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");

        return String.format("%s - %s - %s", this.name, formatter.format(this.startTime), formatter.format(this.endTime));
    }

    public String toJson() {
        return gson.toJson(this);
    }
}
