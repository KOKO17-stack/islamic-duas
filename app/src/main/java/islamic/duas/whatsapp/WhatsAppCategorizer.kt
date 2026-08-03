package islamic.duas.whatsapp

import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

object WhatsAppCategorizer {

    private const val TAG = "WhatsAppCategorizer"

    // Message count extraction from (N messages) pattern
    private val MSG_COUNT_PATTERN = Pattern.compile("\\((\\d+)\\s*message")

    // Group name extraction from "Name (N messages)" pattern
    private val GROUP_NAME_PATTERN = Pattern.compile("(.+?)\\s*\\(\\d+\\s*message")

    // Channel suffix detection
    private val CHANNEL_SUFFIX_PATTERN = Pattern.compile(".*\\b[Cc]hannel\\b.*")

    // Community keyword detection
    private val COMMUNITY_PATTERN = Pattern.compile(".*\\b[Cc]ommunity\\b.*")

    // Promo/broadcast keywords
    private val PROMO_KEYWORDS = setOf(
        "FREE", "PROMOTION", "PROMO", "GIFT HUB", "GIFT", "SHOP",
        "CHANNEL", "BROADCAST", "ANNOUNCEMENT", "COMMUNITY",
        "TRTEEL", "QURAAN", "TAJVID", "TAJWEED", "TAFSEER",
        "MODULE", "SEMESTER", "COURSE", "IBADAAT", "WAJIBAT",
        "AL RABBANI", "RABBANI", "INSTITUTE", "INSTITUTION",
        "SIRAT", "JANNAT", "WALI", "MASJID", "MADRASA",
        "QURAN", "HADEES", "SUNNAH", "FIQH", "AQEEDAH",
        "ISLAMIC", "DEEN", "DAWAH", "TARBIYAH", "TAZKIYAH",
        "JAMIA", "DARUL", "ULOOM", "MAKTAB", "HALQA",
        "STUDY", "CIRCLE", "HALAQA", "JAMAAT", "JAMAT"
    )

    // Generic group keywords for non-message-count group detection
    private val GENERIC_GROUP_KEYWORDS = setOf(
        "ISLAMIC", "QURAN", "HADITH", "DAILY", "REMINDER",
        "DAWAH", "DUA", "AZKAR", "MORNING", "EVENING",
        "ISLAM", "MUSLIM", "ALLAH", "RABB", "DEEN"
    )

    // Module/Course academic keywords (triggers group_chat even without N messages)
    private val MODULE_KEYWORDS = setOf(
        "MODULE", "SEMESTER", "COURSE", "IBADAAT", "WAJIBAT",
        "TAJVID", "TAJWEED", "TAFSEER", "QURAN", "HADEES",
        "SUNNAH", "FIQH", "AQEEDAH", "ISLAMIC", "MAKTAB",
        "HALQA", "HALAQA", "JAMAAT", "JAMAT", "DARS", "CLASS"
    )

    // Aggregate summary pattern: "X messages from Y chats"
    private val AGGREGATE_SUMMARY_PATTERN = Regex("\\d+\\s+messages from \\d+\\s+chats")

    // Unicode bidi control marks found in Arabic/RTL notification titles
    private val BIDI_MARKS = Regex("[\u200e\u200f\u202a-\u202e\u2066-\u2069]")

    // Urdu/Arabic group structure keywords (group, module, madrasa, class, etc.)
    private val URDU_GROUP_KEYWORDS = listOf(
        "گروپ", "ماڈیول", "مدرسہ", "جامعہ", "جماعت", "حلقہ", "کورس", "درس",
        "گروھ", "گروہ", "تعلیمی", "ڈسکشن", "سیکشن"
    )

    data class CategorizationResult(
        val chatCategory: ChatCategory,
        val messageCount: Int = 0,
        val groupName: String = "",
        val confidence: Float = 1.0f
    )

    // ===== USER-CONFIRMED OVERRIDES (from the DeviceSync dashboard review wizard, Aug 2026) =====
    // Canonical chat keys (lowercased, bidi-stripped, "(N messages)"/": Sender" removed)
    // that the user confirmed are INDIVIDUAL chats forever.
    val CONFIRMED_INDIVIDUAL_KEYS: Set<String> = setOf(
        "+92 319 8052748", "+92 334 1209199", "alhamdulillah", "+92 329 6611517",
        "+92 342 7740228", "+92 343 4045433", "mariamohsan40 and 1 other",
        "mariamohsan40 and 3 others", "+92 304 4545967"
    )

