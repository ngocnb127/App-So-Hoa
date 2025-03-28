package com.megatech.fms.helpers;


//import com.google.android.gms.common.util.Strings;

public class NumberConvert
{
    private static String UpperFirstCharacter(String sentence)
    {
        if (sentence.length() > 0)
        {
            return sentence.substring(0,1).toUpperCase() + sentence.substring(1);
        }
        else
        {
            return sentence;
        }
    }
    /**
     Converts number to sentence

     @param number
     @return
     */
    public static String NumberToSentence(double number)
    {
        return UpperFirstCharacter(NumberConvert.NumberToSentence(number, false, ""));
    }
    /**
     Converts number to sentence

     @param number
     @return
     */
    public static String NumberToSentence(double number, boolean english)
    {
        return NumberConvert.NumberToSentence(number, english, "");
    }
    /**
     Converts number to sentence

     @param number
     @param english
     @return
     */
    public static String NumberToSentence(double number, boolean english, String suffix)
    {
        //tri - add negative prefix
        String negative = "";
        if (number < 0)
        {
            negative = english ? "Negative " : "Âm ";
            number = -number;
        }

        //tri //////////////
        String suffix1 = "";

        suffix1 = english ? "per cent" : "phần trăm";
        suffix = suffix.trim();
        if (english)
        {
            if (suffix.equals("USD") || suffix.equals("VND"))
            {
                suffix1 = "cent(s)";
            }
            else
            {
                if (suffix.equals("USD"))
                {
                    suffix = "USD";
                }
            }
        }


        if (suffix.equals("VND"))
        {
            suffix = "đồng";
        }

        suffix1 = suffix.equals("USD") ? "cents" : "xu";


        NumberText Dic = english ? NumberText.English : NumberText.VietNamese;

        number = (double)Math.round(number*100)/100;

        String tmp = String.format("%,.2f",number);

        int lnAt = tmp.indexOf(".");

        String _ChLe = String.format("%2d",(int)Math.round(100*( number % 1.0))).replace(' ','0'); // RIGHT(allt(str(number%1,3,2)),2); //&&Iif(AT(".",tmp)#0,Substr(tmp,lnAt+1,2),"00")
        String _ChChan = String.format("%d",(int)Math.floor(number)).trim(); //&&Left(tmp,Iif(lnAt#0,lnAt-1,Len(tmp)))

        int socap3 = _ChChan.length() / 3;
        int numberdu = _ChChan.length() % 3;

        String chuoi = "";

        if (numberdu > 0)
        {
            chuoi = Break(_ChChan.substring(0, numberdu), Dic, false, true);
            //set3 = (int.Parse(_ChChan.Substring(0, numberdu)) > 1);
            //set1 = (int.Parse(_ChChan.Substring(0, numberdu)) < 100 && int.Parse(_ChChan.Substring(0, numberdu)) > 0);
        }
        chuoi = chuoi + (numberdu != 0 ? " " + Dic.donvi[(socap3 > 3 ? (socap3 % 3 == 0 ? 2 : socap3 % 3) : socap3)] + " " : " ");
        for (int i = socap3; i >= 1; i--)
        {
            boolean _Last = (i == 1 && _ChLe.equals("00"));
            boolean _First = (i == socap3);
            int num = Integer.parseInt(_ChChan.substring((socap3 - i) * 3 + numberdu, (socap3 - i) * 3 + numberdu + 3));
            if (num > 0)
            {
                String Ch_tach = Break(_ChChan.substring( (socap3 - i) * 3 + numberdu, (socap3 - i) * 3 + numberdu+ 3), Dic, _Last, false).trim();
                chuoi = chuoi.trim() + (num < 100 && num > 0 ? " " + Dic.chu[0] + " " + Dic.tram : "") + " " + Ch_tach + " " + Dic.donvi[(i > 3 ? (i % 3 == 0 ? 3 : i % 3 - 1) : i - 1)] + (i % 3 == 1 && i > 3 ? Dic.donvi[3] : "");
            }
        }

        String _Chuoile = "";
        if (!_ChLe.equals("00"))
        {
            _Chuoile = Break(_ChLe, Dic, true, true);
            if (!_Chuoile.equals(""))
            {
                _Chuoile = english ? "and " : "và " + _Chuoile.trim() + " " + suffix1;
            }
        }
        //chuoi = Upper(Left(Allt(chuoi),1))+Lower(Subs(Allt(chuoi),2));

        chuoi = chuoi.trim() + " " + suffix + (_Chuoile!="" ? (" " + _Chuoile) : ""); //&&+ Iif(_ChLe#"00",Iif(VietNamese,"vaø ","and ")+_Chuoile,"")
        return UpperFirstCharacter(negative + chuoi.trim());
    }

    private static class NumberText
    {
        public NumberText()
        {
        }
        public NumberText(String[] _chu, String[] _chuc, String[] _donvi, String _tram)
        {
            chu = _chu;
            chuc = _chuc;
            donvi = _donvi;
            tram = _tram;
        }
        public NumberText(String[] _chu, String[] _chuc, String[] _donvi, String _tram, String _le)
        {
            chu = _chu;
            chuc = _chuc;
            donvi = _donvi;
            tram = _tram;
            le = _le;
        }
        public String[] chu;
        public String[] chuc;
        public String[] donvi;
        public String tram = "";
        public String le = "";


        public static NumberText VietNamese = new NumberText(new String[]{"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "mười", "muời một", "mười hai", "mười ba", "mười bốn", "mười lăm", "muời sáu", "mười bảy", "mười tám", "mười chín", "mốt", "lăm"}, new String[]{"hai mươi", "ba mươi", "bốn mươi", "năm mươi", "sáu mươi", "bảy mươi", "tám mươi", "chín mươi"}, new String[]{"", "nghìn", "triệu", "tỷ", "nghìn ", "triệu ", "tỷ "}, "trăm", "lẻ");
        public static NumberText English = new NumberText(new String[] {" ", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eightteen", "nineteen", "one", "five"}, new String[] {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"}, new String[] {"", "thousand", "million", "billion", "thousand ", "million ", "billion "}, " hundred");

    }

    private static String Break(String text, NumberText Dic)
    {
        return NumberConvert.Break(text, Dic, false, false);
    }

    private static String Break(String text, NumberText Dic, boolean last, boolean first)
    {

        String tmp = "";

        if (text.trim().length() != 0)
        {
            text = String.format("%1$" + 3 + "s", text).replace(' ', '0');
            int dv = Integer.parseInt(text.substring(2, 3));
            int ch = Integer.parseInt(text.substring(1, 2));
            int tr = Integer.parseInt(text.substring(0, 1));

            //tmp = (tr == 0) ? "" : (" " + Dic.chu[tr] + " " + Dic.tram + " " + ((ch == 0 && dv == 0) ? "" : (last ? "and " : "")));
            tmp = (tr == 0) ? "" : (" " + Dic.chu[tr] + " " + Dic.tram + " ");
            if (ch != 1)
            {
                if (ch != 0)
                {
                    tmp += Dic.chuc[ch - 2];
                    if (dv != 0)
                    {
                        tmp += " " + (dv == 1 ? Dic.chu[20] : (dv == 5 ? Dic.chu[21] : Dic.chu[dv]));
                    }

                }
                else
                {
                    if (dv != 0 && !first)
                    {
                        tmp += Dic.le;
                    }
                    else
                    {
                        tmp += " ";
                    }

                    if (dv != 0)
                    {
                        tmp += " " + Dic.chu[dv];
                    }

                }

            }
            else
            {
                tmp += Dic.chu[ch * 10 + dv];
            }
        }
        return tmp;
    }
}

