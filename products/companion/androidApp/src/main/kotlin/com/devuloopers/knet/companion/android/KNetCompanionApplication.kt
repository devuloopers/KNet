package com.devuloopers.knet.companion.android

import android.app.Application

/** Android companion process root that owns the product graph for the process lifetime. */
class KNetCompanionApplication : Application() {
    private val graphDelegate: Lazy<AndroidCompanionProductGraph> = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidCompanionProductGraph.create(this)
    }

    internal val graph: AndroidCompanionProductGraph
        get() = graphDelegate.value

    /** Releases owned callbacks during emulator/test process teardown when Android invokes this callback. */
    override fun onTerminate() {
        if (graphDelegate.isInitialized()) graphDelegate.value.close()
        super.onTerminate()
    }
}
