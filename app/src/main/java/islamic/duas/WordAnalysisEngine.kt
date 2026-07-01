package islamic.duas

class WordAnalysisEngine {

    private val data = WordAnalysisData()

    fun analyze(phrase: String): WordAnalysis? = data.getAnalysis(phrase)

    fun getByCategory(category: String): List<WordAnalysis> = data.getAnalysesByCategory(category)

    fun getAll(): List<WordAnalysis> = data.analyses

    fun search(query: String): List<WordAnalysis> = data.search(query)

    fun getCategories(): List<String> = data.analyses.map { it.category }.distinct()

    fun getTotalCount(): Int = data.getTotalAnalyses()

    fun getRandom(): WordAnalysis = data.analyses.random()

    fun getWordCount(analysis: WordAnalysis): Int = analysis.words.size
}
