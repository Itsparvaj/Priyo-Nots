package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NoteCardItem
import com.example.ui.components.PriyoEmptyState
import com.example.ui.viewmodel.PriyoNotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialCategoryId: Long?,
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.searchCategoryId.collectAsStateWithLifecycle()
    val includeArchived by viewModel.includeArchivedInSearch.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(initialCategoryId) {
        if (initialCategoryId != null) {
            viewModel.updateSearchCategory(initialCategoryId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("search_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Search Notes") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Type keywords, checklist items, or titles...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = includeArchived,
                        onClick = { viewModel.setIncludeArchivedInSearch(!includeArchived) },
                        label = { Text("Include Archived") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { viewModel.updateSearchCategory(null) },
                        label = { Text("All Categories") }
                    )
                }
                items(categories, key = { it.category.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.category.id,
                        onClick = {
                            viewModel.updateSearchCategory(
                                if (selectedCategoryId == cat.category.id) null else cat.category.id
                            )
                        },
                        label = { Text(cat.category.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "${results.size} result${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (results.isEmpty()) {
                PriyoEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matching notes",
                    subtitle = "Try another keyword or clear the category filter.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.note.id }) { item ->
                        NoteCardItem(
                            item = item,
                            hidePreviews = settings.privacyModeHidePreviews,
                            onClick = { onOpenNote(item.note.id) }
                        )
                    }
                }
            }
        }
    }
}
