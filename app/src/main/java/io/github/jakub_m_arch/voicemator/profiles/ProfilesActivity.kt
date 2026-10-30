package io.github.jakub_m_arch.voicemator.profiles

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.history.HistoryDatabase
import io.github.jakub_m_arch.voicemator.text.DictionaryActivity
import io.github.jakub_m_arch.voicemator.ui.settings.ChoiceDialog
import io.github.jakub_m_arch.voicemator.ui.settings.ConfirmDialog
import io.github.jakub_m_arch.voicemator.ui.settings.SettingsRow
import io.github.jakub_m_arch.voicemator.ui.settings.SettingsSection
import io.github.jakub_m_arch.voicemator.ui.settings.SettingsSwitchRow
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/** Lists the app profiles and edits them. */
class ProfilesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoicematorTheme {
                ProfilesScreen(onClose = ::finish)
            }
        }
    }
}

/** An app that can be given a profile. */
data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap)

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = HistoryDatabase.get(application)
    private val dao = database.profiles()
    private val mutableInstalledApps = MutableStateFlow<List<InstalledApp>?>(null)
    private var isLoadingApps = false

    val profiles: StateFlow<List<Profile>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Package name to profile ID. */
    val appProfiles: StateFlow<Map<String, Long>> = dao.observeApps()
        .map { apps -> apps.associate { it.packageName to it.profileId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /** Apps with a launcher icon, sorted by name; null until loaded. */
    val installedApps: StateFlow<List<InstalledApp>?> = mutableInstalledApps.asStateFlow()

    fun profile(id: Long): Flow<Profile?> = dao.observe(id)

    fun dictionarySize(profileId: Long): Flow<Int> = database.dictionary().observe(profileId).map { it.size }

    fun loadInstalledApps() {
        if (mutableInstalledApps.value != null || isLoadingApps) return
        isLoadingApps = true
        viewModelScope.launch(Dispatchers.IO) { mutableInstalledApps.value = queryInstalledApps() }
    }

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(dao.insert(Profile(name = name.trim()))) }
    }

    fun update(profile: Profile) {
        viewModelScope.launch { dao.update(profile) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun setAssigned(profileId: Long, packageName: String, assigned: Boolean) {
        viewModelScope.launch {
            if (assigned) dao.assign(ProfileApp(packageName, profileId)) else dao.unassign(packageName)
        }
    }

    private fun queryInstalledApps(): List<InstalledApp> {
        val context = getApplication<Application>()
        val packageManager = context.packageManager
        val iconSize = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val collator = Collator.getInstance()
        return packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    icon = info.loadIcon(packageManager).toBitmap(iconSize, iconSize).asImageBitmap(),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ICON_SIZE_DP = 40
    }
}

@Composable
private fun ProfilesScreen(onClose: () -> Unit, viewModel: ProfilesViewModel = viewModel()) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    when (val id = editingId) {
        null -> ProfileList(onClose = onClose, onOpen = { editingId = it }, viewModel = viewModel)
        else -> {
            BackHandler { editingId = null }
            ProfileEditor(id = id, onClose = { editingId = null }, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileList(onClose: () -> Unit, onOpen: (Long) -> Unit, viewModel: ProfilesViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val appProfiles by viewModel.appProfiles.collectAsStateWithLifecycle()
    var isCreating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.button_back)) } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.profiles_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.profiles_requirement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (profiles.isNotEmpty()) {
                SettingsSection(stringResource(R.string.profiles_title)) {
                    profiles.forEach { profile ->
                        SettingsRow(
                            icon = Icons.Outlined.Apps,
                            title = profile.name,
                            summary = profileSummary(profile, appCount = appProfiles.values.count { it == profile.id }),
                            onClick = { onOpen(profile.id) },
                        )
                    }
                }
            }
            Button(onClick = { isCreating = true }) { Text(stringResource(R.string.profiles_new)) }
        }
    }

    if (isCreating) {
        NameDialog(
            onConfirm = { name -> viewModel.create(name, onCreated = onOpen) },
            onDismiss = { isCreating = false },
        )
    }
}

