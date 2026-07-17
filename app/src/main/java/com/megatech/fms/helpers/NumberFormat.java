package com.megatech.fms.helpers;

import android.widget.EditText;

import androidx.databinding.InverseMethod;

import java.text.DecimalFormat;
import java.text.ParseException;

public class NumberFormat {
    public static String format(String pattern, double number)
    {
        DecimalFormat format = new DecimalFormat(pattern);
        return format.format(number);
    }

    @InverseMethod("format")
    public static double reverse(String pattern, String value) {
        double val = 0.0;
        DecimalFormat format = new DecimalFormat(pattern);
        try {
            return format.parse(value).doubleValue();
        } catch (ParseException ex) {
            return val;
        }
    }
}
