package com.example.android.transportapp;

import android.app.Application;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class TransportApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //Enable offline work with locally cached data
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
