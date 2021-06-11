/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.gestures;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * The Util to query one-handed mode settings config
 */
public class OneHandedSettingsUtils {

    static final String SUPPORT_ONE_HANDED_MODE = "ro.support_one_handed_mode";
    static final int OFF = 0;
    static final int ON = 1;

    public enum OneHandedTimeout {
        NEVER(0), SHORT(4), MEDIUM(8), LONG(12);

        private final int mValue;

        OneHandedTimeout(int value) {
            this.mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    private final Context mContext;
    private final SettingsObserver mSettingsObserver;

    private static int sCurrentUserId;

    OneHandedSettingsUtils(Context context) {
        mContext = context;
        sCurrentUserId = UserHandle.myUserId();
        mSettingsObserver = new SettingsObserver(new Handler(Looper.getMainLooper()));
    }

    /**
     * Gets One-Handed mode support flag.
     */
    public static boolean isSupportOneHandedMode() {
        return SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false);
    }

    /**
     * Gets one-handed mode feature enable or disable flag from Settings provider.
     *
     * @param context App context
     * @return enable or disable one-handed mode flag.
     */
    public static boolean isOneHandedModeEnabled(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ONE_HANDED_MODE_ENABLED, OFF, sCurrentUserId) == ON;
    }

    /**
     * Sets one-handed mode enable or disable flag to Settings provider.
     *
     * @param context App context
     * @param enable  enable or disable one-handed mode.
     */
    public static void setOneHandedModeEnabled(Context context, boolean enable) {
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ONE_HANDED_MODE_ENABLED, enable ? ON : OFF, sCurrentUserId);
    }

    /**
     * Gets enabling taps app to exit one-handed mode flag from Settings provider.
     *
     * @param context App context
     * @return enable or disable taps app to exit.
     */
    public static boolean isTapsAppToExitEnabled(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.TAPS_APP_TO_EXIT, OFF, sCurrentUserId) == ON;
    }

    /**
     * Sets enabling taps app to exit one-handed mode flag to Settings provider.
     *
     * @param context App context
     * @param enable  enable or disable when taping app to exit one-handed mode.
     */
    public static boolean setTapsAppToExitEnabled(Context context, boolean enable) {
        return Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.TAPS_APP_TO_EXIT, enable ? ON : OFF, sCurrentUserId);
    }

    /**
     * Gets one-handed mode timeout value from Settings provider.
     *
     * @param context App context
     * @return timeout value in seconds.
     */
    public static int getTimeoutValue(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ONE_HANDED_MODE_TIMEOUT,
                OneHandedTimeout.MEDIUM.getValue() /* default MEDIUM(8) by UX */,
                sCurrentUserId);
    }

    /**
     * Gets current user id from OneHandedSettingsUtils
     *
     * @return the current user id in OneHandedSettingsUtils
     */
    public static int getUserId() {
        return sCurrentUserId;
    }

    /**
     * Sets specific user id for OneHandedSettingsUtils
     *
     * @param userId the user id to be updated
     */
    public static void setUserId(int userId) {
        sCurrentUserId = userId;
    }

    /**
     * Sets one-handed mode timeout value to Settings provider.
     *
     * @param context App context
     * @param timeout timeout in seconds for exiting one-handed mode.
     */
    public static void setTimeoutValue(Context context, int timeout) {
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ONE_HANDED_MODE_TIMEOUT, timeout, sCurrentUserId);
    }

    /**
     * Gets Swipe-down-notification enable or disable flag from Settings provider.
     *
     * @param context App context
     * @return enable or disable Swipe-down-notification flag.
     */
    public static boolean isSwipeDownNotificationEnabled(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED, OFF, sCurrentUserId) == ON;
    }

    /**
     * Sets Swipe-down-notification enable or disable flag to Settings provider.
     *
     * @param context App context
     * @param enable enable or disable Swipe-down-notification.
     */
    public static void setSwipeDownNotificationEnabled(Context context, boolean enable) {
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED, enable ? ON : OFF,
                sCurrentUserId);
    }

    /**
     * Get NavigationBar mode flag from Settings provider.
     * @param context App context
     * @return Navigation bar mode:
     *  0 = 3 button
     *  1 = 2 button
     *  2 = fully gestural
     */
    public static int getNavigationBarMode(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.NAVIGATION_MODE, 2 /* fully gestural */, sCurrentUserId);
    }

    /**
     *
     * @param context App context
     * @return Support One-Handed mode feature or not.
     */
    public static boolean isFeatureAvailable(Context context) {
        return isSupportOneHandedMode() && getNavigationBarMode(context) != 0;
    }

    /**
     * Registers callback for observing Settings.Secure.ONE_HANDED_MODE_ENABLED state.
     * @param callback for state changes
     */
    public void registerToggleAwareObserver(TogglesCallback callback) {
        mSettingsObserver.observe();
        mSettingsObserver.setCallback(callback);
    }

    /**
     * Unregisters callback for observing Settings.Secure.ONE_HANDED_MODE_ENABLED state.
     */
    public void unregisterToggleAwareObserver() {
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mSettingsObserver);
    }

    private final class SettingsObserver extends ContentObserver {
        private TogglesCallback mCallback;

        private final Uri mOneHandedEnabledAware = Settings.Secure.getUriFor(
                Settings.Secure.ONE_HANDED_MODE_ENABLED);

        SettingsObserver(Handler handler) {
            super(handler);
        }

        private void setCallback(TogglesCallback callback) {
            mCallback = callback;
        }

        public void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(mOneHandedEnabledAware, true, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mCallback != null) mCallback.onChange(uri);
        }
    }

    /**
     * An interface for when Settings.Secure key state changes.
     */
    public interface TogglesCallback {
        /**
         * Callback method for Settings.Secure key state changes.
         *
         * @param uri The Uri of the changed content.
         */
        void onChange(Uri uri);
    }
}