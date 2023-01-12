package com.ublox.gnss.services;

import java.io.IOException;

public interface FTService {
    void enableRead(int index);

    void sendMessage(int[] outData, int index) throws IOException;

    void destroy() throws IOException;
}
