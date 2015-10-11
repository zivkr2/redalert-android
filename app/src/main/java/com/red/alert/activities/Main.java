package com.red.alert.activities;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.red.alert.R;
import com.red.alert.activities.settings.General;
import com.red.alert.config.API;
import com.red.alert.config.Alerts;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.Integrations;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.push.GCMRegistration;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.model.res.VersionInfo;
import com.red.alert.ui.adapters.AlertAdapter;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.dialogs.custom.LocationDialogs;
import com.red.alert.ui.dialogs.custom.UpdateDialogs;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.bugtracking.SplunkMINT;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.integration.WhatsApp;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends AppCompatActivity
{
    boolean mIsResumed;
    boolean mIsDestroyed;
    boolean mIsReloading;

    Button mImSafe;
    ListView mAlertsList;
    ProgressBar mLoading;
    MenuItem mLoadingItem;
    LinearLayout mNoAlerts;
    AlertAdapter mAlertsAdapter;

    List<Alert> mNewAlerts;
    List<Alert> mDisplayAlerts;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Initialize bug tracking
        SplunkMINT.logExceptions(this);

        // Initialize app UI
        initializeUI();

        // Got any dialogs to display?
        showImportantDialogs();

        // Start polling for recent alerts
        pollRecentAlerts();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);

        // Start the app services
        ServiceManager.startAppServices(this);
    }

    void initializeUpdateChecker()
    {
        // Do it async
        new CheckForUpdatesAsync().execute();
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        // Clear app notifications
        AppNotifications.clearAll(this);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI()
    {
        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set title manually (for override locale to work)
        setTitle(R.string.recentAlerts);

        // Set up UI
        setContentView(R.layout.main);

        // Get views by IDs
        mImSafe = (Button) findViewById(R.id.safe);
        mAlertsList = (ListView) findViewById(R.id.alerts);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mNoAlerts = (LinearLayout) findViewById(R.id.noAlerts);

        // Initialize alert list
        mNewAlerts = new ArrayList<>();
        mDisplayAlerts = new ArrayList<>();

        // Create alert adapter
        mAlertsAdapter = new AlertAdapter(this, mDisplayAlerts);

        // Attach it to list
        mAlertsList.setAdapter(mAlertsAdapter);

        // On-click listeners
        initializeListeners();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        // Avoid call to super(). Bug on API Level > 11.
    }

    void initializeListeners()
    {
        // Alert click listener
        mAlertsList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
            {
                // Get alert by click position
                Alert alert = mDisplayAlerts.get(position);

                // No such alert?
                if (alert == null)
                {
                    return;
                }

                // Create new intent
                Intent alertView = new Intent();

                // Set class
                alertView.setClass(Main.this, AlertView.class);

                // Push extras
                alertView.putExtra(AlertViewParameters.ALERT_ZONE, alert.localizedZone);
                alertView.putExtra(AlertViewParameters.ALERT_DATE_STRING, alert.dateString);

                // Show it
                startActivity(alertView);
            }
        });

        // No alerts image click listener
        mNoAlerts.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // DEBUG ONLY
                //AlertLogic.processIncomingAlert("עוטף עזה 217", "alert", Main.this);
            }
        });

        // "I'm Safe" button click listener
        mImSafe.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                // DEBUG ONLY
                AlertLogic.processIncomingAlert("חיפה והקריות 75", "alert", Main.this);

