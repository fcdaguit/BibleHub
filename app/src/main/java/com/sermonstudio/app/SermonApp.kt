package com.sermonstudio.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sermonstudio.app.data.SampleDataSeeder
import com.sermonstudio.app.data.io.BackupScheduler
import com.sermonstudio.app.data.prefs.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SermonApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: SampleDataSeeder
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scope.launch { seeder.seedIfNeeded() }
        scope.launch {
            settings.settings.map { it.autoBackup }.distinctUntilChanged()
                .collect { BackupScheduler.apply(this@SermonApp, it) }
        }
    }
}
