package com.electrons.myron.anomalycheck

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

import java.util.ArrayList
import java.util.HashMap

import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient

class MainActivity : AppCompatActivity(), SensorEventListener {

    internal lateinit var mFirebaseRemoteConfig: FirebaseRemoteConfig

    internal var threshold: Float = 0.toFloat()

    private var mSensorManager: SensorManager? = null
    private var mSensor: Sensor? = null

    private val dr = FirebaseFirestore.getInstance().document("Anomalies/AnomalyDetails")

    internal lateinit var x: TextView
    internal lateinit var y: TextView
    internal lateinit var z: TextView

    internal var h: Handler? = null

    internal lateinit var items: ArrayList<String>

    internal lateinit var l1: ListView

    internal lateinit var aa1: ArrayAdapter<String>


    internal var x1 = 0.0f
    internal var y1 = 0.0f
    internal var z1 = 0.0f

    internal var latitude: Double = 0.toDouble()
    internal var longitude: Double = 0.toDouble()

    private var mLocationRequest: LocationRequest? = null

    private val UPDATE_INTERVAL = (8 * 1000).toLong()  /* 10 secs */
    private val FASTEST_INTERVAL: Long = 4000 /* 2 sec */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build()
        mFirebaseRemoteConfig.setConfigSettings(configSettings)

        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults)

        // cache expiration in seconds
        var cacheExpiration: Long = 3600

        //expire the cache immediately for development mode.
        if (mFirebaseRemoteConfig.info.configSettings.isDeveloperModeEnabled) {
            cacheExpiration = 0
        }

        // fetch
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // task successful. Activate the fetched data
                        mFirebaseRemoteConfig.activateFetched()
                        Log.d(TAG, "Threshold Updated : $threshold")

                    } else {

                    }
                }

        threshold = java.lang.Float.parseFloat(mFirebaseRemoteConfig.getString("impact_threshold"))

        Log.d(TAG, "Threshold Value is : $threshold")

        startLocationUpdates()

        l1 = findViewById(R.id.anomalyEvent)

        x = findViewById(R.id.x_axis)
        y = findViewById(R.id.y_axis)
        z = findViewById(R.id.z_axis)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSensor = mSensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mSensorManager!!.registerListener(this as SensorEventListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL)

        items = ArrayList()

        aa1 = ArrayAdapter(this.applicationContext, android.R.layout.simple_list_item_1, items)

        l1.adapter = aa1

    }

    protected fun startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest = LocationRequest()
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest!!.interval = UPDATE_INTERVAL
        mLocationRequest!!.fastestInterval = FASTEST_INTERVAL

        // Create LocationSettingsRequest object using location request
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        val locationSettingsRequest = builder.build()

        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            checkPermissionsFine()

            checkPermissions()

            return
        }
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                // do work here
                onLocationChanged(locationResult!!.lastLocation)
            }
        },
                Looper.myLooper())
    }


    fun onLocationChanged(location: Location) {
        // New location has now been determined

        latitude = location.latitude
        longitude = location.longitude

        val msg = "Updated Location: " +
                java.lang.Double.toString(location.latitude) + "," +
                java.lang.Double.toString(location.longitude)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        //        You can now create a LatLng Object for use with maps
        //        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
    }

    override fun onSensorChanged(event: SensorEvent) {

        val x2 = event.values[0]
        val y2 = event.values[1]
        val z2 = event.values[2]

        if (z2 > z1 + threshold) {

            Log.e(TAG, "lat : $latitude")
            Log.e(TAG, "long : $longitude")

            // \n is for new line
            Toast.makeText(applicationContext, "Your Location is - \nLat: $latitude\nLong: $longitude", Toast.LENGTH_LONG).show()

            items.add("Impact :" + (z2 - z1) + "\nLatitude :" + latitude + "\n Longitude :" + longitude)

            val tsLong = System.currentTimeMillis() / 1000

            val dataToSave = HashMap<String, Any>()
            dataToSave[ANOMALY_IMPACT] = z2 - z1
            dataToSave[ANOMALY_LATITUDE] = latitude
            dataToSave[ANOMALY_LONGITUDE] = longitude
            dataToSave[ANOMALY_TIMESTAMP] = tsLong

            dr.collection("AnomalyList").add(dataToSave).addOnSuccessListener { documentReference ->
                Log.e(TAG, "Document Saved : " + documentReference.id)
                Log.e(TAG, "dataToSave : $dataToSave")
            }.addOnFailureListener { e -> Log.e(TAG, "Document Saved!", e) }


        } else {
            // Can't get location.
            // GPS or network is not enabled.
            // Ask user to enable GPS/network in settings.
            //gps.showSettingsAlert();
        }

        aa1.notifyDataSetChanged()

        //            Log.e(TAG,"Anomaly Detected! - " + (z2 - z1));
        //
        //            Log.e(TAG,"List : " + items);

        x.text = "X : $x2"
        y.text = "Y : $y2"
        z.text = "Z : $z2"

        x1 = x2
        y1 = y2
        z1 = z2

    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {

    }

    private fun checkPermissionsFine(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            requestPermissionsFine()
            return false
        }
    }

    private fun requestPermissionsFine() {
        ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION)
    }

    private fun checkPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            requestPermissions()
            return false
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION)
    }

    companion object {

        private val TAG = "AnomalyCheck"
        private val ANOMALY_IMPACT = "Impact"
        private val ANOMALY_LATITUDE = "Latitude"
        private val ANOMALY_LONGITUDE = "Longitude"
        private val ANOMALY_TIMESTAMP = "TimeStamp"
        private val REQUEST_FINE_LOCATION = 1
    }

}
