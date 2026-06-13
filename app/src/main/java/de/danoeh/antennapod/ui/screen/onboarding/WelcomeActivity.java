package de.danoeh.antennapod.ui.screen.onboarding;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.WelcomeActivityBinding;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import de.danoeh.antennapod.ui.preferences.screen.synchronization.PodHopperAuthenticationFragment;

public class WelcomeActivity extends AppCompatActivity {
    public static final String PREF_NAME = "podhopper_onboarding";
    public static final String PREF_WELCOME_SEEN = "welcome_seen";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getNoTitleTheme(this));
        super.onCreate(savedInstanceState);
        WelcomeActivityBinding viewBinding = WelcomeActivityBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                        if (SynchronizationSettings.isProviderConnected()) {
                            finish();
                        }
                    }
                }, false);

        viewBinding.loginButton.setOnClickListener(v -> {
            markSeen(this);
            new PodHopperAuthenticationFragment()
                    .show(getSupportFragmentManager(), PodHopperAuthenticationFragment.TAG);
        });
        viewBinding.skipButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.podhopper_skip_warning)
                .setPositiveButton(R.string.podhopper_skip_continue, (dialog, which) -> {
                    markSeen(this);
                    finish();
                })
                .setNegativeButton(R.string.podhopper_skip_back, null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SynchronizationSettings.isProviderConnected()) {
            finish();
        }
    }

    public static boolean wasWelcomeSeen(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_WELCOME_SEEN, false);
    }

    public static void markSeen(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_WELCOME_SEEN, true).apply();
    }
}
