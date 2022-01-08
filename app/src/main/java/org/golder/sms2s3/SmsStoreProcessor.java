package org.golder.sms2s3;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonParseException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;


public class SmsStoreProcessor extends Thread {
    private ContentResolver resolver;
    private ProgressBar progress;
    private TextView textview;
    private SharedPreferences prefs;
    private DigestCache cache;

    public SmsStoreProcessor(ContentResolver resolver, ProgressBar progress, TextView textview, SharedPreferences prefs, DigestCache cache) {
        this.resolver = resolver;
        this.progress = progress;
        this.textview = textview;
        this.prefs = prefs;
        this.cache = cache;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    public void run() {
        Log.i("main", "Starting SMS Store Processor...");
        this.textview.setText("Starting SMS Store Processor...\n");

        // Check for credentials first
        String awsAccessKeyId = this.prefs.getString("aws_access_key_id", "");
        String awsSecretAccessKey = this.prefs.getString("aws_secret_access_key", "");
        if(awsAccessKeyId.equals("") || awsSecretAccessKey.equals("")) {
            this.textview.setText("AWS credentials are empty. Please configure in settings.\n");
            return;
        }

        // Find SMSs already on phone, send for processing
        Cursor cursor = this.resolver.query(Uri.parse("content://sms/inbox"), null, null, null, null);
        if (!cursor.moveToFirst()) {
            this.textview.append("No SMS messages found in inbox on phone.\n");
            this.progress.setMax(0);
            this.progress.setProgress(0);
            Log.e("processor", "No SMS messages found in inbox on phone.");
            return;
        }
        this.progress.setMax(cursor.getCount());
        this.textview.append("Found " + cursor.getCount() + " messages.\n");

        int count = 0;
        do {
            JSONObject msg = new JSONObject();
            String sender = "N/A";
            for(int idx = 0; idx < cursor.getColumnCount(); idx++) {
                String colname = cursor.getColumnName(idx);
                String colval = cursor.getString(idx);
                try {
                    if (colname.equals("date") || colname.equals("date_sent")) {
                        msg.put(colname, Long.valueOf(colval));
                    } else {
                        msg.put(colname, colval);
                    }
                    if (colname.equals("address")) {
                        sender = colval;
                    }
                }
                catch(JSONException e) {
                   Log.e("processor", "Could not add '" + colval + "' to JSON object.");
                }
            }

            // Dump whole message while debugging
            Log.d("processor", msg.toString());

            // Calculate message hash to use as hash key
            String objectname;
            try {
                String[] hashableitems = new String[3];
                hashableitems[0] = String.valueOf(msg.getLong("date"));
                hashableitems[1] = msg.getString("address");
                hashableitems[2] = msg.getString("body");
                String hashable = StringUtils.join(hashableitems);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(hashable.getBytes(StandardCharsets.UTF_8));
                objectname = bytesToHex(hash);
            }
            catch(JSONException je) {
                Log.e("processor", "Could not determine attributes of message to use for hash: " + je.toString());
                this.textview.append("Message attributes error from '" + sender + "': " + je.toString() + "\n");
                continue;
            }
            catch(NoSuchAlgorithmException nsae) {
                Log.e("processor", nsae.toString());
                this.textview.append("Hashing error from '" + sender + "': " + nsae.toString() + "\n");
                continue;
            }

            // Look up objectname in cache
            if(!cache.exists(objectname)) {
                // Pass message to S3
                try {
                    S3Uploader.upload(msg, objectname, this.prefs);
                    this.textview.append("Processed message from '" + sender + "'\n");
                    cache.add(objectname);
                } catch (IllegalArgumentException iae) {
                    Log.e("processor", iae.toString());
                    this.textview.append("Error processing message from '" + sender + "': " + iae.toString() + "\n");
                }
            }

            // Update progress
            count += 1;
            this.progress.setProgress(count);

        } while (cursor.moveToNext());

        // Flush cache to storage
        try {
            cache.save();
        }
        catch(IOException ioe) {
            Log.e("main", "Unable to save cache: " + ioe.toString());
        }
    }
}