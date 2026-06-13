package de.danoeh.antennapod.net.sync.service.podhopper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.PodHopperPositionSync;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class PodHopperPositionSyncImpl extends PodHopperPositionSync {
    private static final String TAG = "PodHopperPositionSync";
    private static final String PREF_NAME = "podhopper_position_sync";
    private static final String PREF_LAST_PULL_MS = "last_pull_ms";
    private static final String PREF_INSTALL_ID = "install_id";
    private static final long MIN_PUSH_INTERVAL_MS = 4000;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile long lastPushAttemptMs = 0;

    public PodHopperPositionSyncImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void pushPosition(FeedMedia media, boolean immediate) {
        if (!isPodHopperSyncActive() || media == null || media.getItem() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!immediate && now - lastPushAttemptMs < MIN_PUSH_INTERVAL_MS) {
            return;
        }
        lastPushAttemptMs = now;
        final EpisodeAction action = new EpisodeAction.Builder(media.getItem(), EpisodeAction.PLAY)
                .timestamp(new Date())
                .started(media.getPosition() / 1000)
                .position(media.getPosition() / 1000)
                .total(media.getDuration() / 1000)
                .build();
        executor.execute(() -> {
            try {
                newSyncService().uploadEpisodeActions(Collections.singletonList(action));
                Log.d(TAG, "Pushed position " + action.getPosition() + "s for " + action.getEpisode());
            } catch (Exception e) {
                Log.d(TAG, "Position push failed, regular sync will catch up: " + e.getMessage());
            }
        });
    }

    @Override
    public void pullLatestPositions() {
        if (!isPodHopperSyncActive()) {
            return;
        }
        executor.execute(() -> {
            try {
                SupabaseClient supabase = new SupabaseClient(AntennapodHttpClient.getHttpClient(),
                        SynchronizationCredentials.getUsername(), SynchronizationCredentials.getPassword());
                long lastPull = getPrefs().getLong(PREF_LAST_PULL_MS, 0);
                JSONArray rows = supabase.select(PodHopperSyncService.TABLE_PLAYBACK_STATE,
                        "select=guid,episode_url,position_sec,total_sec,updated_at_ms,device_id"
                                + "&updated_at_ms=gt." + lastPull
                                + "&device_id=neq." + getOrCreateInstallId(context)
                                + "&order=updated_at_ms.asc&limit=" + PodHopperConfig.PULL_PAGE_LIMIT);
                long newestTimestamp = applyRemotePositions(rows, lastPull);
                if (newestTimestamp > lastPull) {
                    getPrefs().edit().putLong(PREF_LAST_PULL_MS, newestTimestamp).apply();
                }
            } catch (Exception e) {
                Log.d(TAG, "Position pull failed: " + e.getMessage());
            }
        });
    }

    private long applyRemotePositions(JSONArray rows, long lastPull) throws JSONException {
        long newestTimestamp = lastPull;
        long currentlyPlayingId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        List<FeedItem> updatedItems = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            newestTimestamp = Math.max(newestTimestamp, row.getLong("updated_at_ms"));
            String guid = row.isNull("guid") ? null : row.getString("guid");
            String episodeUrl = row.isNull("episode_url") ? null : row.getString("episode_url");
            int positionSec = row.optInt("position_sec", -1);
            if (positionSec < 0) {
                continue;
            }
            FeedItem feedItem = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, episodeUrl);
            if (feedItem == null || feedItem.getMedia() == null) {
                continue;
            }
            FeedMedia media = feedItem.getMedia();
            if (media.getId() == currentlyPlayingId
                    && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
                continue;
            }
            media.setPosition(positionSec * 1000);
            int smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs();
            boolean almostEnded = media.getDuration() > 0
                    && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000;
            if (almostEnded) {
                feedItem.setPlayed(true);
                media.setPosition(0);
            }
            updatedItems.add(feedItem);
        }
        if (!updatedItems.isEmpty()) {
            DBReader.loadFeedDataOfFeedItemList(updatedItems);
            DBWriter.setItemList(updatedItems);
            Log.d(TAG, "Applied " + updatedItems.size() + " remote positions");
        }
        return newestTimestamp;
    }

    private PodHopperSyncService newSyncService() {
        return new PodHopperSyncService(AntennapodHttpClient.getHttpClient(),
                SynchronizationCredentials.getUsername(), SynchronizationCredentials.getPassword(),
                getOrCreateInstallId(context));
    }

    private boolean isPodHopperSyncActive() {
        return SynchronizationProvider.PODHOPPER.getIdentifier()
                .equals(SynchronizationSettings.getSelectedSyncProviderKey())
                && SynchronizationCredentials.getUsername() != null
                && SynchronizationCredentials.getPassword() != null;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getOrCreateInstallId(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String installId = prefs.getString(PREF_INSTALL_ID, null);
        if (installId == null) {
            installId = UUID.randomUUID().toString();
            prefs.edit().putString(PREF_INSTALL_ID, installId).apply();
        }
        return installId;
    }
}
