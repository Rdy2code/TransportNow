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
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.firebase.ui.auth.AuthUI;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static boolean mEditModeOn;
    private boolean mIsConnected;
    private int mClickedItemIndex;
    private int mOnChildAddedCount;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseUser mUser;
    private static final int RC_SIGN_IN = 1;    //Request code for FirebaseAuthStateListener
    private String mUsername;

    //SwipeRefreshLayout
    @BindView(R.id.swipeRefreshLayout) SwipeRefreshLayout mSwipeRefreshLayout;
    private int mSwipedPosition;
    private boolean mOnSwipe;

    //RecyclerView member variables
    private TransportAdapter mAdapter;
    private ArrayList<Transport> mTransports;
    @BindView(R.id.recyclerview_transports) RecyclerView mRecyclerView;
    @BindView(R.id.empty_view) LinearLayout mEmptyView;
    @BindView(R.id.progress_bar) ProgressBar mProgressBar;
    @BindView(R.id.network_error_tv) TextView mErrorMessageTextView;

    //FAB member variables
    @BindView(R.id.fab) FloatingActionButton mFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Firebase Realtime Database setup: Main Access point
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        //Initialize Firebase Authentication object
        mFirebaseAuth = FirebaseAuth.getInstance();

        //Get a reference to the section of the database where Transports are stored
        mTransportsDatabaseReference = mFirebaseDatabase.getReference().child("transports");

        mEditModeOn = false;

        isStoragePermissionGranted();

        ButterKnife.bind(this);

        //RecyclerView setup
        initRecyclerView();

        //FAB setup
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnChildAddedCount = -1;
                Intent openEditorActivityIntent = new Intent(MainActivity.this, EditorActivity.class);
                startActivity(openEditorActivityIntent);
            }
        });

        //Before the Firebase Database is read by the app, the initial child count is set to -1
        mOnChildAddedCount = -1;

        //Check the connection to the network
        checkNetworkConnection();

        if (savedInstanceState == null) {
            Log.d(TAG, "savedInstanceState null");
        }

        //SwipeRefresh Layout Listener callback when UI is pulled down
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }

            @Override
            public void onRefresh() {
                Log.d(TAG, "refreshing layout");
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

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
    }

    //Adapter Interface method override
    @Override
    public void onListItemClick(int clickedItemIndex) {
        mClickedItemIndex = clickedItemIndex;
        Log.d(TAG, "clicked index is " + mClickedItemIndex);
        Log.d(TAG, mTransports.get(mClickedItemIndex).getOriginCity());
        mOnChildAddedCount = -1;
        Intent openEditorActivity = new Intent(this, EditorActivity.class);
        openEditorActivity.putExtra("Transport", mTransports.get(clickedItemIndex));
        startActivity(openEditorActivity);
    }

    private void initRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        mTransports = new ArrayList<Transport>();

        //Initialize the adapter and register the RecyclerView with the adapter
        mAdapter = new TransportAdapter(
                MainActivity.this, mTransports, MainActivity.this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);

        setItemTouchHelper();
    }

    //Swipe-to-delete action with red background animation
    //Credit to: https://github.com/nemanja-kovacevic/recycler-view-swipe-to-delete/blob/master/
    // app/src/main/java/net/nemanjakovacevic/recyclerviewswipetodelete/MainActivity.java
    private void setItemTouchHelper() {
        ItemTouchHelper.SimpleCallback simpleTouchCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            Drawable background;
            Drawable xMark;
            int xMarkMargin;
            boolean initiated;

            private void init() {
                background = new ColorDrawable(Color.RED);
                xMark = ContextCompat.getDrawable(MainActivity.this, R.drawable.swipe_color);
                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                xMarkMargin = (int) MainActivity.this.getResources().getDimension(R.dimen.standard_padding_margin);
                initiated = true;
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                //Get the position of the item being swiped
                mSwipedPosition = viewHolder.getAdapterPosition();

                //Get a reference to the deleted Transport object from the ArrayList of transports
                Transport transportToDelete = mTransports.get(mSwipedPosition);

                Log.d(TAG, "onSwipe activated" + transportToDelete.getTransportId());

                //Delete the transport from the Firebase
                String path = transportToDelete.getTransportId();
                Log.d(TAG, "transport ID is " + path);
                mTransportsDatabaseReference.child(path).removeValue();

                mOnSwipe = true;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX,
                                    float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;

                if (viewHolder.getAdapterPosition() == -1) {
                    // not interested in those
                    return;
                }

                if (!initiated) {
                    init();
                }

                //Draw red background
                background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                background.draw(c);

                // draw x mark
                int itemHeight = itemView.getBottom() - itemView.getTop();
                int intrinsicWidth = xMark.getIntrinsicWidth();
                int intrinsicHeight = xMark.getIntrinsicWidth();

                int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
                int xMarkRight = itemView.getRight() - xMarkMargin;
                int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight)/2;
                int xMarkBottom = xMarkTop + intrinsicHeight;
                xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);

                xMark.draw(c);

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(simpleTouchCallback);
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    public static void setEditModeOn (boolean editMode) {
        mEditModeOn = editMode;
    }

    //isStoragePermissionGranted and onRequestPermissionResult code derived from stackoverflow
    //after researching the problem of how to obtain permission at runtime
    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 0:
                boolean isPerpermissionForAllGranted = false;
                if (grantResults.length > 0 && permissions.length==grantResults.length) {
                    for (int i = 0; i < permissions.length; i++){
                        isPerpermissionForAllGranted= grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    }

                    Log.e("value", "Permission Granted");
                } else {
                    isPerpermissionForAllGranted=true;
                    Log.e("value", "Permission Denied");
                }
                if(isPerpermissionForAllGranted){
                    // do your stuff here
                }
                break;
        }
    }

    @Override
    protected void onPause() {
        //Activity is no longer in the foreground
        Log.d(TAG, "onPause called");
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        mTransports.clear();
        detachDatabaseReadListener();
    }

    @Override
    protected void onResume() {
        //Activity is in the foreground
        Log.d(TAG, "onResume called");
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    //HELPER METHODS
    private void checkNetworkConnection() {
        //Create an instance of a ConnectivityManager
        ConnectivityManager cm = (ConnectivityManager)
                getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        //Get info on current network
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        //Set status of network connection
        mIsConnected = (activeNetwork != null && activeNetwork.isConnected());
        Log.d(TAG, "mIsConnected is " + mIsConnected);
    }

    //Attach the database read listener. This method called when onAuthStateChanged called
    private void onSignedInInitialize (String username) {
        //User name can be attached to the Transport object if necessary
        mUsername = username;
        Log.d(TAG, "user name is " + mUsername);
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
            Log.d(TAG, "mChildEventListener is null");
            mChildEventListener = new ChildEventListener() {

                //Called whenever a new transport is inserted into the "transports" node
                //Also called for each child transport already in the "transports" list. So, a for loop is
                //not needed when the activity is created.
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    //Add the Uid of each node in the dbase to the transportId child of the node
                    //Below this ID is added to the transportID field of the Transport object
                    //This is called only for newly added Transports, no 'for' loops are necessary either
                    //The Firebase automatically loops through the list
                    mTransportsDatabaseReference
                            .child(dataSnapshot.getKey())
                            .child("transportId")
                            .setValue(dataSnapshot.getKey());       //Triggers a call to onChildChanged


                    //Deserialize the values for each item in the dbase and place them in a Transport object
                    Transport transport = dataSnapshot.getValue(Transport.class);

                    //Not sure why, but found this block was necessary to prevent a null pointer exception
                    if (transport.getTransportId() == null) {
                        transport.setTransportId(dataSnapshot.getKey());
                    }

                    //Add the transport object to the ArrayList of transports and attach the list to the adapter
                    mTransports.add(transport);

                    mAdapter.setTransportData(mTransports);
                }

                //Called when the contents of an existing transport is changed
                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                    Log.d(TAG, "onChildChanged called");

                    if (mEditModeOn) {
                        Log.d(TAG, "conditional in onChildChanged called");
                        //Create a new Transport object with the updated information
                        Transport transport = dataSnapshot.getValue(Transport.class);
                        //Replace the old transport object with the new one
                        mTransports.set(mClickedItemIndex, transport);
                        Log.d(TAG, "clickedItem is " + mClickedItemIndex);
                        //Notify the adapter that the list of transports has been updated
                        mAdapter.setTransportData(mTransports);

                        Toast.makeText(
                                MainActivity.this,
                                getString(R.string.transport_updated_message),
                                Toast.LENGTH_SHORT).show();
                        mEditModeOn = false;
                    }
                }

                //Called when an existing transport is deleted
                //The DataSnapshot returned in this callback is the data for the transport that was removed
                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                    Toast.makeText(
                            MainActivity.this,
                            getString(R.string.transport_deleted_message),
                            Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "onChildRemoved called");
                    Log.d(TAG, "swiped position is " + mSwipedPosition);

                    //User has removed a transport from the EditorActivity screen, so update the list
                    //of transports and notify the adapter.

                    //Deleting from the MainActivity
                    if (mOnSwipe) {
                        Log.d(TAG, "if statement entered");
                        mTransports.remove(mTransports.get(mSwipedPosition));
                        mAdapter.setTransportData(mTransports);
                        //Reset the boolean so this control flow is closed after each swipe
                        if (mTransports.size() == 0) {
                            Log.d(TAG, "mTransports = 0");
                            mRecyclerView.setVisibility(View.INVISIBLE);
                            mEmptyView.setVisibility(View.VISIBLE);
                        }
                        mOnSwipe = false;
                    }

                    //Deleting from the EditorActivity
                    if (mEditModeOn) {
                        Log.d(TAG, "mEditModeOn is " + mEditModeOn);
                        mTransports.remove(mTransports.get(mClickedItemIndex));
                        mAdapter.setTransportData(mTransports);
                        mEditModeOn = false;
                        if (mTransports.size() == 0) {
                            mRecyclerView.setVisibility(View.INVISIBLE);
                            mEmptyView.setVisibility(View.VISIBLE);
                        }
                    }
                }

                //Called if one of the transports changes position in the list
                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Log.d(TAG, "onChildMoved called");
                }

                //Called when an error occurs when user tries to make changes
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    if (mUser != null) {
                        Toast.makeText(
                                MainActivity.this,
                                getString(R.string.error_saving_transport_message),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            };

            //Attach the listener to the database reference we want to listen to
            mTransportsDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener () {
        if (mChildEventListener != null) {
            mTransportsDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }
}
