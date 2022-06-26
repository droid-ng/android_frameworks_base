/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 droid-ng
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.customize;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.State;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.internal.logging.UiEventLogger;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.MultiQSTile;
import com.android.systemui.qs.NewQsHelper;
import com.android.systemui.qs.QSEditEvent;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.customize.TileAdapter.Holder;
import com.android.systemui.qs.customize.TileQueryHelper.TileInfo;
import com.android.systemui.qs.customize.TileQueryHelper.TileStateListener;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.qs.dagger.QSThemedContext;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.tileimpl.QSIconViewImpl;
import com.android.systemui.qs.tileimpl.QSTileViewImpl;
import com.android.systemui.toast.SystemUIToast;
import com.android.systemui.toast.ToastFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;

/** */
@QSScope
public class TileAdapter extends RecyclerView.Adapter<Holder> implements TileStateListener {
    private static final long DRAG_LENGTH = 100;
    private static final float DRAG_SCALE = 1.2f;
    public static final long MOVE_DURATION = 150;

    private static final int TYPE_TILE = 0;
    private static final int TYPE_EDIT = 1;
    private static final int TYPE_ACCESSIBLE_DROP = 2;
    private static final int TYPE_HEADER = 3;
    private static final int TYPE_DIVIDER = 4;
    private static final int TYPE_TILE2 = 5;

    private static final long EDIT_ID = 10000;
    private static final long DIVIDER_ID = 20000;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_ADD = 1;
    private static final int ACTION_MOVE = 2;

    private static final int NUM_COLUMNS_ID = R.integer.quick_settings_num_columns;

    private static final float TOAST_PARAMS_HORIZONTAL_WEIGHT = 1.0f;
    private static final float TOAST_PARAMS_VERTICAL_WEIGHT = 1.0f;
    private static final long SHORT_DURATION_TIMEOUT = 4000;

    private final Context mContext;
    private WindowManager mWindowManager;
    private ToastFactory mToastFactory;


    private final Handler mHandler = new Handler();
    private final List<TileInfo> mTiles = new ArrayList<>();
    private final ItemTouchHelper mItemTouchHelper;
    private ItemDecoration mDecoration;
    private final int mMinNumTiles;
    private final QSTileHost mHost;
    private int mEditIndex;
    private int mTileDividerIndex;
    private int mFocusIndex;

    private boolean mNeedsFocus;
    @Nullable
    private List<String> mCurrentSpecs;
    @Nullable
    private List<TileInfo> mOtherTiles;
    @Nullable
    private List<TileInfo> mAllTiles;

    @Nullable
    private Holder mCurrentDrag;
    private int mAccessibilityAction = ACTION_NONE;
    private int mAccessibilityFromIndex;
    private final UiEventLogger mUiEventLogger;
    //private final AccessibilityDelegateCompat mAccessibilityDelegate;
    @Nullable
    private RecyclerView mRecyclerView;
    private int mNumColumns;
    private int columns2;
    private int columns3;
    private int columns4;
    private int qqsRows1;
    private int qqsRows2;
    private int rowsPerPage1; // set to -1 for infinite
    private int rowsPerPage2; // same thing but for secondary orientation

    @Inject
    public TileAdapter(
            @QSThemedContext Context context,
            QSTileHost qsHost,
            WindowManager windowManager,
            ToastFactory toastFactory,
            UiEventLogger uiEventLogger) {
        mContext = context;
        mHost = qsHost;
        mUiEventLogger = uiEventLogger;
        mWindowManager = windowManager;
        mToastFactory = toastFactory;
        mItemTouchHelper = new ItemTouchHelper(mCallbacks);
        mDecoration = new TileItemDecoration(context);
        mMinNumTiles = context.getResources().getInteger(R.integer.quick_settings_min_num_tiles);
        mNumColumns = -1;
        updateNumColumns();
        //mAccessibilityDelegate = new TileAdapterDelegate();
        //mSizeLookup.setSpanIndexCacheEnabled(true);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = null;
    }

