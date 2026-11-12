package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.text.TextUtils;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.MPClipboardBridge;

public final class MPClipboardSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_mpclipboard);
        refreshSummaries();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        refreshSummaries();
    }

    private void refreshSummaries() {
        final SharedPreferences prefs = getSharedPreferences();
        setSummary(MPClipboardBridge.KEY_URL, prefs.getString(MPClipboardBridge.KEY_URL, ""), false);
        setSummary(MPClipboardBridge.KEY_TOKEN, prefs.getString(MPClipboardBridge.KEY_TOKEN, ""), true);
        setSummary(MPClipboardBridge.KEY_ID, prefs.getString(MPClipboardBridge.KEY_ID, ""), false);
    }

    private void setSummary(final String key, final String value, final boolean secret) {
        final Preference pref = findPreference(key);
        if (pref == null) return;
        if (TextUtils.isEmpty(value)) {
            pref.setSummary("Not set");
        } else {
            pref.setSummary(secret ? "••••••" : value);
        }
    }
}
