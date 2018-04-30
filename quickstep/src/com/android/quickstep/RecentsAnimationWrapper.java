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
package com.android.quickstep;

import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Wrapper around RecentsAnimationController to help with some synchronization
 */
public class RecentsAnimationWrapper {

    public RecentsAnimationControllerCompat controller;
    public RemoteAnimationTargetCompat[] targets;

    private boolean mInputConsumerEnabled = false;
    private boolean mBehindSystemBars = true;
    private boolean mSplitScreenMinimized = false;

    public synchronized void setController(
            RecentsAnimationControllerCompat controller, RemoteAnimationTargetCompat[] targets) {
        TraceHelper.partitionSection("RecentsController", "Set controller " + controller);
        this.controller = controller;
        this.targets = targets;

        if (mInputConsumerEnabled) {
            enableInputConsumer();
        }
    }

    /**
     * @param onFinishComplete A callback that runs after the animation controller has finished
     *                         on the background thread.
     */
    public void finish(boolean toHome, Runnable onFinishComplete) {
        BackgroundExecutor.get().submit(() -> {
            synchronized (this) {
                TraceHelper.endSection("RecentsController",
                        "Finish " + controller + ", toHome=" + toHome);
                if (controller != null) {
                    controller.setInputConsumerEnabled(false);
                    controller.finish(toHome);
                    if (onFinishComplete != null) {
                        onFinishComplete.run();
                    }
                }
            }
        });
    }

    public void enableInputConsumer() {
        mInputConsumerEnabled = true;
        if (mInputConsumerEnabled) {
            BackgroundExecutor.get().submit(() -> {
                synchronized (this) {
                    TraceHelper.partitionSection("RecentsController",
                            "Enabling consumer on " + controller);
                    if (controller != null) {
                        controller.setInputConsumerEnabled(true);
                    }
                }
            });
        }
    }

    public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) {
        if (mBehindSystemBars == behindSystemBars) {
            return;
        }
        mBehindSystemBars = behindSystemBars;
        BackgroundExecutor.get().submit(() -> {
            synchronized (this) {
                TraceHelper.partitionSection("RecentsController",
                        "Setting behind system bars on " + controller);
                if (controller != null) {
                    controller.setAnimationTargetsBehindSystemBars(behindSystemBars);
                }
            }
        });
    }

    /**
     * NOTE: As a workaround for conflicting animations (Launcher animating the task leash, and
     * SystemUI resizing the docked stack, which resizes the task), we currently only set the
     * minimized mode, and not the inverse.
     * TODO: Synchronize the minimize animation with the launcher animation
     */
    public void setSplitScreenMinimizedForTransaction(boolean minimized) {
        if (mSplitScreenMinimized || !minimized) {
            return;
        }
        mSplitScreenMinimized = minimized;
        BackgroundExecutor.get().submit(() -> {
            synchronized (this) {
                TraceHelper.partitionSection("RecentsController",
                        "Setting minimize dock on " + controller);
                if (controller != null) {
                    controller.setSplitScreenMinimized(minimized);
                }
            }
        });
    }
}
