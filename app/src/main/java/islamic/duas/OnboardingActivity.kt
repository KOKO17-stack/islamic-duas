package islamic.duas

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.util.Log
import android.widget.Toast
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView

class OnboardingActivity : ComponentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var nextBtn: TextView
    private lateinit var dots: List<View>
    private lateinit var userProfile: UserProfile

    private val pages = listOf(
        OnboardingPage(
            "أهل الحديث ریسرچ سینٹر",
            "السعودية و باكستان",
            "ایک اسلامی روحانی ساتھی — آپ کی عبادت، اذکار، اور دینی زندگی کو سنوارنے کے لیے"
        ),
        OnboardingPage(
            "عبادت کا سکور اور اسٹریک",
            "🔥 اپنی عبادت کا سکور بڑھائیں",
            "ہر نماز، ہر ذکر، ہر نیکی کا سکور ملتا ہے۔ اپنا اسٹریک برقرار رکھیں اور بیجز حاصل کریں۔"
        ),
        OnboardingPage(
            "آپ کا ذاتی اسلامی معاون",
            "🌿 قرآن، فقہ، اذکار اور بہت کچھ",
            "صبح و شام کے اذکار، تقابلی فقہ، گائیڈڈ سیشنز، حیض ٹریکر، اور عورتوں کے شرعی حقوق — سب ایک جگہ"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set theme before any UI operations
        setTheme(R.style.Theme_App_EmeraldDusk_Dark)

        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.onboarding_activity)

            // Initialize components
            userProfile = UserProfile(this)
            viewPager = findViewById(R.id.viewPager)
            nextBtn = findViewById(R.id.onboardingNextBtn)
            dots = listOf(
                findViewById(R.id.dot1),
                findViewById(R.id.dot2),
                findViewById(R.id.dot3)
            )

            // Set up view pager
            viewPager.adapter = OnboardingAdapter(pages)

            // Page change listener
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateDots(position)
                    nextBtn.text = if (position == pages.size - 1) "شروع کریں" else "اگلا"
                }
            })

            // Next button click listener
            nextBtn.setOnClickListener {
                val current = viewPager.currentItem
                if (current < pages.size - 1) {
                    viewPager.currentItem = current + 1
                } else {
                    showNameDialog()
                }
            }

        } catch (e: Exception) {
            Log.e("Onboarding", "Error during onboarding", e)
            Toast.makeText(this, "Onboarding error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.background = if (index == position) {
                getDrawable(R.drawable.chip_selected)
            } else {
                getDrawable(R.drawable.chip_unselected)
            }
        }
    }

    private fun showNameDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "اپنا نام درج کریں (اختیاری)"
            setTextColor(0xFFE8E6E1.toInt())
            setHintTextColor(0xFF8B7355.toInt())
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("خوش آمدید")
            .setMessage("آپ کا نام کیا ہے؟")
            .setView(input)
            .setPositiveButton("محفوظ کریں") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    userProfile.setName(name)
                }
                startQuiz()
            }
            .setNegativeButton("چھوڑیں") { _, _ ->
                startQuiz()
            }
            .setCancelable(false)
            .show()
    }

    private fun startQuiz() {
        val intent = Intent(this, PersonaQuizActivity::class.java)
        startActivity(intent)
        finish()
    }
}

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String
)

class OnboardingAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.onboardingItemTitle)
        val subtitle: TextView = itemView.findViewById(R.id.onboardingItemSubtitle)
        val description: TextView = itemView.findViewById(R.id.onboardingItemDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.onboarding_item, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        holder.title.text = page.title
        holder.subtitle.text = page.subtitle
        holder.description.text = page.description
    }

    override fun getItemCount() = pages.size
}
