package au.com.shiftyjelly.pocketcasts.repositories.podhopper

/**
 * Configuration for PodHopper's Supabase backend.
 *
 * This is the same project, anon key, and page limit used by the proven AntennaPod
 * implementation. The backend (auth, the subscriptions and playback_state tables, and the
 * pairing and delete-account edge functions) already exists. Phase 3 is a client port only.
 */
object PodHopperConfig {
    const val SUPABASE_URL = "https://vamqoxkasykfhnlfeixz.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_mkv0y6AoTSkPoY6MekLj2g_ZOqpdKVv"
    const val PULL_PAGE_LIMIT = 200L
}
