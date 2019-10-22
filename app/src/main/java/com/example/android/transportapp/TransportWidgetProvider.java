package com.example.android.transportapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.example.android.transportapp.utils.TransportRequestService;

/**
 * Implementation of App Widget functionality.
 */
public class TransportWidgetProvider extends AppWidgetProvider {

    //To update a widget, pass in the ID of the widget and a RemoteViews object describing the widget
    public static void updateAppWidget(Context context,
                                       AppWidgetManager appWidgetManager,
                                       int appWidgetId,
                                       String dateNeededBy,
                                       String originCity,
                                       String destinationCity) {

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.transport_widget_provider);

        //Update the TextViews with latest transport information
        views.setTextViewText(R.id.appwidget_text_date, dateNeededBy);
        views.setTextViewText(R.id.appwidget_text_destination, originCity + " to " + destinationCity);

        //Create a pending intent to launch MainActivity when the icon in the widget is clicked
        //RemoteViews must be linked to PendingIntents
        Intent intent = new Intent (context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        //Register and attach the click handler
        views.setOnClickPendingIntent(R.id.widget_image, pendingIntent);

        //Start the TransportRequestService click handler to get latest transport from the Firebase
        Intent latestTransportIntent = new Intent (context, TransportRequestService.class);
        latestTransportIntent.setAction(TransportRequestService.ACTION_GET_LATEST_TRANSPORT);
        PendingIntent latestTransportPendingIntent = PendingIntent.getService(
                context,
                0,
                latestTransportIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        views.setOnClickPendingIntent(R.id.appwidget_text_title, latestTransportPendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    //Called when each App Widget is added to a host and at each update interval, set in the xml file
    //The AppWidgetManager class gives access to information about all Widgets on the homescreen.
    //Also gives access to forcing an update on all widgets.
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        TransportRequestService.getLatestTransport(context);
    }

    public static void updateTransportWidgets (Context context,
                                               AppWidgetManager appWidgetManager,
                                               int[] appWidgetIds,
                                               String dateNeededBy,
                                               String originCity,
                                               String destinationCity) {

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context,
                    appWidgetManager,
                    appWidgetId,
                    dateNeededBy,
                    originCity,
                    destinationCity);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

