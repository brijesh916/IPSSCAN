package com.example.wifiscanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WifiManager wifiManager;
    private WifiRttManager wifiRttManager;
    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;
    private ArrayList<String> wifiList;
    private Handler scanHandler;

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 100;
    private static final int SCAN_INTERVAL = 10000; // 10 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Wi-Fi and RTT Manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

        // ListView and Adapter setup
        listView = findViewById(R.id.listview);
        wifiList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, wifiList);
        listView.setAdapter(arrayAdapter);

        // Handler to trigger periodic scanning
        scanHandler = new Handler();

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
        } else {
            startPeriodicWifiScan();
        }
    }

    private void startPeriodicWifiScan() {
        scanHandler.post(scanRunnable);  // Start the scan loop
    }

    // Runnable to repeat Wi-Fi scans at regular intervals
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            startWifiScan();  // Trigger Wi-Fi scan
            scanHandler.postDelayed(this, SCAN_INTERVAL);  // Repeat after SCAN_INTERVAL milliseconds
        }
    };

    private void startWifiScan() {
        // Register receiver to listen for scan results
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(new WifiScanReceiver(), intentFilter);

        // Start Wi-Fi scan
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            boolean success = wifiManager.startScan();
            if (!success) {
                // Scan initiation failed
                Toast.makeText(this, "Wi-Fi Scan failed to start.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Wi-Fi is disabled.", Toast.LENGTH_SHORT).show();
        }
    }

    class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
        }
    }

    private void scanSuccess() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        List<ScanResult> results = wifiManager.getScanResults();
        wifiList.clear();

        List<ScanResult> rttCapableAPs = new ArrayList<>();
        for (ScanResult result : results) {
            // Get RSSI value
            int rssi = result.level;
            String ssid = result.SSID;
            String mac=result.BSSID;
            wifiList.add("SSID  :  "+ssid +"\nMAC  :  "+mac+ "  \nRSSI  : " + rssi + " dBm");

            // Check if the AP supports RTT
            if (result.is80211mcResponder()) {
                rttCapableAPs.add(result);
            }
        }
        arrayAdapter.notifyDataSetChanged();

        // Initiate RTT measurements for RTT-capable access points
        if (!rttCapableAPs.isEmpty()) {
            initiateRttMeasurements(rttCapableAPs);
        }
    }

    private void scanFailure() {
        Toast.makeText(this, "Wi-Fi scan failed.", Toast.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void initiateRttMeasurements(List<ScanResult> rttCapableAPs) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        RangingRequest.Builder builder = new RangingRequest.Builder();
        for (ScanResult ap : rttCapableAPs) {
            builder.addAccessPoint(ap);
        }

        wifiRttManager.startRanging(builder.build(), getMainExecutor(), new RangingResultCallback() {
            @Override
            public void onRangingFailure(int code) {
                Toast.makeText(MainActivity.this, "RTT ranging failed.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRangingResults(@NonNull List<RangingResult> results) {
                for (RangingResult result : results) {
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
                        int rssi = result.getRssi();
                        int distance = result.getDistanceMm() / 1000; // Convert mm to meters
                        wifiList.add(result.getMacAddress().toString() + " - Distance: " + distance + " m, RSSI: " + rssi + " dBm");
                    } else {
                        wifiList.add("Ranging failed for " + result.getMacAddress().toString());
                    }
                }
                arrayAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPeriodicWifiScan();
            } else {
                Toast.makeText(this, "Permission required to scan Wi-Fi networks.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop periodic scan on activity destruction
        scanHandler.removeCallbacks(scanRunnable);
    }
}