//                // Prepare share intent
//                Intent shareIntent = new Intent(Intent.ACTION_SEND);
//
//                // Set as text/plain
//                shareIntent.setType("text/plain");
//
//                // Add text
//                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.imSafeMessage));
//
//                // Send via WhatsApp?
//                if (Singleton.getSharedPreferences(Main.this).getBoolean(getString(R.string.imSafeWhatsAppPref), false))
//                {
//                    // WhatsApp installed?
//                    if (WhatsApp.isAppInstalled(Main.this))
//                    {
//                        // Set WhatsApp package
//                        shareIntent.setPackage(Integrations.WHATSAPP_PACKAGE);
//                    }
//                    else
//                    {
//                        // Show toast
//                        Toast.makeText(Main.this, getString(R.string.whatsAppNotInstalled), Toast.LENGTH_SHORT).show();
//                    }
//                }
//
//                // Show chooser
//                startActivity(Intent.createChooser(shareIntent, getString(R.string.imSafeDesc)));
            }
        });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // Save state
        mIsResumed = true;

        // Reload alerts manually
        reloadRecentAlerts();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear app notifications
        AppNotifications.clearAll(this);

        // Ask user to enable GPS if necessary
        LocationDialogs.requestEnableLocationServices(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // Save state
        mIsResumed = false;

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener()
    {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key)
        {
            // Asked for reload?
            if (Key.equalsIgnoreCase(MainActivityParameters.RELOAD_RECENT_ALERTS))
            {
                reloadRecentAlerts();
            }
        }
    };

    void showImportantDialogs()
    {
        // Do we need to register for GCM or Pushy?
        if (!GCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
        {
            // Register async
            new RegisterPushAsync().execute();

            // Avoid checking for updates now (to avoid duplicate dialog)
            return;
        }

        // Check for updates
        initializeUpdateChecker();
    }

    void pollRecentAlerts()
    {
        // Schedule a new timer
        new Timer().scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // App is running?
                        if (mIsResumed)
                        {
                            // Reload every X seconds
                            reloadRecentAlerts();
                        }
                    }
                });
            }
        }, 0, 1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
    }

    void reloadRecentAlerts()
    {
        // Not already reloading?
        if (!mIsReloading)
        {
            // Get recent alerts async
            new GetRecentAlertsAsync().execute();
        }
    }

    void initializeSettingsButton(Menu OptionsMenu)
    {
        // Add refresh in Action Bar
        MenuItem settingsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.settings));

        // Set up the view
        settingsItem.setIcon(R.drawable.ic_settings);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(settingsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, go to Settings
        settingsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
                // Start settings activity
                goToSettings(false);

                // Consume event
                return true;
            }
        });
    }

    void goToSettings(boolean showRegionSelection)
    {
        // Prepare new intent
        Intent settingsIntent = new Intent();

        // Set settings class
        settingsIntent.setClass(Main.this, General.class);

        // Add area selection boolean
        settingsIntent.putExtra(SettingsEvents.SHOW_REGION_SELECTION, showRegionSelection);

        // Start settings activity
        startActivity(settingsIntent);
    }

    void initializeLoadingIndicator(Menu OptionsMenu)
    {
        // Add refresh in Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.loading));

        // Set up the view
        MenuItemCompat.setActionView(mLoadingItem, R.layout.loading);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mLoadingItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Hide by default
        mLoadingItem.setVisible(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu)
    {
        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        // Add settings button
        initializeSettingsButton(OptionsMenu);

        // Show the menu
        return true;
    }

    private int getRecentAlerts()
    {
        // Store JSON as string initially
        String alertsJSON;

        try
        {
            // Get it from /alerts
            alertsJSON = HTTP.get(API.API_ENDPOINT + "/alerts");
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.apiRequestFailed;
        }

        // Prepare tmp object list
        List<Alert> recentAlerts;

        try
        {
            // Convert JSON to object
            recentAlerts = Singleton.getJackson().readValue(alertsJSON, new TypeReference<List<Alert>>(){});
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.jsonFailed;
        }

        // Initialize date format
        SimpleDateFormat dateFormat = new SimpleDateFormat(Alerts.DATE_FORMAT);

        // Loop over alerts
        for (Alert alert : recentAlerts)
        {
            // Convert date to string
            alert.dateString = dateFormat.format(alert.date * 1000);

            // Convert area to friendly name
            alert.cities = LocationData.getCityNamesByZone(alert.zone, this);

            // Localize it
            alert.localizedZone = LocationData.getLocalizedZone(alert.zone, this);
        }

        // Clear global list
        mNewAlerts.clear();

        // Add all the new alerts
        mNewAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }

    private String checkForUpdates()
    {
        // Grab the update JSON
        String updateJson;

        try
        {
            // Get it from /update/android
            updateJson = HTTP.get(API.API_ENDPOINT + "/update/android");
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Get update info failed", exc);

            // Return error code
            return null;
        }

        // Convert into JSON
        VersionInfo updateInfo;

        try
        {
            // Convert JSON to object
            updateInfo = Singleton.getJackson().readValue(updateJson, com.red.alert.model.res.VersionInfo.class);
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Parsing update info failed", exc);

            // Stop execution
            return null;
        }

        // Don't show update dialog?
        if (!updateInfo.showDialog)
        {
            return null;
        }

        // Update available? (Higher version code)
        if (updateInfo.versionCode > AppVersion.getVersionCode(this))
        {
            // Return newer version string for display
            return updateInfo.version;
        }

        // No need to update
        return null;
    }

    void toggleProgressBarVisibility(boolean visibility)
    {
        // Set loading visibility
        if (mLoadingItem != null)
        {
            mLoadingItem.setVisible(visibility);
        }
    }

    public class GetRecentAlertsAsync extends AsyncTaskAdapter<Integer, String, Integer>
    {
        public GetRecentAlertsAsync()
        {
            // Prevent concurrent reload
            mIsReloading = true;

            // Show loading indicator
            toggleProgressBarVisibility(true);
        }

        @Override
        protected Integer doInBackground(Integer... Parameter)
        {
            // Try to get recent alerts
            return getRecentAlerts();
        }

        @Override
        protected void onPostExecute(Integer errorStringResource)
        {
            // No longer reloading
            mIsReloading = false;

            // Activity dead?
            if (isFinishing() || mIsDestroyed)
            {
                return;
            }

            // Hide loading
            mLoading.setVisibility(View.GONE);

            // Hide loading indicator
            toggleProgressBarVisibility(false);

            // Success?
            if (errorStringResource == 0)
            {
                // Invalidate the list
                invalidateAlertList();
            }
            else
            {
                // Show error toast
                Toast.makeText(Main.this, getString(errorStringResource), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // Avoid hiding invalid dialogs
        mIsDestroyed = true;
    }

    public class RegisterPushAsync extends AsyncTaskAdapter<Integer, String, Exception>
    {
        ProgressDialog mLoading;

        public RegisterPushAsync()
        {
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(Main.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.signing_up));

            // Show the progress dialog
            mLoading.show();
        }

        @Override
        protected Exception doInBackground(Integer... Parameter)
        {
            try
            {
                // Register for GCM push notifications
                GCMRegistration.registerForPushNotifications(Main.this);

                // Register for Pushy push notifications
                PushyRegistration.registerForPushNotifications(Main.this);
            }
            catch (Exception exc)
            {
                // Return exception to onPostExecute
                return exc;
            }

            // We're good!
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc)
        {
            // Activity dead?
            if (isFinishing() || mIsDestroyed)
            {
                return;
            }

            // Hide loading
            if (mLoading.isShowing())
            {
                mLoading.dismiss();
            }

            // Failed?
            if (exc != null)
            {
                // Log it
                Log.e(Logging.TAG, "Push registration failed", exc);

                // Build an error message
                String errorMessage = getString(R.string.pushRegistrationFailed) + "\n\n" + exc.toString();

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, Main.this, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // Show success dialog
                        showRegistrationSuccessDialog();
                    }
                });
            }
            else
            {
                // Success, show success dialog
                showRegistrationSuccessDialog();
            }
        }
    }

    public class CheckForUpdatesAsync extends AsyncTaskAdapter<Integer, String, String>
    {
        @Override
        protected String doInBackground(Integer... Parameter)
        {
            // Try to check for updates
            return checkForUpdates();
        }

        @Override
        protected void onPostExecute(String newVersion)
        {
            // Activity dead?
            if (isFinishing() || mIsDestroyed)
            {
                return;
            }

            // Got a return code?
            if (!StringUtils.stringIsNullOrEmpty(newVersion))
            {
                // Show update dialog
                UpdateDialogs.showUpdateDialog(Main.this, newVersion);
            }
        }
    }

    void invalidateAlertList()
    {
        // Clear global list
        mDisplayAlerts.clear();

        // Add all the new alerts
        mDisplayAlerts.addAll(mNewAlerts);

        // Invalidate the list
        mAlertsAdapter.notifyDataSetChanged();

        // No alerts? Show default
        if (mDisplayAlerts.size() == 0)
        {
            mNoAlerts.setVisibility(View.VISIBLE);
        }
        else
        {
            mNoAlerts.setVisibility(View.GONE);
        }
    }

    void showRegistrationSuccessDialog()
    {
        // Already displayed?
        if (AppPreferences.getTutorialDisplayed(this))
        {
            return;
        }

        // Build the dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.pushRegistrationSuccess), getString(R.string.pushRegistrationSuccessDesc), this, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                // Start settings activity
                goToSettings(true);
            }
        });

        // Tutorial was displayed
        AppPreferences.setTutorialDisplayed(this);
    }
}
