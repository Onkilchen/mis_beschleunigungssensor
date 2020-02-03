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
import android.view.View
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

class MainActivity : AppCompatActivity(), SensorEventListener {

    var xValueTextView : TextView? = null
    var yValueTextView : TextView? = null
    var zValueTextView : TextView? = null
    var gesamtWertTextView : TextView? = null


    var sensorManager: SensorManager? = null
    var sensor: Sensor? = null
    var context : Context? = null

    lateinit var startButton : Button

    var beschleunigungsDaten : FloatArray = FloatArray(3)

    var progressBarPosX : ProgressBar? = null
    var progressBarPosY : ProgressBar? = null
    var progressBarPosZ : ProgressBar? = null
    var progressBarPosGesamt : ProgressBar? = null

    var progressBarPosXNegative : ProgressBar? = null
    var progressBarPosYNegative : ProgressBar? = null
    var progressBarPosZNegative : ProgressBar? = null
    var progressBarPosGesamtNegativ : ProgressBar? = null

    var dauer : Int = 0
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
        progressBarPosGesamtNegativ = findViewById(R.id.progressBarPosGesamtNegativ)

        // Gesamtwertbereich
        gesamtWertTextView = findViewById(R.id.gesamtWertTextView)

        // Dauer der Aufnahme
        dauer = Toast.LENGTH_LONG

        // Button zum Starten der Wertemessungsaufnahme
        startButton = findViewById(R.id.startButton)
        startButton.setOnClickListener {
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
                    var toast: Toast = Toast.makeText(context, "Werte gespeichert", dauer)
                    Log.d("filesaver", "file saved")
                    toast.show()
                }
            }.start()
        }
    }


    // start the sensor again when re-entering the app
    override fun onResume() {
        super.onResume()
        sensorManager?.registerListener(
            this,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    // to make sure that the sensor stops after closing the app
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {

        System.arraycopy(event!!.values, 0, beschleunigungsDaten, 0, 3)

        var gesamtWert: Float = sqrt((beschleunigungsDaten[0] * beschleunigungsDaten[0] + beschleunigungsDaten[1] * beschleunigungsDaten[1] + beschleunigungsDaten[2] * beschleunigungsDaten[2]).toDouble())
            .toFloat()

        if (werteAufnehmen) {
            werteListe?.add("" + beschleunigungsDaten[0] + ";" + beschleunigungsDaten[1] + ";" + beschleunigungsDaten[2] + ";" + gesamtWert + "\n")
        }

        xValueTextView?.setText("" + beschleunigungsDaten[0])
        yValueTextView?.setText("" + beschleunigungsDaten[1])
        zValueTextView?.setText("" + beschleunigungsDaten[2])
        gesamtWertTextView?.setText("" + gesamtWert)

        if (beschleunigungsDaten[0] >= 0) {
            progressBarPosX!!.progress = (beschleunigungsDaten[0] * 100 / 15).toInt()
        } else {
            progressBarPosXNegative!!.progress = (beschleunigungsDaten[0] * -100 / 15).toInt()
        }

        if (beschleunigungsDaten[1] >= 0) {
            progressBarPosY!!.progress = (beschleunigungsDaten[1] * 100 / 15).toInt()
        } else {
            progressBarPosYNegative!!.progress = (beschleunigungsDaten[1] * -100 / 15).toInt()
        }

        if (beschleunigungsDaten[2] >= 0) {
            progressBarPosZ!!.progress = (beschleunigungsDaten[2] * 100 / 15).toInt()
        } else {
            progressBarPosZNegative!!.progress = (beschleunigungsDaten[2] * -100 / 15).toInt()
        }

        if (gesamtWert >= 0) {
            progressBarPosGesamt!!.progress = (gesamtWert * 100 / 15).toInt()
        } else {
            progressBarPosGesamtNegativ!!.progress = (gesamtWert * -100 / 15).toInt()
        }

        // Log.d("values", "x: " + event!!.values[0] + " y: " + event!!.values[1] + " z: " + event!!.values[2])
    }

    fun saveAsCsv() {
        var fileName : String = SimpleDateFormat("YYYY-MM-dd-HH-mm-ss'-SensorData.csv'").format(Date())
        var file = File(context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        var fileOutPutStream : FileOutputStream

        try {
            fileOutPutStream = FileOutputStream(file)
            fileOutPutStream.write(("X-Achse, Y-Achse, Z-Achse, Gesamt\n").toByteArray())
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