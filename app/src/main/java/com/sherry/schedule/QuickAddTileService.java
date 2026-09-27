package com.sherry.schedule;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class QuickAddTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel("新建日程");
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, QuickAddActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 7301, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            //noinspection deprecation
            startActivityAndCollapse(intent);
        }
    }
}
