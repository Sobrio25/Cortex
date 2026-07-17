package com.aiagents.app.presentation.workspace_detail

data class OptionSelectionRequest(
    val title: String,
    val options: List<String>,
    val messageContent: String = ""
)

data class MultiQuestionQueue(
    val questions: List<OptionSelectionRequest>,
    val answers: List<String> = emptyList(),
    val currentIndex: Int = 0
) {
    val currentQuestion: OptionSelectionRequest get() = questions[currentIndex]
    val isLastQuestion: Boolean get() = currentIndex >= questions.size - 1
    val progress: String get() = "${currentIndex + 1}/${questions.size}"
}

/** Pure parser/formatter for the legacy `<ask_options>` response protocol. */
internal object OptionSelectionParser {
    private val optionsTagRegex = Regex(
        """<ask_options\s+(?:titulo|title)="([^"]+)">([\s\S]*?)</ask_options>""",
        RegexOption.IGNORE_CASE
    )
    private val optionItemRegex = Regex("""^\s*[-•*]\s+(.+)$""", RegexOption.MULTILINE)

    fun parseAll(content: String): Pair<List<OptionSelectionRequest>, String> {
        val matches = optionsTagRegex.findAll(content).toList()
        if (matches.isEmpty()) return emptyList<OptionSelectionRequest>() to content

        val requests = matches.mapNotNull { match ->
            val options = optionItemRegex.findAll(match.groupValues[2])
                .map { it.groupValues[1].trim() }
                .filter(String::isNotEmpty)
                .toList()
            options.takeIf { it.isNotEmpty() }?.let {
                OptionSelectionRequest(match.groupValues[1].trim(), it)
            }
        }

        val cleanContent = matches.fold(content) { remaining, match ->
            remaining.replace(match.value, "")
        }.trim()
        return requests to cleanContent
    }

    fun attachMessageToFirst(
        requests: List<OptionSelectionRequest>,
        cleanContent: String
    ): List<OptionSelectionRequest> = requests.mapIndexed { index, request ->
        if (index == 0 && cleanContent.isNotBlank()) request.copy(messageContent = cleanContent)
        else request
    }

    fun formatAnswers(
        questions: List<OptionSelectionRequest>,
        answers: List<String>
    ): String = questions.zip(answers) { question, answer ->
        "**${question.title}**: $answer"
    }.joinToString("\n")
}
