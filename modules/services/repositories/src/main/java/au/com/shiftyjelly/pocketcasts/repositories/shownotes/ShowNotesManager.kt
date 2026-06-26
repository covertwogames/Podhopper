package au.com.shiftyjelly.pocketcasts.repositories.shownotes

import au.com.shiftyjelly.pocketcasts.coroutines.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.models.db.dao.TranscriptDao
import au.com.shiftyjelly.pocketcasts.repositories.BuildConfig
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.servers.ShowNotesServiceManager
import au.com.shiftyjelly.pocketcasts.servers.podcast.TranscriptService
import au.com.shiftyjelly.pocketcasts.servers.shownotes.ShowNotesState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ShowNotesManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val showNotesServiceManager: ShowNotesServiceManager,
    private val transcriptDao: TranscriptDao,
    private val transcriptsService: TranscriptService,
    private val episodeManager: EpisodeManager,
    showNotesProcessorFactory: ShowNotesProcessor.Factory,
) {
    private val showNotesProcessor = showNotesProcessorFactory.create(BuildConfig.SERVER_SHOW_NOTES_URLS.toHttpUrl())

    // PodHopper: show notes used to come from the Pocket Casts show-notes server, keyed by
    // their episode ids. Feed episodes are not in that database, so serve the notes the RSS
    // feed already provides (stored locally as the episode description) instead of fetching.
    fun loadShowNotesFlow(podcastUuid: String, episodeUuid: String): Flow<ShowNotesState> = flow {
        emit(ShowNotesState.Loading)
        emit(loadShowNotes(podcastUuid = podcastUuid, episodeUuid = episodeUuid))
    }

    suspend fun downloadToCacheShowNotes(podcastUuid: String, episodeUuid: String) {
        showNotesServiceManager.downloadToCacheShowNotes(
            podcastUuid = podcastUuid,
            processShowNotes = { showNotes ->
                scope.launch {
                    showNotesProcessor.process(
                        podcastUuid = podcastUuid,
                        episodeUuid = episodeUuid,
                        showNotes = showNotes,
                    )
                    val transcripts = transcriptDao.observeTranscripts(episodeUuid).first()
                    for (transcript in transcripts) {
                        runCatching { transcriptsService.getTranscriptOrThrow(transcript.url, CacheControl.FORCE_NETWORK) }
                    }
                }
            },
        )
    }

    suspend fun loadShowNotes(podcastUuid: String, episodeUuid: String): ShowNotesState {
        val notes = episodeManager.findByUuid(episodeUuid)?.episodeDescription
        return if (!notes.isNullOrBlank()) {
            ShowNotesState.Loaded(notes)
        } else {
            ShowNotesState.NotFound
        }
    }
}
