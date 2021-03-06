package com.example.android.transportapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.model.LatLng;

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

    private Toast mToast;

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
    //Uri for photo in Firebase Storage
    Uri mDownloadPhotoUri;

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
                toAddress = new String[]{""};       //TODO: Insert transport coordinator address here

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
                }

                if (mTransport.getDownloadPhotoUrl() != null &&
                        !mTransport.getDownloadPhotoUrl().isEmpty()) {
                    mDownloadPhotoUri = Uri.parse(mTransport.getDownloadPhotoUrl());
                    loadPhoto(mDownloadPhotoUri);
                } else {
                    mPhotoImageView.setImageResource(R.drawable.icon_camera);
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
                ClipboardManager clipboardManager =
                        (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("text", mDestinationCity);
                clipboardManager.setPrimaryClip(clipData);
                Toast.makeText(EditorActivity.this,
                        "Destination city copied to clip board", Toast.LENGTH_SHORT).show();
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

    //After many hours, found that Picasso could not load a Uri from Firebase, but Glide can.
    private void loadPhoto (Uri photoUri) {
        CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(this);
        circularProgressDrawable.setStrokeWidth(5f);
        circularProgressDrawable.setCenterRadius(30f);
        circularProgressDrawable.start();

        Glide.with(this)
                .load(photoUri)
                .placeholder(circularProgressDrawable)
                .error(R.drawable.icon_camera)
                .into(mPhotoImageView);
    }
}
