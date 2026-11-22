package com.luckylessfucky.app.ui.pokedex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckylessfucky.app.domain.model.NeededStatus
import com.luckylessfucky.app.domain.model.PokedexItem
import com.luckylessfucky.app.domain.model.TradeProfile

private val NeededGreen = Color(0xFF15803D)
private val NotNeededSlate = Color(0xFF0F766E)
private val ShinyLockedAmber = Color(0xFFB45309)
private val ShinyGold = Color(0xFFD97706)

@Composable
fun PokedexScreen(
    uiState: PokedexUiState,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (PokedexFilter) -> Unit,
    onTogglePokemon: (PokedexItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Summary Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Offline Shiny & Event Pokédex",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Not Needed (In Dex): ${uiState.notNeededCount}  •  Needed: ${uiState.neededCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.activeTargetProfile != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Target: ${uiState.activeTargetProfile.username}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search Pokémon, #Dex, or Event Costume...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PokedexFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pokémon List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = uiState.items,
                key = { it.variant.variantId }
            ) { item ->
                PokedexVariantCard(
                    item = item,
                    activeTargetProfile = uiState.activeTargetProfile,
                    onToggle = { onTogglePokemon(item) }
                )
            }
        }
    }
}

@Composable
private fun PokedexVariantCard(
    item: PokedexItem,
    activeTargetProfile: TradeProfile?,
    onToggle: () -> Unit
) {
    val variant = item.variant
    val isShinyAvailable = variant.isShinyAvailableInGame

    val borderStroke = if (!isShinyAvailable) {
        BorderStroke(1.5.dp, ShinyLockedAmber.copy(alpha = 0.65f))
    } else if (item.isRecorded) {
        BorderStroke(1.dp, NotNeededSlate.copy(alpha = 0.5f))
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = if (!isShinyAvailable) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = variant.formattedDexNumber,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = variant.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Badges Row: Needed/Not Needed + Shiny Availability + Event Badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Required Label: "Not Needed" if recorded in user's Pokédex, "Needed" otherwise
                        val isNotNeeded = item.neededStatus == NeededStatus.NOT_NEEDED
                        Surface(
                            color = if (isNotNeeded) {
                                Color(0xFFDCFCE7)
                            } else {
                                Color(0xFFFEF3C7)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.neededLabel,
                                color = if (isNotNeeded) NeededGreen else Color(0xFF92400E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Event / Costume Badge
                        if (variant.isEventVariant) {
                            Surface(
                                color = Color(0xFFEDE9FE),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Celebration,
                                        contentDescription = "Event Variant",
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Event Variant",
                                        color = Color(0xFF6D28D9),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Manual Add / Remove Button
                if (item.isRecorded) {
                    FilledTonalButton(
                        onClick = onToggle,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "In Pokédex",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggle,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add to Pokédex",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Shiny Availability Indicator (Required: If a Pokémon and/or an event variant
            // does not have a shiny version, show it in the Pokédex and visually indicate that
            // it cannot be shiny as of yet in the game)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isShinyAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Shiny Available",
                            tint = ShinyGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shiny Available in Pokémon GO",
                            fontSize = 11.sp,
                            color = ShinyGold,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, ShinyLockedAmber),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Shiny Locked",
                                tint = ShinyLockedAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cannot Be Shiny as of Yet in Game",
                                fontSize = 11.sp,
                                color = ShinyLockedAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // If a Lucky Trade Target profile is currently selected, show their status too
                if (activeTargetProfile != null) {
                    val targetNeeds = activeTargetProfile.needsPokemon(variant.variantId)
                    Text(
                        text = "${activeTargetProfile.username}: ${if (targetNeeds) "Needed" else "Not Needed"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (targetNeeds) NeededGreen else Color(0xFFB91C1C)
                    )
                }
            }
        }
    }
}
