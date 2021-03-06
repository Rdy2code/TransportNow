package com.example.android.transportapp;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.icu.text.TimeZoneFormat;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

public class TransportAdapter extends RecyclerView.Adapter<TransportAdapter.TransportViewHolder> {

    private final static String LOG_TAG = TransportAdapter.class.getSimpleName();

    //Instance Variables for collecting and storing data from Firebase
    private Context mContext;
    private ArrayList<Transport> mTransports;

    //Member variables
    final private ListItemClickListener mOnClickListener;
    private String mSubstringOriginCity;
    private String mSubstringDestinationCity;

    //ListItemClickListener Interface
    public interface ListItemClickListener {
        void onListItemClick (int clickedItemIndex);
    }

    //Constructor for instantiating a new adapter
    public TransportAdapter (Context context,
                             ArrayList<Transport> transports,
                             ListItemClickListener listener) {
        mContext = context;
        mTransports = transports;
        mOnClickListener = listener;
    }

    @NonNull
    @Override
    public TransportViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {

        //Inflate the list_item_layout
        Context context = viewGroup.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.list_item_layout, viewGroup, false);

        //Attach the inflated view to the ViewHolder
        TransportViewHolder viewHolder = new TransportViewHolder(view);

        //Return the ViewHolder
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull TransportViewHolder transportViewHolder, int position) {

        //Get a Transport object from the ArrayList<Transport>
        Transport transport = mTransports.get(position);

        //Trim the state from the strings for origin and destination locales
        if (transport.getOriginCity() != null) {
            if (transport.getOriginCity().contains(",")) {
                int comma =  transport.getOriginCity().indexOf(',');
                mSubstringOriginCity =  transport.getOriginCity().substring(0, comma);
            } else {
                mSubstringOriginCity = transport.getOriginCity();
            }
        }

        if (transport.getDestinationCity() != null) {
            if (transport.getOriginCity().contains(",")) {
                int comma =  transport.getDestinationCity().indexOf(',');
                mSubstringDestinationCity =  transport.getDestinationCity().substring(0, comma);
            } else {
                mSubstringDestinationCity = transport.getDestinationCity();
            }
        }

        if (transport.getUrgency()) {
            transportViewHolder.urgentTV.setVisibility(View.VISIBLE);
        } else {
            transportViewHolder.urgentTV.setVisibility(View.INVISIBLE);
        }

        //Calculate the time difference in days between the date posted and the current date
        //TODO: Review use of java.time framework to handle timezone, leap years, daylight saving
        long datePosted = transport.getTimestampLong();
        long currentDate = new Date().getTime();
        String daysPassedSinceUpdate = "";
        long timeDifference = Math.abs(currentDate - datePosted);

        if (timeDifference < 86400000) {
                daysPassedSinceUpdate = "Today";
            } else {
                daysPassedSinceUpdate = String.valueOf(timeDifference/86400000) + " days ago";
            }

        //Bind the data to the views inside the ViewHolder object instance
        transportViewHolder.transportStatusTv.setText(transport.getStatus());
        transportViewHolder.originCityTv.setText(mSubstringOriginCity);
        transportViewHolder.destinationCityTv.setText(mSubstringDestinationCity);
        transportViewHolder.dateTv.setText(transport.getDateNeededBy());
        transportViewHolder.genderTv.setText(transport.getGender());
        transportViewHolder.nameTv.setText(transport.getName());
        transportViewHolder.daysSincePostTv.setText(daysPassedSinceUpdate);
        transportViewHolder.iDTv.setText(transport.getTransportId());
    }

    @Override
    public int getItemCount() {
        if (mTransports == null) {
            return 0;
        }
        return mTransports.size();
    }

    public void setTransportData (ArrayList<Transport> transports) {
        mTransports = transports;
        notifyDataSetChanged();
    }

    class TransportViewHolder extends RecyclerView.ViewHolder implements
    View.OnClickListener {

        @BindView(R.id.transport_status) TextView transportStatusTv;
        @BindView(R.id.textView_origin_city) TextView originCityTv;
        @BindView(R.id.textView_destination_city) TextView destinationCityTv;
        @BindView(R.id.textView_date) TextView dateTv;
        @BindView(R.id.textView_gender) TextView genderTv;
        @BindView(R.id.textView_name) TextView nameTv;
        @BindView(R.id.textview_days_since_post) TextView daysSincePostTv;
        @BindView(R.id.textView_id) TextView iDTv;
        @BindView(R.id.urgent_textview) TextView urgentTV;

        public TransportViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int clickedPosition = getAdapterPosition();
            mOnClickListener.onListItemClick(clickedPosition);
        }
    }

    private long getTimeDifferenceBetweenDates (String dateNeededBy) {
        SimpleDateFormat dateNeeded = new SimpleDateFormat("MMM dd, YYYY");
        long timeInMillis = 0;
        try {
            Date date = dateNeeded.parse(dateNeededBy);
            timeInMillis = date.getTime();
            Log.d("TransportAdapter", "the time is " + date.getTime());
        } catch (java.text.ParseException e) {
            e.printStackTrace();
        }
        Log.d("TransportAdapter", "time in millis is " + timeInMillis);
        return timeInMillis;
    }
}
