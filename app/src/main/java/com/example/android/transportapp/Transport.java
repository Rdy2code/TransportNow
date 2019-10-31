package com.example.android.transportapp;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;

public class Transport implements Parcelable {

    //FIELDS
    private String status;
    private boolean urgency;
    private String originCity;
    private String destinationCity;
    private String dateNeededBy;
    private String name;
    private String gender;
    private String transportId;
    private String photoUrl;
    private String note;
    private String weight;
    private String downloadPhotoUrl;

    //HashMap for Timestamp
    HashMap<String, Object> timestamp;

    //Constructor
    public Transport () {

    }

    //Constructor
    public Transport (String status,
                      boolean urgency,
                      String originCity,
                      String destinationCity,
                      String dateNeededBy,
                      String name,
                      String gender,
                      String transportId,
                      String photoUrl,
                      String note,
                      String weight,
                      String downloadPhotoUrl) {

        //Initialize member variables to the values passed into the constructor
        this.status = status;
        this.urgency = urgency;
        this.originCity = originCity;
        this.destinationCity = destinationCity;
        this.dateNeededBy = dateNeededBy;
        this.name = name;
        this.gender = gender;
        this.transportId = transportId;
        this.photoUrl = photoUrl;
        this.note = note;
        this.weight = weight;
        this.downloadPhotoUrl = downloadPhotoUrl;

        HashMap<String, Object> timeStampCurrent = new HashMap<>();
        timeStampCurrent.put("timestamp", ServerValue.TIMESTAMP);
        this.timestamp = timeStampCurrent;
    }

    //GETTERS
    public String getStatus() {
        return status;
    }

    public boolean getUrgency() {
        return urgency;
    }

    public String getOriginCity() {
        return originCity;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public String getDateNeededBy() {
        return dateNeededBy;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public String getTransportId() {
        return transportId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getNote() {
        return note;
    }

    public String getWeight() {
        return weight;
    }

    public String getDownloadPhotoUrl() {
        return downloadPhotoUrl;
    }

    //HashMap getter
    public HashMap<String, Object> getTimestamp() {
        return timestamp;
    }

    @Exclude
    public long getTimestampLong() {
        return (long) timestamp.get("timestamp");
    }

    //SETTERS
    public void setStatus(String status) {
        this.status = status;
    }

    public void setUrgency(boolean urgency) {
        this.urgency = urgency;
    }

    public void setOriginCity(String originCity) {
        this.originCity = originCity;
    }

    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
    }

    public void setDateNeededBy(String dateNeededBy) {
        this.dateNeededBy = dateNeededBy;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setTransportId(String transportId) {
        this.transportId = transportId;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public void setDownloadPhotoUrl (String downloadPhotoUrl) {
        this.downloadPhotoUrl = downloadPhotoUrl;
    }

    //Implement Parcelable methods:
    //Copy Transport object into a parcel for transmission from one activity to another
    @Override
    public int describeContents() {
        return 0;
    }

    //Write object values to parcel for storage
    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(status);
        parcel.writeValue(urgency);
        parcel.writeString(originCity);
        parcel.writeString(destinationCity);
        parcel.writeString(dateNeededBy);
        parcel.writeString(name);
        parcel.writeString(gender);
        parcel.writeString(transportId);
        parcel.writeString(photoUrl);
        parcel.writeString(note);
        parcel.writeString(weight);
        parcel.writeString(downloadPhotoUrl);
    }

    //Constructor for parcelable called by the receiving activity
    public Transport (Parcel parcel) {
        status = parcel.readString();
        urgency = (Boolean) parcel.readValue(null);
        originCity = parcel.readString();
        destinationCity = parcel.readString();
        dateNeededBy = parcel.readString();
        name = parcel.readString();
        gender = parcel.readString();
        transportId = parcel.readString();
        photoUrl = parcel.readString();
        note = parcel.readString();
        weight = parcel.readString();
        downloadPhotoUrl = parcel.readString();
    }

    public static final Parcelable.Creator<Transport> CREATOR = new Parcelable.Creator<Transport>() {
        @Override
        public Transport createFromParcel(Parcel parcel) {
            return new Transport(parcel);
        }

        @Override
        public Transport[] newArray(int size) {
            return new Transport[0];
        }
    };

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

