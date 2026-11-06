package com.dsha.sagittarius

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.weather.WeatherRepository
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.agent.data.ApiKeyStore
import com.dsha.sagittarius.agent.debug.SagittariusLogger
import com.dsha.sagittarius.agent.data.SagittariusPrefs
import com.dsha.sagittarius.widget.BudgetWidgetRefreshWorker
import com.dsha.sagittarius.widget.WidgetUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SagittariusApp : Application() {

    lateinit var repository: BudgetRepository
        private set

    lateinit var weatherRepository: WeatherRepository
        private set

    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var sagittariusPrefs: SagittariusPrefs
        private set

    lateinit var dietRepository: DietRepository
        private set

    lateinit var chatHistoryDao: com.dsha.sagittarius.data.ChatHistoryDao
        private set

    lateinit var chatHistoryDeleteDao: com.dsha.sagittarius.data.ChatHistoryDeleteDao
        private set

    lateinit var chatSessionDao: com.dsha.sagittarius.data.ChatSessionDao
        private set

    lateinit var cnFoodRepository: com.dsha.sagittarius.data.CnFoodRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ThemeManager.init(this)
        com.dsha.sagittarius.ui.ButtonAccentManager.init(this)
        com.dsha.sagittarius.ui.DateConsoleStyleManager.init(this)
        com.dsha.sagittarius.ui.ChartLockManager.init(this)
        com.dsha.sagittarius.ui.AppModeManager.init(this)
        com.dsha.sagittarius.ui.DateConsoleImageManager.init(this)
        repository = BudgetRepository.getInstance(this)
        weatherRepository = WeatherRepository.getInstance(this)
        apiKeyStore = ApiKeyStore(this)
        sagittariusPrefs = SagittariusPrefs(this)
        SagittariusLogger.init(this)
        dietRepository = DietRepository.getInstance(this)
        chatHistoryDao = com.dsha.sagittarius.data.AppDatabase.get(this).chatHistoryDao()
        chatHistoryDeleteDao = com.dsha.sagittarius.data.AppDatabase.get(this).chatHistoryDeleteDao()
        chatSessionDao = com.dsha.sagittarius.data.AppDatabase.get(this).chatSessionDao()
        cnFoodRepository = com.dsha.sagittarius.data.CnFoodRepository.get(this)

        appScope.launch {
            runCatching {
                cnFoodRepository.ensureSeeded()
                repository.ensureInitialState()
                WidgetUpdateManager.updateAll(this@SagittariusApp)
            }
        }

        val work = PeriodicWorkRequestBuilder<BudgetWidgetRefreshWorker>(
            30, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BudgetWidgetRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
