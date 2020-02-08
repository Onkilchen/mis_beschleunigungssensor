package com.example.beschleunigungssensor

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    class LocationSchnueffeler(activity: MainActivity) : LocationListener {
        val activity = activity
        override fun onLocationChanged(location: Location) {
            activity.updateSpeed(location.speed /* m/s */ * 3.6f /* km/h */)
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private var locationSchnueffeler : LocationSchnueffeler? = null

    class AchselSchnueffeler(activity: MainActivity) : SensorEventListener {
        val activity = activity
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val xyz = event.values
            val t = sqrt((xyz[0] * xyz[0] + xyz[1] * xyz[1] + xyz[2] * xyz[2]).toDouble()).toFloat()
            activity.updateAccel(xyz[0], xyz[1], xyz[2], t)
        }
    }

    private var achselSchnueffeler : AchselSchnueffeler? = null

    class DruckSchnueffeler(activity: MainActivity) : SensorEventListener {
        val activity = activity
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val millibarsOfPressure = event.values[0]
            activity.updatePressure(millibarsOfPressure)
        }
    }

    private var druckSchnueffeler : DruckSchnueffeler? = null
    private var spietBuffer : Float = 0.0f
    private var achselBuffer : String? = null

    private val buttons = arrayOf(R.id.capture)

    var slug : EditText? = null
    var pressureValueTextView : TextView? = null
    var speedValueTextView : TextView? = null
    var seekBar : SeekBar? = null
    var captureTime : SeekBar? = null
    var xValueTextView : TextView? = null
    var yValueTextView : TextView? = null
    var zValueTextView : TextView? = null
    var gesamtWertTextView : TextView? = null


    var locationManager : LocationManager? = null
    var sensorManager: SensorManager? = null
    var context : Context? = null

    var progressBarPosX : ProgressBar? = null
    var progressBarPosY : ProgressBar? = null
    var progressBarPosZ : ProgressBar? = null
    var progressBarPosGesamt : ProgressBar? = null

    var progressBarPosXNegative : ProgressBar? = null
    var progressBarPosYNegative : ProgressBar? = null
    var progressBarPosZNegative : ProgressBar? = null

    var werteAufnehmen : Boolean = false

    // Liste zum speichern der Werte
    val werteListe : LinkedList<String> = LinkedList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // get instance of default acceleration sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        context = applicationContext

        slug = findViewById(R.id.slug)
        slug!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable?) {
                val x = slug!!.text.toString()
                val pos = slug!!.selectionStart
                val y = stripChars(x)
                if (x.equals(y))
                    return

                slug!!.setText(y)
                slug!!.setSelection(minOf(pos-1, y.length))

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        // fill textviews
        pressureValueTextView = findViewById(R.id.pressureValueTextView)
        speedValueTextView = findViewById(R.id.speedValueTextView)
        seekBar = findViewById(R.id.seekBar)
        seekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                findViewById<TextView>(R.id.desiredSpeed).text = range(i)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        findViewById<TextView>(R.id.desiredSpeed).text = range()
        captureTime = findViewById(R.id.captureTime)
        captureTime!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                findViewById<TextView>(R.id.captureTimeView).text = timeRange(i).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        findViewById<TextView>(R.id.captureTimeView).text = timeRange().toString()
        xValueTextView = findViewById(R.id.xValueTextView)
        yValueTextView = findViewById(R.id.yValueTextView)
        zValueTextView = findViewById(R.id.zValueTextView)

        // positive progressbar area
        progressBarPosX = findViewById(R.id.progressBarPosX)
        progressBarPosY = findViewById(R.id.progressBarPosY)
        progressBarPosZ = findViewById(R.id.progressBarPosZ)
        progressBarPosGesamt = findViewById(R.id.progressBarPosGesamt)

        // negative progressbar area
        progressBarPosXNegative = findViewById(R.id.progressBarPosXNegative)
        progressBarPosYNegative = findViewById(R.id.progressBarPosYNegative)
        progressBarPosZNegative = findViewById(R.id.progressBarPosZNegative)

        // Gesamtwertbereich
        gesamtWertTextView = findViewById(R.id.gesamtWertTextView)

        (findViewById<Button>(R.id.capture)).setOnClickListener {
            object : CountDownTimer(timeRange().toLong() * 1000, 1000) {
                var orig : String? = null
                override fun onTick(millisUntilFinished: Long) {
                    val button = (findViewById<Button>(R.id.capture))

                    if (orig == null) {
                        orig = button.text.toString()
                    }

                    val seconds = (millisUntilFinished / 1000).toInt().toString()

                    button.setText("...$seconds...")
                    startCapture()
                }

                override fun onFinish() {
                    stopCapture()
                    val button = (findViewById<Button>(R.id.capture))

                    button.setText(orig)
                    orig = null
                }
            }.start()
        }

    }

    fun stripChars(x : String = ""): String {
        var result : String = ""
        for (c in x) {
            if (c in 'a' .. 'z') {
                result += c
            }
        }
        return result
    }

    fun progressToSimpleKmh(p : Int): Int {
        if (p < 1) {
            return 5
        }

        return p * 10
    }

    fun withinRange(x: Float): Boolean {
        val kmh = progressToSimpleKmh(seekBar!!.progress)
        val offset = kmh * 0.1
        val min = kmh - offset
        val max = kmh + offset
        return x in min .. max
    }

    fun range(i: Int): String {
        val kmh = progressToSimpleKmh(i)
        val offset = kmh * 0.1
        val min = kmh - offset
        val max = kmh + offset
        return "%.0f-%.0f".format(min, max)
    }

    fun range(): String {
        return range(seekBar!!.progress)
    }

    fun timeRange(i: Int): Int {
        if (i < 3) {
            return (i+1)*15
        }

        return ((i-2)*60)
    }

    fun timeRange(): Int {
        return timeRange(captureTime!!.progress)
    }

    fun disableButtons() {
        for (id in buttons) {
            findViewById<Button>(id).isEnabled = false
        }
    }

    fun enableButtons() {
        for (id in buttons) {
            findViewById<Button>(id).isEnabled = true
        }
    }

    fun startCapture() {
        if (werteAufnehmen)
            return

        werteAufnehmen = true
        disableButtons()
    }

    fun stopCapture() {
        if (!werteAufnehmen)
            return

        werteAufnehmen = false
        saveAsCsv()
        Toast.makeText(context, "Werte gespeichert", Toast.LENGTH_LONG).show()
        enableButtons()
    }


    // start the sensor again when re-entering the app
    override fun onResume() {
        super.onResume()

        if (locationSchnueffeler == null) {
            locationSchnueffeler = LocationSchnueffeler(this)

        }
        try {
            locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationSchnueffeler)
        } catch(ex: SecurityException) {
            Log.e("onCreate", "Permission to location denied", ex)
        }

        if (achselSchnueffeler == null) {
            achselSchnueffeler = AchselSchnueffeler(this)
        }

        val achselSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (achselSensor != null) {
            sensorManager?.registerListener(
                achselSchnueffeler,
                achselSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        if (druckSchnueffeler == null) {
            druckSchnueffeler = DruckSchnueffeler(this)
        }

        val druckSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (druckSensor != null) {
            sensorManager?.registerListener(
                druckSchnueffeler,
                druckSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        if (werteAufnehmen) {
            disableButtons()
        } else {
            enableButtons()
        }
    }

    // to make sure that the sensor stops after closing the app
    override fun onPause() {
        super.onPause()
        if (locationSchnueffeler != null) {
            locationManager?.removeUpdates(locationSchnueffeler)
        }

        if (achselSchnueffeler != null) {
            sensorManager?.unregisterListener(achselSchnueffeler)
        }

        if (druckSchnueffeler != null) {
            sensorManager?.unregisterListener(druckSchnueffeler)
        }
    }

    fun updateSpeed(speed: Float) {
        spietBuffer = speed
        speedValueTextView?.text = "%.0f km/h".format(speed)

        if (speed < 2.0) {
            speedValueTextView?.setBackgroundColor(Color.WHITE)
            return
        }

        if (withinRange(speed)) {
            speedValueTextView?.setBackgroundColor(Color.GREEN)
        } else {
            speedValueTextView?.setBackgroundColor(Color.RED)
        }
    }

    fun updateAccel(x: Float, y: Float, z: Float, t: Float) {
        xValueTextView?.setText("%+.4f".format(x))
        yValueTextView?.setText("%+.4f".format(y))
        zValueTextView?.setText("%+.4f".format(z))
        gesamtWertTextView?.setText("%+.4f".format(t))

        if (x >= 0) {
            progressBarPosX!!.progress = (x * 100 / 15).toInt()
            progressBarPosXNegative!!.progress = 0
        } else {
            progressBarPosX!!.progress = 0
            progressBarPosXNegative!!.progress = (x * -100 / 15).toInt()
        }

        if (y >= 0) {
            progressBarPosY!!.progress = (y * 100 / 15).toInt()
            progressBarPosYNegative!!.progress = 0
        } else {
            progressBarPosY!!.progress = 0
            progressBarPosYNegative!!.progress = (y * -100 / 15).toInt()
        }

        if (z >= 0) {
            progressBarPosZ!!.progress = (z * 100 / 15).toInt()
            progressBarPosZNegative!!.progress = 0
        } else {
            progressBarPosZ!!.progress = 0
            progressBarPosZNegative!!.progress = (y * -100 / 15).toInt()
        }

        if (t >= 0) {
            progressBarPosGesamt!!.progress = (t * 100 / 15).toInt()
        } else {
            progressBarPosGesamt!!.progress = 0

        }

        achselBuffer = "$x;$y;$z;$t"
    }

    fun updatePressure(p: Float) {
        if (werteAufnehmen) {
            if (achselBuffer != null) {
                werteListe?.add("$achselBuffer;$spietBuffer;$p\n")
                achselBuffer = null
            }
        }

        pressureValueTextView?.setText("%+.4f".format(p))
    }

    fun makeFilename(): String {
        val slug = if (slug!!.text.isNotEmpty()) slug!!.text else "uncategorized"
        val speed = progressToSimpleKmh(seekBar!!.progress)
        val fileName : String = SimpleDateFormat("YYYY-MM-dd-HH-mm-ss'-SensorData-$slug-$speed.csv'").format(Date())
        return fileName
    }

    fun saveAsCsv() {
        val fileName = makeFilename()
        Log.d("saveAsCsv", fileName)

        val file = File(context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        var fileOutPutStream : FileOutputStream

        try {
            fileOutPutStream = FileOutputStream(file)
            fileOutPutStream.write(("x;y;z;t (m/s^2);v (km/h);p (mbar)\n").toByteArray())

            val s = werteListe!!.size

            for (i in 0 until s step 1) {
                fileOutPutStream.write(werteListe!![i].toByteArray())
            }
            Log.d("saveAsCsv", "wrote $s rows")
            werteListe!!.clear()
            fileOutPutStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}