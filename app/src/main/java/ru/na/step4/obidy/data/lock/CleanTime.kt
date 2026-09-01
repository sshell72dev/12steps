package ru.na.step4.obidy.data.lock

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import ru.na.step4.obidy.Ru

data class CleanTime(
    val days: Long,
    val since: LocalDate,
    val years: Int,
    val months: Int,
    val restDays: Int,
    val periodLine: String,
    val totalLine: String
)

object CleanTimeCalc {
    private val numeric = listOf(
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d/M/yyyy")
    )

    fun parse(raw: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val text = raw.trim().lowercase(Locale("ru"))
        if (text.isBlank()) return null
        when (text) {
            "сегодня", "today" -> return today
            "вчера", "yesterday" -> return today.minusDays(1)
        }
        numeric.forEach { fmt ->
            try {
                return LocalDate.parse(raw.trim(), fmt)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    fun of(raw: String, today: LocalDate = LocalDate.now()): CleanTime? {
        val since = parse(raw, today) ?: return null
        val start = if (since.isAfter(today)) today else since
        val days = ChronoUnit.DAYS.between(start, today)
        val period = Period.between(start, today)
        val years = period.years.coerceAtLeast(0)
        val months = period.months.coerceAtLeast(0)
        val restDays = period.days.coerceAtLeast(0)
        return CleanTime(
            days = days,
            since = start,
            years = years,
            months = months,
            restDays = restDays,
            periodLine = periodLine(years, months, restDays),
            totalLine = "${Ru.lockCleanTotal} ${days.ru(Ru.lockDay1, Ru.lockDay2, Ru.lockDay5)}"
        )
    }

    fun unknownMessage(): String = Ru.lockCleanUnknown

    private fun periodLine(years: Int, months: Int, restDays: Int): String {
        return listOf(
            years.toLong().ru(Ru.lockYear1, Ru.lockYear2, Ru.lockYear5),
            "$months ${Ru.lockMes}",
            restDays.toLong().ru(Ru.lockDay1, Ru.lockDay2, Ru.lockDay5)
        ).joinToString("  ")
    }

    private fun Long.ru(one: String, few: String, many: String): String {
        val n = this
        val mod100 = (n % 100).toInt()
        val mod10 = (n % 10).toInt()
        val word = when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
        return "$n $word"
    }
}