    /**
     * Update the number of columns to show, from resources.
     *
     * @return {@code true} if the number of columns changed, {@code false} otherwise
     */
    public boolean updateNumColumns() {
        Configuration cfg = mContext.getResources().getConfiguration();
        Configuration myCfg = new Configuration(cfg);
        myCfg.orientation = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE ?
            Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
        Context otherCtx = mContext.createConfigurationContext(myCfg);
        int columns1 = NewQsHelper.shouldDisallowDynamicQsRow(mContext)
            ? NewQsHelper.getQsColumnCountForCurrentOrientation(mContext)
            : mContext.getResources().getInteger(NUM_COLUMNS_ID);
        columns2 = NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQsColumnCountForCurrentOrientation(otherCtx) :
            otherCtx.getResources().getInteger(NUM_COLUMNS_ID);
        columns3 = NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQqsColumnCountForCurrentOrientation(mContext) :
            Math.min(mNumColumns, 4);
        columns4 = NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQqsColumnCountForCurrentOrientation(otherCtx) :
            Math.min(columns2, 4);
        qqsRows1 = NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQqsRowCountForCurrentOrientation(mContext) :
            Math.min(columns3 > 0 ? mContext.getResources().getInteger(R.integer.quick_qs_panel_max_tiles) / columns3 : 0,
            mContext.getResources().getInteger(R.integer.quick_qs_panel_max_rows));
        qqsRows2 = NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQqsRowCountForCurrentOrientation(otherCtx) :
            Math.min(columns4 > 0 ? otherCtx.getResources().getInteger(R.integer.quick_qs_panel_max_tiles) / columns4 : 0,
            otherCtx.getResources().getInteger(R.integer.quick_qs_panel_max_rows));
        rowsPerPage1 = NewQsHelper.needVerticalScroll(mContext) ? -1 :
            (NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQsRowCountForCurrentOrientation(mContext) :
            mContext.getResources().getInteger(R.integer.quick_settings_max_rows));
        rowsPerPage2 = NewQsHelper.needVerticalScroll(mContext) ? -1 :
            (NewQsHelper.shouldDisallowDynamicQsRow(mContext) ?
            NewQsHelper.getQsRowCountForCurrentOrientation(otherCtx) :
            otherCtx.getResources().getInteger(R.integer.quick_settings_max_rows));
        if (columns1 != mNumColumns) {
            mNumColumns = columns1;
            return true;
        } else {
            return false;
        }
    }

    public int getNumColumns() {
        return mNumColumns;
    }

    public ItemTouchHelper getItemTouchHelper() {
        return mItemTouchHelper;
    }

    public ItemDecoration getItemDecoration() {
        return mDecoration;
    }

    public void saveSpecs(QSTileHost host) {
        List<String> newSpecs = new ArrayList<>();
        clearAccessibilityState();
        for (int i = 1; i < mTiles.size() && mTiles.get(i) != null; i++) {
            newSpecs.add(mTiles.get(i).spec);
        }
        host.changeTilesByUser(mCurrentSpecs, newSpecs);
        mCurrentSpecs = newSpecs;
    }

    private void clearAccessibilityState() {
        mNeedsFocus = false;
        if (mAccessibilityAction == ACTION_ADD) {
            // Remove blank tile from last spot
            mTiles.remove(--mEditIndex);
            // Update the tile divider position
            notifyDataSetChanged();
        }
        mAccessibilityAction = ACTION_NONE;
    }

    /** */
    public void resetTileSpecs(List<String> specs) {
        // Notify the host so the tiles get removed callbacks.
        mHost.changeTilesByUser(mCurrentSpecs, specs);
        setTileSpecs(specs);
    }

    public void setTileSpecs(List<String> currentSpecs) {
        if (currentSpecs.equals(mCurrentSpecs)) {
            return;
        }
        mCurrentSpecs = currentSpecs;
        recalcSpecs();
    }

    @Override
    public void onTilesChanged(List<TileInfo> tiles) {
        mAllTiles = tiles;
        recalcSpecs();
    }

    private void recalcSpecs() {
        if (mCurrentSpecs == null || mAllTiles == null) {
            return;
        }
        mOtherTiles = new ArrayList<TileInfo>(mAllTiles);
        mTiles.clear();
        mTiles.add(null);
        for (int i = 0; i < mCurrentSpecs.size(); i++) {
            final TileInfo tile = getAndRemoveOther(mCurrentSpecs.get(i));
            if (tile != null) {
                mTiles.add(tile);
            }
        }
        mTiles.add(null);
        for (int i = 0; i < mOtherTiles.size(); i++) {
            final TileInfo tile = mOtherTiles.get(i);
            if (tile.isSystem) {
                mOtherTiles.remove(i--);
                mTiles.add(tile);
            }
        }
        mTileDividerIndex = mTiles.size();
        mTiles.add(null);
        mTiles.addAll(mOtherTiles);
        updateDividerLocations();
        notifyDataSetChanged();
    }

