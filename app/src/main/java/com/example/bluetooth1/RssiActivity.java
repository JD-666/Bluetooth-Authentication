package com.example.bluetooth1;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class RssiActivity extends AppCompatActivity {

    private static final String LOG_TAG = RssiActivity.class.getSimpleName();

    // Statefull attributes needed
    private BluetoothAdapter myBtAdapter;
    Set<BluetoothDevice> btDevices;
    private ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private SendReceive sendReceiveThread;
    private RecyclerView mRecyclerView;
    private TextView rssiView;
    private TextView dstView;
    private TextView connStatusView;
    private TextView rssiLabel;
    private TextView dstLabel;
    private TextView resourceLabel;

    // Threads
    private Timer rssiTimer;

    BluetoothGatt btGatt;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rssi);

        findViewbyIds(); // get needed view references
        myBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void findViewbyIds() {
        mRecyclerView = findViewById(R.id.rssi_recycler);
        rssiView = findViewById(R.id.rssi_val);
        dstView = findViewById(R.id.dst_val);
        connStatusView = findViewById(R.id.conn_status);
        rssiLabel = findViewById(R.id.rssi_textview);
        dstLabel = findViewById(R.id.dst_textview);
        resourceLabel = findViewById(R.id.resource_textview);
    }

    private void toast(String txt) {
        Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_LONG).show();
    }

    private void hideRecycler() {
        mRecyclerView.setVisibility(View.GONE);
        rssiView.setVisibility(View.VISIBLE);
        dstView.setVisibility(View.VISIBLE);
        rssiLabel.setVisibility(View.VISIBLE);
        dstLabel.setVisibility(View.VISIBLE);
        resourceLabel.setVisibility(View.VISIBLE);
    }

    private void showRecyclerView() {
        mRecyclerView.setVisibility(View.VISIBLE);
        rssiView.setVisibility(View.GONE);
        dstView.setVisibility(View.GONE);
        rssiLabel.setVisibility(View.GONE);
        dstLabel.setVisibility(View.GONE);
        resourceLabel.setVisibility(View.GONE);
    }

    // This method is the callback for the client button to select a device
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
                pairedDevices.add(device);
            }
        }
        showRecyclerView(); // show devices on screen
        // Create an adapter and supply the data to be displayed
        DeviceListAdapter adapter = new DeviceListAdapter(this, pairedDevices);
        // Connect the adapter the recycler view
        mRecyclerView.setAdapter(adapter);
        // Give the recycler view a default layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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

        // TODO consider some flag to keep track if server is running to so we don't spawn multiple instances
        ServerClass serverChat = new ServerClass();
        serverChat.start();
    }

    public void startClientChat(BluetoothDevice device) {
        toast(device.getName());
        hideRecycler();
        ClientClass client = new ClientClass(device);
        client.start();


        btGatt = device.connectGatt(this, false, btGattCallback);

        /* TODO consider making a flag: CLIENT_RUNNING = true. So that new clicks do nothing,
           TODO if no client is running then start it and switch flag.
         */
    }

    /**
     * This is a callback for all Gatt activities
     */
    private final BluetoothGattCallback btGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        btGatt.readRemoteRssi();
                    }
                };
                rssiTimer = new Timer();
                rssiTimer.schedule(task, 1000, 1000);
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(LOG_TAG, String.valueOf(rssi));
            setDistanceView(rssi);
            setResourceLabel(rssi);
            rssiView.setText(String.valueOf(rssi));
        }
    };

    private void setDistanceView(int rssi) {
        // compute distance
        String dst;
        if (rssi > -5) {
            dst = "<3m";
        } else if (rssi > -10) {
            dst = "<5m";
        } else if (rssi > -15) {
            dst = "<8m";
        } else if (rssi > -20) {
            dst = "<10m";
        } else if (rssi > -25) {
            dst = "<12m";
        } else if (rssi > -30) {
            dst = "<14m";
        } else {
            dst = ">14m";
        }
        dstView.setText(dst);
    }

    private void setResourceLabel(int rssi) {
        if (rssi == 0) {
            resourceLabel.setText(R.string.resource_unlocked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorUnlocked));
        } else {
            resourceLabel.setText(R.string.resource_locked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorLocked));
        }
    }


    // This Handler acts as a mechanism for threads to send messages to this main activity.
    // It is an event-driven handler that activated when a thread uses handler.sendMesage().
    Handler messageHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    connStatusView.setText("Listening");
                    Log.d(LOG_TAG, "LISTENING");
                    break;
                case STATE_CONNECTING:
                    connStatusView.setText("Connecting");
                    Log.d(LOG_TAG, "CONNECTING");
                    break;
                case STATE_CONNECTED:
                    connStatusView.setText("Connected");
                    Log.d(LOG_TAG, "CONNECTED");
                    break;
                case STATE_CONNECTION_FAILED:
                    connStatusView.setText("Connection Failed");
                    Log.d(LOG_TAG, "CONNECTION FAILED");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    Log.d(LOG_TAG, "MESSAGE RECEIVED");
                    byte[] readBuff = (byte[]) msg.obj;
                    // Message is received but we don't do anything with it.
                    break;
            }
            return true;
        }
    });

/*
    public void sendTextMsg(View view) {
        //TODO this never gets called because no send button, no callback
        //String m = String.valueOf(sendMsgView.getText());
        //sendMsgView.setText("");
        //msgs.add(m);
        //msgAdapter.notifyDataSetChanged();
        //sendReceiveThread.write(m.getBytes());
    }
 */

    @Override
    protected void onPause() {
        super.onPause();
    }


    //----------------------------------------------------------------------------------------------
    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;

        public ServerClass() {
            try {
                serverSocket = myBtAdapter.listenUsingRfcommWithServiceRecord(MainActivity.APP_NAME, MainActivity.APP_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            BluetoothSocket socket = null;
            while (socket == null) {
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING; //look at video 10/11
                    messageHandler.sendMessage(message);

                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED; // look at video 10/11
                    messageHandler.sendMessage(message);
                }
                // Begin
                if (socket != null) {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED; // look at video 10/11
                    messageHandler.sendMessage(message);

                    sendReceiveThread = new SendReceive(socket);
                    sendReceiveThread.start();
                    break;
                }
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    private class ClientClass extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

    ClientClass(BluetoothDevice device) {
        this.device = device;
        try {
            socket = this.device.createRfcommSocketToServiceRecord(MainActivity.APP_UUID);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            socket.connect();
            Message message = Message.obtain();
            message.what = STATE_CONNECTED;
            messageHandler.sendMessage(message);

            sendReceiveThread = new SendReceive(socket);
            sendReceiveThread.start();
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            message.what = STATE_CONNECTION_FAILED;
            messageHandler.sendMessage(message);
        }
    }
}
//----------------------------------------------------------------------------------------------

    /**
     * This class handles socket connections for the bluetooth connection.
     * Note we never call it's write message because we don't use the connection to send data.
     * Instead we only maintain socket connections to keep the connection open, so we can
     * Monitor the RSSI over the channel.
     */
    private class SendReceive extends Thread {
        private final BluetoothSocket btSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(BluetoothSocket socket) {
            this.btSocket = socket;
            try {
                this.inputStream = btSocket.getInputStream();
                this.outputStream = btSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while(true) {
                try {
                    // read message on the socket.
                    // send to handler so that main thread can obtain
                    bytes = inputStream.read(buffer);
                    messageHandler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //----------------------------------------------------------------------------------------------



}
