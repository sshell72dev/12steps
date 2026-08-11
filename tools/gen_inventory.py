# -*- coding: utf-8 -*-
from pathlib import Path

root = Path(r"D:\sites\4шаг\app\src\main\java\ru\na\step4\obidy")


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


questions = [
    (1, "Были ли мои чувства задеты из-за удара по моей гордости (чувству собственного достоинства)?",
     "Гордость / достоинство: унижение, обесценивание, игнор, предательство."),
    (2, "Угрожало ли что-либо моей безопасности или моему благополучию?",
     "Безопасность и благополучие: страх, угроза, потеря опоры."),
    (3, "Были ли задеты или поставлены под угрозу личные отношения?",
     "Личные отношения: близость, доверие, связь с людьми."),
    (4, "Привели ли попытки реализовать свои желания к конфликту с кем-либо или с самим собой?",
     "Желания / амбиции и конфликт вокруг них."),
    (5, "Где в основе моих действий были жадность или вожделение?",
     "Жадность, вожделение, хочу любой ценой."),
    (6, "До каких крайностей я доходил в этой обиде?",
     "Крайности: изоляция, месть, употребление, саморазрушение."),
    (7, "Каким образом я манипулировал другими, и зачем?",
     "Манипуляции и цель манипуляции."),
    (8, "В чём был эгоцентризм моего поведения?",
     "Эгоцентризм: мир крутится вокруг меня."),
    (9, "Думал ли я, что жизнь мне что-то должна?",
     "Ожидание долга от жизни / людей."),
    (10, "Каким образом мои ожидания от других людей приводили меня к разочарованию?",
     "Завышенные ожидания и разочарование."),
    (11, "Как в этой ситуации проявилась гордыня?",
     "Гордыня как реакция на удар по гордости."),
    (12, "Каким образом страх повлиял на мои действия?",
     "Страх и то, как он вел мои поступки."),
    (13, "Какие чувства я не умел или не был готов проживать, и как я их избегал?",
     "Непрожитые чувства и способы избегания."),
]

lines = [
    "package ru.na.step4.obidy.data",
    "",
    "data class InventoryQuestion(",
    "    val number: Int,",
    "    val title: String,",
    "    val hint: String",
    ")",
    "",
    "object InventoryStructure {",
    f'    const val POINT_A = "{esc("Пункт А")}"',
    f'    const val POINT_B = "{esc("Пункт Б")}"',
    f'    const val POINT_V = "{esc("Пункт В")}"',
    f'    const val TARGET_TITLE = "{esc("Кому или чему я обижен?")}"',
    f'    const val TARGET_HINT = "{esc("Человек, учреждение или концепция")}"',
    f'    const val WHAT_TITLE = "{esc("Что произошло?")}"',
    f'    const val WHAT_HINT = "{esc("Конкретная ситуация обиды своими словами")}"',
    f'    const val FELT_TITLE = "{esc("Я чувствовал")}"',
    f'    const val FELT_HINT = "{esc("Какие чувства были тогда и какие возвращаются")}"',
    f'    const val DID_TITLE = "{esc("Я делал")}"',
    f'    const val DID_HINT = "{esc("Как я реагировал: что говорил, делал или не делал")}"',
    f'    const val Q_SECTION = "{esc("13 вопросов к обиде")}"',
    f'    const val Q_SECTION_HINT = "{esc("Пункты Б (1–4) и В (5–13). Можно заполнять по частям.")}"',
    "    val questions = listOf(",
]
for n, t, h in questions:
    lines.append(f'        InventoryQuestion({n}, "{esc(t)}", "{esc(h)}"),')
lines += [
    "    )",
    "",
    "    val defaultCategoryNames = listOf(",
    f'        "{esc("Люди")}",',
    f'        "{esc("Учреждения")}",',
    f'        "{esc("Концепции")}",',
    "    )",
    "}",
    "",
]
(root / "data" / "InventoryStructure.kt").write_text("\n".join(lines), encoding="utf-8")

qfields = "\n".join(f"    val q{n}: String = \"\"," for n in range(1, 14))
progress = "\n".join(
    [
        "            if (target.isNotBlank()) n++",
        "            if (whatHappened.isNotBlank()) n++",
        "            if (iFelt.isNotBlank()) n++",
        "            if (iDid.isNotBlank()) n++",
    ]
    + [f"            if (q{n}.isNotBlank()) n++" for n in range(1, 14)]
)
answers_when = "\n".join(f"        {n} -> q{n}" for n in range(1, 14))
with_answer = "\n".join(f"        {n} -> copy(q{n} = value)" for n in range(1, 14))
resentment = f"""package ru.na.step4.obidy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resentments")
data class Resentment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val target: String = "",
    val whatHappened: String = "",
    val iFelt: String = "",
    val iDid: String = "",
{qfields}
    val notes: String = "",
    val isCompleted: Boolean = false,
    val cause: String = "",
    val affectedAreas: String = "",
    val myPart: String = "",
    val defects: String = "",
    val higherPowerWish: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {{
    val isDraft: Boolean
        get() = target.isBlank() && whatHappened.isBlank() && cause.isBlank()

    val progressSteps: Int
        get() {{
            var n = 0
{progress}
            return n
        }}

    fun answerFor(number: Int): String = when (number) {{
{answers_when}
        else -> ""
    }}

    fun withAnswer(number: Int, value: String): Resentment = when (number) {{
{with_answer}
        else -> this
    }}

    companion object {{
        const val TOTAL_STEPS = 17
    }}
}}
"""
(root / "data" / "Resentment.kt").write_text(resentment, encoding="utf-8")
print("ok")
