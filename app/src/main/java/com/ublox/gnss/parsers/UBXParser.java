package com.ublox.gnss.parsers;

import android.util.Log;

import com.ublox.gnss.messages.MonSpanMsg;
import com.ublox.gnss.messages.RfMsg;

public class UBXParser {
    public MonSpanMsg getMonSpan(byte[] readData) {
        MonSpanMsg monSpanMsg = new MonSpanMsg();
        int[] spectrum = new int[256];
        int shift = 10;
        for (int i = 0; i < 256; i++) {
            spectrum[i] = Byte.toUnsignedInt(readData[i+shift]);
        }
        monSpanMsg.setSpectrum(spectrum);
        monSpanMsg.setSpan(getInt(new byte[] {
                readData[shift + 256],
                readData[shift + 256 + 1],
                readData[shift + 256 + 2],
                readData[shift + 256 + 3],
        }));
        monSpanMsg.setRes(getInt(new byte[] {
                readData[shift + 256 + 4],
                readData[shift + 256 + 5],
                readData[shift + 256 + 6],
                readData[shift + 256 + 7],
        }));
        monSpanMsg.setCenter(getInt(new byte[] {
                readData[shift + 256 + 8],
                readData[shift + 256 + 9],
                readData[shift + 256 + 10],
                readData[shift + 256 + 11],
        }));
        monSpanMsg.setPga(Byte.toUnsignedInt(readData[shift + 256 + 12]));
        return monSpanMsg;
    }

    public boolean isMonSpan(byte[] readData) {
        return String.format("%04x", readData[0]).equals("00b5") && String.format("%04x", readData[3]).equals("0031");
    }

    public boolean isRf(byte[] readData) {
        return String.format("%04x", readData[0]).equals("00b5") && String.format("%04x", readData[3]).equals("0038");
    }

    public RfMsg getRf(byte[] readData) {
        RfMsg rfMsg = new RfMsg();
        int shift = 21;
        rfMsg.setNoisePerMS(getInt(new byte[] {readData[shift], readData[shift+1]}));
        rfMsg.setAgcCnt(getInt(new byte[] {readData[shift+2], readData[shift+3]}));
        rfMsg.setCwSuppression(Byte.toUnsignedInt(readData[shift+4]));
        return rfMsg;
    }

    private int getInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
//        int value = 0;
//        for (byte b : bytes) {
//            value = (value << 8) + (b & 0xFF);
//        }
//        return value;
    }
}
