package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.services.IStartActivityProxy;
import net.typeblog.shelter.services.KillerService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.UriForwardProxy;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends AppCompatActivity {
    public static final String BROADCAST_CONTEXT_MENU_CLOSED = "net.typeblog.shelter.broadcast.CONTEXT_MENU_CLOSED";
    public static final String BROADCAST_SEARCH_FILTER_CHANGED = "net.typeblog.shelter.broadcast.SEARCH_FILTER_CHANGED";

    private final ActivityResultLauncher<Void> mStartSetup =
            registerForActivityResult(new SetupWizardActivity.SetupWizardContract(), this::setupWizardCb);
    private final ActivityResultLauncher<Void> mResumeSetup =
            registerForActivityResult(new SetupWizardActivity.ResumeSetupContract(), this::setupWizardCb);
    private final ActivityResultLauncher<Void> mSelectApk =
            registerForActivityResult(
                    new Utility.ActivityResultContractInputWrapper<>(
                            new ActivityResultContracts.OpenDocument(),
                            new String[]{"application/vnd.android.package-archive"}),
                    this::onApkSelected);
    // Logic of the following intents are quite complicated; use the generic contract for more control
    private final ActivityResultLauncher<Intent> mTryStartWorkService =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::tryStartWorkServiceCb);
    private final ActivityResultLauncher<Intent> mBindWorkService =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::bindWorkServiceCb);

    private LocalStorageManager mStorage = null;

    // Flag to avoid double-killing our services while restarting
    private boolean mRestarting = false;

    // Guard so the pager/navigation is only built once, even when several
    // service-binding paths fail and all fall back to the degraded view.
    private boolean mViewBuilt = false;

    // Two services running in main / work profile
    private IShelterService mServiceMain = null;
    private IShelterService mServiceWork = null;

    // Show all applications or not
    // default to false
    boolean mShowAll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.main_toolbar));
        mStorage = LocalStorageManager.getInstance();

        if (getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName())) {
            // We are now in our own profile
            // We should never start the main activity here.
            android.util.Log.d("MainActivity", "started in user profile. stopping.");
            finish();
        } else {
            init();
        }

    }

    private void init() {
        if (mStorage.getBoolean(LocalStorageManager.PREF_IS_SETTING_UP) && !Utility.isWorkProfileAvailable(this)) {
            // System has already finished provisioning, but Shelter still
            // needs to be brought up inside the work profile
            mResumeSetup.launch(null);
        } else if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
            mStartSetup.launch(null);
        } else {
            // Initialize the settings
            SettingsManager.getInstance().applyAll();
            // Initialize the app (start by binding the services)
            bindServices();
        }
    }

    private void setupWizardCb(Boolean result) {
        if (result) {
            init();
            return;
        }
        // Setup was cancelled, or provisioning failed (e.g. the device has no
        // managed-profile support, which is common on emulators).
        // Upstream Shelter simply calls finish() here. With CalculatorActivity
        // as the root of the launcher task, finishing drops the user straight
        // back to the calculator; the next unlock starts the wizard again and
        // the user is trapped in a loop, never reaching the app list.
        // Instead, remember that setup was dismissed and continue into the main
        // screen in a degraded (main-profile-only) mode.
        mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
        mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
        init();
    }

    private void bindServices() {
        // Bind to the service provided by this app in main user
        // The service in main profile doesn't need to be foreground
        // because this activity will hold a ServiceConnection to the service
        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMain = IShelterService.Stub.asInterface(service);
                tryStartWorkService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        }, false);
    }

    private void tryStartWorkService() {
        // Send a dummy intent to the work profile first
        // to determine if work mode is enabled and we CAN start something in that profile.
        // If work mode is disabled when starting this app, we will receive RESULT_CANCELED
        // in the activity result, and we can then prompt the user to enable it
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IllegalStateException e) {
            // This exception implies a missing work profile, NOT a disabled work
            // profile. Upstream Shelter resets the setup flag and finishes here,
            // which throws the user back to the calculator and restarts the
            // setup wizard on the next unlock. Keep the flag, show a hint and
            // stay on the main screen with the main-profile app list instead.
            Toast.makeText(this, getString(R.string.work_profile_not_found), Toast.LENGTH_LONG).show();
            buildViewSafely();
            return;
        }
        mTryStartWorkService.launch(intent);
    }

    private void tryStartWorkServiceCb(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // RESULT_OK is from DummyActivity. The work profile is enabled!
            bindWorkService();
        } else {
            // The user has been presented with a prompt to enable work mode.
            // Do not finish (that would bounce back to the calculator); show
            // the main-profile app list in degraded mode instead.
            Toast.makeText(this,
                    getString(R.string.work_mode_disabled), Toast.LENGTH_LONG).show();
            buildViewSafely();
        }
    }

    private void bindWorkService() {
        // Bind to the ShelterService in work profile
        Intent intent = new Intent(DummyActivity.START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Utility.transferIntentToProfile(this, intent);
        mBindWorkService.launch(intent);
    }

    private void bindWorkServiceCb(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Bundle extra = result.getData().getBundleExtra("extra");
            IBinder binder = extra.getBinder("service");
            mServiceWork = IShelterService.Stub.asInterface(binder);
            registerStartActivityProxies();
            startKiller();
            buildViewSafely();
        } else {
            // Could not bind the work-profile service: still show the main screen.
            buildViewSafely();
        }
    }

    private void startKiller() {
        // Start the sticky KillerService to kill the ShelterService
        // for us when we are removed from tasks
        // This is a dirty hack because no lifecycle events will be
        // called when task is removed from recents
        Intent intent = new Intent(this, KillerService.class);
        Bundle bundle = new Bundle();
        bundle.putBinder("main", mServiceMain.asBinder());
        bundle.putBinder("work", mServiceWork.asBinder());
        intent.putExtra("extra", bundle);
        startService(intent);
    }

    private void buildViewSafely() {
        if (mViewBuilt) return;
        mViewBuilt = true;
        buildView();
    }

    private void buildView() {
        // Finally we can build the view
        // Find all the views
        ViewPager2 pager = findViewById(R.id.main_pager);
        BottomNavigationView nav = findViewById(R.id.main_bottom_navigation);

        // Without a work profile we can only show the main-profile list.
        if (mServiceWork == null) {
            MenuItem workTab = nav.getMenu().findItem(R.id.bottom_navigation_work);
            if (workTab != null) workTab.setVisible(false);
        }

        // Initialize the ViewPager and the tab
        // All the remaining work will be done in the fragments
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return AppListFragment.newInstance(mServiceMain, false);
                } else if (position == 1) {
                    return AppListFragment.newInstance(mServiceWork, true);
                } else {
                    throw new RuntimeException("How did this happen?");
                }
            }

            @Override
            public int getItemCount() {
                return mServiceWork == null ? 1 : 2;
            }
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int[] menuIds = new int[]{
                        R.id.bottom_navigation_main,
                        R.id.bottom_navigation_work
                };
                nav.setSelectedItemId(menuIds[position]);
            }
        });
        nav.setOnItemSelectedListener((MenuItem item) -> {
            int itemId = item.getItemId();
            if (itemId == R.id.bottom_navigation_main) {
                pager.setCurrentItem(0);
            } else if (itemId == R.id.bottom_navigation_work) {
                pager.setCurrentItem(1);
            }
            return true;
        });
    }

    // Get the service on the other side
    // remote (work) -> main
    // main -> remote (work)
    IShelterService getOtherService(boolean isRemote) {
        return isRemote ? mServiceMain : mServiceWork;
    }

    boolean servicesAlive() {
        try {
            mServiceMain.ping();
        } catch (Exception e) {
            return false;
        }

        try {
            mServiceWork.ping();
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    private void registerStartActivityProxies() {
        try {
            mServiceMain.setStartActivityProxy(new IStartActivityProxy.Stub() {
                @Override
                public void startActivity(Intent intent) throws RemoteException {
                    MainActivity.this.startActivity(intent);
                }
            });

            mServiceWork.setStartActivityProxy(new IStartActivityProxy.Stub() {
                @Override
                public void startActivity(Intent intent) throws RemoteException {
                    // Using the full intent may cause the package manager to
                    // fail to find the DummyActivity inside profile.
                    // Instead we try to use an empty intent with only the action
                    // and then extract the correct component name
                    Intent dummyIntent = new Intent(intent.getAction());
                    Utility.transferIntentToProfileUnsigned(MainActivity.this, dummyIntent);
                    intent.setComponent(dummyIntent.getComponent());
                    MainActivity.this.startActivity(intent);
                }
            });
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mServiceMain != null && mServiceWork != null && !servicesAlive()) {
            // First, ensure that the services are killed before we restart
            // Otherwise, the system will reuse the services and the new activity
            // will end up depending on those old services that we are going to kill
            // in onDestroy()
            doOnDestroy();

            // Tell the onDestroy() logic that we are restarting. Do not kill the
            // KillerService again because the new activity will be starting a new one
            mRestarting = true;

            // Restart the activity if the services are no longer alive
            // This might be caused by KillerService being destroyed and
            // bringing all the other services with it
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        // Go back to the calculator (the disguised launcher) instead of exiting
        // straight to the home screen.
        Intent intent = new Intent(this, CalculatorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // NOTE: Do NOT override onUserLeaveHint() here to force a "lock".
    // MainActivity starts SetupWizardActivity (and other activities) internally;
    // any startActivity() makes the system call onUserLeaveHint() on the outgoing
    // activity, so such a hook would kick the user back to the calculator right in
    // the middle of Shelter's setup flow. Returning to the calculator on a real
    // "leave the app" is handled by the launcher task instead:
    // CalculatorActivity (singleTask + clearTaskOnLaunch) stays as the task root,
    // so re-launching from the home screen finishes everything above it.

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // DO NOT kill anything if we are restarting
        // by the time this method is called, the new
        // activity could have started those services
        // again. We will mess up the new activity
        // if we kill again.
        if (!mRestarting)
            doOnDestroy();
    }

    private void doOnDestroy() {
        // If the activity is stopped first, then kill the KillerService
        // to avoid double-free
        stopService(new Intent(this, KillerService.class));

        Utility.killShelterServices(mServiceMain, mServiceWork);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Upstream Shelter finishes here so the work-profile foreground service
        // dies with the activity. In the disguised build that is unsafe:
        // provisioning runs in a system Activity, so our UI is briefly fully
        // hidden and TRIM_MEMORY_BACKGROUND would destroy MainActivity while
        // its setup-wizard ActivityResult is still pending, breaking the unlock
        // flow. Only keep the old behaviour in the real two-profile mode.
        if (level >= TRIM_MEMORY_BACKGROUND && mServiceMain != null && mServiceWork != null) {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);

        // Initialize the search button
        SearchView searchView = (SearchView) menu.findItem(R.id.main_menu_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Intent intent = new Intent(BROADCAST_SEARCH_FILTER_CHANGED);
                intent.putExtra("text", newText.toLowerCase().trim());
                LocalBroadcastManager.getInstance(MainActivity.this)
                        .sendBroadcast(intent);
                return true;
            }
        });
        return true;
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(BROADCAST_CONTEXT_MENU_CLOSED));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.main_menu_freeze_all) {
            // This is the same as clicking on the batch freeze shortcut
            // so we just forward the request to DummyActivity
            Intent intent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
            intent.setComponent(new ComponentName(this, DummyActivity.class));
            startActivity(intent);
            return true;
        } else if (itemId == R.id.main_menu_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            Bundle extras = new Bundle();
            extras.putBinder("profile_service", mServiceWork.asBinder());
            settingsIntent.putExtra("extras", extras);
            startActivity(settingsIntent);
            return true;
        } else if (itemId == R.id.main_menu_create_freeze_all_shortcut) {
            Intent launchIntent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
            launchIntent.setComponent(new ComponentName(this, DummyActivity.class));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Utility.createLauncherShortcut(this, launchIntent,
                    Icon.createWithResource(this, R.mipmap.ic_freeze),
                    "shelter-freeze-all", getString(R.string.freeze_all_shortcut));
            return true;
        } else if (itemId == R.id.main_menu_install_app_to_profile) {
            mSelectApk.launch(null);
            return true;
        } else if (itemId == R.id.main_menu_show_all) {
            Runnable update = () -> {
                mShowAll = !item.isChecked();
                item.setChecked(mShowAll);
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(AppListFragment.BROADCAST_REFRESH));
            };

            if (!item.isChecked()) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.show_all_warning)
                        .setPositiveButton(R.string.first_run_alert_continue,
                                (dialog, which) -> update.run())
                        .setNegativeButton(R.string.first_run_alert_cancel, null)
                        .show();
            } else {
                update.run();
            }
            return true;
        } else if (itemId == R.id.main_menu_documents_ui) {
            Intent documentsUiIntent = new Intent(Intent.ACTION_VIEW);
            documentsUiIntent.setDataAndType(null, "vnd.android.document/root");
            startActivity(documentsUiIntent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void onApkSelected(Uri uri) {
        if (uri == null) return;
        UriForwardProxy proxy = new UriForwardProxy(getApplicationContext(), uri);

        try {
            mServiceWork.installApk(proxy, new IAppInstallCallback.Stub() {
                @Override
                public void callback(int result) {
                    runOnUiThread(() -> {
                        // The other side will have closed the Fd for us
                        if (result == RESULT_OK)
                            Toast.makeText(MainActivity.this,
                                    R.string.install_app_to_profile_success, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (RemoteException e) {
            // Well, I don't know what to do then
        }
    }
}
