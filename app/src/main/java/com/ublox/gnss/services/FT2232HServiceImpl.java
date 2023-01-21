package com.ublox.gnss.services;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.ublox.gnss.driver.FTDI_Constants;
import com.ublox.gnss.messages.MonSpanMsg;
import com.ublox.gnss.messages.RfMsg;
import com.ublox.gnss.parsers.NMEAParser;
import com.ublox.gnss.parsers.UBXParser;
import com.ublox.gnss.utils.UnsignedOperation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FT2232HServiceImpl extends Service implements FTService {

    public static final String TAG = "UsbService";

    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION_TEXT";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_PERMISSION_GRANTED = "com.gnss.usbservice.USB_PERMISSION_GRANTED";
    public static final String ACTION_USB_PERMISSION_REQUEST = "com.gnss.usbservice.USB_PERMISSION_REQUEST";
    public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.gnss.usbservice.USB_PERMISSION_NOT_GRANTED";
    public static final String ACTION_USB_DISCONNECTED = "com.gnss.usbservice.USB_DISCONNECTED";
    public static final String ACTION_NO_USB = "com.gnss.usbservice.NO_USB";

    public static final String MON_SPAN = "MON-SPAN";
    public static final String MON_RF = "MON-RF";
    public static final String TYPE = "TYPE";

    public static boolean SERVICE_CONNECTED = false;

    private Context deviceFT2232HContext;
    private Context activityContext;

    /*local variables*/
    private int baudRate = 9600; /*baud rate*/
    private int workingRate = 460800; /*required baud rate*/
    public static final int readLength = 512;

    private NMEAParser nmeaParser;
    private UBXParser ubxParser;
    private D2xxManager ftdid2xx;

    @Override
    public UsbManager getUsbManager() {
        return usbManager;
    }

    private UsbManager usbManager;

    @Override
    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    private UsbDevice usbDevice;
    private Handler handler;

    private int devCount = -1;
    private static boolean serialPortConnected = false;

    private static int iEnableReadFlag = 1;
    private byte stopBit; /*1:1stop bits, 2:2 stop bits*/
    private byte dataBit; /*8:8bit, 7: 7bit*/
    private byte parity;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
    private byte flowControl; /*0:none, 1: flow control(CTS,RTS)*/


    /*device 1*/
    private byte[] readData_0;
    public int iavailable_0 = 0;
    public boolean bReadThreadGoing_0 = false;
    private FT_Device port_0;

    /*device 2*/
    private byte[] readData_1;
    public int iavailable_1 = 0;
    public boolean bReadThreadGoing_1 = false;
    private FT_Device port_1;

    private static final int MON_SPAN_SIZE = 284;
    private static final int MON_RF_SIZE = 36;

    private static ScheduledExecutorService worker;

    private IBinder binder = new UsbBinder();

    public class UsbBinder extends Binder {
        public FT2232HServiceImpl getService() {
            return FT2232HServiceImpl.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void requestUserPermission() {
        Log.d(TAG, String.format("requestUserPermission(%X:%X)", usbDevice.getVendorId(), usbDevice.getProductId() ) );
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(usbDevice, mPendingIntent);
    }

    @Override
    public void setActivityContext(Context context) {
        this.activityContext = context;
    }

    @Override
    public void onCreate() {
        try {
            worker = Executors.newScheduledThreadPool(3);
            setFilter();
            findDevices();
            this.deviceFT2232HContext = this;

            this.nmeaParser = new NMEAParser();
            this.ubxParser = new UBXParser();

            this.readData_0 = new byte[readLength];
            this.readData_1 = new byte[readLength];

            this.stopBit = 1;
            this.dataBit = 8;
            this.parity = 0;
            this.flowControl = 0;
            this.devCount = 0;

            bReadThreadGoing_0 = false;
            bReadThreadGoing_1 = false;
            SERVICE_CONNECTED = true;
          //  findDevices();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setHandler(Handler mHandler) {
        this.handler = mHandler;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }
    private void findDevices() {
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> iterator = deviceList.values().iterator();
        while (iterator.hasNext() && usbDevice == null) {
            UsbDevice device = iterator.next();
            if (device != null && is_FTDI_USB_to_Serial_Device(device)) {
                usbDevice = device;
                new Handler(this.getMainLooper())
                        .post(this::requestUserPermission);
                break;
            }
        }
        if (usbDevice==null) {
            // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
            Intent intent = new Intent(ACTION_NO_USB);
            sendBroadcast(intent);
        }

    }

    private void configureDevices() {
        try {
            if (this.ftdid2xx == null) {
                this.ftdid2xx = D2xxManager.getInstance(getApplicationContext());
                this.ftdid2xx.setUsbRegisterBroadcast(false);
                this.ftdid2xx.setRequestPermission(false);
            }
            if (this.worker == null || this.worker.isShutdown() || this.worker.isTerminated()) {
                this.worker = Executors.newScheduledThreadPool(3);
            }
            if (devCount > 0) {
                return;
            }
            createDeviceList();
            if (devCount > 0) {
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
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
        }
    }

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            Log.e("USB_RECEIVE", "ACTION!! " + arg1.getAction());
            if (arg1.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = arg1.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {  // User accepted our USB connection. Try to open the device as a serial port
                    Intent arg = new Intent(FT2232HServiceImpl.ACTION_USB_PERMISSION_GRANTED);
                    arg0.sendBroadcast(arg);
                  //  ftdid2xx.addUsbDevice(usbDevice);
                    configureDevices();
                }
            } else if (arg1.getAction().equals(ACTION_USB_ATTACHED)) {
                if (!serialPortConnected) {
                    findDevices();
                }
            } else if (arg1.getAction().equals(ACTION_USB_DETACHED)) {
                // Usb device was disconnected. send an intent to the Main Activity
                Intent intent = new Intent(ACTION_USB_DISCONNECTED);
                arg0.sendBroadcast(intent);
                if (serialPortConnected) {
                    stopService();
                }
                serialPortConnected = false;
            }
        }
    };

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
            serialPortConnected = true;
        }, 1, TimeUnit.SECONDS);
    }

    private void disableMessages() {
        try {
            sendMessage(Commands.disableMessages, 0);
            sendMessage(Commands.disableMessages, 1);
            sendMessage(Commands.disableGGAMessages, 0);
            sendMessage(Commands.disableGGAMessages, 1);
            Log.d(TAG, "SEND DISABLE MESSAGES DONE");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setMonSpanRate() {
        try {
            sendMessage(Commands.monSpanRate, 0);
            sendMessage(Commands.monSpanRate, 1);
            Log.d(TAG, "SEND MON RATE MESSAGES");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setRfRate() {
        try {
            sendMessage(Commands.rfRate, 0);
            sendMessage(Commands.rfRate, 1);
            Log.d(TAG, "SEND RF MESSAGES DONE");
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
            Log.e(TAG, "SendMessage: device not open");
            return;
        }

        port.setLatencyTimer((byte) 16);
        //		ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
        port.write(resultData, resultData.length);

    }

    private int handleMessage(int type, LinkedList<Byte> fifo, byte[] monSpan, byte[] monRf, byte[] readData, int iavailable, Pair<Integer, Integer> monFilled, int index) {
        Log.e("RECEIVED - " + index, String.format("%02X ", readData[0]) + " " + String.format("%02X ", readData[1]) + " " + String.format("%02X ", readData[2]) + " " + String.format("%02X ", readData[3]));
        for (int i = 0; i< iavailable; i++) {
            fifo.add(readData[i]);
        }


        Byte first = Byte.valueOf("00");
        if (type == 0) {
            while (fifo.size() >= 4) {
                first = fifo.pollFirst();
                monSpan[0] = first;
                monRf[0] = first;
                if (String.format("%04x", first).equals("00b5")) {
                    Byte next = fifo.pollFirst();
                    monSpan[1] = next;
                    monRf[1] = next;
                    next = fifo.pollFirst();
                    monSpan[2] = next;
                    monRf[2] = next;
                    next = fifo.pollFirst();
                    monSpan[3] = next;
                    monRf[3] = next;
                    Log.e("RECEIVED! - " + index, String.format("%04x", first) + " " + String.format("%04x", next) + "   FIFO SIZE - " + fifo.size());
                    if ((String.format("%04x", first).equals("00b5")) &&
                            (String.format("%04x", next).equals("0038"))) {
                        monFilled.second = 4;
                        type = 2;
                        break;
                    } else if ((String.format("%04x", first).equals("00b5") &&
                            (String.format("%04x", next).equals("0031")))) {
                        monFilled.first = 4;
                        type = 1;
                        break;
                    }
                }
            }
        }


        if (type == 1 && fifo.size() > 0) {
            int min = Math.min(fifo.size(), MON_SPAN_SIZE);
            int filledTemp =  monFilled.first ;
            for (int i =  monFilled.first; i < min; i++) {
                monSpan[i] = fifo.pollFirst();
                filledTemp++;
            }
            monFilled.first = filledTemp;
            if ( monFilled.first >= MON_SPAN_SIZE) {
                type = 0;
            }
        }

        if (type == 2 && fifo.size() > 0) {
            int min = Math.min(fifo.size(), MON_RF_SIZE);
            int filledTemp =  monFilled.second;
            for (int i =  monFilled.second; i < min; i++) {
                monRf[i] = fifo.pollFirst();
                filledTemp++;
            }
            monFilled.second = filledTemp;
            if (monFilled.second >= MON_RF_SIZE) {
                type = 0;
            }
        }

        Log.e("REST - " + index, "RESTING " + monFilled.first);
        Log.e("REST - " + index, "RESTIN RF " +  monFilled.second);
        Bundle bundle = new Bundle();
        if (ubxParser.isMonSpan(monSpan) && monFilled.first == MON_SPAN_SIZE) {
            Message msg = handler.obtainMessage(index);
            MonSpanMsg spanMsg = ubxParser.getMonSpan(monSpan);
            bundle.putParcelable(MON_SPAN, spanMsg);
            bundle.putString(TYPE, MON_SPAN);
            msg.setData(bundle);
            handler.sendMessage(msg);
            monFilled.first = 0;

        }
        if (ubxParser.isRf(monRf) &&  monFilled.second == MON_RF_SIZE) {
            Message msg = handler.obtainMessage(index);
            RfMsg rfMsg = ubxParser.getRf(monRf);
            bundle.putParcelable(MON_RF, rfMsg);
            bundle.putString(TYPE, MON_RF);
            msg.setData(bundle);
            handler.sendMessage(msg);
            monFilled.second = 0;
        }
        return type;
    }


    private class ReadPort_0 implements Runnable {
        @Override
        public void run() {
            bReadThreadGoing_0 = true;
            byte[] monSpan = new byte[MON_SPAN_SIZE];
            int monSpanFilled = 0;
            byte[] monRf = new byte[MON_RF_SIZE];
            int monRfFilled = 0;
            LinkedList<Byte> fifo = new LinkedList();
            Pair<Integer, Integer> monFilled = new Pair<>(monSpanFilled, monRfFilled);
            int type = 0;
            while(bReadThreadGoing_0) {
                try {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                    }

                    iavailable_0 = port_0.getQueueStatus();
                    if (iavailable_0 > 0) {

                        if (iavailable_0 > readLength) {
                            iavailable_0 = readLength;
                        }

                        port_0.read(readData_0, iavailable_0);
                        type = handleMessage(type, fifo, monSpan, monRf, readData_0, iavailable_0, monFilled, 1);
                        if (monFilled.second == 0) {
                            monRf = new byte[MON_RF_SIZE];
                        }
                        if (monFilled.first == 0) {
                            monSpan = new byte[MON_SPAN_SIZE];
                        }
                    }


                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }


    private class ReadPort_1  implements Runnable {
        @Override
        public void run() {
            bReadThreadGoing_1 = true;

            byte[] monSpan = new byte[MON_SPAN_SIZE];
            int monSpanFilled = 0;
            byte[] monRf = new byte[MON_RF_SIZE];
            int monRfFilled = 0;
            LinkedList<Byte> fifo = new LinkedList();
            Pair<Integer, Integer> monFilled = new Pair<>(monSpanFilled, monRfFilled);
            int type = 0;
            while(bReadThreadGoing_1) {
                try {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                    }

                    iavailable_1 = port_1.getQueueStatus();
                    if (iavailable_1 > 0) {

                        if(iavailable_1 > readLength){
                            iavailable_1 = readLength;
                        }

                        port_1.read(readData_1, iavailable_1);
                        type = handleMessage(type, fifo, monSpan, monRf, readData_1, iavailable_1, monFilled, 2);
                        if (monFilled.second == 0) {
                            monRf = new byte[MON_RF_SIZE];
                        }
                        if (monFilled.first == 0) {
                            monSpan = new byte[MON_SPAN_SIZE];
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
        port_0 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);

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

        port_1 = ftdid2xx.openByIndex(deviceFT2232HContext, openIndex);

        if(port_1 == null) {
            Toast.makeText(deviceFT2232HContext,"open device port("+openIndex+1+") NG, port_1 == null", Toast.LENGTH_LONG).show();
            return;
        }

        if (port_1.isOpen()) {
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
        if (ftDev == null || !ftDev.isOpen()) {
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
        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);

        Toast.makeText(deviceFT2232HContext, "Config done " + index, Toast.LENGTH_SHORT).show();
    }

    private static boolean is_FTDI_USB_to_Serial_Device(UsbDevice dev) {
        //check VID,
        if(dev.getVendorId() != FTDI_Constants.VID_FTDI) {
            return false;
        }
        //check PID,
        switch(dev.getProductId()) {
            case FTDI_Constants.PID_FT232B_FT245B_FT232R_FT245R:
            case FTDI_Constants.PID_FT232H:
            case FTDI_Constants.PID_FT2232C_FT2232D_FT2232L_FT2232H:
            case FTDI_Constants.PID_FT4232H:
                return true;

            default:
                return false;
        }
        //check bcdDevice, I... don't think we need to go this far...
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        SERVICE_CONNECTED = false;
        stopService();
    }

    private void stopService() {
        bReadThreadGoing_0 = false;
        bReadThreadGoing_1 = false;
        serialPortConnected = false;
        usbDevice = null;
        ftdid2xx = null;

        devCount = -1;

        if(port_0 != null) {
            if(port_0.isOpen()) {
                port_0.close();
            }
        }
        port_0 = null;
        if(port_1 != null) {
            if(port_1.isOpen()) {
                port_1.close();
            }
        }
        port_1 = null;

        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    public class Pair<F, S> {
        public F first;
        public S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }


}
