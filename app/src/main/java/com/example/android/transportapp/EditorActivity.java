package com.example.android.transportapp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.android.transportapp.utils.DatePickerFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class EditorActivity extends AppCompatActivity implements DatePickerDialog.OnDateSetListener,
        AdapterView.OnItemSelectedListener {

    private final static String TAG = EditorActivity.class.getSimpleName();

    //Get references to views and variable for picking and storing animal's gender
    @BindView(R.id.spinner_gender) Spinner mGenderSpinner;
    private String mGender;
    ArrayAdapter<CharSequence> statusSpinnerAdapter;

    //Get references to views and variable for picking and storing transport status
    @BindView(R.id.spinner_status) Spinner mStatusSpinner;
    private String mStatus;
    ArrayAdapter<CharSequence> genderSpinnerAdapter;

    //Get references to the Text and Image views in the EditorActivity layout
    @BindView(R.id.editTextDate) EditText mEditTextDate;
    @BindView (R.id.map_link) TextView mMapLinkTextView;
    @BindView(R.id.edit_origin_city_field) EditText mOriginCityTextView;
    @BindView(R.id.edit_destination_city_field) EditText mDestinationCityTextView;
    @BindView(R.id.edit_notes) EditText mEditTextNotes;
    @BindView(R.id.to_textview) TextView mToTextview;
    @BindView(R.id.editText_name) EditText mEditTextName;
    @BindView(R.id.animal_photo) ImageView mPhotoImageView;
    @BindView(R.id.edit_pet_weight) EditText mEditTextWeight;
    @BindView(R.id.transport_id_textview) TextView mTransportIdTextView;
    @BindView(R.id.details_textview) TextView mLoadPhotoTextView;
    @BindView(R.id.share_fab) FloatingActionButton mShareFab;
    @BindView(R.id.switch_urgency) Switch mSwitchUrgency;

    //Firebase Realtime Database object reference entry points
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mTransportsDatabaseReference;

    //URI for storing and retrieving photos of animals from user's device
    Uri mPhotoUri;

    //Boolean variable used to indicate whether user has updated any parts of a transport
    private boolean mTransportChanged = false;

    //Variable to indicate whether user is in Edit or Add new transport mode
    public boolean mModeEdit; //False = Add a new transport mode

    //Variable to indicate whether the transport is urgent or not
    private boolean mIsTransportUrgent;
    CompoundButton.OnCheckedChangeListener mSwitchListener;

    //Transport Java Object for reading from and writing to Firebase
    Transport mTransport;

    //Listen for Touch events in views, to prevent info loss before navigating back
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mTransportChanged = true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        //Instantiate the views
        ButterKnife.bind(this);

        //This keeps the keyboard out of the way when the EditorActivity is first launched
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        //Allow the user to close the keyboard when done typing note
        mEditTextNotes.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mEditTextNotes.setRawInputType(InputType.TYPE_CLASS_TEXT);

        //Register onTouchListeners with specific views
        mEditTextDate.setOnTouchListener(mTouchListener);
        mOriginCityTextView.setOnTouchListener(mTouchListener);
        mDestinationCityTextView.setOnTouchListener(mTouchListener);
        mEditTextNotes.setOnTouchListener(mTouchListener);
        mEditTextName.setOnTouchListener(mTouchListener);
        mEditTextWeight.setOnTouchListener(mTouchListener);

        //FAB set up for sharing transport info by email
        mShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Get information from relevant TextViews for incorporation into email
                String originCity = mOriginCityTextView.getText().toString().trim();
                String destinationCity = mDestinationCityTextView.getText().toString().trim();
                String dateNeededBy = mEditTextDate.getText().toString().trim();
                String name = mEditTextName.getText().toString().trim();
                String transportId = mTransportIdTextView.getText().toString();

                String subject = getString(R.string.email_subject, dateNeededBy, transportId);

                String bodyText = getString(R.string.email_body_text,
                        name,
                        originCity,
                        destinationCity,
                        dateNeededBy);

                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("*/*");
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                emailIntent.putExtra(Intent.EXTRA_TEXT, bodyText);
                emailIntent.putExtra(Intent.EXTRA_STREAM, mPhotoUri);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (emailIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(emailIntent);
                }
            }
        });

        //Copy the Destination City input to the ClipTray so the user can quickly past it
        //into the Google map app when launched.
        mToTextview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboardManager;
                clipboardManager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData;

                String txtcopy = mDestinationCityTextView.getText().toString();
                clipData = ClipData.newPlainText("text",txtcopy);
                clipboardManager.setPrimaryClip(clipData);
            }
        });

        //Listener on "Details" text view in order to Launch Gallery app
        //See onActivityResult() below
        mLoadPhotoTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent loadPhotoIntent = new Intent (Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(loadPhotoIntent, 0);
            }
        });

        //Listener for Urgency switch
        mSwitchListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d(TAG, "switched on");
                    mSwitchUrgency.setTextColor(getColor(R.color.urgent));
                    mIsTransportUrgent = true;

                } else {
                    mSwitchUrgency.setTextColor(getColor(R.color.textColorPrimary));
                    mIsTransportUrgent = false;
                }
            }
        };
        mSwitchUrgency.setOnCheckedChangeListener(mSwitchListener);

        //Set up Date Picker
        mEditTextDate.setInputType(InputType.TYPE_NULL);
        mEditTextDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment dateFragment = new DatePickerFragment();
                dateFragment.show(getSupportFragmentManager(), "datePicker");
            }
        });

        //Build a map intent. Use Geocoder to convert City, State to Lat, Lng
        //https://stackoverflow.com/questions/3574644/how-can-i-find-the-latitude-and-longitude-from-address
        mMapLinkTextView.setOnClickListener(v -> {
            String originCity = mOriginCityTextView.getText().toString();
            String destinationCity = mDestinationCityTextView.getText().toString();
            Log.d(TAG, "map link called");

            final String BASE_URI = "geo:";

            Geocoder coder = new Geocoder(this);
            List<Address> addresses;
            LatLng p1 = null;

            try {
                // May throw an IOException
                addresses = coder.getFromLocationName(originCity, 5);
                if (addresses == null) {
                    return;
                }

                Address location = addresses.get(0);
                p1 = new LatLng(location.getLatitude(), location.getLongitude() );

            } catch (IOException ex) {
                ex.printStackTrace();
            }

            double lat = p1.latitude;
            double lng = p1.longitude;

            Uri.Builder builder = Uri.parse(BASE_URI + lat + "," + lng).buildUpon();
            Uri uri = builder.build();

            Log.d(TAG, lat + "");
            Log.d(TAG, Double.toString(lat));
            Log.d(TAG, "my new uri is: " + uri.toString());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });

        //Attach the Listener to the spinners to trigger the callback
        mGenderSpinner.setOnItemSelectedListener(this);
        mStatusSpinner.setOnItemSelectedListener(this);

        setUpSpinner();

        if (savedInstanceState != null) {

            if (savedInstanceState.containsKey("genderSelected")) {
                mGenderSpinner.setSelection(savedInstanceState.getInt("genderSelected"));
                Log.d(TAG, savedInstanceState.getInt("genderSelected") + "" );
            }

            if (savedInstanceState.containsKey("statusSelected")) {
                mStatusSpinner.setSelection(savedInstanceState.getInt("statusSelected"));
            }
        }

        //Get the intent that was used from the CatalogActivity to start this activity
        Intent intentFromCatalogActivity = getIntent();
        if (intentFromCatalogActivity != null) {
            if (intentFromCatalogActivity.hasExtra("Transport")) {

                //We are in Edit mode, set the title and get the values from the Transport object
                //and set the title to reflect the mode
                mModeEdit = true; //True means Edit
                setTitle(getString(R.string.edit_transport));

                mTransport = intentFromCatalogActivity.getParcelableExtra("Transport");

                //Get the field values from the Transport object sent with the intent
                String status = mTransport.getStatus();
                boolean urgency = mTransport.getUrgency();
                Log.d(TAG, "ugency is " + urgency);
                String originCity = mTransport.getOriginCity();
                String destinationCity = mTransport.getDestinationCity();
                String dateNeededBy = mTransport.getDateNeededBy();
                String name = mTransport.getName();
                String gender = mTransport.getGender();
                String transportId = mTransport.getTransportId();
                String note = mTransport.getNote();
                String weight = mTransport.getWeight();

                if (mTransport.getPhotoUrl() != null) {
                    Log.d(TAG, "Url is " + mTransport.getPhotoUrl());
                    mPhotoUri = Uri.parse(mTransport.getPhotoUrl());
                    Log.d(TAG, "the retrieved mPhotoUri is " + mPhotoUri);
                    //loadPhoto(mPhotoUri);
                    loadPhoto(mPhotoUri);
                }

                //Set the text into the appropriate fields of the EditorActivity UI
                int statusSpinnerPosition = statusSpinnerAdapter.getPosition(status);
                mStatusSpinner.setSelection(statusSpinnerPosition);

                mOriginCityTextView.setText(originCity);
                mDestinationCityTextView.setText(destinationCity);
                mEditTextDate.setText(dateNeededBy);
                mEditTextName.setText(name);
                mTransportIdTextView.setText(getString(R.string.transport_id_label, transportId));

                int genderSpinnerPosition = genderSpinnerAdapter.getPosition(gender);
                mGenderSpinner.setSelection(genderSpinnerPosition);

                mEditTextNotes.setText(note);
                mEditTextWeight.setText(weight);

                //Set the urgency switch
                mSwitchUrgency.setOnCheckedChangeListener(null);
                mSwitchUrgency.setChecked(urgency);
                isChecked(urgency);
                mSwitchUrgency.setOnCheckedChangeListener(mSwitchListener);

            } else {
                //We are in save a new transport mode
                mModeEdit = false;
                setTitle(getString(R.string.add_transport));
            }
        }

        //Firebase Realtime Database setup
        mFirebaseDatabase = FirebaseDatabase.getInstance();         //Main Access point
        mTransportsDatabaseReference = mFirebaseDatabase.getReference().child("transports");
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (!mModeEdit) {
            MenuItem deleteItem = menu.findItem(R.id.action_delete);
            deleteItem.setVisible(false);
        }
        return true;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {

        String abbrevMonth = "";

        switch (month) {
            case 0:
                abbrevMonth = "Jan";
                break;
            case 1:
                abbrevMonth = "Feb";
                break;
            case 2:
                abbrevMonth = "Mar";
                break;
            case 3:
                abbrevMonth = "Apr";
                break;
            case 4:
                abbrevMonth = "May";
                break;
            case 5:
                abbrevMonth = "Jun";
                break;
            case 6:
                abbrevMonth = "Jul";
                break;
            case 7:
                abbrevMonth = "Aug";
                break;
            case 8:
                abbrevMonth = "Sep";
                break;
            case 9:
                abbrevMonth = "Oct";
                break;
            case 10:
                abbrevMonth = "Nov";
                break;
            case 11:
                abbrevMonth = "Dec";
                break;
            default:
                abbrevMonth = "Unknown";
                break;
        }

        mEditTextDate.setText(abbrevMonth + " " + dayOfMonth + "," + " " + year);
    }

    private void setUpSpinner() {

        //Attach the string values in the arrays file to a spinner layout using an ArrayAdapter
        genderSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.array_gender_options, android.R.layout.simple_spinner_item);
        statusSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.array_status_options, android.R.layout.simple_spinner_item);

        genderSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        statusSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

        //Register the spinners with the adapter
        mGenderSpinner.setAdapter(genderSpinnerAdapter);
        mStatusSpinner.setAdapter(statusSpinnerAdapter);
    }

    //Callback event occurs when a user selects an item in the spinner
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (view == null) {
            LayoutInflater mInflater = (LayoutInflater) this.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            view = mInflater.inflate(R.layout.support_simple_spinner_dropdown_item, null);
        }
        view.setPadding(10, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
        String selection = (String) parent.getItemAtPosition(position);
        if (!TextUtils.isEmpty(selection)) {
            if (selection.equals("Male")) {
                mGender = "Male";
                Log.d(TAG, "The gender is: " + mGender);
            } else if (selection.equals("Female")) {
                mGender = "Female";
            } else if (selection.equals("Unknown")) {
                mGender = "Unknown";
            }

            if (selection.equals("Help Needed")) {
                mStatus = "Help Needed";
            } else if (selection.equals("Covered")) {
                mStatus = "Covered";
            } else if (selection.equals("Cancelled")) {
                mStatus = "Cancelled";
            }
        }
    }

    private void saveTransport() {

        String originCity = mOriginCityTextView.getText().toString().trim();
        String destinationCity = mDestinationCityTextView.getText().toString().trim();
        String dateNeededBy = mEditTextDate.getText().toString().trim();
        String name = mEditTextName.getText().toString().trim();
        String weight = mEditTextWeight.getText().toString().trim();
        String note = mEditTextNotes.getText().toString().trim();

        //Use fieldChecker() helper method to check if the field is empty
        if (fieldChecker(originCity)) {
            Toast userPrompt =
                    Toast.makeText(this, getString(R.string.toast_message_origin_city),
                            Toast.LENGTH_LONG);
            userPrompt.show();
            return;
        }

        if (fieldChecker(destinationCity)) {
            Toast userPrompt =
                    Toast.makeText(this, getString(R.string.toast_message_destination_city),
                            Toast.LENGTH_LONG);
            userPrompt.show();
            return;
        }

        if (fieldChecker(dateNeededBy)) {
            Toast userPrompt =
                    Toast.makeText(this, getString(R.string.toast_message_date_needed_by),
                            Toast.LENGTH_LONG);
            userPrompt.show();
            return;
        }

        if (fieldChecker(name)) {
            name = "unknown";
        }

        if (fieldChecker(weight)) {
            weight = "unknown";
        }

        if (fieldChecker(note)) {
            note = "";
        }

        if ((fieldChecker(originCity)) && (fieldChecker(destinationCity))) {
            return;
        }

        if (mModeEdit) {
            Log.d(TAG, "we are in edit mode");
            //We are in edit mode
            Transport transportObjectEditMode = new Transport(
                    mStatus,
                    mIsTransportUrgent,
                    originCity,
                    destinationCity,
                    dateNeededBy,
                    name,
                    mGender,
                    mTransport.getTransportId(),
                    mPhotoUri.toString(),
                    note,
                    weight
            );

            //Set the mEditModeOn boolean to true in the MainActivity
            MainActivity.setEditModeOn(true);
            mTransportsDatabaseReference.child(mTransport.getTransportId()).setValue(transportObjectEditMode);
            Log.d(TAG, "setValue called");

        } else {
            //We are in save a new transport mode
            //Create and initialize a Transport object for updating the database
            Transport transportObjectSaveMode = new Transport(
                    mStatus,
                    mIsTransportUrgent,
                    originCity,
                    destinationCity,
                    dateNeededBy,
                    name,
                    mGender,
                    null,       //Leave as null here;ID is assigned in onChildAdded() in MainActivity
                    mPhotoUri.toString(),
                    note,
                    weight);
            //Since onChildChanged gets called when onChildAdded calls setValue() to update the
            //transportId in the Firebase for this new transport, we need to toggle the boolean
            //to control the flow of logic in the MainActivity callbacks
            MainActivity.setEditModeOn(false);

            //push().setValue adds a new node with given values to the Firebase
            //This call triggers the onChildAdded() callback, where we can retrieve the Uid for this node
            //In onChildAdded(), the transportId field is then set to the Uid, so that we can later
            //use this Id to delete the node from the Firebase in the onSwiped() method.
            mTransportsDatabaseReference.push().setValue(transportObjectSaveMode);
            Toast.makeText(
                    EditorActivity.this,
                    getString(R.string.transport_saved_message),
                    Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void deleteTransport() {
        if (mModeEdit) {
            //This code removes the node for this transport from the Firebase and triggers the
            //onChildRemoved callback in the Main Activity, where the list of transports is
            //updated
            MainActivity.setEditModeOn(true);
            String pathToRemove = mTransport.getTransportId();
            mTransportsDatabaseReference.child(pathToRemove).removeValue();
        }
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptions called");
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                saveTransport();
                return true;
            case R.id.action_delete:
                showDeleteConfirmationDialog();
                return true;
            case android.R.id.home:
                if (!mTransportChanged) {
                    finish();
                    return true;
                }

                //Otherwise, warn the user they have unsaved changes
                DialogInterface.OnClickListener discardClickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                NavUtils.navigateUpFromSameTask(EditorActivity.this);
                            }
                        };
                warnUnsavedChangesDialog(discardClickListener);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mGender = "Unknown";
        mStatus = "Help Needed";
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("genderSelected", mGenderSpinner.getSelectedItemPosition());
        Log.d(TAG, "spinner position is: " + mGenderSpinner.getSelectedItemPosition());
        outState.putInt("statusSelected", mStatusSpinner.getSelectedItemPosition());
    }

    private boolean fieldChecker (String value) {
        return TextUtils.isEmpty(value);
    }

    //DIALOG BOXES
    private void showDeleteConfirmationDialog() {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the positive and negative buttons on the dialog.

        //Use the Builder class for convenient dialog construction. Allows chaining of calls
        //to set methods and messages in the dialog box and functions with the AlertDialog class
        //in which it is nested.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //Message to user displayed in the dialog box: 'Delete this transport?'
        builder.setMessage(R.string.delete_dialog_message);

        //Set a listener to be invoked when the positive button of the dialog is pressed
        //This button continues the action, e.g., 'yes, delete'
        builder.setPositiveButton(R.string.confirm_delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Delete" button, so delete the transport.
                deleteTransport();
            }
        });

        //Set a listener to be invoked when the negative button of the dialog is pressed.
        //This button cancels the action, e.g., 'no, cancel delete'
        builder.setNegativeButton(R.string.cancel_delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                // and continue editing the transport.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void warnUnsavedChangesDialog (DialogInterface.OnClickListener discardListenerDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_message);
        builder.setPositiveButton(R.string.discard, discardListenerDialog);
        builder.setNegativeButton(R.string.keep_editing, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    @Override
    public void onBackPressed() {

        if (!mTransportChanged) {
            super.onBackPressed();
            return;
        }

        //Otherwise, set up a dialog box to warn the user they have unsaved changes
        DialogInterface.OnClickListener discardButtonClickListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                };

        //Pass the DialogInterface listener into the dialog method to warn user there are
        //unsaved changes
        warnUnsavedChangesDialog(discardButtonClickListener);
    }

    //Get the Uri of the photo on the user's device so that the photo can be loaded into the ImageView
    //Credit for code to: https://stackoverflow.com/questions/11144783/how-to-access-an-image-from-the-phones-photo-gallery
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called");

        if (resultCode == RESULT_OK) {
            mPhotoUri = data.getData();
            Log.d(TAG, "result code okay");
            Log.d(TAG, "the photo uri from the transport object is " + mPhotoUri.toString());
        }
        loadPhoto(mPhotoUri);
    }

    //After many hours, found that Picasso could not load a Uri from Firebase, but Glide can.
    private void loadPhoto (Uri photoUri) {
        Log.d(TAG, "photoUri is " + photoUri);
        Glide.with(this).load(photoUri).into(mPhotoImageView);
    }

    private void isChecked (boolean on) {
        if (on) {
            Log.d(TAG, "switched on");
            mSwitchUrgency.setTextColor(getColor(R.color.urgent));
            mIsTransportUrgent = true;

        } else {
            mSwitchUrgency.setTextColor(getColor(R.color.textColorPrimary));
            mIsTransportUrgent = false;
        }
    }
}
