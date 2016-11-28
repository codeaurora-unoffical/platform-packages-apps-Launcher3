/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.util;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.text.TextUtils;
import com.android.launcher3.Utilities;
/**
 * Utility methods using package manager
 */
public class PackageManagerHelper {
    public static final boolean ATLEAST_MARSHMALLOW =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    /**
     * Returns true if {@param srcPackage} has the permission required to start the activity from
     * {@param intent}. If {@param srcPackage} is null, then the activity should not need
     * any permissions
     */
    public static boolean hasPermissionForActivity(Context context, Intent intent,
            String srcPackage) {
        PackageManager pm = context.getPackageManager();
        ResolveInfo target = pm.resolveActivity(intent, 0);
        if (target == null) {
            // Not a valid target
            return false;
        }
        if (TextUtils.isEmpty(target.activityInfo.permission)) {
            // No permission is needed
            return true;
        }
        if (TextUtils.isEmpty(srcPackage)) {
            // The activity requires some permission but there is no source.
            return false;
        }
        // Source does not have sufficient permissions.
        if(pm.checkPermission(target.activityInfo.permission, srcPackage) !=
                PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!ATLEAST_MARSHMALLOW) {
            // These checks are sufficient for below M devices.
            return true;
        }
        // On M and above also check AppOpsManager for compatibility mode permissions.
        if (TextUtils.isEmpty(AppOpsManager.permissionToOp(target.activityInfo.permission))) {
            // There is no app-op for this permission, which could have been disabled.
            return true;
        }
        // There is no direct way to check if the app-op is allowed for a particular app. Since
        // app-op is only enabled for apps running in compatibility mode, simply block such apps.
        try {
            return pm.getApplicationInfo(srcPackage, 0).targetSdkVersion >= Build.VERSION_CODES.M;
        } catch (NameNotFoundException e) { }
        return false;
    }
}
