package com.molina.suite.terminal.app.terminal.io;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.molina.suite.terminal.R;
import com.molina.suite.terminal.app.TermuxHost;
import com.molina.suite.terminal.shared.terminal.io.extrakeys.ExtraKeysView;
import com.molina.suite.terminal.terminal.TerminalSession;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxHost mHost;
        String mSavedTextInput;

        public PageAdapter(TermuxHost host, String savedTextInput) {
            this.mHost = host;
            this.mSavedTextInput = savedTextInput;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mHost.getHostActivity());
            View layout;
            if (position == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(new TermuxTerminalExtraKeys(mHost.getTerminalView(),
                    mHost.getTermuxTerminalViewClient(), mHost.getTermuxTerminalSessionClient()));
                extraKeysView.setButtonTextAllCaps(mHost.getProperties().shouldExtraKeysTextBeAllCaps());
                mHost.setExtraKeysView(extraKeysView);
                extraKeysView.reload(mHost.getProperties().getExtraKeysInfo());

                // apply extra keys fix if enabled in prefs
                if (mHost.getProperties().isUsingFullScreen() && mHost.getProperties().isUsingFullScreenWorkAround()) {
                    FullScreenWorkAround.apply(mHost);
                }

            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false);
                final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }

                editText.setOnEditorActionListener((v, actionId, event) -> {
                    TerminalSession session = mHost.getCurrentSession();
                    if (session != null) {
                        if (session.isRunning()) {
                            String textToSend = editText.getText().toString();
                            if (textToSend.length() == 0) textToSend = "\r";
                            session.write(textToSend);
                        } else {
                            mHost.getTermuxTerminalSessionClient().removeFinishedSession(session);
                        }
                        editText.setText("");
                    }
                    return true;
                });
            }
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            collection.removeView((View) view);
        }

    }



    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        final TermuxHost mHost;
        final ViewPager mTerminalToolbarViewPager;

        public OnPageChangeListener(TermuxHost host, ViewPager viewPager) {
            this.mHost = host;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (position == 0) {
                mHost.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}
