package islamic.duas.utils

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.core.content.res.ResourcesCompat
import islamic.duas.R

object TypefaceSpanUtil {
    private var arabicTypeface: Typeface? = null
    private var urduTypeface: Typeface? = null

    fun init(context: Context) {
        try {
            arabicTypeface = ResourcesCompat.getFont(context, R.font.scheherazade_new)
        } catch (_: Exception) {
            arabicTypeface = null
        }
        try {
            urduTypeface = ResourcesCompat.getFont(context, R.font.noto_nastaliq_urdu)
        } catch (_: Exception) {
            urduTypeface = null
        }
    }

    fun applyArabic(text: SpannableStringBuilder, start: Int, end: Int) {
        arabicTypeface?.let { tf ->
            text.setSpan(CustomTypefaceSpan(tf), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun applyUrdu(text: SpannableStringBuilder, start: Int, end: Int) {
        urduTypeface?.let { tf ->
            text.setSpan(CustomTypefaceSpan(tf), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun buildMixedString(arabicSegment: String, urduSegment: String): SpannableString {
        val sb = SpannableStringBuilder()
        val startA = sb.length
        sb.append(arabicSegment)
        applyArabic(sb, startA, sb.length)
        sb.append(" ")
        val startU = sb.length
        sb.append(urduSegment)
        applyUrdu(sb, startU, sb.length)
        return SpannableString(sb)
    }
}

class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) {
        ds.setTypeface(typeface)
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.setTypeface(typeface)
    }
}
