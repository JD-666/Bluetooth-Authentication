package com.example.bluetooth1;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;

public class ChatActivity extends AppCompatActivity {

    private static final String LOG_TAG = ChatActivity.class.getSimpleName();

    // Stateful attributes needed
    private BluetoothAdapter myBtAdapter;
    Set<BluetoothDevice> btDevices;
    private ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private RecyclerView mRecyclerView;
    private TextView chatStatusView;
    private EditText sendMsgView;
    private ArrayList<String> msgs = new ArrayList<>();
    private SendReceive sendReceiveThread;
    MsgListAdapter msgAdapter;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        findViewsByIds(); // Get needed View references
        myBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Establish a msg adapter to work with the recyclerview
        msgAdapter= new MsgListAdapter(getApplicationContext(), msgs);
        setRecyclerViewAdapter(msgAdapter);

        //Intent intent = getIntent(); // Get intent obj from whoever activated us

    }

    private void setRecyclerViewAdapter(RecyclerView.Adapter a) {
        // Connect the adapter the recycler view
        mRecyclerView.setAdapter(msgAdapter);
        // Give the recycler view a default layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
    }

    private void findViewsByIds() {
        // Get a reference to some of the views for later use
        mRecyclerView = findViewById(R.id.msg_recycler);
        chatStatusView = findViewById(R.id.chat_status);
        sendMsgView = findViewById(R.id.send_msg_edittext);
    }

    private void toast(String txt) {
        Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_LONG).show();
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
                pairedDevices.add(device);
            }
        }
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
        setRecyclerViewAdapter(msgAdapter);
    }

    public void startClientChat(BluetoothDevice device) {
        toast(device.getName());
        ClientClass client = new ClientClass(device);
        setRecyclerViewAdapter(msgAdapter);
        msgAdapter.notifyDataSetChanged();
        client.start();

        // TODO consider making a new ListAdapter for text messages so that we don't have onClick'spawning more child threads each time.
        // TODO or maybe just set a flag CLIENT_RUNNING = true. So that new clicks do nothing,
        // TODO if no client is running then start it and switch flag.
    }


    // This Handler acts as a mechanism for threads to send messages to this main activity.
    // It is an event-driven handler that activated when a thread uses handler.sendMesage().
    Handler messageHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    chatStatusView.setText("Listening");
                    Log.d(LOG_TAG, "LISTENING");
                    break;
                case STATE_CONNECTING:
                    chatStatusView.setText("Connecting");
                    Log.d(LOG_TAG, "CONNECTING");
                    break;
                case STATE_CONNECTED:
                    chatStatusView.setText("Connected");
                    Log.d(LOG_TAG, "CONNECTED");
                    break;
                case STATE_CONNECTION_FAILED:
                    chatStatusView.setText("Connection Failed");
                    Log.d(LOG_TAG, "CONNECTION FAILED");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    Log.d(LOG_TAG, "MESSAGE RECEIVED");
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    // now add this msg to recycler view
                    msgs.add(tempMsg);
                    msgAdapter.notifyDataSetChanged();
                    break;
            }
            return true;
        }
    });

    public void sendTextMsg(View view) {
        String m = String.valueOf(sendMsgView.getText());
        sendMsgView.setText("");
        msgs.add(m);
        msgAdapter.notifyDataSetChanged();
        sendReceiveThread.write(m.getBytes());
    }
    //----------------------------------------------------------------------------------------------

    /**
     * This class can be deleted. It is just a demo to show how messages from a Thread are passed
     * to the main activity using a Handler as a middle man.
     * TODO Delete
     */
    private class MessageThread extends Thread {
        @Override
        public void run() {
            for (int i=0; i<50; i++) {
                Message message = Message.obtain();
                message.arg1 = i;
                messageHandler.sendMessage(message);
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
