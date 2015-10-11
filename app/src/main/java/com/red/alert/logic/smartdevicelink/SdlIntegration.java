package com.red.alert.logic.smartdevicelink;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.services.external.sdl.SdlService;

public class SdlIntegration
{
    public static void updateSdlDisplay(final Context context, final String title, final int seconds)
    {
        // Bind to the SDL proxy service
        context.bindService(new Intent(context, SdlService.class), new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder)
            {
                // Get service instance
                SdlService service = ((SdlService.LocalBinder) binder).getService();

                // Do whatever we want now
                service.countdownRocketAlert(title, seconds);

                // Unbind fom service
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName)
            {
                // Log it
                Log.e(Logging.TAG, "Service disconnected");
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void alertDriver(final Context context, final String alertType, final String title, final String description)
    {
        // Only for primary alerts
        if (!alertType.equals(AlertTypes.PRIMARY))
        {
            // Stop execution
            return;
        }

        // Bind to the SDL proxy service
        context.bindService(new Intent(context, SdlService.class), new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder)
            {
                // Get service instance
                SdlService service = ((SdlService.LocalBinder) binder).getService();

                // Do whatever we want now
                service.notifyRocketAlert(title, description);

                // Unbind fom service
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName)
            {
                // Log it
                Log.e(Logging.TAG, "Service disconnected");
            }
        }, Context.BIND_AUTO_CREATE);
    }
}
