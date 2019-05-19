/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.states.RotationHelper.REQUEST_ROTATE;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.views.IconRecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public OverviewState(int id) {
        this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    protected OverviewState(int id, int transitionDuration, int stateFlags) {
        super(id, LauncherLogProto.ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1f, 0f, 0f);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        IconRecentsView recentsView = launcher.getOverviewPanel();
        recentsView.onBeginTransitionToOverview();
        // Request orientation be set to unspecified, letting the system decide the best
        // orientation.
        launcher.getRotationHelper().setCurrentStateRequest(REQUEST_ROTATE);
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DEACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return NONE;
    }

    @Override
    public float getWorkspaceScrimAlpha(Launcher launcher) {
        return 0.5f;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_desc_recent_apps);
    }

    @Override
    public void onBackPressed(Launcher launcher) {
        // TODO: Add logic to go back to task if coming from a currently running task.
        super.onBackPressed(launcher);
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return getDefaultSwipeHeight(launcher.getDeviceProfile());
    }

    public static float getDefaultSwipeHeight(DeviceProfile dp) {
        return dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
    }


    public static OverviewState newBackgroundState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newPeekState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new OverviewState(id);
    }
}
