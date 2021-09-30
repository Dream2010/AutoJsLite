package org.autojs.autojs.ui.main.drawer;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.AppOpsKt;
import com.stardust.app.GlobalAppContext;
import com.stardust.notification.NotificationListenerService;
import com.stardust.util.IntentUtil;
import com.stardust.view.accessibility.AccessibilityService;

import org.autojs.autojs.Pref;
import org.autojs.autojs.R;
import org.autojs.autojs.external.foreground.ForegroundService;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.tool.AccessibilityServiceTool;
import org.autojs.autojs.tool.Observers;
import org.autojs.autojs.tool.WifiTool;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.floating.CircularMenu;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.settings.SettingsActivity;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by Stardust on 2017/1/30.
 * TODO these codes are so ugly!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
public class DrawerFragment extends androidx.fragment.app.Fragment {
    View mShadow;
    RecyclerView mDrawerMenu;


    private DrawerMenuItem mConnectionItem = new DrawerMenuItem(R.drawable.ic_connect_to_pc, R.string.debug, 0, this::connectOrDisconnectToRemote);
    private DrawerMenuItem mAccessibilityServiceItem = new DrawerMenuItem(R.drawable.ic_service_green, R.string.text_accessibility_service, 0, this::enableOrDisableAccessibilityService);
    private DrawerMenuItem mStableModeItem = new DrawerMenuItem(R.drawable.ic_stable, R.string.text_stable_mode, R.string.key_stable_mode, null) {
        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            if (checked)
                showStableModePromptIfNeeded();
        }
    };

    private DrawerMenuItem mNotificationPermissionItem = new DrawerMenuItem(R.drawable.ic_ali_notification, R.string.text_notification_permission, 0, this::goToNotificationServiceSettings);
    private DrawerMenuItem mUsageStatsPermissionItem = new DrawerMenuItem(R.drawable.ic_ali_notification, R.string.text_usage_stats_permission, 0, this::goToUsageStatsSettings);
    private DrawerMenuItem mForegroundServiceItem = new DrawerMenuItem(R.drawable.ic_service_green, R.string.text_foreground_service, R.string.key_foreground_servie, this::toggleForegroundService);

    private DrawerMenuItem mFloatingWindowItem = new DrawerMenuItem(R.drawable.ic_robot_64, R.string.text_floating_window, 0, this::showOrDismissFloatingWindow);

    private DrawerMenuAdapter mDrawerMenuAdapter;
    private Disposable mConnectionStateDisposable;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnectionStateDisposable = DevPluginService.getInstance().connectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    if (mConnectionItem != null) {
                        setChecked(mConnectionItem, state.getState() == DevPluginService.State.CONNECTED);
                        setProgress(mConnectionItem, state.getState() == DevPluginService.State.CONNECTING);
                    }
                    if (state.getException() != null) {
                        showMessage(state.getException().getMessage());
                    }
                });
        EventBus.getDefault().register(this);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drawer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mShadow = view.findViewById(R.id.shadow);
        mDrawerMenu = view.findViewById(R.id.drawer_menu);
        setUpViews();
    }

    void setUpViews() {
        initMenuItems();
        if (Pref.isFloatingMenuShown()) {
            FloatyWindowManger.showCircularMenuIfNeeded();
            setChecked(mFloatingWindowItem, true);
        }
        setChecked(mConnectionItem, DevPluginService.getInstance().isConnected());
        if (Pref.isForegroundServiceEnabled()) {
            ForegroundService.start(GlobalAppContext.get());
            setChecked(mForegroundServiceItem, true);
        }
    }

    private void initMenuItems() {
        mDrawerMenuAdapter = new DrawerMenuAdapter(new ArrayList<>(Arrays.asList(
                new DrawerMenuGroup(R.string.text_service),
                mAccessibilityServiceItem,
                mStableModeItem,
                mNotificationPermissionItem,
                mForegroundServiceItem,
                mUsageStatsPermissionItem,
                mFloatingWindowItem,

                new DrawerMenuGroup(R.string.text_others),
                mConnectionItem
        )));
        mDrawerMenu.setAdapter(mDrawerMenuAdapter);
        mDrawerMenu.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    void enableOrDisableAccessibilityService(DrawerMenuItemViewHolder holder) {
        boolean isAccessibilityServiceEnabled = isAccessibilityServiceEnabled();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !isAccessibilityServiceEnabled) {
            enableAccessibilityService();
        } else if (!checked && isAccessibilityServiceEnabled) {
            if (!AccessibilityService.Companion.disable()) {
                AccessibilityServiceTool.goToAccessibilitySetting();
            }
        }
    }

    void goToNotificationServiceSettings(DrawerMenuItemViewHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return;
        }
        boolean enabled = NotificationListenerService.Companion.getInstance() != null;
        boolean checked = holder.getSwitchCompat().isChecked();
        if ((checked && !enabled) || (!checked && enabled)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    void goToUsageStatsSettings(DrawerMenuItemViewHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        boolean enabled = AppOpsKt.isOpPermissionGranted(getContext(), AppOpsManager.OPSTR_GET_USAGE_STATS);
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !enabled) {
            if (new NotAskAgainDialog.Builder(getContext(), "DrawerFragment.usage_stats")
                    .title(R.string.text_usage_stats_permission)
                    .content(R.string.description_usage_stats_permission)
                    .positiveText(R.string.ok)
                    .dismissListener(dialog -> IntentUtil.requestAppUsagePermission(getContext()))
                    .show() == null) {
                IntentUtil.requestAppUsagePermission(getContext());
            }
        }
        if (!checked && enabled) {
            IntentUtil.requestAppUsagePermission(getContext());
        }
    }

    void showOrDismissFloatingWindow(DrawerMenuItemViewHolder holder) {
        boolean isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (getActivity() != null && !getActivity().isFinishing()) {
            Pref.setFloatingMenuShown(checked);
        }
        if (checked && !isFloatingWindowShowing) {
            setChecked(mFloatingWindowItem, FloatyWindowManger.showCircularMenu());
            enableAccessibilityServiceByRootIfNeeded();
        } else if (!checked && isFloatingWindowShowing) {
            FloatyWindowManger.hideCircularMenu();
        }
    }

    @SuppressLint("CheckResult")
    private void enableAccessibilityServiceByRootIfNeeded() {
        Observable.fromCallable(() -> Pref.shouldEnableAccessibilityServiceByRoot() && !isAccessibilityServiceEnabled())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(needed -> {
                    if (needed) {
                        enableAccessibilityServiceByRoot();
                    }
                });

    }

    void connectOrDisconnectToRemote(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        boolean connected = DevPluginService.getInstance().isConnected();
        if (checked && !connected) {
            inputRemoteHost();
        } else if (!checked && connected) {
            DevPluginService.getInstance().disconnectIfNeeded();
        }
    }


    private void toggleForegroundService(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked) {
            ForegroundService.start(GlobalAppContext.get());
        } else {
            ForegroundService.stop(GlobalAppContext.get());
        }
    }


    private void inputRemoteHost() {
        String host = Pref.getServerAddressOrDefault(WifiTool.getRouterIp(getActivity()));
        new MaterialDialog.Builder(getActivity())
                .title(R.string.text_server_address)
                .input("", host, (dialog, input) -> {
                    Pref.saveServerAddress(input.toString());
                    DevPluginService.getInstance().connectToServer(input.toString())
                            .subscribe(Observers.emptyConsumer(), this::onConnectException);
                })
                .cancelListener(dialog -> setChecked(mConnectionItem, false))
                .show();
    }

    private void onConnectException(Throwable e) {
        setChecked(mConnectionItem, false);
        Toast.makeText(GlobalAppContext.get(), getString(R.string.error_connect_to_remote, e.getMessage()),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncSwitchState();
    }

    private void syncSwitchState() {
        setChecked(mAccessibilityServiceItem, AccessibilityServiceTool.isAccessibilityServiceEnabled(getActivity()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setChecked(mNotificationPermissionItem, NotificationListenerService.Companion.getInstance() != null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setChecked(mUsageStatsPermissionItem, AppOpsKt.isOpPermissionGranted(getContext(), AppOpsManager.OPSTR_GET_USAGE_STATS));
        }
    }

    private void enableAccessibilityService() {
        if (!Pref.shouldEnableAccessibilityServiceByRoot()) {
            AccessibilityServiceTool.goToAccessibilitySetting();
            return;
        }
        enableAccessibilityServiceByRoot();
    }

    private void enableAccessibilityServiceByRoot() {
        setProgress(mAccessibilityServiceItem, true);
        Observable.fromCallable(() -> AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(4000))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(succeed -> {
                    if (!succeed) {
                        Toast.makeText(getContext(), R.string.text_enable_accessibitliy_service_by_root_failed, Toast.LENGTH_SHORT).show();
                        AccessibilityServiceTool.goToAccessibilitySetting();
                    }
                    setProgress(mAccessibilityServiceItem, false);
                });
    }


    @Subscribe
    public void onCircularMenuStateChange(CircularMenu.StateChangeEvent event) {
        setChecked(mFloatingWindowItem, event.getCurrentState() != CircularMenu.STATE_CLOSED);
    }

    private void showStableModePromptIfNeeded() {
        new NotAskAgainDialog.Builder(getContext(), "DrawerFragment.stable_mode")
                .title(R.string.text_stable_mode)
                .content(R.string.description_stable_mode)
                .positiveText(R.string.ok)
                .show();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mConnectionStateDisposable.dispose();
        EventBus.getDefault().unregister(this);
    }


    private void showMessage(CharSequence text) {
        if (getContext() == null)
            return;
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }


    private void setProgress(DrawerMenuItem item, boolean progress) {
        item.setProgress(progress);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private void setChecked(DrawerMenuItem item, boolean checked) {
        item.setChecked(checked);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private boolean isAccessibilityServiceEnabled() {
        return AccessibilityServiceTool.isAccessibilityServiceEnabled(getActivity());
    }

}
