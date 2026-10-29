package io.github.jakub_m_arch.voicemator.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.jakub_m_arch.voicemator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onClose: () -> Unit) {
    var openAsset by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = openAsset != null) { openAsset = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    TextButton(onClick = { if (openAsset != null) openAsset = null else onClose() }) {
                        Text(stringResource(R.string.button_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val asset = openAsset
        if (asset == null) {
            ComponentList(
                modifier = Modifier.padding(innerPadding),
                onOpenLicense = { openAsset = it },
            )
        } else {
            LicenseText(asset = asset, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun ComponentList(modifier: Modifier, onOpenLicense: (String) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.licenses_app_section)) }
        item {
            ComponentRow(
                component = ThirdPartyComponent(
                    name = stringResource(R.string.app_name),
                    version = null,
                    copyright = stringResource(R.string.licenses_app_copyright),
                    license = "Apache-2.0",
                    url = stringResource(R.string.licenses_app_url),
                    licenseAsset = "apache-2.0.txt",
                ),
                onOpenLicense = onOpenLicense,
            )
        }
        item { SectionHeader(stringResource(R.string.licenses_bundled_section)) }
        items(ThirdPartyNotices.bundled) { ComponentRow(it, onOpenLicense) }
        item { SectionHeader(stringResource(R.string.licenses_downloaded_section)) }
        items(ThirdPartyNotices.downloaded) { ComponentRow(it, onOpenLicense) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ComponentRow(component: ThirdPartyComponent, onOpenLicense: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val licenseAsset = component.licenseAsset
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (licenseAsset != null) {
                    onOpenLicense(licenseAsset)
                } else {
                    uriHandler.openUri(ThirdPartyNotices.CC_BY_4_URL)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val title = listOfNotNull(component.name, component.version).joinToString(" ")
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(component.copyright, style = MaterialTheme.typography.bodySmall)
        Text(
            "${component.license} · ${component.url}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        component.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun LicenseText(asset: String, modifier: Modifier) {
    val context = LocalContext.current
    val text = remember(asset) {
        context.assets.open("$LICENSES_DIR/$asset").bufferedReader().use { it.readText() }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
    )
}

private const val LICENSES_DIR = "licenses"
