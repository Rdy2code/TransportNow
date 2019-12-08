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
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.android.transportapp.MainActivity;
import com.example.android.transportapp.R;
import com.example.android.transportapp.Transport;

/**
 * Utility class for creating transport notifications whenever a new transport is added or an existing
 * transport is updated
 */

public class NotificationUtils {

    private static final int TRANSPORT_NOTIFICATION_PENDING_INTENT_ID = 10;
    private static final String NOTIFICATION_CHANNEL_ID = "transport-notification-channel";
    private static final int TRANSPORT_NOTIFICATION_ID = 11;
    public static final String ACTION_DISMISS_NOTIFICATION = "dismiss-notification";
    private static final int ACTION_DISMISS_NOTIFICATION_PENDING_INTENT_ID = 12;

    public static void notifyUserOfUpdate (Context context, Transport newTransport) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        //Create channel and set to high to force peek-in function
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.main_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        //Get values from Transport object that represents the latest transport
        String dateNeededBy = newTransport.getDateNeededBy();
        String originCity = newTransport.getOriginCity();
        String destinationCity = newTransport.getDestinationCity();
        String status = newTransport.getStatus();

        //Build the notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setSmallIcon(R.drawable.ic_add_pet)
                .setLargeIcon(largeIcon(context))
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(status + " - " + dateNeededBy)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        dateNeededBy + "\n" + originCity + " to " + destinationCity))
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(contentIntent(context))
                .addAction(dismissNotificationAction(context))
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

    private static PendingIntent dismissNotificationIntent (Context context) {
        Intent dismissIntent = new Intent (context, TransportRequestService.class);
        dismissIntent.setAction(ACTION_DISMISS_NOTIFICATION);
        PendingIntent dismissPendingIntent = PendingIntent.getService(
                context,
                ACTION_DISMISS_NOTIFICATION_PENDING_INTENT_ID,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return dismissPendingIntent;
    }

    private static NotificationCompat.Action dismissNotificationAction (Context context) {
        NotificationCompat.Action ignoreNotificationAction = new NotificationCompat.Action(
                R.drawable.ic_cancel,
                context.getString(R.string.dismiss_notification),
                dismissNotificationIntent(context));
        return  ignoreNotificationAction;
    }

    //Create the bitmap image for the large icon that is displayed to the right of the content
    private static Bitmap largeIcon (Context context) {
        Resources res = context.getResources();
        Bitmap largeIcon = BitmapFactory.decodeResource(res, R.drawable.launch_icon);
        return largeIcon;
    }

    public static void clearAllNotifications (Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }

    public static void setTransport (Context context, Transport newTransport) {
        notifyUserOfUpdate (context, newTransport);
    }

}
