package com.example.bluetooth1;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    public static final int ENABLE_BT_REQUEST = 1;

    private BluetoothAdapter myBtAdapter;
    private Button btOnBtn, btOffBtn;
    private RecyclerView pairedDevices_view;
    private RecyclerView discoveredDevices_view;
    private BroadcastReceiver discoverReceiver;
    Set<BluetoothDevice> btDevices;
    private ArrayList<String> pairedDevices = new ArrayList<>();
    private ArrayList<String> discoveredDevices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get a reference to some of the views for later use
        btOnBtn = findViewById(R.id.bt_on_btn);
        btOffBtn = findViewById(R.id.bt_off_btn);
        pairedDevices_view = findViewById(R.id.paired_devices);
        discoveredDevices_view = findViewById(R.id.discovered_devices);

        // Initialize a reference to the Bluetooth Adapter
        myBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // initialize discovery settings
        setupDiscoverListener();
        setupDiscoverableListener();
    }

    /**
     * Tries to enable Bluetooth
     * @param view - the view that this callback is for
     */
    public void enableBluetooth(View view) {
        // Check to see if BT is supported by the device
        if (myBtAdapter == null ) {
            // Device doesn't support BT
            Log.d(LOG_TAG, "Device does not support Bluetooth");
            toast("Device does not support Bluetooth");
        } else { // Bluetooth is supported
            if (myBtAdapter.isEnabled() == false) { // Bluetooth  is disabled, so try to enable
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, ENABLE_BT_REQUEST);
                Log.d(LOG_TAG, "Bluetooth is not enabled, please enable.");
            } else {
                toast("Bluetooth is already Enabled.");
            }
        }
    }

    /**
     * Tries to disable Bluetooth
     * @param view - The view that called this method
     */
    public void disableBluetooth(View view) {
        if (myBtAdapter.isEnabled()) {
            myBtAdapter.disable();
            toast("Bluetooth has been disabled");
        } else {
            toast("Bluetooth is already disabled");
        }
    }

    public void showPairedDevices(View view) {
        if (!myBtAdapter.isEnabled()) { // If BlueTooth is off then return
            toast("Please enable Bluetooth");
            return;
        }
        btDevices = myBtAdapter.getBondedDevices(); // Returns Set of paired devices
        // Create a list of strings representing the device names
        pairedDevices.clear(); // list of paired devices
        if (btDevices.size() > 0) {
            for (BluetoothDevice device : btDevices) {
                pairedDevices.add(device.getName());
            }
        }
        // Create an adapter and supply the data to be displayed
        DeviceListAdapter adapter = new DeviceListAdapter(this, pairedDevices);
        // Connect the adapter the recycler view
        pairedDevices_view.setAdapter(adapter);
        // Give the recycler view a default layout manager
        pairedDevices_view.setLayoutManager(new LinearLayoutManager(this));
        // Show the RecycleView for paired-devices and hide the one for discovered-devices
        pairedDevices_view.setVisibility(View.VISIBLE);
        discoveredDevices_view.setVisibility(View.GONE);
    }

    /**
     * This function enables BT discovery so that other devices can find this one.
     * The default discovery time is 120 seconds.
     * @param view - the view that called this function.
     */
    public void enableDiscoverable(View view) {
        if (!myBtAdapter.isEnabled()) {
            toast("Please enable Bluetooth...");
            return;
        }
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 20);
        startActivity(discoverableIntent);
    }

    /**
     * Searches for BT devices and updates the recyclerview when one is found
     * @param view - view that called this method
     */
    public void discoverDevices(View view) {
        if (!myBtAdapter.isEnabled()) { // Make sure BT is on before discovering
            toast("Please enable Bluetooth...");
            return;
        }
        if (myBtAdapter.isDiscovering()) {
            myBtAdapter.cancelDiscovery();
        }
        // Starting from Android Marshmallow onward you must explicitly ask for permission as well
        // as including it in you manifest.
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
        discoveredDevices.clear(); // clear previous results if exist
        // Show the RecycleView for discovered-devices and hide the one for paired-devices
        discoveredDevices_view.setVisibility(View.VISIBLE);
        pairedDevices_view.setVisibility(View.GONE);
        // Begin the discovery scan
        myBtAdapter.startDiscovery();
        toast("Discovering devices... (12s)");
        Log.d(LOG_TAG,"startingDiscovery");
    }

    private void setupDiscoverListener() {
        Log.d(LOG_TAG,"setupDiscoverListener");
        // Create an adapter and supply the data to be displayed
        final DeviceListAdapter adapter = new DeviceListAdapter(this, discoveredDevices);
        // Connect the adapter the recycler view
        discoveredDevices_view.setAdapter(adapter);
        // Give the recycler view a default layout manager
        discoveredDevices_view.setLayoutManager(new LinearLayoutManager(this));
        // ------------------------------------------------------------------------------------
        // Create a Receiver object that will handle the incoming intents
        this.discoverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    Log.d("MainActivity","onReceive BluetoothDevice.ACTION_FOUND");
                    BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String name = d.getName();
                    if (name == null) {
                        discoveredDevices.add(d.getAddress());
                    } else {
                        discoveredDevices.add(d.getAddress() + " " + name);
                    }
                    adapter.notifyDataSetChanged();
                }
            }
        }; // ------------------------------------------------------------------------------------
        // Create an intentFilter to listen for BluetoothDevice.ACTION_FOUND
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        // Register the receiver with the intentFilter
        registerReceiver(this.discoverReceiver, intentFilter);
    }

    /**
     * Initialized the IntentFilter listener for BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.
     * TODO consider changing this method to update some view rather than a toast.
     */
    public void setupDiscoverableListener() {
        Log.d(LOG_TAG, "setupDiscoverableListener");
        IntentFilter discoverableIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        BroadcastReceiver discoverableReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // Only check this intent if it's the right action.
                if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                    // Get the Scan mode value returned and process it.
                    int modeValue = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                    if (modeValue == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
                        toast("Not in discoverable mode but can still receive connections");
                    } else if (modeValue == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        toast("In Discoverable mode");
                    } else if (modeValue == BluetoothAdapter.SCAN_MODE_NONE) {
                        toast("Not discoverable and can't receive connections");
                    } else {
                        toast("BluetoothAdapter.ACTION_SCAN_MODE_CHANGED Error");
                    }
                }
            }
        };
        registerReceiver(discoverableReceiver, discoverableIntentFilter);
    }

    /**
     * This is the default callback function for when a spawned intent finishes and returns.
     * @param requestCode - code representing the type of request this intent was for.
     * @param resultCode - code representing if this intent returns successfully or not.
     * @param data - TODO look up what this passed param is
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ENABLE_BT_REQUEST) { // Handle request BT intent return
            if (resultCode == RESULT_OK) { // If success
                Log.d(LOG_TAG, "BT was manually enabled.");
                toast("Bluetooth has been enabled");
            } else if (resultCode == RESULT_CANCELED) { // If cancelled
                Log.d(LOG_TAG, "BT was enabling was cancelled.");
                toast("Bluetooth enabling has been cancelled");
            }
        }
    }

    /**
     * Makes a long toast with the specified txt on the android device.
     * @param txt - String to use as the Toast message.
     */
    private void toast(String txt) {
        Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (myBtAdapter.isDiscovering()) {
            myBtAdapter.cancelDiscovery();
        }
        super.onDestroy();
    }
}
