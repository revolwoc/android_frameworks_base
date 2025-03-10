/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static com.android.systemui.statusbar.phone.StatusBarIconHolder.TYPE_BLUETOOTH;
import static com.android.systemui.statusbar.phone.StatusBarIconHolder.TYPE_ICON;
import static com.android.systemui.statusbar.phone.StatusBarIconHolder.TYPE_MOBILE;
import static com.android.systemui.statusbar.phone.StatusBarIconHolder.TYPE_NETWORK_TRAFFIC;
import static com.android.systemui.statusbar.phone.StatusBarIconHolder.TYPE_WIFI;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.android.systemui.Dependency;

import androidx.annotation.VisibleForTesting;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.demomode.DemoModeCommandReceiver;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.BaseStatusBarWifiView;
import com.android.systemui.statusbar.StatusBarBluetoothView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.StatusBarMobileView;
import com.android.systemui.statusbar.StatusBarWifiView;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy.BluetoothIconState;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.CallIndicatorIconState;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.WifiIconState;
import com.android.systemui.statusbar.pipeline.StatusBarPipelineFlags;
import com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView;
import com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.WifiViewModel;
import com.android.systemui.statusbar.policy.NetworkTrafficSB;
import com.android.systemui.util.Assert;

import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.tuner.TunerService;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public interface StatusBarIconController {

    /**
     * When an icon is added with TAG_PRIMARY, it will be treated as the primary icon
     * in that slot and not added as a sub slot.
     */
    int TAG_PRIMARY = 0;

    /** */
    void addIconGroup(IconManager iconManager);
    /** */
    void removeIconGroup(IconManager iconManager);
    /** Refresh the state of an IconManager by recreating the views */
    void refreshIconGroup(IconManager iconManager);
    /** */
    void setExternalIcon(String slot);
    /** */
    void setIcon(String slot, int resourceId, CharSequence contentDescription);
    /** */
    void setIcon(String slot, StatusBarIcon icon);
    /** */
    void setSignalIcon(String slot, WifiIconState state);
    /** */
    void setBluetoothIcon(String slot, BluetoothIconState state);
    /** */
    void setMobileIcons(String slot, List<MobileIconState> states);
    /**
     * Display the no calling & SMS icons.
     */
    void setCallStrengthIcons(String slot, List<CallIndicatorIconState> states);
    /**
     * Display the no calling & SMS icons.
     */
    void setNoCallingIcons(String slot, List<CallIndicatorIconState> states);

    public void setIconVisibility(String slot, boolean b);

    /**
     * Sets the live region mode for the icon
     * @see android.view.View#setAccessibilityLiveRegion(int)
     * @param slot Icon slot to set region for
     * @param accessibilityLiveRegion live region mode for the icon
     */
    void setIconAccessibilityLiveRegion(String slot, int accessibilityLiveRegion);

    /**
     * If you don't know what to pass for `tag`, either remove all icons for slot, or use
     * TAG_PRIMARY to refer to the first icon at a given slot.
     */
    void removeIcon(String slot, int tag);
    /** */
    void removeAllIconsForSlot(String slot);

    // TODO: See if we can rename this tunable name.
    String ICON_HIDE_LIST = "icon_blacklist";

    /** Reads the default hide list from config value unless hideListStr is provided. */
    static ArraySet<String> getIconHideList(Context context, String hideListStr) {
        ArraySet<String> ret = new ArraySet<>();
        String[] hideList = hideListStr == null
            ? context.getResources().getStringArray(R.array.config_statusBarIconsToExclude)
            : hideListStr.split(",");
        for (String slot : hideList) {
            if (!TextUtils.isEmpty(slot)) {
                ret.add(slot);
            }
        }
        return ret;
    }

    /**
     * Version of ViewGroup that observes state from the DarkIconDispatcher.
     */
    class DarkIconManager extends IconManager {
        private final DarkIconDispatcher mDarkIconDispatcher;
        private int mIconHPadding;

        public DarkIconManager(
                LinearLayout linearLayout,
                FeatureFlags featureFlags,
                StatusBarLocation location,
                StatusBarPipelineFlags statusBarPipelineFlags,
                WifiViewModel wifiViewModel,
                DarkIconDispatcher darkIconDispatcher) {
            super(linearLayout,
                    featureFlags,
                    location,
                    statusBarPipelineFlags,
                    wifiViewModel);
            mIconHPadding = mContext.getResources().getDimensionPixelSize(
                    R.dimen.status_bar_icon_padding);
            mDarkIconDispatcher = darkIconDispatcher;
        }

        @Override
        protected void onIconAdded(int index, String slot, boolean blocked,
                StatusBarIconHolder holder) {
            StatusIconDisplayable view = addHolder(index, slot, blocked, holder);
            mDarkIconDispatcher.addDarkReceiver((DarkReceiver) view);
        }

        @Override
        protected LayoutParams onCreateLayoutParams() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize);
            lp.setMargins(mIconHPadding, 0, mIconHPadding, 0);
            return lp;
        }

        @Override
        protected void destroy() {
            for (int i = 0; i < mGroup.getChildCount(); i++) {
                mDarkIconDispatcher.removeDarkReceiver((DarkReceiver) mGroup.getChildAt(i));
            }
            mGroup.removeAllViews();
        }

        @Override
        protected void onRemoveIcon(int viewIndex) {
            mDarkIconDispatcher.removeDarkReceiver((DarkReceiver) mGroup.getChildAt(viewIndex));
            super.onRemoveIcon(viewIndex);
        }

        @Override
        public void onSetIcon(int viewIndex, StatusBarIcon icon) {
            View view = mGroup.getChildAt(viewIndex);
            if (view instanceof StatusBarIconView) {
                ((StatusBarIconView) view).set(icon);
            }
        }

        @Override
        protected DemoStatusIcons createDemoStatusIcons() {
            DemoStatusIcons icons = super.createDemoStatusIcons();
            mDarkIconDispatcher.addDarkReceiver(icons);

            return icons;
        }

        @Override
        protected void exitDemoMode() {
            mDarkIconDispatcher.removeDarkReceiver(mDemoStatusIcons);
            super.exitDemoMode();
        }

        @SysUISingleton
        public static class Factory {
            private final FeatureFlags mFeatureFlags;
            private final StatusBarPipelineFlags mStatusBarPipelineFlags;
            private final WifiViewModel mWifiViewModel;
            private final DarkIconDispatcher mDarkIconDispatcher;

            @Inject
            public Factory(
                    FeatureFlags featureFlags,
                    StatusBarPipelineFlags statusBarPipelineFlags,
                    WifiViewModel wifiViewModel,
                    DarkIconDispatcher darkIconDispatcher) {
                mFeatureFlags = featureFlags;
                mStatusBarPipelineFlags = statusBarPipelineFlags;
                mWifiViewModel = wifiViewModel;
                mDarkIconDispatcher = darkIconDispatcher;
            }

            public DarkIconManager create(LinearLayout group, StatusBarLocation location) {
                return new DarkIconManager(
                        group,
                        mFeatureFlags,
                        location,
                        mStatusBarPipelineFlags,
                        mWifiViewModel,
                        mDarkIconDispatcher);
            }
        }
    }

    /** */
    class TintedIconManager extends IconManager {
        private int mColor;

        public TintedIconManager(
                ViewGroup group,
                FeatureFlags featureFlags,
                StatusBarLocation location,
                StatusBarPipelineFlags statusBarPipelineFlags,
                WifiViewModel wifiViewModel) {
            super(group,
                    featureFlags,
                    location,
                    statusBarPipelineFlags,
                    wifiViewModel);
        }

        @Override
        protected void onIconAdded(int index, String slot, boolean blocked,
                StatusBarIconHolder holder) {
            StatusIconDisplayable view = addHolder(index, slot, blocked, holder);
            view.setStaticDrawableColor(mColor);
            view.setDecorColor(mColor);
        }

        public void setTint(int color) {
            mColor = color;
            for (int i = 0; i < mGroup.getChildCount(); i++) {
                View child = mGroup.getChildAt(i);
                if (child instanceof StatusIconDisplayable) {
                    StatusIconDisplayable icon = (StatusIconDisplayable) child;
                    icon.setStaticDrawableColor(mColor);
                    icon.setDecorColor(mColor);
                }
            }
        }

        @Override
        protected DemoStatusIcons createDemoStatusIcons() {
            DemoStatusIcons icons = super.createDemoStatusIcons();
            icons.setColor(mColor);
            return icons;
        }

        @SysUISingleton
        public static class Factory {
            private final FeatureFlags mFeatureFlags;
            private final StatusBarPipelineFlags mStatusBarPipelineFlags;
            private final WifiViewModel mWifiViewModel;

            @Inject
            public Factory(
                    FeatureFlags featureFlags,
                    StatusBarPipelineFlags statusBarPipelineFlags,
                    WifiViewModel wifiViewModel) {
                mFeatureFlags = featureFlags;
                mStatusBarPipelineFlags = statusBarPipelineFlags;
                mWifiViewModel = wifiViewModel;
            }

            public TintedIconManager create(ViewGroup group, StatusBarLocation location) {
                return new TintedIconManager(
                        group,
                        mFeatureFlags,
                        location,
                        mStatusBarPipelineFlags,
                        mWifiViewModel);
            }
        }
    }

    /**
     * Turns info from StatusBarIconController into ImageViews in a ViewGroup.
     */
    class IconManager implements DemoModeCommandReceiver, TunerService.Tunable {
        protected final ViewGroup mGroup;
        private final StatusBarLocation mLocation;
        private final StatusBarPipelineFlags mStatusBarPipelineFlags;
        private final WifiViewModel mWifiViewModel;
        private final FeatureFlags mFeatureFlags;
        protected final Context mContext;
        protected final int mIconSize;
        // Whether or not these icons show up in dumpsys
        protected boolean mShouldLog = false;
        private StatusBarIconController mController;

        // Enables SystemUI demo mode to take effect in this group
        protected boolean mDemoable = true;
        private boolean mIsInDemoMode;
        protected DemoStatusIcons mDemoStatusIcons;

        protected ArrayList<String> mBlockList = new ArrayList<>();
        private final boolean mNewIconStyle;
        private final boolean mShowNotificationCount;

        private boolean mOldStyleType;

        private static final String USE_OLD_MOBILETYPE =
            "system:" + Settings.System.USE_OLD_MOBILETYPE;

        public IconManager(
                ViewGroup group,
                FeatureFlags featureFlags,
                StatusBarLocation location,
                StatusBarPipelineFlags statusBarPipelineFlags,
                WifiViewModel wifiViewModel) {
            mGroup = group;
            mFeatureFlags = featureFlags;
            mLocation = location;
            mStatusBarPipelineFlags = statusBarPipelineFlags;
            mWifiViewModel = wifiViewModel;
            mContext = group.getContext();
            mIconSize = mContext.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.status_bar_icon_size);
            mNewIconStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUSBAR_COLORED_ICONS, 0, UserHandle.USER_CURRENT) == 1;
            mShowNotificationCount = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUSBAR_NOTIF_COUNT,
                mContext.getResources().getBoolean(R.bool.config_statusBarShowNumber) ? 1 : 0,
                UserHandle.USER_CURRENT) == 1;
        }

        public boolean isDemoable() {
            return mDemoable;
        }

        public void setIsDemoable(boolean demoable) {
            mDemoable = demoable;
        }

        void setController(StatusBarIconController controller) {
            mController = controller;
        }

        public void setBlockList(@Nullable List<String> blockList) {
            Assert.isMainThread();
            mBlockList.clear();
            mBlockList.addAll(blockList);
            if (mController != null) {
                mController.refreshIconGroup(this);
            }
        }

        public void setShouldLog(boolean should) {
            mShouldLog = should;
        }

        public boolean shouldLog() {
            return mShouldLog;
        }

        protected void onIconAdded(int index, String slot, boolean blocked,
                StatusBarIconHolder holder) {
            addHolder(index, slot, blocked, holder);
        }

        protected StatusIconDisplayable addHolder(int index, String slot, boolean blocked,
                StatusBarIconHolder holder) {
            // This is a little hacky, and probably regrettable, but just set `blocked` on any icon
            // that is in our blocked list, then we'll never see it
            if (mBlockList.contains(slot)) {
                blocked = true;
            }
            switch (holder.getType()) {
                case TYPE_ICON:
                    return addIcon(index, slot, blocked, holder.getIcon());

                case TYPE_WIFI:
                    return addWifiIcon(index, slot, holder.getWifiState());

                case TYPE_MOBILE:
                    return addMobileIcon(index, slot, holder.getMobileState());

                case TYPE_NETWORK_TRAFFIC:
                    return addNetworkTraffic(index, slot);

                case TYPE_BLUETOOTH:
                    return addBluetoothIcon(index, slot, holder.getBluetoothState());
            }

            return null;
        }

        @VisibleForTesting
        protected StatusBarIconView addIcon(int index, String slot, boolean blocked,
                StatusBarIcon icon) {
            StatusBarIconView view = onCreateStatusBarIconView(slot, blocked);
            view.setIconStyle(mNewIconStyle);
            view.setShowCount(mShowNotificationCount);
            view.set(icon);
            mGroup.addView(view, index, onCreateLayoutParams());
            return view;
        }

        @VisibleForTesting
        protected StatusIconDisplayable addWifiIcon(int index, String slot, WifiIconState state) {
            final BaseStatusBarWifiView view;
            if (mStatusBarPipelineFlags.isNewPipelineFrontendEnabled()) {
                view = onCreateModernStatusBarWifiView(slot);
                // When [ModernStatusBarWifiView] is created, it will automatically apply the
                // correct view state so we don't need to call applyWifiState.
            } else {
                StatusBarWifiView wifiView = onCreateStatusBarWifiView(slot);
                wifiView.applyWifiState(state);
                view = wifiView;
            }
            mGroup.addView(view, index, onCreateLayoutParams());

            if (mIsInDemoMode) {
                mDemoStatusIcons.addDemoWifiView(state);
            }
            return view;
        }

        protected NetworkTrafficSB addNetworkTraffic(int index, String slot) {
            NetworkTrafficSB view = onCreateNetworkTraffic(slot);
            mGroup.addView(view, index, onCreateLayoutParams());
            return view;
        }

        @VisibleForTesting
        protected StatusBarMobileView addMobileIcon(int index, String slot, MobileIconState state) {
            StatusBarMobileView view = onCreateStatusBarMobileView(slot);
            view.applyMobileState(state);
            mGroup.addView(view, index, onCreateLayoutParams());
            Dependency.get(TunerService.class).addTunable(this, USE_OLD_MOBILETYPE);

            if (mIsInDemoMode) {
                mDemoStatusIcons.addMobileView(state);
            }
            return view;
        }

        protected StatusBarBluetoothView addBluetoothIcon(
                int index, String slot, BluetoothIconState state) {
            StatusBarBluetoothView view = onCreateStatusBarBluetoothView(slot);
            view.applyBluetoothState(state);
            mGroup.addView(view, index, onCreateLayoutParams());
            return view;
        }

        private StatusBarIconView onCreateStatusBarIconView(String slot, boolean blocked) {
            return new StatusBarIconView(mContext, slot, null, blocked);
        }

        private StatusBarWifiView onCreateStatusBarWifiView(String slot) {
            StatusBarWifiView view = StatusBarWifiView.fromContext(mContext, slot);
            return view;
        }

        private ModernStatusBarWifiView onCreateModernStatusBarWifiView(String slot) {
            return ModernStatusBarWifiView.constructAndBind(
                    mContext, slot, mWifiViewModel, mLocation);
        }

        private StatusBarMobileView onCreateStatusBarMobileView(String slot) {
            StatusBarMobileView view = StatusBarMobileView
                    .fromContext(mContext, slot, mFeatureFlags.isEnabled(Flags.COMBINED_STATUS_BAR_SIGNAL_ICONS));
            return view;
        }

        private NetworkTrafficSB onCreateNetworkTraffic(String slot) {
            NetworkTrafficSB view = new NetworkTrafficSB(mContext);
            view.setPadding(2, 0, 2, 0);
            return view;
        }

        private StatusBarBluetoothView onCreateStatusBarBluetoothView(String slot) {
            StatusBarBluetoothView view = StatusBarBluetoothView.fromContext(mContext, slot);
            return view;
        }

        protected LinearLayout.LayoutParams onCreateLayoutParams() {
            return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize);
        }

        protected void destroy() {
            mGroup.removeAllViews();
            Dependency.get(TunerService.class).removeTunable(this);
        }

        protected void onIconExternal(int viewIndex, int height) {
            ImageView imageView = (ImageView) mGroup.getChildAt(viewIndex);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            setHeightAndCenter(imageView, height);
        }

        protected void onDensityOrFontScaleChanged() {
            for (int i = 0; i < mGroup.getChildCount(); i++) {
                View child = mGroup.getChildAt(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize);
                child.setLayoutParams(lp);
            }
        }

        private void setHeightAndCenter(ImageView imageView, int height) {
            ViewGroup.LayoutParams params = imageView.getLayoutParams();
            params.height = height;
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).gravity = Gravity.CENTER_VERTICAL;
            }
            imageView.setLayoutParams(params);
        }

        protected void onRemoveIcon(int viewIndex) {
            if (mIsInDemoMode) {
                mDemoStatusIcons.onRemoveIcon((StatusIconDisplayable) mGroup.getChildAt(viewIndex));
            }
            mGroup.removeViewAt(viewIndex);
        }

        public void onSetIcon(int viewIndex, StatusBarIcon icon) {
            View view = mGroup.getChildAt(viewIndex);
            if (view instanceof StatusBarIconView) {
                ((StatusBarIconView) view).set(icon);
            }
        }

        public void onSetIconHolder(int viewIndex, StatusBarIconHolder holder) {
            switch (holder.getType()) {
                case TYPE_ICON:
                    View view = mGroup.getChildAt(viewIndex);
                    if (view instanceof StatusBarIconView) {
                        onSetIcon(viewIndex, holder.getIcon());
                    }
                    return;
                case TYPE_WIFI:
                    onSetWifiIcon(viewIndex, holder.getWifiState());
                    return;
                case TYPE_MOBILE:
                    onSetMobileIcon(viewIndex, holder.getMobileState());
                    return;
                case TYPE_BLUETOOTH:
                    onSetBluetoothIcon(viewIndex, holder.getBluetoothState());
                    return;
                default:
                    break;
            }
        }

        public void onSetWifiIcon(int viewIndex, WifiIconState state) {
            View view = mGroup.getChildAt(viewIndex);
            if (view instanceof StatusBarWifiView) {
                ((StatusBarWifiView) view).applyWifiState(state);
            } else if (view instanceof ModernStatusBarWifiView) {
                // ModernStatusBarWifiView will automatically apply state based on its callbacks, so
                // we don't need to call applyWifiState.
            } else {
                throw new IllegalStateException("View at " + viewIndex + " must be of type "
                        + "StatusBarWifiView or ModernStatusBarWifiView");
            }

            if (mIsInDemoMode) {
                mDemoStatusIcons.updateWifiState(state);
            }
        }

        public void onSetMobileIcon(int viewIndex, MobileIconState state) {
            StatusBarMobileView sbView;
            View view = mGroup.getChildAt(viewIndex);
            sbView = null;
            if (view instanceof StatusBarMobileView) {
                sbView = (StatusBarMobileView) view;
            }
            if (sbView != null) {
                sbView.applyMobileState(state);
            }

            if (mIsInDemoMode) {
                mDemoStatusIcons.updateMobileState(state);
            }
        }

        public void onSetBluetoothIcon(int viewIndex, BluetoothIconState state) {
            StatusBarBluetoothView sbView;
            View view = mGroup.getChildAt(viewIndex);
            sbView = null;
            if (view instanceof StatusBarBluetoothView) {
                sbView = (StatusBarBluetoothView) view;
            }
            if (sbView != null) {
                sbView.applyBluetoothState(state);
            }
        }

        @Override
        public void dispatchDemoCommand(String command, Bundle args) {
            if (!mDemoable) {
                return;
            }

            mDemoStatusIcons.dispatchDemoCommand(command, args);
        }

        @Override
        public void onDemoModeStarted() {
            mIsInDemoMode = true;
            if (mDemoStatusIcons == null) {
                mDemoStatusIcons = createDemoStatusIcons();
            }
            mDemoStatusIcons.onDemoModeStarted();
        }

        @Override
        public void onDemoModeFinished() {
            if (mDemoStatusIcons != null) {
                mDemoStatusIcons.onDemoModeFinished();
                exitDemoMode();
                mIsInDemoMode = false;
            }
        }

        protected void exitDemoMode() {
            mDemoStatusIcons.remove();
            mDemoStatusIcons = null;
        }

        protected DemoStatusIcons createDemoStatusIcons() {
            return new DemoStatusIcons((LinearLayout) mGroup, mIconSize, mFeatureFlags);
        }

        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case USE_OLD_MOBILETYPE:
                    mOldStyleType =
                        TunerService.parseIntegerSwitch(newValue, false);
                    updateOldStyleMobileDataIcons();
                    break;
                default:
                    break;
            }
        }

        private void updateOldStyleMobileDataIcons() {
            for (int i = 0; i < mGroup.getChildCount(); i++) {
                View child = mGroup.getChildAt(i);
                if (child instanceof StatusBarMobileView) {
                    ((StatusBarMobileView) child).updateDisplayType(mOldStyleType);
                }
            }
        }
    }
}
