/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.DRAWABLE_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.DRAG_HANDLE_INDICATOR;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.HOTSEAT_SEARCH_BOX;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;

import android.view.View;

import com.android.launcher3.LauncherState.PageAlphaProvider;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.graphics.ViewScrim;

/**
 * Manages the animations between each of the workspace states.
 */
public class WorkspaceStateTransitionAnimation {

    private final Launcher mLauncher;
    private final Workspace mWorkspace;

    private float mNewScale;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER);
    }

    public void setStateWithAnimation(LauncherState toState, AnimatorSetBuilder builder,
            AnimationConfig config) {
        setWorkspaceProperty(toState, config.getProperSetter(builder));
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter) {
        float[] scaleAndTranslation = state.getWorkspaceScaleAndTranslation(mLauncher);
        mNewScale = scaleAndTranslation[0];
        PageAlphaProvider pageAlphaProvider = state.getWorkspacePageAlphaProvider(mLauncher);
        final int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, (CellLayout) mWorkspace.getChildAt(i), i, pageAlphaProvider,
                    propertySetter);
        }

        propertySetter.setFloat(mWorkspace, SCALE_PROPERTY, mNewScale, Interpolators.ZOOM_OUT);
        propertySetter.setFloat(mWorkspace, View.TRANSLATION_X,
                scaleAndTranslation[1], Interpolators.ZOOM_OUT);
        propertySetter.setFloat(mWorkspace, View.TRANSLATION_Y,
                scaleAndTranslation[2], Interpolators.ZOOM_OUT);

        int elements = state.getVisibleElements(mLauncher);
        float hotseatIconsAlpha = (elements & HOTSEAT_ICONS) != 0 ? 1 : 0;
        propertySetter.setViewAlpha(mLauncher.getHotseat().getLayout(), hotseatIconsAlpha,
                pageAlphaProvider.interpolator);
        propertySetter.setViewAlpha(mLauncher.getWorkspace().getPageIndicator(),
                hotseatIconsAlpha, pageAlphaProvider.interpolator);

        propertySetter.setViewAlpha(mLauncher.getHotseatSearchBox(),
                (elements & HOTSEAT_SEARCH_BOX) != 0 ? 1 : 0,
                pageAlphaProvider.interpolator);

        propertySetter.setViewAlpha(mLauncher.getDragHandleIndicator(),
                (elements & DRAG_HANDLE_INDICATOR) != 0 ? 1 : 0,
                pageAlphaProvider.interpolator);

        // Set scrim
        propertySetter.setFloat(ViewScrim.get(mWorkspace), ViewScrim.PROGRESS,
                state.hasScrim ? 1 : 0, Interpolators.LINEAR);
        propertySetter.setFloat(ViewScrim.get(mLauncher.getAppsView()), ViewScrim.PROGRESS,
                state.hasAllAppsScrim ? 1 : 0, Interpolators.LINEAR);
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, state.getWorkspacePageAlphaProvider(mLauncher),
                NO_ANIM_PROPERTY_SETTER);
    }

    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            PageAlphaProvider pageAlphaProvider, PropertySetter propertySetter) {
        float pageAlpha = pageAlphaProvider.getPageAlpha(childIndex);
        int drawableAlpha = Math.round(pageAlpha * (state.hasWorkspacePageBackground ? 255 : 0));

        propertySetter.setInt(cl.getScrimBackground(),
                DRAWABLE_ALPHA, drawableAlpha, Interpolators.ZOOM_OUT);
        propertySetter.setFloat(cl.getShortcutsAndWidgets(), View.ALPHA,
                pageAlpha, pageAlphaProvider.interpolator);
    }
}