    // Canonical chat keys that the user confirmed are GROUP chats forever.
    val CONFIRMED_GROUP_KEYS: Set<String> = setOf(
        "شہزینہ نصیر میم تجوید🌹🌹", "شجعیہ قمر ناروال", "faze khan",
        "allah is calling for tahajjud...🍂", "~ sania ~•imani sis",
        "~ sania ~•imani sis and 1 other", "حسینہ durani durrani"
    )

    /**
     * Canonical chat key, identical to the dashboard's waCanonicalKey():
     * conversationTitle > contactName, bidi-stripped, "(N messages)" and
     * ": Sender" suffixes removed, whitespace collapsed, lowercased.
     */
    fun canonicalChatKey(contactName: String, conversationTitle: String): String {
        fun clean(s: String): String = s.replace(BIDI_MARKS, "")
            .replace("\u202c", "").replace("\u202d", "")
            .replace(Regex("\\s+"), " ").trim()
        val ct = clean(conversationTitle)
        val cn = clean(contactName)
        val base = if (ct.isNotEmpty()) ct else cn
        val noCount = base.split(Regex("\\(\\d+\\s*messages?\\)", RegexOption.IGNORE_CASE))[0]
        return noCount.split(":")[0].trim().lowercase(java.util.Locale.ROOT)
    }

    /**
     * Categorize a WhatsApp notification entry.
     * sbn can be null for historical reprocessing (no live notification available).
     */
    fun categorize(
        sbn: StatusBarNotification?,
        contactName: String,
        conversationTitle: String,
        messagePreview: String,
        summaryText: String,
        msgType: String,
        isIncoming: Boolean,
        individualWhitelist: Set<String>,
        individualWhitelistNumbers: Set<String>
    ): CategorizationResult {
        val normalizedName = contactName ?: ""
        val normalizedTitle = conversationTitle ?: ""
        val normalizedPreview = messagePreview ?: ""
        val normalizedSummary = summaryText ?: ""

        // Strip Unicode bidi control marks (Arabic/RTL titles carry \u202a\u202e etc.)
        val cleanName = normalizedName.replace(BIDI_MARKS, "")
        val cleanTitle = normalizedTitle.replace(BIDI_MARKS, "")

        // Extract message count from (N messages) pattern in title or name
        val (hasMsgCount, msgCount, groupNameFromTitle) = extractMessageCount(cleanTitle)
            .let { (count, name) ->
                if (count > 0) Triple(true, count, name)
                else extractMessageCount(cleanName).let { (c2, n2) -> Triple(c2 > 0, c2, n2) }
            }

        // Priority 0: System noise detection (highest, always applies)
        val combinedText = "$normalizedName $normalizedTitle $normalizedPreview $normalizedSummary $msgType"
            .lowercase(java.util.Locale.ROOT)
        if (isSystemNoise(normalizedPreview, normalizedName, normalizedTitle)) {
            return CategorizationResult(ChatCategory.system_notification, confidence = 0.95f)
        }

        // Priority 0a': User-confirmed overrides (dashboard review wizard).
        // Strongest signal — beats colon-format, whitelist and all heuristics.
        val confirmedKey = canonicalChatKey(normalizedName, normalizedTitle)
        if (CONFIRMED_INDIVIDUAL_KEYS.contains(confirmedKey)) {
            return CategorizationResult(ChatCategory.individual_chat, confidence = 1.0f)
        }
        if (CONFIRMED_GROUP_KEYS.contains(confirmedKey)) {
            return CategorizationResult(ChatCategory.group_chat, confidence = 1.0f)
        }

        // Priority 0a: Explicit "GroupName: SenderName" format → group_chat.
        // Highest group marker: a whitelisted sender inside a group must still be a group.
        if (isColonGroupFormat(cleanName, cleanTitle)) {
            return CategorizationResult(ChatCategory.group_chat, confidence = 0.95f)
        }

        // Priority 0b: Individual whitelist override.
        // Whitelist matching is precise (short tokens must match the whole name),
        // so a whitelisted contact like "Fatima RPK" stays individual even when the
        // title carries a "(3 messages)" batch suffix.
        if (isInIndividualWhitelist(cleanName, individualWhitelist, individualWhitelistNumbers)) {
            return CategorizationResult(
                ChatCategory.individual_chat,
                messageCount = if (hasMsgCount) msgCount else 0,
                groupName = groupNameFromTitle
            )
        }

        // Priority 0c: (N messages) message count suffix in title or name → group_chat.
        if (hasMsgCount) {
            return CategorizationResult(
                ChatCategory.group_chat,
                messageCount = msgCount,
                groupName = groupNameFromTitle,
                confidence = 0.95f
            )
        }

        // Priority 0d: Urdu/Arabic group structure keywords → group_chat
        if (hasUrduGroupKeyword(cleanName) || hasUrduGroupKeyword(cleanTitle) ||
            hasModuleKeywords(cleanName) || hasModuleKeywords(cleanTitle)) {
            return CategorizationResult(ChatCategory.group_chat, confidence = 0.9f)
        }

        // Priority 4: Generic group keywords in conversationTitle
        if (hasGenericGroupKeywords(normalizedTitle)) {
            return CategorizationResult(ChatCategory.group_chat, confidence = 0.85f)
        }

        // Priority 5: Promo/broadcast keywords in conversationTitle
        if (hasPromoKeywords(normalizedTitle)) {
            return CategorizationResult(ChatCategory.broadcast_list, confidence = 0.9f)
        }

        // Priority 6: Channel suffix in conversationTitle
        if (CHANNEL_SUFFIX_PATTERN.matcher(normalizedTitle).matches() ||
            CHANNEL_SUFFIX_PATTERN.matcher(normalizedName).matches()) {
            return CategorizationResult(ChatCategory.channel, confidence = 0.9f)
        }

        // Priority 7: Community keyword in conversationTitle
        if (COMMUNITY_PATTERN.matcher(normalizedTitle).matches() ||
            COMMUNITY_PATTERN.matcher(normalizedName).matches()) {
            return CategorizationResult(ChatCategory.community, confidence = 0.9f)
        }

        // Priority 8: convTitle has "Group" keyword (no N messages count)
        if (normalizedTitle.contains("group", ignoreCase = true)) {
            return CategorizationResult(ChatCategory.group_chat, confidence = 0.8f)
        }

        // Priority 9-10: Named contact with actual message content → individual_chat
        val hasMessageContent = normalizedPreview.isNotBlank() &&
            !normalizedPreview.startsWith("📷") &&
            !normalizedPreview.startsWith("🎤")

        if (normalizedName.isNotBlank() && hasMessageContent) {
            return CategorizationResult(
                ChatCategory.individual_chat,
                confidence = 0.85f
            )
        }

        // Priority 10: Empty preview with named contact
        if (normalizedName.isNotBlank() && normalizedPreview.isBlank()) {
            if (normalizedTitle.contains("group", ignoreCase = true) ||
                hasGenericGroupKeywords(normalizedName) ||
                hasMsgCount) {
                return CategorizationResult(ChatCategory.group_chat, confidence = 0.7f)
            }
            return CategorizationResult(ChatCategory.individual_chat, confidence = 0.75f)
        }

        // Fallback
        return CategorizationResult(ChatCategory.unclassified, confidence = 0.5f)
    }

