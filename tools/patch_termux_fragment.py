#!/usr/bin/env python3
"""Tahap 3a: TermuxFragment + fragment_termux.xml (belum disambungkan ke shell).
Dry-run default; --apply untuk menulis. Idempoten. Semua berkas ditulis atau tidak sama sekali."""
import difflib
import os
import re
import sys

APPLY = '--apply' in sys.argv
ROOT = 'engines/termux'
MAIN = ROOT + '/app/src/main'
E = MAIN + '/java/com/molina/suite/terminal/app'


class Gagal(Exception):
    pass


def read(p):
    if not os.path.isfile(p):
        raise Gagal('berkas tidak ada: ' + p)
    with open(p, encoding='utf-8', newline='') as f:
        return f.read()


def eol_of(t):
    return '\r\n' if '\r\n' in t else '\n'


def sub_n(text, pattern, repl, n, label):
    new, c = re.subn(pattern, repl, text, flags=re.M)
    if c != n:
        raise Gagal('%s: ditemukan %d, seharusnya %d' % (label, c, n))
    return new


# ---------------------------------------------------------------- patch kecil

def p_host(t):
    if 'T findViewById(int id);' in t:
        return t
    eol = eol_of(t)
    return sub_n(
        t, r'^([ \t]*)int getNavBarHeight\(\);',
        lambda m: (m.group(0) + eol + eol + m.group(1) +
                   '/** View milik tempat terminal ditampilkan (Activity atau fragment); null bila belum ada. */' + eol +
                   m.group(1) + '<T extends View> T findViewById(int id);'),
        1, 'TermuxHost')


def p_session(t):
    if 'mHost.findViewById(R.id.terminal_sessions_list)' in t:
        return t
    return sub_n(t, r'mActivity\.findViewById\(R\.id\.terminal_sessions_list\)',
                 'mHost.findViewById(R.id.terminal_sessions_list)', 1, 'SessionClient findViewById')


def p_view(t):
    if 'mHost.findViewById(R.id.terminal_toolbar_text_input)' in t:
        return t
    return sub_n(t, r'mActivity\.findViewById\(R\.id\.terminal_toolbar_text_input\)',
                 'mHost.findViewById(R.id.terminal_toolbar_text_input)', 1, 'ViewClient findViewById')


CHANGES_BLOCK = """
## Tahap 3a: TermuxFragment (belum disambungkan ke shell)
- Ditambahkan: TermuxFragment.java dan res/layout/fragment_termux.xml (salinan activity_termux.xml tanpa fitsSystemWindows). TermuxActivity tidak diubah dan tetap berfungsi.
- Fragment tidak mengimplementasikan TermuxHost langsung (Fragment.isVisible() final); TermuxHost diberikan lewat kelas dalam FragmentHost.
- TermuxHost: tambah findViewById(int) (metode ke-23). TermuxTerminalSessionClient dan TermuxTerminalViewClient memakai mHost.findViewById untuk view milik fragment.
- Visibilitas dikendalikan lewat onResume, onStop, dan onHiddenChanged (shell memakai add/hide). Saat disembunyikan: flag disable-keyboard dihapus, softInputMode window dikembalikan, keyboard disembunyikan.
- Sesi terakhir selesai: tidak menutup apa pun; sesi baru dibuat otomatis kecuali service sedang berhenti.
- Belum dibawa: mode fullscreen, shortcut ACTION_RUN, tombol menu hardware, penerusan onContextMenuClosed (butuh kontrak di core-common).
"""


def p_changes(t):
    if 'Tahap 3a' in t:
        return t
    eol = eol_of(t)
    if not t.endswith(eol):
        t += eol
    return t + CHANGES_BLOCK.replace('\n', eol)


# -------------------------------------------------------------------- layout

def make_layout(src):
    eol = eol_of(src)
    out = sub_n(src, r'\s*android:fitsSystemWindows="true"', '', 1, 'activity_termux.xml fitsSystemWindows')
    head = '<!-- molina-suite: salinan activity_termux.xml tanpa fitsSystemWindows; dipakai TermuxFragment (insets ditangani shell). -->'
    return head + eol + out


