/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.tapl;

import static com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel.ZERO_BUTTON;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.TestProtocol;

/**
 * Operations on AllApps opened from Home. Also a parent for All Apps opened from Overview.
 */
public class AllApps extends LauncherInstrumentation.VisibleContainer {
    private static final int MAX_SCROLL_ATTEMPTS = 40;
    private static final int MIN_INTERACT_SIZE = 100;
    private static final int FLING_SPEED = LauncherInstrumentation.needSlowGestures() ? 1000 : 3000;

    private final int mHeight;

    AllApps(LauncherInstrumentation launcher) {
        super(launcher);
        final UiObject2 allAppsContainer = verifyActiveContainer();
        mHeight = allAppsContainer.getVisibleBounds().height();
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.ALL_APPS;
    }

    private boolean hasClickableIcon(UiObject2 allAppsContainer, BySelector appIconSelector) {
        final UiObject2 icon = allAppsContainer.findObject(appIconSelector);
        if (icon == null) return false;
        if (mLauncher.getNavigationModel() == ZERO_BUTTON) return true;
        final UiObject2 navBar = mLauncher.waitForSystemUiObject("navigation_bar_frame");
        return icon.getVisibleBounds().bottom < navBar.getVisibleBounds().top;
    }

    /**
     * Finds an icon. Fails if the icon doesn't exist. Scrolls the app list when needed to make
     * sure the icon is visible.
     *
     * @param appName name of the app.
     * @return The app.
     */
    @NonNull
    public AppIcon getAppIcon(String appName) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get app icon on all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            if (mLauncher.getNavigationModel() != ZERO_BUTTON) {
                final UiObject2 navBar = mLauncher.waitForSystemUiObject("navigation_bar_frame");
                allAppsContainer.setGestureMargins(0, 0, 0, navBar.getVisibleBounds().height() + 1);
            }
            final BySelector appIconSelector = AppIcon.getAppIconSelector(appName, mLauncher);
            if (!hasClickableIcon(allAppsContainer, appIconSelector)) {
                scrollBackToBeginning();
                int attempts = 0;
                try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled")) {
                    while (!hasClickableIcon(allAppsContainer, appIconSelector) &&
                            allAppsContainer.scroll(Direction.DOWN, 0.8f)) {
                        mLauncher.assertTrue(
                                "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                                ++attempts <= MAX_SCROLL_ATTEMPTS);
                        verifyActiveContainer();
                    }
                }
                verifyActiveContainer();
            }

            final UiObject2 appIcon = mLauncher.getObjectInContainer(allAppsContainer,
                    appIconSelector);
            ensureIconVisible(appIcon, allAppsContainer);
            return new AppIcon(mLauncher, appIcon);
        }
    }

    private void scrollBackToBeginning() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to scroll back in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            final UiObject2 searchBox =
                    mLauncher.waitForObjectInContainer(allAppsContainer,
                            "search_container_all_apps");

            int attempts = 0;
            allAppsContainer.setGestureMargins(0, searchBox.getVisibleBounds().bottom + 1, 0, 5);

            for (int scroll = getScroll(allAppsContainer);
                    scroll != 0;
                    scroll = getScroll(allAppsContainer)) {
                mLauncher.assertTrue("Negative scroll position", scroll > 0);

                mLauncher.assertTrue(
                        "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                        ++attempts <= MAX_SCROLL_ATTEMPTS);

                allAppsContainer.scroll(Direction.UP, 1);
            }

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled up")) {
                verifyActiveContainer();
            }
        }
    }

    private int getScroll(UiObject2 allAppsContainer) {
        return mLauncher.getAnswerFromLauncher(allAppsContainer, TestProtocol.GET_SCROLL_MESSAGE).
                getInt(TestProtocol.SCROLL_Y_FIELD, -1);
    }

    private void ensureIconVisible(UiObject2 appIcon, UiObject2 allAppsContainer) {
        final int appHeight = appIcon.getVisibleBounds().height();
        if (appHeight < MIN_INTERACT_SIZE) {
            // Try to figure out how much percentage of the container needs to be scrolled in order
            // to reveal the app icon to have the MIN_INTERACT_SIZE
            final float pct = Math.max(((float) (MIN_INTERACT_SIZE - appHeight)) / mHeight, 0.2f);
            allAppsContainer.scroll(Direction.DOWN, pct);
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "scrolled an icon in all apps to make it visible - and then")) {
                mLauncher.waitForIdle();
                verifyActiveContainer();
            }
        }
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling forward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center to avoid starting at elements near the top.
            allAppsContainer.setGestureMargins(0, 0, 0, mHeight / 2);
            allAppsContainer.fling(Direction.DOWN,
                    (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
            verifyActiveContainer();
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center, for symmetry with forward.
            allAppsContainer.setGestureMargins(0, mHeight / 2, 0, 0);
            allAppsContainer.fling(Direction.UP,
                    (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
            verifyActiveContainer();
        }
    }
}
