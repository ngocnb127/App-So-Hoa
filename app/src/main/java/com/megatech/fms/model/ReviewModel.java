package com.megatech.fms.model;



import com.megatech.fms.enums.REVIEW_RATE;

import java.util.Date;

public class ReviewModel extends BaseModel{

    private String flightUniqueId;
    private int flightId;
    private Date reviewDate;
    private REVIEW_RATE rate = REVIEW_RATE.NEUTRAL;

    private int badReviewReason;

    public int getBadReviewReason() {
        return badReviewReason;
    }

    public void setBadReviewReason(int badReviewReason) {
        this.badReviewReason = badReviewReason;
    }

    private String otherReason;

    public String getFlightUniqueId() {
        return flightUniqueId;
    }

    public void setFlightUniqueId(String flightUniqueId) {
        this.flightUniqueId = flightUniqueId;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public Date getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(Date reviewDate) {
        this.reviewDate = reviewDate;
    }

    public REVIEW_RATE getRate() {
        return rate;
    }

    public void setRate(REVIEW_RATE rate) {
        this.rate = rate;
    }

    public String getOtherReason() {
        return otherReason;
    }

    public void setOtherReason(String otherReason) {
        this.otherReason = otherReason;
    }

    public boolean isWorst()
    {
        return rate == REVIEW_RATE.WORST;
    }

    public boolean isBad()
    {
        return rate == REVIEW_RATE.BAD;
    }
    public boolean isNeutral()
    {
        return rate == REVIEW_RATE.NEUTRAL;
    }
    public boolean isGood()
    {
        return rate == REVIEW_RATE.GOOD;
    }
    public boolean isBest()
    {
        return rate == REVIEW_RATE.BEST;
    }

    private boolean badReason(int i) {
        return (badReviewReason & i) > 0;
    }
    public void setBadReason(int i) {
        badReviewReason = badReviewReason | i;
    }
    public void clearBadReason(int i) {
        badReviewReason = (badReviewReason | i) & ~i;
    }
    public boolean isBadReason1()
    {
        return badReason(1);
    }

    public boolean isBadReason2()
    {
        return badReason(2);
    }
    public boolean isBadReason4()
    {
        return badReason(4);
    }
    public boolean isBadReason8()
    {
        return badReason(8);
    }
    public boolean isBadReason16()
    {
        return badReason(16);
    }
    public boolean isBadReason32()
    {
        return badReason(32);
    }

    private String imagePath;

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    private String imageString;

    public String getImageString() {
        return imageString;
    }

    public void setImageString(String imageString) {
        this.imageString = imageString;
    }

    private boolean isCaptured;

    public boolean isCaptured() {
        return isCaptured;
    }

    public void setCaptured(boolean captured) {
        isCaptured = captured;
    }
}

