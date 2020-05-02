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
package com.android.launcher3;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_HIDE_BACK_BUTTON;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.DiscoveryBounce.BOUNCE_MAX_COUNT;
import static com.android.launcher3.allapps.DiscoveryBounce.HOME_BOUNCE_COUNT;
import static com.android.launcher3.allapps.DiscoveryBounce.HOME_BOUNCE_SEEN;
import static com.android.launcher3.allapps.DiscoveryBounce.SHELF_BOUNCE_COUNT;
import static com.android.launcher3.allapps.DiscoveryBounce.SHELF_BOUNCE_SEEN;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;

import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.accessibility.SystemActions;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.proxy.ProxyActivityStarter;
import com.android.launcher3.proxy.StartActivityParams;
import com.android.launcher3.statehandlers.BackButtonAlphaHandler;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.uioverrides.RecentsViewStateController;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteFadeOutAnimationListener;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.stream.Stream;

/**
 * Extension of Launcher activity to provide quickstep specific functionality
 */
public abstract class BaseQuickstepLauncher extends Launcher
        implements NavigationModeChangeListener {

    private DepthController mDepthController = new DepthController(this);
    protected SystemActions mSystemActions;

    /**
     * Reusable command for applying the back button alpha on the background thread.
     */
    public static final UiThreadHelper.AsyncCommand SET_BACK_BUTTON_ALPHA =
            (context, arg1, arg2) -> SystemUiProxy.INSTANCE.get(context).setBackButtonAlpha(
                    Float.intBitsToFloat(arg1), arg2 != 0);

    private final ShelfPeekAnim mShelfPeekAnim = new ShelfPeekAnim(this);

    private View mActionsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSystemActions = new SystemActions(this);

        SysUINavigationMode.INSTANCE.get(this).addModeChangeListener(this);

        if (!getSharedPrefs().getBoolean(HOME_BOUNCE_SEEN, false)) {
            getStateManager().addStateListener(new LauncherStateManager.StateListener() {
                @Override
                public void onStateTransitionStart(LauncherState toState) { }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled = SysUINavigationMode.INSTANCE
                            .get(BaseQuickstepLauncher.this).getMode().hasGestures;
                    LauncherState prevState = getStateManager().getLastState();

                    if (((swipeUpEnabled && finalState == OVERVIEW) || (!swipeUpEnabled
                            && finalState == ALL_APPS && prevState == NORMAL) || BOUNCE_MAX_COUNT
                            <= getSharedPrefs().getInt(HOME_BOUNCE_COUNT, 0))) {
                        getSharedPrefs().edit().putBoolean(HOME_BOUNCE_SEEN, true).apply();
                        getStateManager().removeStateListener(this);
                    }
                }
            });
        }

        if (!getSharedPrefs().getBoolean(SHELF_BOUNCE_SEEN, false)) {
            getStateManager().addStateListener(new LauncherStateManager.StateListener() {
                @Override
                public void onStateTransitionStart(LauncherState toState) { }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    LauncherState prevState = getStateManager().getLastState();

                    if ((finalState == ALL_APPS && prevState == OVERVIEW) || BOUNCE_MAX_COUNT
                            <= getSharedPrefs().getInt(SHELF_BOUNCE_COUNT, 0)) {
                        getSharedPrefs().edit().putBoolean(SHELF_BOUNCE_SEEN, true).apply();
                        getStateManager().removeStateListener(this);
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        SysUINavigationMode.INSTANCE.get(this).removeModeChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        getDragLayer().recreateControllers();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mSystemActions.onActivityResult(requestCode);
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep, so that high-res thumbnails can load the next time we enter
        // overview
        RecentsModel.INSTANCE.get(this).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    protected void onUiChangedWhileSleeping() {
        // Remove the snapshot because the content view may have obvious changes.
        ActivityManagerWrapper.getInstance().invalidateHomeTaskSnapshot(this);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intentSender = intent;
            params.fillInIntent = fillInIntent;
            params.flagsMask = flagsMask;
            params.flagsValues = flagsValues;
            params.extraFlags = extraFlags;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask,
                    flagsValues, extraFlags, options);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intent = intent;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    @Override
    protected void onDeferredResumed() {
        if (mPendingActivityRequestCode != -1 && isInState(NORMAL)) {
            // Remove any active ProxyActivityStarter task and send RESULT_CANCELED to Launcher.
            onActivityResult(mPendingActivityRequestCode, RESULT_CANCELED, null);
            // ProxyActivityStarter is started with clear task to reset the task after which it
            // removes the task itself.
            startActivity(ProxyActivityStarter.getLaunchIntent(this, null));
        }

        // Register all system actions once they are available
        mSystemActions.register();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSystemActions.unregister();
    }

    @Override
    protected void setupViews() {
        super.setupViews();
        mActionsView = findViewById(R.id.overview_actions_view);


        if (FeatureFlags.ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(this)) {
            // Overview is above all other launcher elements, including qsb, so move it to the top.
            getOverviewPanel().bringToFront();
            if (mActionsView != null) {
                mActionsView.bringToFront();
            }
        }
    }

    public View getActionsView() {
        return mActionsView;
    }

    @Override
    protected void closeOpenViews(boolean animate) {
        super.closeOpenViews(animate);
        ActivityManagerWrapper.getInstance()
                .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
    }

    @Override
    protected StateHandler[] createStateHandlers() {
        return new StateHandler[] {
                getAllAppsController(),
                getWorkspace(),
                getDepthController(),
                new RecentsViewStateController(this),
                new BackButtonAlphaHandler(this)};
    }

    public DepthController getDepthController() {
        return mDepthController;
    }

    @Override
    protected ScaleAndTranslation getOverviewScaleAndTranslationForNormalState() {
        if (SysUINavigationMode.getMode(this) == Mode.NO_BUTTON) {
            PagedOrientationHandler layoutVertical =
                ((RecentsView)getOverviewPanel()).getPagedViewOrientedState().getOrientationHandler();
            return layoutVertical.getScaleAndTranslation(getDeviceProfile(),
                getOverviewPanel());
        }
        return super.getOverviewScaleAndTranslationForNormalState();
    }

    @Override
    public void useFadeOutAnimationForLauncherStart(CancellationSignal signal) {
        QuickstepAppTransitionManagerImpl appTransitionManager =
                (QuickstepAppTransitionManagerImpl) getAppTransitionManager();
        appTransitionManager.setRemoteAnimationProvider(new RemoteAnimationProvider() {
            @Override
            public AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] appTargets,
                    RemoteAnimationTargetCompat[] wallpaperTargets) {

                // On the first call clear the reference.
                signal.cancel();

                ValueAnimator fadeAnimation = ValueAnimator.ofFloat(1, 0);
                fadeAnimation.addUpdateListener(new RemoteFadeOutAnimationListener(appTargets,
                        wallpaperTargets));
                AnimatorSet anim = new AnimatorSet();
                anim.play(fadeAnimation);
                return anim;
            }
        }, signal);
    }

    @Override
    public void onDragLayerHierarchyChanged() {
        onLauncherStateOrFocusChanged();
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        if ((changeBits
                & (ACTIVITY_STATE_WINDOW_FOCUSED | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0) {
            onLauncherStateOrFocusChanged();
        }

        if ((changeBits & ACTIVITY_STATE_STARTED) != 0) {
            mDepthController.setActivityStarted(isStarted());
        }

        super.onActivityFlagsChanged(changeBits);
    }

    /**
     * Sets the back button visibility based on the current state/window focus.
     */
    private void onLauncherStateOrFocusChanged() {
        Mode mode = SysUINavigationMode.getMode(this);
        boolean shouldBackButtonBeHidden = mode.hasGestures
                && getStateManager().getState().hideBackButton
                && hasWindowFocus()
                && (getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0;
        if (shouldBackButtonBeHidden) {
            // Show the back button if there is a floating view visible.
            shouldBackButtonBeHidden = AbstractFloatingView.getTopOpenViewWithType(this,
                    TYPE_ALL & ~TYPE_HIDE_BACK_BUTTON) == null;
        }
        UiThreadHelper.setBackButtonAlphaAsync(this, SET_BACK_BUTTON_ALPHA,
                shouldBackButtonBeHidden ? 0f : 1f, true /* animate */);
        if (getDragLayer() != null) {
            getRootView().setDisallowBackGesture(shouldBackButtonBeHidden);
        }
    }

    @Override
    public void finishBindingItems(int pageBoundFirst) {
        super.finishBindingItems(pageBoundFirst);
        // Instantiate and initialize WellbeingModel now that its loading won't interfere with
        // populating workspace.
        // TODO: Find a better place for this
        WellbeingModel.INSTANCE.get(this);
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        return Stream.concat(super.getSupportedShortcuts(),
                Stream.of(WellbeingModel.SHORTCUT_FACTORY));
    }

    public ShelfPeekAnim getShelfPeekAnim() {
        return mShelfPeekAnim;
    }
}
