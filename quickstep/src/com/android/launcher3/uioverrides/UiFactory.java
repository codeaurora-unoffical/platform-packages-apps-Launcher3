/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.getPrefs;
import static com.android.quickstep.OverviewInteractionState.KEY_SWIPE_UP_ENABLED;
import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.WindowManagerWrapper;

public class UiFactory {

    public static TouchController[] createTouchControllers(Launcher launcher) {
        SharedPreferences prefs = getPrefs(launcher);
        boolean swipeUpEnabled = prefs.getBoolean(KEY_SWIPE_UP_ENABLED, true);
        if (!swipeUpEnabled) {
            return new TouchController[] {
                    launcher.getDragController(),
                    new LandscapeStatesTouchController(launcher),
                    new LauncherTaskViewcontroller(launcher)};
        }
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return new TouchController[] {
                    launcher.getDragController(),
                    new LandscapeStatesTouchController(launcher),
                    new LandscapeEdgeSwipeController(launcher),
                    new LauncherTaskViewcontroller(launcher)};
        } else {
            return new TouchController[] {
                    launcher.getDragController(),
                    new PortraitStatesTouchController(launcher),
                    new LauncherTaskViewcontroller(launcher)};
        }
    }

    public static StateHandler[] getStateHandler(Launcher launcher) {
        return new StateHandler[] {
                launcher.getAllAppsController(), launcher.getWorkspace(),
                new RecentsViewStateController(launcher)};
    }

    public static void onLauncherStateOrFocusChanged(Launcher launcher) {
        boolean shouldBackButtonBeHidden = launcher != null
                && launcher.getStateManager().getState().hideBackButton
                && launcher.hasWindowFocus();
        if (shouldBackButtonBeHidden) {
            // Show the back button if there is a floating view visible.
            shouldBackButtonBeHidden = AbstractFloatingView.getTopOpenView(launcher) == null;
        }
        OverviewInteractionState.getInstance(launcher)
                .setBackButtonVisible(!shouldBackButtonBeHidden);
    }

    public static void resetOverview(Launcher launcher) {
        RecentsView recents = launcher.getOverviewPanel();
        recents.reset();
    }

    public static void onStart(Context context) {
        RecentsModel model = RecentsModel.getInstance(context);
        if (model != null) {
            model.onStart();
        }
    }

    public static void onLauncherStateOrResumeChanged(Launcher launcher) {
        LauncherState state = launcher.getStateManager().getState();
        DeviceProfile profile = launcher.getDeviceProfile();
        WindowManagerWrapper.getInstance().setShelfHeight(
                state != ALL_APPS && launcher.isUserActive() && !profile.isVerticalBarLayout(),
                profile.hotseatBarSizePx);

        if (state == NORMAL) {
            launcher.<RecentsView>getOverviewPanel().setSwipeDownShouldLaunchApp(false);
        }
    }

    public static void onTrimMemory(Context context, int level) {
        RecentsModel model = RecentsModel.getInstance(context);
        if (model != null) {
            model.onTrimMemory(level);
        }
    }

    private static class LauncherTaskViewcontroller extends TaskViewTouchController<Launcher> {

        public LauncherTaskViewcontroller(Launcher activity) {
            super(activity);
        }

        @Override
        protected boolean isRecentsInteractive() {
            return mActivity.isInState(OVERVIEW);
        }
    }
}
