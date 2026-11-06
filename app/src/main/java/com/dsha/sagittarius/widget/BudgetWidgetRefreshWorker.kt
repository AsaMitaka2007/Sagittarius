package com.dsha.sagittarius.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BudgetWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            WidgetUpdateManager.updateAll(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "budget_widget_periodic_refresh_30m"
    }
}
