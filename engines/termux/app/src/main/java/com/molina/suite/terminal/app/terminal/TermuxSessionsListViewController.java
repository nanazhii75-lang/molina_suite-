package com.molina.suite.terminal.app.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.molina.suite.terminal.R;
import com.molina.suite.terminal.app.TermuxHost;
import com.molina.suite.terminal.shared.shell.TermuxSession;
import com.molina.suite.terminal.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxHost mHost;
    final Activity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxHost host, List<TermuxSession> sessionList) {
        super(host.getHostActivity().getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mHost = host;
        this.mActivity = host.getHostActivity();
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        ImageButton closeButton = sessionRowView.findViewById(R.id.session_close_button);

        // Sesi pertama tidak bisa ditutup dari daftar; sesi ke-2 dan seterusnya punya tombol x.
        closeButton.setVisibility(position > 0 ? View.VISIBLE : View.GONE);

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            closeButton.setVisibility(View.GONE);
            return sessionRowView;
        }

        boolean isUsingBlackUI = mHost.getProperties().isUsingBlackUI();

        closeButton.setColorFilter(isUsingBlackUI ? Color.WHITE : Color.BLACK);
        closeButton.setOnClickListener(v -> confirmAndCloseSession(sessionAtRow));

        if (isUsingBlackUI) {
            sessionTitleView.setBackground(
                ContextCompat.getDrawable(mActivity, R.drawable.session_background_black_selected)
            );
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = isUsingBlackUI ? Color.WHITE : Color.BLACK;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);
        return sessionRowView;
    }

    /**
     * Menutup sesi dari tombol x. Sesi yang masih berjalan dikonfirmasi dulu karena prosesnya
     * akan dihentikan; sesi yang sudah selesai langsung dihapus dari daftar.
     */
    private void confirmAndCloseSession(TerminalSession session) {
        if (!session.isRunning()) {
            mHost.getTermuxTerminalSessionClient().closeSession(session);
            return;
        }

        new AlertDialog.Builder(mActivity)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.title_confirm_kill_process)
            .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                dialog.dismiss();
                mHost.getTermuxTerminalSessionClient().closeSession(session);
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        TerminalSession clickedTerminal = clickedSession == null ? null : clickedSession.getTerminalSession();
        android.util.Log.i("TermuxSessions", "klik daftar sesi: posisi=" + position + " dari " + getCount()
            + ", sesi=" + (clickedTerminal == null ? "null" : (clickedTerminal.isRunning() ? "berjalan" : "selesai")));
        mHost.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mHost.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mHost.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }

}
