/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.qs.customize

import android.content.Context
import android.text.TextUtils
import com.android.systemui.plugins.qs.QSIconView
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.tileimpl.QSTileViewImplNew

/**
 * Class for displaying tiles in [QSCustomizer] with the new design (labels on the side).
 */
open class CustomizeTileViewNew(
    context: Context,
    icon: QSIconView
) : QSTileViewImplNew(context, icon, false), CustomizeTileView {

    override var shouldShowSecondaryLabel = true

    override var showAppLabel = false
        set(value) {
            field = value
            secondaryLabel.visibility = getVisibilityState(secondaryLabel.text)
        }

    override var showSideView = true
        set(value) {
            field = value
            if (!showSideView) sideView.visibility = INVISIBLE
        }

    override fun handleStateChanged(state: QSTile.State) {
        super.handleStateChanged(state)
        showRippleEffect = false
        secondaryLabel.visibility = getVisibilityState(state.secondaryLabel)
        if (!showSideView) sideView.visibility = INVISIBLE
    }

    private fun getVisibilityState(text: CharSequence?): Int {
        return if (showAppLabel && !TextUtils.isEmpty(text)) {
            VISIBLE
        } else {
            INVISIBLE
        }
    }

    override fun animationsEnabled(): Boolean {
        return false
    }

    override fun isLongClickable(): Boolean {
        return false
    }

    override fun changeState(state: QSTile.State) {
        handleStateChanged(state)
    }

    override fun loadTunables() {
        super.loadTunables()
        allowChevron = false
        shouldShowSecondaryLabel = true
        shouldAllowPrimaryLabel = true
    }
}

class CustomizeTileViewBig(
    context: Context,
    icon: QSIconView
) : CustomizeTileViewNew(context, icon) {
    override var showSideView = false
        set(value) {} // feign ignorance
}
