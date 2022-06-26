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

package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Slog;

public class NewQsHelper {
    private NewQsHelper() {} // No instances

    private static void loadSettings(Context context) {
        newQsStyle = 1;
        primaryLabel = 0 + 4 /* qqs quadratic = yes */ /* + 16 qqs round = no */;
        showSecondaryLabel = 0
                /* + 2 qs quadratic = no */ /* + 4 qqs quadratic = no */
                /* + 8 qs round = no */ /* + 16 qqs round = no */;
        allowChevron = true;
        useSmartLabel = true;

        verticalScroll = false;
        useQsMediaPlayer = 1;
        dualTone = 0;
        useSeperateShade = false;
        useSplitShade = 0 + 1 /* override = yes */ /* + 2 portrait = no */ + 4 /* landscape = yes */;
        disallowDynamicQsRow = true;
        qqsRowPortrait = 2;
        qsRowPortrait = 4;
        qqsRowLandscape = 2;
        qsRowLandscape = 2;
        qqsColumnPortrait = 4;
        qsColumnPortrait = 4;
        qqsColumnLandscape = 4;
        qsColumnLandscape = 4;
    }

    // === TILE === //

    public static Integer newQsStyle = null; // 0 = disabled, 1 = quadratic, 2 = round
    public static Integer primaryLabel = null;
    public static Integer showSecondaryLabel = null;
    public static Boolean allowChevron = null;
    public static Boolean useSmartLabel = null;

    private static int getStyle(Context context) {
        if (newQsStyle == null) loadSettings(context);
        return newQsStyle;
    }

    public static boolean isAnyTypeOfNewQs(Context context) {
        return getStyle(context) != 0;
    }

    public static boolean shouldBeRoundTile(Context context) {
        int newQsStyle = getStyle(context);
        return newQsStyle == 2; // Ensure the type isn't quadratic.
    }

    public static boolean shouldShowLabelChevron(Context context, boolean isRoundTile, boolean isCollapsed) {
        return shouldAllowChevron(context, isRoundTile, isCollapsed) &&
            shouldAllowPrimaryLabel(context, isRoundTile, isCollapsed) && isRoundTile;
        // round tiles show label chevron, quadratic show icon chevron. however, no label = no label chevron
    }

    // true will center the text sacrificing free space
    // false = text & chevron as a whole gets centered
    public static boolean shouldBalanceLabelChevron(Context context, boolean isRoundTile, boolean isCollapsed) {
        return false; // Feature currently force disabled
    }

    // true will force the chevron to go outside of viewbounds, which leads to centered text
    // false = text & chevron as a whole gets centered
    // ignored if shouldBalanceLabelChevron returns true
    public static boolean shouldInverseBalanceLabelChevron(Context context, boolean isRoundTile, boolean isCollapsed) {
        return true; // Feature currently force enabled
    }

