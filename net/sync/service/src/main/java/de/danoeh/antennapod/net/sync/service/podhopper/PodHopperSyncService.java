package de.danoeh.antennapod.net.sync.service.podhopper;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse;
import okhttp3.OkHttpClient;

public class PodHopperSyncService implements ISyncService {
    public static final String TABLE_PLAYBACK_STATE = "playback_state";
    public static final String TABLE_SUBSCRIPTIONS = "subscriptions";

    private final SupabaseClient supabase;
    private final String deviceId;

    public PodHopperSyncService(OkHttpClient httpClient, String email, String password, String deviceId) {
        this.supabase = new SupabaseClient(httpClient, email, password);
        this.deviceId = deviceId;
    }

    @Override
    public void login() throws SyncServiceException {
        supabase.ensureSession();
    }

    @Override
    public SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException {
        JSONArray rows = supabase.select(TABLE_SUBSCRIPTIONS,
                "select=feed_url,subscribed,updated_at_ms&updated_at_ms=gt." + lastSync
                        + "&order=updated_at_ms.asc");
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        long newestTimestamp = lastSync;
        try {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String feedUrl = row.getString("feed_url");
                if (row.getBoolean("subscribed")) {
                    added.add(feedUrl);
                } else {
                    removed.add(feedUrl);
                }
                newestTimestamp = Math.max(newestTimestamp, row.getLong("updated_at_ms"));
            }
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
        return new SubscriptionChanges(added, removed, newestTimestamp);
    }

    @Override
    public UploadChangesResponse uploadSubscriptionChanges(
            List<String> addedFeeds, List<String> removedFeeds) throws SyncServiceException {
        long now = System.currentTimeMillis();
        JSONArray rows = new JSONArray();
        try {
            String userId = supabase.getUserId();
            for (String feedUrl : addedFeeds) {
                rows.put(subscriptionRow(userId, feedUrl, true, now));
            }
            for (String feedUrl : removedFeeds) {
                rows.put(subscriptionRow(userId, feedUrl, false, now));
            }
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
        supabase.upsert(TABLE_SUBSCRIPTIONS, "user_id,feed_url", rows);
        return new PodHopperUploadChangesResponse(now);
    }

    @Override
    public EpisodeActionChanges getEpisodeActionChanges(long lastSync) throws SyncServiceException {
        JSONArray rows = supabase.select(TABLE_PLAYBACK_STATE,
                "select=guid,episode_url,podcast_url,position_sec,total_sec,updated_at_ms,device_id"
                        + "&updated_at_ms=gt." + lastSync
                        + "&order=updated_at_ms.asc&limit=" + PodHopperConfig.PULL_PAGE_LIMIT);
        List<EpisodeAction> actions = new ArrayList<>();
        long newestTimestamp = lastSync;
        try {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                EpisodeAction action = actionFromRow(row);
                if (action != null) {
                    actions.add(action);
                }
                newestTimestamp = Math.max(newestTimestamp, row.getLong("updated_at_ms"));
            }
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
        return new EpisodeActionChanges(actions, newestTimestamp);
    }

    @Override
    public UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions)
            throws SyncServiceException {
        long now = System.currentTimeMillis();
        Map<String, EpisodeAction> latestPerEpisode = new HashMap<>();
        for (EpisodeAction action : queuedEpisodeActions) {
            if (action.getAction() != EpisodeAction.Action.PLAY || action.getPosition() < 0) {
                continue;
            }
            String key = episodeKey(action.getGuid(), action.getEpisode());
            if (key == null) {
                continue;
            }
            EpisodeAction existing = latestPerEpisode.get(key);
            if (existing == null || isNewer(action, existing)) {
                latestPerEpisode.put(key, action);
            }
        }
        JSONArray rows = new JSONArray();
        try {
            String userId = supabase.getUserId();
            for (Map.Entry<String, EpisodeAction> entry : latestPerEpisode.entrySet()) {
                rows.put(playbackRow(userId, entry.getKey(), entry.getValue()));
            }
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
        supabase.upsert(TABLE_PLAYBACK_STATE, "user_id,episode_key", rows);
        return new PodHopperUploadChangesResponse(now);
    }

    @Override
    public void logout() {
        SupabaseClient.clearSession();
    }

    public static String episodeKey(String guid, String episodeUrl) {
        if (!TextUtils.isEmpty(guid)) {
            return guid;
        }
        if (!TextUtils.isEmpty(episodeUrl)) {
            return episodeUrl;
        }
        return null;
    }

    private static boolean isNewer(EpisodeAction candidate, EpisodeAction existing) {
        Date candidateTime = candidate.getTimestamp();
        Date existingTime = existing.getTimestamp();
        if (candidateTime == null) {
            return false;
        }
        return existingTime == null || candidateTime.after(existingTime);
    }

    private JSONObject playbackRow(String userId, String episodeKey, EpisodeAction action) throws JSONException {
        long timestampMs = action.getTimestamp() != null
                ? action.getTimestamp().getTime() : System.currentTimeMillis();
        JSONObject row = new JSONObject();
        row.put("user_id", userId);
        row.put("episode_key", episodeKey);
        row.put("guid", action.getGuid() != null ? action.getGuid() : JSONObject.NULL);
        row.put("episode_url", action.getEpisode() != null ? action.getEpisode() : JSONObject.NULL);
        row.put("podcast_url", action.getPodcast() != null ? action.getPodcast() : JSONObject.NULL);
        row.put("position_sec", action.getPosition());
        row.put("total_sec", action.getTotal());
        row.put("device_id", deviceId);
        row.put("device_name", Build.MODEL);
        row.put("updated_at_ms", timestampMs);
        return row;
    }

    private static JSONObject subscriptionRow(String userId, String feedUrl,
                                              boolean subscribed, long timestampMs) throws JSONException {
        JSONObject row = new JSONObject();
        row.put("user_id", userId);
        row.put("feed_url", feedUrl);
        row.put("subscribed", subscribed);
        row.put("updated_at_ms", timestampMs);
        return row;
    }

    private static EpisodeAction actionFromRow(JSONObject row) throws JSONException {
        String guid = row.isNull("guid") ? null : row.getString("guid");
        String episodeUrl = row.isNull("episode_url") ? null : row.getString("episode_url");
        String podcastUrl = row.isNull("podcast_url") ? null : row.getString("podcast_url");
        if (TextUtils.isEmpty(episodeUrl) && TextUtils.isEmpty(guid)) {
            return null;
        }
        int position = row.optInt("position_sec", -1);
        int total = row.optInt("total_sec", -1);
        if (position < 0) {
            return null;
        }
        EpisodeAction.Builder builder = new EpisodeAction.Builder(
                podcastUrl != null ? podcastUrl : "", episodeUrl != null ? episodeUrl : "", EpisodeAction.PLAY)
                .timestamp(new Date(row.getLong("updated_at_ms")))
                .started(position)
                .position(position)
                .total(total);
        if (!TextUtils.isEmpty(guid)) {
            builder.guid(guid);
        }
        return builder.build();
    }
}
