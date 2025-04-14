package me.thuanc177.kotsune.viewmodel

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Helper class to provide application context to ViewModels
 * This avoids the need to access KotsuneApplication.instance directly
 */
object ViewModelContextProvider {
    private var weakContext: WeakReference<Context>? = null

    /**
     * The application context, or null if not set
     */
    val context: Context?
        get() = weakContext?.get()

    /**
     * Set the application context
     * This should be called in the main activity's onCreate method
     */
    fun setContext(context: Context) {
        weakContext = WeakReference(context.applicationContext)
    }
}