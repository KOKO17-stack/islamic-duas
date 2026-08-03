package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DhikrItem(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val virtue: String,
    val source: String,
    val count: Int = 1,
    val category: String = "" // morning, evening, after_salah
)

class AdhkarEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("adhkar", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        val MORNING_ADHKAR = listOf(
            DhikrItem("m1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ", "A'udhu billahi minash shaytanir rajeem", "میں اللہ کی پناہ مانگتی ہوں شیطان مردود سے", "صبح کے آغاز میں شیطان سے پناہ مانگنا سنت ہے", "ابو داؤد", 1, "morning"),
            DhikrItem("m2", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں، زندہ اور خودمختار", "جو صبح آیت الکرسی پڑھے گا وہ شام تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 1, "morning"),
            DhikrItem("m3", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "جو صبح سورۃ الاخلاص تین بار پڑھے گا وہ شام تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 3, "morning"),
            DhikrItem("m4", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "صبح کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "morning"),
            DhikrItem("m5", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "صبح کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "morning"),
            DhikrItem("m6", "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ", "Allahumma bika asbahna wa bika amsayna wa bika nahya wa bika namutu wa ilaykan nushur", "اے اللہ، تیرے ہی نام سے ہم نے صبح کی اور تیرے ہی نام سے شام کی، تیرے ہی نام سے ہم زندہ ہیں اور تیرے ہی نام سے مرتے ہیں، اور تجھی کی طرف اٹھ کر جانا ہے", "صبح کی دعا — اللہ کے نام سے دن شروع کرنا", "ابو داؤد", 1, "morning"),
            DhikrItem("m7", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", "Allahumma anta rabbi la ilaha illa anta khalaqtani wa ana 'abduka wa ana 'ala 'ahdika wa wa'dika mastata'tu a'udhu bika min sharri ma sana'tu abu'u laka bini'matika 'alayya wa abu'u bidhanbi faghfir li fainnahu la yaghfirudh dhunuba illa anta", "اے اللہ، تو میرا رب ہے، تیرے علاوہ کوئی معبود نہیں، تو نے مجھے بنایا اور میں تیری بندی ہوں، اور میں تیرے عہد اور وعدے پر ہوں جہاں تک میں کر سکتی ہوں، میں اپنے کیے کی برائی سے تیری پناہ مانگتی ہوں، تیرے ان احسانات کا اقرار کرتی ہوں جو مجھ پر ہیں اور اپنے گناہوں کا بھی، تو مجھے بخش دے، کیونکہ تیرے سوا گناہ کوئی نہیں معاف کرتا", "جو صبح یقین کے ساتھ یہ دعا پڑھے گا وہ جنت میں جائے گا", "صحیح البخاری", 1, "morning"),
            DhikrItem("m8", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ×100", "Subhanallahi wa bihamdihi 100x", "اللہ پاک ہے اور اس کی حمد ہے", "جو صبح 100 بار سبحان اللہ وبحمده پڑھے گا اس کے گناہ معاف ہو جائیں گے", "صحیح مسلم", 100, "morning"),
            DhikrItem("m9", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", "La ilaha illallahu wahdahu la sharika lahu lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir", "اللہ کے علاوہ کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں، اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے", "جو صبح دس بار یہ پڑھے گا اس کے گناہ معاف ہو جائیں گے اگرچہ سمندر کے جھاگ کے برابر ہوں", "صحیح البخاری", 10, "morning"),
            DhikrItem("m10", "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا وَرِزْقًا طَيِّبًا وَعَمَلًا مُتَقَبَّلًا", "Allahumma inni as'aluka 'ilman nafi'an wa rizqan tayyiban wa 'amalan mutaqabbalan", "اے اللہ، میں تجھ سے نفع بخش علم، پاکیزہ رزق اور مقبول عمل مانگتی ہوں", "صبح کی دعا — علم نافع کی طلب", "ابن ماجہ", 1, "morning"),
            DhikrItem("m11", "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا", "Radeetu billahi rabban wa bil islami dinan wa bi muhammadin sallallahu 'alayhi wa sallam nabiyya", "میں اللہ کو رب مان کر راضی ہوں اور اسلام کو دین اور محمد صلی اللہ علیہ وسلم کو نبی", "جو صبح یہ تین بار پڑھے گا، اللہ اسے قیامت کے دن خوش کرے گا", "سنن النسائی", 3, "morning"),
            DhikrItem("m12", "اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ", "Allahumma ma asbaha bi min ni'matin aw bi ahadin min khalqika faminka wahdaka la sharika lak falakal hamdu wa lakash shukr", "اے اللہ، جو بھی نعمت مجھے صبح ملی یا تیری کسی مخلوق کو ملی، وہ صرف تیری طرف سے ہے، تیرا کوئی شریک نہیں، پس تیرے لیے تعریف اور تیرا ہی شکر ہے", "صبح کی دعا — نعمتوں کا شکر", "ابو داؤد", 1, "morning"),
            DhikrItem("m13", "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ", "Asbahna wa asbahal mulku lillah walhamdu lillah la ilaha illallahu wahdahu la sharika lah lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir rabbi as'aluka khayra ma fi hadhal yawmi wa khayra ma ba'dahu wa a'udhu bika min sharri ma fi hadhal yawmi wa sharri ma ba'dahu rabbi a'udhu bika minal kasali wa su'il kibari rabbi a'udhu bika min 'adhabin fin nari wa 'adhabin fil qabr", "ہم نے صبح کی اور ساری بادشاہی اللہ کے لیے ہے، تمام تعریف اللہ کے لیے، اللہ کے سوا کوئی معبود نہیں وہ اکیلا ہے اس کا کوئی شریک نہیں، اسی کی بادشاہی اور اسی کی تعریف ہے اور وہ ہر چیز پر قادر ہے۔ اے میرے رب میں تجھ سے آج کے دن کی بھلائی اور اس کے بعد کی بھلائی مانگتی ہوں اور آج کے دن کی برائی اور اس کے بعد کی برائی سے پناہ مانگتی ہوں۔ اے میرے رب میں کاہلی اور بڑھاپے کی برائی سے تیری پناہ مانگتی ہوں، اور دوزخ کے عذاب اور قبر کے عذاب سے تیری پناہ مانگتی ہوں", "صبح کی دعا — دن کی بھلائی کی طلب", "صحیح مسلم", 1, "morning"),
            DhikrItem("m14", "اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ وَأُشْهِدُ حَمَلَةَ عَرْشِكَ وَمَلَائِكَتَكَ وَجَمِيعَ خَلْقِكَ أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ", "Allahumma inni asbahtu ushhiduka wa ushhidu hamalata 'arshika wa mala'ikataka wa jami'a khalqika annaka antallahu la ilaha illa anta wahdaka la sharika lak wa anna muhammadan 'abduka wa rasuluk", "اے اللہ، میں نے صبح کی، میں تجھے گواہ بناتی ہوں اور تیرے عرش کے اٹھانے والوں کو، تیرے فرشتوں کو اور تیری ساری مخلوق کو گواہ بناتی ہوں کہ تو ہی اللہ ہے، تیرے سوا کوئی معبود نہیں، تو اکیلا ہے اور تیرا کوئی شریک نہیں، اور محمد صلی اللہ علیہ وسلم تیرے بندے اور رسول ہیں", "صبح کی دعا — توحید کی گواہی", "ابو داؤد", 4, "morning"),
            DhikrItem("m15", "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ", "Hasbiyallahu la ilaha illa huwa 'alayhi tawakkaltu wa huwa rabbul 'arshil 'azim", "مجھے اللہ کافی ہے، اس کے سوا کوئی معبود نہیں، اسی پر میں نے توکل کیا اور وہی عظیم عرش کا رب ہے", "جو صبح سات بار یہ پڑھے گا اللہ اس کے تمام معاملات کا کفیل ہو جائے گا", "سنن ابو داؤد", 7, "morning"),
            DhikrItem("m16", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", "Bismillahil lazi la yadurru ma'asmihi shay'un fil ardi wa la fis sama'i wa huwas sami'ul 'alim", "اللہ کے نام سے جس کے نام کے ساتھ زمین اور آسمان کی کوئی چیز نقصان نہیں پہنچا سکتی، اور وہ سننے والا جاننے والا ہے", "جو صبح تین بار یہ پڑھے گا اسے کوئی نقصان نہ پہنچے گا", "سنن الترمذی", 3, "morning"),
            DhikrItem("m17", "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ", "Ya hayyu ya qayyumu birahmatika astaghithu aslih li sha'ni kullahu wa la takilni ila nafsi tarfata 'ayn", "اے زندہ، اے خودمختار! تیری رحمت کے وسیلے سے میں مدد مانگتی ہوں، میرے تمام معاملات سنوار دے اور مجھے پلک جھپکنے کے برابر بھی میرے نفس کے حوالے نہ کر", "صبح و شام کی دعا — اللہ پر توکل", "حاکم، صحیح الاسناد", 1, "morning"),
            DhikrItem("m18", "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ", "A'udhu bikalimatillahit tammati min sharri ma khalaq", "میں اللہ کے مکمل کلمات کی پناہ مانگتی ہوں ہر اس چیز کی برائی سے جو اس نے پیدا کی", "جو صبح تین بار یہ پڑھے گا اسے کوئی ضرر نہ پہنچے گا", "صحیح مسلم", 3, "morning"),
            DhikrItem("m19", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ", "Subhanallahi wa bihamdihi 'adada khalqihi wa ridha nafsihi wa zinata 'arshihi wa midada kalimatih", "اللہ پاک ہے اور اس کی حمد ہے جتنی اس کی مخلوق ہے، جتنی اس کی رضا، جتنا اس کا عرش اور جتنی اس کے کلمات کی سیاہی ہے", "صبح و شام کی تسبیح — اللہ کی حمد", "صحیح مسلم", 3, "morning"),
            DhikrItem("m20", "اللَّهُمَّ عَافِنِي فِي بَدَنِي اللَّهُمَّ عَافِنِي فِي سَمْعِي اللَّهُمَّ عَافِنِي فِي بَصَرِي لَا إِلَهَ إِلَّا أَنْتَ", "Allahumma 'afini fi badani allahumma 'afini fi sam'i allahumma 'afini fi basari la ilaha illa anta", "اے اللہ، میرے جسم کو عافیت دے، اے اللہ، میری سماعت کو عافیت دے، اے اللہ، میری بینائی کو عافیت دے، تیرے سوا کوئی معبود نہیں", "صبح و شام کی دعا — جسمانی عافیت کی طلب", "سنن ابو داؤد", 3, "morning")
        )

        val EVENING_ADHKAR = listOf(
            DhikrItem("e1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ", "A'udhu billahi minash shaytanir rajeem", "میں اللہ کی پناہ مانگتی ہوں شیطان مردود سے", "ہر وقت شیطان سے پناہ مانگنا سنت ہے", "ابو داؤد", 1, "evening"),
            DhikrItem("e2", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں", "جو شام آیت الکرسی پڑھے گا وہ صبح تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 1, "evening"),
            DhikrItem("e3", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "شام کے اذکار میں سورۃ الاخلاص پڑھنا سنت ہے", "صحیح البخاری", 3, "evening"),
            DhikrItem("e4", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "شام کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "evening"),
            DhikrItem("e5", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "شام کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "evening"),
            DhikrItem("e6", "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ", "Allahumma bika amsayna wa bika asbahna wa bika nahya wa bika namutu wa ilaykal masir", "اے اللہ، تیرے ہی نام سے ہم نے شام کی اور تیرے ہی نام سے صبح کی، تیرے ہی نام سے ہم زندہ ہیں اور تیرے ہی نام سے مرتے ہیں، اور تجھی کی طرف لوٹ کر جانا ہے", "شام کی دعا — اللہ کے نام سے شام کرنا", "ابو داؤد", 1, "evening"),
            DhikrItem("e7", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", "Allahumma anta rabbi la ilaha illa anta khalaqtani wa ana 'abduka wa ana 'ala 'ahdika wa wa'dika mastata'tu a'udhu bika min sharri ma sana'tu abu'u laka bini'matika 'alayya wa abu'u bidhanbi faghfir li fainnahu la yaghfirudh dhunuba illa anta", "اے اللہ، تو میرا رب ہے، تیرے علاوہ کوئی معبود نہیں، تو نے مجھے بنایا اور میں تیری بندی ہوں، اور میں تیرے عہد اور وعدے پر ہوں جہاں تک میں کر سکتی ہوں، میں اپنے کیے کی برائی سے تیری پناہ مانگتی ہوں، تیرے ان احسانات کا اقرار کرتی ہوں جو مجھ پر ہیں اور اپنے گناہوں کا بھی، تو مجھے بخش دے، کیونکہ تیرے سوا گناہ کوئی نہیں معاف کرتا", "جو شام یہ دعا پڑھے گا وہ جنت میں جائے گا", "صحیح البخاری", 1, "evening"),
            DhikrItem("e8", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ×100", "Subhanallahi wa bihamdihi 100x", "اللہ پاک ہے اور اس کی حمد ہے", "جو شام 100 بار پڑھے گا اس کے گناہ معاف ہوں گے", "صحیح مسلم", 100, "evening"),
            DhikrItem("e9", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", "La ilaha illallahu wahdahu la sharika lahu lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir", "اللہ کے علاوہ کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں، اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے", "جو شام یہ پڑھے گا اس کے گناہ معاف ہو جائیں گے", "صحیح البخاری", 10, "evening"),
            DhikrItem("e10", "اللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا فِيهَا وَأَعُوذُ بِكَ مِنْ شَرِّ هَذِهِ اللَّيْلَةِ وَشَرِّ مَا فِيهَا", "Allahumma inni as'aluka khayra hadhihil laylati wa khayra ma fiha wa a'udhu bika min sharri hadhihil laylati wa sharri ma fiha", "اے اللہ، میں تجھ سے اس رات کی بھلائی اور اس میں جو کچھ ہے اس کی بھلائی مانگتی ہوں، اور اس رات کی برائی اور اس میں جو کچھ ہے اس کی برائی سے تیری پناہ مانگتی ہوں", "شام کی دعا — رات کی بھلائی کی طلب", "ابو داؤد", 1, "evening"),
            DhikrItem("e11", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ", "Amsayna wa amsal mulku lillah walhamdu lillah la ilaha illallahu wahdahu la sharika lah lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir rabbi as'aluka khayra ma fi hadhihil laylati wa khayra ma ba'daha wa a'udhu bika min sharri ma fi hadhihil laylati wa sharri ma ba'daha rabbi a'udhu bika minal kasali wa su'il kibari rabbi a'udhu bika min 'adhabin fin nari wa 'adhabin fil qabr", "ہم نے شام کی اور ساری بادشاہی اللہ کے لیے ہے، تمام تعریف اللہ کے لیے، اللہ کے سوا کوئی معبود نہیں وہ اکیلا ہے اس کا کوئی شریک نہیں، اسی کی بادشاہی اور اسی کی تعریف ہے اور وہ ہر چیز پر قادر ہے۔ اے میرے رب میں تجھ سے اس رات کی بھلائی اور اس کے بعد کی بھلائی مانگتی ہوں اور اس رات کی برائی اور اس کے بعد کی برائی سے پناہ مانگتی ہوں۔ اے میرے رب میں کاہلی اور بڑھاپے کی برائی سے تیری پناہ مانگتی ہوں، اور دوزخ کے عذاب اور قبر کے عذاب سے تیری پناہ مانگتی ہوں", "شام کی دعا — رات کی بھلائی کی طلب", "صحیح مسلم", 1, "evening"),
            DhikrItem("e12", "اللَّهُمَّ عَافِنِي فِي بَدَنِي اللَّهُمَّ عَافِنِي فِي سَمْعِي اللَّهُمَّ عَافِنِي فِي بَصَرِي لَا إِلَهَ إِلَّا أَنْتَ", "Allahumma 'afini fi badani allahumma 'afini fi sam'i allahumma 'afini fi basari la ilaha illa anta", "اے اللہ، میرے جسم کو عافیت دے، اے اللہ، میری سماعت کو عافیت دے، اے اللہ، میری بینائی کو عافیت دے، تیرے سوا کوئی معبود نہیں", "شام کی دعا — جسمانی عافیت کی طلب", "سنن ابو داؤد", 3, "evening"),
            DhikrItem("e13", "اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ وَأُشْهِدُ حَمَلَةَ عَرْشِكَ وَمَلَائِكَتَكَ وَجَمِيعَ خَلْقِكَ أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ", "Allahumma inni amsaytu ushhiduka wa ushhidu hamalata 'arshika wa mala'ikataka wa jami'a khalqika annaka antallahu la ilaha illa anta wahdaka la sharika lak wa anna muhammadan 'abduka wa rasuluk", "اے اللہ، میں نے شام کی، میں تجھے گواہ بناتی ہوں اور تیرے عرش کے اٹھانے والوں کو، تیرے فرشتوں کو اور تیری ساری مخلوق کو گواہ بناتی ہوں کہ تو ہی اللہ ہے، تیرے سوا کوئی معبود نہیں، تو اکیلا ہے اور تیرا کوئی شریک نہیں، اور محمد صلی اللہ علیہ وسلم تیرے بندے اور رسول ہیں", "شام کی دعا — توحید کی گواہی", "ابو داؤد", 4, "evening"),
            DhikrItem("e14", "اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ", "Allahumma ma amsa bi min ni'matin aw bi ahadin min khalqika faminka wahdaka la sharika lak falakal hamdu wa lakash shukr", "اے اللہ، جو بھی نعمت مجھے شام ملی یا تیری کسی مخلوق کو ملی، وہ صرف تیری طرف سے ہے، تیرا کوئی شریک نہیں، پس تیرے لیے تعریف اور تیرا ہی شکر ہے", "شام کی دعا — نعمتوں کا شکر", "ابو داؤد", 1, "evening"),
            DhikrItem("e15", "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ", "Hasbiyallahu la ilaha illa huwa 'alayhi tawakkaltu wa huwa rabbul 'arshil 'azim", "مجھے اللہ کافی ہے، اس کے سوا کوئی معبود نہیں، اسی پر میں نے توکل کیا اور وہی عظیم عرش کا رب ہے", "جو شام سات بار یہ پڑھے گا اللہ اس کے تمام معاملات کا کفیل ہو جائے گا", "سنن ابو داؤد", 7, "evening"),
            DhikrItem("e16", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", "Bismillahil lazi la yadurru ma'asmihi shay'un fil ardi wa la fis sama'i wa huwas sami'ul 'alim", "اللہ کے نام سے جس کے نام کے ساتھ زمین اور آسمان کی کوئی چیز نقصان نہیں پہنچا سکتی، اور وہ سننے والا جاننے والا ہے", "جو شام تین بار یہ پڑھے گا اسے کوئی نقصان نہ پہنچے گا", "سنن الترمذی", 3, "evening"),
            DhikrItem("e17", "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا", "Radeetu billahi rabban wa bil islami dinan wa bi muhammadin sallallahu 'alayhi wa sallam nabiyya", "میں اللہ کو رب مان کر راضی ہوں اور اسلام کو دین اور محمد صلی اللہ علیہ وسلم کو نبی", "جو شام یہ تین بار پڑھے گا، اللہ اسے قیامت کے دن خوش کرے گا", "سنن النسائی", 3, "evening"),
            DhikrItem("e18", "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ", "Ya hayyu ya qayyumu birahmatika astaghithu aslih li sha'ni kullahu wa la takilni ila nafsi tarfata 'ayn", "اے زندہ، اے خودمختار! تیری رحمت کے وسیلے سے میں مدد مانگتی ہوں، میرے تمام معاملات سنوار دے اور مجھے پلک جھپکنے کے برابر بھی میرے نفس کے حوالے نہ کر", "شام کی دعا — اللہ پر توکل", "حاکم، صحیح الاسناد", 1, "evening"),
            DhikrItem("e19", "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ", "A'udhu bikalimatillahit tammati min sharri ma khalaq", "میں اللہ کے مکمل کلمات کی پناہ مانگتی ہوں ہر اس چیز کی برائی سے جو اس نے پیدا کی", "جو شام تین بار یہ پڑھے گا اسے کوئی ضرر نہ پہنچے گا", "صحیح مسلم", 3, "evening"),
            DhikrItem("e20", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ", "Subhanallahi wa bihamdihi 'adada khalqihi wa ridha nafsihi wa zinata 'arshihi wa midada kalimatih", "اللہ پاک ہے اور اس کی حمد ہے جتنی اس کی مخلوق ہے، جتنی اس کی رضا، جتنا اس کا عرش اور جتنی اس کے کلمات کی سیاہی ہے", "شام کی تسبیح — اللہ کی حمد", "صحیح مسلم", 3, "evening")
        )

        val AFTER_SALAH_ADHKAR = listOf(
            DhikrItem("s1", "أَسْتَغْفِرُ اللَّهَ ×3", "Astaghfirullah 3x", "میں اللہ سے معافی مانگتی ہوں", "نماز کے بعد تین بار استغفار پڑھنا سنت ہے", "صحیح مسلم", 3, "after_salah"),
            DhikrItem("s2", "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ", "Allahumma antas salam wa minkas salam tabarakta ya dhal jalali wal ikram", "اے اللہ، تو سلام ہے اور تجھ سے سلامتی ہے، بابرکت ہے تو اے جلال اور عظمت والے", "نماز کے بعد سلام کے بعد یہ دعا پڑھنا سنت ہے", "صحیح مسلم", 1, "after_salah"),
            DhikrItem("s3", "سُبْحَانَ اللَّهِ ×33", "Subhanallah 33x", "اللہ پاک ہے", "نماز کے بعد 33 بار سبحان اللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "after_salah"),
            DhikrItem("s4", "الْحَمْدُ لِلَّهِ ×33", "Alhamdulillah 33x", "تمام تعریفیں اللہ کے لیے ہیں", "نماز کے بعد 33 بار الحمدللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "after_salah"),
            DhikrItem("s5", "اللَّهُ أَكْبَرُ ×34", "Allahu Akbar 34x", "اللہ سب سے بڑا ہے", "نماز کے بعد 34 بار اللہ اکبر پڑھنا سنت ہے", "صحیح مسلم", 34, "after_salah"),
            DhikrItem("s6", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ وَلَا مُعْطِيَ لِمَا مَنَعْتَ وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ", "La ilaha illallahu wahdahu la sharika lahu lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir allahumma la mani'a lima a'tayta wa la mu'tiya lima mana'ta wa la yanfa'u dhal jaddi minkal jadd", "اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے اس کا کوئی شریک نہیں، اسی کی بادشاہی اور اسی کی تعریف ہے اور وہ ہر چیز پر قادر ہے۔ اے اللہ، جو تو دے اسے کوئی روکنے والا نہیں اور جو تو روکے اسے کوئی دینے والا نہیں، اور تیرے پاس مال و دولت والے کی دولت کوئی فائدہ نہیں دیتی", "نماز کے بعد یہ دعا پڑھنا سنت ہے", "صحیح البخاری", 1, "after_salah"),
            DhikrItem("s7", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", "La ilaha illallahu wahdahu la sharika lahu lahul mulku wa lahul hamdu wa huwa 'ala kulli shay'in qadir", "اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے اس کا کوئی شریک نہیں، اسی کی بادشاہی اور اسی کی تعریف ہے اور وہ ہر چیز پر قادر ہے", "33+33+34 کے بعد ایک بار یہ پڑھنے سے تعداد 100 مکمل ہوتی ہے", "صحیح مسلم", 1, "after_salah"),
            DhikrItem("s8", "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ", "Allahumma a'inni 'ala dhikrika wa shukrika wa husni 'ibadatik", "اے اللہ، میری مدد فرما تیرے ذکر، تیرے شکر اور تیری بہترین عبادت پر", "نماز کے بعد یہ دعا مانگنا سنت ہے", "سنن ابو داؤد", 1, "after_salah"),
            DhikrItem("s9", "رَبِّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ", "Rabbi qini 'adhabaka yawma tab'athu 'ibadak", "اے میرے رب، مجھے اپنے عذاب سے بچا لے جس دن تو اپنے بندوں کو اٹھائے گا", "فجر اور مغرب کی نماز کے بعد یہ دعا پڑھنا سنت ہے", "صحیح مسلم", 3, "after_salah")
        )

        val SLEEP_ADHKAR = listOf(
            DhikrItem("sl1", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں، زندہ اور خودمختار", "سوتے وقت آیت الکرسی پڑھنا قبر کا عذاب دور کرتا ہے", "صحیح البخاری", 1, "sleep"),
            DhikrItem("sl2", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "سوتے وقت سورۃ الاخلاص تین بار پڑھنا سنت ہے", "صحیح البخاری", 3, "sleep"),
            DhikrItem("sl3", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "سوتے وقت معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "sleep"),
            DhikrItem("sl4", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "سوتے وقت معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "sleep"),
            DhikrItem("sl5", "سُبْحَانَ اللَّهِ ×33", "Subhanallah 33x", "اللہ پاک ہے", "سوتے وقت 33 بار سبحان اللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "sleep"),
            DhikrItem("sl6", "الْحَمْدُ لِلَّهِ ×33", "Alhamdulillah 33x", "تمام تعریفیں اللہ کے لیے ہیں", "سوتے وقت 33 بار الحمدللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "sleep"),
            DhikrItem("sl7", "اللَّهُ أَكْبَرُ ×34", "Allahu Akbar 34x", "اللہ سب سے بڑا ہے", "سوتے وقت 34 بار اللہ اکبر پڑھنا سنت ہے", "صحیح مسلم", 34, "sleep"),
            DhikrItem("sl8", "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا", "Bismika allahumma amutu wa ahya", "اے اللہ، تیرے نام پر میں مرتی ہوں اور جیتی ہوں", "سونے کی دعا — اللہ کے نام پر سونا", "صحیح البخاری", 1, "sleep"),
            DhikrItem("sl9", "اللَّهُمَّ إِنِّي أَسْلَمْتُ نَفْسِي إِلَيْكَ وَوَجَّهْتُ وَجْهِي إِلَيْكَ وَفَوَّضْتُ أَمْرِي إِلَيْكَ وَأَلْجَأْتُ ظَهْرِي إِلَيْكَ رَغْبَةً وَرَهْبَةً إِلَيْكَ لَا مَلْجَأَ وَلَا مَنْجَا مِنْكَ إِلَّا إِلَيْكَ آمَنْتُ بِكِتَابِكَ الَّذِي أَنْزَلْتَ وَبِنَبِيِّكَ الَّذِي أَرْسَلْتَ", "Allahumma inni aslamtu nafsi ilayka wa wajjahtu wajhi ilayka wa fawwadtu amri ilayka wa alja'tu dhahri ilayka raghbatan wa rahbatan ilayka la malja'a wa la manja minka illa ilayka amantu bikitabikal lazi anzalta wa binabiyyikal lazi arsalt", "اے اللہ، میں اپنی جان تیری طرف سپرد کرتی ہوں، اپنا چہرہ تیری طرف کرتی ہوں، اپنا معاملہ تیرے حوالے کرتی ہوں، اور اپنی پشت تیری پناہ میں دیتی ہوں، رغبت اور خوف کے ساتھ تیری طرف، تیرے سوا کوئی پناہ اور کوئی نجات دینے والا نہیں، میں تیری نازل کردہ کتاب پر اور تیرے بھیجے ہوئے نبی پر ایمان لائی", "سونے کی دعا — اللہ کے سپرد ہونا", "صحیح مسلم", 1, "sleep"),
            DhikrItem("sl10", "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ", "Allahumma qini 'adhabaka yawma tab'athu 'ibadaka", "اے اللہ، مجھے تیرے عذاب سے بچا لے جس دن تو اپنے بندوں کو اٹھائے گا", "قبر کے عذاب سے پناہ کی دعا", "سنن الترمذی", 1, "sleep")
        )

        fun getAllAdhkar(): List<DhikrItem> = MORNING_ADHKAR + EVENING_ADHKAR + AFTER_SALAH_ADHKAR + SLEEP_ADHKAR
    }

    val today: String get() = dateFormat.format(Date())

    fun isDhikrDone(dhikrId: String): Boolean {
        return prefs.getBoolean("${dhikrId}_$today", false)
    }

    fun markDhikrDone(dhikrId: String) {
        prefs.edit().putBoolean("${dhikrId}_$today", true).apply()
    }

    fun unmarkDhikr(dhikrId: String) {
        prefs.edit().remove("${dhikrId}_$today").apply()
    }

    fun getMorningProgress(): Pair<Int, Int> {
        val done = MORNING_ADHKAR.count { isDhikrDone(it.id) }
        return done to MORNING_ADHKAR.size
    }

    fun getEveningProgress(): Pair<Int, Int> {
        val done = EVENING_ADHKAR.count { isDhikrDone(it.id) }
        return done to EVENING_ADHKAR.size
    }

    fun getAfterSalahProgress(): Pair<Int, Int> {
        val done = AFTER_SALAH_ADHKAR.count { isDhikrDone(it.id) }
        return done to AFTER_SALAH_ADHKAR.size
    }

    fun getSleepProgress(): Pair<Int, Int> {
        val done = SLEEP_ADHKAR.count { isDhikrDone(it.id) }
        return done to SLEEP_ADHKAR.size
    }

    fun isMorningComplete(): Boolean {
        return MORNING_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isEveningComplete(): Boolean {
        return EVENING_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isAfterSalahComplete(): Boolean {
        return AFTER_SALAH_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isSleepComplete(): Boolean {
        return SLEEP_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun markAllMorningDone() {
        MORNING_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun markAllEveningDone() {
        EVENING_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun markAllSleepDone() {
        SLEEP_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun unmarkAllMorning() {
        MORNING_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun unmarkAllEvening() {
        EVENING_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun unmarkAllSleep() {
        SLEEP_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun resetDaily() {
        val editor = prefs.edit()
        getAllAdhkar().forEach { editor.remove("${it.id}_$today") }
        editor.apply()
    }
}
