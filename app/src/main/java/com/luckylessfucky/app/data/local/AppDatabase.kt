package com.luckylessfucky.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luckylessfucky.app.data.local.dao.PokemonVariantDao
import com.luckylessfucky.app.data.local.dao.TradeProfileDao
import com.luckylessfucky.app.data.local.dao.UserPokedexDao
import com.luckylessfucky.app.data.local.entity.PokemonVariantEntity
import com.luckylessfucky.app.data.local.entity.TradeProfileEntity
import com.luckylessfucky.app.data.local.entity.TradeProfileEntryEntity
import com.luckylessfucky.app.data.local.entity.UserPokedexEntryEntity

@Database(
    entities = [
        PokemonVariantEntity::class,
        UserPokedexEntryEntity::class,
        TradeProfileEntity::class,
        TradeProfileEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pokemonVariantDao(): PokemonVariantDao
    abstract fun userPokedexDao(): UserPokedexDao
    abstract fun tradeProfileDao(): TradeProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "luckylessfucky.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
