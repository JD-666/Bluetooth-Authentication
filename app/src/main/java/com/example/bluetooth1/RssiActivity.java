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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class RssiActivity extends AppCompatActivity {

    private static final String LOG_TAG = RssiActivity.class.getSimpleName();

    // Statefull attributes needed
    private BluetoothAdapter myBtAdapter;
    private Set<BluetoothDevice> btDevices;
    private ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private int smoothedRssi;
    private double alpha = 0.8; // alpha smoothing coefficient

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
    private boolean UNLOCKED = false; // keep track of when device becomes unlocked
    private boolean CONNECTED = false; // Keep track of when we have a BT socket connection

    // Threads
    private Timer rssiTimer;
    private SendReceive sendReceiveThread;

    BluetoothGatt btGatt;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;
    static final int STATE_IMAGE_RECEIVED = 6;

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
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.lanterns));
        // TODO consider having the server constantly send messages to the client about it's RSSI
    }

    public void startClientChat(BluetoothDevice device) {
        imageView.setImageResource(0); // remove image if one exists
        toast(device.getName()); // show the selected device
        hideRecycler(); // remove devices list from display
        ClientClass client = new ClientClass(device);
        client.start();

        // Initiate client RSSI callbacks
        // TODO consider having the client NOT read RSSI, instead read messages from the server and display that

        //btGatt = device.connectGatt(this, false, clientGattCallback);
    }

    private void setDistanceView(int rssi) {
        // compute distance
        // TODO create a more advance algorithm based on those papers or some sort of calibration
        // Rolling average + calibration + bucketizing
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

    private void setResourceLabel(boolean unlocked) {
        if (unlocked) {
            resourceLabel.setText(R.string.resource_unlocked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorUnlocked));
        } else {
            resourceLabel.setText(R.string.resource_locked);
            resourceLabel.setTextColor(ContextCompat.getColor(this, R.color.colorLocked));
        }
    }


    public void sendTextMsg(String txt) {
        //String m = String.valueOf(sendMsgView.getText());
        //sendMsgView.setText("");
        //msgs.add(m);
        //msgAdapter.notifyDataSetChanged();
        // TODO add some sort of code so they know this is a text and not an image
        //sendReceiveThread.write(txt.getBytes());
    }

    public void sendImage() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.lanterns);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
        byte[] imageBytes = stream.toByteArray();

        int subArraySize = 400;

        //sendReceiveThread.writeInt(2);
        // First send the number of bytes in the image so client knows how much to read
        sendReceiveThread.write(String.valueOf(imageBytes.length).getBytes());
        //sendReceiveThread.writeInt(imageBytes.length);

        // Then send the image
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
                case STATE_MESSAGE_RECEIVED:
                    Log.d(LOG_TAG, "MESSAGE RECEIVED");
                    byte[] readBuff = (byte[]) msg.obj;
                    // Message is received but we don't do anything with it.
                    break;
                case STATE_IMAGE_RECEIVED:
                    Log.d(LOG_TAG, "IMAGE RECEIVED");
                    byte[] buff = (byte[]) msg.obj; // All the image bytes
                    // 0 is starting arg1 is total bytes
                    Bitmap bitmap = BitmapFactory.decodeByteArray(buff, 0, msg.arg1);
                    imageView.setImageBitmap(bitmap);
                    break;
            }
            return true;
        }
    });
    //----------------------------------------------------------------------------------------------
    /**
     * This is a callback for all Gatt activities on hte client side
     */
    private final BluetoothGattCallback clientGattCallback = new BluetoothGattCallback() {
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
                rssiTimer.schedule(task, 500, 500);
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(LOG_TAG, String.valueOf(rssi));
            smoothedRssi = (int) ((smoothedRssi * alpha) + ((1 - alpha) * rssi));
            setDistanceView(smoothedRssi);
            rssiView.setText(String.valueOf(smoothedRssi));
            if (smoothedRssi >= 0) {
                setResourceLabel(true);
            } else {
                setResourceLabel(false);
            }
        }
    };

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
                rssiTimer.schedule(task, 500, 500);
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(LOG_TAG, String.valueOf(rssi));
            smoothedRssi = (int) ((smoothedRssi * alpha) + ((1 - alpha) * rssi));
            setDistanceView(smoothedRssi);
            rssiView.setText(String.valueOf(smoothedRssi));
            if (!UNLOCKED) { // If locked
                if (smoothedRssi >= 0) {
                    setResourceLabel(true);
                    UNLOCKED = true;
                    sendImage();
                    // Remove image from our screen after we send it
                    imageView.setImageResource(0);
                }
            }
            // TODO when to reset back to locked? never?
        }
    };


    //----------------------------------------------------------------------------------------------
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

                    // Connect the communicatino thread
                    sendReceiveThread = new SendReceive(socket);
                    sendReceiveThread.start();

                    // Connect the RSSI callback
                    remoteDevice = socket.getRemoteDevice();
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
     * Note we never call it's write message because we don't use the connection to send data.
     * Instead we only maintain socket connections to keep the connection open, so we can
     * Monitor the RSSI over the channel.
     * // TODO maybe write
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
            byte[] buffer = new byte[1024];
            int totalBytes = 0;
            int index = 0;
            boolean waitingForImage = true;
            while(true) {
                // TODO consider inputStream.read() to get message code, then process images vs text separately.
                if (waitingForImage) {
                    try {
                        // TODO try sending and receiving an Int... if we can get that to work..
                        // Read image size (first message)
                        byte[] temp = new byte[dataInputStream.available()];
                        if (dataInputStream.read(temp) > 0) {
                            totalBytes = Integer.parseInt(new String(temp, StandardCharsets.UTF_8));
                            Log.d(LOG_TAG, "totalBytes: "+totalBytes);
                            buffer = new byte[totalBytes];
                            waitingForImage = false;
                        }
                    } catch (IOException e) {
                        Message message = Message.obtain();
                        message.what = STATE_CONNECTION_FAILED;
                        messageHandler.sendMessage(message);
                        e.printStackTrace();
                        break;
                    }
                } else {
                    // We've read image size, now read that many bytes
                    try {
                        byte[] data = new byte[dataInputStream.available()];
                        int bytesRead = dataInputStream.read(data);
                        System.arraycopy(data, 0, buffer, index, bytesRead);
                        index = index + bytesRead;
                        if (index == totalBytes) {
                            messageHandler.obtainMessage(STATE_IMAGE_RECEIVED, totalBytes, -1, buffer).sendToTarget();
                            // Reset flag to go back to waiting for image length message
                            waitingForImage = true;
                        }
                    } catch (IOException e) {
                        Message message = Message.obtain();
                        message.what = STATE_CONNECTION_FAILED;
                        messageHandler.sendMessage(message);
                        e.printStackTrace();
                        break;
                    }

                }
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
