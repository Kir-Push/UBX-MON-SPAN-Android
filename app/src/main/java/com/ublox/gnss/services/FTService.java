package com.ublox.gnss.services;

import android.os.Handler;

import java.io.IOException;

public interface FTService  {
    void setHandler(Handler mHandler);

    void sendMessage(int[] outData, int index) throws IOException;

}