    @Nullable
    private TileInfo getAndRemoveOther(String s) {
        for (int i = 0; i < mOtherTiles.size(); i++) {
            if (mOtherTiles.get(i).spec.equals(s)) {
                return mOtherTiles.remove(i);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        }
        if (mAccessibilityAction == ACTION_ADD && position == mEditIndex - 1) {
            return TYPE_ACCESSIBLE_DROP;
        }
        if (position == mTileDividerIndex) {
            return TYPE_DIVIDER;
        }
        if (mTiles.get(position) == null) {
            return TYPE_EDIT;
        }
        if (findTileRet(mTiles.get(position).spec, tile -> (
            tile instanceof MultiQSTile ?
            (((MultiQSTile) tile).getColumnsConsumed() > 1) || (((MultiQSTile) tile).getRowsConsumed() > 1)
            : false))) {
            return TYPE_TILE2;
        }
        return TYPE_TILE;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        final Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.qs_customize_header, parent, false);
            v.setMinimumHeight(calculateHeaderMinHeight(context));
            return new Holder(v);
        }
        if (viewType == TYPE_DIVIDER) {
            return new Holder(inflater.inflate(R.layout.qs_customize_tile_divider, parent, false));
        }
        if (viewType == TYPE_EDIT) {
            return new Holder(inflater.inflate(R.layout.qs_customize_divider, parent, false));
        }
        FrameLayout frame = (FrameLayout) inflater.inflate(R.layout.qs_customize_tile_frame, parent,
                false);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        View view;
        if (viewType == TYPE_TILE2)
            view = new CustomizeTileViewBig(context, new QSIconViewImpl(context));
        else if (NewQsHelper.isAnyTypeOfNewQs(context))
            view = new CustomizeTileViewNew(context, new QSIconViewImpl(context));
        else
            view = new CustomizeTileViewReal(context, new QSIconViewImpl(context));
        if (view instanceof CustomizeTileViewNew && !(view instanceof CustomizeTileViewBig)) {
            ((CustomizeTileViewNew) view).setIsNew2(NewQsHelper.shouldBeRoundTile(context));
        }
        frame.addView(view);
        return new Holder(frame);
    }

    @Override
    public int getItemCount() {
        return mTiles.size();
    }

    @Override
    public boolean onFailedToRecycleView(Holder holder) {
        holder.stopDrag();
        holder.clearDrag();
        return true;
    }

    private void setSelectableForHeaders(View view) {
        final boolean selectable = mAccessibilityAction == ACTION_NONE;
        view.setFocusable(selectable);
        view.setImportantForAccessibility(selectable
                ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setFocusableInTouchMode(selectable);
    }

    @Override
    public void onBindViewHolder(final Holder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) {
            setSelectableForHeaders(holder.itemView);
            return;
        }
        if (holder.getItemViewType() == TYPE_DIVIDER) {
            holder.itemView.setVisibility(mTileDividerIndex < mTiles.size() - 1 ? View.VISIBLE
                    : View.INVISIBLE);
            return;
        }
        if (holder.getItemViewType() == TYPE_EDIT) {
            final String titleText;
            Resources res = mContext.getResources();
            if (mCurrentDrag == null) {
                titleText = res.getString(R.string.drag_or_tap_to_add_tiles);
            } else if (!canRemoveTiles() && mCurrentDrag.getAdapterPosition() < mEditIndex) {
                titleText = res.getString(R.string.drag_to_remove_disabled, mMinNumTiles);
            } else {
                titleText = res.getString(R.string.drag_to_remove_tiles);
            }

            ((TextView) holder.itemView.findViewById(android.R.id.title)).setText(titleText);
            setSelectableForHeaders(holder.itemView);

            return;
        }
        if (holder.getItemViewType() == TYPE_ACCESSIBLE_DROP) {
            holder.mTileView.setClickable(true);
            holder.mTileView.setFocusable(true);
            holder.mTileView.setFocusableInTouchMode(true);
            holder.mTileView.setVisibility(View.VISIBLE);
            holder.mTileView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            holder.mTileView.setContentDescription(mContext.getString(
                    R.string.accessibility_qs_edit_tile_add_to_position, position));
            holder.mTileView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectPosition(holder.getLayoutPosition());
                }
            });
            focusOnHolder(holder);
            return;
        }

        TileInfo info = mTiles.get(position);

        final boolean selectable = 0 < position && position < mEditIndex;
        if (selectable && mAccessibilityAction == ACTION_ADD) {
            info.state.contentDescription = mContext.getString(
                    R.string.accessibility_qs_edit_tile_add_to_position, position);
        } else if (selectable && mAccessibilityAction == ACTION_MOVE) {
            info.state.contentDescription = mContext.getString(
                    R.string.accessibility_qs_edit_tile_move_to_position, position);
        } else {
            info.state.contentDescription = info.state.label;
        }
        info.state.expandedAccessibilityClassName = "";

        CustomizeTileView tileView =
                Objects.requireNonNull(
                        holder.getTileAsCustomizeView(), "The holder must have a tileView");
        if (holder.getItemViewType() == TYPE_TILE2) {
            final Pair consumed = findTileRet(info.spec, tile ->
                new Pair<>(tile instanceof MultiQSTile ? ((MultiQSTile) tile).getRowsConsumed() : 1,
                    tile instanceof MultiQSTile ? ((MultiQSTile) tile).getColumnsConsumed() : 1));
            int rowsConsumed = (Integer) consumed.first;
            int columnsConsumed = (Integer) consumed.second;
            ((CustomizeTileViewBig) tileView).setSize(rowsConsumed, columnsConsumed, 0, 0);
        }
        tileView.changeState(info.state);
        tileView.setShowAppLabel(position > mEditIndex && !info.isSystem);
        // Don't show the side view for third party tiles, as we don't have the actual state.
        tileView.setShowSideView(!NewQsHelper.shouldBeRoundTile(mContext) && (position < mEditIndex || info.isSystem));
        holder.mTileView.setSelected(true);
        /*holder.mTileView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        holder.mTileView.setClickable(true);
        holder.mTileView.setOnClickListener(null);
        holder.mTileView.setFocusable(true);
        holder.mTileView.setFocusableInTouchMode(true);*/

        holder.mTileView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = holder.getLayoutPosition();
                boolean b = (position >= mEditIndex || canRemoveTiles()) && move(position, mEditIndex);
                if (!b) {
                    AlertDialog dialog = new AlertDialog.Builder(mContext)
                        .setTitle(R.string.qs_cant_add_tile)
                        .setMessage(R.string.qs_cant_add_tile_msg)
                        .setPositiveButton(R.string.ok, (d, i) -> d.dismiss())
                        .create();
                    SystemUIDialog.applyFlags(dialog);
                    SystemUIDialog.registerDismissListener(dialog);
                    dialog.show();
                }
            }
        });

        /*if (mAccessibilityAction != ACTION_NONE) {
            holder.mTileView.setClickable(selectable);
            holder.mTileView.setFocusable(selectable);
            holder.mTileView.setFocusableInTouchMode(selectable);
            holder.mTileView.setImportantForAccessibility(selectable
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (selectable) {
                holder.mTileView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int position = holder.getLayoutPosition();
                        if (position == RecyclerView.NO_POSITION) return;
                        if (mAccessibilityAction != ACTION_NONE) {
                            selectPosition(position);
                        }
                    }
                });
            }
        }
        if (position == mFocusIndex) {
            focusOnHolder(holder);
        }*/
    }

    private void focusOnHolder(Holder holder) {
        if (mNeedsFocus) {
            // Wait for this to get laid out then set its focus.
            // Ensure that tile gets laid out so we get the callback.
            holder.mTileView.requestLayout();
            holder.mTileView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    holder.mTileView.removeOnLayoutChangeListener(this);
                    holder.mTileView.requestAccessibilityFocus();
                }
            });
            mNeedsFocus = false;
            mFocusIndex = RecyclerView.NO_POSITION;
        }
    }

    private boolean canRemoveTiles() {
        return mCurrentSpecs.size() > mMinNumTiles;
    }

    private void selectPosition(int position) {
        if (mAccessibilityAction == ACTION_ADD) {
            // Remove the placeholder.
            mTiles.remove(mEditIndex--);
        }
        mAccessibilityAction = ACTION_NONE;
        move(mAccessibilityFromIndex, position, false);
        mFocusIndex = position;
        mNeedsFocus = true;
        notifyDataSetChanged();
    }

    private void startAccessibleAdd(int position) {
        mAccessibilityFromIndex = position;
        mAccessibilityAction = ACTION_ADD;
        // Add placeholder for last slot.
        mTiles.add(mEditIndex++, null);
        // Update the tile divider position
        mTileDividerIndex++;
        mFocusIndex = mEditIndex - 1;
        final int focus = mFocusIndex;
        mNeedsFocus = true;
        if (mRecyclerView != null) {
            mRecyclerView.post(() -> {
                final RecyclerView recyclerView = mRecyclerView;
                if (recyclerView != null) {
                    recyclerView.smoothScrollToPosition(focus);
                }
            });
        }
        notifyDataSetChanged();
    }

    private void startAccessibleMove(int position) {
        mAccessibilityFromIndex = position;
        mAccessibilityAction = ACTION_MOVE;
        mFocusIndex = position;
        mNeedsFocus = true;
        notifyDataSetChanged();
    }

    private boolean canRemoveFromPosition(int position) {
        return canRemoveTiles() && isCurrentTile(position);
    }

    private boolean isCurrentTile(int position) {
        return position < mEditIndex;
    }

    private boolean canAddFromPosition(int position) {
        return position > mEditIndex;
    }

    private boolean addFromPosition(int position) {
        if (!canAddFromPosition(position)) return false;
        move(position, mEditIndex);
        return true;
    }

    private boolean removeFromPosition(int position) {
        if (!canRemoveFromPosition(position)) return false;
        TileInfo info = mTiles.get(position);
        move(position, info.isSystem ? mEditIndex : mTileDividerIndex);
        return true;
    }

    public SpannedGridLayoutManager.GridSpanLookup getSizeLookup() {
        return mSizeLookup;
    }

    private boolean move(int from, int to) {
        return move(from, to, true);
    }

    private boolean move(int from, int to, boolean notify) {
        if (to == from) {
            return true;
        }
        ArrayList<Pair<Integer, Integer>> moveStrategy = new ArrayList<>();
        boolean canMove = true;
        if (notify) {
            canMove &= mCallbacks.canDropOver(mRecyclerView, mRecyclerView.findViewHolderForAdapterPosition(from),
                    mRecyclerView.findViewHolderForAdapterPosition(to));
            if (canMove) {
                moveStrategy.add(new Pair<>(from, to));
                // Now reorder all 1x1 tiles to fix layout
                ArrayList<Pair<Integer, Pair<Integer, Integer>>> tileStack = new ArrayList<>();
                for (int i = 1; i < (to > mEditIndex || (from < mEditIndex && to == mEditIndex)
                    ? mEditIndex - 1 : (from > mEditIndex ? mEditIndex + 1 : mEditIndex)); i++) {
                    int oldPosition = i;
                    for (Pair<Integer, Integer> p : moveStrategy) {
                        // Because the move() calls are one after another, the order shuffles around. Get pos BEFORE moving
                        oldPosition = oldPosition == p.second ? p.first : (p.first < p.second ?
                            (oldPosition < p.first ? oldPosition : (oldPosition > p.second ? oldPosition : oldPosition + 1))
                            : (oldPosition < p.second ? oldPosition : (oldPosition > p.first ? oldPosition : oldPosition - 1)));
                    }
                    final String spec = mTiles.get(oldPosition).spec;
                    final Pair consumed = findTileRet(spec, tile ->
                        new Pair<>(tile instanceof MultiQSTile ? ((MultiQSTile) tile).getRowsConsumed() : 1,
                            tile instanceof MultiQSTile ? ((MultiQSTile) tile).getColumnsConsumed() : 1));
                    tileStack.add(new Pair<>(oldPosition, consumed));
                }
                int column = 0;
                int row = 0;
                int lastPlacedPos = 1;
                LinkedList<Integer> /* mostly used as Queue */ offsets = new LinkedList<Integer>();
                while (tileStack.size() > 0) {
                    if (column >= mNumColumns) {
                        row++;
                        if (offsets.size() > 0) offsets.removeFirst();
                        column = 0;
                    }
                    if (offsets.size() > 0 && (offsets.get(0) & (int) Math.pow(2, column)) > 0) {
                        column++;
                        continue;
                    }

                    int rowsWithAnchor = 1, columnsWithAnchor = 1, oldPosition = -1, i = 0;
                    boolean fits = false;
                    while (!fits) {
                        fits = true;
                        Pair<Integer, Pair<Integer, Integer>> tile = tileStack.get(i++);
                        oldPosition = tile.first;
                        rowsWithAnchor = tile.second.first;
                        columnsWithAnchor = tile.second.second;
                        fits &= column + (columnsWithAnchor - 1) < mNumColumns;
                        if (fits) {
                            for (int j = 0; j < rowsWithAnchor; j++) {
                                for (int k = 0; k < columnsWithAnchor; k++) {
                                    fits &= !(offsets.size() > j && (offsets.get(j) & (int) Math.pow(2, column + k)) > 0);
                                }
                            }
                        }
                        if (fits) {
                            // QQS, primary orientation
                            fits &= validateLocation(column, row, columns3, qqsRows1, columnsWithAnchor, rowsWithAnchor, true);
                            // QQS, secondary orientation
                            fits &= validateLocation(column, row, columns4, qqsRows2, columnsWithAnchor, rowsWithAnchor, true);
                            // QS, primary orientation
                            fits &= validateLocation(column, row, mNumColumns, rowsPerPage1, columnsWithAnchor, rowsWithAnchor, false);
                            // QS, secondary orientation
                            fits &= validateLocation(column, row, columns2, rowsPerPage2, columnsWithAnchor, rowsWithAnchor, false);
                        }
                        if (!fits && i >= tileStack.size()) {
                            canMove = false;
                            break;
                        }
                    }
                    if (!canMove) break;
                    tileStack.remove(--i);
                    int newPosition = oldPosition;
                    for (Pair<Integer, Integer> p : moveStrategy) {
                        // Because the move() calls are one after another, the order shuffles around. Get pos AFTER moving
                        newPosition = newPosition == p.first ? p.second : (p.first < p.second ?
                            (newPosition < p.first ? newPosition : (newPosition > p.second ? newPosition : newPosition - 1))
                            : (newPosition < p.second ? newPosition : (newPosition > p.first ? newPosition : newPosition + 1)));
                    }
                    moveStrategy.add(new Pair<>(newPosition, lastPlacedPos++));

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
                    column++;
                }
            }
        }
        if (canMove) {
            for (Pair<Integer, Integer> toMove : moveStrategy) {
                if (toMove.first == toMove.second) continue;
                move(toMove.first, toMove.second, mTiles, notify);
            }
            updateDividerLocations();
            if (to >= mEditIndex) {
                mUiEventLogger.log(QSEditEvent.QS_EDIT_REMOVE, 0, strip(mTiles.get(to)));
            } else if (from >= mEditIndex) {
                mUiEventLogger.log(QSEditEvent.QS_EDIT_ADD, 0, strip(mTiles.get(to)));
            } else {
                mUiEventLogger.log(QSEditEvent.QS_EDIT_MOVE, 0, strip(mTiles.get(to)));
            }
            saveSpecs(mHost);
        }
        return canMove;
    }

    private void updateDividerLocations() {
        // The first null is the header label (index 0) so we can skip it,
        // the second null is the edit tiles label, the third null is the tile divider.
        // If there is no third null, then there are no non-system tiles.
        mEditIndex = -1;
        mTileDividerIndex = mTiles.size();
        for (int i = 1; i < mTiles.size(); i++) {
            if (mTiles.get(i) == null) {
                if (mEditIndex == -1) {
                    mEditIndex = i;
                } else {
                    mTileDividerIndex = i;
                }
            }
        }
        if (mTiles.size() - 1 == mTileDividerIndex) {
            notifyItemChanged(mTileDividerIndex);
        }
    }

    private QSTile findTile(String spec) {
        return mHost.getTiles().stream().filter(i -> spec.equals(i.getTileSpec())).findAny().orElse(null);
    }

    private <T> T findTileRet(String spec, Function<QSTile, T> consumer) {
        QSTile tile = findTile(spec);
        T ret;
        if (tile != null) {
            ret = consumer.apply(tile);
        } else {
            tile = mHost.createTile(spec);
            ret = consumer.apply(tile);
            tile.destroy();
        }
        return ret;
    }

    private void findTile(String spec, Consumer<QSTile> consumer) {
        findTileRet(spec, tile -> {
            consumer.accept(tile);
            return null;
        });
    }

    private static String strip(TileInfo tileInfo) {
        String spec = tileInfo.spec;
        if (spec.startsWith(CustomTile.PREFIX)) {
            ComponentName component = CustomTile.getComponentFromSpec(spec);
            return component.getPackageName();
        }
        return spec;
    }

    private <T> void move(int from, int to, List<T> list, boolean notify) {
        list.add(to, list.remove(from));
        if (notify) {
            notifyItemMoved(from, to);
        }
    }

    public class Holder extends ViewHolder {
        @Nullable private QSTileViewImpl mTileView;

        public Holder(View itemView) {
            super(itemView);
            if (itemView instanceof FrameLayout) {
                mTileView = (QSTileViewImpl) ((FrameLayout) itemView).getChildAt(0);
                mTileView.getIcon().disableAnimation();
                mTileView.setTag(this);
                //ViewCompat.setAccessibilityDelegate(mTileView, mAccessibilityDelegate);
            }
        }

        @Nullable
        public CustomizeTileView getTileAsCustomizeView() {
            return (CustomizeTileView) mTileView;
        }

        @Nullable
        public SpannedGridLayoutManager.LayoutParams getGridLayoutParams() {
            if (mTileView == null)
                return null;
            View tileView = (View) mTileView;
            if (tileView.getParent() instanceof FrameLayout) {
                tileView = (FrameLayout) tileView.getParent();
            }
            return (SpannedGridLayoutManager.LayoutParams) tileView.getLayoutParams();
        }

        public void clearDrag() {
            itemView.clearAnimation();
            itemView.setScaleX(1);
            itemView.setScaleY(1);
        }

        public void startDrag() {
            itemView.animate()
                    .setDuration(DRAG_LENGTH)
                    .scaleX(DRAG_SCALE)
                    .scaleY(DRAG_SCALE);
        }

        public void stopDrag() {
            itemView.animate()
                    .setDuration(DRAG_LENGTH)
                    .scaleX(1)
                    .scaleY(1);
        }

        boolean canRemove() {
            return canRemoveFromPosition(getLayoutPosition());
        }

        boolean canAdd() {
            return canAddFromPosition(getLayoutPosition());
        }

        void toggleState() {
            if (canAdd()) {
                add();
            } else {
                remove();
            }
        }

        private void add() {
            if (addFromPosition(getLayoutPosition())) {
                itemView.announceForAccessibility(
                        itemView.getContext().getText(R.string.accessibility_qs_edit_tile_added));
            }
        }

        private void remove() {
            if (removeFromPosition(getLayoutPosition())) {
                itemView.announceForAccessibility(
                        itemView.getContext().getText(R.string.accessibility_qs_edit_tile_removed));
            }
        }

        boolean isCurrentTile() {
            return TileAdapter.this.isCurrentTile(getLayoutPosition());
        }

        void startAccessibleAdd() {
            TileAdapter.this.startAccessibleAdd(getLayoutPosition());
        }

        void startAccessibleMove() {
            TileAdapter.this.startAccessibleMove(getLayoutPosition());
        }

        boolean canTakeAccessibleAction() {
            return mAccessibilityAction == ACTION_NONE;
        }
    }

    private final SpannedGridLayoutManager.GridSpanLookup mSizeLookup = new SpannedGridLayoutManager.GridSpanLookup() {
        @Override
        public SpannedGridLayoutManager.SpanInfo getSpanInfo(int position) {
            final int type = getItemViewType(position);
            final TileInfo gTile = mTiles.size() > position ? mTiles.get(position) : null;
            if (type == TYPE_EDIT || type == TYPE_DIVIDER || type == TYPE_HEADER) { // UI element
                return new SpannedGridLayoutManager.SpanInfo(mNumColumns, 1);
            } else if (gTile != null && gTile.spec != null) { // QS tile
                return findTileRet(gTile.spec, tile -> {
                    if (tile != null && tile instanceof MultiQSTile) {
                        MultiQSTile multiTile = (MultiQSTile) tile;
                        return new SpannedGridLayoutManager.SpanInfo(multiTile.getColumnsConsumed(), multiTile.getRowsConsumed());
                    } else {
                        return SpannedGridLayoutManager.SpanInfo.SINGLE_CELL;
                    }
                });
            } else { // Fallback
                return SpannedGridLayoutManager.SpanInfo.SINGLE_CELL;
            }
        }
    };

    private class TileItemDecoration extends ItemDecoration {
        private final Drawable mDrawable;

        private TileItemDecoration(Context context) {
            mDrawable = context.getDrawable(R.drawable.qs_customize_tile_decoration);
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, State state) {
            super.onDraw(c, parent, state);

            final int childCount = parent.getChildCount();
            final int width = parent.getWidth();
            final int bottom = parent.getBottom();
            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final ViewHolder holder = parent.getChildViewHolder(child);
                // Do not draw background for the holder that's currently being dragged
                if (holder == mCurrentDrag) {
                    continue;
                }
                // Do not draw background for holders before the edit index (header and current
                // tiles)
                if (holder.getAdapterPosition() == 0 ||
                        holder.getAdapterPosition() < mEditIndex && !(child instanceof TextView)) {
                    continue;
                }

                final int top = child.getTop() + Math.round(ViewCompat.getTranslationY(child));
                mDrawable.setBounds(0, top, width, bottom);
                mDrawable.draw(c);
                break;
            }
        }
    }

    private final ItemTouchHelper.Callback mCallbacks = new ItemTouchHelper.Callback() {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public void onSelectedChanged(ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder = null;
            }
            if (viewHolder == mCurrentDrag) return;
            if (mCurrentDrag != null) {
                int position = mCurrentDrag.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                TileInfo info = mTiles.get(position);
                ((CustomizeTileView) mCurrentDrag.mTileView).setShowAppLabel(
                        position > mEditIndex && !info.isSystem);
                mCurrentDrag.stopDrag();
                mCurrentDrag = null;
            }
            if (viewHolder != null) {
                mCurrentDrag = (Holder) viewHolder;
                mCurrentDrag.startDrag();
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyItemChanged(mEditIndex);
                }
            });
        }

        @Override
        public boolean canDropOver(RecyclerView recyclerView, ViewHolder current,
                ViewHolder target) {
            if (current == null || target == null) {
                return false;
            }
            final int position = target.getAdapterPosition();
            final int from = current.getAdapterPosition();
            if (from == 0 || from == RecyclerView.NO_POSITION || position == 0 || position == RecyclerView.NO_POSITION) {
                return false;
            }
            if (!canRemoveTiles() && from < mEditIndex && position >= mEditIndex) {
                return false; // don't delete if we should not delete
            }
            if (position > mEditIndex || (from < mEditIndex && position == mEditIndex)) {
                return true; // but allow deleting tiles with simple logic
            }
            if (position > mEditIndex + 1) return false; // (AOSP) make deleting simpler
            // Below code has to cover big tile add and move cases
            final String spec = mTiles.get(from).spec;
            final Pair consumed = findTileRet(spec, tile ->
                new Pair<>(tile instanceof MultiQSTile ? ((MultiQSTile) tile).getRowsConsumed() : 1,
                    tile instanceof MultiQSTile ? ((MultiQSTile) tile).getColumnsConsumed() : 1));
            final SpannedGridLayoutManager.LayoutParams lp = ((Holder) target).getGridLayoutParams();
            int rowsConsumed = (Integer) consumed.first;
            int columnsConsumed = (Integer) consumed.second;
            int row = -1;
            int column = -1;
            if (lp != null) {
                row = lp.row - 1; // Header TextView counts as row
                column = lp.column;
                // If we have our 2x2 tile at (2, 0) and are dragging to (4, 0),
                // we end up at (3, 0) instead because if we move away, all tiles
                // will move one column back. Address this
                if (from < position) {
                    column -= (columnsConsumed) - 1;
                    if (column < 0) return false;
                }
            } else { // We are trying to place a tile to the last position where no other tile currently is.
                ViewHolder pre = recyclerView.findViewHolderForAdapterPosition(position - 1);
                if (pre != null) {
                    final SpannedGridLayoutManager.LayoutParams subLp = ((Holder) pre).getGridLayoutParams();
                    if (subLp != null) {
                        final Pair subconsumed = findTileRet(mTiles.get(position - 1).spec, tile ->
                            new Pair<>(tile instanceof MultiQSTile ? ((MultiQSTile) tile).getRowsConsumed() : 1,
                                tile instanceof MultiQSTile ? ((MultiQSTile) tile).getColumnsConsumed() : 1));
                        int rowsSubConsumed = (Integer) subconsumed.first;
                        int columnsSubConsumed = (Integer) subconsumed.second;
                        row = subLp.row - 1; // Header TextView counts as row
                        column = subLp.column + 1;
                        if ((subLp.column + columnsSubConsumed) == mNumColumns) {
                            column = 0;
                            row++;
                            if (rowsSubConsumed > 1) {
                                if (columnsSubConsumed == mNumColumns)
                                    row += rowsSubConsumed - 1;
                                else if ((mNumColumns - columnsSubConsumed) < columnsConsumed)
                                    return false;
                                else
                                    column = columnsSubConsumed;
                            }
                        }
                    } else if ((columnsConsumed * rowsConsumed) > 1) {
                        return false;
                    }
                } else if ((columnsConsumed * rowsConsumed) > 1) {
                    return false;
                }
            }
            if (row >= 0) {
                // QQS, primary orientation
                if (!validateLocation(column, row, columns3, qqsRows1, columnsConsumed, rowsConsumed, true)) return false;
                // QQS, secondary orientation
                if (!validateLocation(column, row, columns4, qqsRows2, columnsConsumed, rowsConsumed, true)) return false;
                // QS, primary orientation
                if (!validateLocation(column, row, mNumColumns, rowsPerPage1, columnsConsumed, rowsConsumed, false)) return false;
                // QS, secondary orientation
                if (!validateLocation(column, row, columns2, rowsPerPage2, columnsConsumed, rowsConsumed, false)) return false;
            }
            // If we are here, we can move the tile!
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, ViewHolder viewHolder) {
            switch (viewHolder.getItemViewType()) {
                case TYPE_EDIT:
                case TYPE_DIVIDER:
                case TYPE_HEADER:
                    // Fall through
                    return makeMovementFlags(0, 0);
                default:
                    int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                            | ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT;
                    return makeMovementFlags(dragFlags, 0);
            }
        }

        private long rateLimit = 0;

        @Override
        public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();
            if (from == 0 || from == RecyclerView.NO_POSITION ||
                    to == 0 || to == RecyclerView.NO_POSITION) {
                return false;
            }
            if (!canDropOver(recyclerView, viewHolder, target)) return false;
            boolean b = move(from, to);
            if (!b && System.currentTimeMillis() - rateLimit > (SHORT_DURATION_TIMEOUT + 500)) {
                rateLimit = System.currentTimeMillis();
                makeOverlayToast(R.string.qs_cant_add_tile);
            }
            return b;
        }

        @Override
        public void onSwiped(ViewHolder viewHolder, int direction) {
        }

        // Just in case, make sure to animate to base state.
        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            ((Holder) viewHolder).stopDrag();
            super.clearView(recyclerView, viewHolder);
        }
    };

    private static int calculateHeaderMinHeight(Context context) {
        Resources res = context.getResources();
        // style used in qs_customize_header.xml for the Toolbar
        TypedArray toolbarStyle = context.obtainStyledAttributes(
                R.style.QSCustomizeToolbar, com.android.internal.R.styleable.Toolbar);
        int buttonStyle = toolbarStyle.getResourceId(
                com.android.internal.R.styleable.Toolbar_navigationButtonStyle, 0);
        toolbarStyle.recycle();
        int buttonMinWidth = 0;
        if (buttonStyle != 0) {
            TypedArray t = context.obtainStyledAttributes(buttonStyle, android.R.styleable.View);
            buttonMinWidth = t.getDimensionPixelSize(android.R.styleable.View_minWidth, 0);
            t.recycle();
        }
        return res.getDimensionPixelSize(R.dimen.qs_panel_padding_top)
                + res.getDimensionPixelSize(R.dimen.brightness_mirror_height)
                + res.getDimensionPixelSize(R.dimen.qs_brightness_margin_top)
                + res.getDimensionPixelSize(R.dimen.qs_brightness_margin_bottom)
                - buttonMinWidth
                - res.getDimensionPixelSize(R.dimen.qs_tile_margin_top_bottom);
    }

    private boolean validateLocation(int inColumn, int inRow, int columns, int rows, int columnsConsumed, int rowsConsumed, boolean firstPageOnly) {
        if (!(columns > 0 && rows > 0)) return true;
        int position = (inRow * mNumColumns) + inColumn;
        int row = position / columns;
        int column = position % columns;
        if ((!firstPageOnly || position < (columns * rows))) {
            if ((rows - (row % rows)) < rowsConsumed) return false;
            if ((columns - column) < columnsConsumed) return false;
        }
        return true;
    }

    private void makeOverlayToast(int stringId) {
        final Resources res = mContext.getResources();

        final SystemUIToast systemUIToast = mToastFactory.createToast(mContext,
                res.getString(stringId), mContext.getPackageName(), UserHandle.myUserId(),
                res.getConfiguration().orientation);
        if (systemUIToast == null) {
            return;
        }

        View toastView = systemUIToast.getView();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.format = PixelFormat.TRANSLUCENT;
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
        params.y = systemUIToast.getYOffset();
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        int absGravity = Gravity.getAbsoluteGravity(systemUIToast.getGravity(),
                res.getConfiguration().getLayoutDirection());
        params.gravity = absGravity;
        if ((absGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
            params.horizontalWeight = TOAST_PARAMS_HORIZONTAL_WEIGHT;
        }
        if ((absGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
            params.verticalWeight = TOAST_PARAMS_VERTICAL_WEIGHT;
        }

        mWindowManager.addView(toastView, params);

        Animator inAnimator = systemUIToast.getInAnimation();
        if (inAnimator != null) {
            inAnimator.start();
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Animator outAnimator = systemUIToast.getOutAnimation();
                if (outAnimator != null) {
                    outAnimator.start();
                    outAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mWindowManager.removeViewImmediate(toastView);
                        }
                    });
                }
            }
        }, SHORT_DURATION_TIMEOUT);
    }
}
