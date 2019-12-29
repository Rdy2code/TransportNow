package com.example.android.transportapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.transportapp.utils.TransportRequestService;
import com.firebase.ui.auth.AuthUI;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements TransportAdapter.ListItemClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final String ANONYMOUS = "anonymous";

    //Key for retrieving recycler view layout from savedInstanceState bundle on rotation
    private static final String RECYCLER_LAYOUT = "recycler_layout_key";
    private static final String SAVED_RECYCLER_VIEW_DATASET_ID = "recipe_list_key";
    private Parcelable mListState;

    //Firebase Realtime Database object reference entry points
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mTransportsDatabaseReference;
    private ChildEventListener mChildEventListener;
    private boolean mIsConnected;
    private int mClickedItemIndex;
    private int mOnChildAddedCount;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseUser mUser;
    private static final int RC_SIGN_IN = 1;    //Request code for FirebaseAuthStateListener
    private String mUsername;

    //RecyclerView member variables
    private TransportAdapter mAdapter;
    private ArrayList<Transport> mTransports;
    @BindView(R.id.recyclerview_transports) RecyclerView mRecyclerView;
    @BindView(R.id.empty_view) LinearLayout mEmptyView;
    @BindView(R.id.progress_bar) ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //This block of code ensures that onCreateOptionsMenu is called
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Firebase Realtime Database setup: Main Access point
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        //Initialize Firebase Authentication object
        mFirebaseAuth = FirebaseAuth.getInstance();

        //Get a reference to the section of the database where Transports are stored
        mTransportsDatabaseReference = mFirebaseDatabase.getReference().child("transports");
        mTransportsDatabaseReference.keepSynced(true);

        ButterKnife.bind(this);

        //RecyclerView setup
        initRecyclerView();

        //Before the Firebase Database is read by the app, the initial child count is set to -1
        mOnChildAddedCount = -1;

        //Check the connection to the network-backup method
        checkNetworkConnection();

        //Initialize FirebaseAuthStateListener
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {

            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.EmailBuilder().build(),
                    new AuthUI.IdpConfig.GoogleBuilder().build());

            //Called when it is added to our FirebaseAuth object in onResume. If you resume the code
            //and a user is logged in, a database listener will be attached
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //user is signed in
                    onSignedInInitialize(user.getDisplayName());

                    //Read the database

                } else {
                    onSignedOutCleanup();
                    //user is signed out: Launch sign in flow
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        mEmptyView.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    //Get result code from sign in so that we either close the app or go forward
    //onActivityResult is called before onResume
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, getString(R.string.toast_signed_in),
                        Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED)  {
                Toast.makeText(this, getString(R.string.toast_lost_connection),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Adapter Interface method override
    @Override
    public void onListItemClick(int clickedItemIndex) {
        mClickedItemIndex = clickedItemIndex;
        mOnChildAddedCount = -1;
        Intent openEditorActivity = new Intent(this, EditorActivity.class);
        openEditorActivity.putExtra("Transport", mTransports.get(clickedItemIndex));
        startActivity(openEditorActivity);
    }

    private void initRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setHasFixedSize(true);

        //Initialize the adapter and register the RecyclerView with the adapter
        mAdapter = new TransportAdapter(
                MainActivity.this, mTransports, MainActivity.this);

        mTransports = new ArrayList<Transport>();

        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        mTransports.clear();
        detachDatabaseReadListener();
    }

    @Override
    protected void onPause() {
        //Activity is no longer in the foreground
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mTransports.clear();
    }

    @Override
    protected void onResume() {
        //Activity is in the foreground
        super.onResume();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Create a new local array list for sorting while leaving the main ArrayList un-modified
        ArrayList<Transport> list = new ArrayList<>();

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            case R.id.sort_by_covered:
                list.addAll(mTransports);
                Log.d(TAG, list.get(0).getDateNeededBy());
                Toast.makeText(this, getString(R.string.action_show_covered), Toast.LENGTH_SHORT).show();
                for (Iterator<Transport> iterator = list.iterator(); iterator.hasNext();) {
                    Transport transport = iterator.next();
                    String status = transport.getStatus();
                    if (!status.equals(getString(R.string.covered))) {
                        iterator.remove();
                    }
                }
                mAdapter.setTransportData(list);
                return true;
            case R.id.sort_by_cancelled:
                list.addAll(mTransports);
                Log.d(TAG, list.get(0).getDateNeededBy());
                Toast.makeText(this, getString(R.string.action_show_cancelled), Toast.LENGTH_SHORT).show();
                for (Iterator<Transport> iterator = list.iterator(); iterator.hasNext();) {
                    Transport transport = iterator.next();
                    String status = transport.getStatus();
                    if (!status.equals(getString(R.string.cancelled))) {
                        iterator.remove();
                    }
                }
                mAdapter.setTransportData(list);
                return true;
            case R.id.sort_by_help_needed:
                list.addAll(mTransports);
                Toast.makeText(this, getString(R.string.action_help_needed), Toast.LENGTH_SHORT).show();
                for (Iterator<Transport> iterator = list.iterator(); iterator.hasNext();) {
                    Transport transport = iterator.next();
                    String status = transport.getStatus();
                    if (!status.equals(getString(R.string.help_needed))) {
                        iterator.remove();
                    }
                }
                mAdapter.setTransportData(list);
                return true;
            case R.id.show_all:
                Toast.makeText(this, getString(R.string.action_all), Toast.LENGTH_SHORT).show();
                mAdapter.setTransportData(mTransports);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //HELPER METHODS

    //Internet check-backup method if needed
    private void checkNetworkConnection() {
        //Create an instance of a ConnectivityManager
        ConnectivityManager cm = (ConnectivityManager)
                getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        //Get info on current network
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        //Set status of network connection
        mIsConnected = (activeNetwork != null && activeNetwork.isConnected());
    }

    //Attach the database read listener. This method called when onAuthStateChanged called
    private void onSignedInInitialize (String username) {
        //User name can be attached to the Transport object if necessary
        mUsername = username;
        attachDatabaseReadListener();
    }

    private void onSignedOutCleanup() {
        //unset the user name and detach the listener
        mUsername = ANONYMOUS;
        mTransports.clear();
        detachDatabaseReadListener();
    }

    private void attachDatabaseReadListener () {

        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {

                //Called whenever a new transport is inserted into the "transports" node
                //Also called for each child transport already in the "transports" list. So, a for loop is
                //not needed when the activity is created.
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    //Deserialize the values for each item in the dbase and place them in a Transport object
                    Transport transport = dataSnapshot.getValue(Transport.class);

                    //Add the transport object to the ArrayList of transports and attach the list to the adapter
                    mTransports.add(transport);
                    mOnChildAddedCount++;

                    if (mOnChildAddedCount >= 0) {
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                        mEmptyView.setVisibility(View.INVISIBLE);
                    }

                    mAdapter.setTransportData(mTransports);
                }

                //Called when the contents of an existing transport is changed
                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                    //Key of updated child in the Firebase
                    String key = dataSnapshot.getKey();
                    int index = 0;
                    Transport updatedTransport = null;

                    //Loop through the ArrayList of transports to find the transport that must be updated
                    for (Iterator<Transport> iterator = mTransports.iterator(); iterator.hasNext();) {
                        updatedTransport = iterator.next();
                        String id = updatedTransport.getCurrentFirebaseKey();
                        if (id.equals(key)) {
                            index = mTransports.indexOf(updatedTransport);
                        }
                    }

                    //Read the values in the Firebaes for the updated transport and then update the
                    //list of transports in the ArrayList
                    mTransports.set(index, dataSnapshot.getValue(Transport.class));

                    //Notify the adapter that the list of transports has been updated
                    mAdapter.setTransportData(mTransports);

                    //Notify the Widget so that Widget displays most recently updated transport
                    TransportRequestService.getLatestTransport(getApplicationContext());
                }

                /**Called when an existing transport is deleted.
                 * The DataSnapshot returned in this callback is the data for the transport that was removed
                 */
                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                    String key = dataSnapshot.getKey();

                    for (Iterator<Transport> iterator = mTransports.iterator(); iterator.hasNext();) {
                        Transport transport = iterator.next();
                        String id = transport.getCurrentFirebaseKey();
                        if (id.equals(key)) {
                            iterator.remove();
                        }
                    }

                    mAdapter.setTransportData(mTransports);

                    //Set empty view if this was the last transport in the list
                    if (mTransports.size() == 0) {
                        mRecyclerView.setVisibility(View.INVISIBLE);
                        mEmptyView.setVisibility(View.VISIBLE);
                    }
                }

                //Called if one of the transports changes position in the list
                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                //Called when an error occurs when user tries to make changes
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };

            //Attach the listener to the database reference we want to listen to
            mTransportsDatabaseReference.addChildEventListener(mChildEventListener);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private void detachDatabaseReadListener () {
        if (mChildEventListener != null) {
            mTransportsDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }
}
