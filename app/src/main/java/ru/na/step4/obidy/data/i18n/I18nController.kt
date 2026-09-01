package ru.na.step4.obidy.data.i18n

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class I18nController(
    context: Context,
    initialLanguage: String
) {
    private val cache = TranslationCache(context)
    private val mutex = Mutex()
    private val _languageCode = MutableStateFlow(LocaleHelper.normalize(initialLanguage))
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val currentLanguage: String get() = _languageCode.value

    fun isRussian(): Boolean = LocaleHelper.isRussian(currentLanguage)

    fun setLanguage(code: String) {
        val normalized = LocaleHelper.normalize(code)
        if (normalized == _languageCode.value) return
        _languageCode.value = normalized
        bump()
    }

    fun t(key: String, sourceRu: String): String {
        SourceCatalog.register(key, sourceRu)
        if (sourceRu.isBlank()) return sourceRu
        if (isRussian()) return sourceRu
        return cache.get(currentLanguage, key, sourceRu) ?: sourceRu
    }

    fun t(key: String): String {
        val source = SourceCatalog.get(key) ?: return key
        return t(key, source)
    }

    suspend fun ensure(keys: Collection<String>): Boolean {
        if (isRussian() || keys.isEmpty()) return true
        return mutex.withLock {
            val missing = ArrayList<TranslateClient.Item>()
            keys.forEach { key ->
                val source = SourceCatalog.get(key) ?: return@forEach
                if (source.isBlank()) return@forEach
                if (!cache.hasValid(currentLanguage, key, source)) {
                    missing += TranslateClient.Item(key, source)
                }
            }
            if (missing.isEmpty()) return@withLock true
            _loading.value = true
            try {
                val batches = missing.chunked(BATCH_SIZE)
                var anyOk = false
                for (batch in batches) {
                    val result = withContext(Dispatchers.IO) {
                        TranslateClient.translate(currentLanguage, batch)
                    }
                    when (result) {
                        is TranslateClient.Result.Err -> Unit
                        is TranslateClient.Result.Ok -> {
                            val mapped = result.items.mapNotNull { item ->
                                val source = SourceCatalog.get(item.key) ?: return@mapNotNull null
                                item.key to CachedTranslation(
                                    text = item.text,
                                    sourceHash = SourceCatalog.sourceHash(source)
                                )
                            }.toMap()
                            if (mapped.isNotEmpty()) {
                                cache.putAll(currentLanguage, mapped)
                                anyOk = true
                            }
                        }
                    }
                }
                if (anyOk) bump()
                anyOk || missing.isEmpty()
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun ensureBundle(bundle: ScreenBundle): Boolean =
        ensure(bundle.keys())

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    companion object {
        private const val BATCH_SIZE = 40
    }
}

object I18n {
    @Volatile
    private var controller: I18nController? = null

    fun bind(ctrl: I18nController) {
        controller = ctrl
    }

    fun controller(): I18nController? = controller

    fun t(key: String, sourceRu: String): String {
        SourceCatalog.register(key, sourceRu)
        return controller?.t(key, sourceRu) ?: sourceRu
    }

    fun languageCode(): String =
        controller?.currentLanguage ?: LocaleHelper.deviceLanguage()

    fun isRussian(): Boolean = LocaleHelper.isRussian(languageCode())

    fun locale() = LocaleHelper.toLocale(languageCode())

    fun speechTag(): String = LocaleHelper.speechTag(languageCode())

    fun languageInstruction(): String = LocaleHelper.languageInstruction(languageCode())
}
