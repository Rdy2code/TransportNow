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
import android.widget.Button;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class EditorActivity extends AppCompatActivity {

    private final static String TAG = EditorActivity.class.getSimpleName();
    private String mOriginCity;
    private String mDestinationCity;
    private String mDateNeededBy;
    private String mName;
    private String mGender;
    private String mWeight;
    private String mTransportId;
    private String mNote;
    private String mStatus;

    //Get references to the Text and Image views in the EditorActivity layout
    @BindView(R.id.textview_editor_status) TextView mStatusTextView;
    @BindView(R.id.textview_editor_date) TextView mDateTextView;
    @BindView(R.id.textview_editor_origin_destination) TextView mOriginDestinationTextView;
    @BindView(R.id.button) Button mImAvailableButton;
    @BindView(R.id.icon_map) ImageView mMapIcon;
    @BindView(R.id.animal_photo) ImageView mPhotoImageView;
    @BindView(R.id.textview_editor_transportId_field) TextView mTransportIdTextView;
    @BindView(R.id.textview_editor_name_field) TextView mNameTextView;
    @BindView(R.id.textview_editor_gender_field) TextView mGenderTextView;
    @BindView(R.id.textview_editor_weight_field) TextView mWeightTextView;
    @BindView(R.id.textview_editor_notes_field) TextView mNotesTextView;
    @BindView(R.id.textview_editor_copy_destination) TextView mCopyDestinationTextView;


    //URI for animal photo on user's device
    Uri mPhotoUri;

    //Transport Java Object
    Transport mTransport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        //Instantiate the views
        ButterKnife.bind(this);


        //FAB set up for sharing transport info by email
        mImAvailableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String subject = getString(R.string.email_subject,
                        mDateNeededBy,
                        mTransportId);

                String bodyText = getString(R.string.email_body_text_user_mode,
                        mName,
                        mOriginCity,
                        mDestinationCity,
                        mDateNeededBy);

                //Put transport coordinator address here
                String[] toAddress;
                toAddress = new String[]{"mailatcolorado@yahoo.com"};

                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("text/plain");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, toAddress);
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                emailIntent.putExtra(Intent.EXTRA_TEXT, bodyText);
                if (emailIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(emailIntent);
                }
            }
        });

        //Get the intent that was used from the CatalogActivity to start this activity
        Intent intentFromCatalogActivity = getIntent();
        if (intentFromCatalogActivity != null) {
            if (intentFromCatalogActivity.hasExtra("Transport")) {
                setTitle(getString(R.string.title_transport_details));

                mTransport = intentFromCatalogActivity.getParcelableExtra("Transport");

                //Get the field values from the Transport object sent with the intent
                mStatus = mTransport.getStatus();
                mOriginCity = mTransport.getOriginCity();
                mDestinationCity = mTransport.getDestinationCity();
                mDateNeededBy = mTransport.getDateNeededBy();
                mName = mTransport.getName();
                mGender = mTransport.getGender();
                mTransportId = mTransport.getTransportId();
                mNote = mTransport.getNote();
                mWeight = mTransport.getWeight();

                if (mTransport.getPhotoUrl() != null) {
                    mPhotoUri = Uri.parse(mTransport.getPhotoUrl());
                    //loadPhoto(mPhotoUri);
                    loadPhoto(mPhotoUri);
                }

                mStatusTextView.setText(mStatus);
                mOriginDestinationTextView.setText(getString(R.string.from_to_string, mOriginCity, mDestinationCity));
                mDateTextView.setText(mDateNeededBy);
                mNameTextView.setText(mName);
                mTransportIdTextView.setText(mTransportId);
                mNotesTextView.setText(mNote);
                mWeightTextView.setText(mWeight);
                mGenderTextView.setText(mGender);
            }
        }

        //Copy the Destination City input to the ClipTray so the user can quickly past it
        //into the Google map app when launched.
        mCopyDestinationTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboardManager;
                clipboardManager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData;

                String txtcopy = mDestinationCity;
                clipData = ClipData.newPlainText("text",txtcopy);
                clipboardManager.setPrimaryClip(clipData);
            }
        });

        //Build a map intent. Use Geocoder to convert City, State to Lat, Lng
        //https://stackoverflow.com/questions/3574644/how-can-i-find-the-latitude-and-longitude-from-address
        mMapIcon.setOnClickListener(v -> {

            final String BASE_URI = "geo:";

            Geocoder coder = new Geocoder(this);
            List<Address> addresses;
            LatLng p1 = null;

            try {
                // May throw an IOException
                addresses = coder.getFromLocationName(mOriginCity, 5);
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

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        return;
    }

    //Get the Uri of the photo on the user's device so that the photo can be loaded into the ImageView
    //Credit for code to: https://stackoverflow.com/questions/11144783/how-to-access-an-image-from-the-phones-photo-gallery
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            mPhotoUri = data.getData();
        }
        loadPhoto(mPhotoUri);
    }

    //After many hours, found that Picasso could not load a Uri from Firebase, but Glide can.
    private void loadPhoto (Uri photoUri) {
        Glide.with(this).load(photoUri).into(mPhotoImageView);
    }
}
