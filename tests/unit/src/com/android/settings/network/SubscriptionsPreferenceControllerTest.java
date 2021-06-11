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

package com.android.settings.network;

import static android.telephony.SignalStrength.SIGNAL_STRENGTH_GOOD;
import static android.telephony.SignalStrength.SIGNAL_STRENGTH_GREAT;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.text.Html;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.Utils;
import com.android.settings.network.SubscriptionsPreferenceController.SubsPrefCtrlInjector;
import com.android.settings.testutils.ResourcesUtils;
import com.android.settings.wifi.WifiPickerTrackerHelper;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.mobile.MobileMappings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SubscriptionsPreferenceControllerTest {
    private static final String KEY = "preference_group";

    @Mock
    private UserManager mUserManager;
    @Mock
    private SubscriptionManager mSubscriptionManager;
    @Mock
    private ConnectivityManager mConnectivityManager;
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private TelephonyManager mTelephonyManagerForSub;
    @Mock
    private Network mActiveNetwork;
    @Mock
    private Lifecycle mLifecycle;
    @Mock
    private LifecycleOwner mLifecycleOwner;
    @Mock
    private WifiPickerTrackerHelper mWifiPickerTrackerHelper;

    private LifecycleRegistry mLifecycleRegistry;
    private int mOnChildUpdatedCount;
    private Context mContext;
    private SubscriptionsPreferenceController.UpdateListener mUpdateListener;
    private PreferenceCategory mPreferenceCategory;
    private PreferenceScreen mPreferenceScreen;
    private PreferenceManager mPreferenceManager;
    private NetworkCapabilities mNetworkCapabilities;
    private FakeSubscriptionsPreferenceController mController;
    private static SubsPrefCtrlInjector sInjector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mLifecycleRegistry = new LifecycleRegistry(mLifecycleOwner);

        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);
        when(mContext.getSystemService(ConnectivityManager.class)).thenReturn(mConnectivityManager);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mTelephonyManager);
        when(mConnectivityManager.getActiveNetwork()).thenReturn(mActiveNetwork);
        when(mConnectivityManager.getNetworkCapabilities(mActiveNetwork))
                .thenReturn(mNetworkCapabilities);
        when(mUserManager.isAdminUser()).thenReturn(true);
        when(mLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);

        mPreferenceManager = new PreferenceManager(mContext);
        mPreferenceScreen = mPreferenceManager.createPreferenceScreen(mContext);
        mPreferenceScreen.setInitialExpandedChildrenCount(3);
        mPreferenceCategory = new PreferenceCategory(mContext);
        mPreferenceCategory.setKey(KEY);
        mPreferenceCategory.setOrderingAsAdded(true);
        mPreferenceScreen.addPreference(mPreferenceCategory);

        mOnChildUpdatedCount = 0;
        mUpdateListener = () -> mOnChildUpdatedCount++;

        sInjector = spy(new SubsPrefCtrlInjector());
        initializeMethod(true, 1, 1, 1, false, false);
        mController =  new FakeSubscriptionsPreferenceController(mContext, mLifecycle,
                mUpdateListener, KEY, 5);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
    }

    @After
    public void tearDown() {
        SubscriptionUtil.setActiveSubscriptionsForTesting(null);
    }

    @Test
    public void isAvailable_oneSubscription_availableFalse() {
        setupMockSubscriptions(1);

        assertThat(mController.isAvailable()).isFalse();
    }

    @Test
    public void isAvailable_oneSubAndProviderOn_availableTrue() {
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        setupMockSubscriptions(1);

        assertThat(mController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_twoSubscriptions_availableTrue() {
        setupMockSubscriptions(2);

        assertThat(mController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_fiveSubscriptions_availableTrue() {
        doReturn(true).when(sInjector).canSubscriptionBeDisplayed(mContext, 0);
        setupMockSubscriptions(5);

        assertThat(mController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_airplaneModeOn_availableFalse() {
        setupMockSubscriptions(2);

        assertThat(mController.isAvailable()).isTrue();

        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);

        assertThat(mController.isAvailable()).isFalse();
    }

    @Test
    @UiThreadTest
    public void onAirplaneModeChanged_airplaneModeTurnedOn_eventFired() {
        setupMockSubscriptions(2);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mController.isAvailable()).isTrue();

        final int updateCountBeforeModeChange = mOnChildUpdatedCount;
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);

        mController.onAirplaneModeChanged(true);

        assertThat(mController.isAvailable()).isFalse();
        assertThat(mOnChildUpdatedCount).isEqualTo(updateCountBeforeModeChange + 1);
    }

    @Test
    @UiThreadTest
    public void onAirplaneModeChanged_airplaneModeTurnedOff_eventFired() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        setupMockSubscriptions(2);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        assertThat(mController.isAvailable()).isFalse();

        final int updateCountBeforeModeChange = mOnChildUpdatedCount;
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);

        mController.onAirplaneModeChanged(true);

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mOnChildUpdatedCount).isEqualTo(updateCountBeforeModeChange + 1);
    }

    @Test
    @UiThreadTest
    public void onSubscriptionsChanged_countBecameTwo_eventFired() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(2);
        SubscriptionUtil.setActiveSubscriptionsForTesting(subs.subList(0, 1));

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mController.isAvailable()).isFalse();

        final int updateCountBeforeSubscriptionChange = mOnChildUpdatedCount;
        SubscriptionUtil.setActiveSubscriptionsForTesting(subs);

        mController.onSubscriptionsChanged();

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mOnChildUpdatedCount).isEqualTo(updateCountBeforeSubscriptionChange + 1);
    }

    @Test
    @UiThreadTest
    public void onSubscriptionsChanged_countBecameOne_eventFiredAndPrefsRemoved() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(2);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        final int updateCountBeforeSubscriptionChange = mOnChildUpdatedCount;
        SubscriptionUtil.setActiveSubscriptionsForTesting(subs.subList(0, 1));

        mController.onSubscriptionsChanged();

        assertThat(mController.isAvailable()).isFalse();
        assertThat(mOnChildUpdatedCount).isEqualTo(updateCountBeforeSubscriptionChange + 1);
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    @UiThreadTest
    public void onSubscriptionsChanged_subscriptionReplaced_preferencesChanged() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(3);
        doReturn(subs).when(mSubscriptionManager).getAvailableSubscriptionInfoList();

        // Start out with only sub1 and sub2.
        SubscriptionUtil.setActiveSubscriptionsForTesting(subs.subList(0, 2));
        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub2");
        assertThat(mPreferenceCategory.getPreference(1).getTitle()).isEqualTo("sub1");

        // Now replace sub2 with sub3, and make sure the old preference was removed and the new
        // preference was added.
        final int updateCountBeforeSubscriptionChange = mOnChildUpdatedCount;
        SubscriptionUtil.setActiveSubscriptionsForTesting(Arrays.asList(subs.get(0), subs.get(2)));
        mController.onSubscriptionsChanged();

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mOnChildUpdatedCount).isEqualTo(updateCountBeforeSubscriptionChange + 1);
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub3");
        assertThat(mPreferenceCategory.getPreference(1).getTitle()).isEqualTo("sub1");
    }

    @Test
    public void getSummary_twoSubsOneDefaultForEverythingDataActive() {
        setupMockSubscriptions(2);

        doReturn(11).when(sInjector).getDefaultSmsSubscriptionId();
        doReturn(11).when(sInjector).getDefaultVoiceSubscriptionId();
        when(mTelephonyManager.isDataEnabled()).thenReturn(true);
        doReturn(true).when(sInjector).isActiveCellularNetwork(mContext);

        assertThat(mController.getSummary(11, true)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "default_for_calls_and_sms")
                        + System.lineSeparator()
                        + ResourcesUtils.getResourcesString(mContext, "mobile_data_active"));

        assertThat(mController.getSummary(22, false)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "subscription_available"));
    }

    @Test
    public void getSummary_twoSubsOneDefaultForEverythingDataNotActive() {
        setupMockSubscriptions(2, 1, true);

        doReturn(1).when(sInjector).getDefaultSmsSubscriptionId();
        doReturn(1).when(sInjector).getDefaultVoiceSubscriptionId();

        assertThat(mController.getSummary(1, true)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "default_for_calls_and_sms")
                        + System.lineSeparator()
                        + ResourcesUtils.getResourcesString(mContext, "default_for_mobile_data"));

        assertThat(mController.getSummary(2, false)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "subscription_available"));
    }

    @Test
    public void getSummary_twoSubsOneDefaultForEverythingDataDisabled() {
        setupMockSubscriptions(2);

        doReturn(1).when(sInjector).getDefaultSmsSubscriptionId();
        doReturn(1).when(sInjector).getDefaultVoiceSubscriptionId();

        assertThat(mController.getSummary(1, true)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "default_for_calls_and_sms")
                        + System.lineSeparator()
                        + ResourcesUtils.getResourcesString(mContext, "mobile_data_off"));

        assertThat(mController.getSummary(2, false)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "subscription_available"));
    }

    @Test
    public void getSummary_twoSubsOneForCallsAndDataOneForSms() {
        setupMockSubscriptions(2, 1, true);

        doReturn(2).when(sInjector).getDefaultSmsSubscriptionId();
        doReturn(1).when(sInjector).getDefaultVoiceSubscriptionId();

        assertThat(mController.getSummary(1, true)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "default_for_calls")
                        + System.lineSeparator()
                        + ResourcesUtils.getResourcesString(mContext, "default_for_mobile_data"));

        assertThat(mController.getSummary(2, false)).isEqualTo(
                ResourcesUtils.getResourcesString(mContext, "default_for_sms"));
    }

    @Test
    @UiThreadTest
    public void setIcon_greatSignal_correctLevels() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(2, 1, true);
        setMockSubSignalStrength(subs, 0, SIGNAL_STRENGTH_GREAT);
        setMockSubSignalStrength(subs, 1, SIGNAL_STRENGTH_GREAT);
        final Preference pref = new Preference(mContext);
        final Drawable greatDrawWithoutCutOff = mock(Drawable.class);
        doReturn(greatDrawWithoutCutOff).when(sInjector)
                .getIcon(any(), anyInt(), anyInt(), anyBoolean());

        mController.setIcon(pref, 1, true /* isDefaultForData */);
        assertThat(pref.getIcon()).isEqualTo(greatDrawWithoutCutOff);

        final Drawable greatDrawWithCutOff = mock(Drawable.class);
        doReturn(greatDrawWithCutOff).when(sInjector)
                .getIcon(any(), anyInt(), anyInt(), anyBoolean());
        mController.setIcon(pref, 2, false /* isDefaultForData */);
        assertThat(pref.getIcon()).isEqualTo(greatDrawWithCutOff);
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasSim_showPreference() {
        final List<SubscriptionInfo> sub = setupMockSubscriptions(1);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        doReturn(sub).when(mSubscriptionManager).getAvailableSubscriptionInfoList();

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub1");
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasMultiSim_showDataSubPreference() {
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        doReturn(sub).when(mSubscriptionManager).getAvailableSubscriptionInfoList();

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub1");
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasMultiSimAndActive_connectedAndRat() {
        final CharSequence expectedSummary =
                Html.fromHtml("Connected / 5G", Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "5G";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), true, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(mock(MobileMappings.Config.class)).when(sInjector).getConfig(mContext);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasMultiSimAndActiveCarrierWifi_connectedAndWPlus() {
        final CharSequence expectedSummary =
                Html.fromHtml("Connected / W+", Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "W+";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), false, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(mock(MobileMappings.Config.class)).when(sInjector).getConfig(mContext);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(true));
        doReturn(true).when(mWifiPickerTrackerHelper).isActiveCarrierNetwork();
        mController.setWifiPickerTrackerHelper(mWifiPickerTrackerHelper);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasMultiSimButMobileDataOff_notAutoConnect() {
        final String dataOffSummary =
                ResourcesUtils.getResourcesString(mContext, "mobile_data_off_summary");
        final CharSequence expectedSummary =
                Html.fromHtml(dataOffSummary, Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "5G";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), false, false,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreference(0).getSummary())
            .isEqualTo(expectedSummary.toString());
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndHasMultiSimAndNotActive_showRatOnly() {
        final CharSequence expectedSummary = Html.fromHtml("5G", Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "5G";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), false, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));
        when(mTelephonyManager.isDataEnabled()).thenReturn(true);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void displayPreference_providerAndNoSim_noPreference() {
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(null).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    @UiThreadTest
    public void onTelephonyDisplayInfoChanged_providerAndHasMultiSimAndActive_connectedAndRat() {
        final CharSequence expectedSummary =
                Html.fromHtml("Connected / LTE", Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "LTE";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        final TelephonyDisplayInfo telephonyDisplayInfo =
                new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), true, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(mock(MobileMappings.Config.class)).when(sInjector).getConfig(mContext);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));
        when(mTelephonyManager.isDataEnabled()).thenReturn(true);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        mController.onTelephonyDisplayInfoChanged(telephonyDisplayInfo);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void onTelephonyDisplayInfoChanged_providerAndHasMultiSimAndNotActive_showRat() {
        final CharSequence expectedSummary =
                Html.fromHtml("LTE", Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "LTE";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        final TelephonyDisplayInfo telephonyDisplayInfo =
                new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), false, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        doReturn(mock(MobileMappings.Config.class)).when(sInjector).getConfig(mContext);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        mController.onTelephonyDisplayInfoChanged(telephonyDisplayInfo);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void onTelephonyDisplayInfoChanged_providerAndHasMultiSimAndOutOfService_noConnection() {
        final String noConnectionSummary =
                ResourcesUtils.getResourcesString(mContext, "mobile_data_no_connection");
        final CharSequence expectedSummary =
                Html.fromHtml(noConnectionSummary, Html.FROM_HTML_MODE_LEGACY);
        final String networkType = "LTE";
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        final TelephonyDisplayInfo telephonyDisplayInfo =
                new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        setupGetIconConditions(sub.get(0).getSubscriptionId(), false, true,
                TelephonyManager.DATA_DISCONNECTED, ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(mock(MobileMappings.Config.class)).when(sInjector).getConfig(mContext);
        doReturn(networkType)
                .when(sInjector).getNetworkType(any(), any(), any(), anyInt(), eq(false));

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        mController.onTelephonyDisplayInfoChanged(telephonyDisplayInfo);

        assertThat(mPreferenceCategory.getPreference(0).getSummary()).isEqualTo(expectedSummary);
    }

    @Test
    @UiThreadTest
    public void onAirplaneModeChanged_providerAndHasSim_noPreference() {
        setupMockSubscriptions(1);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);

        mController.onAirplaneModeChanged(true);

        assertThat(mController.isAvailable()).isFalse();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    @UiThreadTest
    public void dataSubscriptionChanged_providerAndHasMultiSim_showSubId1Preference() {
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        doReturn(sub).when(mSubscriptionManager).getAvailableSubscriptionInfoList();
        Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        mController.mConnectionChangeReceiver.onReceive(mContext, intent);

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub1");
    }

    @Test
    @UiThreadTest
    public void dataSubscriptionChanged_providerAndHasMultiSim_showSubId2Preference() {
        final List<SubscriptionInfo> sub = setupMockSubscriptions(2);
        final int subId = sub.get(0).getSubscriptionId();
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        doReturn(sub).when(mSubscriptionManager).getAvailableSubscriptionInfoList();
        Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub1");

        doReturn(sub.get(1)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();

        mController.mConnectionChangeReceiver.onReceive(mContext, intent);

        assertThat(mController.isAvailable()).isTrue();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo("sub2");
    }

    @Test
    @UiThreadTest
    public void getIcon_cellularIsActive_iconColorIsAccentDefaultColor() {
        final List<SubscriptionInfo> sub = setupMockSubscriptions(1);
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(sub.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        Drawable icon = mock(Drawable.class);
        doReturn(icon).when(sInjector).getIcon(any(), anyInt(), anyInt(), eq(false));
        setupGetIconConditions(sub.get(0).getSubscriptionId(), true, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);

        verify(icon).setTint(Utils.getColorAccentDefaultColor(mContext));
    }

    @Test
    @UiThreadTest
    public void getIcon_dataStateConnectedAndMobileDataOn_iconIsSignalIcon() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(1);
        final int subId = subs.get(0).getSubscriptionId();
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(subs.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        Drawable icon = mock(Drawable.class);
        doReturn(icon).when(sInjector).getIcon(any(), anyInt(), anyInt(), eq(false));
        setupGetIconConditions(subId, false, true,
                TelephonyManager.DATA_CONNECTED, ServiceState.STATE_IN_SERVICE);
        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        Drawable actualIcon = mPreferenceCategory.getPreference(0).getIcon();

        assertThat(icon).isEqualTo(actualIcon);
    }

    @Test
    @UiThreadTest
    public void getIcon_voiceInServiceAndMobileDataOff_iconIsSignalIcon() {
        final List<SubscriptionInfo> subs = setupMockSubscriptions(1);
        final int subId = subs.get(0).getSubscriptionId();
        doReturn(true).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(subs.get(0)).when(mSubscriptionManager).getDefaultDataSubscriptionInfo();
        Drawable icon = mock(Drawable.class);
        doReturn(icon).when(sInjector).getIcon(any(), anyInt(), anyInt(), eq(false));

        setupGetIconConditions(subId, false, false,
                TelephonyManager.DATA_DISCONNECTED, ServiceState.STATE_IN_SERVICE);

        mController.onResume();
        mController.displayPreference(mPreferenceScreen);
        Drawable actualIcon = mPreferenceCategory.getPreference(0).getIcon();
        doReturn(TelephonyManager.DATA_CONNECTED).when(mTelephonyManagerForSub).getDataState();

        assertThat(icon).isEqualTo(actualIcon);
    }

    @Test
    public void connectCarrierNetwork_isDataEnabled_helperConnect() {
        when(mTelephonyManager.isDataEnabled()).thenReturn(true);
        mController.setWifiPickerTrackerHelper(mWifiPickerTrackerHelper);

        mController.connectCarrierNetwork();

        verify(mWifiPickerTrackerHelper).connectCarrierNetwork(any());
    }

    @Test
    public void connectCarrierNetwork_isNotDataEnabled_helperNeverConnect() {
        when(mTelephonyManager.isDataEnabled()).thenReturn(false);
        mController.setWifiPickerTrackerHelper(mWifiPickerTrackerHelper);

        mController.connectCarrierNetwork();

        verify(mWifiPickerTrackerHelper, never()).connectCarrierNetwork(any());
    }

    private void setupGetIconConditions(int subId, boolean isActiveCellularNetwork,
            boolean isDataEnable, int dataState, int servicestate) {
        doReturn(mTelephonyManagerForSub).when(mTelephonyManager).createForSubscriptionId(subId);
        doReturn(isActiveCellularNetwork).when(sInjector).isActiveCellularNetwork(mContext);
        doReturn(isDataEnable).when(mTelephonyManagerForSub).isDataEnabled();
        doReturn(dataState).when(mTelephonyManagerForSub).getDataState();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ss).when(mTelephonyManagerForSub).getServiceState();
        doReturn(servicestate).when(ss).getState();
    }

    private List<SubscriptionInfo> setupMockSubscriptions(int count) {
        return setupMockSubscriptions(count, 0, true);
    }

    /** Helper method to setup several mock active subscriptions. The generated subscription id's
     * start at 1.
     *
     * @param count How many subscriptions to create
     * @param defaultDataSubId The subscription id of the default data subscription - pass
     *                         INVALID_SUBSCRIPTION_ID if there should not be one
     * @param mobileDataEnabled Whether mobile data should be considered enabled for the default
     *                          data subscription
     */
    private List<SubscriptionInfo> setupMockSubscriptions(int count, int defaultDataSubId,
            boolean mobileDataEnabled) {
        if (defaultDataSubId != INVALID_SUBSCRIPTION_ID) {
            when(sInjector.getDefaultDataSubscriptionId()).thenReturn(defaultDataSubId);
        }
        final ArrayList<SubscriptionInfo> infos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int subscriptionId = i + 1;
            final SubscriptionInfo info = mock(SubscriptionInfo.class);
            final TelephonyManager mgrForSub = mock(TelephonyManager.class);
            final SignalStrength signalStrength = mock(SignalStrength.class);

            if (subscriptionId == defaultDataSubId) {
                when(mgrForSub.isDataEnabled()).thenReturn(mobileDataEnabled);
            }
            when(info.getSubscriptionId()).thenReturn(subscriptionId);
            when(info.getDisplayName()).thenReturn("sub" + (subscriptionId));
            doReturn(mgrForSub).when(mTelephonyManager).createForSubscriptionId(eq(subscriptionId));
            when(mgrForSub.getSignalStrength()).thenReturn(signalStrength);
            when(signalStrength.getLevel()).thenReturn(SIGNAL_STRENGTH_GOOD);
            doReturn(true).when(sInjector).canSubscriptionBeDisplayed(mContext, subscriptionId);
            infos.add(info);
        }
        SubscriptionUtil.setActiveSubscriptionsForTesting(infos);
        return infos;
    }

    /**
     * Helper method to set the signal strength returned for a mock subscription
     * @param subs The list of subscriptions
     * @param index The index in of the subscription in |subs| to change
     * @param level The signal strength level to return for the subscription. Pass -1 to force
     *              return of a null SignalStrength object for the subscription.
     */
    private void setMockSubSignalStrength(List<SubscriptionInfo> subs, int index, int level) {
        final int subId =  subs.get(index).getSubscriptionId();
        doReturn(mTelephonyManagerForSub).when(mTelephonyManager).createForSubscriptionId(subId);
        if (level == -1) {
            when(mTelephonyManagerForSub.getSignalStrength()).thenReturn(null);
        } else {
            final SignalStrength signalStrength = mock(SignalStrength.class);
            doReturn(signalStrength).when(mTelephonyManagerForSub).getSignalStrength();
            when(signalStrength.getLevel()).thenReturn(level);
        }
    }

    private void initializeMethod(boolean isSubscriptionCanBeDisplayed,
            int defaultSmsSubscriptionId, int defaultVoiceSubscriptionId,
            int defaultDataSubscriptionId, boolean isActiveCellularNetwork,
            boolean isProviderModelEnabled) {
        doReturn(isSubscriptionCanBeDisplayed)
                .when(sInjector).canSubscriptionBeDisplayed(mContext, eq(anyInt()));
        doReturn(defaultSmsSubscriptionId).when(sInjector).getDefaultSmsSubscriptionId();
        doReturn(defaultVoiceSubscriptionId).when(sInjector).getDefaultVoiceSubscriptionId();
        doReturn(defaultDataSubscriptionId).when(sInjector).getDefaultDataSubscriptionId();
        doReturn(isActiveCellularNetwork).when(sInjector).isActiveCellularNetwork(mContext);
        doReturn(isProviderModelEnabled).when(sInjector).isProviderModelEnabled(mContext);
        doReturn(mock(Drawable.class))
                .when(sInjector).getIcon(any(), anyInt(), anyInt(), eq(false));
    }

    private static class FakeSubscriptionsPreferenceController
            extends SubscriptionsPreferenceController {

        /**
         * @param context            the context for the UI where we're placing these preferences
         * @param lifecycle          for listening to lifecycle events for the UI
         * @param updateListener     called to let our parent controller know that our
         *                           availability has
         *                           changed, or that one or more of the preferences we've placed
         *                           in the
         *                           PreferenceGroup has changed
         * @param preferenceGroupKey the key used to lookup the PreferenceGroup where Preferences
         *                          will
         *                           be placed
         * @param startOrder         the order that should be given to the first Preference
         *                           placed into
         *                           the PreferenceGroup; the second will use startOrder+1, third
         *                           will
         *                           use startOrder+2, etc. - this is useful for when the parent
         *                           wants
         *                           to have other preferences in the same PreferenceGroup and wants
         */
        FakeSubscriptionsPreferenceController(Context context, Lifecycle lifecycle,
                UpdateListener updateListener, String preferenceGroupKey, int startOrder) {
            super(context, lifecycle, updateListener, preferenceGroupKey, startOrder);
        }

        @Override
        protected SubsPrefCtrlInjector createSubsPrefCtrlInjector() {
            return sInjector;
        }
    }
}