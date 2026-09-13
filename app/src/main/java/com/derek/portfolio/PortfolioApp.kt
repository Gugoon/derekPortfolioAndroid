package com.derek.portfolio

import android.app.Application
import com.derek.portfolio.data.visit.VisitLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PortfolioApp : Application() {

    @Inject
    lateinit var visitLogger: VisitLogger

    override fun onCreate() {
        super.onCreate()
        // 방문 기록은 화면과 상관없이 조용히 남긴다. IP 조회가 끼어 있어 몇백 ms 가
        // 걸릴 수 있는데, 그걸 기다리느라 첫 화면이 늦어질 이유는 없다.
        if (FirebaseConfig.isConfigured) visitLogger.recordInBackground()
    }
}
