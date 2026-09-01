package ru.na.step4.obidy.data.messenger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessengerChatRow::class, MessengerMessageRow::class, MessengerContactRow::class],
    version = 1,
    exportSchema = false
)
abstract class MessengerDatabase : RoomDatabase() {
    abstract fun dao(): MessengerDao

    companion object {
        @Volatile
        private var instance: MessengerDatabase? = null

        fun get(context: Context): MessengerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessengerDatabase::class.java,
                    "messenger.db"
                ).build().also { instance = it }
            }
        }
    }
}
