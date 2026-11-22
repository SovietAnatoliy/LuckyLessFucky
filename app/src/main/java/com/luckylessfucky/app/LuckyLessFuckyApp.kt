package com.luckylessfucky.app

import android.app.Application
import com.luckylessfucky.app.data.catalog.CatalogSeeder
import com.luckylessfucky.app.data.local.AppDatabase
import com.luckylessfucky.app.data.preferences.AppPreferencesRepository
import com.luckylessfucky.app.data.repository.PokedexRepository
import com.luckylessfucky.app.data.repository.ProfileRepository
import com.luckylessfucky.app.sync.BluetoothProfileSyncManager
import com.luckylessfucky.app.sync.FileImportExportHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LuckyLessFuckyApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    lateinit var pokedexRepository: PokedexRepository
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var fileImportExportHelper: FileImportExportHelper
        private set

    lateinit var bluetoothSyncManager: BluetoothProfileSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferencesRepository = AppPreferencesRepository(this)
        pokedexRepository = PokedexRepository(
            pokemonVariantDao = database.pokemonVariantDao(),
            userPokedexDao = database.userPokedexDao()
        )
        profileRepository = ProfileRepository(
            tradeProfileDao = database.tradeProfileDao(),
            pokedexRepository = pokedexRepository,
            preferencesRepository = preferencesRepository
        )
        fileImportExportHelper = FileImportExportHelper(this)
        bluetoothSyncManager = BluetoothProfileSyncManager(this)

        val seeder = CatalogSeeder(this, database.pokemonVariantDao())
        applicationScope.launch {
            seeder.ensureSeeded()
            preferencesRepository.getOrCreateUserProfileId()
        }
    }
}
