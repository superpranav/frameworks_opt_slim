/*
* Copyright (C) 2016 SlimRoms Project
* Copyright (C) 2013-14 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.systemui.statusbar.slim;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.slimrecent.RecentController;
import com.android.systemui.statusbar.phone.SlimNavigationBarView;
import com.android.systemui.statusbar.phone.NavigationBarView;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;

import org.slim.provider.SlimSettings;

import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_OPAQUE;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSLUCENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_WARNING;

public class SlimStatusBar extends PhoneStatusBar {

    private static final String TAG = SlimStatusBar.class.getSimpleName();

    private PhoneStatusBarView mStatusBarView;
    private SlimNavigationBarView mSlimNavigationBarView;
    private RecentController mSlimRecents;
    private Display mDisplay;

    private SlimStatusBarIconController mSlimIconController;

    private boolean mHasNavigationBar = false;
    private boolean mNavigationBarAttached = false;
    private boolean mDisableHomeLongpress = false;

    private ImageView mMultiUserAvatar;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.USE_SLIM_RECENTS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.RECENT_CARD_BG_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.RECENT_CARD_TEXT_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_BUTTON_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_BUTTON_TINT_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_GLOW_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_SHOW),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_CONFIG),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_CAN_MOVE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.MENU_LOCATION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.MENU_VISIBILITY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_TIMEOUT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ALPHA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ANIMATE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ANIMATE_DURATION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_TOUCH_ANYWHERE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.USE_SLIM_RECENTS))) {
                updateRecents();
            } else if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.RECENT_CARD_BG_COLOR))
                    || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.RECENT_CARD_TEXT_COLOR))) {
                rebuildRecentsScreen();
            } else if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_BUTTON_TINT))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_BUTTON_TINT_MODE))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_CONFIG))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_GLOW_TINT))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.MENU_LOCATION))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.MENU_VISIBILITY))) {
                if (mSlimNavigationBarView != null) {
                    mSlimNavigationBarView.recreateNavigationBar();
                    prepareNavigationBarView();
                }
            } else if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_CAN_MOVE))) {
                prepareNavigationBarView();
            } else if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_TIMEOUT))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ALPHA))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ANIMATE))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_ANIMATE_DURATION))
                || uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.DIM_NAV_BUTTONS_TOUCH_ANYWHERE))) {
                if (mSlimNavigationBarView != null) {
                    mSlimNavigationBarView.updateNavigationBarSettings();
                    mSlimNavigationBarView.onNavButtonTouched();
                }
            } else if (uri.equals(SlimSettings.System.getUriFor(
                    SlimSettings.System.NAVIGATION_BAR_SHOW))) {
                updateNavigationBarVisibility();
            }
        }
    }

    @Override
    public void start() {
        super.start();

        mDisplay = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();

        updateNavigationBarVisibility();

        updateRecents();

        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observe();
    }

    @Override
    protected PhoneStatusBarView makeStatusBarView() {
        mStatusBarView = super.makeStatusBarView();

        if (mSlimNavigationBarView == null) {
            mSlimNavigationBarView = (SlimNavigationBarView)
                    View.inflate(mContext, R.layout.slim_navigation_bar, null);
        }
        mSlimNavigationBarView.setDisabledFlags(mDisabled1);
        mSlimNavigationBarView.setBar(this);
        mSlimNavigationBarView.setOnVerticalChangedListener(
                new NavigationBarView.OnVerticalChangedListener() {
            @Override
            public void onVerticalChanged(boolean isVertical) {
                if (mAssistManager != null) {
                    mAssistManager.onConfigurationChanged();
                }
                mNotificationPanel.setQsScrimEnabled(!isVertical);
            }
        });
        mSlimNavigationBarView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                checkUserAutohide(v, event);
                return false;
            }
        });

        if (!(mNavigationBarView instanceof SlimNavigationBarView) && mNavigationBarView != null) {
            if (mNavigationBarView.isAttachedToWindow()) {
                try {
                    mWindowManager.removeView(mNavigationBarView);
                } catch (Exception e) {}
            }
        }

        if (mNavigationBarView != mSlimNavigationBarView) {
            mNavigationBarView = mSlimNavigationBarView;
        }

        SlimBatteryContainer container =(SlimBatteryContainer) mStatusBarView.findViewById(
                R.id.slim_battery_container);
        if (mBatteryController != null) {
            container.setBatteryController(mBatteryController);
        }

        mSlimIconController = new SlimStatusBarIconController(mContext, mStatusBarView, this);

        mMultiUserAvatar = (ImageView) mHeader.findViewById(R.id.multi_user_avatar);
        mMultiUserAvatar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings",
                        "com.android.settings.Settings$NotificationStationActivity");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                animateCollapsePanels();
                mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                return true;
            }
        });


        return mStatusBarView;
    }

    private void updateNavigationBarVisibility() {
        final int showByDefault = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_showNavigationBar) ? 1 : 0;
        mHasNavigationBar = SlimSettings.System.getIntForUser(mContext.getContentResolver(),
                    SlimSettings.System.NAVIGATION_BAR_SHOW, showByDefault,
                    UserHandle.USER_CURRENT) == 1;

        if (mHasNavigationBar) {
            addNavigationBar();
        } else {
            if (mNavigationBarAttached) {
                mNavigationBarAttached = false;
                mWindowManager.removeView(mSlimNavigationBarView);
            }
        }
    }

    @Override
    protected void prepareNavigationBarView() {
        mSlimNavigationBarView.reorient();

        View home = mSlimNavigationBarView.getHomeButton();
        View recents = mSlimNavigationBarView.getRecentsButton();

        mSlimNavigationBarView.setPinningCallback(mLongClickCallback);

        /*if (recents != null) {
            recents.setOnClickListener(mRecentsClickListener);
            recents.setOnTouchListener(mRecentsPreloadOnTouchListener);
        }
        if (home != null) {
            home.setOnTouchListener(mHomeActionListener);
        }*/

        mAssistManager.onConfigurationChanged();
    }

    @Override
    protected void addNavigationBar() {
        if (DEBUG) Log.v(TAG, "addNavigationBar: about to add " + mSlimNavigationBarView);
        if (mSlimNavigationBarView == null) return;

        prepareNavigationBarView();

        if (!mNavigationBarAttached) {
            mNavigationBarAttached = true;
            try {
                mWindowManager.addView(mSlimNavigationBarView, getNavigationBarLayoutParams());
            } catch (Exception e) {}
        }
    }

    @Override
    protected void repositionNavigationBar() {
        if (mSlimNavigationBarView == null
                || !mSlimNavigationBarView.isAttachedToWindow()) return;

        prepareNavigationBarView();

        mWindowManager.updateViewLayout(mSlimNavigationBarView, getNavigationBarLayoutParams());
    }

    private WindowManager.LayoutParams getNavigationBarLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        // this will allow the navbar to run in an overlay on devices that support this
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("NavigationBar");
        lp.windowAnimations = 0;
        return lp;
    }

    @Override
    public void setSystemUiVisibility(int vis, int mask) {
        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask | vis&mask);
        final int diff = newVal ^ oldVal;

        if (diff != 0) {
            final int sbMode = computeBarMode(oldVal, newVal, mStatusBarView.getBarTransitions(),
                    View.STATUS_BAR_TRANSIENT, View.STATUS_BAR_TRANSLUCENT);
            final boolean sbModeChanged = sbMode != -1;
            if ((diff & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0 || sbModeChanged) {
                boolean isTransparentBar = (mStatusBarMode == MODE_TRANSPARENT
                        || sbMode == MODE_LIGHTS_OUT_TRANSPARENT);
                boolean allowLight = isTransparentBar;
                boolean light = (vis & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0;
                boolean animate = true;/*mFingerprintUnlockController == null
                        || (mFingerprintUnlockController.getMode()
                                != FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING
                        && mFingerprintUnlockController.getMode()
                                != FingerprintUnlockController.MODE_WAKE_AND_UNLOCK);*/

                mSlimIconController.setIconsDark(allowLight && light, animate);
            }
        }

        super.setSystemUiVisibility(vis, mask);
    }

    private long mLastLockToAppLongPress;
    private SlimKeyButtonView.LongClickCallback mLongClickCallback =
            new SlimKeyButtonView.LongClickCallback() {
        @Override
        public boolean onLongClick(View v) {
            return handleLongPressBackRecents(v);
        }
    };

    private boolean handleLongPressBackRecents(View v) {
        try {
            boolean sendBackLongPress = false;
            IActivityManager activityManager = ActivityManagerNative.getDefault();
            boolean isAccessiblityEnabled = mAccessibilityManager.isEnabled();
            if (activityManager.isInLockTaskMode() && !isAccessiblityEnabled) {
                // If we recently long-pressed the other button then they were
                // long-pressed 'together'
                if (mSlimNavigationBarView.getRightMenuButton().isPressed()
                        && mSlimNavigationBarView.getLeftMenuButton().isPressed()) {
                    activityManager.stopLockTaskModeOnCurrent();
                    // When exiting refresh disabled flags.
                    mSlimNavigationBarView.setDisabledFlags(mDisabled1, true);
                    mSlimNavigationBarView.setOverrideMenuKeys(false);
                } else if ((v.getId() == mSlimNavigationBarView.getLeftMenuButton().getId())
                        && !mSlimNavigationBarView.getRightMenuButton().isPressed()) {
                    // If we aren't pressing recents right now then they presses
                    // won't be together, so send the standard long-press action.
                    sendBackLongPress = true;
                }
            } else {
                // If this is back still need to handle sending the long-press event.
                long time = System.currentTimeMillis();
                if (( time - mLastLockToAppLongPress) < 2000) {
                    if (v.getId() == mSlimNavigationBarView.getLeftMenuButton().getId()
                        || v.getId() == mSlimNavigationBarView.getRightMenuButton().getId()) {
                        sendBackLongPress = true;
                    }
                } else if (isAccessiblityEnabled && activityManager.isInLockTaskMode()) {
                    // When in accessibility mode a long press that is recents (not back)
                    // should stop lock task.
                    activityManager.stopLockTaskModeOnCurrent();
                    // When exiting refresh disabled flags.
                    mSlimNavigationBarView.setDisabledFlags(mDisabled1, true);
                    mSlimNavigationBarView.setOverrideMenuKeys(false);
                }
                mLastLockToAppLongPress = time;
            }
            return sendBackLongPress;
        } catch (RemoteException e) {
            Log.d(TAG, "Unable to reach activity manager", e);
            return false;
        }
    }

    @Override
    protected void hideRecents(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        if (mSlimRecents != null) {
            mSlimRecents.hideRecents(triggeredFromHomeKey);
        } else {
            super.hideRecents(triggeredFromAltTab, triggeredFromHomeKey);
        }
    }

    @Override
    protected void toggleRecents() {
        if (mSlimRecents != null) {
            sendCloseSystemWindows(mContext, SYSTEM_DIALOG_REASON_RECENT_APPS);
            mSlimRecents.toggleRecents(mDisplay, mLayoutDirection, getStatusBarView());
        } else {
            super.toggleRecents();
        }
    }

    @Override
    protected void preloadRecents() {
        if (mSlimRecents != null) {
            mSlimRecents.preloadRecentTasksList();
        } else {
            super.preloadRecents();
        }
    }

    @Override
    protected void cancelPreloadingRecents() {
        if (mSlimRecents != null) {
            mSlimRecents.cancelPreloadingRecentTasksList();
        } else {
            super.cancelPreloadingRecents();
        }
    }

    protected void rebuildRecentsScreen() {
        if (mSlimRecents != null) {
            mSlimRecents.rebuildRecentsScreen();
        }
    }

    protected void updateRecents() {
        boolean slimRecents = SlimSettings.System.getIntForUser(mContext.getContentResolver(),
                SlimSettings.System.USE_SLIM_RECENTS, 1, UserHandle.USER_CURRENT) == 1;

        if (slimRecents) {
            mSlimRecents = new RecentController(mContext, mLayoutDirection);
            mSlimRecents.setCallback(this);
            rebuildRecentsScreen();
        } else {
            mSlimRecents = null;
        }
    }

    private static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }
}
