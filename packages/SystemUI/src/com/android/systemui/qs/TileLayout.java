/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.qs;

import static com.android.systemui.util.Utils.useQsMediaPlayer;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import com.android.internal.logging.UiEventLogger;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.MultiQSTile;
import com.android.systemui.qs.QSPanel.QSTileLayout;
import com.android.systemui.qs.QSPanelControllerBase.TileRecord;
import com.android.systemui.qs.tileimpl.HeightOverrideable;
import com.android.systemui.qs.tileimpl.QSTileViewImplBig;
import com.android.systemui.qs.tileimpl.QSTileViewImplKt;
import com.android.systemui.qs.tileimpl.QSTileViewImplNew;

import java.util.ArrayList;
import java.util.LinkedList;

public class TileLayout extends ViewGroup implements QSTileLayout, Revealable {

    public static final int NO_MAX_COLUMNS = 100;

    private static final String TAG = "TileLayout";

    protected int mColumns;
    protected int mCellWidth;
    protected int mCellHeightResId = R.dimen.qs_tile_height;
    protected int mCellHeight;
    protected int mMaxCellHeight;
    protected int mCellMarginHorizontal;
    protected int mCellMarginVertical;
    protected int mSidePadding;
    protected int mRows = 1;

    protected final ArrayList<TileRecord> mRecords = new ArrayList<>();
    protected boolean mListening;
    protected int mMaxAllowedRows = 3;

    // Prototyping with less rows
    protected boolean mLessRows;
    private int mMinRows = 1;
    protected int mMaxColumns = NO_MAX_COLUMNS;
    protected int mResourceColumns;
    private float mSquishinessFraction = 1f;
    protected int mLastTileBottom;

    public TileLayout(Context context) {
        this(context, null);
    }