# ------------------------------------------------------------ TermuxFragment

FRAGMENT_JAVA = r'''package com.molina.suite.terminal.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.molina.suite.terminal.R;
import com.molina.suite.terminal.app.activities.HelpActivity;
import com.molina.suite.terminal.app.activities.SettingsActivity;
import com.molina.suite.terminal.app.settings.properties.TermuxAppSharedProperties;
import com.molina.suite.terminal.app.terminal.TermuxActivityRootView;
import com.molina.suite.terminal.app.terminal.TermuxSessionsListViewController;
import com.molina.suite.terminal.app.terminal.TermuxTerminalSessionClient;
import com.molina.suite.terminal.app.terminal.TermuxTerminalViewClient;
import com.molina.suite.terminal.app.terminal.io.TerminalToolbarViewPager;
import com.molina.suite.terminal.app.utils.CrashUtils;
import com.molina.suite.terminal.shared.activities.ReportActivity;
import com.molina.suite.terminal.shared.data.DataUtils;
import com.molina.suite.terminal.shared.interact.TextInputDialogUtils;
import com.molina.suite.terminal.shared.logger.Logger;
import com.molina.suite.terminal.shared.packages.PermissionUtils;
import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;
import com.molina.suite.terminal.shared.termux.TermuxConstants;
import com.molina.suite.terminal.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.molina.suite.terminal.shared.termux.TermuxUtils;
import com.molina.suite.terminal.shared.terminal.io.extrakeys.ExtraKeysView;
import com.molina.suite.terminal.shared.view.KeyboardUtils;
import com.molina.suite.terminal.shared.view.ViewUtils;
import com.molina.suite.terminal.terminal.TerminalSession;
import com.molina.suite.terminal.view.TerminalView;

/**
 * Berkas tambahan molina-suite. Terminal Termux sebagai fragment di dalam shell, supaya bottom
 * navigation tetap tampil. Padanan {@link TermuxActivity}; TermuxActivity tetap ada sebagai cadangan.
 * <p/>
 * Shell memakai add/hide, jadi fragment yang disembunyikan tidak masuk onStop. Status "visible"
 * (mIsVisible) karena itu dikendalikan lewat onResume, onStop, dan onHiddenChanged.
 * <p/>
 * Fragment tidak mengimplementasikan {@link TermuxHost} langsung karena Fragment.isVisible() final;
 * kontrak diberikan lewat {@link FragmentHost}.
 */
public class TermuxFragment extends Fragment {

    private static final String LOG_TAG = "TermuxFragment";

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private final TermuxHost mHost = new FragmentHost();

    private TermuxAppSharedPreferences mPreferences;
    private TermuxAppSharedProperties mProperties;

    private TermuxService mTermuxService;
    private Context mServiceContext;

    private View mFragmentView;
    private TerminalView mTerminalView;
    private TermuxTerminalViewClient mTermuxTerminalViewClient;
    private TermuxTerminalSessionClient mTermuxTerminalSessionClient;
    private TermuxActivityRootView mTermuxActivityRootView;
    private View mTermuxActivityBottomSpaceView;
    private ExtraKeysView mExtraKeysView;
    private TermuxSessionsListViewController mTermuxSessionListViewController;

    private Toast mLastToast;

    private boolean mIsVisible;
    private boolean isOnResumeAfterOnCreate;
    private boolean mIsInvalidState;

    /** Bootstrap/sesi pertama sudah diminta (mencegah dua kali setupBootstrapIfNeeded). */
    private boolean mFirstSessionRequested;
    /** Service terhubung saat fragment tersembunyi; sesi pertama dibuat begitu tab tampil. */
    private boolean mFirstSessionPending;
    /** Sesi pertama sudah ada (dibuat di sini atau sudah ada di service). */
    private boolean mInitialSessionReady;

    private boolean mReceiverRegistered;
    private int mSavedSoftInputMode;
    private int mTerminalToolbarDefaultHeight;


    public static TermuxFragment newInstance(boolean failsafeSession) {
        TermuxFragment fragment = new TermuxFragment();
        Bundle args = new Bundle();
        args.putBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, failsafeSession);
        fragment.setArguments(args);
        return fragment;
    }


    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.logDebug(LOG_TAG, "onCreate");

        isOnResumeAfterOnCreate = true;

        final Context context = requireContext();

        // Notifikasi crash dari run sebelumnya, bila ada.
        CrashUtils.notifyAppCrashOnLastRun(context, LOG_TAG);

        // Hapus ReportInfo serialized lebih dari 14 hari.
        ReportActivity.deleteReportInfoFilesOlderThanXDays(context, 14, false);

        mProperties = new TermuxAppSharedProperties(context);

        // Gagal bila TermuxConstants.TERMUX_PACKAGE_NAME tidak sama dengan applicationId.
        mPreferences = TermuxAppSharedPreferences.build(context, true);
        if (mPreferences == null) {
            // Dialog untuk menutup aplikasi sudah ditampilkan; jangan jalankan kode terminal.
            mIsInvalidState = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (mIsInvalidState) return new FrameLayout(requireContext());

        final int themeRes = mProperties.isUsingBlackUI() ? R.style.Theme_Termux_Black : R.style.Theme_Termux;
        final LayoutInflater themedInflater = inflater.cloneInContext(new ContextThemeWrapper(requireContext(), themeRes));

        mFragmentView = themedInflater.inflate(R.layout.fragment_termux, container, false);

        mTermuxActivityRootView = (TermuxActivityRootView) mFragmentView;
        mTermuxActivityRootView.setHost(mHost);
        mTermuxActivityBottomSpaceView = mFragmentView.findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        setMargins();
        setDrawerTheme();
        setTermuxTerminalViewAndClients();
        setTerminalToolbarView(savedInstanceState);
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();

        registerForContextMenu(mTerminalView);

        bindTermuxService();

        return mFragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState || mFragmentView == null) return;
        if (isHidden()) return;

        if (!mIsVisible) markVisible();
        resumeClients();
    }

    @Override
    public void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");

        leaveVisible();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        Logger.logDebug(LOG_TAG, "onHiddenChanged: " + hidden);

        if (mIsInvalidState || mFragmentView == null) return;

        if (hidden) {
            leaveVisible();
        } else if (isResumed()) {
            markVisible();
            resumeClients();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveTerminalToolbarTextInput(outState);
    }

    @Override
    public void onDestroyView() {
        Logger.logDebug(LOG_TAG, "onDestroyView");

        leaveVisible();

        if (mTermuxService != null) {
            // Jangan biarkan service dan session client memegang referensi ke view yang dihancurkan.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        if (mServiceContext != null) {
            try {
                mServiceContext.unbindService(mServiceConnection);
            } catch (Exception e) {
                // abaikan
            }
            mServiceContext = null;
        }

        mFirstSessionRequested = false;
        mFirstSessionPending = false;
        mInitialSessionReady = false;

        mFragmentView = null;
        mTerminalView = null;
        mTermuxTerminalViewClient = null;
        mTermuxTerminalSessionClient = null;
        mTermuxActivityRootView = null;
        mTermuxActivityBottomSpaceView = null;
        mExtraKeysView = null;
        mTermuxSessionListViewController = null;

        super.onDestroyView();
    }


    // ------------------------------------------------------------- visibilitas

    /** Padanan onStart() TermuxActivity. */
    private void markVisible() {
        if (mIsInvalidState || mIsVisible || mFragmentView == null) return;

        mIsVisible = true;
        mSavedSoftInputMode = requireActivity().getWindow().getAttributes().softInputMode;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addRootViewGlobalLayoutListener();

        registerBroadcastReceiver();
    }

    /** Padanan onResume() TermuxActivity. */
    private void resumeClients() {
        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        isOnResumeAfterOnCreate = false;

        maybeStartFirstSession();
    }

    /** Padanan onStop() TermuxActivity, ditambah pemulihan state window milik shell. */
    private void leaveVisible() {
        if (!mIsVisible) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeRootViewGlobalLayoutListener();
        unregisterBroadcastReceiver();

        DrawerLayout drawer = findDrawer();
        if (drawer != null) drawer.closeDrawers();

        // Flag keyboard yang diatur klien terminal berlaku pada window shell; kembalikan agar tab lain normal.
        final Context context = getContext();
        if (context != null && mTerminalView != null)
            KeyboardUtils.hideSoftKeyboard(context, mTerminalView);

        final Activity activity = getActivity();
        if (activity != null) {
            KeyboardUtils.clearDisableSoftKeyboardFlags(activity);
            activity.getWindow().setSoftInputMode(mSavedSoftInputMode);
        }
    }


    // ------------------------------------------------------------------ service

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            handleServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        }
    };

    private void bindTermuxService() {
        mServiceContext = requireContext();

        // Jalankan TermuxService dan biarkan hidup terlepas dari siapa yang terikat.
        Intent serviceIntent = new Intent(mServiceContext, TermuxService.class);
        mServiceContext.startService(serviceIntent);

        if (!mServiceContext.bindService(serviceIntent, mServiceConnection, 0))
            throw new RuntimeException("bindService() failed");

        // Beri tahu aplikasi lain bahwa terminal dibuka.
        TermuxUtils.sendTermuxOpenedBroadcast(mServiceContext);
    }

    private void handleServiceConnected(IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        if (mFragmentView == null) return; // view sudah dihancurkan

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                startFirstSession();
            } else {
                // Service terhubung saat tab tersembunyi: tunda sampai tab tampil.
                mFirstSessionPending = true;
            }
        } else {
            mInitialSessionReady = true;
            mTermuxTerminalSessionClient.setCurrentSession(mTermuxTerminalSessionClient.getCurrentStoredSessionOrLast());
        }

        // Perbarui klien pada TerminalSession dan TerminalEmulator.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionClient);
    }

    private void maybeStartFirstSession() {
        if (!mFirstSessionPending || mTermuxService == null || !mIsVisible) return;

        mFirstSessionPending = false;
        if (mTermuxService.isTermuxSessionsEmpty()) startFirstSession();
    }

    private void startFirstSession() {
        if (mFirstSessionRequested) return;
        mFirstSessionRequested = true;

        TermuxInstaller.setupBootstrapIfNeeded(requireActivity(), () -> {
            if (mTermuxService == null || mTermuxTerminalSessionClient == null) return; // view sudah dihancurkan
            try {
                Bundle args = getArguments();
                boolean launchFailsafe = args != null && args.getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionClient.addNewSession(launchFailsafe, null);
                mInitialSessionReady = true;
            } catch (WindowManager.BadTokenException e) {
                // Activity ditutup saat dialog bootstrap - abaikan.
            }
        });
    }

    /**
     * Pengganti finishActivityIfNotFinishing(): di dalam shell tidak ada Activity yang ditutup.
     * Bila sesi habis dan service tidak sedang berhenti, buat sesi baru agar tab tidak kosong.
     */
    private void onTerminalCloseRequested() {
        final TermuxService service = mTermuxService;
        if (service == null || service.wantsToStop() || mTermuxTerminalSessionClient == null) return;
        if (!mInitialSessionReady) return; // bootstrap/sesi pertama belum selesai

        if (service.isTermuxSessionsEmpty())
            mTermuxTerminalSessionClient.addNewSession(false, null);
    }


    // ------------------------------------------------------------------- view

    private void setDrawerTheme() {
        if (mProperties.isUsingBlackUI()) {
            mFragmentView.findViewById(R.id.left_drawer).setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.background_dark));
            ((ImageButton) mFragmentView.findViewById(R.id.settings_button)).setColorFilter(Color.WHITE);
        }
    }

    private void setMargins() {
        RelativeLayout relativeLayout = mFragmentView.findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    private void addRootViewGlobalLayoutListener() {
        if (mTermuxActivityRootView != null)
            mTermuxActivityRootView.getViewTreeObserver().addOnGlobalLayoutListener(mTermuxActivityRootView);
    }

    private void removeRootViewGlobalLayoutListener() {
        if (mTermuxActivityRootView != null)
            mTermuxActivityRootView.getViewTreeObserver().removeOnGlobalLayoutListener(mTermuxActivityRootView);
    }

    private void setTermuxTerminalViewAndClients() {
        mTermuxTerminalSessionClient = new TermuxTerminalSessionClient(mHost);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(mHost, mTermuxTerminalSessionClient);

        mTerminalView = mFragmentView.findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        mTermuxTerminalViewClient.onCreate();
        mTermuxTerminalSessionClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = mFragmentView.findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(mHost, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        final ViewPager terminalToolbarViewPager = findToolbarPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(mHost, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(mHost, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = findToolbarPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = (int) Math.round(mTerminalToolbarDefaultHeight *
            (mProperties.getExtraKeysInfo() == null ? 0 : mProperties.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    private void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = findToolbarPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(requireContext(), (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && terminalToolbarViewPager.getCurrentItem() == 1) {
            // Fokuskan text input bila baru ditampilkan.
            mFragmentView.findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle outState) {
        if (outState == null || mFragmentView == null) return;

        final EditText textInputView = mFragmentView.findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) outState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = mFragmentView.findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    private void setNewSessionButtonView() {
        View newSessionButton = mFragmentView.findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(requireActivity(), R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        mFragmentView.findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            findDrawer().closeDrawers();
        });

        mFragmentView.findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    private DrawerLayout findDrawer() {
        if (mFragmentView == null) return null;
        DrawerLayout drawer = mFragmentView.findViewById(R.id.drawer_layout);
        return drawer;
    }

    private ViewPager findToolbarPager() {
        if (mFragmentView == null) return null;
        ViewPager pager = mFragmentView.findViewById(R.id.terminal_toolbar_view_pager);
        return pager;
    }

    private TerminalSession currentSession() {
        return mTerminalView != null ? mTerminalView.getCurrentSession() : null;
    }


    // ---------------------------------------------------------- API untuk shell

    /**
     * Tombol back dari shell. Mengembalikan true bila fragment sudah menanganinya (drawer ditutup);
     * false bila shell yang memutuskan.
     */
    public boolean handleBackPressed() {
        final DrawerLayout drawer = findDrawer();
        if (drawer != null && drawer.isDrawerOpen(Gravity.LEFT)) {
            drawer.closeDrawers();
            return true;
        }
        return false;
    }

    /** Diteruskan shell saat context menu ditutup (Fragment tidak menerima callback ini). */
    public void onContextMenuClosed(Menu menu) {
        if (mTerminalView != null) mTerminalView.onContextMenuClosed(menu);
    }


    // ------------------------------------------------------------ context menu

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, @Nullable ContextMenu.ContextMenuInfo menuInfo) {
        TerminalSession currentSession = currentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, currentSession.getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        TerminalSession session = currentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                startActivity(new Intent(requireContext(), HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                startActivity(new Intent(requireContext(), SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(requireActivity());
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionClient != null)
                mTermuxTerminalSessionClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // startActivity() tidak terdokumentasi melempar IllegalArgumentException,
            // tetapi laporan crash menunjukkan kadang terjadi.
            new AlertDialog.Builder(requireActivity()).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install, (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))).setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

    private void showToast(String text, boolean longDuration) {
        final Context context = getContext();
        if (context == null || text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(context, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }


    // ------------------------------------------------------------------ izin

    /** Proses yang mengakses /sdcard membutuhkan izin ini. */
    private boolean ensureStoragePermissionGranted() {
        if (PermissionUtils.checkPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return true;
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission not granted, requesting permission.");
            // Hasil permintaan kembali ke fragment ini (bukan ke Activity).
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.logInfo(LOG_TAG, "Storage permission granted by user on request.");
            TermuxInstaller.setupStorageSymlinks(requireContext());
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission denied by user on request.");
        }
    }


    // -------------------------------------------------------- broadcast receiver

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !mIsVisible) return;

            fixBroadcastIntent(intent);

            final String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                    Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                    if (ensureStoragePermissionGranted())
                        TermuxInstaller.setupStorageSymlinks(requireContext());
                    return;
                case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                    Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                    reloadStyling();
                    return;
                default:
            }
        }
    };

    private void registerBroadcastReceiver() {
        if (mReceiverRegistered) return;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);

        requireContext().registerReceiver(mBroadcastReceiver, intentFilter);
        mReceiverRegistered = true;
    }

    private void unregisterBroadcastReceiver() {
        if (!mReceiverRegistered) return;
        mReceiverRegistered = false;

        final Context context = getContext();
        if (context == null) return;
        try {
            context.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // sudah tidak terdaftar
        }
    }

    private void fixBroadcastIntent(Intent intent) {
        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    private void reloadStyling() {
        if (mProperties != null) {
            mProperties.loadTermuxPropertiesFromDisk();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mProperties.getExtraKeysInfo());
            }
        }

        setMargins();
        setTerminalToolbarHeight();

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onReload();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReload();

        if (mTermuxService != null)
            mTermuxService.setTerminalTranscriptRows();
    }


    // ------------------------------------------------------------ kontrak host

    /** Implementasi {@link TermuxHost} untuk fragment ini. */
    private final class FragmentHost implements TermuxHost {

        @Override
        public Activity getHostActivity() {
            return requireActivity();
        }

        @Override
        public TerminalView getTerminalView() {
            return mTerminalView;
        }

        @Override
        public TermuxService getTermuxService() {
            return mTermuxService;
        }

        @Override
        public TermuxAppSharedPreferences getPreferences() {
            return mPreferences;
        }

        @Override
        public TermuxAppSharedProperties getProperties() {
            return mProperties;
        }

        @Override
        public DrawerLayout getDrawer() {
            return findDrawer();
        }

        @Override
        public ViewPager getTerminalToolbarViewPager() {
            return findToolbarPager();
        }

        @Override
        public ExtraKeysView getExtraKeysView() {
            return mExtraKeysView;
        }

        @Override
        public void setExtraKeysView(ExtraKeysView extraKeysView) {
            mExtraKeysView = extraKeysView;
        }

        @Override
        public TermuxActivityRootView getTermuxActivityRootView() {
            return mTermuxActivityRootView;
        }

        @Override
        public View getTermuxActivityBottomSpaceView() {
            return mTermuxActivityBottomSpaceView;
        }

        @Override
        public TermuxTerminalViewClient getTermuxTerminalViewClient() {
            return mTermuxTerminalViewClient;
        }

        @Override
        public TermuxTerminalSessionClient getTermuxTerminalSessionClient() {
            return mTermuxTerminalSessionClient;
        }

        @Nullable
        @Override
        public TerminalSession getCurrentSession() {
            return currentSession();
        }

        @Override
        public boolean isTerminalViewSelected() {
            return findToolbarPager().getCurrentItem() == 0;
        }

        @Override
        public boolean isVisible() {
            return mIsVisible;
        }

        @Override
        public boolean isOnResumeAfterOnCreate() {
            return isOnResumeAfterOnCreate;
        }

        @Override
        public void toggleTerminalToolbar() {
            TermuxFragment.this.toggleTerminalToolbar();
        }

        @Override
        public void finishActivityIfNotFinishing() {
            onTerminalCloseRequested();
        }

        @Override
        public void showToast(String text, boolean longDuration) {
            TermuxFragment.this.showToast(text, longDuration);
        }

        @Override
        public void termuxSessionListNotifyUpdated() {
            if (mTermuxSessionListViewController != null)
                mTermuxSessionListViewController.notifyDataSetChanged();
        }

        @Override
        public int getNavBarHeight() {
            // Insets ditangani shell; workaround fullscreen tidak dipakai di dalam shell.
            return 0;
        }

        @Override
        public <T extends View> T findViewById(int id) {
            if (mFragmentView == null) return null;
            return mFragmentView.findViewById(id);
        }
    }

}
'''


