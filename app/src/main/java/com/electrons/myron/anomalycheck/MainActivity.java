package com.electrons.myron.anomalycheck;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    FirebaseRemoteConfig mFirebaseRemoteConfig;

    float threshold;

    private static final String TAG = "AnomalyCheck";
    private static final String ANOMALY_IMPACT = "Impact";
    private static final String ANOMALY_LATITUDE = "Latitude";
    private static final String ANOMALY_LONGITUDE = "Longitude";
    private static final String ANOMALY_TIMESTAMP = "TimeStamp";
    private static final int REQUEST_FINE_LOCATION = 1;

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private DocumentReference dr = FirebaseFirestore.getInstance().document("Anomalies/AnomalyDetails");

    TextView x, y, z;

    Handler h;

    ArrayList<String> items;

    ListView l1;

    ArrayAdapter<String> aa1;

    Button btnShowLocation;

    float x1 = 0.0f, y1 = 0.0f, z1 = 0.0f;

    double latitude,longitude;

    private LocationRequest mLocationRequest;

    private long UPDATE_INTERVAL = 8 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 4000; /* 2 sec */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);

        // cache expiration in seconds
        long cacheExpiration = 3600;

        //expire the cache immediately for development mode.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }

        // fetch
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            // task successful. Activate the fetched data
                            mFirebaseRemoteConfig.activateFetched();
                            Log.d(TAG,"Threshold Updated : " + threshold);

                        } else {

                        }
                    }
                });

        threshold = Float.parseFloat(mFirebaseRemoteConfig.getString("impact_threshold"));

        Log.d(TAG,"Threshold Value is : " + threshold);

        startLocationUpdates();

        l1 = findViewById(R.id.anomalyEvent);

        x = findViewById(R.id.x_axis);
        y = findViewById(R.id.y_axis);
        z = findViewById(R.id.z_axis);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener((SensorEventListener) this, mSensor, mSensorManager.SENSOR_DELAY_NORMAL);

        items = new ArrayList<>();

        aa1 = new ArrayAdapter<String>(this.getApplicationContext(), android.R.layout.simple_list_item_1, items);

        l1.setAdapter(aa1);

    }

    protected void startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            checkPermissionsFine();

            checkPermissions();

            return;
        }
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        // do work here
                        onLocationChanged(locationResult.getLastLocation());
                    }
                },
                Looper.myLooper());
    }


    public void onLocationChanged(Location location) {
        // New location has now been determined

        latitude = location.getLatitude();
        longitude = location.getLongitude();

        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
//        You can now create a LatLng Object for use with maps
//        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float x2 = event.values[0];
        float y2 = event.values[1];
        float z2 = event.values[2];

        if(z2 > (z1+threshold)) {

                Log.e(TAG,"lat : "+latitude);
                Log.e(TAG,"long : "+longitude);

                // \n is for new line
                Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_LONG).show();

                items.add("Impact :" + (z2 - z1) + "\nLatitude :" + latitude + "\n Longitude :" + longitude);

                Long tsLong = System.currentTimeMillis()/1000;

                final Map<String, Object> dataToSave = new HashMap<String, Object>();
                dataToSave.put(ANOMALY_IMPACT, (z2 - z1));
                dataToSave.put(ANOMALY_LATITUDE, latitude);
                dataToSave.put(ANOMALY_LONGITUDE, longitude);
                dataToSave.put(ANOMALY_TIMESTAMP, tsLong);

                dr.collection("AnomalyList").add(dataToSave).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.e(TAG, "Document Saved : " + documentReference.getId());
                        Log.e(TAG, "dataToSave : " + dataToSave);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Document Saved!", e);
                    }
                });


            } else {
                // Can't get location.
                // GPS or network is not enabled.
                // Ask user to enable GPS/network in settings.
                //gps.showSettingsAlert();
            }

            aa1.notifyDataSetChanged();

//            Log.e(TAG,"Anomaly Detected! - " + (z2 - z1));
//
//            Log.e(TAG,"List : " + items);

        x.setText("X : " + x2);
        y.setText("Y : " + y2);
        z.setText("Z : " + z2);

        x1 = x2;
        y1 = y2;
        z1 = z2;

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private boolean checkPermissionsFine() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissionsFine();
            return false;
        }
    }

    private void requestPermissionsFine() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_FINE_LOCATION);
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissions();
            return false;
        }
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_FINE_LOCATION);
    }

}
