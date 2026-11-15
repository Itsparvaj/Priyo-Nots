package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AppSettingsEntity
import com.example.ui.viewmodel.PriyoNotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenBackupHistory: () -> Unit,
    onOpenUpdateScreen: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SettingsSectionHeader("Appearance")
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppSettingsEntity.THEME_SYSTEM to "Follow System",
                        AppSettingsEntity.THEME_LIGHT to "Light",
                        AppSettingsEntity.THEME_DARK to "Dark"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_chip_$mode")
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Default Note Sorting", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppSettingsEntity.SORT_MODIFIED_DESC to "Modified",
                        AppSettingsEntity.SORT_CREATED_DESC to "Created",
                        AppSettingsEntity.SORT_TITLE_ASC to "A–Z",
                        AppSettingsEntity.SORT_TITLE_DESC to "Z–A"
                    ).forEach { (order, label) ->
                        FilterChip(
                            selected = settings.defaultSortOrder == order,
                            onClick = { viewModel.setDefaultSortOrder(order) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Notifications & Reminders")
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Enable Notifications",
                subtitle = "Allow local reminder notifications",
                checked = settings.notificationsEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(notificationsEnabled = enabled) }
                }
            )
            SettingsNavRow(
                icon = Icons.Outlined.Notifications,
                title = "Reminder Settings",
                subtitle = "Sound, vibration, and active scheduled reminders",
                onClick = onOpenReminderSettings,
                testTag = "settings_reminders_row"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Backup, Export & Import")
            SettingsNavRow(
                icon = Icons.Outlined.Backup,
                title = "Backup & Restore",
                subtitle = settings.lastBackupStatus,
                onClick = onOpenBackupRestore,
                testTag = "settings_backup_row"
            )
            SettingsNavRow(
                icon = Icons.Outlined.History,
                title = "Backup History",
                subtitle = "View saved backup sizes and share files",
                onClick = onOpenBackupHistory,
                testTag = "settings_history_row"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Privacy")
            SettingsSwitchRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Hide Note Previews on Lists",
                subtitle = "Show titles only in Home and Search cards",
                checked = settings.privacyModeHidePreviews,
                onCheckedChange = { hide ->
                    viewModel.updateSettings { it.copy(privacyModeHidePreviews = hide) }
                }
            )
            SettingsNavRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "Offline-first storage, zero tracking",
                onClick = onOpenPrivacyPolicy
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Updates & App Info")
            SettingsSwitchRow(
                icon = Icons.Outlined.SystemUpdate,
                title = "Check Updates on Launch",
                subtitle = "Checks your configured HTTPS JSON update endpoint",
                checked = settings.updateCheckEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(updateCheckEnabled = enabled) }
                }
            )
            SettingsNavRow(
                icon = Icons.Outlined.SystemUpdate,
                title = "Check for Updates",
                subtitle = if (settings.updateEndpointUrl.isBlank()) "Configure HTTPS update URL" else settings.updateEndpointUrl,
                onClick = onOpenUpdateScreen,
                testTag = "settings_updates_row"
            )
            SettingsNavRow(
                icon = Icons.Outlined.Info,
                title = "About Priyo Nots",
                subtitle = "By Mohammad Parvaj · Version, Legal & Contact",
                onClick = onOpenAbout,
                testTag = "settings_about_row"
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
