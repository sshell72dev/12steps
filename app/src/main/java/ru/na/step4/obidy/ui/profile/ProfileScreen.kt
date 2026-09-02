package ru.na.step4.obidy.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.data.profile.ProfileProblems
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire
import ru.na.step4.obidy.data.profile.ProfileRu
import ru.na.step4.obidy.data.profile.QuestionnaireQuestion
import ru.na.step4.obidy.data.i18n.AppLanguages
import ru.na.step4.obidy.data.i18n.LocaleHelper
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.components.navigationBarsPaddingIfImeHidden
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.VoiceOutlinedTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var name by remember(snap.name) { mutableStateOf(snap.name) }
    var birth by remember(snap.birthYear) { mutableStateOf(snap.birthYear) }
    var place by remember(snap.location) { mutableStateOf(snap.location) }
    var about by remember(snap.aboutMe) { mutableStateOf(snap.aboutMe) }
    var program by remember(snap.program) { mutableStateOf(snap.program) }
    var customProgram by remember { mutableStateOf("") }
    var problems by remember(snap.problems) { mutableStateOf(snap.problems) }
    var personality by remember(snap.personality) { mutableStateOf(snap.personality) }
    var gender by remember(snap.answers) {
        mutableStateOf(snap.answers[ProfileQuestionnaire.ID_GENDER].orEmpty())
    }
    var addiction by remember(snap.answers) {
        mutableStateOf(snap.answers[ProfileQuestionnaire.ID_ADDICTION].orEmpty())
    }
    var lastUse by remember(snap.answers) {
        mutableStateOf(snap.answers[ProfileQuestionnaire.ID_LAST_USE].orEmpty())
    }
    var reason by remember(snap.answers) {
        mutableStateOf(snap.answers[ProfileQuestionnaire.ID_REASON].orEmpty())
    }
    var motivation by remember(snap.answers) {
        mutableStateOf(snap.answers[ProfileQuestionnaire.ID_MOTIVATION].orEmpty())
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            viewModel.clearNotice()
        }
    }

    val filled = listOf(
        name, program, gender, addiction, lastUse, reason, motivation, birth, place, about
    ).count { it.isNotBlank() }

    fun saveAll() {
        viewModel.save(
            name = name,
            birthYear = birth,
            location = place,
            aboutMe = about,
            program = program,
            problems = problems,
            personality = personality,
            answers = mapOf(
                ProfileQuestionnaire.ID_GENDER to gender,
                ProfileQuestionnaire.ID_ADDICTION to addiction,
                ProfileQuestionnaire.ID_LAST_USE to lastUse,
                ProfileQuestionnaire.ID_REASON to reason,
                ProfileQuestionnaire.ID_MOTIVATION to motivation,
                ProfileQuestionnaire.ID_PROGRAM to program
            )
        )
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ProfileRu.eyebrow, style = MaterialTheme.typography.labelMedium, color = Amber)
                        Text(ProfileRu.title, style = MaterialTheme.typography.titleLarge, color = Forest)
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Sand.copy(alpha = 0.96f))
                    .imePadding()
                    .navigationBarsPaddingIfImeHidden()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                SaveBtn(ProfileRu.saveAll, onClick = ::saveAll)
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    ProfileRu.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LanguagePicker(
                    current = snap.languageCode,
                    onPick = viewModel::setLanguage
                )
                Text(
                    ProfileRu.filledCount.format(filled, 10),
                    color = Amber,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(ProfileRu.sectionAnketa, style = MaterialTheme.typography.titleLarge, color = Forest)
                ProfileField(name, { name = it }, ProfileRu.name, ProfileRu.nameHint)
                Text(localizedQuestion(ProfileQuestionnaire.ID_PROGRAM).text, color = Forest)
                ChipGroup(
                    options = ProfileQuestionnaire.programs,
                    selected = program,
                    onPick = { program = it },
                    labelOf = { src ->
                        val idx = ProfileQuestionnaire.programs.indexOf(src)
                        if (idx >= 0) ru.na.step4.obidy.data.i18n.I18n.t("profile.program.$idx", src) else src
                    }
                )
                ProfileField(customProgram, { customProgram = it }, ProfileRu.customProgram)
                if (customProgram.isNotBlank()) {
                    SaveBtn(ProfileRu.customProgram) { program = customProgram.trim() }
                }
                QuestionChips(localizedQuestion(ProfileQuestionnaire.ID_GENDER), gender) { gender = it }
                QuestionChips(localizedQuestion(ProfileQuestionnaire.ID_ADDICTION), addiction) { addiction = it }
                ProfileField(
                    lastUse,
                    { lastUse = it },
                    localizedQuestion(ProfileQuestionnaire.ID_LAST_USE).text,
                    localizedQuestion(ProfileQuestionnaire.ID_LAST_USE).hint
                )
                QuestionChips(localizedQuestion(ProfileQuestionnaire.ID_REASON), reason) { reason = it }
                QuestionChips(
                    localizedQuestion(ProfileQuestionnaire.ID_MOTIVATION),
                    motivation,
                    columns = 5
                ) { motivation = it }
                ProfileField(birth, { birth = it }, localizedQuestion(ProfileQuestionnaire.ID_BIRTH).text)
                ProfileField(place, { place = it }, localizedQuestion(ProfileQuestionnaire.ID_LOCATION).text)
                ProfileField(about, { about = it }, localizedQuestion(ProfileQuestionnaire.ID_ABOUT).text, minLines = 3)
                Text(ProfileRu.problems, color = Forest, style = MaterialTheme.typography.titleMedium)
                Text(ProfileRu.problemsHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileProblems.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { option ->
                            FilterChip(
                                selected = option.key in problems,
                                onClick = {
                                    problems = problems.toMutableSet().also { set ->
                                        if (!set.add(option.key)) set.remove(option.key)
                                    }
                                },
                                label = {
                                    Text(
                                        ru.na.step4.obidy.data.i18n.I18n.t(
                                            "profile.problem.${option.key}",
                                            option.label
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = chipColors()
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }

                Text(ProfileRu.sectionPersonality, style = MaterialTheme.typography.titleLarge, color = Forest)
                NoteView(NoteIds.JOURNAL_PERSONALITY, ProfileRu.personalityHint, ProfileRu.sectionPersonality)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (snap.personalityEnabled) ProfileRu.personalityOn else ProfileRu.personalityOff,
                        modifier = Modifier.weight(1f),
                        color = Forest
                    )
                    Switch(
                        checked = snap.personalityEnabled,
                        onCheckedChange = viewModel::setPersonalityEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = Forest)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (snap.personalityCollectEnabled) ProfileRu.collectOn else ProfileRu.collectOff,
                        modifier = Modifier.weight(1f),
                        color = Forest
                    )
                    Switch(
                        checked = snap.personalityCollectEnabled,
                        onCheckedChange = viewModel::setPersonalityCollect,
                        colors = SwitchDefaults.colors(checkedTrackColor = Forest)
                    )
                }
                ProfileField(
                    personality,
                    { personality = it },
                    ProfileRu.sectionPersonality,
                    ProfileRu.personalityEmpty,
                    minLines = 5
                )
                if (!notice.isNullOrBlank()) {
                    Text(notice.orEmpty(), color = Amber)
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(current: String, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var custom by remember(current) { mutableStateOf("") }
    val normalized = LocaleHelper.normalize(current)
    val options = remember(query) { AppLanguages.search(query) }
    Text(ProfileRu.language, style = MaterialTheme.typography.titleMedium, color = Forest)
    Text(ProfileRu.languageHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        AppLanguages.label(normalized),
        color = Amber,
        style = MaterialTheme.typography.labelLarge
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(ProfileRu.languageSearch) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest,
            cursorColor = Forest
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { lang ->
            FilterChip(
                selected = LocaleHelper.normalize(lang.code) == normalized,
                onClick = { onPick(lang.code) },
                label = { Text("${lang.nativeName} (${lang.code})") },
                colors = chipColors()
            )
        }
    }
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(ProfileRu.languageOther) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest,
            cursorColor = Forest
        )
    )
    if (custom.isNotBlank()) {
        SaveBtn(ProfileRu.languageApply) { onPick(custom.trim()) }
    }
}

@Composable
private fun QuestionChips(
    question: QuestionnaireQuestion,
    selected: String,
    columns: Int? = null,
    onPick: (String) -> Unit
) {
    Text(question.text, color = Forest)
    ChipGroup(
        options = question.options,
        selected = selected,
        onPick = onPick,
        columns = columns,
        labelOf = { src ->
            val idx = question.options.indexOf(src)
            if (idx >= 0) {
                ru.na.step4.obidy.data.i18n.I18n.t("profile.q.${question.id}.opt.$idx", src)
            } else {
                src
            }
        }
    )
}

@Composable
private fun ChipGroup(
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    labelOf: (String) -> String = { it },
    columns: Int? = null
) {
    if (columns != null && columns > 0) {
        options.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onPick(option) },
                        label = { Text(labelOf(option)) },
                        modifier = Modifier.weight(1f),
                        colors = chipColors()
                    )
                }
                repeat(columns - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
        return
    }
    options.forEach { option ->
        FilterChip(
            selected = selected == option,
            onClick = { onPick(option) },
            label = { Text(labelOf(option)) },
            colors = chipColors()
        )
    }
}

@Composable
private fun ProfileField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String = "",
    minLines: Int = 1
) {
    VoiceOutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (hint.isNotBlank()) ({ Text(hint) }) else null,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SaveBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
        shape = RoundedCornerShape(14.dp)
    ) { Text(label) }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Forest,
    selectedLabelColor = Sand,
    containerColor = SandDeep,
    labelColor = Forest
)

private fun q(id: String): QuestionnaireQuestion =
    ProfileQuestionnaire.questions.first { it.id == id }

private fun localizedQuestion(id: String): QuestionnaireQuestion {
    val base = q(id)
    return base.copy(
        text = ru.na.step4.obidy.data.i18n.I18n.t("profile.q.$id.text", base.text),
        hint = if (base.hint.isBlank()) ""
        else ru.na.step4.obidy.data.i18n.I18n.t("profile.q.$id.hint", base.hint)
    )
}
