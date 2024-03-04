/*
 * This file is part of Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package de.rochefort.childmonitor

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.rochefort.childmonitor.ListenService.ListenBinder

class ListenActivity : Activity() {
    // Don't attempt to unbind from the service unless the client has received some
    // information about the service's state.
    private var shouldUnbind = false
    private lateinit var volumeView: VolumeView
    private var visualizer: Visualizer? = null
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            val bs = (service as ListenBinder).service
            Toast.makeText(this@ListenActivity, R.string.connect,
                    Toast.LENGTH_SHORT).show()
            val connectedText = findViewById<TextView>(R.id.connectedTo)
            connectedText.text = bs.childDeviceName
            bs.onAudioSessionId = { audioSessionId ->
                this@ListenActivity.visualizer = Visualizer(audioSessionId).also {
                    it.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                    val minCaptureSize = Visualizer.getCaptureSizeRange()[0]
                    it.setCaptureSize(minCaptureSize)
                    val listener = object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            visualizer?.let {
                                val measurement = Visualizer.MeasurementPeakRms()
                                val ret = visualizer.getMeasurementPeakRms(measurement)
                                if (Visualizer.SUCCESS == ret) {
                                    volumeView.postValue(measurement.mRms)
                                }
                            }
                        }
                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {}
                    }
                    val rateInMilliHz = Visualizer.getMaxCaptureRate().
                        coerceAtMost(20_000) // 20Hz
                    it.setDataCaptureListener(listener,
                        rateInMilliHz,
                        true,
                        false)
                    it.enabled = true
                }
            }
            bs.onError = { postErrorMessage() }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            Toast.makeText(this@ListenActivity, R.string.disconnected,
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureServiceRunningAndBind(bundle: Bundle?) {
        val context: Context = this
        val intent = Intent(context, ListenService::class.java)
        bundle?.let {
            intent.putExtra("name", it.getString("name"))
            intent.putExtra("address", it.getString("address"))
            intent.putExtra("port", it.getInt("port"))
            ContextCompat.startForegroundService(context, intent)
        }
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        if (bindService(intent, connection, BIND_AUTO_CREATE)) {
            this.shouldUnbind = true
            Log.i(TAG, "Bound listen service")
        } else {
            Log.e(TAG, "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.")
        }
    }

    private fun doUnbindAndStopService() {
        if (this.shouldUnbind) {
            // Release information about the service's state.
            unbindService(connection)
            this.shouldUnbind = false
        }
        val context: Context = this
        val intent = Intent(context, ListenService::class.java)
        context.stopService(intent)
    }

    fun postErrorMessage() {
        val status = findViewById<TextView>(R.id.textStatus)
        status.post { status.setText(R.string.disconnected) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listen)
        this.volumeView = findViewById<VolumeView>(R.id.volume)
        ensureServiceRunningAndBind(this.intent.extras)
        this.volumeControlStream = AudioManager.STREAM_MUSIC
        val statusText = findViewById<TextView>(R.id.textStatus)
        statusText.setText(R.string.listening)
    }

    public override fun onDestroy() {
        doUnbindAndStopService()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ListenActivity"
    }
}
