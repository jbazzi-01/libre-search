package org.libre.search

import android.app.Application
import org.libre.search.browser.AdBlocker
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import org.libre.search.data.LibraryStore
import org.libre.search.player.NpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class LibreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "Libre Search ${BuildConfig.VERSION_NAME}\n" +
                        "Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})\n" +
                        "Thread: ${t.name}\n\n" + e.stackTraceToString().take(12000)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
        Prefs.init(this)
        Http.init(this)
        LibraryStore.init(this)
        AdBlocker.init(this)
        try {
            NewPipe.init(NpDownloader, Localization("en", "GB"), ContentCountry("FR"))
        } catch (_: Throwable) {
        }
        val osm = org.osmdroid.config.Configuration.getInstance()
        osm.userAgentValue = Http.APP_UA
        osm.osmdroidBasePath = filesDir
        osm.osmdroidTileCache = java.io.File(cacheDir, "tiles")
    }

    companion object {
        lateinit var instance: LibreApp
            private set
    }
}
