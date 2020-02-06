package com.example.beschleunigungssensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

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
    private var buffer : String? = null

    var pressureValueTextView : TextView? = null
    var xValueTextView : TextView? = null
    var yValueTextView : TextView? = null
    var zValueTextView : TextView? = null
    var gesamtWertTextView : TextView? = null


    var sensorManager: SensorManager? = null
    var sensor: Sensor? = null
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
    var werteListe : LinkedList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get instance of default acceleration sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        context = applicationContext

        werteListe = LinkedList()


        // fill textviews
        pressureValueTextView = findViewById(R.id.pressureValueTextView)
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

        (findViewById<Button>(R.id.startButton)).setOnClickListener {
            Log.d("OnClick", "starting OnClickListener")
            object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    Log.d("countdown", "start countdown")
                    if (!werteAufnehmen) {
                        werteAufnehmen = true
                    }
                }

                override fun onFinish() {
                    werteAufnehmen = false
                    saveAsCsv()
                    Log.d("filesaver", "file saved")
                    Toast.makeText(context, "Werte gespeichert", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
    }

    // start the sensor again when re-entering the app
    override fun onResume() {
        super.onResume()

        if (achselSchnueffeler == null) {
            achselSchnueffeler = AchselSchnueffeler(this)
        }
        sensorManager?.registerListener(
            achselSchnueffeler,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (druckSchnueffeler == null) {
            druckSchnueffeler = DruckSchnueffeler(this)
        }

        sensorManager?.registerListener(
            druckSchnueffeler,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_PRESSURE),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    // to make sure that the sensor stops after closing the app
    override fun onPause() {
        super.onPause()
        if (achselSchnueffeler != null) {
            sensorManager?.unregisterListener(achselSchnueffeler)
        }

        if (druckSchnueffeler != null) {
            sensorManager?.unregisterListener(druckSchnueffeler)
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

        buffer = "$x;$y;$z;$t"
    }

    fun updatePressure(p: Float) {
        if (werteAufnehmen) {
            if (buffer != null) {
                werteListe?.add("$buffer;$p\n")
                buffer = null
            }
        }

        pressureValueTextView?.setText("%+.4f".format(p))
    }

    fun saveAsCsv() {
        var fileName : String = SimpleDateFormat("YYYY-MM-dd-HH-mm-ss'-SensorData.csv'").format(Date())
        var file = File(context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        var fileOutPutStream : FileOutputStream

        try {
            fileOutPutStream = FileOutputStream(file)
            fileOutPutStream.write(("x;y;z;t;p\n").toByteArray())
            Log.d("directory", "" + Environment.DIRECTORY_DOWNLOADS)

            for (i in 0 until werteListe!!.size step 1) {
                fileOutPutStream.write(werteListe!![i].toByteArray())
            }
            Log.d("savecsv", "I am here")
            werteListe!!.clear()
            fileOutPutStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}