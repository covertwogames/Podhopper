# PodHopper deletion candidates

Code made unreachable or inert by the Pocket Casts server tear-out. Nothing here is deleted yet.
Leave it in place until the app is confirmed working, then delete in a dedicated pass, checking the
"verify first" notes because some pieces are still referenced from places that also need editing.

## Safe once confirmed: now unreachable

Discover (tab removed from the bottom nav in MainActivity, curated worker no longer scheduled):
- modules/features/discover/  (the whole module, ~40 files)
  - Verify first: the app still references the discover deep-link path. MainActivity.openDiscoverListDeeplink
    uses discoverDeepLinkManager and PodcastListFragment, and the tab-opened analytics has a
    navigation_discover branch. Remove or redirect those when deleting the module.

Pocket Casts account sync (no longer called; RefreshPodcastsThread.sync() is now a no-op):
- modules/services/repositories/.../sync/data/DataSyncProcess.kt
- modules/services/repositories/.../sync/data/PodcastSync.kt
- modules/services/repositories/.../sync/data/EpisodeSync.kt
- modules/services/repositories/.../sync/data/FoldersSync.kt
- modules/services/repositories/.../sync/data/PlaylistSync.kt
- modules/services/repositories/.../sync/data/BookmarkSync.kt
  - Verify first: confirm each is referenced only by DataSyncProcess before deleting.

Background workers no longer scheduled (PocketCastsApplication no longer enqueues them):
- modules/features/discover/.../worker/CuratedPodcastsSyncWorker.kt
- modules/services/repositories/.../stats/PlaybackStatsSyncWorker.kt
  - Note: once deleted, also drop their now-unused imports in PocketCastsApplication (already removed)
    and cancel any already-scheduled periodic work on devices (a one-time app-data clear does this).

Server autocomplete (ImprovedSearchManagerImpl.autoCompleteSearch now returns empty):
- modules/services/servers/.../search/AutoCompleteSearchService (and its DI wiring)
  - Verify first: confirm nothing else still calls it.

## Inert but still wired (candidates for a later, larger pass)

Still scheduled but login-gated, so they do nothing without a Pocket Casts login (which PodHopper
never performs). Left in place because their call sites are in core playback/profile code:
- UpNextSyncWorker  (scheduled from UpNextQueueImpl and from DataSyncProcess)
- StarredSyncWorker (scheduled from ProfileEpisodeListFragment)

The whole Pocket Casts account/sync server layer. Still referenced by UserManager.getSignInState and
the Pocket Casts account/onboarding screens, so removing it is a separate, deliberate job:
- modules/services/repositories/.../sync/SyncManager.kt / SyncManagerImpl.kt
- modules/services/repositories/.../sync/SyncAccountManagerImpl.kt
- modules/services/servers/.../sync/ (the gRPC SyncService and com.pocketcasts.service.api usage)
- the Pocket Casts onboarding/login screens in modules/features/account/ (the sign-in nag banner is
  already suppressed in ProfileViewModel; the screens themselves remain)

Dead server search branch (unreachable because SearchViewModel intercepts pasted http URLs first):
- SearchHandler.kt http branch that calls serviceManager.searchForPodcastsRx
