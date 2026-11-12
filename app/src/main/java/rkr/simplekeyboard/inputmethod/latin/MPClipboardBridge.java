package rkr.simplekeyboard.inputmethod.latin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;

import dev.ibylich.mpclipboard.Connectivity;
import dev.ibylich.mpclipboard.MPClipboard;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import dev.ibylich.mpclipboard.Output;

public final class MPClipboardBridge {
    private static final String TAG = "MPClipboardBridge";

    public static final String KEY_URL = "mpclipboard_url";
    public static final String KEY_TOKEN = "mpclipboard_token";
    public static final String KEY_ID = "mpclipboard_id";

    private final Context mContext;
    private final ClipboardManager mClipboard;
    private final SharedPreferences mPrefs;
    private final MessageQueue mQueue;

    private MPClipboard mClient;
    private ParcelFileDescriptor mFd;
    private String mLastSynced;

    private final ClipboardManager.OnPrimaryClipChangedListener mClipListener = this::onLocalClipChanged;

    private final MessageQueue.OnFileDescriptorEventListener mFdListener = (fd, events) -> {
        if ((events & MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR) != 0) {
            Log.e(TAG, "fd error, stopping");
            stopClient();
            return 0;
        }
        drain();
        return MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT
                | MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR;
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = (prefs, key) -> {
        if (KEY_URL.equals(key) || KEY_TOKEN.equals(key) || KEY_ID.equals(key)) {
            Log.i(TAG, "config changed, restarting");
            stopClient();
            startClient();
        }
    };

    public MPClipboardBridge(final Context context) {
        mContext = context;
        mClipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(context);
        mQueue = Looper.getMainLooper().getQueue();

        mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
        mClipboard.addPrimaryClipChangedListener(mClipListener);
        startClient();
    }

    public void close() {
        mClipboard.removePrimaryClipChangedListener(mClipListener);
        mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        stopClient();
    }

    private void startClient() {
        final String url = mPrefs.getString(KEY_URL, null);
        final String token = mPrefs.getString(KEY_TOKEN, null);
        final String id = mPrefs.getString(KEY_ID, null);
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(token) || TextUtils.isEmpty(id)) {
            Log.w(TAG, "not configured (" + KEY_URL + "/" + KEY_TOKEN + "/" + KEY_ID + ")");
            MPClipboardTileService.setConnectivity(Connectivity.DISCONNECTED);
            return;
        }

        mClient = MPClipboard.newInline(url, token, id);
        if (mClient == null) {
            Log.e(TAG, "MPClipboard.newInline failed");
            MPClipboardTileService.setConnectivity(Connectivity.DISCONNECTED);
            return;
        }

        try {
            mFd = ParcelFileDescriptor.fromFd(mClient.getFd());
        } catch (IOException e) {
            Log.e(TAG, "failed to dup fd", e);
            mClient.close();
            mClient = null;
            MPClipboardTileService.setConnectivity(Connectivity.DISCONNECTED);
            return;
        }
        mQueue.addOnFileDescriptorEventListener(mFd.getFileDescriptor(),
                MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT
                        | MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR,
                mFdListener);
        Log.i(TAG, "started, url=" + url + " id=" + id);
    }

    private void stopClient() {
        if (mFd != null) {
            final FileDescriptor fd = mFd.getFileDescriptor();
            mQueue.removeOnFileDescriptorEventListener(fd);
            try {
                mFd.close();
            } catch (IOException ignored) {
            }
            mFd = null;
        }
        if (mClient != null) {
            mClient.close();
            mClient = null;
        }
        MPClipboardTileService.setConnectivity(Connectivity.DISCONNECTED);
    }

    private void drain() {
        if (mClient == null) return;
        Output out;
        while ((out = mClient.read()) != null) {
            final Connectivity connectivity = out.getConnectivity();
            if (connectivity != null) {
                Log.i(TAG, "connectivity: " + connectivity);
                MPClipboardTileService.setConnectivity(connectivity);
            }
            final String text = out.getText();
            if (text != null && !text.equals(mLastSynced)) {
                Log.d(TAG, "received " + text.length() + " chars");
                mLastSynced = text;
                mClipboard.setPrimaryClip(ClipData.newPlainText("mpclipboard", text));
            }
        }
    }

    private void onLocalClipChanged() {
        if (mClient == null) return;
        final ClipData clip = mClipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        final CharSequence cs = clip.getItemAt(0).coerceToText(mContext);
        if (cs == null) return;
        final String text = cs.toString();
        if (text.isEmpty() || text.equals(mLastSynced)) return;
        Log.d(TAG, "pushing " + text.length() + " chars");
        mLastSynced = text;
        mClient.pushText(text);
    }
}
