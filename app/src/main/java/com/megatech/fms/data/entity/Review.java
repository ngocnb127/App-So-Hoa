package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.ReviewModel;

import java.util.Date;

@Entity
public class Review extends BaseEntity{

    private int flightId;
    private String flightUniqueId;
    private Date reviewTime;
    private int rate;
    private String note;

    public static Review fromModel(ReviewModel model)
    {
        String json = model.toJson();
        Review item = gson.fromJson(json, Review.class);
        item.jsonData = json;

        return  item;

    }

    public ReviewModel toModel()
    {
        return gson.fromJson(this.jsonData,ReviewModel.class);
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime) {
        this.reviewTime = reviewTime;
    }

    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getFlightUniqueId() {
        return flightUniqueId;
    }

    public void setFlightUniqueId(String flightUniqueId) {
        this.flightUniqueId = flightUniqueId;
    }
}
