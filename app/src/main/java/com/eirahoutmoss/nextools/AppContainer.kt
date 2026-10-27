package com.eirahoutmoss.nextools

import android.content.Context
import com.eirahoutmoss.nextools.barolog.PressureLog
import com.eirahoutmoss.nextools.export.Exporter
import com.eirahoutmoss.nextools.hardware.HardwareProbe
import com.eirahoutmoss.nextools.records.RecordStore
import com.eirahoutmoss.nextools.settings.CalibrationStore
import com.eirahoutmoss.nextools.settings.SettingsStore
import com.eirahoutmoss.nextools.update.UpdateManager

/**
 * Elle kurulan bağımlılık kabı (NexYapı ile aynı yaklaşım): DI kütüphanesi yok,
 * daha az sihir, hatada okunur yığın izi.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val app get() = appContext

    val settings: SettingsStore by lazy { SettingsStore(app) }
    val calibrations: CalibrationStore by lazy { CalibrationStore(app) }
    val hardware: HardwareProbe by lazy { HardwareProbe(app) }
    val records: RecordStore by lazy { RecordStore(app) }
    val exporter: Exporter by lazy { Exporter(app, records) }
    val updateManager: UpdateManager by lazy { UpdateManager(app) }
    val pressureLog: PressureLog by lazy { PressureLog(app) }
}
