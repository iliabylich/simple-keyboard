package rkr.simplekeyboard.inputmethod.latin;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import dev.ibylich.mpclipboard.Connectivity;

public final class MPClipboardTileService extends TileService {
    private static Connectivity sConnectivity = Connectivity.DISCONNECTED;
    private static MPClipboardTileService sListening;

    static void setConnectivity(final Connectivity connectivity) {
        if (sConnectivity == connectivity) return;
        sConnectivity = connectivity;
        if (sListening != null) {
            sListening.render();
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        sListening = this;
        render();
    }

    @Override
    public void onStopListening() {
        if (sListening == this) {
            sListening = null;
        }
        super.onStopListening();
    }

    private void render() {
        final Tile tile = getQsTile();
        if (tile == null) return;

        final String status;
        switch (sConnectivity) {
            case CONNECTED:
                status = "Connected";
                break;
            case CONNECTING:
                status = "Connecting";
                break;
            default:
                status = "Disconnected";
                break;
        }

        tile.setState(sConnectivity == Connectivity.CONNECTED ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setLabel("mpclipboard");
            tile.setSubtitle(status);
        } else {
            tile.setLabel("mpclipboard: " + status);
        }
        tile.updateTile();
    }
}
