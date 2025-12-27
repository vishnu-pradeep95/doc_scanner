/**
 * SoundManager.kt - Cute Sound Effects Manager
 * 
 * Handles playback of cute sound effects throughout the app.
 * Uses Android's built-in ToneGenerator for simple sounds and
 * MediaPlayer for custom sounds when available.
 */

package com.pdfscanner.app.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Singleton manager for playing cute sound effects
 * Uses system sounds and haptic feedback for a delightful experience
 */
object SoundManager {
    
    private var toneGenerator: ToneGenerator? = null
    private var context: Context? = null
    
    // Sound enabled state
    private var soundEnabled = true
    private var hapticEnabled = true
    
    /**
     * Initialize sound manager
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
        } catch (e: Exception) {
            // ToneGenerator not available
        }
    }
    
    /**
     * Enable or disable sound effects
     */
    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }
    
    /**
     * Enable or disable haptic feedback
     */
    fun setHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }
    
    /**
     * Check if sounds are enabled
     */
    fun isSoundEnabled() = soundEnabled
    
    /**
     * Play camera capture sound with haptic
     */
    fun playCapture(view: View? = null) {
        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    
    /**
     * Play success/completion sound
     */
    fun playSuccess(view: View? = null) {
        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        }
        if (hapticEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
    
    /**
     * Play pop sound (for UI interactions)
     */
    fun playPop(view: View? = null) {
        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 50)
        }
        if (hapticEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    
    /**
     * Play click sound (for button presses)
     */
    fun playClick(view: View? = null) {
        if (hapticEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    
    /**
     * Play task complete sound
     */
    fun playComplete(view: View? = null) {
        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        }
        if (hapticEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
    
    /**
     * Play error sound
     */
    fun playError(view: View? = null) {
        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
        }
        if (hapticEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
