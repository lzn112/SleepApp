package com.sleepagent.prototype.intervention.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepagent.prototype.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen alarm activity displayed when the fallback alarm triggers.
 *
 * Shows over lock screen, plays alarm sound + vibration, requires manual dismissal.
 */
class WakeAlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isConfirmed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        startAlarmAudio()
        startVibration()

        setContent {
            AlarmScreen(
                currentTime = remember { mutableStateOf(getCurrentTimeString()) },
                onDismiss = { dismissAlarm() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAudio()
        stopVibration()
    }

    private fun startAlarmAudio() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(
                this@WakeAlarmActivity,
                android.net.Uri.parse(
                    "android.resource://${packageName}/${R.raw.alarm_soft_plucks}"
                )
            )
            isLooping = true
            prepare()
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarmAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun dismissAlarm() {
        if (isConfirmed) return
        isConfirmed = true
        stopAlarmAudio()
        stopVibration()
        finish()
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, WakeAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    currentTime: androidx.compose.runtime.MutableState<String>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentTime.value,
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "该起床了",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已到你设定的最晚起床时间",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = "我醒了",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}
