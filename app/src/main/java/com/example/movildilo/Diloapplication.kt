package com.example.movildilo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.movildilo.ia.ZoeOnboardingManager


class DiloApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                ZoeOnboardingManager.onActivityReanudada(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                ZoeOnboardingManager.onActivityPausada(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}