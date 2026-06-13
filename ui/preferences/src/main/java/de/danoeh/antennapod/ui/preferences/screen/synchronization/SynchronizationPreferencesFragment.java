package de.danoeh.antennapod.ui.preferences.screen.synchronization;

import android.app.Activity;
import android.os.Bundle;
import android.text.Spanned;
import android.text.format.DateUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.text.HtmlCompat;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.Locale;
import java.util.concurrent.Executors;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.service.podhopper.PodHopperPairing;
import de.danoeh.antennapod.net.sync.service.podhopper.SupabaseClient;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.ui.preferences.R;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;

public class SynchronizationPreferencesFragment extends AnimatedPreferenceFragment {
    private static final String PREFERENCE_SYNCHRONIZATION_DESCRIPTION = "preference_synchronization_description";
    private static final String PREFERENCE_GPODNET_SETLOGIN_INFORMATION = "pref_gpodnet_setlogin_information";
    private static final String PREFERENCE_SYNC = "pref_synchronization_sync";
    private static final String PREFERENCE_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync";
    private static final String PREFERENCE_LOGOUT = "pref_synchronization_logout";
    private static final String PREFERENCE_PODHOPPER_PAIR = "pref_podhopper_pair_device";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_synchronization);
        setupScreen();
        updateScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.synchronization_pref);
        updateScreen();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle("");
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void syncStatusChanged(SyncServiceEvent event) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return;
        }
        updateScreen();
        if (event.getMessageResId() == R.string.sync_status_error
                || event.getMessageResId() == R.string.sync_status_success) {
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful(),
                    SynchronizationSettings.getLastSyncAttempt());
        } else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(event.getMessageResId());
        }
    }

    private void setupScreen() {
        final Activity activity = getActivity();
        findPreference(PREFERENCE_PODHOPPER_PAIR).setOnPreferenceClickListener(preference -> {
            showPairDeviceDialog();
            return true;
        });
        findPreference(PREFERENCE_GPODNET_SETLOGIN_INFORMATION)
                .setOnPreferenceClickListener(preference -> {
                    AuthenticationDialog dialog = new AuthenticationDialog(activity,
                            R.string.pref_gpodnet_setlogin_information_title,
                            false, SynchronizationCredentials.getUsername(), null) {
                        @Override
                        protected void onConfirmed(String username, String password) {
                            SynchronizationCredentials.setPassword(password);
                        }
                    };
                    dialog.show();
                    return true;
                });
        findPreference(PREFERENCE_SYNC).setOnPreferenceClickListener(preference -> {
            SynchronizationQueue.getInstance().syncImmediately();
            return true;
        });
        findPreference(PREFERENCE_FORCE_FULL_SYNC).setOnPreferenceClickListener(preference -> {
            SynchronizationQueue.getInstance().fullSync();
            return true;
        });
        findPreference(PREFERENCE_LOGOUT).setOnPreferenceClickListener(preference -> {
            SynchronizationCredentials.clear();
            SynchronizationQueue.getInstance().clear();
            Snackbar.make(getView(), R.string.pref_synchronization_logout_toast, Snackbar.LENGTH_LONG).show();
            SynchronizationSettings.setSelectedSyncProvider(null);
            updateScreen();
            if (getActivity() != null && getActivity().getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                getActivity().finish();
            }
            return true;
        });
    }

    private void updateScreen() {
        final boolean loggedIn = SynchronizationSettings.isProviderConnected();
        Preference preferenceHeader = findPreference(PREFERENCE_SYNCHRONIZATION_DESCRIPTION);
        if (loggedIn) {
            SynchronizationProvider selectedProvider =
                    SynchronizationProvider.fromIdentifier(getSelectedSyncProviderKey());
            preferenceHeader.setTitle("");
            preferenceHeader.setSummary(getProviderSummary(selectedProvider));
            preferenceHeader.setIcon(getProviderIcon(selectedProvider));
            preferenceHeader.setOnPreferenceClickListener(null);
        } else {
            preferenceHeader.setTitle(R.string.synchronization_choose_title);
            preferenceHeader.setSummary(R.string.synchronization_summary_unchoosen);
            preferenceHeader.setIcon(null);
            preferenceHeader.setOnPreferenceClickListener((preference) -> {
                chooseProviderAndLogin();
                return true;
            });
        }

        findPreference(PREFERENCE_PODHOPPER_PAIR).setVisible(
                loggedIn && isProviderSelected(SynchronizationProvider.PODHOPPER));
        Preference gpodnetSetLoginPreference = findPreference(PREFERENCE_GPODNET_SETLOGIN_INFORMATION);
        gpodnetSetLoginPreference.setVisible(isProviderSelected(SynchronizationProvider.GPODDER_NET));
        gpodnetSetLoginPreference.setEnabled(loggedIn);
        findPreference(PREFERENCE_SYNC).setEnabled(loggedIn);
        findPreference(PREFERENCE_FORCE_FULL_SYNC).setEnabled(loggedIn);
        findPreference(PREFERENCE_LOGOUT).setEnabled(loggedIn);
        if (loggedIn) {
            String summary = getString(R.string.synchronization_login_status,
                    SynchronizationCredentials.getUsername(), SynchronizationCredentials.getHosturl());
            Spanned formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY);
            findPreference(PREFERENCE_LOGOUT).setSummary(formattedSummary);
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful(),
                    SynchronizationSettings.getLastSyncAttempt());
        } else {
            findPreference(PREFERENCE_LOGOUT).setSummary(null);
            ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(null);
        }
    }

    private void chooseProviderAndLogin() {
        new PodHopperAuthenticationFragment()
                .show(getChildFragmentManager(), PodHopperAuthenticationFragment.TAG);
    }

    private boolean isProviderSelected(@NonNull SynchronizationProvider provider) {
        String selectedSyncProviderKey = getSelectedSyncProviderKey();
        return provider.getIdentifier().equals(selectedSyncProviderKey);
    }

    private String getSelectedSyncProviderKey() {
        return SynchronizationSettings.getSelectedSyncProviderKey();
    }

    private void updateLastSyncReport(boolean successful, long lastTime) {
        String status = String.format("%1$s (%2$s)", getString(successful
                        ? R.string.gpodnetsync_pref_report_successful : R.string.gpodnetsync_pref_report_failed),
                DateUtils.getRelativeDateTimeString(getContext(),
                        lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME));
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(status);
    }

    private @StringRes int getProviderSummary(SynchronizationProvider provider) {
        switch (provider) {
            case PODHOPPER:
                return R.string.podhopper_description;
            case GPODDER_NET:
                return R.string.gpodnet_description;
            case NEXTCLOUD_GPODDER:
                return R.string.synchronization_summary_nextcloud;
            default:
                return R.string.sync_status_error;
        }
    }

    private @DrawableRes int getProviderIcon(SynchronizationProvider provider) {
        switch (provider) {
            case PODHOPPER:
                return R.drawable.ic_podhopper;
            case GPODDER_NET:
                return R.drawable.gpodder_icon;
            case NEXTCLOUD_GPODDER:
                return R.drawable.nextcloud_logo;
            default:
                return R.drawable.ic_error;
        }
    }

    private void showPairDeviceDialog() {
        final EditText codeInput = new EditText(getContext());
        codeInput.setHint(R.string.podhopper_pair_enter_code);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        FrameLayout container = new FrameLayout(getContext());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);
        container.addView(codeInput);
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.podhopper_pair_title)
                .setView(container)
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    final String code = codeInput.getText().toString().trim().toUpperCase(Locale.US);
                    final Context appContext = getContext().getApplicationContext();
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            SupabaseClient supabase = new SupabaseClient(AntennapodHttpClient.getHttpClient(),
                                    SynchronizationCredentials.getUsername(),
                                    SynchronizationCredentials.getPassword());
                            String accessToken = supabase.ensureSession();
                            new PodHopperPairing(AntennapodHttpClient.getHttpClient())
                                    .approvePairing(code, accessToken);
                            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext,
                                    R.string.podhopper_pair_success, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext,
                                    appContext.getString(R.string.podhopper_pair_failed)
                                            + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .show();
    }
}
