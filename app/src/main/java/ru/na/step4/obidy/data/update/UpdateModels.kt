package ru.na.step4.obidy.data.update

/** Описание доступного обновления, которое отдаёт сервер. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: List<String> = emptyList(),
    val mandatory: Boolean = false,
    val sizeBytes: Long = 0L
)
