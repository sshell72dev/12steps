package ru.na.step4.obidy.ui.analysis

import android.content.Context
import android.content.Intent
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisAnswers
import ru.na.step4.obidy.data.analysis.QaPair

fun shareAnalysis(
    context: Context,
    title: String,
    createdAt: Long,
    answers: List<QaPair>
) {
    val text = AnalysisAnswers.asShareText(title, createdAt, answers)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, Ru.analysisShareSubject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, Ru.analysisShare))
}
