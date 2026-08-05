package com.example.movildilo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.movildilo.ia.ZoeOnboardingManager

/**
 * Application propia de Dilo Móvil.
 *
 * Su único propósito extra (además de lo que ya hace Application por defecto) es registrar un
 * ActivityLifecycleCallbacks que le permite a la guía de bienvenida de Zoe ([ZoeOnboardingManager])
 * mostrar su burbuja flotante en CUALQUIER pantalla de la app sin tener que tocar el código de
 * cada Activity una por una: cuando la guía está activa y una Activity se reanuda, aparece la
 * burbuja con la explicación de esa pantalla; cuando se pausa, se retira.
 */
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