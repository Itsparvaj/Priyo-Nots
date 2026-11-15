package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.ui.viewmodel.PriyoNotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenContactUs: () -> Unit,
    onOpenUpdates: () -> Unit
) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("about_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("About Priyo Nots") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.priyo_nots_logo),
                contentDescription = "Priyo Nots Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Text(
                text = "Priyo Nots",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Owner: Mohammad Parvaj",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Text(
                text = "A lightweight, offline-first personal notes app powered by local Room SQLite storage, an HTML5/CSS3/JS rich-text editor, reminders, and encrypted JSON backups.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    AboutActionRow(Icons.Outlined.PrivacyTip, "Privacy Policy", onOpenPrivacyPolicy)
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.Gavel, "Terms & Conditions", onOpenTerms)
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.Email, "Contact Us", onOpenContactUs)
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.SystemUpdate, "Check for Updates", onOpenUpdates)
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.Share, "Share App") {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out Priyo Nots — an offline-first personal notes app by Mohammad Parvaj."
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Priyo Nots"))
                    }
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.StarOutline, "Rate App") {
                        val marketIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${context.packageName}")
                        )
                        runCatching { context.startActivity(marketIntent) }
                            .onFailure {
                                val webIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                )
                                runCatching { context.startActivity(webIntent) }
                            }
                    }
                    HorizontalDivider()
                    AboutActionRow(Icons.Outlined.Description, "Open Source Licenses") {
                        showLicenses = true
                    }
                }
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open Source Licenses") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• AndroidX Jetpack Compose, Material3, Navigation & Lifecycle — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• AndroidX Room SQLite Persistence Library — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• Kotlin & Kotlinx Coroutines — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• Google Fonts (Plus Jakarta Sans, Inter, JetBrains Mono) — SIL Open Font License 1.1", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("privacy_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Privacy Policy") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Priyo Nots Privacy Commitment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Owner: Mohammad Parvaj", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "1. Local-First Storage: All notes, categories, reminders, and settings are stored privately on your device inside a local Room SQLite database. No account registration or sign-in is required.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "2. Zero Tracking or Analytics: Priyo Nots contains no third-party analytics SDKs, advertising trackers, or telemetry collectors.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "3. Backups & Encryption: Manual and automatic backups are saved only to locations you choose (or app-private storage). You may optionally encrypt backups using AES-256-GCM with a password you control. Your password is never transmitted or stored.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "4. Optional Cloud Sync & App Updates: Internet access is used only when you connect your own Google Drive account for personal cloud backups or check for official Priyo Nots app updates.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "5. Permissions: Notification permission is requested only to deliver note reminders you schedule. Storage Access Framework (SAF) file/folder pickers are used without broad storage permissions.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("terms_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Terms & Conditions") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Terms & Conditions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Application: Priyo Nots · Owner: Mohammad Parvaj", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "1. Personal Use: Priyo Nots is provided as a personal offline-first notes, checklist, and reminder application.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "2. Data Responsibility: Because notes are stored locally on your device, you are responsible for exporting external backups before uninstalling the app, resetting your device, or changing phones. App-private backups do not survive uninstallation.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "3. Encrypted Backups: If you protect a backup with a password, keep your password safe. Encrypted backups cannot be decrypted without the original password.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "4. Updates & Cloud Backups: App updates are verified by package signature before installation. Google Drive backups stay strictly inside your personal Drive account.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactUsScreen(
    viewModel: PriyoNotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var contactEmail by remember(settings.contactEmail) { mutableStateOf(settings.contactEmail) }
    var contactWebsite by remember(settings.contactWebsite) { mutableStateOf(settings.contactWebsite) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("contact_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Contact Us") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("App Owner", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Mohammad Parvaj", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Configure support contact details below for your deployment or send feedback directly via your email app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        label = { Text("Support Email (Configurable)") },
                        placeholder = { Text("support@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactWebsite,
                        onValueChange = { contactWebsite = it },
                        label = { Text("Website URL (Configurable)") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.updateSettings {
                                it.copy(
                                    contactEmail = contactEmail.trim(),
                                    contactWebsite = contactWebsite.trim()
                                )
                            }
                            viewModel.emitMessage("Contact details saved")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Contact Configuration")
                    }
                    if (contactWebsite.trim().startsWith("https://")) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contactWebsite.trim())))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Language, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Visit Website")
                        }
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val targetEmail = contactEmail.trim()
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$targetEmail")
                                putExtra(Intent.EXTRA_SUBJECT, subject.ifBlank { "Priyo Nots Feedback" })
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "$message\n\n---\nApp: Priyo Nots v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                                )
                            }
                            runCatching { context.startActivity(Intent.createChooser(emailIntent, "Send Feedback")) }
                                .onFailure { viewModel.emitMessage("No email app found on this device") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Email App")
                    }
                }
            }
        }
    }
}
