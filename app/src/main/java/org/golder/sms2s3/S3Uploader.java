package org.golder.sms2s3;

import android.content.SharedPreferences;
import android.util.Log;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.google.gson.JsonParseException;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.StringUtils;


public class S3Uploader {
    public static S3Uploader instance;

    public static AmazonS3Client s3 = null;
    public static String region;
    public static String bucketname;

    public static void upload(JSONObject msg, String objectname, SharedPreferences prefs) throws IllegalArgumentException {

        // Avoid doing credentials thing too often
        if(s3 == null) {
            String awsAccessKeyId = prefs.getString("aws_access_key_id", "");
            String awsSecretAccessKey = prefs.getString("aws_secret_access_key", "");
            if (awsAccessKeyId.equals("") || awsSecretAccessKey.equals("")) {
                Log.w("s3uploader", "AWS credentials are empty. Please configure in settings.");
                return;
            }

            BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
                    awsAccessKeyId,
                    awsSecretAccessKey,
                    null);

            region = prefs.getString("aws_bucket_region", "");
            bucketname = prefs.getString("aws_bucket_name", "");
            s3 = new AmazonS3Client(sessionCredentials, Region.getRegion(region));
        }

        // Check whether we already have it
        Log.i("s3uploader", "Looking up object: " + objectname);
        boolean duplicate = false;
        try {
            duplicate = s3.doesObjectExist(bucketname, objectname);
        }
        catch(AmazonS3Exception s3e) {
            Log.w("s3uploader", s3e.toString());
        }
        if(duplicate) {
            Log.i("s3uploader", "Object " + objectname + " already exists on S3");
            return;
        }

        // Push to S3
        Log.i("s3uploader", "Uploading object '" + objectname + "'");
        s3.putObject(bucketname, objectname, msg.toString());

        // Notify success to increment progress bar in UI
        Log.i("s3uploader", "Uploaded object '" + objectname + "'");

    }
}
