package islamic.duas.utils

import android.util.Base64

object Obfuscation {
    private val KEY = byteArrayOf(
        0x44, 0x33, 0x76, 0x53, 0x79, 0x6E, 0x63, 0x21,
        0x4B, 0x33, 0x79, 0x23, 0x32, 0x30, 0x32, 0x34
    )

    fun d(encoded: String): String {
        val data = Base64.decode(encoded, Base64.DEFAULT)
        return String(data.mapIndexed { i, b -> (b.toInt() xor KEY[i % KEY.size].toInt()).toByte() }.toByteArray())
    }
}
