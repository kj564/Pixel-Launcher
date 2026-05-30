package com.example.ui

import androidx.activity.compose.BackHandler
import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.data.InstalledApp
import com.example.data.LauncherViewModel
import com.example.data.PinnedApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Dynamic Style Theme Definitions
enum class KeyboardMode { ALPHABET, DIALER }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PixelLauncherScreen(
    viewModel: LauncherViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect States from Room-backed ViewModel
    val rawApps by viewModel.rawInstalledApps.collectAsState()
    val drawerApps by viewModel.filteredAppsForDrawer.collectAsState()
    val homeApps by viewModel.homeGridApps.collectAsState()
    val dockApps by viewModel.dockApps.collectAsState()
    
    val accentName by viewModel.themeAccent.collectAsState()
    val themedIconsEnabled by viewModel.themedIconsEnabled.collectAsState()
    val gridColumns by viewModel.gridColumnSize.collectAsState()
    val support480p by viewModel.support480pEnabled.collectAsState()
    val olauncherModeEnabled by viewModel.olauncherModeEnabled.collectAsState()
    val olauncherShowIcons by viewModel.olauncherShowIcons.collectAsState()
    val wallpaperIdx by viewModel.wallpaperIndex.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val simulatedAppOpen by viewModel.simulatedAppOpen.collectAsState()

    // UI Interactive States
    val density = LocalDensity.current
    var isDrawerOpen by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(1000f) }
    var dragStartOffsetY by remember { mutableStateOf(0f) }
    var activeLongClickApp by remember { mutableStateOf<InstalledApp?>(null) }
    var showWallpaperStyleDialog by remember { mutableStateOf(false) }
    var showWeatherForecast by remember { mutableStateOf(false) }
    var showAssistantSheet by remember { mutableStateOf(false) }
    var assistantQuery by remember { mutableStateOf("") }

    // Back gesture handling: prioritize simulated apps, then the app drawer, then sheets/dialogs
    BackHandler(enabled = simulatedAppOpen != null || isDrawerOpen || showAssistantSheet || showWeatherForecast || showWallpaperStyleDialog) {
        when {
            simulatedAppOpen != null -> {
                viewModel.closeSimulatedApp()
            }
            isDrawerOpen -> {
                isDrawerOpen = false
            }
            showAssistantSheet -> {
                showAssistantSheet = false
                assistantQuery = ""
            }
            showWeatherForecast -> {
                showWeatherForecast = false
            }
            showWallpaperStyleDialog -> {
                showWallpaperStyleDialog = false
            }
        }
    }

    // Real system clock tick (optimized for Olauncher - updates every 15s)
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(15000)
        }
    }

    // Material You dynamic color configurations
    val themeColorTokens = remember(accentName) {
        when (accentName) {
            "Blue" -> ColorToken(Color(0xFF1A73E8), Color(0xFFD2E3FC), Color(0xFFE8F0FE))
            "Mint" -> ColorToken(Color(0xFF0F9D58), Color(0xFFCEEAD6), Color(0xFFE6F4EA))
            "Coral" -> ColorToken(Color(0xFFEA4335), Color(0xFFFAD2CF), Color(0xFFFCE8E6))
            "Lavender" -> ColorToken(Color(0xFF381E72), Color(0xFFD0BCFF), Color(0xFFEADDFF))
            "Charcoal" -> ColorToken(Color(0xFF3C4043), Color(0xFFE8EAED), Color(0xFFF1F3F4))
            else -> ColorToken(Color(0xFF381E72), Color(0xFFD0BCFF), Color(0xFFEADDFF))
        }
    }

    // Adaptive padding metrics based on resolution choice
    val appIconSize = if (support480p) 44.dp else 52.dp
    val textLabelSize = if (support480p) 10.sp else 12.sp
    val gridSpacing = if (support480p) 6.dp else 12.dp

    // Is the current wallpaper background light (index 0 is our Light Material Polish theme)
    val isLightBg = (wallpaperIdx == 0)
    val desktopTextColor = if (isLightBg) Color(0xFF1D1B20) else Color.White
    val desktopSubTextColor = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.8f)

    // Vector procedural gradient backgrounds resembling Google Pixel styles
    val activeWallpaperBrush = remember(wallpaperIdx) {
        when (wallpaperIdx) {
            0 -> Brush.verticalGradient(listOf(Color(0xFFF7F2FA), Color(0xFFFFFFFF), Color(0xFFEADDFF)))
            1 -> Brush.verticalGradient(listOf(Color(0xFF08143A), Color(0xFF0B315E), Color(0xFF166AA5)))
            2 -> Brush.verticalGradient(listOf(Color(0xFF05241C), Color(0xFF0C4D35), Color(0xFF268F63)))
            3 -> Brush.verticalGradient(listOf(Color(0xFF3E1E1E), Color(0xFF6C2525), Color(0xFF9E4130)))
            4 -> Brush.verticalGradient(listOf(Color(0xFF22052D), Color(0xFF4C0E5F), Color(0xFF823D9E)))
            else -> Brush.verticalGradient(listOf(Color(0xFF202124), Color(0xFF3C4043), Color(0xFF5F6368)))
        }
    }

    // Master Screen container
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(activeWallpaperBrush) },
        contentAlignment = Alignment.TopCenter
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val targetOffset = if (isDragging) dragOffsetY else (if (isDrawerOpen) 0f else screenHeightPx)
        val drawerOffset by animateFloatAsState(
            targetValue = targetOffset,
            animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "drawerOffset"
        )

        // --- LAYER 1: HOME DESKTOP ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(screenHeightPx, olauncherModeEnabled, rawApps) {
                    var accumulatedDragX = 0f
                    var accumulatedDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffsetY = drawerOffset
                            dragStartOffsetY = drawerOffset
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            
                            // Horizontal Swipe Detection for Olauncher Mode: Swipe Left (Camera), Swipe Right (Dialer)
                            if (olauncherModeEnabled && !isDrawerOpen && Math.abs(accumulatedDragX) > Math.abs(accumulatedDragY) + 120f) {
                                if (accumulatedDragX < -120f) {
                                    val cameraApp = rawApps.find { it.packageName.contains("camera", ignoreCase = true) || it.label.contains("Kamera", ignoreCase = true) }
                                    if (cameraApp != null) {
                                        viewModel.launchApplication(cameraApp) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                    } else {
                                        Toast.makeText(context, "Membuka Kamera...", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (accumulatedDragX > 120f) {
                                    val phoneApp = rawApps.find { it.packageName.contains("dialer", ignoreCase = true) || it.label.contains("Telepon", ignoreCase = true) }
                                    if (phoneApp != null) {
                                        viewModel.launchApplication(phoneApp) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                    } else {
                                        Toast.makeText(context, "Membuka Telepon...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val totalDragDistance = dragStartOffsetY - dragOffsetY
                                val thresholdPx = with(density) { 40.dp.toPx() }
                                if (isDrawerOpen) {
                                    if (totalDragDistance < -thresholdPx) {
                                        isDrawerOpen = false
                                    }
                                } else {
                                    if (totalDragDistance > thresholdPx) {
                                        isDrawerOpen = true
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragX += dragAmount.x
                            accumulatedDragY += dragAmount.y
                            
                            if (!olauncherModeEnabled || isDrawerOpen) {
                                dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, screenHeightPx)
                            } else {
                                dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, screenHeightPx)
                            }
                        }
                    )
                }
                .graphicsLayer {
                    // Slight alpha & scaling down when drawer sweeps up
                    val progress = ((screenHeightPx - drawerOffset) / screenHeightPx).coerceIn(0f, 1f)
                    alpha = 1f - (progress * 0.7f)
                    scaleX = 1f - (progress * 0.08f)
                    scaleY = 1f - (progress * 0.08f)
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Section 1: At A Glance & System Time (Optimized local scope clock)
            MinimalHeaderClock(
                currentTime = currentTime,
                desktopTextColor = desktopTextColor,
                desktopSubTextColor = desktopSubTextColor,
                isLightBg = isLightBg,
                support480p = support480p,
                olauncherModeEnabled = olauncherModeEnabled,
                onDateClick = { showWeatherForecast = true },
                onWeatherClick = { showWeatherForecast = true },
                atGlanceWeatherText = stringResource(id = com.example.R.string.at_a_glance_weather),
                themeColorTokens = themeColorTokens
            )

            // Section 2: Home Workspace Area (Supports Olauncher Minimalist list & Google Pixel Workspace grid)
            if (olauncherModeEnabled) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showWallpaperStyleDialog = true }
                        )
                        .padding(horizontal = if (support480p) 16.dp else 28.dp)
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    if (homeApps.isEmpty()) {
                        Text(
                            text = "Tekan lama layar untuk pengaturan wallpaper, atau geser ke atas untuk daftar aplikasi.",
                            color = desktopSubTextColor.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else {
                        homeApps.forEach { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { viewModel.launchApplication(app) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
                                        onLongClick = { activeLongClickApp = app }
                                    )
                                    .padding(vertical = if (support480p) 8.dp else 12.dp)
                                    .fillMaxWidth()
                            ) {
                                if (olauncherShowIcons) {
                                    Box(
                                        modifier = Modifier.size(if (support480p) 24.dp else 30.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RawAppIcon(
                                            app = app,
                                            themedEnabled = themedIconsEnabled,
                                            size = if (support480p) 22.dp else 28.dp,
                                            activeThemeColor = themeColorTokens
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(if (support480p) 12.dp else 16.dp))
                                }
                                Text(
                                    text = app.label,
                                    color = desktopTextColor,
                                    fontSize = if (support480p) 18.sp else 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }

                // Transparent bottom spacer to prevent system bar overlap without cluttering the screen with indicators
                Spacer(modifier = Modifier.height(if (support480p) 16.dp else 28.dp))
            } else {
                // Section 2: Home Apps Workspace Grid
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showWallpaperStyleDialog = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (homeApps.isEmpty()) {
                        // Guide overlay for new setup
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Tekan lama layar untuk pengaturan",
                                color = desktopSubTextColor.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Geser ke atas untuk membuka semua aplikasi",
                                color = desktopSubTextColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(gridSpacing / 2),
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                        ) {
                            items(homeApps) { app ->
                                AppIconItem(
                                    app = app,
                                    themedEnabled = themedIconsEnabled,
                                    activeThemeColor = themeColorTokens,
                                    iconSize = appIconSize,
                                    textSize = textLabelSize,
                                    textColor = desktopTextColor,
                                    onClick = { viewModel.launchApplication(app) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
                                    onLongClick = { activeLongClickApp = app }
                                )
                            }
                        }
                    }
                }

            if (!olauncherModeEnabled) {
                // Section 3: Bottom Area (Fixed Dock + Bottom search capsule)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (support480p) 52.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Classic swipe-up arrow handle to open drawer on 480p/keypad devices with 48dp target
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 24.dp)
                            ) {
                                isDrawerOpen = true
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Buka Laci Aplikasi",
                            tint = desktopTextColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(if (support480p) 20.dp else 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (support480p) 1.dp else 4.dp))

                    // Bottom Dock Apps Row (Standard 5 Pixel Icons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = if (support480p) 4.dp else 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dockApps.take(5).forEach { app ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                AppIconItem(
                                    app = app,
                                    themedEnabled = themedIconsEnabled,
                                    activeThemeColor = themeColorTokens,
                                    iconSize = appIconSize,
                                    textSize = textLabelSize,
                                    showLabel = false, // Pure minimalistic pixel style dock
                                    onClick = { viewModel.launchApplication(app) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
                                    onLongClick = { activeLongClickApp = app }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (support480p) 4.dp else 12.dp))

                    // The iconic Google Pixel search bar at the absolute bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pixel_search_bar")
                            .height(if (support480p) 42.dp else 48.dp)
                            .background(if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                            .clickable { showAssistantSheet = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Google Multi-color G logo
                        Text(
                            text = "G",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            ),
                            // Mimics original Google primary scheme
                            color = ColorToken.getGoogleColoredG()
                        )
                        
                        Text(
                            text = "Cari di ponsel Anda...",
                            color = if (isLightBg) Color(0xFF49454F) else Color.Gray,
                            fontSize = if (support480p) 11.sp else 13.sp,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Search Mic",
                                tint = if (isLightBg) Color(0xFF49454F) else ColorToken.getGoogleColoredG(1),
                                modifier = Modifier.size(if (support480p) 18.dp else 22.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Lens Camera",
                                tint = if (isLightBg) Color(0xFF49454F) else ColorToken.getGoogleColoredG(2),
                                modifier = Modifier.size(if (support480p) 18.dp else 22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            }
        }

        // --- LAYER 2: TRANSLATABLE APP DRAWER SHEET ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = drawerOffset
                }
                .background(
                    color = if (isLightBg) Color(0xFFF7F2FA) else ColorToken.getDrawerBackground(isDark = true),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(enabled = false) {} // block click intercepts
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = if (support480p) 8.dp else 16.dp)
            ) {
                // Draggable header area containing physical handle and layout padding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(screenHeightPx) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetY = drawerOffset
                                    dragStartOffsetY = drawerOffset
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val totalDragDistance = dragStartOffsetY - dragOffsetY
                                    val thresholdPx = with(density) { 40.dp.toPx() }
                                    if (isDrawerOpen) {
                                        if (totalDragDistance < -thresholdPx) {
                                            isDrawerOpen = false
                                        }
                                    } else {
                                        if (totalDragDistance > thresholdPx) {
                                            isDrawerOpen = true
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, screenHeightPx)
                                }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Comfort interactive handle pill with large, easy-to-click target area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isDrawerOpen = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 6.dp)
                                .background(if (isLightBg) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(if (support480p) 4.dp else 8.dp))
                }

                // App Drawer top Search bar matching Google Pixel UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (support480p) 42.dp else 48.dp)
                        .background(if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cari",
                        tint = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        textStyle = TextStyle(
                            color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                            fontSize = if (support480p) 13.sp else 15.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotEmpty()) {
                                viewModel.launchGoogleWebSearch(searchQuery)
                            }
                        }),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(id = com.example.R.string.search_apps),
                                    color = if (isLightBg) Color(0xFF49454F).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                                    fontSize = if (support480p) 13.sp else 15.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hapus",
                                tint = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Vertical Apps list of ALL matching items
                if (drawerApps.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = "No result",
                            tint = if (isLightBg) Color(0xFF49454F).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aplikasi tidak ditemukan",
                            color = if (isLightBg) Color(0xFF1D1B20).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.launchGoogleWebSearch(searchQuery) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColorTokens.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = "Cari di Internet", fontSize = 11.sp)
                        }
                    }
                } else {
                    if (olauncherModeEnabled) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 72.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drawerApps) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.launchApplication(app) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                                isDrawerOpen = false
                                            },
                                            onLongClick = { activeLongClickApp = app }
                                        )
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (olauncherShowIcons) {
                                        Box(
                                            modifier = Modifier.size(if (support480p) 24.dp else 28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            RawAppIcon(
                                                app = app,
                                                themedEnabled = themedIconsEnabled,
                                                size = if (support480p) 22.dp else 26.dp,
                                                activeThemeColor = themeColorTokens
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    Text(
                                        text = app.label,
                                        color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                        fontSize = if (support480p) 15.sp else 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(gridSpacing),
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                        ) {
                            items(drawerApps) { app ->
                                AppIconItem(
                                    app = app,
                                    themedEnabled = false, // Drawer is always multi-color on stock Pixel
                                    activeThemeColor = themeColorTokens,
                                    iconSize = appIconSize,
                                    textSize = textLabelSize,
                                    textColor = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    onClick = {
                                        viewModel.launchApplication(app) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                        isDrawerOpen = false
                                    },
                                    onLongClick = { activeLongClickApp = app }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 4: DIALOGS & OVERLAYS ---

        // A. WALLPAPER & STYLE DRAWER CONFIG DIALOG
        if (showWallpaperStyleDialog) {
            Dialog(
                onDismissRequest = { showWallpaperStyleDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(20.dp))
                            .background(if (isLightBg) Color(0xFFFAFAFA) else Color(0xFF202124), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        // Title
                        Text(
                            text = stringResource(id = com.example.R.string.settings_title),
                            color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Modus Minimalis Olauncher Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modus Minimalis Olauncher",
                                    color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Aktifkan tampilan teks super bersih & hemat baterai",
                                    color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = olauncherModeEnabled,
                                onCheckedChange = { viewModel.toggleOlauncherMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = themeColorTokens.primary,
                                    checkedTrackColor = themeColorTokens.secondary
                                )
                            )
                        }

                        // Olauncher Show Icons Toggle
                        if (olauncherModeEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tampilkan Ikon Aplikasi",
                                        color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Sajikan ikon dengan gaya stock di samping nama aplikasi",
                                        color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = olauncherShowIcons,
                                    onCheckedChange = { viewModel.toggleOlauncherShowIcons(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = themeColorTokens.primary,
                                        checkedTrackColor = themeColorTokens.secondary
                                    )
                                )
                            }
                        }

                        // 1. Accent color selection
                        Text(
                            text = stringResource(id = com.example.R.string.theme_color_title),
                            color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(
                                Triple("Blue", Color(0xFF1973E8), "Sian"),
                                Triple("Mint", Color(0xFF0F9D58), "Mint"),
                                Triple("Coral", Color(0xFFEA4335), "Coral"),
                                Triple("Lavender", Color(0xFF381E72), "Ungu"),
                                Triple("Charcoal", Color(0xFF757575), "Abu-abu")
                            ).forEach { (name, color, indLabel) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { viewModel.changeThemeAccent(name) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color, CircleShape)
                                            .border(
                                                width = if (accentName == name) 3.dp else 0.dp,
                                                color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = indLabel, color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 9.sp)
                                }
                            }
                        }

                        // 2. Themed icons toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = com.example.R.string.icon_style_title),
                                    color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Monokromatik serasi Material You",
                                    color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = themedIconsEnabled,
                                onCheckedChange = { viewModel.toggleThemedIcons(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = themeColorTokens.primary,
                                    checkedTrackColor = themeColorTokens.secondary
                                )
                            )
                        }

                        if (!olauncherModeEnabled) {
                            // 3. Grid Columns toggle
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(id = com.example.R.string.grid_size_title),
                                        color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Atur tata letak jumlah kisi layar",
                                        color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.changeGridColumns(4) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (gridColumns == 4) themeColorTokens.primary else (if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.1f)),
                                            contentColor = if (gridColumns == 4) Color.White else (if (isLightBg) Color(0xFF1D1B20) else Color.White)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                    ) {
                                        Text("4x4", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.changeGridColumns(5) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (gridColumns == 5) themeColorTokens.primary else (if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.1f)),
                                            contentColor = if (gridColumns == 5) Color.White else (if (isLightBg) Color(0xFF1D1B20) else Color.White)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                    ) {
                                        Text("5x5", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // 4. 480p low resolution optimization
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = com.example.R.string.resolution_optimization),
                                    color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Mengurangi padding agar pas di resolusi 480p",
                                    color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = support480p,
                                onCheckedChange = { viewModel.toggle480pOptimization(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = themeColorTokens.primary,
                                    checkedTrackColor = themeColorTokens.secondary
                                )
                            )
                        }

                        // 5. Procedural Dynamic Wallpapers (Prebuilt gradients)
                        Text(
                            text = stringResource(id = com.example.R.string.wallpaper_choice_title),
                            color = if (isLightBg) Color(0xFF1D1B20) else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val listGradientsColors = listOf(
                                listOf(Color(0xFFF7F2FA), Color(0xFFEADDFF)),
                                listOf(Color(0xFF08143A), Color(0xFF166AA5)),
                                listOf(Color(0xFF05241C), Color(0xFF268F63)),
                                listOf(Color(0xFF3E1E1E), Color(0xFF9E4130)),
                                listOf(Color(0xFF22052D), Color(0xFF823D9E)),
                                listOf(Color(0xFF202124), Color(0xFF5F6368))
                            )
                            listGradientsColors.forEachIndexed { idx, brush ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp, 56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.verticalGradient(brush))
                                        .border(
                                            width = if (wallpaperIdx == idx) 2.dp else 0.dp,
                                            color = if (isLightBg) Color(0xFF381E72) else Color.White,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.changeWallpaperIndex(idx) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showWallpaperStyleDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColorTokens.primary)
                        ) {
                            Text(text = "Terapkan", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // B. APP LONG CLICK QUICK ACTION ACTIONS SHEET
        if (activeLongClickApp != null) {
            val app = activeLongClickApp!!
            val isAppPinnedHome = homeApps.contains(app)
            val isAppPinnedDock = dockApps.contains(app)

            Dialog(
                onDismissRequest = { activeLongClickApp = null }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isLightBg) Color(0xFFFAFAFA) else Color(0xFF282A2D)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header App summary icon + title
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                RawAppIcon(app = app, themedEnabled = false, size = 32.dp, activeThemeColor = themeColorTokens)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = app.label, color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = app.packageName, color = if (isLightBg) Color(0xFF49454F) else Color.Gray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        HorizontalDivider(color = if (isLightBg) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 8.dp))

                        // Action 1: Pin to desktop screen
                        TextButton(
                            onClick = {
                                viewModel.toggleAppPin(app, isDock = false)
                                activeLongClickApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAppPinnedHome) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                                    contentDescription = "Pin Home",
                                    tint = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (olauncherModeEnabled) {
                                        if (isAppPinnedHome) "Lepaskan dari Aplikasi Favorit" else "Sematkan ke Aplikasi Favorit"
                                    } else {
                                        if (isAppPinnedHome) "Lepaskan dari Layar Utama" else "Pin ke Layar Utama"
                                    },
                                    color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Action 2: Pin to bottom dock
                        if (!olauncherModeEnabled) {
                            TextButton(
                                onClick = {
                                    viewModel.toggleAppPin(app, isDock = true)
                                    activeLongClickApp = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isAppPinnedDock) Icons.Rounded.BookmarkRemove else Icons.Rounded.InstallMobile,
                                        contentDescription = "Pin Dock",
                                        tint = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (isAppPinnedDock) "Lepaskan dari Dock" else "Sematkan ke Dock Bawah",
                                        color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Action 3: Sembunyikan Aplikasi (Hide app)
                        TextButton(
                            onClick = {
                                viewModel.toggleHideApp(app.packageName)
                                activeLongClickApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Hide App",
                                    tint = if (isLightBg) Color(0xFFB06000) else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "Sembunyikan dari Menu Utama", color = if (isLightBg) Color(0xFFB06000) else Color(0xFFFFB74D), fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = if (isLightBg) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))

                        // Action 4: Info Aplikasi (App Info)
                        TextButton(
                            onClick = {
                                viewModel.openAppInfo(app.packageName) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                activeLongClickApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "App Info",
                                    tint = if (isLightBg) Color(0xFF1973E8) else Color(0xFF8AB4F8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "Info Aplikasi", color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 12.sp)
                            }
                        }

                        // Action 5: Hapus Instalasi (Uninstall)
                        TextButton(
                            onClick = {
                                viewModel.uninstallApplication(app.packageName) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                activeLongClickApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Uninstall App",
                                    tint = if (isLightBg) Color(0xFFEA4335) else Color(0xFFF28B82),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "Hapus Instalasi (Uninstall)", color = if (isLightBg) Color(0xFFEA4335) else Color(0xFFF28B82), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // C. WEATHER AT A GLANCE FORECAST DIALOG
        if (showWeatherForecast) {
            Dialog(
                onDismissRequest = { showWeatherForecast = false }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isLightBg) Color(0xFFFAFAFA) else Color(0xFF1E2124)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Cuaca (Pixel Google)", color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { showWeatherForecast = false }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = if (isLightBg) Color(0xFF1D1B20) else Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Status
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = "Weather",
                            tint = if (isLightBg) Color(0xFFE4A11B) else Color(0xFFFFD54F),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "28°C", color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "Jakarta • Hujan Gerimis Ringan", color = if (isLightBg) Color(0xFF49454F) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(20.dp))

                        // Procedural forecast slots metric representation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                Triple("18.00", "27°", Icons.Rounded.Cloud),
                                Triple("19.00", "26°", Icons.Rounded.Cloud),
                                Triple("20.00", "25°", Icons.Rounded.Cloud),
                                Triple("21.00", "25°", Icons.Rounded.Cloud)
                            ).forEach { (time, temp, icon) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = time, color = if (isLightBg) Color(0xFF49454F) else Color.Gray, fontSize = 10.sp)
                                    Icon(imageVector = icon, contentDescription = "Icon", tint = if (isLightBg) Color(0xFF49454F) else Color.LightGray, modifier = Modifier.size(18.dp).padding(vertical = 4.dp))
                                    Text(text = temp, color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Kelembaban: 82% • Angin: 14km/jam", color = if (isLightBg) Color(0xFF49454F).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    }
                }
            }
        }

        // D. DETAILED GOOGLE VOICE ASSISTANT / GEMINI SEARCH PANEL OVERLAY SHEET
        if (showAssistantSheet) {
            Dialog(
                onDismissRequest = { 
                    showAssistantSheet = false
                    assistantQuery = ""
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (dragAmount.y > 20f) {
                                        showAssistantSheet = false
                                        assistantQuery = ""
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(if (isLightBg) Color(0xFFFAFAFA) else Color(0xFF1E1F22), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .padding(bottom = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Assistant visual animated bottom pills
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Box(modifier = Modifier.size(36.dp, 4.dp).background(if (isLightBg) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp)))
                        }

                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Asisten Pixel",
                                    color = if (isLightBg) Color(0xFF1D1B20) else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Interactive colored Google dots
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val colorsGoogle = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                                    colorsGoogle.forEach { col ->
                                        Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = assistantQuery,
                                    onValueChange = { assistantQuery = it },
                                    textStyle = TextStyle(color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 14.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (assistantQuery.isNotEmpty()) {
                                            viewModel.launchGoogleWebSearch(assistantQuery)
                                            showAssistantSheet = false
                                        }
                                    }),
                                    decorationBox = { inner ->
                                        if (assistantQuery.isEmpty()) {
                                            Text(text = "Tanyakan apa saja kepada asisten Google...", color = if (isLightBg) Color(0xFF49454F).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (assistantQuery.isNotEmpty()) {
                                            viewModel.launchGoogleWebSearch(assistantQuery)
                                            showAssistantSheet = false
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Query", tint = if (isLightBg) Color(0xFF49454F) else Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Suggested instant searches
                            Text(text = "Rekomendasi pencarian:", color = if (isLightBg) Color(0xFF49454F) else Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Cuaca Hari Ini",
                                    "Berita Terbaru",
                                    "Google Pixel 8",
                                    "Cara Memasak Rendang",
                                    "Indikator Skor Sepakbola"
                                ).forEach { text ->
                                    Box(
                                        modifier = Modifier
                                            .background(if (isLightBg) Color(0xFFECE6F0) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                            .border(1.dp, if (isLightBg) Color.Transparent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.launchGoogleWebSearch(text)
                                                showAssistantSheet = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = text, color = if (isLightBg) Color(0xFF1D1B20) else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // --- LAYER 3: MATURE FULL-SCREEN PLAYGROUND OVERLAY INTERFACES ---
        if (simulatedAppOpen != null) {
            val app = simulatedAppOpen!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Determine layout overlay depending on app mock packages config
                    when {
                        app.packageName.contains("dialer") -> SimulatedPhoneApp(onClose = { viewModel.closeSimulatedApp() }, accentColor = themeColorTokens)
                        app.packageName.contains("messaging") -> SimulatedMessagesApp(onClose = { viewModel.closeSimulatedApp() }, accentColor = themeColorTokens)
                        app.packageName.contains("chrome") -> SimulatedChromeApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("camera") -> SimulatedCameraApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("vending") -> SimulatedPlayStoreApp(onClose = { viewModel.closeSimulatedApp() }, accentColor = themeColorTokens)
                        app.packageName.contains("maps") -> SimulatedMapsApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("gm") -> SimulatedGmailApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("youtube") -> SimulatedYouTubeApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("photos") -> SimulatedPhotosApp(onClose = { viewModel.closeSimulatedApp() })
                        app.packageName.contains("settings") -> SimulatedSettingsApp(
                            onClose = { viewModel.closeSimulatedApp() },
                            viewModel = viewModel,
                            accentColor = themeColorTokens
                        )
                        else -> SimulatedGenericApp(app = app, onClose = { viewModel.closeSimulatedApp() })
                    }
                }
            }
        }
    }
}

// Accent Color Token container
data class ColorToken(
    val primary: Color,
    val secondary: Color,
    val surfaceVariant: Color
) {
    companion object {
        fun getGoogleColoredG(id: Int = 0): Color {
            return when (id) {
                0 -> Color(0xFF4285F4) // Blue
                1 -> Color(0xFFEA4335) // Red
                2 -> Color(0xFFFBBC05) // Yellow
                else -> Color(0xFF34A853) // Green
            }
        }

        fun getGoogleColoredG(): Color {
            return Color(0xFF4285F4)
        }

        fun getDrawerBackground(isDark: Boolean): Color {
            return if (isDark) Color(0xFF1E1F22) else Color(0xFFFAFAFA)
        }
    }
}

// Master unified App Icon component resolver
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconItem(
    app: InstalledApp,
    themedEnabled: Boolean,
    activeThemeColor: ColorToken,
    iconSize: Dp,
    textSize: TextUnit,
    textColor: Color = Color.White,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RawAppIcon(app = app, themedEnabled = themedEnabled, size = iconSize, activeThemeColor = activeThemeColor)
        }
        
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.label,
                color = textColor,
                fontSize = textSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

// High-fidelity stock icons drawer based on package name strings
@Composable
fun RawAppIcon(
    app: InstalledApp,
    themedEnabled: Boolean,
    size: Dp,
    activeThemeColor: ColorToken
) {
    val context = LocalContext.current
    if (app.icon != null) {
        // System real package icon
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = app.label,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // High polish procedural Google icons
        val isThemed = themedEnabled
        val packageStr = app.packageName

        val iconTint = if (isThemed) activeThemeColor.primary else Color.White
        val backdropColor = if (isThemed) activeThemeColor.surfaceVariant else when {
            packageStr.contains("dialer") -> Color(0xFF0F9D58)     // Phone Green
            packageStr.contains("messaging") -> Color(0xFF4285F4)  // Message Blue
            packageStr.contains("chrome") -> Color.White            // Google Pure White
            packageStr.contains("camera") -> Color(0xFF3C4043)      // Metallic gray
            packageStr.contains("vending") -> Color.White           // Store White
            packageStr.contains("maps") -> Color(0xFFEEEEEE)         // Maps background
            packageStr.contains("gm") -> Color.White               // Mail White
            packageStr.contains("youtube") -> Color(0xFFEA4335)     // Red YouTube
            packageStr.contains("settings") -> Color(0xFF78909C)    // Settings Teal-slate
            else -> activeThemeColor.primary
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val vector = when {
                packageStr.contains("dialer") -> Icons.Rounded.Phone
                packageStr.contains("messaging") -> Icons.Rounded.Sms
                packageStr.contains("chrome") -> Icons.Rounded.Language
                packageStr.contains("camera") -> Icons.Rounded.CameraAlt
                packageStr.contains("vending") -> Icons.Rounded.PlayArrow
                packageStr.contains("maps") -> Icons.Rounded.Map
                packageStr.contains("gm") -> Icons.Rounded.Mail
                packageStr.contains("youtube") -> Icons.Rounded.SmartDisplay
                packageStr.contains("photos") -> Icons.Rounded.PhotoLibrary
                packageStr.contains("calendar") -> Icons.Rounded.CalendarToday
                packageStr.contains("tasks") -> Icons.Rounded.TaskAlt
                packageStr.contains("settings") -> Icons.Rounded.Settings
                else -> Icons.Rounded.Apps
            }

            // Google multi-colored fallback overlay on white backgrounds when themed icons are OFF
            val useGoogleColors = !isThemed && (packageStr.contains("chrome") || packageStr.contains("vending") || packageStr.contains("maps"))

            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = if (useGoogleColors) ColorToken.getGoogleColoredG(0) else iconTint,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

// ====== SIMULATION OVERLAY INTERFACES ======

// 1. DIALER / SMART PHONE SIMULATION
@Composable
fun SimulatedPhoneApp(onClose: () -> Unit, accentColor: ColorToken) {
    var dialDigits by remember { mutableStateOf("") }
    var dialStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101214))
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF1B1E21)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Telepon", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Icon(imageVector = Icons.Rounded.Dialpad, contentDescription = "Switch Mode", tint = Color.White)
        }

        // Display area
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (dialDigits.isEmpty()) "Mulai Mengetik Nomor..." else dialDigits,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dialStatus.isNotEmpty()) {
                    Text(text = dialStatus, color = accentColor.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Mock contacts list capsule
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Kontak Cepat", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "Layanan Google Pixel Hub" to "112",
                        "Layanan Darurat Indonesia" to "119",
                        "Hubungi Admin" to "0812-4011-2041"
                    ).forEach { (name, num) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { dialDigits = num }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = name, color = Color.White, fontSize = 12.sp)
                            Text(text = num, color = accentColor.primary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Standard Numeric pad
            val padRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                padRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { char ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .clickable {
                                        dialDigits += char
                                        dialStatus = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = char, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Call circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (dialDigits.isNotEmpty()) {
                                dialDigits = dialDigits.dropLast(1)
                            }
                        },
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Clear", tint = Color.LightGray)
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF34A853), CircleShape)
                            .clickable {
                                if (dialDigits.isEmpty()) {
                                    dialStatus = "Masukkan nomor tujuan"
                                } else {
                                    dialStatus = "Menghubungi $dialDigits..."
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Rounded.Phone, contentDescription = "Call", tint = Color.White)
                    }
                }
            }
        }
    }
}

// 2. MESSAGES SIMULATION WITH INTERACTIVE BOT RESPONSES
@Composable
fun SimulatedMessagesApp(onClose: () -> Unit, accentColor: ColorToken) {
    var txtMessage by remember { mutableStateOf("") }
    var listChat by remember { mutableStateOf(listOf(
        "Halo! Terima kasih telah memasang Pixel Launcher rasa Google Pixel.",
        "Ini adalah simulasi SMS Interaktif Pixel. Silakan ketik baris apa saja di bawah!"
    )) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1C1D1F))
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF232528)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(36.dp).background(accentColor.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Rounded.Sms, contentDescription = "Bot", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Rekan Google Pixel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "Aktif (Simulated)", color = Color.Green, fontSize = 10.sp)
            }
        }

        // Chats body panel
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listChat) { msg ->
                val isUser = msg.startsWith("USER: ")
                val cleanText = if (isUser) msg.substring(6) else msg
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isUser) accentColor.primary else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                            .widthIn(max = 240.dp)
                    ) {
                        Text(text = cleanText, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Message Input Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF232528))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = txtMessage,
                    onValueChange = { txtMessage = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (txtMessage.isEmpty()) {
                            Text(text = "Ketik sms balasan...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (txtMessage.isNotEmpty()) {
                        val input = txtMessage
                        listChat = listChat + "USER: $input"
                        txtMessage = ""
                        
                        // Instant bot answer routine
                        val botAnswer = when {
                            input.contains("halo", true) || input.contains("hi", true) -> "Halo juga! Senang berbincang dengan Anda."
                            input.contains("info", true) -> "Peluncur ini teroptimasi sempurna untuk resolusi layar minimal 480p."
                            input.contains("pixel", true) -> "Google Pixel memiliki gaya antarmuka Material You yang sangat harmonis."
                            else -> "Pesan Anda diterima! Ini adalah contoh chat interaktif asisten launcher."
                        }
                        listChat = listChat + botAnswer
                    }
                }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = accentColor.primary)
            }
        }
    }
}

// 3. CHROME SIMULATION WITH BASIC WEATHER OR RESOURCE VIEW
@Composable
fun SimulatedChromeApp(onClose: () -> Unit) {
    var queryText by remember { mutableStateOf("https://www.google.com") }
    var loadedPageDesc by remember { mutableStateOf("Selamat datang di Google Search! Masukkan kueri atau website di kolom pencarian.") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212))
    ) {
        // Bar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF222222)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Rounded.Language, contentDescription = "Web", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = {
                loadedPageDesc = "Membuka halaman web untuk: $queryText\n\nMenampilkan portal Google Pixel Hub. Menyambungkan koneksi virtual..."
            }) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val colorsG = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                        colorsG.forEach { col ->
                            Box(modifier = Modifier.size(14.dp).background(col, CircleShape))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Hasil Browser Virtual", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = loadedPageDesc, color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// 4. CAMERA SIMULATION WITH FLASHLIGHT VIEWS
@Composable
fun SimulatedCameraApp(onClose: () -> Unit) {
    var previewText by remember { mutableStateOf("Fokus Kamera: Siap Memotret") }
    var photosCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            Text(text = "Pixel Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
        }

        // Viewfinder
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Rounded.Camera, contentDescription = "Camera viewport", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = previewText, color = Color.White, fontSize = 12.sp)
                if (photosCount > 0) {
                    Text(text = "Jumlah foto tersimpan: $photosCount", color = Color(0xFFFFD54F), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Capture bar
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            // Big white shutter trigger button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, CircleShape)
                    .clickable {
                        photosCount++
                        previewText = "Cekrekk! Foto berhasil disimpan."
                    }
            )

            Box(
                modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Rounded.Videocam, contentDescription = "Video mode", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// 5. PLAY STORE SIMULATION
@Composable
fun SimulatedPlayStoreApp(onClose: () -> Unit, accentColor: ColorToken) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF222222)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Play Store", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(text = "Rekomendasi Aplikasi Teratas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }

            val marketApps = listOf(
                Triple("WhatsApp", "Komunikasi • 4.8★", Icons.Rounded.Sms),
                Triple("Discord", "Komunikasi • 4.4★", Icons.Rounded.Gamepad),
                Triple("Spotify", "Musik • 4.6★", Icons.Rounded.MusicNote),
                Triple("Google Meet", "Bisnis • 4.5★", Icons.Rounded.VideoCall)
            )

            items(marketApps) { (name, label, icon) ->
                var isInstalledBySim by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).background(accentColor.primary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(imageVector = icon, contentDescription = "Icon", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = label, color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Button(
                            onClick = { isInstalledBySim = !isInstalledBySim },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isInstalledBySim) Color.Gray else accentColor.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text(text = if (isInstalledBySim) "Terpasang" else "Pasang", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// 6. MAPS SIMULATION SHOWING TRAVEL ROUTES BETWEEN CAPITALS
@Composable
fun SimulatedMapsApp(onClose: () -> Unit) {
    var routeStatus by remember { mutableStateOf("Jakarta -> Bandung • Menghitung rute terbaik") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE8F0FE))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF1B5E20)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text(text = "Google Maps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Icon(imageVector = Icons.Rounded.MyLocation, contentDescription = "Locator", tint = Color.White)
        }

        // Map layout procedural
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFFE8F0FE))) {
                // Background routes styling
                drawLine(
                    color = Color(0xFFA5D6A7),
                    start = androidx.compose.ui.geometry.Offset(0f, 300f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 400f),
                    strokeWidth = 18f
                )
                drawLine(
                    color = Color(0xFFFFCC80),
                    start = androidx.compose.ui.geometry.Offset(200f, 0f),
                    end = androidx.compose.ui.geometry.Offset(100f, size.height),
                    strokeWidth = 14f
                )
            }

            // Route search capsule
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = routeStatus, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { routeStatus = "Jakarta -> Bandung • Waktu tempuh 2 jam 14 menit" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Rute A", fontSize = 9.sp, color = Color.White)
                        }
                        Button(
                            onClick = { routeStatus = "Gambir -> Padalarang • Kereta Cepat Whoosh 30 menit" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Rute B (Whoosh)", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// 7. GMAIL SIMULATION
@Composable
fun SimulatedGmailApp(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1F2023))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF2E3135)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Gmail Inbox", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val mailList = listOf(
                Triple("Tim Pixel Global", "Pembaruan Peluncur Android", "Uji coba Pixel Launcher pada versi 480p selesai dikerjakan..."),
                Triple("Sistem AI Studio", "Verifikasi Kunci Sandbox", "Akun pengembang berhasil terintegrasi secara aman pada database..."),
                Triple("Rilis GitHub Info", "Dukungan Proyek Olauncher", "Dukungan repositori Olauncher kini mendukung tema Pixel asli...")
            )
            items(mailList) { (sender, subject, body) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = sender, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Hari ini", color = Color.Gray, fontSize = 8.sp)
                        }
                        Text(text = subject, color = Color(0xFF9ECAFF), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 2.dp))
                        Text(text = body, color = Color.LightGray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// 8. YOUTUBE SIMULATION
@Composable
fun SimulatedYouTubeApp(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF212121)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.Red) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "YouTube", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val videos = listOf(
                "Unboxing Google Pixel 8 Pro di tahun 2026!" to "Pixel Lover • 120rb x • 2 hari lalu",
                "Integrasi Jetpack Compose Dasar untuk Pemula" to "Komedi Coder • 4rb x • 1 minggu lalu",
                "Fitur Keren Android 16 ala Pixel UI" to "GadgetIndo • 342rb x • 1 bulan lalu"
            )

            items(videos) { (title, stats) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Rounded.SmartDisplay, contentDescription = "Play", tint = Color.Red, modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = stats, color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

// 9. PHOTOS SIMULATION
@Composable
fun SimulatedPhotosApp(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E2022))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF2E3134)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Google Foto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(6) { idx ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = "Photo", tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// 10. SYSTEM SETTINGS (SETELAN) SIMULATION
@Composable
fun SimulatedSettingsApp(
    onClose: () -> Unit,
    viewModel: LauncherViewModel,
    accentColor: ColorToken
) {
    val accentName by viewModel.themeAccent.collectAsState()
    val themedIcons by viewModel.themedIconsEnabled.collectAsState()
    val columnsSize by viewModel.gridColumnSize.collectAsState()
    val is480pEnabled by viewModel.support480pEnabled.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1C1E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF222427)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Setelan Sistem", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Pengaturan Cepat Launcher", color = accentColor.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            // Dynamic accent
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Tema Warna Saat ini", color = Color.White, fontSize = 13.sp)
                    Text(text = accentName, color = Color.Gray, fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        val nextTheme = when (accentName) {
                            "Blue" -> "Mint"
                            "Mint" -> "Coral"
                            "Coral" -> "Lavender"
                            "Lavender" -> "Charcoal"
                            else -> "Blue"
                        }
                        viewModel.changeThemeAccent(nextTheme)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.primary)
                ) {
                    Text(text = "Ubah", fontSize = 11.sp, color = Color.White)
                }
            }

            // Themed icons
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Material You Icons", color = Color.White, fontSize = 13.sp)
                    Text(text = if (themedIcons) "Aktif" else "Nonaktif", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = themedIcons,
                    onCheckedChange = { viewModel.toggleThemedIcons(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor.primary)
                )
            }

            // Sizing Grid
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Koleksi Kisi Layar", color = Color.White, fontSize = 13.sp)
                    Text(text = "Ukuran: ${columnsSize}x${columnsSize}", color = Color.Gray, fontSize = 10.sp)
                }
                Button(
                    onClick = { viewModel.changeGridColumns(if (columnsSize == 4) 5 else 4) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.primary)
                ) {
                    Text(text = "Ubah Kisi", fontSize = 11.sp)
                }
            }

            // 480p
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Optimasi Layar 480p", color = Color.White, fontSize = 13.sp)
                    Text(text = if (is480pEnabled) "Aktif" else "Nonaktif", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = is480pEnabled,
                    onCheckedChange = { viewModel.toggle480pOptimization(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor.primary)
                )
            }

            // Olauncher Mode Focus Toggle
            val olauncherModeEnabled by viewModel.olauncherModeEnabled.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Modus Minimalis Olauncher", color = Color.White, fontSize = 13.sp)
                    Text(text = if (olauncherModeEnabled) "Aktif (Tanpa G-Bar & Clutter)" else "Nonaktif", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = olauncherModeEnabled,
                    onCheckedChange = { viewModel.toggleOlauncherMode(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor.primary)
                )
            }

            if (olauncherModeEnabled) {
                val olauncherShowIcons by viewModel.olauncherShowIcons.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Tampilkan Ikon Olauncher", color = Color.White, fontSize = 13.sp)
                        Text(text = if (olauncherShowIcons) "Aktif" else "Nonaktif", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = olauncherShowIcons,
                        onCheckedChange = { viewModel.toggleOlauncherShowIcons(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info device
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Tentang Pixel Launcher", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Versi: Loncatan 1.0 (Produksi)", color = Color.Gray, fontSize = 10.sp)
                    Text(text = "Desain: 1:1 Google Pixel Engine", color = Color.Gray, fontSize = 10.sp)
                    Text(text = "Optimasi Grafis: Rendah-Padat Resolusi (480p)", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

// 11. GENERIC BACKUP DIALOG OVERLAY FOR CUSTOM APP PACKAGE ITEMS
@Composable
fun SimulatedGenericApp(app: InstalledApp, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E2022))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF2D3033)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.Apps, contentDescription = "Generic", tint = Color.LightGray, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Memuat Hub Konten...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aplikasi ${app.label} (${app.packageName}) sedang disimulasikan dalam modus aman Sandbox. Selamat menjelajah!",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalHeaderClock(
    currentTime: Long,
    desktopTextColor: Color,
    desktopSubTextColor: Color,
    isLightBg: Boolean,
    support480p: Boolean,
    olauncherModeEnabled: Boolean,
    onDateClick: () -> Unit,
    onWeatherClick: () -> Unit,
    atGlanceWeatherText: String,
    themeColorTokens: ColorToken = ColorToken(Color(0xFF381E72), Color(0xFFD0BCFF), Color(0xFFEADDFF))
) {
    val clockString = remember(currentTime) {
        val sdf = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(currentTime))
    }
    val dayDateString = remember(currentTime) {
        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(currentTime))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (support480p) 4.dp else 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (olauncherModeEnabled) {
            // Olauncher 1:1 Pure Elegant Minimalist Text Style
            Text(
                text = clockString,
                color = desktopTextColor,
                fontSize = if (support480p) 52.sp else 64.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 0.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayDateString,
                color = themeColorTokens.primary,
                fontSize = if (support480p) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDateClick() }
            )
        } else {
            // Large clock indicator (highly legible on low-res 480p screens)
            Text(
                text = clockString,
                color = desktopTextColor,
                fontSize = if (support480p) 44.sp else 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // Google Pixel Classic At A Glance layout (Date + Weather)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDateClick() }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = "Date",
                    tint = desktopTextColor,
                    modifier = Modifier.size(if (support480p) 14.dp else 18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dayDateString,
                    color = desktopTextColor,
                    fontSize = if (support480p) 12.sp else 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onWeatherClick() }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "Weather",
                    tint = if (isLightBg) Color(0xFFE4A11B) else Color(0xFFFFD54F),
                    modifier = Modifier.size(if (support480p) 16.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = atGlanceWeatherText,
                    color = desktopSubTextColor,
                    fontSize = if (support480p) 11.sp else 13.sp
                )
            }
        }
    }
}
