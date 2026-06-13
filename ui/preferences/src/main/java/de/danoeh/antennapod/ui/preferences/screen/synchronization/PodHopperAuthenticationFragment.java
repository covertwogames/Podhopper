package de.danoeh.antennapod.ui.preferences.screen.synchronization;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.Executors;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.service.podhopper.SupabaseClient;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.preferences.R;
import de.danoeh.antennapod.ui.preferences.databinding.PodhopperAuthDialogBinding;

public class PodHopperAuthenticationFragment extends DialogFragment {
    public static final String TAG = "PodHopperAuthenticationFragment";
    private PodhopperAuthDialogBinding viewBinding;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(R.string.podhopper_description);
        dialog.setNegativeButton(R.string.cancel_label, null);
        dialog.setCancelable(false);
        this.setCancelable(false);

        viewBinding = PodhopperAuthDialogBinding.inflate(getLayoutInflater());
        dialog.setView(viewBinding.getRoot());

        viewBinding.loginButton.setOnClickListener(v -> attemptLogin());
        viewBinding.signupButton.setOnClickListener(v -> attemptSignup());
        viewBinding.forgotButton.setOnClickListener(v -> attemptRecover());
        return dialog.create();
    }

    private void attemptLogin() {
        final String email = viewBinding.emailText.getText() != null
                ? viewBinding.emailText.getText().toString().trim() : "";
        final String password = viewBinding.passwordText.getText() != null
                ? viewBinding.passwordText.getText().toString() : "";
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError(getString(R.string.podhopper_auth_error));
            return;
        }
        viewBinding.errorText.setVisibility(View.GONE);
        viewBinding.loginButton.setEnabled(false);
        viewBinding.loginProgress.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SupabaseClient.clearSession();
                new SupabaseClient(AntennapodHttpClient.getHttpClient(), email, password).ensureSession();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> onLoginSuccess(email, password));
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        viewBinding.loginButton.setEnabled(true);
                        viewBinding.loginProgress.setVisibility(View.GONE);
                        showError(getString(R.string.podhopper_auth_error) + ": " + e.getMessage());
                    });
                }
            }
        });
    }

    private void onLoginSuccess(String email, String password) {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.PODHOPPER.getIdentifier());
        SynchronizationCredentials.clear();
        SynchronizationCredentials.setUsername(email);
        SynchronizationCredentials.setPassword(password);
        SynchronizationQueue.getInstance().fullSync();
        dismiss();
    }

    private void showError(String message) {
        viewBinding.errorText.setText(message);
        viewBinding.errorText.setVisibility(View.VISIBLE);
    }

    private void attemptSignup() {
        final String email = viewBinding.emailText.getText() != null
                ? viewBinding.emailText.getText().toString().trim() : "";
        final String password = viewBinding.passwordText.getText() != null
                ? viewBinding.passwordText.getText().toString() : "";
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError(getString(R.string.podhopper_auth_error));
            return;
        }
        setBusy(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SupabaseClient.clearSession();
                boolean sessionActive = new SupabaseClient(AntennapodHttpClient.getHttpClient(),
                        email, password).signUp(email, password);
                if (getActivity() == null) {
                    return;
                }
                if (sessionActive) {
                    getActivity().runOnUiThread(() -> onLoginSuccess(email, password));
                } else {
                    getActivity().runOnUiThread(() -> {
                        setBusy(false);
                        showError(getString(R.string.podhopper_auth_confirm_email));
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setBusy(false);
                        showError(getString(R.string.podhopper_auth_error) + ": " + e.getMessage());
                    });
                }
            }
        });
    }

    private void attemptRecover() {
        final String email = viewBinding.emailText.getText() != null
                ? viewBinding.emailText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            showError(getString(R.string.podhopper_auth_error));
            return;
        }
        setBusy(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                new SupabaseClient(AntennapodHttpClient.getHttpClient(), email, null).recoverPassword(email);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setBusy(false);
                        showError(getString(R.string.podhopper_auth_recover_sent));
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setBusy(false);
                        showError(getString(R.string.podhopper_auth_error) + ": " + e.getMessage());
                    });
                }
            }
        });
    }

    private void setBusy(boolean busy) {
        viewBinding.loginButton.setEnabled(!busy);
        viewBinding.signupButton.setEnabled(!busy);
        viewBinding.forgotButton.setEnabled(!busy);
        viewBinding.loginProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            viewBinding.errorText.setVisibility(View.GONE);
        }
    }
}
