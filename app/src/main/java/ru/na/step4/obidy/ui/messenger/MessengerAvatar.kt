package ru.na.step4.obidy.ui.messenger

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

/**
 * Аватар чата, группы или участника: фото с сервера, а при его отсутствии —
 * первая буква имени на зелёном круге.
 */
@Composable
fun MessengerAvatar(
    avatarUrl: String,
    title: String,
    size: Dp,
    viewModel: MessengerViewModel,
    modifier: Modifier = Modifier
) {
    val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    var photo by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        photo = null
        if (avatarUrl.isBlank()) return@LaunchedEffect
        val bytes = viewModel.avatarBytes(avatarUrl) ?: return@LaunchedEffect
        photo = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    val image = photo
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Forest),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = Sand, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Иконка группы «Неудобные вопросы»: перевёрнутый вопросительный знак
 * на зелёном круге — в неё пишут от лица «Анонимный».
 */
@Composable
fun AnonQuestionsAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Forest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            color = Sand,
            fontSize = (size.value * 0.6f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.rotate(180f)
        )
    }
}
