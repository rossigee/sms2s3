package org.golder.sms2s3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.widget.Toolbar;


public class MainActivity extends AppCompatActivity {
    private TextView textView;
    private ProgressBar progress;

    private DigestCache cache;
    private SmsStoreProcessor processor;

    public final String APP_NAME = "SMS2S3";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check/acquire permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_SMS,
                        Manifest.permission.SEND_SMS},
                PackageManager.PERMISSION_GRANTED);

        // Set up main UI view
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        progress = findViewById(R.id.progressBar);
        textView.setText("Starting main activity...\n");

        Toolbar appToolbar = (Toolbar) findViewById(R.id.app_toolbar);
        setSupportActionBar(appToolbar);

        // Initialise cache
        String cachefilename = getCacheDir().getAbsolutePath() + File.separator + "cache";
        cache = DigestCache.getInstance();
        cache.setFilename(cachefilename);
        try {
            cache.load();
        }
        catch(IOException ioe) {
            Log.e("main", "Unable to read cache file");
        }

        // Start sweep of inbox
        refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        if (id == R.id.action_refresh) {
            refresh();
            return true;
        }
        if (id == R.id.action_clear_cache) {
            cache.clear();
            return true;
        }
        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refresh() {
        // Process any messages already on phone
        Log.i("main", "Running SMS Store Processor...");
        ContentResolver resolver = getContentResolver();
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if(processor != null) {
            processor.interrupt();
        }
        processor = new SmsStoreProcessor(resolver, progress, textView, prefs, cache);
        processor.start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Flush cache to storage
        try {
            cache.save();
        }
        catch(IOException ioe) {
            Log.e("main", "Unable to save cache: " + ioe.toString());
        }
    }
}