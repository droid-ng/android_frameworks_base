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

package com.android.systemui.qs.tileimpl

import android.content.Context
import android.content.res.Resources.ID_NULL
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

import com.android.systemui.R
import com.android.systemui.plugins.qs.QSIconView
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTileView

open class QSTileViewImplFrame @JvmOverloads constructor(
    context: Context,
    private val consistentSize: Boolean,
    private val iconView: QSIconView
) : QSTileView(context), QSTileViewImplBig {

    private var l = LinearLayout(context)
    private var view: View? = null
    protected var widthMultiplier = 0
    protected var heightMultiplier = 0
    protected var gapSizeW = 0
    protected var gapSizeH = 0

    init {
        setId(generateViewId())
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER
        l.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        addView(l)
        setSize(1, 1, 0, 0)
    }

    public fun updateView(newView: View) {
        view?.let { l.removeView(it) }
        view = newView
        l.addView(view)
    }

    override fun setSize(w: Int, h: Int, gw: Int, gh: Int) {
        if (widthMultiplier == w && heightMultiplier == h && gapSizeW == gw && gapSizeH == gh) return
        widthMultiplier = w
        heightMultiplier = h
        gapSizeW = gw
        gapSizeH = gh
        if (!consistentSize) return
        val extraw = gapSizeW * (widthMultiplier - 1)
        val extrah = gapSizeH * (heightMultiplier - 1)
        val size = resources.getDimensionPixelSize(R.dimen.new_qs_tile_width)
        val height = resources.getDimensionPixelSize(R.dimen.qs_tile_height)
        val myLayoutParams = LayoutParams(extraw + (size * widthMultiplier), extrah + (height * heightMultiplier))
        l.layoutParams = myLayoutParams
    }

    // Boilerplate
    companion object {
        private const val INVALID = -1
    }
    private var _position: Int = INVALID
    override fun setPosition(position: Int) {
        _position = position
    }
    private val locInScreen = IntArray(2)
    private val dummy = LinearLayout(context)
    override fun getIcon(): QSIconView {
        return iconView
    }
    override fun getIconWithBackground(): View {
        return dummy
    }
    override fun init(tile: QSTile) {
//         init(
//                 { v: View? -> tile.click(this) },
//                 { view: View? ->
//                     tile.longClick(this)
//                     true
//                 }
//         )
    }
    override fun onStateChanged(state: QSTile.State) {
        post {
            //handleStateChanged(state)
        }
    }
    override fun getDetailY(): Int {
        return top + height / 2
    }
    override fun getLabelContainer(): View {
        return dummy
    }
    override fun getLabel(): View {
        return dummy
    }
    override fun getSecondaryLabel(): View {
        return dummy
    }
    override fun getSecondaryIcon(): View {
        return dummy
    }
    override fun updateAccessibilityOrder(previousView: View?): View {
        accessibilityTraversalAfter = previousView?.id ?: ID_NULL
        return this
    }
    protected open fun animationsEnabled(): Boolean {
        if (!isShown) {
            return false
        }
        if (alpha != 1f) {
            return false
        }
        getLocationOnScreen(locInScreen)
        return locInScreen.get(1) >= -height
    }
}
