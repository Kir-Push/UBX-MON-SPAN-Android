package com.ublox.gnss.parsers;


import com.ublox.gnss.utils.UnsignedOperation;

import java.nio.ByteBuffer;
import java.util.Vector;

public class UBXRateConfiguration {

    public final int uBloxPrefix1 = 0xB5;
    public final  int uBloxPrefix2 = 0x62;

    //private int rate;
    private int CK_A;
    private int CK_B;
    private Vector<Integer> msg;

    public UBXRateConfiguration(int measRate, int navRate, int timeRef) {
        byte[] measRateBytes = ByteBuffer.allocate(4).putInt(measRate).array();
        byte[] navRateBytes = ByteBuffer.allocate(4).putInt(navRate).array();
        byte[] timeRefBytes = ByteBuffer.allocate(4).putInt(timeRef).array();
        msg = new Vector();
        msg.addElement(new Integer(uBloxPrefix1));
        msg.addElement(new Integer(uBloxPrefix2));
        msg.addElement(new Integer(0x06)); // CFG
        msg.addElement(new Integer(0x08)); // RATE
        msg.addElement(new Integer(6)); // length low
        msg.addElement(new Integer(0)); // length hi
        msg.addElement(new Integer(measRateBytes[3]));
        msg.addElement(new Integer(measRateBytes[2]));
        msg.addElement(new Integer(navRateBytes[3]));
        msg.addElement(new Integer(navRateBytes[2]));
        msg.addElement(new Integer(timeRefBytes[3]));
        msg.addElement(new Integer(timeRefBytes[2]));
        checkSum();
        msg.addElement(new Integer(CK_A));
        msg.addElement(new Integer(CK_B));
    }

    private void checkSum() {
        CK_A = 0;
        CK_B = 0;
        for (int i = 2; i < msg.size(); i++) {
            CK_A = CK_A + ((Integer) msg.elementAt(i)).intValue();
            CK_B = CK_B + CK_A;

        }
        CK_A = CK_A & 0xFF;
        CK_B = CK_B & 0xFF;
    }

    public byte[] getByte() {
        byte[] bytes = new byte[msg.size()];
        for (int i = 0; i < msg.size(); i++) {
            bytes[i] = UnsignedOperation.unsignedIntToByte(((Integer)msg.elementAt(i)).intValue());
        }
        return bytes;
    }

}