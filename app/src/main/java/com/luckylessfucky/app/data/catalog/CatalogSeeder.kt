package com.luckylessfucky.app.data.catalog

import android.content.Context
import com.luckylessfucky.app.data.local.dao.PokemonVariantDao
import com.luckylessfucky.app.data.local.entity.PokemonVariantEntity
import com.luckylessfucky.app.domain.model.PokemonVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class CatalogSeeder(
    private val context: Context,
    private val pokemonVariantDao: PokemonVariantDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val currentCount = pokemonVariantDao.getCount()
        if (currentCount > 0) return@withContext

        val rawJson = context.assets.open(CATALOG_ASSET_FILE).bufferedReader().use { it.readText() }
        val variants = parseCatalogJson(rawJson)
        val entities = variants.map { PokemonVariantEntity.fromDomain(it) }
        pokemonVariantDao.insertAll(entities)
    }

    fun parseCatalogJson(rawJson: String): List<PokemonVariant> {
        return json.decodeFromString<List<PokemonVariant>>(rawJson)
    }

    companion object {
        const val CATALOG_ASSET_FILE = "pokemon_go_catalog.json"
    }
}
