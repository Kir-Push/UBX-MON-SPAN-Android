package com.ublox.gnss.services;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;

import java.io.IOException;

public interface FTService  {
    void setHandler(Handler mHandler);

    UsbManager getUsbManager();

    UsbDevice getUsbDevice();

    void setActivityContext(Context context);

    void sendMessage(int[] outData, int index) throws IOException;

}
