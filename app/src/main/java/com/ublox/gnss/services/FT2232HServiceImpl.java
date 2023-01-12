package com.ublox.gnss.services;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.ublox.gnss.R;
import com.ublox.gnss.messages.MonSpanMsg;
import com.ublox.gnss.messages.RfMsg;
import com.ublox.gnss.parsers.NMEAParser;
import com.ublox.gnss.parsers.UBXParser;
import com.ublox.gnss.utils.UnsignedOperation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FT2232HServiceImpl implements FTService {

    public static final String MON_SPAN = "MON-SPAN";
    public static final String MON_RF = "MON-RF";
    public static final String TYPE = "TYPE";

    private Context deviceFT2232HContext;

    /*local variables*/
    private int baudRate = 9600; /*baud rate*/
    private int workingRate = 460800; /*required baud rate*/
    public static final int readLength = 512;

    private NMEAParser nmeaParser;
    private UBXParser ubxParser;
    private D2xxManager ftdid2xx;

    private int devCount = -1;
    private int currentIndex = -1;

    private static int iEnableReadFlag = 1;
    private byte stopBit; /*1:1stop bits, 2:2 stop bits*/
    private byte dataBit; /*8:8bit, 7: 7bit*/
    private byte parity;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
    private byte flowControl; /*0:none, 1: flow control(CTS,RTS)*/
    private int portNumber; /*port number*/


    /*device 1*/
    private byte[] readData_0;
    private char[] readDataToText_0;
    public int iavailable_0 = 0;
    public boolean bReadThreadGoing_0 = false;
  //  private UsbSerialPort port_0;
    private FT_Device port_0;

    public LineChart chart;
    public BarChart barChart;

    /*device 2*/
    private byte[] readData_1;
    private char[] readDataToText_1;
    public int iavailable_1 = 0;
    public boolean bReadThreadGoing_1 = false;
  //  private UsbSerialPort port_1;
    private FT_Device port_1;

    private LineChart chart_1;
    private BarChart barChart_1;

    private static final int MON_SPAN_SIZE = 284;
    private static final int MON_RF_SIZE = 36;

    private final ScheduledExecutorService worker;


    public FT2232HServiceImpl(Context parentContext, D2xxManager ftdid2xx, LineChart chart, LineChart chart_1, BarChart barChart, BarChart barChart_1) {
        this.nmeaParser = new NMEAParser();
        this.ubxParser = new UBXParser();
        this.deviceFT2232HContext = parentContext;
        this.ftdid2xx = ftdid2xx;

        this.readData_0 = new byte[readLength];
        this.readDataToText_0 = new char[readLength];
        this.readData_1 = new byte[readLength];
        this.readDataToText_1 = new char[readLength];

        this.chart = chart;
        this.chart_1 = chart_1;
        this.barChart = barChart;
        this.barChart_1 =barChart_1;

        this.stopBit = 1;
        this.dataBit = 8;
        this.parity = 0;
        this.flowControl = 0;
        this.devCount = 0;

        this.worker = Executors.newScheduledThreadPool(3);

        try {
            createDeviceList();
            if(devCount > 0) {
                connectFunctionPort1();
                setConfig(baudRate, dataBit, stopBit, parity, flowControl, 0);
                connectFunctionPort2();
                setConfig(baudRate, dataBit, stopBit, parity, flowControl, 1);
            }

            worker.schedule(() -> {
                try {
                    configure();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, 1, TimeUnit.SECONDS);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createDeviceList() {
        int tempDevCount = ftdid2xx.createDeviceInfoList(deviceFT2232HContext);
        Toast.makeText(deviceFT2232HContext, tempDevCount + " port device attached", Toast.LENGTH_SHORT).show();
        if (tempDevCount > 0) {
            if( devCount != tempDevCount ) {
                devCount = tempDevCount;
                updatePortNumberSelector();
            }
        }
        else {
            devCount = -1;
            currentIndex = -1;
        }
    }

    public void updatePortNumberSelector() {
        if(devCount == 2) {
            Toast.makeText(deviceFT2232HContext, devCount + " port device attached", Toast.LENGTH_SHORT).show();
        }

    }

    private void configure() throws IOException {
//        Log.d("MSG", "SEND BAUD RATE");
//        sendMessage(Commands.baudRate14400, 0);
//        setConfig(14400, dataBit, stopBit, parity, flowControl, 0);
//
//        Log.d("MSG", "SEND BAUD RATE1");
//        sendMessage(Commands.baudRate14400, 1);
//        Log.d("MSG", "SEND BAUD RATE11");
//        setConfig(14400, dataBit, stopBit, parity, flowControl, 1);
//        Log.d("MSG", "SEND BAUD RATE DONE");

        worker.schedule(() -> {
            disableMessages();
            setMeasureRate();
            setMonSpanRate();
            setRfRate();
        }, 1, TimeUnit.SECONDS);
    }

    private void disableMessages() {
        try {
            sendMessage(Commands.disableMessages, 0);
            sendMessage(Commands.disableMessages, 1);
            sendMessage(Commands.disableGGAMessages, 0);
            sendMessage(Commands.disableGGAMessages, 1);
            Log.d("MSG", "SEND DISABLE MESSAGES DONE");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setMonSpanRate() {
        try {
            sendMessage(Commands.monSpanRate, 0);
            sendMessage(Commands.monSpanRate, 1);
            Log.d("MSG", "SEND MON RATE MESSAGES");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setRfRate() {
        try {
            sendMessage(Commands.rfRate, 0);
            sendMessage(Commands.rfRate, 1);
            Log.d("MSG", "SEND RF MESSAGES DONE");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setMeasureRate() {
        try {
            sendMessage(Commands.measureRate50, 0);
            sendMessage(Commands.measureRate50, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void enableRead(int index){
        iEnableReadFlag = (iEnableReadFlag + 1)%2;

        FT_Device port = null;
        if (index == 0) {
            port = port_0;
        } else if (index == 1) {
            port = port_1;
        }

        if (port == null) {
            Log.e("j2xx", "SendMessage: device not open");
            return;
        }

        if(iEnableReadFlag == 1) {
            port.purge((byte) (D2xxManager.FT_PURGE_TX));
            port.restartInTask();
        }
        else {
            port.stopInTask();
        }
    }

    @Override
    public void sendMessage(int[] outData, int index) throws IOException {
        byte[] resultData = new byte[outData.length];
        for (int i = 0; i < outData.length; i++) {
            resultData[i] = UnsignedOperation.unsignedIntToByte(outData[i]);
        }

        FT_Device port = null;
        if (index == 0) {
            port = port_0;
        } else if (index == 1) {
            port = port_1;
        }

        if (port == null || !port.isOpen()) {
            Log.e("j2xx", "SendMessage: device not open");
            return;
        }

        port.setLatencyTimer((byte) 16);
        //		ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
        port.write(resultData, resultData.length);

    }

    private final Handler handler =  new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if (bundle.getString(TYPE) == null) {
                return;
            }
            if (bundle.getString(TYPE).equals(MON_SPAN)) {
                MonSpanMsg monSpanMsg = bundle.getParcelable(MON_SPAN);
                if (monSpanMsg != null) {
                    updateChart(monSpanMsg, msg.what);
                }
            } else if (bundle.getString(TYPE).equals(MON_RF)) {
                RfMsg rfMsg = bundle.getParcelable(MON_RF);
                if (rfMsg != null) {
                    updateBarChart(rfMsg, msg.what);
                }
            }
        }
    };

    private void updateChart(MonSpanMsg monSpanMsg, int index) {
        LineChart tempChap = index == 1 ? chart : chart_1;
        tempChap.setVisibleXRange(0, 256);

        int[] spectrum = monSpanMsg.getSpectrum();
        List<Entry> entryList = new ArrayList<>();
        for (int i = 0; i < spectrum.length; i++) {

            Entry entryForIndex = new Entry();
            entryForIndex.setX(i);
            entryForIndex.setY(spectrum[i]);
            entryList.add(entryForIndex);
        }
        LineDataSet dataSet = new LineDataSet(entryList, "RF " + index);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        dataSet.setDrawFilled(true);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(1.8f);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleColor(Color.RED);
        dataSet.setHighLightColor(Color.rgb(244, 117, 117));
        dataSet.setColor(Color.RED);
        dataSet.setFillColor(Color.RED);
        dataSet.setFillAlpha(100);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setFillFormatter((dataSet1, dataProvider) -> chart.getAxisLeft().getAxisMinimum());
        dataSet.setColor(R.color.purple_200);
        dataSet.setValueTextColor(R.color.purple_500);
        LineData lineData = new LineData(dataSet);
        tempChap.setData(lineData);
        tempChap.invalidate();
    }

    private void updateBarChart(RfMsg monRfMsg, int index) {
        BarChart tempBarChart = index == 1 ? barChart : barChart_1;
        BarData barData = tempBarChart.getBarData();
        barData.removeEntry(1, 0);

        barData.addEntry(new BarEntry(1, monRfMsg.getNoisePerMS()), 0);
        tempBarChart.setData(barData);
        tempBarChart.invalidate();
    }

    private class ReadPort_0 implements Runnable {
        @Override
        public void run() {
            bReadThreadGoing_0 = true;
            byte[] monSpan = new byte[MON_SPAN_SIZE];
            int monSpanFilled = 0;
            byte[] monRf = new byte[MON_RF_SIZE];
            int monRfFilled = 0;
            int lastFilled = 0; // 1 monSpan, 2 monRf.
            int beforeFilled;
            while(bReadThreadGoing_0) {
                try {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                    }

                    iavailable_0 = port_0.getQueueStatus();
                    Log.e("AVAILABLE", "AVAILABLE " + iavailable_0);
                    if (iavailable_0 > 0) {

                        if(iavailable_0 > readLength){
                            iavailable_0 = readLength;
                        }

                        port_0.read(readData_0, iavailable_0);
                        Log.e("RECEIVED", String.format("%02X ", readData_0[0]) + " " + String.format("%02X ", readData_0[1]) + " " + String.format("%02X ", readData_0[2]) + " " + String.format("%02X ", readData_0[3]));

                        if (lastFilled == 0 || lastFilled == 1) {
                            beforeFilled = monSpanFilled;
                            monSpanFilled = findMonSpan(readData_0, iavailable_0, monSpan, monSpanFilled, lastFilled);
                            if (beforeFilled > monSpanFilled) {
                                lastFilled = 1;
                            }
                            beforeFilled = monRfFilled;
                            monRfFilled = findMonRF(readData_0, iavailable_0, monRf, monRfFilled, lastFilled);
                            if (beforeFilled > monRfFilled) {
                                lastFilled = 2;
                            }
                        } else if (lastFilled == 2) {
                            beforeFilled = monRfFilled;
                            monRfFilled = findMonRF(readData_0, iavailable_0, monRf, monRfFilled, lastFilled);
                            if (beforeFilled > monRfFilled) {
                                lastFilled = 2;
                            }
                            beforeFilled = monSpanFilled;
                            monSpanFilled = findMonSpan(readData_0, iavailable_0, monSpan, monSpanFilled, lastFilled);
                            if (beforeFilled > monSpanFilled) {
                                lastFilled = 1;
                            }
                        }

                        Bundle bundle = new Bundle();
                        if (ubxParser.isMonSpan(monSpan) && monSpanFilled == MON_SPAN_SIZE) {
                            Message msg = handler.obtainMessage(1);
                            MonSpanMsg spanMsg = ubxParser.getMonSpan(monSpan);
                            bundle.putParcelable(MON_SPAN, spanMsg);
                            bundle.putString(TYPE, MON_SPAN);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                            monSpan = new byte[MON_SPAN_SIZE];
                            monSpanFilled = 0;
                            if (lastFilled == 1) {
                                lastFilled = 0;
                            }

                        }
                        if (ubxParser.isRf(monRf) && monRfFilled == MON_RF_SIZE) {
                            Message msg = handler.obtainMessage(1);
                            RfMsg rfMsg = ubxParser.getRf(monRf);
                            bundle.putParcelable(MON_RF, rfMsg);
                            bundle.putString(TYPE, MON_RF);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                            monRf = new byte[MON_RF_SIZE];
                            monRfFilled = 0;
                            if (lastFilled == 2) {
                                lastFilled = 0;
                            }
                        }
                    }


                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    private int findMonSpan(byte[] readData, int iavailable, byte[] monSpan, int monSpanFilled, int lastFilled) {
        if (monSpanFilled == 0) {
            for (int i = 0; i < iavailable - 3; i++) {
                if (String.format("%04x", readData[i]).equals("00b5") && String.format("%04x", readData[i+3]).equals("0031")) {
                    int restInFrame = Math.min((iavailable - i), MON_SPAN_SIZE);
                    for (int c = 0; c < restInFrame; c++) {
                        monSpan[c] = readData[c + i];
                        monSpanFilled++;
                    }
                    break;
                }
            }
        } else if (lastFilled == 1) {
            int restInFrame = Math.min((iavailable - monSpanFilled), MON_SPAN_SIZE);
            for (int i = 0; i < restInFrame; i++) {
                monSpan[i] = readData[i];
                monSpanFilled++;
            }
        }
        return monSpanFilled;
    }

    private int findMonRF(byte[] readData, int iavailable, byte[] monRf, int monRfFilled, int lastFilled) {
        if (monRfFilled == 0) {
            for (int i = 0; i < iavailable - 3; i++) {
                if (String.format("%04x", readData[i]).equals("00b5") && String.format("%04x", readData[i+3]).equals("0038")) {
                    int restInFrame = Math.min((iavailable - i), MON_RF_SIZE);
                    for (int c = 0; c < restInFrame; c++) {
                        monRf[c] = readData[c + i];
                        monRfFilled++;
                    }
                    break;
                }
            }
        } else if (lastFilled == 2) {
            int restInFrame = Math.min((iavailable - monRfFilled), MON_RF_SIZE);
            for (int i = 0; i < restInFrame; i++) {
                monRf[i] = readData[i];
                monRfFilled++;
            }
        }
        return monRfFilled;

    }

    private class ReadPort_1  implements Runnable {
        @Override
        public void run() {
            bReadThreadGoing_1 = true;

            byte[] monSpan = new byte[MON_SPAN_SIZE];
            int monSpanFilled = 0;
            byte[] monRf = new byte[MON_RF_SIZE];
            int monRfFilled = 0;
            int lastFilled = 0; // 1 monSpan, 2 monRf.
            int beforeFilled;
            while(bReadThreadGoing_1) {
                try {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                    }

                    iavailable_1 = port_1.getQueueStatus();
                    if (iavailable_1 > 0) {

                        if(iavailable_1 > readLength){
                            iavailable_1 = readLength;
                        }

                        port_1.read(readData_1, iavailable_1);

                        if (lastFilled == 0 || lastFilled == 1) {
                            beforeFilled = monSpanFilled;
                            monSpanFilled = findMonSpan(readData_1, iavailable_1, monSpan, monSpanFilled, lastFilled);
                            if (beforeFilled > monSpanFilled) {
                                lastFilled = 1;
                            }
                            beforeFilled = monRfFilled;
                            monRfFilled = findMonRF(readData_1, iavailable_1, monRf, monRfFilled, lastFilled);
                            if (beforeFilled > monRfFilled) {
                                lastFilled = 2;
                            }
                        } else if (lastFilled == 2) {
                            beforeFilled = monRfFilled;
                            monRfFilled = findMonRF(readData_1, iavailable_1, monRf, monRfFilled, lastFilled);
                            if (beforeFilled > monRfFilled) {
                                lastFilled = 2;
                            }
                            beforeFilled = monSpanFilled;
                            monSpanFilled = findMonSpan(readData_1, iavailable_1, monSpan, monSpanFilled, lastFilled);
                            if (beforeFilled > monSpanFilled) {
                                lastFilled = 1;
                            }
                        }

                        Bundle bundle = new Bundle();
                        if (ubxParser.isMonSpan(monSpan) && monSpanFilled == MON_SPAN_SIZE) {
                            Message msg = handler.obtainMessage(2);
                            MonSpanMsg spanMsg = ubxParser.getMonSpan(monSpan);
                            bundle.putParcelable(MON_SPAN, spanMsg);
                            bundle.putString(TYPE, MON_SPAN);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                            monSpan = new byte[MON_SPAN_SIZE];
                            monSpanFilled = 0;
                            if (lastFilled == 1) {
                                lastFilled = 0;
                            }

                        }
                        if (ubxParser.isRf(monRf) && monRfFilled == MON_RF_SIZE) {
                            Message msg = handler.obtainMessage(2);
                            RfMsg rfMsg = ubxParser.getRf(monRf);
                            bundle.putParcelable(MON_RF, rfMsg);
                            bundle.putString(TYPE, MON_RF);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                            monRf = new byte[MON_RF_SIZE];
                            monRfFilled = 0;
                            if (lastFilled == 2) {
                                lastFilled = 0;
                            }
                        }
                    }
                    readData_1 = new byte[readLength];

                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }


    public void connectFunctionPort1() {
        int openIndex = 0;
        if(null == port_0) {
            port_0 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);
        } else {
            synchronized(port_0) {
                port_0 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);
            }
        }

        if(port_0 == null) {
            Toast.makeText(deviceFT2232HContext,"open device port("+openIndex+1+") NG, port_0 == null", Toast.LENGTH_LONG).show();
            return;
        }

        if (port_0.isOpen()) {
            Toast.makeText(deviceFT2232HContext, "open device port(" + openIndex+1 + ") OK", Toast.LENGTH_SHORT).show();

            if(!bReadThreadGoing_0) {
                worker.execute(new ReadPort_0());
                bReadThreadGoing_0 = true;
            }
        }
        else {
            Toast.makeText(deviceFT2232HContext, "open device port(" + openIndex+1 + ") NG", Toast.LENGTH_LONG).show();
        }
    }

    public void connectFunctionPort2() {
        int openIndex = 1;

        if(null == port_1) {
            port_1 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);
        }
        else {
            synchronized(port_1) {
                port_1 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);
            }
        }

        if(port_1 == null) {
            Toast.makeText(deviceFT2232HContext,"open device port("+openIndex+1+") NG, port_1 == null", Toast.LENGTH_LONG).show();
            return;
        }

        if (port_1.isOpen()) {
            currentIndex = openIndex;
            Toast.makeText(deviceFT2232HContext, "open device port(" + openIndex+1 + ") OK", Toast.LENGTH_SHORT).show();

            if(!bReadThreadGoing_1) {
                worker.execute(new ReadPort_1());
                bReadThreadGoing_1 = true;
            }
        }
        else {
            Toast.makeText(deviceFT2232HContext, "open device port(" + openIndex+1 + ") NG", Toast.LENGTH_LONG).show();
        }
    }

    public void setConfig(int baud, byte dataBits, byte stopBits, byte parity, byte flowControl, int index) {
        FT_Device ftDev = index == 0 ? port_0 : port_1;
        if (!ftDev.isOpen()) {
            Log.e("j2xx", "SetConfig: device not open");
            return;
        }

        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

        ftDev.setBaudRate(baud);

        switch (dataBits) {
            case 7:
                dataBits = D2xxManager.FT_DATA_BITS_7;
                break;
            case 8:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
            default:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
        }

        switch (stopBits) {
            case 1:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
            case 2:
                stopBits = D2xxManager.FT_STOP_BITS_2;
                break;
            default:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
        }

        switch (parity) {
            case 0:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
            case 1:
                parity = D2xxManager.FT_PARITY_ODD;
                break;
            case 2:
                parity = D2xxManager.FT_PARITY_EVEN;
                break;
            case 3:
                parity = D2xxManager.FT_PARITY_MARK;
                break;
            case 4:
                parity = D2xxManager.FT_PARITY_SPACE;
                break;
            default:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
        }

        ftDev.setDataCharacteristics(dataBits, stopBits, parity);

        short flowCtrlSetting;
        switch (flowControl) {
            case 0:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
            case 1:
                flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
                break;
            case 2:
                flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
                break;
            case 3:
                flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
                break;
            default:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
        }

        // TODO : flow ctrl: XOFF/XOM
        // TODO : flow ctrl: XOFF/XOM
        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);

        Toast.makeText(deviceFT2232HContext, "Config done " + index, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void destroy() {
        worker.shutdown();
        bReadThreadGoing_0 = false;
        bReadThreadGoing_1 = false;

        devCount = -1;
        currentIndex = -1;
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(port_0 != null) {
            synchronized(port_0) {
                if(port_0.isOpen()) {
                    port_0.close();
                }
            }
        }
        if(port_1 != null) {
            synchronized(port_1) {
                if(port_1.isOpen()) {
                    port_1.close();
                }
            }
        }
    }




}
