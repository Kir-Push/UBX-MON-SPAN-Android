package com.ublox.gnss;

import static com.ublox.gnss.services.FT2232HServiceImpl.MON_RF;
import static com.ublox.gnss.services.FT2232HServiceImpl.MON_SPAN;
import static com.ublox.gnss.services.FT2232HServiceImpl.TYPE;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import com.ublox.gnss.driver.FTDI_Constants;
import com.ublox.gnss.messages.MonSpanMsg;
import com.ublox.gnss.messages.RfMsg;
import com.ublox.gnss.services.FT2232HServiceImpl;
import com.ublox.gnss.services.FTService;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private FTService usbService;
    private Handler mHandler;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((FT2232HServiceImpl.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                Bundle bundle = msg.getData();
                if (bundle.getString(TYPE) == null) {
                    return;
                }
                if (bundle.getString(TYPE).equals(MON_SPAN)) {
                    MonSpanMsg monSpanMsg = bundle.getParcelable(MON_SPAN);
                    if (monSpanMsg != null) {
                        mActivity.get().updateChart(monSpanMsg, msg.what);
                    }
                } else if (bundle.getString(TYPE).equals(MON_RF)) {
                    RfMsg rfMsg = bundle.getParcelable(MON_RF);
                    if (rfMsg != null) {
                        mActivity.get().updateBarChart(rfMsg, msg.what);
                    }
                }
            } finally {
                super.handleMessage(msg);
            }
        }
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(FT2232HServiceImpl.ACTION_NO_USB);
        filter.addAction(FT2232HServiceImpl.ACTION_USB_DISCONNECTED);
        filter.addAction(FT2232HServiceImpl.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!FT2232HServiceImpl.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("USB_RECEIVE - !!!!", "ACTION!! " + intent.getAction());
            switch (intent.getAction()) {
                case FT2232HServiceImpl.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case FT2232HServiceImpl.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case FT2232HServiceImpl.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(FT2232HServiceImpl.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }


    public void updateChart(MonSpanMsg monSpanMsg, int index) {
        LineChart tempChart = index == 1 ? findViewById(R.id.chart) :  findViewById(R.id.chart_2);
        tempChart.setVisibleXRange(0, 256);

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
        dataSet.setFillFormatter((dataSet1, dataProvider) -> tempChart.getAxisLeft().getAxisMinimum());
        dataSet.setColor(R.color.purple_200);
        dataSet.setValueTextColor(R.color.purple_500);
        LineData lineData = new LineData(dataSet);
        tempChart.setData(lineData);
        tempChart.invalidate();
    }

    public void updateBarChart(RfMsg monRfMsg, int index) {
        BarChart tempBarChart = index == 1 ? findViewById(R.id.index) :  findViewById(R.id.index_2);
        BarData barData = tempBarChart.getBarData();
        barData.removeEntry(1, 0);

        barData.addEntry(new BarEntry(1, monRfMsg.getNoisePerMS()), 0);
        tempBarChart.setData(barData);
        tempBarChart.invalidate();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.ublox.gnss.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mHandler = new MyHandler(this);
        setupCharts();

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