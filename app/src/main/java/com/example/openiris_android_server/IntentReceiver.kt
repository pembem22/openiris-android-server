package com.example.openiris_android_server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class IntentReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            error("USB device attached intent expected")
        }

        val serviceIntent = Intent(context, ForegroundService::class.java)
        serviceIntent.putExtra(UsbManager.EXTRA_DEVICE, intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE))

        context.startService(serviceIntent)
    }
}