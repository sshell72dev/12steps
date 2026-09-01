package ru.na.step4.obidy.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.io.File
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.auth.AppLockStore
import ru.na.step4.obidy.auth.BiometricUnlock
import ru.na.step4.obidy.data.lock.CleanTimeCalc
import ru.na.step4.obidy.data.lock.LockMoodStore
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand

private const val MIN_PASSWORD_LENGTH = 4
private val OnPhoto = Color(0xFFF7F1E4)
private val PhotoScrim = Color(0xCC1B2E24)
private val Glass = Color(0xB3000000)

@Composable
fun AppLockGate(
    activity: FragmentActivity,
    store: AppLockStore,
    unlocked: Boolean,
    onBiometricPromptActive: (Boolean) -> Unit = {},
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (!unlocked) {
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { keyboard?.hide() }
            BackHandler { }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                if (!store.isConfigured) {
                    SetupLockScreen(
                        store = store,
                        biometricAvailable = BiometricUnlock.canAuthenticate(activity),
                        onReady = onUnlocked
                    )
                } else {
                    UnlockLockScreen(
                        activity = activity,
                        store = store,
                        onBiometricPromptActive = onBiometricPromptActive,
                        onUnlocked = onUnlocked
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupLockScreen(
    store: AppLockStore,
    biometricAvailable: Boolean,
    onReady: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(biometricAvailable) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val mood = rememberLockMood()
    val photo = remember(mood) { mood.pickBackground(null, System.currentTimeMillis()) }

    fun submit() {
        when {
            password.length < MIN_PASSWORD_LENGTH ->
                error = Ru.lockPasswordTooShort
            password != confirm ->
                error = Ru.lockPasswordMismatch
            else -> {
                store.setPassword(password)
                store.biometricEnabled = biometricAvailable && useBiometric
                keyboard?.hide()
                onReady()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LockPhoto(photo)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Lock, null, tint = OnPhoto, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            Ru.lockSetupTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = OnPhoto,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            Ru.lockSetupBody,
            style = MaterialTheme.typography.bodyMedium,
            color = OnPhoto.copy(alpha = 0.88f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        LockCard {
            PasswordField(
                value = password,
                onValueChange = { password = it; error = null },
                label = Ru.lockPasswordLabel,
                imeAction = ImeAction.Next,
                glass = true
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it; error = null },
                label = Ru.lockPasswordConfirm,
                imeAction = ImeAction.Done,
                onDone = ::submit,
                glass = true
            )
            if (biometricAvailable) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { useBiometric = !useBiometric }) {
                    Text(
                        if (useBiometric) Ru.lockBiometricOn else Ru.lockBiometricOff,
                        color = OnPhoto
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = OnPhoto
                ),
                shape = RoundedCornerShape(14.dp)
            ) { Text(Ru.lockSave) }
        }
    }
    }
}

@Composable
private fun UnlockLockScreen(
    activity: FragmentActivity,
    store: AppLockStore,
    onBiometricPromptActive: (Boolean) -> Unit,
    onUnlocked: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometricOk = BiometricUnlock.canAuthenticate(activity)
    if (biometricOk && !store.biometricEnabled) {
        store.biometricEnabled = true
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val app = LocalContext.current.applicationContext as Step4App
    val mood = rememberLockMood()
    val profile by app.profileStore.snapshot.collectAsStateWithLifecycle()
    val lastUse = profile.answers[ProfileQuestionnaire.ID_LAST_USE].orEmpty()
    val displayName = profile.name.ifBlank {
        profile.answers[ProfileQuestionnaire.ID_NAME].orEmpty()
    }.trim()
    val clean = remember(lastUse) { CleanTimeCalc.of(lastUse) }
    val sessionSeed = remember { System.currentTimeMillis() }
    val photo = remember(clean?.days, sessionSeed) { mood.pickBackground(clean?.days, sessionSeed) }
    var quote by remember { mutableStateOf(mood.cachedQuote()) }

    fun tryPassword() {
        if (store.verifyPassword(password)) {
            keyboard?.hide()
            onUnlocked()
        } else {
            error = Ru.lockWrongPassword
            password = ""
        }
    }

    fun tryBiometric() {
        error = null
        onBiometricPromptActive(true)
        BiometricUnlock.prompt(
            activity = activity,
            onSuccess = {
                keyboard?.hide()
                onUnlocked()
                onBiometricPromptActive(false)
            },
            onError = {
                onBiometricPromptActive(false)
                error = it
            },
            onDismissed = { onBiometricPromptActive(false) }
        )
    }

    LaunchedEffect(Unit) {
        quote = mood.refresh()
    }
    LaunchedEffect(biometricOk) {
        if (!biometricOk) return@LaunchedEffect
        delay(350)
        tryBiometric()
    }

    Box(Modifier.fillMaxSize()) {
    LockPhoto(photo)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Glass)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (displayName.isNotBlank()) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = OnPhoto,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    Ru.lockYouAreClean,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = OnPhoto,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                if (clean != null) {
                    Text(
                        clean.periodLine,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Amber,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        clean.totalLine,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = OnPhoto.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        CleanTimeCalc.unknownMessage(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        color = OnPhoto.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    quote,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = OnPhoto,
                    textAlign = TextAlign.Center
                )
            }
        }
        LockCard {
            PasswordField(
                value = password,
                onValueChange = { password = it; error = null },
                label = Ru.lockPasswordLabel,
                imeAction = ImeAction.Done,
                onDone = ::tryPassword,
                glass = true
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFFFB4A8), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = ::tryPassword,
                enabled = password.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = OnPhoto,
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = OnPhoto.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) { Text(Ru.lockUnlock) }
        }
        if (biometricOk) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { tryBiometric() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = Ru.lockBiometricCd,
                        tint = OnPhoto,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Text(
                    Ru.lockFingerprintHint,
                    style = MaterialTheme.typography.labelLarge,
                    color = OnPhoto.copy(alpha = 0.85f)
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
    }
}

@Composable
private fun rememberLockMood(): LockMoodStore {
    val context = LocalContext.current
    return remember { LockMoodStore(context) }
}

@Composable
private fun LockPhoto(asset: String) {
    val context = LocalContext.current
    val bitmap = remember(asset) {
        runCatching {
            val file = File(asset)
            if (file.isAbsolute && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AtmosphereBackground(Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to PhotoScrim.copy(alpha = 0.55f),
                        0.38f to Color.Transparent,
                        0.72f to PhotoScrim.copy(alpha = 0.55f),
                        1f to PhotoScrim.copy(alpha = 0.82f)
                    )
                )
        )
    }
}

@Composable
private fun LockCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Glass)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    glass: Boolean = false
) {
    val fieldColors = if (glass) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OnPhoto.copy(alpha = 0.7f),
            unfocusedBorderColor = OnPhoto.copy(alpha = 0.32f),
            cursorColor = OnPhoto,
            focusedLabelColor = OnPhoto.copy(alpha = 0.9f),
            unfocusedLabelColor = OnPhoto.copy(alpha = 0.7f),
            focusedTextColor = OnPhoto,
            unfocusedTextColor = OnPhoto,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest,
            unfocusedBorderColor = Moss.copy(alpha = 0.35f),
            cursorColor = Forest,
            focusedLabelColor = Forest,
            focusedContainerColor = Sand,
            unfocusedContainerColor = Sand.copy(alpha = 0.92f)
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() }
        ),
        colors = fieldColors,
        shape = RoundedCornerShape(12.dp)
    )
}
