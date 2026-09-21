package com.sleepagent.prototype.intervention.audio

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Media3 ExoPlayer-based background audio player for sleep sounds.
 *
 * Uses [Player.REPEAT_MODE_ONE] for looping background sounds.
 * Supports crossfade switching and volume fading.
 */
class Media3BackgroundAudioPlayer(
    private val context: Context
) : BackgroundAudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioPlaybackState())
    override val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var currentResourceId: Int = 0
    private var currentSoundKey: SleepSoundKey? = null

    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(context)
            .build()
            .also { p ->
                player = p
                p.repeatMode = Player.REPEAT_MODE_ONE
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _state.update { it.copy(isPlaying = playbackState == Player.STATE_READY) }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.update {
                            it.copy(isPlaying = false, currentSoundKey = null)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }
                })
            }
    }

    private fun rawResourceUri(resourceId: Int): Uri {
        return "android.resource://${context.packageName}/$resourceId".toUri()
    }

    override suspend fun playLoop(resourceId: Int, gain: Float) {
        withContext(Dispatchers.Main.immediate) {
            val p = getOrCreatePlayer()
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = gain.coerceIn(0f, 1f)
            val mediaItem = MediaItem.fromUri(rawResourceUri(resourceId))
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            currentResourceId = resourceId
            _state.update { it.copy(isPlaying = true, currentGain = gain) }
        }
    }

    override suspend fun playOnce(resourceId: Int, gain: Float) {
        withContext(Dispatchers.Main.immediate) {
            val p = getOrCreatePlayer()
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.volume = gain.coerceIn(0f, 1f)
            val mediaItem = MediaItem.fromUri(rawResourceUri(resourceId))
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            currentResourceId = resourceId
            _state.update { it.copy(isPlaying = true, currentGain = gain) }
        }
    }

    override suspend fun switchLoop(resourceId: Int, gain: Float, crossFadeDurationMs: Long) {
        if (currentResourceId == resourceId) {
            // Same resource, just adjust volume
            fadeTo(gain, crossFadeDurationMs)
            return
        }
        if (crossFadeDurationMs > 0) {
            fadeTo(0f, crossFadeDurationMs / 2)
            delay(crossFadeDurationMs / 2)
        }
        playLoop(resourceId, gain)
        if (crossFadeDurationMs > 0) {
            withContext(Dispatchers.Main.immediate) {
                getOrCreatePlayer().volume = 0f
            }
            fadeTo(gain, crossFadeDurationMs / 2)
        }
    }

    override suspend fun fadeTo(targetGain: Float, durationMs: Long) {
        if (durationMs <= 0) {
            withContext(Dispatchers.Main.immediate) {
                getOrCreatePlayer().volume = targetGain.coerceIn(0f, 1f)
            }
            _state.update { it.copy(currentGain = targetGain) }
            return
        }
        val startVolume = withContext(Dispatchers.Main.immediate) {
            getOrCreatePlayer().volume
        }
        val steps = 20
        val stepMs = durationMs / steps
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            val nextVolume = (startVolume + (targetGain - startVolume) * fraction).coerceIn(0f, 1f)
            withContext(Dispatchers.Main.immediate) {
                getOrCreatePlayer().volume = nextVolume
            }
            delay(stepMs)
        }
        withContext(Dispatchers.Main.immediate) {
            getOrCreatePlayer().volume = targetGain.coerceIn(0f, 1f)
        }
        _state.update { it.copy(currentGain = targetGain) }
    }

    override fun pause() {
        runOnMainBlocking {
            player?.pause()
            _state.update { it.copy(isPaused = true) }
        }
    }

    override fun stop() {
        runOnMainBlocking {
            player?.stop()
            player?.clearMediaItems()
            currentResourceId = 0
            _state.update { AudioPlaybackState() }
        }
    }

    override fun release() {
        runOnMainBlocking {
            player?.stop()
            player?.release()
            player = null
            currentResourceId = 0
            _state.update { AudioPlaybackState() }
        }
    }

    private fun runOnMainBlocking(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runBlocking(Dispatchers.Main.immediate) {
                block()
            }
        }
    }
}
