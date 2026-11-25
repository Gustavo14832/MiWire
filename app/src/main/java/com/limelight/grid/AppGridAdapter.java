package com.limelight.grid;

import android.content.Context;
import android.os.Build;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.ui.AppIcons;
import com.limelight.ui.Rainbow;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unchecked")
public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private static final int ART_WIDTH_PX = 300;
    private static final int SMALL_WIDTH_DP = 100;
    private static final int LARGE_WIDTH_DP = 150;

    private final ComputerDetails computer;
    private final String uniqueId;
    private final boolean showHiddenApps;
    private final float density;

    private CachedAppAssetLoader loader;
    private int selectedAppId = -1;
    private Set<Integer> hiddenAppIds = new HashSet<>();
    private ArrayList<AppView.AppObject> allApps = new ArrayList<>();
    private final SparseBooleanArray customSettingsCache = new SparseBooleanArray();
    private final SparseBooleanArray customSettingsCached = new SparseBooleanArray();
    private Rainbow.Ring selectionRing;
    private WeakReference<View> lastSelectedTile;
    private WeakReference<AbsListView> attachedListView;

    public AppGridAdapter(Context context, PreferenceConfiguration prefs, ComputerDetails computer, String uniqueId, boolean showHiddenApps) {
        super(context, getLayoutIdForPreferences(prefs));

        this.computer = computer;
        this.uniqueId = uniqueId;
        this.showHiddenApps = showHiddenApps;
        this.density = context.getResources().getDisplayMetrics().density;

        updateLayoutWithPreferences(context, prefs);
    }

    public void updateHiddenApps(Set<Integer> newHiddenAppIds, boolean hideImmediately) {
        this.hiddenAppIds.clear();
        this.hiddenAppIds.addAll(newHiddenAppIds);

        if (hideImmediately) {
            // Reconstruct the itemList with the new hidden app set
            itemList.clear();
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());

                if (!app.isHidden || showHiddenApps) {
                    itemList.add(app);
                }
            }
        }
        else {
            // Just update the isHidden state to show the correct UI indication
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());
            }
        }

        notifyDataSetChanged();
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        if (prefs.smallIconMode) {
            return R.layout.app_grid_item_small;
        }
        else {
            return R.layout.app_grid_item;
        }
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        int dpi = context.getResources().getDisplayMetrics().densityDpi;
        int dp;

        if (prefs.smallIconMode) {
            dp = SMALL_WIDTH_DP;
        }
        else {
            dp = LARGE_WIDTH_DP;
        }

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        if (loader != null) {
            // Shut down the old loader and its executors when switching grid layout densities
            loader.freeCacheMemory();
            loader.shutdown();
        }

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(context));

        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void attachListView(AbsListView listView) {
        this.attachedListView = listView != null ? new WeakReference<>(listView) : null;
    }

    public void invalidateCustomSettingsCache() {
        customSettingsCache.clear();
        customSettingsCached.clear();
    }

    public void shutdown() {
        if (selectionRing != null) {
            selectionRing.stop();
        }
        if (lastSelectedTile != null) {
            View prev = lastSelectedTile.get();
            if (prev != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                prev.setForeground(null);
            }
            lastSelectedTile.clear();
        }
        if (loader != null) {
            loader.shutdown();
        }
    }

    private Rainbow.Ring getOrCreateSelectionRing() {
        if (selectionRing == null) {
            selectionRing = new Rainbow.Ring(26 * density, 3 * density);
        }
        return selectionRing;
    }

    private void applyTileSelectionVisuals(View tile, AppView.AppObject obj, boolean selected, boolean hasCustom) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (selected) {
                if (lastSelectedTile != null) {
                    View prev = lastSelectedTile.get();
                    if (prev != null && prev != tile) {
                        prev.setForeground(null);
                    }
                }
                Rainbow.Ring ring = getOrCreateSelectionRing();
                if (tile.getForeground() != ring) {
                    tile.setForeground(ring);
                }
                lastSelectedTile = new WeakReference<>(tile);
            }
            else if (tile.getForeground() != null) {
                tile.setForeground(null);
                if (lastSelectedTile != null && lastSelectedTile.get() == tile) {
                    lastSelectedTile.clear();
                }
            }
        }

        if (obj.isHidden) {
            tile.setAlpha(0.40f);
        }
        else {
            tile.setAlpha(selected || selectedAppId == -1 ? 1.0f : 0.82f);
        }

        StringBuilder label = new StringBuilder(obj.app.getAppName());
        if (obj.isRunning) {
            label.append(", ").append(context.getString(R.string.cg_running));
        }
        if (obj.isHidden) {
            label.append(", ").append(context.getString(R.string.cg_a11y_hidden));
        }
        if (hasCustom) {
            label.append(", ").append(context.getString(R.string.cg_chip_custom));
        }
        if (selected) {
            label.append(", ").append(context.getString(R.string.cg_a11y_selected));
        }
        tile.setContentDescription(label);
    }

    // Cover Glow: the game shown in the home screen's hero gets a ring in the cover row
    public void setSelectedAppId(int appId) {
        if (appId == selectedAppId) {
            return;
        }
        selectedAppId = appId;
        AbsListView listView = attachedListView != null ? attachedListView.get() : null;
        if (listView != null && listView.getChildCount() > 0) {
            if (lastSelectedTile != null) {
                View prev = lastSelectedTile.get();
                if (prev != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    prev.setForeground(null);
                }
            }
            int childCount = listView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = listView.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof ViewHolder) {
                    ViewHolder holder = (ViewHolder) tag;
                    if (holder.boundTag instanceof AppView.AppObject) {
                        AppView.AppObject obj = (AppView.AppObject) holder.boundTag;
                        boolean hasCustom = holder.settingsIndicator != null &&
                                holder.settingsIndicator.getVisibility() == View.VISIBLE;
                        applyTileSelectionVisuals(child, obj, obj.app.getAppId() == selectedAppId, hasCustom);
                    }
                }
            }
            return;
        }
        notifyDataSetChanged();
    }

    // Loads a game's cover into another view (the hero), through the same cache as the grid
    public void loadCover(AppView.AppObject obj, ImageView imgView, TextView txtView) {
        if (AppIcons.useIcon(context, obj.app.getAppName())) {
            // The hero shows the name beside the cover, so the tile has no text
            showIconTile(obj, imgView, txtView);
            txtView.setVisibility(View.GONE);
            return;
        }
        loader.populateImageView(obj.app, imgView, txtView);
    }

    // Cover Glow: a designed icon tile in place of the PC's cover (see AppIcons). Replacing the
    // loader's placeholder also stops any load still running for this view from drawing over it.
    private void showIconTile(AppView.AppObject obj, ImageView imgView, TextView txtView) {
        imgView.clearAnimation();
        imgView.setImageDrawable(AppIcons.tile(context, obj.app.getAppName()));
        imgView.setVisibility(View.VISIBLE);
        txtView.setText(obj.app.getAppName());
        txtView.setVisibility(View.VISIBLE);
        txtView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    }

    public void cancelQueuedOperations() {
        loader.cancelForegroundLoads();
        loader.cancelBackgroundLoads();
        loader.freeCacheMemory();
    }

    private static void sortList(List<AppView.AppObject> list) {
        Collections.sort(list, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().toLowerCase().compareTo(rhs.app.getAppName().toLowerCase());
            }
        });
    }

    public void addApp(AppView.AppObject app) {
        // Update hidden state
        app.isHidden = hiddenAppIds.contains(app.app.getAppId());

        // Always add the app to the all apps list
        allApps.add(app);
        sortList(allApps);

        // Add the app to the adapter data if it's not hidden
        if (showHiddenApps || !app.isHidden) {
            // Queue a request to fetch this bitmap into cache
            loader.queueCacheLoad(app.app);

            // Add the app to our sorted list
            itemList.add(app);
            sortList(itemList);
        }
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
        allApps.remove(app);
    }

    @Override
    public void clear() {
        super.clear();
        allApps.clear();
        invalidateCustomSettingsCache();
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, ImageView settingsIndicator, AppView.AppObject obj) {
        Object tag = parentView.getTag();
        if (tag instanceof ViewHolder) {
            ((ViewHolder) tag).boundTag = obj;
        }

        // Rounded corners: the XML attribute only works from Android 12, so set it here too
        if (imgView.getBackground() == null) {
            imgView.setBackgroundResource(R.drawable.cg_cover);
        }
        imgView.setClipToOutline(true);
        if (AppIcons.useIcon(context, obj.app.getAppName())) {
            showIconTile(obj, imgView, txtView);
        }
        else {
            // Let the cached asset loader handle it. A missing cover shows the Starwire tile
            // with the name at the bottom, under the logo.
            txtView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            loader.populateImageView(obj.app, imgView, txtView);
        }

        if (obj.isRunning) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.ic_play);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        boolean hasCustom = false;
        // Show settings indicator if app has custom settings (use global settings is false)
        if (settingsIndicator != null) {
            int appId = obj.app.getAppId();
            if (!customSettingsCached.get(appId, false)) {
                String appKey = this.computer.uuid + ":" + appId;
                hasCustom = AppPreferences.hasCustomSettings(context, appKey);
                customSettingsCache.put(appId, hasCustom);
                customSettingsCached.put(appId, true);
            }
            else {
                hasCustom = customSettingsCache.get(appId, false);
            }
            settingsIndicator.setVisibility(hasCustom ? View.VISIBLE : View.GONE);
        }

        boolean selected = obj.app.getAppId() == selectedAppId;
        applyTileSelectionVisuals(parentView, obj, selected, hasCustom);
    }
}
