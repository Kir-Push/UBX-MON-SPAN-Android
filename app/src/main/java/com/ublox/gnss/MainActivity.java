package com.ublox.gnss;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.ftdi.j2xx.D2xxManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ublox.gnss.databinding.ActivityMainBinding;
import com.ublox.gnss.services.FT2232HServiceImpl;
import com.ublox.gnss.services.FTService;

import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FTService ftService;
    private D2xxManager ftdid2xx;
    private UsbDevice usbDevice;

    private UsbManager manager;
    private final BroadcastReceiver attachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                stopFtService();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                try {
                    startFtService();
                } catch (D2xxManager.D2xxException e) {
                    e.printStackTrace();
                }
            } else if (intent.getAction().equals("com.android.example.USB_PERMISSION")) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) { // User accepted our USB connection. Try to open the device as a serial port
                    try {
                        startFtService();
                    } catch (D2xxManager.D2xxException e) {
                        e.printStackTrace();
                    }
                } else {
                    PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, //Carefull check whether it's true approach.
                            new Intent("com.android.example.USB_PERMISSION"), 0);
                    manager.requestPermission(usbDevice, mPermissionIntent);
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction("com.android.example.USB_PERMISSION");
        registerReceiver(attachReceiver, filter);

        com.ublox.gnss.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        setupCharts();

        try {
            startFtService();
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
        }

    }

    private void setupCharts() {
        LineChart chart = findViewById(R.id.chart);
        List<Entry> entries = new ArrayList<Entry>();

        LineDataSet dataSet = new LineDataSet(entries, "MON-SPAN RF 1"); // add entries to dataset
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(1.8f);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleColor(Color.RED);
        dataSet.setHighLightColor(Color.rgb(244, 117, 117));
        dataSet.setColor(Color.RED);
        dataSet.setFillColor(Color.RED);
        dataSet.setFillAlpha(100);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setValueTextColor(R.color.purple_500);
        for (int i = 0; i< 256;i++) {
            dataSet.addEntry(new Entry(i, 0));
        }
        LineData lineData = new LineData(dataSet);
        Description description = new Description();
        description.setText("");
        chart.setDescription(description);
        chart.getXAxis().setDrawLabels(false);
        chart.getXAxis().disableGridDashedLine();
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawGridLinesBehindData(false);
        chart.getAxisRight().setDrawGridLinesBehindData(false);
        chart.getAxisRight().setDrawGridLines(false);
        chart.getAxisRight().setDrawLabels(false);
        chart.getAxisRight().disableGridDashedLine();
        chart.getAxisLeft().setDrawGridLinesBehindData(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().disableGridDashedLine();
        chart.getXAxis().setDrawAxisLine(true);
        chart.setData(lineData);
        chart.invalidate(); // refresh

        LineChart chart1 = findViewById(R.id.chart_2);
        List<Entry> entries1 = new ArrayList<Entry>();

        LineDataSet dataSet1 = new LineDataSet(entries1, "MON-SPAN RF 2"); // add entries to dataset
        dataSet1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet1.setCubicIntensity(0.2f);
        dataSet1.setDrawCircles(false);
        dataSet1.setLineWidth(1.8f);
        dataSet1.setCircleRadius(4f);
        dataSet1.setCircleColor(Color.RED);
        dataSet1.setHighLightColor(Color.rgb(244, 117, 117));
        dataSet1.setColor(Color.RED);
        dataSet1.setFillColor(Color.RED);
        dataSet1.setFillAlpha(100);
        dataSet1.setDrawHorizontalHighlightIndicator(false);
        dataSet1.setColor(R.color.purple_200);
        dataSet1.setValueTextColor(R.color.purple_500);
        for (int i = 0; i< 256;i++) {
            dataSet1.addEntry(new Entry(i, 0));
        }

        LineData lineData1 = new LineData(dataSet1);
        description = new Description();
        description.setText("");
        chart1.setDescription(description);
        chart1.getXAxis().setDrawLabels(false);
        chart1.getXAxis().disableGridDashedLine();
        chart1.getXAxis().setDrawGridLines(false);
        chart1.getXAxis().setDrawGridLinesBehindData(false);
        chart1.getAxisRight().setDrawGridLinesBehindData(false);
        chart1.getAxisRight().setDrawGridLines(false);
        chart1.getAxisRight().setDrawLabels(false);
        chart1.getAxisRight().disableGridDashedLine();
        chart1.getAxisLeft().setDrawGridLinesBehindData(false);
        chart1.getAxisLeft().setDrawGridLines(false);
        chart1.getAxisLeft().disableGridDashedLine();
        chart1.getXAxis().setDrawAxisLine(true);
        chart1.setData(lineData1);
        chart1.invalidate(); // refresh

        BarChart barChart = findViewById(R.id.index);
        BarDataSet barDataSet = new BarDataSet(new ArrayList<>(), "NoisePerMS RF 1");
        barDataSet.setColor(R.color.teal_200);
        barDataSet.setValueTextColor(R.color.teal_700);

        BarData barData = new BarData(barDataSet);
        barData.addEntry(new BarEntry(1, 0), 0);
        barChart.setData(barData);
        description = new Description();
        description.setText("");
        barChart.setDescription(description);
        barChart.getXAxis().setDrawLabels(false);
        barChart.getXAxis().disableGridDashedLine();
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getXAxis().setDrawGridLinesBehindData(false);
        barChart.getAxisRight().setDrawGridLinesBehindData(false);
        barChart.getAxisRight().setDrawGridLines(false);
        barChart.getAxisRight().setDrawLabels(false);
        barChart.getAxisRight().disableGridDashedLine();
        barChart.getAxisLeft().setDrawGridLinesBehindData(false);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisLeft().disableGridDashedLine();
        barChart.invalidate();

        BarChart barChart1 = findViewById(R.id.index_2);
        BarDataSet barDataSet1 = new BarDataSet(new ArrayList<>(), "NoisePerMS RF 2");
        barDataSet.setColor(R.color.teal_200);
        barDataSet.setValueTextColor(R.color.teal_700);

        BarData barData1 = new BarData(barDataSet1);
        barData1.addEntry(new BarEntry(1, 0), 0);
        barChart1.setData(barData1);
        description = new Description();
        description.setText("");
        barChart1.setDescription(description);
        barChart1.getXAxis().setDrawLabels(false);
        barChart1.getXAxis().disableGridDashedLine();
        barChart1.getXAxis().setDrawGridLines(false);
        barChart1.getXAxis().setDrawGridLinesBehindData(false);
        barChart1.getAxisRight().setDrawGridLinesBehindData(false);
        barChart1.getAxisRight().setDrawGridLines(false);
        barChart1.getAxisRight().setDrawLabels(false);
        barChart1.getAxisRight().disableGridDashedLine();
        barChart1.getAxisLeft().setDrawGridLinesBehindData(false);
        barChart1.getAxisLeft().setDrawGridLines(false);
        barChart1.getAxisLeft().disableGridDashedLine();
        barChart1.invalidate();
    }

    private void startFtService() throws D2xxManager.D2xxException {
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (ftdid2xx == null && manager.getDeviceList().size() > 0) {
            ftdid2xx = D2xxManager.getInstance(getApplicationContext());
            Iterator<UsbDevice> iterator = manager.getDeviceList().values().iterator();
            while (iterator.hasNext() && usbDevice == null) {
                usbDevice = iterator.next();
            }
            // Open a connection to the first available driver.
            UsbDeviceConnection connection = manager.openDevice(usbDevice);
            if (connection == null || !manager.hasPermission(usbDevice)) {
                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                        new Intent("com.android.example.USB_PERMISSION"), 0);
                manager.requestPermission(usbDevice, mPermissionIntent);
                return;
            }
            ftdid2xx.addUsbDevice(usbDevice);
        }

        if (ftService == null && usbDevice != null && manager.hasPermission(usbDevice)) {
            ftService = new FT2232HServiceImpl(getApplicationContext(), ftdid2xx,
                    findViewById(R.id.chart), findViewById(R.id.chart_2),
                    findViewById(R.id.index), findViewById(R.id.index_2));
        }
    }

    private void stopFtService() {
        try {
            if (ftService != null) {
                ftService.destroy();
            }
            ftService = null;
            manager = null;
            usbDevice = null;
            ftdid2xx = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}