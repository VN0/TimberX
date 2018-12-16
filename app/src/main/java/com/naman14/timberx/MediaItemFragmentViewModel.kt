package com.naman14.timberx

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.*
import com.naman14.timberx.util.id
import com.naman14.timberx.util.isPlaying

/**
 * [ViewModel] for [MediaItemFragment].
 */
class MediaItemFragmentViewModel(private val mediaId: String,
                                 mediaSessionConnection: MediaSessionConnection
) : ViewModel() {

    /**
     * Use a backing property so consumers of mediaItems only get a [LiveData] instance so
     * they don't inadvertently modify it.
     */
    private val _mediaItems = MutableLiveData<List<MediaBrowserCompat.MediaItem>>()
            .apply { postValue(emptyList()) }
    val mediaItems: LiveData<List<MediaItem>> = _mediaItems

    private val subscriptionCallback = object : SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>) {
            _mediaItems.postValue(children)
        }
    }

    /**
     * When the session's [PlaybackStateCompat] changes, the [mediaItems] need to be updated
     * so the correct [MediaItemData.playbackRes] is displayed on the active item.
     * (i.e.: play/pause button or blank)
     */
    private val playbackStateObserver = Observer<PlaybackStateCompat> {
        val playbackState = it ?: EMPTY_PLAYBACK_STATE
        val metadata = mediaSessionConnection.nowPlaying.value ?: NOTHING_PLAYING
//        _mediaItems.postValue(updateState(playbackState, metadata))
    }

    /**
     * When the session's [MediaMetadataCompat] changes, the [mediaItems] need to be updated
     * as it means the currently active item has changed. As a result, the new, and potentially
     * old item (if there was one), both need to have their [MediaItemData.playbackRes]
     * changed. (i.e.: play/pause button or blank)
     */
    private val mediaMetadataObserver = Observer<MediaMetadataCompat> {
        val playbackState = mediaSessionConnection.playbackState.value ?: EMPTY_PLAYBACK_STATE
        val metadata = it ?: NOTHING_PLAYING
//        _mediaItems.postValue(updateState(playbackState, metadata))
    }

    /**
     * Because there's a complex dance between this [ViewModel] and the [MediaSessionConnection]
     * (which is wrapping a [MediaBrowserCompat] object), the usual guidance of using
     * [Transformations] doesn't quite work.
     *
     * Specifically there's three things that are watched that will cause the single piece of
     * [LiveData] exposed from this class to be updated.
     *
     * [subscriptionCallback] (defined above) is called if/when the children of this
     * ViewModel's [mediaId] changes.
     *
     * [MediaSessionConnection.playbackState] changes state based on the playback state of
     * the player, which can change the [MediaItemData.playbackRes]s in the list.
     *
     * [MediaSessionConnection.nowPlaying] changes based on the item that's being played,
     * which can also change the [MediaItemData.playbackRes]s in the list.
     */
    private val mediaSessionConnection = mediaSessionConnection.also {
        it.subscribe(mediaId, subscriptionCallback)

        it.playbackState.observeForever(playbackStateObserver)
        it.nowPlaying.observeForever(mediaMetadataObserver)
    }

//    private fun updateState(playbackState: PlaybackStateCompat,
//                            mediaMetadata: MediaMetadataCompat): List<MediaItem> {
//
//        val newResId = when (playbackState.isPlaying) {
//            true -> R.drawable.ic_pause_black_24dp
//            else -> R.drawable.ic_play_arrow_black_24dp
//        }
//
//        return mediaItems.value?.map {
//            val useResId = if (it.mediaId == mediaMetadata.id) newResId else NO_RES
//            it.copy(playbackRes = useResId)
//        } ?: emptyList()
//    }


    /**
     * Since we use [LiveData.observeForever] above (in [mediaSessionConnection]), we want
     * to call [LiveData.removeObserver] here to prevent leaking resources when the [ViewModel]
     * is not longer in use.
     *
     * For more details, see the kdoc on [mediaSessionConnection] above.
     */
    override fun onCleared() {
        super.onCleared()

        // Remove the permanent observers from the MediaSessionConnection.
        mediaSessionConnection.playbackState.removeObserver(playbackStateObserver)
        mediaSessionConnection.nowPlaying.removeObserver(mediaMetadataObserver)

        // And then, finally, unsubscribe the media ID that was being watched.
        mediaSessionConnection.unsubscribe(mediaId, subscriptionCallback)
    }

    class Factory(private val mediaId: String,
                  private val mediaSessionConnection: MediaSessionConnection
    ) : ViewModelProvider.NewInstanceFactory() {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MediaItemFragmentViewModel(mediaId, mediaSessionConnection) as T
        }
    }
}

private const val TAG = "MediaItemFragmentVM"
private const val NO_RES = 0