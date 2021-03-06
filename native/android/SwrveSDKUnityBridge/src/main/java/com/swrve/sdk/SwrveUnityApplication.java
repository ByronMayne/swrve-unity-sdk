package com.swrve.sdk;

import android.app.Application;

/**
 * This class is needed for when you want Swrve to properly process background
 * processes for the Unity SDK, like push notifications, and location campaign
 * searching and merging.
 *
 * This file is included in the swrve-unity aar, but needs to be referred to in
 * your AndroidManifest file, with
 * <application android:name="com.swrve.sdk.SwrveUnityApplication">
 *
 */
public class SwrveUnityApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        SwrveCommon.setRunnable(new Runnable() {
            @Override
            public void run() {new SwrveUnityCommon(SwrveUnityApplication.this);
            }
        });
    }
}
