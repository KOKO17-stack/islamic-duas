package com.kojoscope.viewer.util

/**
 * Faithful Kotlin port of the web dashboard's WhatsApp chat classification
 * (viewer-index.html: waClassify / waChatClass / waBuildChatClasses).
 *
 * Classification is PER-CHAT: a chat's canonical key (conversationTitle, or
 * contactName as fallback) aggregates evidence (noise / whitelisted / anyGroup)
 * across ALL of that chat's notifications, then priority is applied:
 * hard-override > noise > whitelist > group-heuristic > individual.
 */
object WaClassifier {

    private val BIDI = Regex("[\u200e\u200f\u202a-\u202e\u2066-\u2069]")
    private val BIDI2 = Regex("[\u202c\u202d]")
    private val SPACES = Regex("\\s+")
    private val MSG_COUNT = Regex("\\(\\d+\\s*messages?\\)", RegexOption.IGNORE_CASE)
    private val SUMMARY = Regex(
        "\\d+\\s+messages?\\s+from\\s+\\d+\\s*chats|\\(\\d+\\s*messages?\\)|\\d+\\s*new\\s*messages",
        RegexOption.IGNORE_CASE
    )
    private val NOISE_PREVIEW = Regex("^(downloading|checking for new messages|you may have new messages)", RegexOption.IGNORE_CASE)
    private val GROUP_KW = Regex(
        "گروپ|ماڈیول|مدرسہ|جامعہ|جماعت|حلقہ|کورس|درس|گروہ|group|module|semester|course|wajibat|ibadaat|academy|jamaat|salaah|section|batch"
    )
    private val CATEGORIES = setOf("group_chat", "broadcast_list", "channel", "community")
    private val cleanedWhitelist = WaConstants.HARD_WHITELIST.map { clean(it) }.filter { it.isNotEmpty() }

    private class Evidence { var noise = false; var whitelisted = false; var anyGroup = false }

    data class Doc(
        val type: String,
        val conversationTitle: String?,
        val contactName: String?,
        val chatCategory: String?,
        val isGroup: String?,
        val groupName: String?,
        val summaryText: String?,
        val messageCount: String?,
        val messagePreview: String?
    ) {
        val canonicalKey: String get() = WaClassifier.canonicalKey(conversationTitle, contactName)
        val isWhatsApp: Boolean get() = WaClassifier.isWhatsApp(type)
    }

    fun clean(s: String?): String = (s ?: "")
        .replace(BIDI, "")
        .replace(BIDI2, "")
        .replace(SPACES, " ")
        .trim()

    fun isWhatsApp(type: String): Boolean {
        val t = type.lowercase()
        if (t.indexOf("snapchat") != -1) return false
        return t.indexOf("whatsapp") != -1 || t.indexOf("message") != -1
    }

    // conversationTitle > contactName; strip "(N messages)" and ": sender" head.
    fun canonicalKey(conversationTitle: String?, contactName: String?): String {
        val base = clean(conversationTitle).ifEmpty { clean(contactName) }
        return base.split(MSG_COUNT)[0].split(":")[0].trim()
    }

    fun noise(d: Doc): Boolean {
        val k = d.canonicalKey.lowercase()
        if (k in WaConstants.NOISE_KEYS) return true
        if (SUMMARY.containsMatchIn(d.summaryText ?: "")) return true
        return NOISE_PREVIEW.containsMatchIn(d.messagePreview ?: "")
    }

    fun whitelistMatch(d: Doc): Boolean {
        val ct = clean(d.conversationTitle).lowercase()
        val ck = d.canonicalKey.lowercase()
        val digits = ck.filter { it.isDigit() }
        for (w in cleanedWhitelist) {
            val wl = w.lowercase()
            if (ck == wl || (ct.isNotEmpty() && ct == wl)) return true
            if (wl.length > 3 && wl.indexOf(' ') != -1) {
                if (ck.indexOf(wl) != -1 || ct.indexOf(wl) != -1) return true
            }
            val wd = w.filter { it.isDigit() }
            if (digits.length >= 10 && wd.length >= 10 && digits.takeLast(10) == wd.takeLast(10)) return true
        }
        return false
    }

    fun groupLogic(d: Doc): Boolean {
        val cat = (d.chatCategory ?: "").lowercase()
        if (cat in CATEGORIES) return true
        if ((d.isGroup ?: "").lowercase() == "true") return true
        val cn = clean(d.contactName)
        val ct = clean(d.conversationTitle)
        val su = (d.summaryText ?: "").trim()
        if (cn.isNotEmpty() && cn != "WhatsApp" && d.groupName != null && d.groupName.trim().isNotEmpty()) return true
        val cnt = (d.messageCount ?: "0").toIntOrNull() ?: 0
        if (cnt > 1) return true
        if (MSG_COUNT.containsMatchIn(ct) || MSG_COUNT.containsMatchIn(cn)) return true
        if (SUMMARY.containsMatchIn(su)) return true
        if (ct.length >= 3 && cn.startsWith(ct) && cn.substring(ct.length).trim().startsWith(":")) return true
        if (ct.isNotEmpty() && GROUP_KW.containsMatchIn(ct)) return true
        if (cn.indexOf(':') > 0 && GROUP_KW.containsMatchIn(cn.split(":")[0])) return true
        return false
    }

    // Per-chat aggregation: any group/whitelist/noise evidence in ANY doc wins.
    fun buildChatClasses(docs: List<Doc>): Map<String, String> {
        val map = HashMap<String, Evidence>()
        for (d in docs) {
            if (!d.isWhatsApp) continue
            val k = d.canonicalKey.lowercase()
            if (k.isEmpty()) continue
            val m = map.getOrPut(k) { Evidence() }
            if (noise(d)) m.noise = true
            if (whitelistMatch(d)) m.whitelisted = true
            if (groupLogic(d)) m.anyGroup = true
        }
        val out = HashMap<String, String>(map.size)
        for ((k, m) in map) {
            val rec = WaConstants.HARD_OVERRIDES[k]
            out[k] = when {
                rec == "ind" -> "individual"
                rec == "grp" -> "group"
                m.noise && !m.whitelisted -> "noise"
                m.whitelisted -> "individual"
                m.anyGroup -> "group"
                else -> "individual"
            }
        }
        return out
    }

    // Per-document fallback used when a chat has no aggregated class (e.g. empty
    // canonical key). Mirrors the dashboard's waClassify(): empty keys are noise
    // because '' is a WA_NOISE_KEY.
    fun classifyPerDoc(d: Doc): String {
        val k = d.canonicalKey.lowercase()
        val rec = WaConstants.HARD_OVERRIDES[k]
        if (rec == "ind") return "individual"
        if (rec == "grp") return "group"
        if (noise(d)) return "noise"
        if (whitelistMatch(d)) return "individual"
        if (groupLogic(d)) return "group"
        return "individual"
    }
}