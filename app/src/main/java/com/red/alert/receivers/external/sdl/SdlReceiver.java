package com.red.alert.receivers.external.sdl;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.red.alert.services.external.sdl.SdlService;

public class SdlReceiver  extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {
        //final BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        Log.d("Receiver", intent.getAction());

        // if SYNC connected to phone via bluetooth, start service (which starts proxy)
        if (intent.getAction().compareTo(BluetoothDevice.ACTION_ACL_CONNECTED) == 0)
        {
            Intent startIntent = new Intent(context, SdlService.class);
            startIntent.putExtras(intent);
            context.startService(startIntent);
        }
        else if (intent.getAction().equals(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            // signal your service to stop playback
        }
    }
}