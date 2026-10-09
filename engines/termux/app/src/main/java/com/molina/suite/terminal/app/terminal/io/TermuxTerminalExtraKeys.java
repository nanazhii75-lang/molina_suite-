package com.molina.suite.terminal.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.molina.suite.terminal.app.terminal.TermuxTerminalSessionClient;
import com.molina.suite.terminal.app.terminal.TermuxTerminalViewClient;
import com.molina.suite.terminal.shared.terminal.io.TerminalExtraKeys;
import com.molina.suite.terminal.view.TerminalView;

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
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
