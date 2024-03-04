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

import androidx.annotation.UiThread
import androidx.collection.CircularArray

@UiThread
class VolumeHistory internal constructor(private val maxHistory: Int) {
    // See: https://developer.android.com/reference/android/media/audiofx/Visualizer#MEASUREMENT_MODE_PEAK_RMS
    // (And: https://en.wikipedia.org/wiki/DBFS)
    //    0mB means maximum loudness
    // -600mB means 50% loudness
    private val minRms = -7500 // -9600
    private var maxVolume = -6500 - minRms
    var volumeNorm = 1.0 / maxVolume
        private set
    private val historyData: CircularArray<Int> = CircularArray(maxHistory)

    operator fun get(i: Int): Int {
        return historyData[i]
    }

    fun size(): Int {
        return historyData.size()
    }

    fun addLast(rms: Int) {
        val volume = rms.coerceIn(minRms, 0) - minRms
        if (volume > this.maxVolume) {
            this.maxVolume = volume
            this.volumeNorm = 1.0 / volume
        }
        historyData.addLast(volume)
        historyData.removeFromStart(historyData.size() - maxHistory)
    }
}
