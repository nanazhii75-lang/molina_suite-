package com.molina.suite.terminal.app;

import android.app.Application;
import android.content.Context;

import com.molina.suite.terminal.shared.crash.TermuxCrashUtils;
import com.molina.suite.terminal.shared.logger.Logger;
import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;

/**
 * molina-suite: engine ini dibangun sebagai library, sehingga {@link Application} milik
 * host memanggil {@link #initialize(Application)} (lihat TerminalEngineModule).
 * Kelas ini tetap dapat dipakai langsung sebagai Application jika engine dijalankan mandiri.
 */
public class TermuxApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initialize(this);
    }

    /** Pasang crash handler dan terapkan log level dari preferensi. */
    public static void initialize(Application application) {
        // Set crash handler for the app
        TermuxCrashUtils.setCrashHandler(application);

        // Set log level for the app
        setLogLevel(application.getApplicationContext());
    }

    private static void setLogLevel(Context context) {
        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
        Logger.logDebug("Starting Application");
    }
}
