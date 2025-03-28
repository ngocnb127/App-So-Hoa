package com.megatech.fms.helpers;

import androidx.databinding.InverseMethod;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {

    private static Date truncateTime(Date d)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime( d);
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        return cal.getTime();
    }

    public static String formatDate(Date date, String pattern)
    {
        if (date == null) return "";

        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date) ;
    }
    public static String formatDatePlus(Date date, String pattern)
    {
        if (date == null) return "";

        Date today = truncateTime(new Date());

        Date day = truncateTime(date);

        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date) + (day.after(today)?"+":"" );
    }
    public static String getTime(Date date) {
        if (date == null) return "";
        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
        return format.format(date);
    }

    @InverseMethod("getTime")
    public static Date setTime(String time) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm");
            return format.parse(time);
        } catch (ParseException ex) {
            return null;
        }
    }

    public static String getDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        return format.format(date);
    }

    @InverseMethod("getDate")
    public static Date setDate(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
            return format.parse(date);
        } catch (ParseException ex) {
            return null;
        }
    }

    public static Date getCurrentDate () {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MILLISECOND,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        return cal.getTime();

    }
    public static Date getNextDate (Date d) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MILLISECOND,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        cal.add(Calendar.DATE,1);
        return cal.getTime();

    }
}