    public TileLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusableInTouchMode(true);
        mLessRows = !NewQsHelper.shouldDisallowDynamicQsRow(context) &&
            ((Settings.System.getInt(context.getContentResolver(), "qs_less_rows", 0) != 0)
                || useQsMediaPlayer(context, true));
        updateResources();
    }

    @Override
    public int getOffsetTop(TileRecord tile) {
        return getTop();
    }

    public void setListening(boolean listening) {
        setListening(listening, null);
    }

    @Override
    public void setListening(boolean listening, @Nullable UiEventLogger uiEventLogger) {
        if (mListening == listening) return;
        mListening = listening;
        for (TileRecord record : mRecords) {
            record.tile.setListening(this, mListening);
        }
    }

    @Override
    public boolean setMinRows(int minRows) {
        if (mMinRows != minRows) {
            mMinRows = minRows;
            updateResources();
            return true;
        }
        return false;
    }

    @Override
    public boolean setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
        return updateColumns();
    }

    public void addTile(TileRecord tile) {
        mRecords.add(tile);
        tile.tile.setListening(this, mListening);
        addTileView(tile);
    }

    protected void addTileView(TileRecord tile) {
        addView(tile.tileView);
    }

    @Override
    public void removeTile(TileRecord tile) {
        mRecords.remove(tile);
        tile.tile.setListening(this, false);
        removeView(tile.tileView);
    }

    public void removeAllViews() {
        for (TileRecord record : mRecords) {
            record.tile.setListening(this, false);
        }
        mRecords.clear();
        super.removeAllViews();
    }

    public boolean updateResources() {
        final Resources res = mContext.getResources();
        mResourceColumns = Math.max(1, NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQsColumnCountForCurrentOrientation(mContext) : res.getInteger(R.integer.quick_settings_num_columns));
        mMaxCellHeight = mContext.getResources().getDimensionPixelSize(mCellHeightResId);
        mCellMarginHorizontal = res.getDimensionPixelSize(R.dimen.qs_tile_margin_horizontal);
        mSidePadding = useSidePadding() ? mCellMarginHorizontal / 2 : 0;
        mCellMarginVertical= res.getDimensionPixelSize(R.dimen.qs_tile_margin_vertical);
        mMaxAllowedRows = Math.max(1, NewQsHelper.shouldDisallowDynamicQsRow(mContext) ? Integer.MAX_VALUE
            : getResources().getInteger(R.integer.quick_settings_max_rows));
        if (mLessRows) mMaxAllowedRows = Math.max(mMinRows, mMaxAllowedRows - 1);
        if (updateColumns()) {
            requestLayout();
            return true;
        }
        return false;
    }

    protected boolean useSidePadding() {
        return true;
    }

    protected boolean updateColumns() {
        int oldColumns = mColumns;
        mColumns = Math.min(mResourceColumns, mMaxColumns);
        return oldColumns != mColumns;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mColumns < 1) {
            setMeasuredDimension(0, 0);
            return;
        }
        // If called with AT_MOST, it will limit the number of rows. If called with UNSPECIFIED
        // it will show all its tiles. In this case, the tiles have to be entered before the
        // container is measured. Any change in the tiles, should trigger a remeasure.
        final int numTiles = mRecords.size();
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int availableWidth = width - getPaddingStart() - getPaddingEnd();
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int gaps = mColumns - 1;
        int numSlots = 0;
        mCellWidth =
                (availableWidth - (mCellMarginHorizontal * gaps) - mSidePadding * 2) / mColumns;

        // Measure each QS tile.
        View previousView = this;
        int verticalMeasure = getCellHeight();
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            final int rowsWithAnchor = (record.tile instanceof MultiQSTile) ? ((MultiQSTile) record.tile).getRowsConsumed() : 1;
            final int columnsWithAnchor = (record.tile instanceof MultiQSTile) ? ((MultiQSTile) record.tile).getColumnsConsumed() : 1;
            if (record.tileView instanceof QSTileViewImplNew && rowsWithAnchor == 1 && columnsWithAnchor == 1) {
                // Multi tile must never be round, but 1x1 may. Configure it.
                QSTileViewImplNew tileViewImplNew = (QSTileViewImplNew) record.tileView;
                tileViewImplNew.setIsNew2(NewQsHelper.shouldBeRoundTile(mContext));
            }
            if (record.tileView instanceof QSTileViewImplBig) {
                QSTileViewImplBig tileViewImplBig = (QSTileViewImplBig) record.tileView;
                tileViewImplBig.setSize(columnsWithAnchor, rowsWithAnchor, mCellMarginHorizontal, mCellMarginVertical);
            }
            record.tileView.measure(exactly(mCellWidth * columnsWithAnchor + mCellMarginHorizontal * (columnsWithAnchor - 1)),
                                exactly(verticalMeasure * rowsWithAnchor + mCellMarginVertical * (rowsWithAnchor - 1)));
            previousView = record.tileView.updateAccessibilityOrder(previousView);
            if (rowsWithAnchor == 1) mCellHeight = record.tileView.getMeasuredHeight();
            numSlots += rowsWithAnchor * columnsWithAnchor;
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            mRows = (numSlots + mColumns - 1) / mColumns;
        }

        int height = (mCellHeight + mCellMarginVertical) * mRows;
        height -= mCellMarginVertical;

        if (height < 0) height = 0;

        setMeasuredDimension(width, height);
    }

    /**
     * Determines the maximum number of rows that can be shown based on height. Clips at a minimum
     * of 1 and a maximum of mMaxAllowedRows.
     *
     * @param allowedHeight The height this view has visually available
     * @param tilesCount Upper limit on the number of tiles to show. to prevent empty rows.
     */
    public boolean updateMaxRows(int allowedHeight, int tilesCount) {
        // Add the cell margin in order to divide easily by the height + the margin below
        final int availableHeight =  allowedHeight + mCellMarginVertical;
        final int previousRows = mRows;
        if (!NewQsHelper.shouldDisallowDynamicQsRow(mContext))
            mRows = availableHeight / (getCellHeight() + mCellMarginVertical);
        else
            mRows = NewQsHelper.getQsRowCountForCurrentOrientation(mContext);
        if (mRows < mMinRows) {
            mRows = mMinRows;
        } else if (mRows >= mMaxAllowedRows) {
            mRows = mMaxAllowedRows;
        }
        if (mColumns > 0) {
            if (mRows > (tilesCount + mColumns - 1) / mColumns) {
                mRows = (tilesCount + mColumns - 1) / mColumns;
            }
        }
        return previousRows != mRows;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    protected static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    protected int getCellHeight() {
        return mMaxCellHeight;
    }

    private void layoutTileRecords(int numRecords, boolean forLayout) {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int row = 0;
        int column = 0;
        mLastTileBottom = 0;
        LinkedList<Integer> /* mostly used as Queue */ offsets = new LinkedList<Integer>();

        // Layout each QS tile.
        final int tilesToLayout = Math.min(numRecords, mRows * mColumns);
        for (int i = 0; i < tilesToLayout; column++) {
            // If we reached the last column available to layout a tile, wrap back to the next row.
            if (column == mColumns) {
                column = 0;
                if (offsets.size() > 0) offsets.removeFirst();
                row++;
            }
            if (offsets.size() > 0 && (offsets.get(0) & (int) Math.pow(2, column)) > 0)
                continue;
            final TileRecord record = mRecords.get(i++);

            final int rowsWithAnchor = (record.tile instanceof MultiQSTile) ? ((MultiQSTile) record.tile).getRowsConsumed() : 1;
            final int columnsWithAnchor = (record.tile instanceof MultiQSTile) ? ((MultiQSTile) record.tile).getColumnsConsumed() : 1;

            for (int j = 0; j < rowsWithAnchor; j++) {
                int offset = 0;
                if (offsets.size() > j) offset = offsets.get(j);
                for (int k = 0; k < columnsWithAnchor; k++) {
                    offset |= (int) Math.pow(2, column + k);
                }
                if (offsets.size() > j)
                    offsets.set(j, offset);
                else {
                    if (offsets.size() != j) {
                        for (int l = offsets.size(); l < j; l++) {
                            offsets.addLast(0);
                        }
                    }
                    offsets.addLast(offset);
                }
            }

            final int top = getRowTop(row);
            final int left = getColumnStart(isRtl ? mColumns - column - 1 : column);
            final int right = left + (mCellWidth * columnsWithAnchor) + (mCellMarginHorizontal * (columnsWithAnchor - 1));
            final int bottom = top + record.tileView.getMeasuredHeight();
            if (forLayout) {
                record.tileView.layout(left, top, right, bottom);
            } else {
                record.tileView.setLeftTopRightBottom(left, top, right, bottom);
            }
            record.tileView.setPosition(i);

            // Set the bottom to the unoverriden squished bottom. This is to avoid fake bottoms that
            // are only used for QQS -> QS expansion animations
            float scale = QSTileViewImplKt.constrainSquishiness(mContext, mSquishinessFraction);
            mLastTileBottom = Math.max(mLastTileBottom, top + (int) (record.tileView.getMeasuredHeight() * scale));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutTileRecords(mRecords.size(), true /* forLayout */);
    }

    protected int getRowTop(int row) {
        float scale = QSTileViewImplKt.constrainSquishiness(mContext, mSquishinessFraction);
        return (int) (row * (mCellHeight * scale + mCellMarginVertical));
    }

    protected int getColumnStart(int column) {
        return getPaddingStart() + mSidePadding
                + column *  (mCellWidth + mCellMarginHorizontal);
    }

    @Override
    public int getNumVisibleTiles() {
        if (getNumColumns() == 0) return 0;
        return mRecords.size();
    }

    public int getNumColumns() {
        return mColumns;
    }

    public boolean isFull() {
        return false;
    }

    /**
     * @return The maximum number of tiles this layout can hold
     */
    public int maxTiles() {
        return Math.max(mColumns * mRows, 0);
    }

    @Override
    public int getTilesHeight() {
        return mLastTileBottom + getPaddingBottom();
    }

    @Override
    public void setSquishinessFraction(float squishinessFraction) {
        if (Float.compare(mSquishinessFraction, squishinessFraction) == 0) {
            return;
        }
        mSquishinessFraction = squishinessFraction;
        layoutTileRecords(mRecords.size(), false /* forLayout */);

        for (TileRecord record : mRecords) {
            if (record.tileView instanceof HeightOverrideable) {
                ((HeightOverrideable) record.tileView).setSquishinessFraction(mSquishinessFraction);
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoInternal(info);
        info.setCollectionInfo(
                new AccessibilityNodeInfo.CollectionInfo(mRecords.size(), 1, false));
    }
}
