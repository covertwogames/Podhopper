package au.com.shiftyjelly.pocketcasts.analytics.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// PodHopper: all inherited third-party analytics trackers have been removed.
// The original Pocket Casts release build bound TracksAnalyticsTracker (Automattic),
// FirebaseAnalyticsTracker (Google) and AnonymousBumpStatsTracker (Pocket Casts) here.
// PodHopper does not transmit usage analytics, so this module intentionally binds
// nothing. The EventSink falls back to the no-op tracker provided in AnalyticsModule,
// and no analytics events leave the device.
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseTrackerModule
