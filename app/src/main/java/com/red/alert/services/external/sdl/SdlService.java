package com.red.alert.services.external.sdl;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.activities.Main;
import com.red.alert.activities.external.SdlLockscreen;
import com.red.alert.config.Logging;
import com.red.alert.services.sound.StopSoundService;
import com.smartdevicelink.exception.SdlException;
import com.smartdevicelink.exception.SdlExceptionCause;
import com.smartdevicelink.proxy.RPCRequest;
import com.smartdevicelink.proxy.SdlProxyALM;
import com.smartdevicelink.proxy.callbacks.OnServiceEnded;
import com.smartdevicelink.proxy.callbacks.OnServiceNACKed;
import com.smartdevicelink.proxy.interfaces.IProxyListenerALM;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.AddCommandResponse;
import com.smartdevicelink.proxy.rpc.AddSubMenuResponse;
import com.smartdevicelink.proxy.rpc.AlertManeuverResponse;
import com.smartdevicelink.proxy.rpc.AlertResponse;
import com.smartdevicelink.proxy.rpc.ChangeRegistrationResponse;
import com.smartdevicelink.proxy.rpc.CreateInteractionChoiceSetResponse;
import com.smartdevicelink.proxy.rpc.DeleteCommandResponse;
import com.smartdevicelink.proxy.rpc.DeleteFileResponse;
import com.smartdevicelink.proxy.rpc.DeleteInteractionChoiceSetResponse;
import com.smartdevicelink.proxy.rpc.DeleteSubMenuResponse;
import com.smartdevicelink.proxy.rpc.DiagnosticMessageResponse;
import com.smartdevicelink.proxy.rpc.DialNumberResponse;
import com.smartdevicelink.proxy.rpc.EndAudioPassThruResponse;
import com.smartdevicelink.proxy.rpc.GenericResponse;
import com.smartdevicelink.proxy.rpc.GetDTCsResponse;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.Image;
import com.smartdevicelink.proxy.rpc.ListFiles;
import com.smartdevicelink.proxy.rpc.ListFilesResponse;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnAudioPassThru;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnDriverDistraction;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnHashChange;
import com.smartdevicelink.proxy.rpc.OnKeyboardInput;
import com.smartdevicelink.proxy.rpc.OnLanguageChange;
import com.smartdevicelink.proxy.rpc.OnLockScreenStatus;
import com.smartdevicelink.proxy.rpc.OnPermissionsChange;
import com.smartdevicelink.proxy.rpc.OnStreamRPC;
import com.smartdevicelink.proxy.rpc.OnSystemRequest;
import com.smartdevicelink.proxy.rpc.OnTBTClientState;
import com.smartdevicelink.proxy.rpc.OnTouchEvent;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.PerformAudioPassThruResponse;
import com.smartdevicelink.proxy.rpc.PerformInteractionResponse;
import com.smartdevicelink.proxy.rpc.PutFile;
import com.smartdevicelink.proxy.rpc.PutFileResponse;
import com.smartdevicelink.proxy.rpc.ReadDIDResponse;
import com.smartdevicelink.proxy.rpc.ResetGlobalPropertiesResponse;
import com.smartdevicelink.proxy.rpc.ScrollableMessageResponse;
import com.smartdevicelink.proxy.rpc.SendLocationResponse;
import com.smartdevicelink.proxy.rpc.SetAppIconResponse;
import com.smartdevicelink.proxy.rpc.SetDisplayLayoutResponse;
import com.smartdevicelink.proxy.rpc.SetGlobalPropertiesResponse;
import com.smartdevicelink.proxy.rpc.SetMediaClockTimerResponse;
import com.smartdevicelink.proxy.rpc.ShowConstantTbtResponse;
import com.smartdevicelink.proxy.rpc.ShowResponse;
import com.smartdevicelink.proxy.rpc.SliderResponse;
import com.smartdevicelink.proxy.rpc.SoftButton;
import com.smartdevicelink.proxy.rpc.SpeakResponse;
import com.smartdevicelink.proxy.rpc.StreamRPCResponse;
import com.smartdevicelink.proxy.rpc.SubscribeButtonResponse;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.SystemRequestResponse;
import com.smartdevicelink.proxy.rpc.UnsubscribeButtonResponse;
import com.smartdevicelink.proxy.rpc.UnsubscribeVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.UpdateTurnListResponse;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.ImageType;
import com.smartdevicelink.proxy.rpc.enums.LockScreenStatus;
import com.smartdevicelink.proxy.rpc.enums.SdlDisconnectedReason;
import com.smartdevicelink.proxy.rpc.enums.TextAlignment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class SdlService extends Service implements IProxyListenerALM
{
    private static final String APP_NAME = "RedAlert";
    private static final String APP_ID = "4235301937";

    private static final String WELCOME_SHOW = "Welcome to RedAlert";
    private static final String WELCOME_SPEAK = "Welcome to Red Alert";

    // Alert GUI
    private static final String ALERT_SHOW = "WARNING!";
    private static final String ALERT_SPEAK = "Warning! A rocket alert is now present in your area";

    private static final String REMOTE_APP_ICON_FILENAME = "ic_launcher_3.png";
    private static final String REMOTE_ALERT_ICON_FILENAME = "ic_redalert_2.png";

    // "Silence Alert" Command
    private static final int SILENCE_ALERT_COMMAND_ID = 1;
    private static final String SILENCE_ALERT_ICON = "0x12";
    private static final String SILENCE_ALERT_COMMAND = "Silence Alert";

    // "Silence Alert" Command
    private static final int SHOW_ALERTS_COMMAND_ID = 1;
    private static final String SHOW_ALERTS_ICON = "0x4F";
    private static final String SHOW_ALERTS_COMMAND = "Show Alerts";

    // Allow binding to service
    IBinder mServiceBinder;

    List<String> mRemoteFiles;

    // variable used to increment correlation ID for every request sent to SYNC
    public int mAutoIncCorrId = 0;
    private int mIconCorrelationId;

    // variable to create and call functions of the SyncProxy
    private SdlProxyALM mProxy = null;

    private boolean mFirstNonHmiNone = true;
    private boolean mLockScreenDisplayed = false;
    private boolean mIsVehicleDataSubscribed = false;

    @Override
    public IBinder onBind(Intent arg0)
    {
        // Provide service binder
        return mServiceBinder;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        // Initialize binder
        mServiceBinder = new LocalBinder();

        mRemoteFiles = new ArrayList<String>();
    }

    public class LocalBinder extends Binder
    {
        public SdlService getService()
        {
            // Return the instance
            return SdlService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(Logging.TAG, "SDL Started");

        if (intent != null)
        {
            startProxy();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        disposeSyncProxy();

        //LockScreenManager.clearLockScreen();

        super.onDestroy();
    }

    public void startProxy()
    {
        if (mProxy == null)
        {
            try
            {
                mProxy = new SdlProxyALM(this, APP_NAME, true, APP_ID);
            }
            catch (SdlException e)
            {
                Log.e(Logging.TAG, "Exception", e);

                // error creating proxy, returned proxy = null
                if (mProxy == null)
                {
                    stopSelf();
                }
            }
        }
    }

    public void disposeSyncProxy()
    {
        if (mProxy != null)
        {
            try
            {
                mProxy.dispose();
            }
            catch (SdlException e)
            {
                Log.e(Logging.TAG, "Exception", e);
            }

            mProxy = null;
            //LockScreenManager.clearLockScreen();
        }

        this.mFirstNonHmiNone = true;
        this.mIsVehicleDataSubscribed = false;
    }

    public void reset()
    {
        if (mProxy != null)
        {
            try
            {
                mProxy.resetProxy();

                this.mFirstNonHmiNone = true;
                this.mIsVehicleDataSubscribed = false;
            }
            catch (SdlException e1)
            {
                Log.e(Logging.TAG, "Exception", e1);

                // Proxy reset failed - stop the service
                if (mProxy == null)
                {
                    stopSelf();
                }
            }
        }
        else
        {
            startProxy();
        }
    }

    /**
     * Will show a sample test message on screen as well as speak a sample test message
     */
    public void silenceAlertRequested()
    {
        try
        {
            // Stop the media service
            StopSoundService.stop(this);

            // Change the view?
            //mProxy.show(SILENCE_ALERT_COMMAND, "Command has been selected", TextAlignment.CENTERED, mAutoIncCorrId++);

            // Say something?
            //mProxy.speak(SILENCE_ALERT_COMMAND, mAutoIncCorrId++);
        }
        catch (Exception e)
        {
            Log.e(Logging.TAG, "Exception", e);
        }
    }

    /**
     * Add commands for the app on SDL.
     */
    public void sendCommands()
    {
        // Add both voice commands
        //addVoiceCommand( SHOW_ALERTS_COMMAND_ID, SHOW_ALERTS_COMMAND, SHOW_ALERTS_ICON );
        addVoiceCommand(SILENCE_ALERT_COMMAND_ID, SILENCE_ALERT_COMMAND, SILENCE_ALERT_ICON);

        // Log it
        Log.d(Logging.TAG, "Voice commands installed");
    }

    public void addVoiceCommand(int commandId, String voiceCommand, String iconResource)
    {
        // Set up menu option
        MenuParams params = new MenuParams();
        params.setMenuName(voiceCommand);

        // Set up command icon
        Image icon = new Image();

        icon.setImageType(ImageType.STATIC);
        icon.setValue(iconResource);

        // Set up voice command
        AddCommand command = new AddCommand();

        command.setCmdIcon(icon);
        command.setCmdID(commandId);
        command.setMenuParams(params);
        command.setVrCommands(Arrays.asList(new String[]{voiceCommand}));

        // Finally, send request to TDK
        sendRpcRequest(command);
    }

    /**
     * Sends an RPC Request to the connected head unit. Automatically adds a correlation id.
     *
     * @param request
     */
    private void sendRpcRequest(RPCRequest request)
    {
        request.setCorrelationID(mAutoIncCorrId++);

        try
        {
            mProxy.sendRPCRequest(request);
        }
        catch (SdlException e)
        {
            Log.e(Logging.TAG, "Exception", e);
        }
    }

    /**
     * Sends the app icon through the uploadImage method with correct params
     *
     * @throws SdlException
     */
    private void sendIcons() throws SdlException
    {
        // Upload other images
        uploadImage(R.drawable.ic_cover, REMOTE_ALERT_ICON_FILENAME, mAutoIncCorrId++, true);

        // Upload app icon
        mIconCorrelationId = mAutoIncCorrId++;
        uploadImage(R.drawable.ic_sdl, REMOTE_APP_ICON_FILENAME, mIconCorrelationId, true);
    }

    /**
     * This method will help upload an image to the head unit
     *
     * @param resource      the R.drawable.__ value of the image you wish to send
     * @param imageName     the filename that will be used to reference this image
     * @param correlationId the correlation id to be used with this request. Helpful for monitoring putfileresponses
     * @param isPersistent  tell the system if the file should stay or be cleared out after connection.
     */
    private void uploadImage(int resource, String imageName, int correlationId, boolean isPersistent)
    {
        PutFile putFile = new PutFile();

        putFile.setFileType(FileType.GRAPHIC_PNG);
        putFile.setSdlFileName(imageName);
        putFile.setCorrelationID(correlationId);
        putFile.setPersistentFile(isPersistent);
        putFile.setSystemFile(false);
        putFile.setBulkData(contentsOfResource(resource));

        try
        {
            mProxy.sendRPCRequest(putFile);
        }
        catch (SdlException e)
        {
            Log.e(Logging.TAG, "Exception", e);
        }
    }

    /**
     * Helper method to take resource files and turn them into byte arrays
     *
     * @param resource
     * @return
     */
    private byte[] contentsOfResource(int resource)
    {
        InputStream is = null;
        try
        {
            is = getResources().openRawResource(resource);
            ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());
            final int buffersize = 4096;
            final byte[] buffer = new byte[buffersize];
            int available = 0;
            while ((available = is.read(buffer)) >= 0)
            {
                os.write(buffer, 0, available);
            }
            return os.toByteArray();
        }
        catch (IOException e)
        {
            Log.w("SDL Service", "Can't read icon file", e);
            return null;
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    Log.e(Logging.TAG, "Exception", e);
                }
            }
        }
    }

    @Override
    public void onProxyClosed(String info, Exception e, SdlDisconnectedReason reason)
    {
        if (!(e instanceof SdlException))
        {
            Log.v(Logging.TAG, "Reset proxy in onProxy closed");
            reset();
        }
        else if ((((SdlException) e).getSdlExceptionCause() != SdlExceptionCause.SDL_PROXY_CYCLED))
        {
            if (((SdlException) e).getSdlExceptionCause() != SdlExceptionCause.BLUETOOTH_DISABLED)
            {
                Log.v(Logging.TAG, "Reset proxy in onProxy closed");
                reset();
            }
        }

        clearLockScreen();

        stopSelf();
    }

    @Override
    public void onOnHMIStatus(OnHMIStatus notification)
    {
        if (notification.getHmiLevel().equals(HMILevel.HMI_FULL))
        {
            if (notification.getFirstRun())
            {
                // send welcome message if applicable
                performWelcomeMessage();
            }
            // Other HMI (Show, PerformInteraction, etc.) would go here
        }

        // HMI Level None?
        if (!notification.getHmiLevel().equals(HMILevel.HMI_NONE) && mFirstNonHmiNone)
        {
            sendCommands();

            //uploadImages();
            mFirstNonHmiNone = false;

            // Other app setup (SubMenu, CreateChoiceSet, etc.) would go here
        }
        else
        {
            //We have HMI_NONE
            if (notification.getFirstRun())
            {
                uploadImages();
            }
        }
    }

    /**
     * Will show a sample welcome message on screen as well as speak a sample welcome message
     */
    private void performWelcomeMessage()
    {
        try
        {
            // Set the welcome message on screen
            mProxy.show(APP_NAME, WELCOME_SHOW, TextAlignment.CENTERED, mAutoIncCorrId++);

            //Say the welcome message
            //mProxy.speak(WELCOME_SPEAK, mAutoIncCorrId++);
        }
        catch (SdlException e)
        {
            Log.e(Logging.TAG, "Exception", e);
        }
    }

    private Image getAlertIcon()
    {
        // Set up command icon
        Image icon = new Image();

        icon.setImageType(ImageType.DYNAMIC);
        icon.setValue(REMOTE_ALERT_ICON_FILENAME);

        return icon;
    }
    /**
     * Will notify the driver when a rocket alert sounds
     */
    public void notifyRocketAlert(String title, String description)
    {
        try
        {
            // Set the welcome message on screen
            mProxy.show(title, ALERT_SPEAK, description, "123", "TEST", null, "TRACK", getAlertIcon(), new Vector<SoftButton>(), null, TextAlignment.CENTERED, mAutoIncCorrId++);

            //Say the welcome message
            mProxy.speak(ALERT_SPEAK + ". " + title, mAutoIncCorrId++);
        }
        catch (SdlException e)
        {
            Log.e(Logging.TAG, "Exception", e);
        }
    }

    /**
     * Requests list of images to SDL, and uploads images that are missing.
     */
    private void uploadImages()
    {
        ListFiles listFiles = new ListFiles();
        this.sendRpcRequest(listFiles);

    }

    @Override
    public void onListFilesResponse(ListFilesResponse response)
    {
        Log.i(Logging.TAG, "onListFilesResponse from SDL ");

        if (response.getSuccess())
        {
            mRemoteFiles = response.getFilenames();
        }

        // Check the mutable set for the AppIcon
        // If not present, upload the image
        if (mRemoteFiles == null || !mRemoteFiles.contains(SdlService.REMOTE_APP_ICON_FILENAME))
        {
            try
            {
                sendIcons();
            }
            catch (SdlException e)
            {
                Log.e(Logging.TAG, "Exception", e);
            }
        }
        else
        {
            try
            {
                // If the file is already present, send the SetAppIcon request
                mProxy.setappicon(REMOTE_APP_ICON_FILENAME, mAutoIncCorrId++);
            }
            catch (SdlException e)
            {
                Log.e(Logging.TAG, "Exception", e);
            }
        }
    }

    @Override
    public void onPutFileResponse(PutFileResponse response)
    {
        Log.i(Logging.TAG, "onPutFileResponse from SDL");

        if (response.getCorrelationID().intValue() == mIconCorrelationId)
        { //If we have successfully uploaded our icon, we want to set it
            try
            {
                mProxy.setappicon(REMOTE_APP_ICON_FILENAME, mAutoIncCorrId++);
            }
            catch (SdlException e)
            {
                Log.e(Logging.TAG, "Exception", e);
            }
        }

    }

    @Override
    public void onOnLockScreenNotification(OnLockScreenStatus notification)
    {
        if (!mLockScreenDisplayed && notification.getShowLockScreen() == LockScreenStatus.REQUIRED)
        {
            // Show lock screen
            Intent intent = new Intent(getApplicationContext(), SdlLockscreen.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
            mLockScreenDisplayed = true;
            startActivity(intent);
        }
        else if (mLockScreenDisplayed && notification.getShowLockScreen() != LockScreenStatus.REQUIRED)
        {
            // Clear lock screen
            clearLockScreen();
        }
    }

    private void clearLockScreen()
    {
        Intent intent = new Intent(getApplicationContext(), Main.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mLockScreenDisplayed = false;
    }

    @Override
    public void onOnCommand(OnCommand notification)
    {
        Integer id = notification.getCmdID();
        if (id != null)
        {
            switch (id)
            {
                case SILENCE_ALERT_COMMAND_ID:
                    silenceAlertRequested();
                    break;
            }
            //onAddCommandClicked(id);
        }
    }

    /**
     * Callback method that runs when the add command response is received from SDL.
     */
    @Override
    public void onAddCommandResponse(AddCommandResponse response)
    {
        Log.i(Logging.TAG, "AddCommand response from SDL: " + response);

    }


	/*  Vehicle Data   */


    @Override
    public void onOnPermissionsChange(OnPermissionsChange notification)
    {
        Log.i(Logging.TAG, "Permision changed: " + notification);
        /* Uncomment to subscribe to vehicle data
		List<PermissionItem> permissions = notification.getPermissionItem();
		for(PermissionItem permission:permissions){
			if(permission.getRpcName().equalsIgnoreCase(FunctionID.SUBSCRIBE_VEHICLE_DATA.name())){
				if(permission.getHMIPermissions().getAllowed()!=null && permission.getHMIPermissions().getAllowed().size()>0){
					if(!mIsVehicleDataSubscribed){ //If we haven't already subscribed we will subscribe now
						//TODO: Add the vehicle data items you want to subscribe to
						//proxy.subscribevehicledata(gps, speed, rpm, fuelLevel, fuelLevel_State, instantFuelConsumption, externalTemperature, prndl, tirePressure, odometer, beltStatus, bodyInformation, deviceStatus, driverBraking, correlationID);
						proxy.subscribevehicledata(false, true, rpm, false, false, false, false, false, false, false, false, false, false, false, autoIncCorrId++);
					}
				}
			}
		}
		*/
    }

    @Override
    public void onSubscribeVehicleDataResponse(SubscribeVehicleDataResponse response)
    {
        if (response.getSuccess())
        {
            Log.i(Logging.TAG, "Subscribed to vehicle data");
            this.mIsVehicleDataSubscribed = true;
        }
    }

    @Override
    public void onOnVehicleData(OnVehicleData notification)
    {
        Log.i(Logging.TAG, "Vehicle data notification from SDL");
        //TODO Put your vehicle data code here
        //ie, notification.getSpeed().

    }

    /**
     * Rest of the SDL callbacks from the head unit
     */

    @Override
    public void onAddSubMenuResponse(AddSubMenuResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onCreateInteractionChoiceSetResponse(CreateInteractionChoiceSetResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onAlertResponse(AlertResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onDeleteCommandResponse(DeleteCommandResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onDeleteInteractionChoiceSetResponse(DeleteInteractionChoiceSetResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onDeleteSubMenuResponse(DeleteSubMenuResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onPerformInteractionResponse(PerformInteractionResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onResetGlobalPropertiesResponse(
            ResetGlobalPropertiesResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSetGlobalPropertiesResponse(SetGlobalPropertiesResponse response)
    {
    }

    @Override
    public void onSetMediaClockTimerResponse(SetMediaClockTimerResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onShowResponse(ShowResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSpeakResponse(SpeakResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onOnButtonEvent(OnButtonEvent notification)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onOnButtonPress(OnButtonPress notification)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSubscribeButtonResponse(SubscribeButtonResponse response)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onUnsubscribeButtonResponse(UnsubscribeButtonResponse response)
    {
        // TODO Auto-generated method stub
    }


    @Override
    public void onOnTBTClientState(OnTBTClientState notification)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void onUnsubscribeVehicleDataResponse(
            UnsubscribeVehicleDataResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onGetVehicleDataResponse(GetVehicleDataResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onReadDIDResponse(ReadDIDResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onGetDTCsResponse(GetDTCsResponse response)
    {
        // TODO Auto-generated method stub

    }


    @Override
    public void onPerformAudioPassThruResponse(PerformAudioPassThruResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onEndAudioPassThruResponse(EndAudioPassThruResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnAudioPassThru(OnAudioPassThru notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDeleteFileResponse(DeleteFileResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSetAppIconResponse(SetAppIconResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onScrollableMessageResponse(ScrollableMessageResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onChangeRegistrationResponse(ChangeRegistrationResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSetDisplayLayoutResponse(SetDisplayLayoutResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnLanguageChange(OnLanguageChange notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSliderResponse(SliderResponse response)
    {
        // TODO Auto-generated method stub

    }


    @Override
    public void onOnHashChange(OnHashChange notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnSystemRequest(OnSystemRequest notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSystemRequestResponse(SystemRequestResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnKeyboardInput(OnKeyboardInput notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnTouchEvent(OnTouchEvent notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDiagnosticMessageResponse(DiagnosticMessageResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnStreamRPC(OnStreamRPC notification)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStreamRPCResponse(StreamRPCResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDialNumberResponse(DialNumberResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSendLocationResponse(SendLocationResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onServiceEnded(OnServiceEnded serviceEnded)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onServiceNACKed(OnServiceNACKed serviceNACKed)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onShowConstantTbtResponse(ShowConstantTbtResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onAlertManeuverResponse(AlertManeuverResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUpdateTurnListResponse(UpdateTurnListResponse response)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onServiceDataACK()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOnDriverDistraction(OnDriverDistraction notification)
    {
        // Some RPCs (depending on region) cannot be sent when driver distraction is active.
    }

    @Override
    public void onError(String info, Exception e)
    {
        Log.d(Logging.TAG, info, e);

        // TODO Auto-generated method stub
    }

    @Override
    public void onGenericResponse(GenericResponse response)
    {
        // TODO Auto-generated method stub
    }

}
