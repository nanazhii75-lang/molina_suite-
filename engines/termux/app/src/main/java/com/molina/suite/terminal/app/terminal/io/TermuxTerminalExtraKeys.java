package com.molina.suite.terminal.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.molina.suite.terminal.R;
import com.molina.suite.terminal.app.terminal.TermuxTerminalSessionClient;
import com.molina.suite.terminal.app.terminal.TermuxTerminalViewClient;
import com.molina.suite.terminal.shared.data.DataUtils;
import com.molina.suite.terminal.shared.interact.ShareUtils;
import com.molina.suite.terminal.shared.terminal.io.TerminalExtraKeys;
import com.molina.suite.terminal.view.TerminalView;

import java.nio.charset.StandardCharsets;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {


    TermuxTerminalViewClient mTermuxTerminalViewClient;
    TermuxTerminalSessionClient mTermuxTerminalSessionClient;

    public TermuxTerminalExtraKeys(@NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionClient termuxTerminalSessionClient) {
        super(terminalView);
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionClient = termuxTerminalSessionClient;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getHost().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionClient != null)
                mTermuxTerminalSessionClient.onPasteTextFromClipboard(null);
        } else if ("COPY".equals(key)) {
            copyFullTranscript(view);
        } else if ("CLEAR".equals(key)) {
            clearScreen();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

    /** Menyalin seluruh isi terminal (riwayat scroll dan layar), termasuk baris prompt. */
    private void copyFullTranscript(View view) {
        if (mTermuxTerminalViewClient == null) return;
        TerminalView terminalView = mTermuxTerminalViewClient.getHost().getTerminalView();
        if (terminalView == null) return;
        Context context = view.getContext();
        // Panjang teks dibatasi agar clipboard tidak kena TransactionTooLargeException. Bila terlalu
        // panjang, yang dibuang baris terlama sehingga prompt terbaru tetap ikut tersalin.
        String text = DataUtils.getTruncatedCommandOutput(terminalView.getFullTranscriptText(),
            DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES - 1, false, true, false);
        if (text.isEmpty()) {
            Toast.makeText(context, R.string.molina_extra_key_screen_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ShareUtils.copyTextToClipboard(context, text, context.getString(R.string.molina_extra_key_screen_copied));
    }

    /**
     * Mengosongkan layar terminal beserta riwayat scroll. CSI 3 J dikirim langsung ke emulator
     * karena Ctrl+L di bash hanya membersihkan layar yang tampak; setelah itu Ctrl+L membuat
     * shell membersihkan layar dan menggambar ulang prompt.
     */
    private void clearScreen() {
        if (mTermuxTerminalViewClient == null) return;
        TerminalView terminalView = mTermuxTerminalViewClient.getHost().getTerminalView();
        if (terminalView == null) return;
        if (terminalView.mEmulator != null) {
            byte[] eraseScrollback = "\033[3J".getBytes(StandardCharsets.UTF_8);
            terminalView.mEmulator.append(eraseScrollback, eraseScrollback.length);
            terminalView.onScreenUpdated();
        }
        terminalView.inputCodePoint('l', true, false);
    }

}
