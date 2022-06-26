/*
 * Copyright (C) 2023 droid-ng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.view.View;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.R.drawable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SettingObserver;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

public class MediaHostTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(drawable.ic_music_note);

    @Inject
    public MediaHostTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        // Do nothing
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        // Do nothing
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        // Do nothing
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        // Do nothing
    }

    @Override
    public Intent getLongClickIntent() {
        // Do nothing
        return null;
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Do nothing
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_media_host_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = false;
        state.state = Tile.STATE_INACTIVE;
        state.label = getTileLabel();
        state.icon = mIcon;
        state.expandedAccessibilityClassName = Switch.class.getName();
        state.contentDescription = state.label;
    }

    @Override
    public int getMetricsCategory() {
        // MetricsProto/MetricsEvent is deprecated, so just simply return 0 here.
        return 0;
    }

    @Override
    public int getFrameType() {
        return 1; // Media Player
    }

    @Override
    public boolean useConsistentSize() {
        return true;
    }

    @Override
    public int getRowsConsumed() {
        return 2;
    }

    @Override
    public int getColumnsConsumed() {
        return 2;
    }
}