    public static boolean shouldShowIconChevron(Context context, boolean isRoundTile, boolean isCollapsed) {
        // round tiles show label chevron, quadratic show icon chevron
        return shouldAllowChevron(context, isRoundTile, isCollapsed) && !isRoundTile;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldShowSecondaryLabel(Context context, boolean isRoundTile, boolean isCollapsed) {
        if (showSecondaryLabel == null) loadSettings(context);
        return (showSecondaryLabel & (int) Math.pow(2, 1 + (isRoundTile ? 2 : 0) + (isCollapsed ? 1 : 0))) > 0;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldAllowChevron(Context context, boolean isRoundTile, boolean isCollapsed) {
        if (allowChevron == null) loadSettings(context);
        return allowChevron;
    }

    public static boolean shouldAllowPrimaryLabel(Context context, boolean isRoundTile, boolean isCollapsed) {
        if (!isAnyTypeOfNewQs(context)) return true; // this shouldn't be respected by AOSP qs
        if (primaryLabel == null) loadSettings(context);
        if (!isCollapsed) return true; // for now, don't allow hiding primary label in expanded state, it makes things harder
        return (primaryLabel & (int) Math.pow(2, 1 + (isRoundTile ? 2 : 0) + (isCollapsed ? 1 : 0))) > 0;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldUseSmartLabel(Context context) {
        if (useSmartLabel == null) loadSettings(context);
        return useSmartLabel;
    }

    // === PANEL === //

    public static Boolean verticalScroll = null;
    public static Integer useQsMediaPlayer = null; // negative = default, positive = bitmask (1 mediahost, 2 panel feature)
    public static Integer dualTone = null; // 0 = dual-tone (default), 1 = foreground, 2 = background
    public static Boolean useSeperateShade = null;
    public static Integer useSplitShade = null;
    public static Boolean disallowDynamicQsRow = null;
    public static Integer qqsRowPortrait = null;
    public static Integer qsRowPortrait = null;
    public static Integer qqsRowLandscape = null;
    public static Integer qsRowLandscape = null;
    public static Integer qqsColumnPortrait = null;
    public static Integer qsColumnPortrait = null;
    public static Integer qqsColumnLandscape = null;
    public static Integer qsColumnLandscape = null;

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean needVerticalScroll(Context context) {
        if (verticalScroll == null) loadSettings(context);
        return verticalScroll;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static int getDualToneSetting(Context context) {
        if (dualTone == null) loadSettings(context);
        return dualTone;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static Integer shouldUseQsMediaPlayer(Context context) {
        if (useQsMediaPlayer == null) loadSettings(context);
        return useQsMediaPlayer >= 0 ? useQsMediaPlayer : null;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    // ignored if shouldUseSplitShade returns true
    public static boolean shouldUseSeperateShade(Context context) {
        if (useSeperateShade == null) loadSettings(context);
        return useSeperateShade;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldUseSplitShade(Resources res, boolean def, boolean isPortrait) {
        if (useSplitShade == null) {
            Slog.w("SystemUI:NewQsHelper", "shouldUseSplitShade() called too early... Context is unavailable");
            return def;
        }
        if ((useSplitShade & 1) == 0) return def;
        return (useSplitShade & (isPortrait ? 2 : 4)) > 0;
    }

    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldUseSplitShade(Resources res, boolean def) {
        return shouldUseSplitShade(res, def, res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    // returning false means using AOSP qs row / column code
    // CAUTION: this gets respected even when newQsStyle is 0
    public static boolean shouldDisallowDynamicQsRow(Context context) {
        if (disallowDynamicQsRow == null) loadSettings(context);
        return disallowDynamicQsRow;
    }

    public static int getQsColumnCountForCurrentOrientation(Context context) {
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return getQsColumnCountForPortrait(context);
        } else {
            return getQsColumnCountForLandscape(context);
        }
    }

    public static int getQsRowCountForCurrentOrientation(Context context) {
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return getQsRowCountForPortrait(context);
        } else {
            return getQsRowCountForLandscape(context);
        }
    }

    public static int getQqsColumnCountForCurrentOrientation(Context context) {
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return getQqsColumnCountForPortrait(context);
        } else {
            return getQqsColumnCountForLandscape(context);
        }
    }

    public static int getQqsRowCountForCurrentOrientation(Context context) {
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return getQqsRowCountForPortrait(context);
        } else {
            return getQqsRowCountForLandscape(context);
        }
    }

    public static int getQqsRowCountForPortrait(Context context) {
        if (qqsRowPortrait == null) loadSettings(context);
        return qqsRowPortrait;
    }

    public static int getQqsRowCountForLandscape(Context context) {
        if (qqsRowLandscape == null) loadSettings(context);
        return qqsRowLandscape;
    }

    public static int getQsRowCountForPortrait(Context context) {
        if (qsRowPortrait == null) loadSettings(context);
        return qsRowPortrait;
    }

    public static int getQsRowCountForLandscape(Context context) {
        if (qsRowLandscape == null) loadSettings(context);
        return qsRowLandscape;
    }

    public static int getQsColumnCountForPortrait(Context context) {
        if (qsColumnPortrait == null) loadSettings(context);
        return qsColumnPortrait;
    }

    public static int getQsColumnCountForLandscape(Context context) {
        if (qsColumnLandscape == null) loadSettings(context);
        return qsColumnLandscape;
    }

    public static int getQqsColumnCountForPortrait(Context context) {
        if (qqsColumnPortrait == null) loadSettings(context);
        return qqsColumnPortrait;
    }

    public static int getQqsColumnCountForLandscape(Context context) {
        if (qqsColumnLandscape == null) loadSettings(context);
        return qqsColumnLandscape;
    }

    // State
    private static Boolean skipQqsOnExpansion = null;
    public static boolean shouldSkipQqsOnExpansion(Context context, boolean value) {
        return (shouldRespectSkipQqsOnExpansion(context) && (skipQqsOnExpansion == null || skipQqsOnExpansion))
                    || (!shouldRespectSkipQqsOnExpansion(context) && value);
    }

    private static boolean shouldRespectSkipQqsOnExpansion(Context context) {
        return shouldUseQsMediaPlayer(context) != null && (shouldUseQsMediaPlayer(context) & 2) == 0;
    }

    public static void setShouldSkipQqsOnExpansion(boolean newValue) {
        skipQqsOnExpansion = newValue;
    }
}
