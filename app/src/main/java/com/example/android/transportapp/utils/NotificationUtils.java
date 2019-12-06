package com.example.android.transportapp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationBuilderWithBuilderAccessor;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.android.transportapp.MainActivity;
import com.example.android.transportapp.R;

/**
 * Utility class for creating transport notifications whenever a new transport is added or an existing
 * transport is updated
 */

public class NotificationUtils {

    private static final int TRANSPORT_NOTIFICATION_PENDING_INTENT_ID = 11;
    private static final String NOTIFICATION_CHANNEL_ID = "transport-notification-channel";
    private static final int TRANSPORT_NOTIFICATION_ID = 23;

    public static void notifyUserOfUpdate (Context context) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        //Create channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.main_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setSmallIcon(R.drawable.ic_add_pet)
                .setLargeIcon(largeIcon(context))
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText("Help Needed: Modesto to Loomis")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Help Needed: Modesto to Loomis."))
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true);

        // If the build version is greater than or equal to JELLY_BEAN and less than OREO,
        // set the notification's priority to PRIORITY_HIGH.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        notificationManager.notify(TRANSPORT_NOTIFICATION_ID, notificationBuilder.build());
    }

    //Helper method returns a PendingIntent that opens the MainActivity when a notification
    //is pressed
    private static PendingIntent contentIntent (Context context) {
        Intent startMainActivity = new Intent (context, MainActivity.class);
        return PendingIntent.getActivity(
                context,
                TRANSPORT_NOTIFICATION_PENDING_INTENT_ID,
                startMainActivity,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Bitmap largeIcon (Context context) {
        Resources res = context.getResources();
        Bitmap largeIcon = BitmapFactory.decodeResource(res, R.drawable.launch_icon);
        return largeIcon;
    }

}