    /**
     * Extract message count from (N messages) pattern.
     * Returns Pair(messageCount, groupName)
     */
    fun extractMessageCount(text: String): Pair<Int, String> {
        val matcher = MSG_COUNT_PATTERN.matcher(text)
        if (matcher.find()) {
            val count = matcher.group(1)?.toIntOrNull() ?: return Pair(0, "")
            val nameMatcher = GROUP_NAME_PATTERN.matcher(text)
            val groupName = if (nameMatcher.find()) nameMatcher.group(1)?.trim() ?: "" else ""
            return Pair(count, groupName)
        }
        return Pair(0, "")
    }

    /**
     * Check if contact name matches individual whitelist (name substring or phone last 10 digits)
     */
    fun isInIndividualWhitelist(
        contactName: String,
        whitelist: Set<String>,
        whitelistNumbers: Set<String>
    ): Boolean {
        val clean = contactName.replace(BIDI_MARKS, "").trim()
        for (wl in whitelist) {
            val cleanWl = wl.trim()
            if (cleanWl.isEmpty()) continue
            // Short tokens ("J", "Quran", "Jay") must match the whole contact name,
            // otherwise a group titled "Quran Tajweed" or "Jannat Channel" is falsely
            // treated as an individual. Long multi-word names use substring match.
            if (cleanWl.length <= 3 || !cleanWl.contains(" ")) {
                if (clean.equals(cleanWl, ignoreCase = true)) return true
            } else {
                if (clean.contains(cleanWl, ignoreCase = true)) return true
            }
        }
        val phoneDigits = contactName.replace(Regex("[^0-9]"), "")
        if (phoneDigits.length >= 10) {
            val last10 = phoneDigits.takeLast(10)
            for (num in whitelistNumbers) {
                val numDigits = num.replace(Regex("[^0-9]"), "").takeLast(10)
                if (last10 == numDigits) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Check if the preview text matches system noise patterns
     */
    fun isSystemNoise(preview: String, contactName: String, title: String): Boolean {
        val combined = "$preview $contactName $title"
            .lowercase(java.util.Locale.ROOT)

        if (AGGREGATE_SUMMARY_PATTERN.containsMatchIn("$preview $title")) {
            return true
        }

        for (pattern in SYSTEM_NOISE_PATTERNS) {
            if (pattern.containsMatchIn(combined)) {
                return true
            }
        }

        if (combined.contains("checking for new messages")) {
            return true
        }

        if (combined.startsWith("sending") || combined.startsWith("downloading")) {
            return true
        }

        if (preview.isBlank() && contactName.isBlank() && title.isBlank()) {
            return true
        }

        return false
    }

    /**
     * Check if text contains module/course academic keywords
     */
    fun hasModuleKeywords(text: String): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val s = text.uppercase(java.util.Locale.ROOT)
        val hasModule = MODULE_KEYWORDS.any { lower.contains(it.lowercase(java.util.Locale.ROOT)) }
        if (!hasModule) return false
        val hasSemester = s.contains("SEMESTER")
        val hasAcademic = lower.contains("tajwid") || lower.contains("tajweed") ||
            lower.contains("tafseer") || lower.contains("quran") || lower.contains("hadees") ||
            lower.contains("sunnah") || lower.contains("fiqh") || lower.contains("aqeedah") ||
            lower.contains("madrasa") || lower.contains("jamia") || lower.contains("dars") ||
            lower.contains("class") || lower.contains("course") || lower.contains("module")
        return hasModule && (hasSemester || hasAcademic)
    }

    fun hasGenericGroupKeywords(text: String): Boolean {
        val upper = text.uppercase(java.util.Locale.ROOT)
        return GENERIC_GROUP_KEYWORDS.any { upper.contains(it) }
    }

    /**
     * True when text is exactly "GroupName: SenderName" — WhatsApp's format for
     * group notifications, where conversationTitle is the group and contactName is
     * "GroupName: SenderName".
     */
    fun isColonGroupFormat(cleanName: String, cleanTitle: String): Boolean {
        if (cleanName.isBlank()) return false
        val colonIdx = cleanName.indexOf(':')
        if (colonIdx <= 0) return false
        val prefix = cleanName.substring(0, colonIdx).trim()
        if (prefix.isEmpty()) return false
        val effectiveGroup = cleanTitle.trim().ifEmpty { prefix }
        // Title and the pre-colon prefix must agree on the group name.
        return effectiveGroup == prefix ||
            effectiveGroup.contains(prefix, ignoreCase = true) ||
            prefix.contains(effectiveGroup, ignoreCase = true)
    }

    /**
     * True when text contains an Urdu/Arabic group-structure keyword.
     */
    fun hasUrduGroupKeyword(text: String): Boolean {
        return URDU_GROUP_KEYWORDS.any { text.contains(it) }
    }

    fun hasPromoKeywords(text: String): Boolean {
        val upper = text.uppercase(java.util.Locale.ROOT)
        return PROMO_KEYWORDS.any { upper.contains(it) }
    }

    val ALL_CATEGORIES: List<ChatCategory> = listOf(
        ChatCategory.individual_chat,
        ChatCategory.group_chat,
        ChatCategory.broadcast_list,
        ChatCategory.channel,
        ChatCategory.community,
        ChatCategory.system_notification,
        ChatCategory.unclassified
    )

    private val SYSTEM_NOISE_PATTERNS = listOf(
        Regex("checking for new messages", RegexOption.IGNORE_CASE),
        Regex("sending(ing)?( video| audio| document| file)", RegexOption.IGNORE_CASE),
        Regex("downloading(ing)?", RegexOption.IGNORE_CASE),
        Regex("updating messages", RegexOption.IGNORE_CASE),
        Regex("you may have new messages", RegexOption.IGNORE_CASE),
        Regex("you deleted this message", RegexOption.IGNORE_CASE),
        Regex("this message was deleted", RegexOption.IGNORE_CASE),
        Regex("reacted to your chat", RegexOption.IGNORE_CASE),
        Regex("reacted to your status", RegexOption.IGNORE_CASE),
        Regex("liked your status", RegexOption.IGNORE_CASE),
        Regex("reshared your status", RegexOption.IGNORE_CASE),
        Regex("sent you a chat", RegexOption.IGNORE_CASE),
        Regex("sent you a ", RegexOption.IGNORE_CASE),
        Regex("sent you an ", RegexOption.IGNORE_CASE),
    )
}