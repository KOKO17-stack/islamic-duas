package islamic.duas

import android.app.AlertDialog
import android.content.Context

class SujoodSahwDiagnostic(private val context: Context) {

    private var step = 1
    private var doubtType: Int? = null
    private var location: Int? = null
    private var result: String? = null

    interface Callback {
        fun onResult(result: String)
        fun onDismiss()
    }

    fun start(callback: Callback) {
        step = 1
        doubtType = null
        location = null
        showStep1(callback)
    }

    private fun showStep1(callback: Callback) {
        AlertDialog.Builder(context)
            .setTitle(Localization.step1Title)
            .setItems(
                arrayOf(Localization.step1Option1, Localization.step1Option2, Localization.step1Option3)
            ) { _, which ->
                doubtType = which
                step = 2
                showStep2(callback)
            }
            .setNegativeButton("منسوخ") { _, _ -> callback.onDismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showStep2(callback: Callback) {
        val options = arrayOf(Localization.step2OptionQaada, Localization.step2OptionRukn, Localization.step2BeforeSalam)
        AlertDialog.Builder(context)
            .setTitle(Localization.step2Title)
            .setItems(options) { _, which ->
                location = which
                step = 3
                showResult(callback)
            }
            .setNegativeButton("واپس") { _, _ -> showStep1(callback) }
            .setCancelable(false)
            .show()
    }

    private fun showResult(callback: Callback) {
        result = when (doubtType) {
            0 -> { // уверен в недостатке
                when (location) {
                    0 -> Localization.step3SahwAfter
                    1 -> Localization.step3Restart
                    2 -> Localization.step3SahwBefore
                    else -> Localization.step3SahwBefore
                }
            }
            1 -> { // уверен в излишке
                when (location) {
                    0 -> Localization.step3SahwAfter
                    1 -> Localization.step3Restart
                    2 -> Localization.step3SahwAfter
                    else -> Localization.step3SahwAfter
                }
            }
            2 -> { // сомнение с预обладающим мнением
                when (location) {
                    0 -> Localization.step3SahwBefore
                    1 -> Localization.step3Restart
                    2 -> Localization.step3SahwBefore
                    else -> Localization.step3SahwBefore
                }
            }
            else -> Localization.step3SahwBefore
        }

        AlertDialog.Builder(context)
            .setTitle(Localization.step3Title)
            .setMessage(result)
            .setPositiveButton("سمجھ گیا") { _, _ -> callback.onResult(result!!) }
            .setNegativeButton("دوبارہ") { _, _ ->
                step = 1
                doubtType = null
                location = null
                showStep1(callback)
            }
            .setCancelable(false)
            .show()
    }

    fun reset() {
        step = 1
        doubtType = null
        location = null
        result = null
    }
}
