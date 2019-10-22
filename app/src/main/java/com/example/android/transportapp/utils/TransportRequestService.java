package com.example.android.transportapp.utils;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.android.transportapp.Transport;
import com.example.android.transportapp.TransportWidgetProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class TransportRequestService extends IntentService {

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mTransportsDatabaseReference;
    private ChildEventListener mChildEventListener;
    private Transport mMostRecentTransport;
    private ArrayList<Transport> mTransports;
    private List<Long> mTimestamps;

    //Define the actions that the IntentService will handle
    public static final String ACTION_GET_LATEST_TRANSPORT = "com.example.android.transportapp.action.get_transport";

    public TransportRequestService() {
        super("TransportRequestService");
    }

    //Public method that will trigger the Service to perform the specific action
    public static void getLatestTransport (Context context) {
        Intent intent = new Intent (context, TransportRequestService.class);
        intent.setAction(ACTION_GET_LATEST_TRANSPORT);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_GET_LATEST_TRANSPORT.equals(action)) {
                handleActionGetLatestTransport();
            }
        }
    }

    //Get the latest transport
    private void handleActionGetLatestTransport() {
        //Firebase Realtime Database setup: Main Access point
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        //Get a reference to the section of the database where Transports are stored
        mTransportsDatabaseReference = mFirebaseDatabase.getReference().child("transports");

        mTransports = new ArrayList<Transport>();
        mTimestamps = new ArrayList<>();
        mMostRecentTransport = new Transport();

        mChildEventListener = new ChildEventListener() {

            //Triggered for existing children when listener is first attached and then again
            //when any future children are added while the listener is attached
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Transport transport = dataSnapshot.getValue(Transport.class);
                mTransports.add(transport);
                long index = 0;
                long currentTime = transport.getTimestampLong();
                int a = 0;

                mTimestamps.add(currentTime);

                for (int i = 0; i < mTimestamps.size(); i++) {
                    if (mTimestamps.get(i) > index) {
                        index = mTimestamps.get(i);
                        a = i;
                    }
                }

                mMostRecentTransport = mTransports.get(a);
                sendToUpdateAppWidgets(mMostRecentTransport);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        };

        mTransportsDatabaseReference.addChildEventListener(mChildEventListener);
    }

    private void sendToUpdateAppWidgets(Transport recentTransport) {
        String dateNeededBy = recentTransport.getDateNeededBy();
        String originCity = recentTransport.getOriginCity();
        String destinationCity = recentTransport.getDestinationCity();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, TransportWidgetProvider.class));
        //Update all widgets
        TransportWidgetProvider.updateTransportWidgets(this,
                appWidgetManager,
                appWidgetIds,
                dateNeededBy,
                originCity,
                destinationCity);
    }
}
