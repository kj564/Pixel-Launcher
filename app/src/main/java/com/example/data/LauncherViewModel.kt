package com.example.data

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable? = null,
    val isSystem: Boolean = false
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LauncherDatabase.getDatabase(application)
    private val repository = LauncherRepository(db.launcherDao())

    // All applications (real + system queried)
    private val _rawInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    // Database state flows
    val dbSettings: StateFlow<Map<String, String>> = repository.settings
        .map { list -> list.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Dynamically apply user's customized labels from the database
    val rawInstalledApps: StateFlow<List<InstalledApp>> = combine(
        _rawInstalledApps,
        dbSettings
    ) { apps, settings ->
        apps.map { app ->
            val customLabel = settings["custom_label_${app.packageName}"]
            if (customLabel != null && customLabel.trim().isNotEmpty()) {
                app.copy(label = customLabel)
            } else {
                app
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dbPinnedApps: StateFlow<List<PinnedApp>> = repository.pinnedApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dbHiddenApps: StateFlow<Set<String>> = repository.hiddenApps
        .map { list -> list.map { it.packageName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Combined live UI configurations
    val themeAccent: StateFlow<String> = dbSettings
        .map { it["theme_accent_color"] ?: "Blue" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Blue")

    val themedIconsEnabled: StateFlow<Boolean> = dbSettings
        .map { it["themed_icons_enabled"]?.toBoolean() ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val gridColumnSize: StateFlow<Int> = dbSettings
        .map { it["grid_columns"]?.toIntOrNull() ?: 4 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 4)

    val support480pEnabled: StateFlow<Boolean> = dbSettings
        .map { it["support_480p"]?.toBoolean() ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val olauncherModeEnabled: StateFlow<Boolean> = dbSettings
        .map { it["olauncher_mode_enabled"]?.toBoolean() ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val wallpaperIndex: StateFlow<Int> = dbSettings
        .map { it["wallpaper_index"]?.toIntOrNull() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val olauncherShowIcons: StateFlow<Boolean> = dbSettings
        .map { it["olauncher_show_icons"]?.toBoolean() ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Derived layout lists
    val filteredAppsForDrawer: StateFlow<List<InstalledApp>> = combine(
        rawInstalledApps,
        _searchQuery,
        dbHiddenApps
    ) { apps, query, hidden ->
        apps.filter { app ->
            !hidden.contains(app.packageName) &&
            (query.isEmpty() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
        }.sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeGridApps: StateFlow<List<InstalledApp>> = combine(
        rawInstalledApps,
        dbPinnedApps
    ) { apps, pinned ->
        val pinnedHome = pinned.filter { !it.isDock }.associateBy { it.packageName }
        apps.filter { pinnedHome.containsKey(it.packageName) }
            .sortedBy { pinnedHome[it.packageName]?.position ?: 99 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dockApps: StateFlow<List<InstalledApp>> = combine(
        rawInstalledApps,
        dbPinnedApps
    ) { apps, pinned ->
        val pinnedDock = pinned.filter { it.isDock }.associateBy { it.packageName }
        apps.filter { pinnedDock.containsKey(it.packageName) }
            .sortedBy { pinnedDock[it.packageName]?.position ?: 99 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active simulated experience screen state
    private val _simulatedAppOpen = MutableStateFlow<InstalledApp?>(null)
    val simulatedAppOpen: StateFlow<InstalledApp?> = _simulatedAppOpen.asStateFlow()

    init {
        reloadApplications()
        viewModelScope.launch {
            // Wait for Room database flow's first actual result emission to check if initialized
            repository.pinnedApps.first().let { list ->
                if (list.isEmpty()) {
                    initializeDefaultPins()
                }
            }
        }
    }

    fun reloadApplications() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val apps = try {
                pm.queryIntentActivities(mainIntent, 0).map { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val activityName = resolveInfo.activityInfo.name
                    val label = resolveInfo.loadLabel(pm).toString()
                    val icon = try {
                        resolveInfo.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                    InstalledApp(packageName, activityName, label, icon, isSystem = false)
                }.filter { it.packageName != getApplication<Application>().packageName }
            } catch (e: Exception) {
                Log.e("LauncherVM", "Error querying system apps", e)
                emptyList()
            }

            _rawInstalledApps.value = apps
        }
    }

    private suspend fun initializeDefaultPins() {
        // Dynamically find and pin existing real packages on the device to populate our customized database
        val pm = getApplication<Application>().packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val realApps = try {
            pm.queryIntentActivities(mainIntent, 0).map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                packageName to label
            }.filter { it.first != getApplication<Application>().packageName }
        } catch (e: Exception) {
            emptyList()
        }

        if (realApps.isEmpty()) return

        // Signature Google Pixel / Olauncher app categories mapping based on actual package identifiers
        val dialerApp = realApps.find { it.first.contains("dialer", ignoreCase = true) || it.first.contains("phone", ignoreCase = true) || it.second.contains("Telepon", ignoreCase = true) || it.second.contains("Phone", ignoreCase = true) }
        val smsApp = realApps.find { it.first.contains("messaging", ignoreCase = true) || it.first.contains("mms", ignoreCase = true) || it.second.contains("Pesan", ignoreCase = true) || it.second.contains("Message", ignoreCase = true) }
        val browserApp = realApps.find { it.first.contains("chrome", ignoreCase = true) || it.first.contains("browser", ignoreCase = true) || it.second.contains("Browser", ignoreCase = true) || it.second.contains("Chrome", ignoreCase = true) }
        val cameraApp = realApps.find { it.first.contains("camera", ignoreCase = true) || it.second.contains("Kamera", ignoreCase = true) || it.second.contains("Camera", ignoreCase = true) }
        val playStoreApp = realApps.find { it.first.contains("vending", ignoreCase = true) || it.second.contains("Play Store", ignoreCase = true) || it.second.contains("Google Play", ignoreCase = true) }

        val dockSelected = mutableListOf<Pair<String, String>>()
        dialerApp?.let { dockSelected.add(it) }
        smsApp?.let { dockSelected.add(it) }
        browserApp?.let { dockSelected.add(it) }
        cameraApp?.let { dockSelected.add(it) }
        playStoreApp?.let { dockSelected.add(it) }

        // Backfill up to 5 dock apps safely with other installed apps
        for (app in realApps) {
            if (dockSelected.size >= 5) break
            if (!dockSelected.any { it.first == app.first }) {
                dockSelected.add(app)
            }
        }

        dockSelected.forEachIndexed { index, pair ->
            repository.pinApp(pair.first, pair.second, index, isDock = true)
        }

        val homeSelected = mutableListOf<Pair<String, String>>()
        val mapsApp = realApps.find { it.first.contains("maps", ignoreCase = true) || it.second.contains("Maps", ignoreCase = true) || it.second.contains("Map", ignoreCase = true) }
        val mailApp = realApps.find { it.first.contains("gm", ignoreCase = true) || it.first.contains("mail", ignoreCase = true) || it.second.contains("Gmail", ignoreCase = true) }
        val ytApp = realApps.find { it.first.contains("youtube", ignoreCase = true) || it.second.contains("YouTube", ignoreCase = true) }
        val settingsApp = realApps.find { it.first.contains("settings", ignoreCase = true) || it.second.contains("Setelan", ignoreCase = true) || it.second.contains("Settings", ignoreCase = true) }

        mapsApp?.let { if (!dockSelected.any { d -> d.first == it.first }) homeSelected.add(it) }
        mailApp?.let { if (!dockSelected.any { d -> d.first == it.first }) homeSelected.add(it) }
        ytApp?.let { if (!dockSelected.any { d -> d.first == it.first }) homeSelected.add(it) }
        settingsApp?.let { if (!dockSelected.any { d -> d.first == it.first }) homeSelected.add(it) }

        // Backfill up to 4 home screen apps safely
        for (app in realApps) {
            if (homeSelected.size >= 4) break
            if (!dockSelected.any { it.first == app.first } && !homeSelected.any { it.first == app.first }) {
                homeSelected.add(app)
            }
        }

        homeSelected.forEachIndexed { index, pair ->
            repository.pinApp(pair.first, pair.second, index, isDock = false)
        }
    }

    // Search query management
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Setting modifiers
    fun changeThemeAccent(accentName: String) {
        viewModelScope.launch {
            repository.setSetting("theme_accent_color", accentName)
        }
    }

    fun toggleThemedIcons(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("themed_icons_enabled", enabled.toString())
        }
    }

    fun changeGridColumns(columns: Int) {
        viewModelScope.launch {
            repository.setSetting("grid_columns", columns.toString())
        }
    }

    fun toggle480pOptimization(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("support_480p", enabled.toString())
        }
    }

    fun toggleOlauncherMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("olauncher_mode_enabled", enabled.toString())
        }
    }

    fun changeWallpaperIndex(index: Int) {
        viewModelScope.launch {
            repository.setSetting("wallpaper_index", index.toString())
            // Dynamic theme harmonization based on wallpaper colors (Material You DNA)
            val accentColorName = when (index) {
                0 -> "Lavender"
                1 -> "Blue"
                2 -> "Mint"
                3 -> "Coral"
                4 -> "Lavender"
                else -> "Charcoal"
            }
            repository.setSetting("theme_accent_color", accentColorName)
        }
    }

    fun toggleOlauncherShowIcons(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("olauncher_show_icons", enabled.toString())
        }
    }

    // App Pin / Hide manipulation
    fun toggleAppPin(app: InstalledApp, isDock: Boolean) {
        viewModelScope.launch {
            val isPinned = dbPinnedApps.value.any { it.packageName == app.packageName && it.isDock == isDock }
            if (isPinned) {
                repository.unpinAppByPackage(app.packageName, isDock)
            } else {
                // Find next free position
                val currentMaxPos = dbPinnedApps.value.filter { it.isDock == isDock }.maxOfOrNull { it.position } ?: -1
                repository.pinApp(app.packageName, app.label, currentMaxPos + 1, isDock)
            }
        }
    }

    fun unpinAppByPkg(packageName: String) {
        viewModelScope.launch {
            repository.unpinAppByPackage(packageName)
        }
    }

    fun toggleHideApp(packageName: String) {
        viewModelScope.launch {
            val isHidden = dbHiddenApps.value.contains(packageName)
            if (isHidden) {
                repository.unhideApp(packageName)
            } else {
                repository.hideApp(packageName)
                repository.unpinAppByPackage(packageName) // Unpin if hidden
            }
        }
    }

    // App launch handling
    fun launchApplication(app: InstalledApp, onSystemLaunchError: (String) -> Unit) {
        if (app.isSystem) {
            // Active simulation overlay
            _simulatedAppOpen.value = app
        } else {
            // Real device launcher launch
            val pm = getApplication<Application>().packageManager
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    getApplication<Application>().startActivity(intent)
                } catch (e: Exception) {
                    // Fail over to simulated overlay if start fails for any sandboxed reasons
                    _simulatedAppOpen.value = app
                }
            } else {
                // Fail over to simulation
                _simulatedAppOpen.value = app
            }
        }
    }

    fun closeSimulatedApp() {
        _simulatedAppOpen.value = null
    }

    fun launchGoogleWebSearch(query: String) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val url = "https://www.google.com/search?q=" + Uri.encode(q)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                // Web fallback
                _simulatedAppOpen.value = InstalledApp(
                    packageName = "com.android.chrome",
                    activityName = "mock.Chrome",
                    label = "Chrome Search",
                    icon = null,
                    isSystem = true
                )
            }
        }
    }

    fun renameApplication(packageName: String, newLabel: String) {
        viewModelScope.launch {
            if (newLabel.trim().isEmpty()) {
                repository.deleteSetting("custom_label_$packageName")
            } else {
                repository.setSetting("custom_label_$packageName", newLabel.trim())
            }
        }
    }

    fun openAppInfo(packageName: String, onError: (String) -> Unit) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            onError("Gagal membuka info aplikasi: ${e.message}")
        }
    }

    fun uninstallApplication(packageName: String, onError: (String) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            onError("Gagal uninstal aplikasi: ${e.message}")
        }
    }
}
