package com.electrons.myron.anomalycheck;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "AnomalyCheck" ;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    TextView x, y, z;

    Handler h;

    ArrayList<String> items;

    ListView l1;

    ArrayAdapter<String> aa1;


    Button btnShowLocation;

    // GPSTracker class
    GPSTracker gps;

    float x1 = 0.0f, y1 = 0.0f, z1 = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        l1 = findViewById(R.id.anomalyEvent);

        x = findViewById(R.id.x_axis);
        y = findViewById(R.id.y_axis);
        z = findViewById(R.id.z_axis);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener((SensorEventListener) this, mSensor, mSensorManager.SENSOR_DELAY_NORMAL);

        items = new ArrayList<>();

        aa1 = new ArrayAdapter<String>(this.getApplicationContext(), android.R.layout.simple_list_item_1,items);

        l1.setAdapter(aa1);



    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        float x2 = event.values[0];
        float y2 = event.values[1];
        float z2 = event.values[2];

        if(z2 > (z1+3)) {

            gps = new GPSTracker(MainActivity.this);

            // Check if GPS enabled
            if(gps.canGetLocation()) {

                double latitude = gps.getLatitude();
                double longitude = gps.getLongitude();

                Log.e(TAG,"lat : "+latitude);
                Log.e(TAG,"long : "+longitude);

                // \n is for new line
                Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_LONG).show();

                items.add("Impact :" + (z2 - z1) + "\t Latitude :" + latitude + "\t Longitude :" + longitude);

            } else {
                // Can't get location.
                // GPS or network is not enabled.
                // Ask user to enable GPS/network in settings.
                gps.showSettingsAlert();
            }

            aa1.notifyDataSetChanged();

//            Log.e(TAG,"Anomaly Detected! - " + (z2 - z1));
//
//            Log.e(TAG,"List : " + items);

        }

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

}
