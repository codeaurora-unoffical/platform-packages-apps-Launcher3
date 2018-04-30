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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.util.RemoteAnimationProvider;

import java.lang.ref.WeakReference;
import java.util.function.BiPredicate;

/**
 * Utility class to track create/destroy for RecentsActivity
 */
@TargetApi(Build.VERSION_CODES.P)
public class RecentsActivityTracker implements ActivityInitListener {

    private static final Object LOCK = new Object();
    private static WeakReference<RecentsActivityTracker> sTracker = new WeakReference<>(null);
    private static WeakReference<RecentsActivity> sCurrentActivity = new WeakReference<>(null);

    private final BiPredicate<RecentsActivity, Boolean> mOnInitListener;

    public RecentsActivityTracker(BiPredicate<RecentsActivity, Boolean> onInitListener) {
        mOnInitListener = onInitListener;
    }

    @Override
    public void register() {
        synchronized (LOCK) {
            sTracker = new WeakReference<>(this);
        }
    }

    @Override
    public void unregister() {
        synchronized (LOCK) {
            if (sTracker.get() == this) {
                sTracker.clear();
            }
        }
    }

    public static void onRecentsActivityCreate(RecentsActivity activity) {
        synchronized (LOCK) {
            RecentsActivityTracker tracker = sTracker.get();
            if (tracker != null && tracker.mOnInitListener.test(activity, false)) {
                sTracker.clear();
            }
            sCurrentActivity = new WeakReference<>(activity);
        }
    }

    public static void onRecentsActivityDestroy(RecentsActivity activity) {
        synchronized (LOCK) {
            if (sCurrentActivity.get() == activity) {
                sCurrentActivity.clear();
            }
        }
    }

    public static RecentsActivity getCurrentActivity() {
        synchronized (LOCK) {
            return sCurrentActivity.get();
        }
    }

    @Override
    public void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
            Context context, Handler handler, long duration) {
        register();

        Bundle options = animProvider.toActivityOptions(handler, duration).toBundle();
        context.startActivity(intent, options);
    }
}
