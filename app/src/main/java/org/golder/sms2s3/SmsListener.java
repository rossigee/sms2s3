package org.golder.sms2s3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;


public class SmsListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("listener", "Handling event " + intent.getAction());
        if (!intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.w("listener", "No data found.");
            return;
        }

        SmsMessage[] messages = null;
        String msgFrom;

        //---retrieve the SMS message received---
        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = (String)bundle.get("format");
            messages = new SmsMessage[pdus.length];
            for (int i = 0; i < messages.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                msgFrom = messages[i].getOriginatingAddress();
                Log.i("listener", "Handling SMS from '" + msgFrom + "'");

                String msgBody = messages[i].getMessageBody();
                Log.d("listener", msgBody);

                // Pass off as JSONObject to S3Uploader

                // Add something to UI to show receipt
                String strMessage = "SMS from '" + msgFrom + "'";
            }
        } catch (Exception e) {
            Log.e("listener", e.toString());
        }
    }
}