def check_host_impl(host_text, frag_text):
    names = re.findall(r'^[ \t]+(?:@\w+[ \t]+)?(?:<[^>]+>[ \t]+)?[\w.<>\[\]]+[ \t]+(\w+)\([^)]*\);', host_text, re.M)
    names = sorted(set(names))
    if len(names) != 23:
        raise Gagal('TermuxHost: terbaca %d metode, seharusnya 23 (%s)' % (len(names), ', '.join(names)))
    missing = [n for n in names if not re.search(r'public\s+[\w.<>\[\], ]+\s+' + n + r'\(', frag_text)]
    if missing:
        raise Gagal('FragmentHost tidak mengimplementasikan: ' + ', '.join(missing))


def new_file(path, content):
    if os.path.isfile(path):
        cur = read(path)
        if cur != content:
            raise Gagal('berkas sudah ada dengan isi berbeda, tidak ditimpa: ' + path)
        return cur, content
    return '', content


def main():
    if not os.path.isdir(ROOT):
        print('GAGAL: jalankan dari /storage/emulated/0/molina-suite (engines/termux tidak ada)')
        return 1
    plan = []
    try:
        host_old = read(E + '/TermuxHost.java')
        host_new = p_host(host_old)
        plan.append((E + '/TermuxHost.java', host_old, host_new))

        s_old = read(E + '/terminal/TermuxTerminalSessionClient.java')
        plan.append((E + '/terminal/TermuxTerminalSessionClient.java', s_old, p_session(s_old)))

        v_old = read(E + '/terminal/TermuxTerminalViewClient.java')
        plan.append((E + '/terminal/TermuxTerminalViewClient.java', v_old, p_view(v_old)))

        layout_src = read(MAIN + '/res/layout/activity_termux.xml')
        layout_path = MAIN + '/res/layout/fragment_termux.xml'
        o, n = new_file(layout_path, make_layout(layout_src))
        plan.append((layout_path, o, n))

        if FRAGMENT_JAVA.count('{') != FRAGMENT_JAVA.count('}'):
            raise Gagal('TermuxFragment: kurung kurawal tidak seimbang (salin-tempel terpotong?)')
        check_host_impl(host_new, FRAGMENT_JAVA)
        frag_path = E + '/TermuxFragment.java'
        o, n = new_file(frag_path, FRAGMENT_JAVA)
        plan.append((frag_path, o, n))

        c_old = read(ROOT + '/MOLINA-CHANGES.md')
        plan.append((ROOT + '/MOLINA-CHANGES.md', c_old, p_changes(c_old)))
    except Gagal as e:
        print('GAGAL: %s' % e)
        print('Tidak ada berkas yang ditulis.')
        return 1

    for path, old, new in plan:
        if old == new:
            continue
        print('=' * 72)
        if old == '':
            print('BARU: %s (%d baris)' % (path, len(new.splitlines())))
            continue
        print(path)
        for line in difflib.unified_diff(old.splitlines(), new.splitlines(), 'lama', 'baru', n=0, lineterm=''):
            print(line)

    print('=' * 72)
    print('RINGKASAN (%s)' % ('APPLY' if APPLY else 'DRY-RUN'))
    for path, old, new in plan:
        state = 'sudah diterapkan' if old == new else ('berkas baru' if old == '' else 'ubah')
        print('  %-72s %s' % (path.replace(ROOT + '/', ''), state))

    if all(o == n for _, o, n in plan):
        print('Tidak ada perubahan (idempoten).')
        return 0
    if not APPLY:
        print('Dry-run: tidak ada berkas ditulis. Jalankan dengan --apply untuk menulis.')
        return 0
    for path, old, new in plan:
        if old != new:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(new)
    print('Selesai: %d berkas ditulis.' % sum(1 for _, o, n in plan if o != n))
    return 0


sys.exit(main())
