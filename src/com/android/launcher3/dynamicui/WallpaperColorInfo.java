package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.util.Pair;

import com.android.launcher3.compat.WallpaperColorsCompat;
import com.android.launcher3.compat.WallpaperManagerCompat;

import java.util.ArrayList;

import static android.app.WallpaperManager.FLAG_SYSTEM;

public class WallpaperColorInfo implements WallpaperManagerCompat.OnColorsChangedListenerCompat {

    private static final int FALLBACK_COLOR = Color.WHITE;
    private static final Object sInstanceLock = new Object();
    private static WallpaperColorInfo sInstance;

    public static WallpaperColorInfo getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new WallpaperColorInfo(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManagerCompat mWallpaperManager;
    private final ColorExtractionAlgorithm mExtractionType;
    private int mMainColor;
    private int mSecondaryColor;
    private boolean mIsDark;
    private boolean mSupportsDarkText;

    private OnChangeListener[] mTempListeners;

    private WallpaperColorInfo(Context context) {
        mWallpaperManager = WallpaperManagerCompat.getInstance(context);
        mWallpaperManager.addOnColorsChangedListener(this);
        mExtractionType = ColorExtractionAlgorithm.newInstance(context);
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    public int getMainColor() {
        return mMainColor;
    }

    public int getSecondaryColor() {
        return mSecondaryColor;
    }

    public boolean isDark() {
        return mIsDark;
    }

    public boolean supportsDarkText() {
        return mSupportsDarkText;
    }

    @Override
    public void onColorsChanged(WallpaperColorsCompat colors, int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            update(colors);
            notifyChange();
        }
    }

    private void update(WallpaperColorsCompat wallpaperColors) {
        Pair<Integer, Integer> colors = mExtractionType.extractInto(wallpaperColors);
        if (colors != null) {
            mMainColor = colors.first;
            mSecondaryColor = colors.second;
        } else {
            mMainColor = FALLBACK_COLOR;
            mSecondaryColor = FALLBACK_COLOR;
        }
        mSupportsDarkText = wallpaperColors != null
                ? (wallpaperColors.getColorHints()
                    & WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) > 0 : false;
        mIsDark = wallpaperColors != null
                ? (wallpaperColors.getColorHints()
                    & WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME) > 0 : false;
    }

    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeOnChangeListener(OnChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyChange() {
        OnChangeListener[] copy =
                mTempListeners != null && mTempListeners.length == mListeners.size() ?
                        mTempListeners : new OnChangeListener[mListeners.size()];

        // Create a new array to avoid concurrent modification when the activity destroys itself.
        mTempListeners = mListeners.toArray(copy);
        for (OnChangeListener listener : mTempListeners) {
            listener.onExtractedColorsChanged(this);
        }
    }

    public interface OnChangeListener {
        void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo);
    }
}
