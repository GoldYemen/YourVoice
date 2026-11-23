package com.sotak.app

import android.app.Application
import com.sotak.app.call.CallCoordinator

/**
 * Application entry point. Kept intentionally light so cold start stays fast --
 * a voice app should open almost instantly.
 *
 * It owns the single [CallCoordinator], which both the UI and the foreground
 * service share so a call outlives the activity.
 */
class SotakApplication : Application() {

    val coordinator: CallCoordinator by lazy { CallCoordinator(this) }
}
