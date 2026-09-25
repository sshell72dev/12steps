package ru.na.step4.obidy.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Пароль на локальный файл копии: AES-256-GCM, ключ выводится из пароля через PBKDF2.
 *
 * Формат файла: "12SB1" + соль(16) + iv(12) + шифртекст с тегом аутентификации.
 * Данные 12 шагов слишком чувствительные, чтобы файл копии лежал открытым текстом.
 */
object BackupCrypto {

    private const val MAGIC = "12SB1"
    private const val MAGIC_LEN = 5
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    /** Неверный пароль или испорченный файл: GCM не сошёлся. */
    class WrongPassword : Exception("wrong password")

    /** Читает признак шифрования из начала потока, не сдвигая его дальше заголовка. */
    fun isEncrypted(input: InputStream): Boolean {
        val head = ByteArray(MAGIC_LEN)
        var read = 0
        while (read < MAGIC_LEN) {
            val part = input.read(head, read, MAGIC_LEN - read)
            if (part <= 0) return false
            read += part
        }
        return String(head, Charsets.US_ASCII) == MAGIC
    }

    /** Оборачивает поток: заголовок пишется сразу, дальше всё уходит зашифрованным. */
    fun encrypting(output: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        output.write(MAGIC.toByteArray(Charsets.US_ASCII))
        output.write(salt)
        output.write(iv)
        output.flush()
        return CipherOutputStream(output, cipher(Cipher.ENCRYPT_MODE, password, salt, iv))
    }

    /** Вызывать после [isEncrypted]: читает соль и iv и снимает шифр. */
    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val salt = readBlock(input, SALT_LEN)
        val iv = readBlock(input, IV_LEN)
        return CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, password, salt, iv))
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readBlock(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val part = input.read(buffer, read, length - read)
            if (part <= 0) throw WrongPassword()
            read += part
        }
        return buffer
    }

    /** Признак неверного пароля: тег GCM не совпал. */
    fun isAuthFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is WrongPassword || current is javax.crypto.AEADBadTagException) return true
            val name = current.javaClass.simpleName
            if (name.contains("BadTag") || name.contains("BadPadding")) return true
            current = current.cause
        }
        return false
    }
}
