/**
 * AnimationHelper.kt - Cute Animation Utilities
 * 
 * Helper functions for applying cute animations to views
 */

package com.pdfscanner.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import com.pdfscanner.app.R

/**
 * Animation helper for cute UI interactions
 */
object AnimationHelper {
    
    /**
     * Play bounce animation on view
     */
    fun bounce(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.85f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.85f, 1.1f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 400
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }
    
    /**
     * Play pop-in animation (scale from 0 to 1)
     */
    fun popIn(view: View) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.isVisible = true
        
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }
    
    /**
     * Play pop-out animation (scale to 0)
     */
    fun popOut(view: View, onEnd: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 200
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.isVisible = false
                    onEnd?.invoke()
                }
            })
            start()
        }
    }
    
    /**
     * Wiggle animation for attention/error
     */
    fun wiggle(view: View) {
        val rotation = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, -8f, 8f, -6f, 6f, -4f, 4f, 0f)
        rotation.duration = 400
        rotation.start()
    }
    
    /**
     * Pulse animation (gentle scale up and down)
     */
    fun pulse(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.15f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            start()
        }
    }
    
    /**
     * Success celebration animation
     */
    fun celebrate(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.3f, 0.9f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.3f, 0.9f, 1.1f, 1f)
        val rotation = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 10f, -10f, 5f, 0f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, rotation)
            duration = 500
            start()
        }
    }
    
    /**
     * Slide up with fade animation
     */
    fun slideUpFadeIn(view: View) {
        view.translationY = 100f
        view.alpha = 0f
        view.isVisible = true
        
        val translateY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 100f, 0f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        
        AnimatorSet().apply {
            playTogether(translateY, alpha)
            duration = 350
            interpolator = OvershootInterpolator(1f)
            start()
        }
    }
    
    /**
     * Heart beat animation for capture button
     */
    fun heartbeat(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.2f, 1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.2f, 1f, 1.15f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 600
            start()
        }
    }
    
    /**
     * Loading shimmer effect placeholder
     */
    fun shimmer(view: View, duration: Long = 1500) {
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.5f, 1f)
        alpha.duration = duration
        alpha.repeatCount = ObjectAnimator.INFINITE
        alpha.start()
    }
}