@Composable
private fun profileSummary(profile: Profile, appCount: Int): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.profile_apps, appCount, appCount))
        if (!profile.capitalizeFirst) add(stringResource(R.string.profile_summary_small_letter))
        if (!profile.endWithPeriod) add(stringResource(R.string.profile_summary_no_period))
        when (profile.finalPass) {
            true -> add(stringResource(R.string.profile_summary_final_pass_on))
            false -> add(stringResource(R.string.profile_summary_final_pass_off))
            null -> Unit
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun NameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profiles_name)) },
                placeholder = { Text(stringResource(R.string.profiles_name_example)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.profiles_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(id: Long, onClose: () -> Unit, viewModel: ProfilesViewModel) {
    val context = LocalContext.current
    val profile by remember(id) { viewModel.profile(id) }.collectAsStateWithLifecycle(initialValue = null)
    val dictionarySize by remember(id) { viewModel.dictionarySize(id) }.collectAsStateWithLifecycle(initialValue = 0)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val appProfiles by viewModel.appProfiles.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var editedName by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var isPickingApps by rememberSaveable { mutableStateOf(false) }
    var isChoosingFinalPass by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val current = profile
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name.orEmpty()) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.button_back)) } },
                actions = {
                    TextButton(onClick = { isConfirmingDelete = true }) {
                        Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { contentPadding ->
        if (current == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            OutlinedTextField(
                value = editedName ?: current.name,
                onValueChange = { name ->
                    editedName = name
                    if (name.isNotBlank()) viewModel.update(current.copy(name = name.trim()))
                },
                label = { Text(stringResource(R.string.profiles_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val assigned = appProfiles.filterValues { it == id }.keys.sorted()
                .map { packageName -> packageName to installedApps?.find { it.packageName == packageName } }
            SettingsSection(stringResource(R.string.profile_section_apps)) {
                if (assigned.isEmpty()) {
                    Text(
                        stringResource(R.string.profile_no_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                assigned.forEach { (packageName, app) ->
                    AppRow(
                        app = app,
                        packageName = packageName,
                        trailing = {
                            IconButton(onClick = { viewModel.setAssigned(id, packageName, assigned = false) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.profile_remove_app))
                            }
                        },
                    )
                }
                SettingsRow(
                    icon = Icons.Outlined.Add,
                    title = stringResource(R.string.profile_add_apps),
                    onClick = { isPickingApps = true },
                )
            }

            SettingsSection(stringResource(R.string.profile_section_text)) {
                SettingsSwitchRow(
                    icon = Icons.Outlined.TextFields,
                    title = stringResource(R.string.profile_capitalize_title),
                    summary = stringResource(R.string.profile_capitalize_summary),
                    checked = current.capitalizeFirst,
                    onCheckedChange = { viewModel.update(current.copy(capitalizeFirst = it)) },
                )
                SettingsSwitchRow(
                    icon = Icons.Outlined.FiberManualRecord,
                    title = stringResource(R.string.profile_period_title),
                    summary = stringResource(R.string.profile_period_summary),
                    checked = current.endWithPeriod,
                    onCheckedChange = { viewModel.update(current.copy(endWithPeriod = it)) },
                )
            }

            SettingsSection(stringResource(R.string.transcription_settings_title)) {
                SettingsRow(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.row_final_pass_title),
                    summary = finalPassLabel(current.finalPass).first,
                    onClick = { isChoosingFinalPass = true },
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = stringResource(R.string.dictionary_title),
                    summary = if (dictionarySize == 0) {
                        stringResource(R.string.profile_dictionary_empty)
                    } else {
                        pluralStringResource(R.plurals.row_dictionary_entries, dictionarySize, dictionarySize)
                    },
                    onClick = { context.startActivity(DictionaryActivity.intent(context, profileId = id)) },
                )
            }
        }

        if (isPickingApps) {
            AppPicker(
                apps = installedApps,
                profileId = id,
                appProfiles = appProfiles,
                profileNames = profiles.associate { it.id to it.name },
                onToggle = { packageName, assign -> viewModel.setAssigned(id, packageName, assign) },
                onDismiss = { isPickingApps = false },
            )
        }
        if (isChoosingFinalPass) {
            ChoiceDialog(
                title = stringResource(R.string.row_final_pass_title),
                options = listOf(null, true, false),
                selected = current.finalPass,
                label = { finalPassLabel(it) },
                onSelect = { viewModel.update(current.copy(finalPass = it)) },
                onDismiss = { isChoosingFinalPass = false },
            )
        }
        if (isConfirmingDelete) {
            ConfirmDialog(
                title = stringResource(R.string.profile_delete_confirm_title),
                body = stringResource(R.string.profile_delete_confirm_body),
                confirmLabel = stringResource(R.string.history_delete),
                onConfirm = {
                    viewModel.delete(id)
                    onClose()
                },
                onDismiss = { isConfirmingDelete = false },
            )
        }
    }
}

@Composable
private fun finalPassLabel(finalPass: Boolean?): Pair<String, String> = when (finalPass) {
    null -> stringResource(R.string.profile_final_pass_default) to stringResource(R.string.profile_final_pass_default_body)
    true -> stringResource(R.string.summary_on) to stringResource(R.string.profile_final_pass_on_body)
    false -> stringResource(R.string.summary_off) to stringResource(R.string.profile_final_pass_off_body)
}

/** An app with its icon; apps that are no longer installed show their package name. */
@Composable
private fun AppRow(
    app: InstalledApp?,
    packageName: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(app?.label ?: packageName) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                leading?.invoke()
                if (app != null) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                } else {
                    Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
    )
}

@Composable
private fun AppPicker(
    apps: List<InstalledApp>?,
    profileId: Long,
    appProfiles: Map<String, Long>,
    profileNames: Map<Long, String>,
    onToggle: (packageName: String, assign: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_add_apps)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.profile_search_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            val owner = appProfiles[app.packageName]
                            val isChecked = owner == profileId
                            AppRow(
                                app = app,
                                packageName = app.packageName,
                                supporting = owner?.takeIf { it != profileId }
                                    ?.let { profileNames[it] }
                                    ?.let { stringResource(R.string.profile_app_in_other, it) },
                                leading = { Checkbox(checked = isChecked, onCheckedChange = null) },
                                modifier = Modifier.clickable { onToggle(app.packageName, !isChecked) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.status_done)) } },
    )
}
