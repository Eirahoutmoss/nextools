package com.eirahoutmoss.nextools.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eirahoutmoss.nextools.AppContainer
import com.eirahoutmoss.nextools.NexToolsApp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.settings.ThemeMode
import com.eirahoutmoss.nextools.ui.screens.CompareScreen
import com.eirahoutmoss.nextools.ui.screens.DeviceReportScreen
import com.eirahoutmoss.nextools.ui.screens.FavoritesScreen
import com.eirahoutmoss.nextools.ui.screens.HomeScreen
import com.eirahoutmoss.nextools.ui.screens.NotebookScreen
import com.eirahoutmoss.nextools.ui.screens.RecordDetailScreen
import com.eirahoutmoss.nextools.ui.screens.SettingsScreen
import com.eirahoutmoss.nextools.ui.tools.AirtightnessTool
import com.eirahoutmoss.nextools.ui.tools.ColorTemperatureTool
import com.eirahoutmoss.nextools.ui.tools.GaitTool
import com.eirahoutmoss.nextools.ui.tools.IrRemoteTool
import com.eirahoutmoss.nextools.ui.tools.PhReaderTool
import com.eirahoutmoss.nextools.ui.tools.SonarTool
import com.eirahoutmoss.nextools.ui.tools.SpeedVideoTool
import com.eirahoutmoss.nextools.ui.tools.MagnetStrengthTool
import com.eirahoutmoss.nextools.ui.tools.MetronomeTool
import com.eirahoutmoss.nextools.ui.tools.ProtractorTool
import com.eirahoutmoss.nextools.ui.tools.RulerTool
import com.eirahoutmoss.nextools.ui.tools.SpeakerMicTestTool
import com.eirahoutmoss.nextools.ui.tools.TachometerTool
import com.eirahoutmoss.nextools.ui.tools.WeatherTrendTool
import com.eirahoutmoss.nextools.ui.tools.CosmicRayTool
import com.eirahoutmoss.nextools.ui.tools.GpsSpoofTool
import com.eirahoutmoss.nextools.ui.tools.IdCardTool
import com.eirahoutmoss.nextools.ui.tools.PrecisePositionTool
import com.eirahoutmoss.nextools.ui.tools.SpectroscopeTool
import com.eirahoutmoss.nextools.ui.tools.UwbTool
import com.eirahoutmoss.nextools.ui.tools.WifiRttTool
import com.eirahoutmoss.nextools.ui.tools.AltimeterTool
import com.eirahoutmoss.nextools.ui.tools.DrivingProfileTool
import com.eirahoutmoss.nextools.ui.tools.FlickerTool
import com.eirahoutmoss.nextools.ui.tools.MachineHealthTool
import com.eirahoutmoss.nextools.ui.tools.PulseTool
import com.eirahoutmoss.nextools.ui.tools.RoadQualityTool
import com.eirahoutmoss.nextools.ui.tools.RoomEchoTool
import com.eirahoutmoss.nextools.ui.tools.BarometerTool
import com.eirahoutmoss.nextools.ui.tools.CompassTool
import com.eirahoutmoss.nextools.ui.tools.LevelTool
import com.eirahoutmoss.nextools.ui.tools.LuxTool
import com.eirahoutmoss.nextools.ui.tools.MagnetometerTool
import com.eirahoutmoss.nextools.ui.tools.MetalFinderTool
import com.eirahoutmoss.nextools.ui.tools.SensorTestTool
import com.eirahoutmoss.nextools.ui.tools.SoundLevelTool
import com.eirahoutmoss.nextools.ui.tools.ColorPickerTool
import com.eirahoutmoss.nextools.ui.tools.FloorCounterTool
import com.eirahoutmoss.nextools.ui.tools.MagnifierTool
import com.eirahoutmoss.nextools.ui.tools.QrTool
import com.eirahoutmoss.nextools.ui.tools.SeismographTool
import com.eirahoutmoss.nextools.ui.tools.SpectrumTool
import com.eirahoutmoss.nextools.ui.tools.ToneGeneratorTool
import com.eirahoutmoss.nextools.ui.tools.TunerTool
import com.eirahoutmoss.nextools.ui.tools.VibrationTool
import com.eirahoutmoss.nextools.ui.tools.AreaTool
import com.eirahoutmoss.nextools.ui.tools.BatteryTool
import com.eirahoutmoss.nextools.ui.tools.BleScannerTool
import com.eirahoutmoss.nextools.ui.tools.GpsStatusTool
import com.eirahoutmoss.nextools.ui.tools.NfcReaderTool
import com.eirahoutmoss.nextools.ui.tools.ScreenTestTool
import com.eirahoutmoss.nextools.ui.tools.TrackTool
import com.eirahoutmoss.nextools.ui.tools.WifiAnalyzerTool

