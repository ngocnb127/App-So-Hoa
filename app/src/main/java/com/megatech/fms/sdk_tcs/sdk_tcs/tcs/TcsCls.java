package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TcsCls {

    private static final byte[] crcArray = new byte[] {
            0, 94, -68, -30, 97, 63, -35, -125, -62, -100, 126, 32, -93, -3, 31, 65,
            -99, -61, 33, 127, -4, -94, 64, 30, 95, 1, -29, -67, 62, 96, -126, -36,
            35, 125, -97, -63, 66, 28, -2, -96, -31, -65, 93, 3, -128, -34, 60, 98,
            -66, -32, 2, 92, -33, -127, 99, 61, 124, 34, -64, -98, 29, 67, -95, -1,
            70, 24, -6, -92, 39, 121, -101, -59, -124, -38, 56, 102, -27, -69, 89, 7,
            -37, -123, 103, 57, -70, -28, 6, 88, 25, 71, -91, -5, 120, 38, -60, -102,
            101, 59, -39, -121, 4, 90, -72, -26, -89, -7, 27, 69, -58, -104, 122, 36,
            -8, -90, 68, 26, -103, -57, 37, 123, 58, 100, -122, -40, 91, 5, -25, -71,
            -116, -46, 48, 110, -19, -77, 81, 15, 78, 16, -14, -84, 47, 113, -109, -51,
            17, 79, -83, -13, 112, 46, -52, -110, -45, -115, 111, 49, -78, -20, 14, 80,
            -81, -15, 19, 77, -50, -112, 114, 44, 109, 51, -47, -113, 12, 82, -80, -18,
            50, 108, -114, -48, 83, 13, -17, -79, -16, -82, 76, 18, -111, -49, 45, 115,
            -54, -108, 118, 40, -85, -11, 23, 73, 8, 86, -76, -22, 105, 55, -43, -117,
            87, 9, -21, -75, 54, 104, -118, -44, -107, -53, 41, 119, -12, -86, 72, 22,
            -23, -73, 85, 11, -120, -42, 52, 106, 43, 117, -105, -55, 74, 20, -10, -88,
            116, 42, -56, -106, 21, 75, -87, -9, -73, -23, 10, 84, -41, -119, 107, 53
    };

    public byte msgCrc(byte[] data) {
        int len = data.length;
        byte crc = 0;

        for (int i = 0; i < len; i++) {
            int index = (crc ^ data[i]) & 0xFF;
            //System.out.println("index: "+index);
            crc = crcArray[index];
        }
        return crc;
    }


    public byte[] msgCrc(byte[] data, int crc) {
        byte[] dataCrc = Arrays.copyOfRange(data, 2, data.length);
        byte[] bResult = new byte[2];

        for (byte b : dataCrc) {
            for (int i = 7; i >= 0; --i) {
                boolean xorFlag = (crc & 0x8000) != 0;
                crc <<= 1;
                crc |= ((b >> i) & 0x01);
                if (xorFlag) {
                    crc ^= 0x1021;
                }
            }
        }

        bResult[0] = (byte) (crc & 0xFF);
        bResult[1] = (byte) ((crc >> 8) & 0xFF);
        return bResult;
    }

    public byte[] setMessageArray(byte[] data) {
        if (data.length > 0) {
            byte[] retArray = new byte[checkAndSetDataMaxSize(data.length)];
            System.arraycopy(data, 0, retArray, 0, retArray.length);
            return retArray;
        } else {
            return new byte[0];
        }
    }

    public byte[] setMessageArray(String strData) {
        if (strData != null && !strData.isEmpty()) {
            byte[] tempData = strData.getBytes(StandardCharsets.US_ASCII);
            byte[] retArray = new byte[checkAndSetDataMaxSize(tempData.length)];
            System.arraycopy(tempData, 0, retArray, 0, retArray.length);
            return retArray;
        } else {
            return new byte[0];
        }
    }

    private int checkAndSetDataMaxSize(int size) {
        return size;
    }
}
