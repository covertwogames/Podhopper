package de.danoeh.antennapod.ui.screen.onboarding;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.PairingActivityBinding;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.service.podhopper.PodHopperPairing;
import de.danoeh.antennapod.net.sync.service.podhopper.SupabaseClient;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import de.danoeh.antennapod.ui.preferences.screen.synchronization.PodHopperAuthenticationFragment;

public class PairingActivity extends AppCompatActivity {
    private static final long POLL_INTERVAL_MS = 3000;

    private PairingActivityBinding viewBinding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String currentCode = null;
    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getNoTitleTheme(this));
        super.onCreate(savedInstanceState);
        viewBinding = PairingActivityBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        viewBinding.emailFallbackButton.setOnClickListener(v -> new PodHopperAuthenticationFragment()
                .show(getSupportFragmentManager(), PodHopperAuthenticationFragment.TAG));
        viewBinding.codeText.setOnClickListener(v -> requestCode());
        viewBinding.statusText.setOnClickListener(v -> requestCode());
        requestCode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SynchronizationSettings.isProviderConnected()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void requestCode() {
        viewBinding.codeText.setText("\u2022\u2022\u2022\u2022\u2022\u2022");
        viewBinding.statusText.setText(R.string.podhopper_pairing_waiting);
        executor.execute(() -> {
            try {
                String code = new PodHopperPairing(AntennapodHttpClient.getHttpClient())
                        .startPairing(Build.MODEL);
                currentCode = code;
                mainHandler.post(() -> {
                    if (!destroyed) {
                        viewBinding.codeText.setText(code);
                        viewBinding.statusText.setText(R.string.podhopper_pairing_waiting);
                        scheduleNextPoll();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!destroyed) {
                        viewBinding.statusText.setText(R.string.podhopper_pairing_offline);
                    }
                });
            }
        });
    }

    private void scheduleNextPoll() {
        mainHandler.postDelayed(this::pollOnce, POLL_INTERVAL_MS);
    }

    private void pollOnce() {
        if (destroyed || currentCode == null) {
            return;
        }
        final String code = currentCode;
        executor.execute(() -> {
            try {
                String tokenHash = new PodHopperPairing(AntennapodHttpClient.getHttpClient()).pollPairing(code);
                if (tokenHash != null) {
                    SupabaseClient.clearSession();
                    String email = SupabaseClient.claimPairingSession(
                            AntennapodHttpClient.getHttpClient(), tokenHash);
                    SynchronizationSettings.setSelectedSyncProvider(
                            SynchronizationProvider.PODHOPPER.getIdentifier());
                    SynchronizationCredentials.setUsername(email);
                    SynchronizationCredentials.setPassword(null);
                    SynchronizationQueue.getInstance().fullSync();
                    mainHandler.post(() -> {
                        if (!destroyed) {
                            finish();
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (!destroyed) {
                            scheduleNextPoll();
                        }
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!destroyed) {
                        viewBinding.statusText.setText(R.string.podhopper_pairing_expired);
                        currentCode = null;
                    }
                });
            }
        });
    }
}
