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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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

import com.example.android.transportapp.utils.NotificationUtils;
import com.example.android.transportapp.utils.TransportRequestService;
import com.firebase.ui.auth.AuthUI;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
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
    private static boolean mEditModeOn;
    private boolean mIsConnected;
    private int mClickedItemIndex;
    private int mOnChildAddedCount;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseUser mUser;
    public static final int RC_SIGN_IN = 11;    //Request code for FirebaseAuthStateListener
    private String mUsername;

    //SwipeRefreshLayout
    private int mSwipedPosition;
    private Transport mRecentlyDeletedItem;

    //RecyclerView member variables
    private TransportAdapter mAdapter;
    private ArrayList<Transport> mTransports;
    @BindView(R.id.recyclerview_transports) RecyclerView mRecyclerView;
    @BindView(R.id.empty_view) LinearLayout mEmptyView;
    @BindView(R.id.progress_bar) ProgressBar mProgressBar;

    //FAB member variables
    @BindView(R.id.fab) FloatingActionButton mFab;

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

        mEditModeOn = false;

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
                    isStoragePermissionGranted();
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
                Toast.makeText(this, getString(R.string.toast_signed_in), Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED)  {
                Toast.makeText(this, getString(R.string.toast_sign_in_cancel), Toast.LENGTH_SHORT).show();
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
                mRecentlyDeletedItem = mTransports.get(mSwipedPosition);

                //Delete the transport from the Firebase
                String path = mRecentlyDeletedItem.getCurrentFirebaseKey();
                mTransportsDatabaseReference.child(path).removeValue(); //Triggers call to onChildRemoved

                //Show Snackbar asking if user would like to undo the delete
                showUndoSnackbar();
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

    //Display the Snackbar
    private void showUndoSnackbar() {
        View view = this.findViewById(R.id.coordinator_layout);
        Snackbar snackbar = Snackbar.make(view, "Transport Deleted", Snackbar.LENGTH_INDEFINITE);
        View snackbarView = snackbar.getView();
        TextView tv = (TextView) snackbarView.findViewById(R.id.snackbar_text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color));
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_trash_can, 0,0 , 0);
        snackbarView.setBackgroundColor(ContextCompat.getColor(this, R.color.snackbar_background));
        snackbar.setAction("Undo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoDelete();
            }
        });
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color));
        snackbar.show();
    }

    private void undoDelete() {
        //TODO: Add the recently deleted item back to Firebase
        mTransportsDatabaseReference.push().setValue(mRecentlyDeletedItem);     //Triggers onChildAdded()
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
                return true;
            } else {

                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
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
                } else {
                    isPerpermissionForAllGranted=true;
                }
                if(isPerpermissionForAllGranted){
                    // do your stuff here
                }
                break;
        }
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

                    //Assign the ID to the Transport Object field if it does not have one
                    if (transport.getTransportId() == null) {
                        transport.setTransportId(dataSnapshot.getKey());
                    }

                    //Assign the Firebase Key as a value to matching field in object
                    if (transport.getCurrentFirebaseKey() == null) {
                        transport.setCurrentFirebaseKey(dataSnapshot.getKey());
                    }

                    //Add the transport object to the ArrayList of transports and attach the list to the adapter
                    mTransports.add(transport);
                    mOnChildAddedCount++;

                    if (mOnChildAddedCount >= 0) {
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                        mEmptyView.setVisibility(View.INVISIBLE);
                    }

                    mAdapter.setTransportData(mTransports);

                    //For new transports, add the TransportID to the Firebase
                    //New transports don't yet have a TransportID
                    if ((dataSnapshot.getValue(Transport.class).getTransportId() == null)) {

                        mTransportsDatabaseReference.child(transport.getCurrentFirebaseKey())
                                .setValue(transport);   //Triggers onChildChanged()

                        //Because this is new request, send a notification
                        NotificationUtils.notifyUserOfUpdate(MainActivity.this,
                                dataSnapshot.getValue(Transport.class) );
                    }


                    //If currentFirebaseKey (CFK) is out of sync with the Firebase node,
                    //this is a Transport that is getting re-added from "undo" swipe, so we need
                    //to reset the CFK value
                    if ((!dataSnapshot.getKey().equals(dataSnapshot.getValue(
                            Transport.class).getCurrentFirebaseKey()))) {

                        transport.setCurrentFirebaseKey(dataSnapshot.getKey());

                        mTransportsDatabaseReference.child(transport.getCurrentFirebaseKey())
                                .setValue(transport);  //Triggers onChildChanged
                    }
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

                    //Update the list of transports in the local storage
                    mTransports.set(index, dataSnapshot.getValue(Transport.class));

                    //Notify the adapter that the list of transports has been updated
                    mAdapter.setTransportData(mTransports);

                    Toast.makeText(
                            MainActivity.this,
                            getString(R.string.transport_updated_message),
                            Toast.LENGTH_SHORT).show();

                    //Notify the Widget so that Widget displays most recently updated transport
                    TransportRequestService.getLatestTransport(getApplicationContext());
                }

                /**Called when an existing transport is deleted.
                 * The DataSnapshot returned in this callback is the data for the transport that was removed
                 */
                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                    String key = dataSnapshot.getKey();

                    //Find the transport that was removed in the ArrayList and then remove it
                    for (Iterator<Transport> iterator = mTransports.iterator(); iterator.hasNext();) {
                        Transport transport = iterator.next();
                        String id = transport.getCurrentFirebaseKey();
                        if (id.equals(key)) {
                            iterator.remove();
                        }
                    }

                    mAdapter.setTransportData(mTransports);

                    Toast.makeText(
                            MainActivity.this,
                            getString(R.string.transport_deleted_message),
                            Toast.LENGTH_SHORT).show();

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
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private void detachDatabaseReadListener () {
        if (mChildEventListener != null) {
            mTransportsDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }
}
