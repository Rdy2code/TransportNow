package com.example.android.transportapp;

        import android.app.Application;

        import com.google.firebase.database.FirebaseDatabase;

public class TransportApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //Enable offline work with locally cached data
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
