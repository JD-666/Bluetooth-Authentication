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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class RssiActivity extends AppCompatActivity {

    private static final String LOG_TAG = RssiActivity.class.getSimpleName();
    private static final int S10_TX_PWR = -6; // This is S10 txpwr. Used when Pixel is server
    private static final int PIXEL_TX_PWR = 14; // This is pixel txpwr. Used when S10 is server
    private static final String S10_ADDRESS = "A8:2B:B9:A8:90:9B";
    private static final String PIXEL_ADDRESS = "AC:37:43:BB:CA:30";

    // Statefull attributes needed
    private BluetoothAdapter myBtAdapter;
    private Set<BluetoothDevice> btDevices;
    private ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private int smoothedRssi = -99;
    private double alpha = 0.7; // alpha smoothing coefficient
    private int txPwr = S10_TX_PWR; // default

    // Views
    private RecyclerView mRecyclerView;
    private TextView rssiView;
    private TextView dstView;
    private TextView connStatusView;
    private TextView rssiLabel;
    private TextView dstLabel;
    private TextView resourceLabel;
    private ImageView imageView;

    // Flags
    private boolean LOCKED = true; // keep track of when device becomes unlocked
    private boolean CONNECTED = false; // Keep track of when we have a BT socket connection

    // Threads
    private Timer rssiTimer;
    private SendReceive sendReceiveThread;

    BluetoothGatt btGatt;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STRING_MESSAGE = 5;
    static final int IMAGE_MESSAGE = 6;
    static final int RSSI_STR_MESSAGE = 7;

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
        imageView = findViewById(R.id.imageView);
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
        imageView.setVisibility(View.VISIBLE);
    }

    private void showRecyclerView() {
        mRecyclerView.setVisibility(View.VISIBLE);
        rssiView.setVisibility(View.GONE);
        dstView.setVisibility(View.GONE);
        rssiLabel.setVisibility(View.GONE);
        dstLabel.setVisibility(View.GONE);
        resourceLabel.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
    }

    // This method is the callback for the client button to select a device
    public void showPairedDevices(View view) {
        if (!myBtAdapter.isEnabled()) { // If BlueTooth is off then return
            toast("Please enable Bluetooth");
            return;
        }
        if (isConnected()) {
            toast("already connected");
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
     */
    public void enableDiscoverable(View view) {
        if (!myBtAdapter.isEnabled()) {
            toast("Please enable Bluetooth...");
            return;
        }
        if (isConnected()) {
            toast("already connected");
            return;
        }
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 20);
        startActivity(discoverableIntent);

        ServerClass serverChat = new ServerClass();
        serverChat.start();
        // Set image on screen then later delete it when image is sent
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.gmurobot));
    }

    public void startClientChat(BluetoothDevice device) {
        imageView.setImageResource(0); // remove image if one exists
        toast(device.getName()); // show the selected device
        hideRecycler(); // remove devices list from display
        ClientClass client = new ClientClass(device);
        client.start();
    }



    double getDistance(int rssi, double txPwr) {
         // RSSI = TxPower - 10 * n * lg(d)
         // n = 2 (in free space)
         // d = 10 ^ ((TxPower - RSSI) / (10 * n))
        int n = 2;
        return Math.pow(10d, (txPwr - rssi) / (10 * n));
    }

    double getTxPwr(double distance, int rssi) {
        int n = 2;
        return rssi + (10 * n * Math.log10(distance));
    }

    double getRssi(double distance, double txpwr) {
        int n = 2;
        return txpwr - (10 * n * Math.log10(distance));
    }


    private void setResourceLabel(boolean unlocked) {
        if (unlocked) {
            resourceLabel.setText(R.string.resource_unlocked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorUnlocked));
        } else {
            resourceLabel.setText(R.string.resource_locked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorLocked));
        }
    }

    private void setResourceLabel(String status) {
        if (status.equals("Locked")) {
            setResourceLabel(false);
        } else if (status.equals("Unlocked")) {
            setResourceLabel(true);
        }
    }

    public void sendImage() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gmurobot);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
        byte[] imageBytes = stream.toByteArray();

        // Our protocol says first send: [int type] [int payloadSize] [bytes data]
        sendReceiveThread.writeInt(IMAGE_MESSAGE);
        sendReceiveThread.writeInt(imageBytes.length);
        // Then send the image
        int subArraySize = 400;
        for (int i = 0; i < imageBytes.length; i += subArraySize) {
            byte[] tempArray;
            tempArray = Arrays.copyOfRange(imageBytes, i, Math.min(imageBytes.length, i+subArraySize));
            sendReceiveThread.write(tempArray);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void setStatusConnected() {
        connStatusView.setText("Connected");
        CONNECTED = true;
    }

    private void setStatusDisconnected() {
        connStatusView.setText("Disconnected");
        CONNECTED = false;
    }

    private boolean isConnected() {
        return CONNECTED;
    }

    //----------------------------------------------------------------------------------------------
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
                    setStatusConnected();
                    Log.d(LOG_TAG, "CONNECTED");
                    break;
                case STATE_CONNECTION_FAILED:
                    connStatusView.setText("Connection Failed");
                    setStatusDisconnected();
                    Log.d(LOG_TAG, "CONNECTION FAILED");
                    break;
                case STRING_MESSAGE:
                    Log.d(LOG_TAG, "MESSAGE RECEIVED");
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    Log.d(LOG_TAG, tempMsg);
                    break;
                case IMAGE_MESSAGE:
                    Log.d(LOG_TAG, "IMAGE RECEIVED");
                    byte[] buff = (byte[]) msg.obj; // All the image bytes
                    // 0 is starting arg1 is total bytes
                    Bitmap bitmap = BitmapFactory.decodeByteArray(buff, 0, msg.arg1);
                    imageView.setImageBitmap(bitmap);
                    break;
                case RSSI_STR_MESSAGE:
                    Log.d(LOG_TAG, "RSSI MSG RECEIVED");
                    byte[] rBuff = (byte[]) msg.obj;
                    String vals = new String(rBuff, 0, msg.arg1);
                    String[] parts = vals.split(",");
                    String rssi = parts[0];
                    String dst = parts[1];
                    String status = parts[2];
                    Log.d(LOG_TAG, "rssi: "+rssi+". dst: "+dst+" status: "+status);

                    rssiView.setText(rssi);
                    dstView.setText(dst);
                    setResourceLabel(status);

                    break;
            }
            return true;
        }
    });

    //----------------------------------------------------------------------------------------------
    /**
     * This is a callback for all Gatt activities on the server side
     */
    private final BluetoothGattCallback serverGattCallback = new BluetoothGattCallback() {
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
            smoothedRssi = (int) ((smoothedRssi * alpha) + ((1 - alpha) * rssi));
            //int dst = getDistanceFromRssi(smoothedRssi);
            double dst = getDistance(smoothedRssi, txPwr);
            dstView.setText(""+String.format("%.2f", dst));
            rssiView.setText(String.valueOf(smoothedRssi));

            if (smoothedRssi >= 0) {
                if (LOCKED) { // If locked
                    tellClientTheDistance(smoothedRssi, dst, "Unlocked");
                    setResourceLabel(true);
                    LOCKED = false;
                    sendImage();
                    // Remove image from our screen after we send it
                    imageView.setImageResource(0);
                }
            } else {
                if (LOCKED) {
                    tellClientTheDistance(smoothedRssi, dst, "Locked");
                }
            }
            // TODO when to reset back to locked? never? Maybe a reset button..
        }
    };

    private void tellClientTheDistance(int rssi, double distance, String status) {
        String message = rssi+","+String.format("%.2f", distance)+","+status;
        sendReceiveThread.writeInt(RSSI_STR_MESSAGE);
        sendReceiveThread.write(message.getBytes());
    }

    //----------------------------------------------------------------------------------------------
    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;

        public ServerClass() {
            try {
                serverSocket = myBtAdapter.listenUsingRfcommWithServiceRecord(MainActivity.APP_NAME, MainActivity.APP_UUID);
            } catch (IOException e) {
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                messageHandler.sendMessage(message);
                e.printStackTrace();
            }
        }

        public void run() {
            BluetoothSocket socket = null;
            BluetoothDevice remoteDevice;
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

                    // Connect the communication thread
                    sendReceiveThread = new SendReceive(socket);
                    sendReceiveThread.start();

                    // Set txPwr variable based on who the client is.
                    remoteDevice = socket.getRemoteDevice();
                    if (remoteDevice.getAddress().equals(PIXEL_ADDRESS)) {
                        txPwr = PIXEL_TX_PWR;
                    } else if (remoteDevice.getAddress().equals(S10_ADDRESS)) {
                        txPwr = S10_TX_PWR;
                    }
                    Log.d(LOG_TAG,"remote device = " + remoteDevice.getAddress());

                    // Connect the RSSI callback
                    btGatt = remoteDevice.connectGatt(getApplicationContext(), false, serverGattCallback);
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
            Message message = Message.obtain();
            message.what = STATE_CONNECTION_FAILED;
            messageHandler.sendMessage(message);
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
     */
    private class SendReceive extends Thread {
        private final BluetoothSocket btSocket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private DataInputStream dataInputStream;
        private DataOutputStream dataOutputStream;

        public SendReceive(BluetoothSocket socket) {
            this.btSocket = socket;
            try {
                this.inputStream = btSocket.getInputStream();
                this.outputStream = btSocket.getOutputStream();
                dataInputStream = new DataInputStream(new BufferedInputStream(inputStream));
                dataOutputStream = new DataOutputStream(new BufferedOutputStream(outputStream));
            } catch (IOException e) {
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                messageHandler.sendMessage(message);
                e.printStackTrace();
            }
        }

        // Receive messages loop
        public void run() {
            int imageSize = 0;
            while(true) {
                try {
                    // Read the message type first
                    int type = dataInputStream.readInt();
                    Log.d(LOG_TAG, "type: " + type);
                    if (type == IMAGE_MESSAGE) {
                        // Read image size (second message)
                        imageSize = dataInputStream.readInt();
                        Log.d(LOG_TAG, "imageSize: " + imageSize);
                        processImage(imageSize);
                    } else if (type == STRING_MESSAGE || type == RSSI_STR_MESSAGE) {
                        processMessage(type);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        private void processImage(int imageSize) {
            byte[] buffer = new byte[imageSize];
            try {
                dataInputStream.readFully(buffer);
                messageHandler.obtainMessage(IMAGE_MESSAGE, imageSize, -1, buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processMessage(int type) {
            byte[] buffer = new byte[1024];
            int bytes;
            try {
                bytes = dataInputStream.read(buffer);
                messageHandler.obtainMessage(type, bytes, -1, buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void write(byte[] bytes) {
            try {
                dataOutputStream.write(bytes);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void writeInt(int i) {
            try {
                dataOutputStream.writeInt(i);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //----------------------------------------------------------------------------------------------

}
