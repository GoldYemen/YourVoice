package com.sotak.app

import android.app.Application

/**
 * Application entry point. Kept intentionally light so cold start stays fast --
 * a voice app should open almost instantly.
 */
class SotakApplication : Application()
