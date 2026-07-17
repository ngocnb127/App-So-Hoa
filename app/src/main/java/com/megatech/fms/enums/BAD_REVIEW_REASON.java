package com.megatech.fms.enums;

import androidx.room.TypeConverter;

public enum BAD_REVIEW_REASON {
    WORST (1),
    BAD (2),
    NEUTRAL (3),
    GOOD (4),
    BEST (5);

     BAD_REVIEW_REASON(int i)
    {
        val = i;
    }
    private int val;

    @TypeConverter
    public static Integer getInt(BAD_REVIEW_REASON status) {
        return status.val;
    }

}
