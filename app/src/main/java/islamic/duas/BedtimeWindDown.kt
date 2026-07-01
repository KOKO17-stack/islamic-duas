package islamic.duas

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils

class BedtimeWindDown(private val context: Context) {

    private var step = 0
    private var vibrator: Vibrator? = null
    private var listener: StepListener? = null

    val steps = listOf(
        Localization.bedtimeStep1,
        Localization.bedtimeStep2,
        Localization.bedtimeStep3,
        Localization.bedtimeStep4,
        Localization.bedtimeFarewell
    )

    interface StepListener {
        fun onStepChanged(step: Int, text: String, isComplete: Boolean)
        fun onComplete()
    }

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun setListener(listener: StepListener) {
        this.listener = listener
    }

    fun start() {
        step = 0
        listener?.onStepChanged(step, steps[step], false)
    }

    fun handleSwipeUp(view: View): Boolean {
        if (step >= 4) return false
        step++
        animateStep(view)
        vibrate()
        if (step == 4) {
            listener?.onStepChanged(step, steps[step], true)
        } else {
            listener?.onStepChanged(step, steps[step], false)
        }
        return true
    }

    fun handleLongPress(view: View) {
        if (step == 1) {
            step++
            animateStep(view)
            vibrate()
            listener?.onStepChanged(step, steps[step], false)
        }
    }

    fun handleSwipeReveal(view: View) {
        if (step == 3) {
            step++
            animateStep(view)
            vibrate()
            listener?.onStepChanged(step, steps[step], true)
        }
    }

    fun handleFarewellTap(view: View) {
        if (step == 4) {
            animateCompletion(view)
            listener?.onComplete()
            reset()
        }
    }

    fun reset() {
        step = 0
    }

    private fun animateStep(view: View) {
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).setDuration(200)
        fadeOut.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {}
            override fun onAnimationEnd(animator: Animator) {
                val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).setDuration(300)
                fadeIn.start()
            }
            override fun onAnimationCancel(animator: Animator) {}
            override fun onAnimationRepeat(animator: Animator) {}
        })
        fadeOut.start()
    }

    private fun animateCompletion(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f).setDuration(500)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f).setDuration(500)
        val set = AnimatorSet()
        set.playTogether(scaleX, scaleY)
        set.interpolator = AccelerateDecelerateInterpolator()
        set.start()
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    class GestureListener(private val windDown: BedtimeWindDown, private val view: View) :
        GestureDetector.SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val diffY = e2.y - e1.y
            if (diffY < -100) {
                return windDown.handleSwipeUp(view)
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            windDown.handleLongPress(view)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            windDown.handleFarewellTap(view)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (windDown.step == 3) {
                windDown.handleSwipeReveal(view)
                return true
            }
            return false
        }
    }
}
