package com.example.bluetooth1;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

    private LayoutInflater inflater;
    private List<String> deviceList;

    /**
     *
     * @param context
     * @param deviceList
     */
    public DeviceListAdapter(Context context, List<String> deviceList) {
        this.inflater = LayoutInflater.from(context);
        this.deviceList = deviceList;
    }

    /**
     * Creates a view and returns it.
     * @param parent - reference to parent viewgroup RecyclerView
     * @param viewType
     * @return
     */
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate an item view
        View deviceView = inflater.inflate(R.layout.bt_device_item, parent, false);;
        return new DeviceViewHolder(deviceView, this);
    }

    /**
     * Associates the data with the ViewHolder for a given position in the RecyclerView.
     * @param holder
     * @param position
     */
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        // Retrieve the data for that position
        String current = deviceList.get(position);
        // Add the data to the view
        holder.deviceItemView.setText(current);
    }

    /**
     * @return The number of data items available for displaying
     */
    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView deviceItemView;
        DeviceListAdapter adapter;

        DeviceViewHolder(View itemView, DeviceListAdapter adapter) {
            super(itemView);
            this.deviceItemView = itemView.findViewById(R.id.bt_item);
            this.adapter = adapter;
            deviceItemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            deviceItemView.setText("Clicked!" + deviceItemView.getText());
        }


    }
}