object Routes {
    const val TOOLS = "tools"
    const val FAVORITES = "favorites"
    const val NOTEBOOK = "notebook"
    const val SETTINGS = "settings"
    const val TOOL = "tool/{key}"
    const val RECORD = "record/{id}"
    const val COMPARE = "compare/{a}/{b}"

    fun tool(id: ToolId) = "tool/${id.key}"
    fun record(id: String) = "record/$id"
    fun compare(a: String, b: String) = "compare/$a/$b"

    val TOP_LEVEL = setOf(TOOLS, FAVORITES, NOTEBOOK, SETTINGS)
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as NexToolsApp).container

/** Durum çubuğu simgeleri seçilen temaya uysun (telefon açık, uygulama koyu olabilir). */
@Composable
private fun SystemBars() {
    val c = appContainer()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val activity = LocalContext.current as? ComponentActivity
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0f).toArgb()
    DisposableEffect(dark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
            navigationBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
        )
        onDispose { }
    }
}

private data class TopItem(val route: String, val icon: ImageVector, val label: Int)

@Composable
fun NexToolsRoot() {
    val c = appContainer()
    SystemBars()
    LaunchedEffect(Unit) { c.records.ensureLoaded() }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val items = listOf(
        TopItem(Routes.TOOLS, Icons.Outlined.GridView, R.string.nav_tools),
        TopItem(Routes.FAVORITES, Icons.Outlined.StarOutline, R.string.nav_favorites),
        TopItem(Routes.NOTEBOOK, Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_notebook),
        TopItem(Routes.SETTINGS, Icons.Outlined.Settings, R.string.nav_settings),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in Routes.TOP_LEVEL) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { nav.switchTab(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Alt çubuk yalnızca üst düzey ekranlarda var; araç ekranları kendi kenar boşluklarını yönetir.
        val inner = if (route in Routes.TOP_LEVEL) {
            val bottom = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding())
            Modifier.padding(bottom).consumeWindowInsets(bottom)
        } else Modifier
        NavHost(navController = nav, startDestination = Routes.TOOLS, modifier = inner) {
            composable(Routes.TOOLS) {
                HomeScreen(onOpenTool = { nav.navigate(Routes.tool(it)) })
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(onOpenTool = { nav.navigate(Routes.tool(it)) }, onBrowse = { nav.switchTab(Routes.TOOLS) })
            }
            composable(Routes.NOTEBOOK) {
                NotebookScreen(onOpen = { nav.navigate(Routes.record(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenTool = { nav.navigate(Routes.tool(it)) })
            }
            composable(Routes.TOOL, arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
                val id = ToolId.fromKey(e.arguments?.getString("key").orEmpty())
                val back: () -> Unit = { nav.popBackStack() }
                val openRecord: (String) -> Unit = { nav.navigate(Routes.record(it)) }
                when (id) {
                    ToolId.LEVEL -> LevelTool(back, openRecord)
                    ToolId.COMPASS -> CompassTool(back, openRecord)
                    ToolId.MAGNETOMETER -> MagnetometerTool(back, openRecord)
                    ToolId.METAL_FINDER -> MetalFinderTool(back, openRecord)
                    ToolId.BAROMETER -> BarometerTool(back, openRecord)
                    ToolId.ALTIMETER -> AltimeterTool(back, openRecord)
                    ToolId.LUX -> LuxTool(back, openRecord)
                    ToolId.SOUND_LEVEL -> SoundLevelTool(back, openRecord)
                    ToolId.SENSOR_TEST -> SensorTestTool(back, openRecord)
                    ToolId.DEVICE_REPORT -> DeviceReportScreen(back)
                    ToolId.VIBRATION -> VibrationTool(back, openRecord)
                    ToolId.SEISMOGRAPH -> SeismographTool(back, openRecord)
                    ToolId.SPECTRUM -> SpectrumTool(back, openRecord)
                    ToolId.TUNER -> TunerTool(back, openRecord)
                    ToolId.TONE_GENERATOR -> ToneGeneratorTool(back)
                    ToolId.FLOOR_COUNTER -> FloorCounterTool(back, openRecord)
                    ToolId.COLOR_PICKER -> ColorPickerTool(back, openRecord)
                    ToolId.MAGNIFIER -> MagnifierTool(back)
                    ToolId.QR -> QrTool(back)
                    ToolId.GPS_STATUS -> GpsStatusTool(back, openRecord)
                    ToolId.AREA -> AreaTool(back, openRecord)
                    ToolId.TRACK -> TrackTool(back, openRecord)
                    ToolId.WIFI_ANALYZER -> WifiAnalyzerTool(back, openRecord)
                    ToolId.BLE_SCANNER -> BleScannerTool(back, openRecord)
                    ToolId.NFC_READER -> NfcReaderTool(back)
                    ToolId.BATTERY -> BatteryTool(back, openRecord)
                    ToolId.SCREEN_TEST -> ScreenTestTool(back)
                    ToolId.RT60 -> RoomEchoTool(back, openRecord)
                    ToolId.FLICKER -> FlickerTool(back, openRecord)
                    ToolId.ROAD_QUALITY -> RoadQualityTool(back, openRecord)
                    ToolId.DRIVING_PROFILE -> DrivingProfileTool(back, openRecord)
                    ToolId.MACHINE_HEALTH -> MachineHealthTool(back, openRecord)
                    ToolId.AIRTIGHTNESS -> AirtightnessTool(back, openRecord)
                    ToolId.PULSE -> PulseTool(back, openRecord)
                    ToolId.PRECISE_POSITION -> PrecisePositionTool(back, openRecord)
                    ToolId.GPS_SPOOF -> GpsSpoofTool(back, openRecord)
                    ToolId.WIFI_RTT_RANGE -> WifiRttTool(back, openRecord)
                    ToolId.UWB_RANGE -> UwbTool(back, openRecord)
                    ToolId.ID_CARD -> IdCardTool(back)
                    ToolId.SPECTROSCOPE -> SpectroscopeTool(back, openRecord)
                    ToolId.COSMIC_RAY -> CosmicRayTool(back, openRecord)
                    ToolId.MAGNET_STRENGTH -> MagnetStrengthTool(back, openRecord)
                    ToolId.TACHOMETER -> TachometerTool(back, openRecord)
                    ToolId.WEATHER_TREND -> WeatherTrendTool(back, openRecord)
                    ToolId.METRONOME -> MetronomeTool(back)
                    ToolId.SPEAKER_MIC_TEST -> SpeakerMicTestTool(back)
                    ToolId.PROTRACTOR -> ProtractorTool(back)
                    ToolId.RULER -> RulerTool(back)
                    ToolId.GAIT -> GaitTool(back, openRecord)
                    ToolId.COLOR_TEMPERATURE -> ColorTemperatureTool(back, openRecord)
                    ToolId.PH_READER -> PhReaderTool(back, openRecord)
                    ToolId.SONAR -> SonarTool(back, openRecord)
                    ToolId.SPEED_VIDEO -> SpeedVideoTool(back, openRecord)
                    ToolId.IR_REMOTE -> IrRemoteTool(back)
                    else -> LaunchedEffect(Unit) { nav.popBackStack() }
                }
            }
            composable(Routes.RECORD, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                RecordDetailScreen(
                    id = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onCompare = { a, b -> nav.navigate(Routes.compare(a, b)) },
                )
            }
            composable(
                Routes.COMPARE,
                arguments = listOf(navArgument("a") { type = NavType.StringType }, navArgument("b") { type = NavType.StringType }),
            ) { e ->
                CompareScreen(e.arguments?.getString("a").orEmpty(), e.arguments?.getString("b").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
