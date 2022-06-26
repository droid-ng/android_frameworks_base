/*
 * Copyright (C) 2021 The Android Open Source Project
 * Copyright (C) 2022-2023 droid-ng
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

import android.annotation.SuppressLint
import android.content.Context
import android.service.quicksettings.Tile
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.android.systemui.FontSizeUtils
import com.android.systemui.R
import com.android.systemui.qs.NewQsHelper
import com.android.systemui.plugins.qs.QSIconView
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTile.BooleanState
import kotlin.random.Random

@SuppressLint("ViewConstructor")
open class QSTileViewImplNew @JvmOverloads constructor(
        context: Context,
        iicon: QSIconView,
        ccollapsed: Boolean = false
) : QSTileViewImpl(context, iicon, ccollapsed, true), QSTileViewImplBig {
    private val dummy = LinearLayout(mContext)
    private var l: LinearLayout? = null
    private var subl: RelativeLayout? = null
    private var sideViewSpacer: View? = null
    protected var usenew2 = false
    // see setIsNew2
    protected var allowLabelChevron = false
    protected var balanceLabelChevron = false
    protected var inverseBalanceLabelChevron = false
    protected var shouldAllowPrimaryLabel = false
    protected var allowIconChevron = false
    protected var allowChevron = false

    protected var widthMultiplier = 0
    protected var heightMultiplier = 0
    protected var gapSizeW = 0
    protected var gapSizeH = 0

    override fun realInit() {
        gravity = Gravity.CENTER
        orientation = LinearLayout.VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        clipChildren = false
        clipToPadding = false
        isFocusable = true

        setSize(1, 1, 0, 0)
    }

    // QS animator changes these values without our intention
    override fun setClipChildren(unused: Boolean) {
        super.setClipChildren(false)
    }
    override fun setClipToPadding(unused: Boolean) {
        super.setClipToPadding(false)
    }

    fun setIsNew2(new2: Boolean) {
        if (usenew2 == new2)
            return
        usenew2 = new2
        loadTunables()
        allowChevron = (allowLabelChevron || allowIconChevron) && allowChevron
        allowLabelChevron = allowLabelChevron && allowChevron
        allowIconChevron = allowIconChevron && allowChevron

        val extraw = gapSizeW * (widthMultiplier - 1)
        val extrah = gapSizeH * (heightMultiplier - 1)
        val newsize = resources.getDimensionPixelSize(R.dimen.new_qs_tile_width)
        val newsize2 = resources.getDimensionPixelSize(R.dimen.qs_tile_height)
        val new2size = resources.getDimensionPixelSize(R.dimen.new_qs2_tile_width)
        val newlayoutParams = LayoutParams(extraw + (newsize * widthMultiplier), extrah + (newsize2 * heightMultiplier))
        val new2layoutParams = LayoutParams(extraw + (new2size * widthMultiplier), extrah + (new2size * heightMultiplier))

        removeView(labelContainer)
        subl?.removeView(_icon)
        l?.removeView(labelContainer)
        l?.removeView(subl)
        removeView(sideView)
        removeView(l)

        subl = RelativeLayout(context)
        val iconSize = resources.getDimensionPixelSize(R.dimen.qs_icon_size)
        val iconlp = RelativeLayout.LayoutParams(iconSize, iconSize)
        iconlp.addRule(RelativeLayout.CENTER_HORIZONTAL)
        iconlp.addRule(RelativeLayout.CENTER_VERTICAL)
        _icon.id = View.generateViewId()
        subl!!.addView(_icon, iconlp)
        l = LinearLayout(context)
        l!!.apply {
            layoutParams = if (usenew2) new2layoutParams else newlayoutParams
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            background = createTileBackground()
            addView(subl, LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, iconSize))
        }
        this.addView(l)
        createAndAddLabels()
        createAndAddSideView()
        this.setColor(this.getBackgroundColorForState(lastState))
    }

    protected open fun loadTunables() {
        // Tunables
        allowLabelChevron = NewQsHelper.shouldShowLabelChevron(context, usenew2, collapsed) // Allow chevron next to label
        balanceLabelChevron = NewQsHelper.shouldBalanceLabelChevron(context, usenew2, collapsed) // Balance out label chevron
        inverseBalanceLabelChevron = NewQsHelper.shouldInverseBalanceLabelChevron(context, usenew2, collapsed) // Reverse-balance out label chevron
        allowIconChevron = NewQsHelper.shouldShowIconChevron(context, usenew2, collapsed) // Allow chevron next to icon
        shouldShowSecondaryLabel = NewQsHelper.shouldShowSecondaryLabel(context, usenew2, collapsed) // Allow secondary label
        shouldAllowPrimaryLabel = NewQsHelper.shouldAllowPrimaryLabel(mContext, usenew2, collapsed)
        allowChevron = NewQsHelper.shouldAllowChevron(context, usenew2, collapsed) // Allow any chevron at all
    }

    override fun setSize(w: Int, h: Int, gw: Int, gh: Int) {
        if (widthMultiplier == w && heightMultiplier == h && gapSizeW == gw && gapSizeH == gh) return
        widthMultiplier = w
        heightMultiplier = h
        gapSizeW = gw
        gapSizeH = gh
        usenew2 = !usenew2
        setIsNew2(!usenew2)
    }

    fun isRound(): Boolean {
        return usenew2
    }

    override fun getIconWithBackground(): View {
        if (usenew2) return l!!
        if (allowChevron && allowIconChevron) return subl!!
        return super.getIcon()
    }

    override fun getSecondaryIcon(): View {
        return dummy
    }

    override fun init(tile: QSTile) {
        init(
                { v: View? -> tile.click(l!!) },
                { view: View? ->
                    tile.longClick(l!!)
                    true
                }
        )
    }

    override fun updateResources() {
        FontSizeUtils.updateFontSize(label, R.dimen.new_qs_tile_text_size)
        FontSizeUtils.updateFontSize(secondaryLabel, R.dimen.new_qs_tile_text_size)

        val iconSize = context.resources.getDimensionPixelSize(R.dimen.qs_icon_size)
        _icon.layoutParams.apply {
            height = iconSize
            width = iconSize
        }
    }

    override fun createAndAddLabels() {
        labelContainer = LayoutInflater.from(context)
                .inflate(if (usenew2) R.layout.qs_tile_label_new2 else R.layout.qs_tile_label_new, this, false) as IgnorableChildLinearLayout
        label = labelContainer.requireViewById(R.id.tile_label)
        label.alpha = if (shouldAllowPrimaryLabel) 1f else 0f
        secondaryLabel = labelContainer.requireViewById(R.id.app_label)
        if (!shouldShowSecondaryLabel) {
            secondaryLabel.alpha = 0f
            if (!usenew2) {
                labelContainer.ignoreLastView = true
                secondaryLabel.measure(View.MeasureSpec.getMode(View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.getMode(View.MeasureSpec.UNSPECIFIED))
                val mlp = (secondaryLabel.layoutParams as LinearLayout.LayoutParams)
                mlp.setMargins(0, 0, 0, -1 * secondaryLabel.measuredHeight)
                secondaryLabel.layoutParams = mlp
                // Ideally, it'd be great if the parent could set this up when measuring just this child
                // instead of the View class having to support this. However, due to the mysteries of
                // LinearLayout's double measure pass, we cannot overwrite `measureChild` or any of its
                // sibling methods to have special behavior for labelContainer.
                labelContainer.forceUnspecifiedMeasure = true
            }
        }
        setLabelColor(getLabelColorForState(lastState))
        setSecondaryLabelColor(getSecondaryLabelColorForState(lastState))
        if (usenew2) {
            addView(labelContainer)
        } else {
            l!!.addView(labelContainer)
        }
    }

    override fun createAndAddSideView() {
        if (allowIconChevron) {
            sideView = LayoutInflater.from(context)
                .inflate(R.layout.qs_tile_side_icon, this, false) as ViewGroup
            sideView.setPaddingRelative(context.resources.getDimensionPixelSize(R.dimen.new_qs_sideview_pad), context.resources.getDimensionPixelSize(R.dimen.new_qs_sideview_downpad), 0, 0)
            sideViewSpacer = null
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.RIGHT_OF, _icon.id)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            subl!!.addView(sideView, lp)
        } else {
            sideView = labelContainer.requireViewById(R.id.sideView)
            sideViewSpacer = sideView.requireViewById(R.id.sideViewSpacer)
        }
        chevronView = sideView.requireViewById(R.id.chevron)
        customDrawableView = sideView.requireViewById(R.id.customDrawable)
        setChevronColor(getChevronColorForState(QSTile.State.DEFAULT_STATE))
    }

    override fun setClickable(clickable: Boolean) {
        super.setClickable(clickable)
        l!!.background = if (clickable && showRippleEffect) {
            ripple.also {
                // In case that the colorBackgroundDrawable was used as the background, make sure
                // it has the correct callback instead of null
                colorBackgroundDrawable.callback = it
            }
        } else {
            colorBackgroundDrawable
        }
    }

    override fun loadSideViewDrawableIfNecessary(state: QSTile.State) {
        val labelIconBox: LinearLayout = labelContainer.requireViewById(R.id.label_icon_box)
        if (allowLabelChevron && balanceLabelChevron)
            labelIconBox.setPaddingRelative(context.resources.getDimensionPixelSize(R.dimen.new_qs_icon_size), 0, 0, 0)
        else
            labelIconBox.setPaddingRelative(0, 0, 0, 0)
        sideViewSpacer?.visibility = if (allowLabelChevron && balanceLabelChevron) VISIBLE else GONE
        if (allowChevron && state.sideViewCustomDrawable != null) {
            customDrawableView.setImageDrawable(state.sideViewCustomDrawable)
            customDrawableView.visibility = VISIBLE
            chevronView.visibility = GONE
            if (allowLabelChevron && !balanceLabelChevron && inverseBalanceLabelChevron)
                labelIconBox.setPaddingRelative(0, 0, context.resources.getDimensionPixelSize(R.dimen.new_qs_icon_size_inverted), 0)
        } else if (allowChevron && (state !is BooleanState || state.forceExpandIcon)) {
            customDrawableView.setImageDrawable(null)
            customDrawableView.visibility = GONE
            chevronView.visibility = VISIBLE
            if (allowLabelChevron && !balanceLabelChevron && inverseBalanceLabelChevron)
                labelIconBox.setPaddingRelative(0, 0, context.resources.getDimensionPixelSize(R.dimen.new_qs_icon_size_inverted), 0)
        } else {
            customDrawableView.setImageDrawable(null)
            customDrawableView.visibility = GONE
            chevronView.visibility = GONE
        }
    }

    override fun getLabelColorForState(state: Int, disabledByPolicy: Boolean): Int {
        return when {
            usenew2 && (state == Tile.STATE_UNAVAILABLE || disabledByPolicy) -> colorLabelUnavailable
            usenew2 -> colorLabelInactive
            else -> super.getLabelColorForState(state, disabledByPolicy)
        }
    }

    override fun getSecondaryLabelColorForState(state: Int, disabledByPolicy: Boolean): Int {
        return when {
            usenew2 && (state == Tile.STATE_UNAVAILABLE || disabledByPolicy) -> colorSecondaryLabelUnavailable
            usenew2 -> colorSecondaryLabelInactive
            else -> super.getSecondaryLabelColorForState(state, disabledByPolicy)
        }
    }

    override fun handleStateChanged(state: QSTile.State) {
        super.handleStateChanged(state)
        secondaryLabel.visibility = VISIBLE
    }

    override fun setLabelColor(color: Int) {
        label.setTextColor(color)
        label.alpha = if (shouldAllowPrimaryLabel) 1f else 0f
    }

    override fun setSecondaryLabelColor(color: Int) {
        secondaryLabel.setTextColor(color)
        secondaryLabel.alpha = if (shouldShowSecondaryLabel) 1f else 0f
    }

    //todo: check if neccessary
    override fun getDetailY(): Int {
        if (!usenew2) return super.getDetailY()
        return top + labelContainer.top + labelContainer.height / 2
    }

    override fun updateHeight() {} // Do nothing ;)
}
