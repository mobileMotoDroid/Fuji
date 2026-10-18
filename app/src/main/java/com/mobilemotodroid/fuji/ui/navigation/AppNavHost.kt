package com.mobilemotodroid.fuji.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobilemotodroid.fuji.ui.activity.ActivityDetailScreen
import com.mobilemotodroid.fuji.ui.activity.ActivityListScreen
import com.mobilemotodroid.fuji.ui.map.MapScreen
import com.mobilemotodroid.fuji.ui.offline.OfflineDownloadScreen
import com.mobilemotodroid.fuji.ui.offline.OfflineListScreen
import com.mobilemotodroid.fuji.ui.route.RouteListScreen
import com.mobilemotodroid.fuji.ui.save.SaveRecordingScreen
import com.mobilemotodroid.fuji.ui.settings.SettingsScreen

/** 画面の経路（要件定義書 6.1 の SCR-02〜SCR-10。SCR-01 は MainActivity で表示する）。 */
object Routes {
    const val MAP = "map" // SCR-02 / SCR-03
    const val SAVE = "save/{activityId}" // SCR-04
    const val ACTIVITIES = "activities" // SCR-05
    const val ACTIVITY_DETAIL = "activity/{activityId}" // SCR-06
    const val ROUTES = "routes" // SCR-07
    const val OFFLINE = "offline" // SCR-08
    const val OFFLINE_DOWNLOAD = "offline/download?routeId={routeId}" // SCR-09
    const val SETTINGS = "settings" // SCR-10

    fun save(id: Long) = "save/$id"
    fun activityDetail(id: Long) = "activity/$id"
    fun offlineDownload(routeId: Long? = null) = "offline/download?routeId=${routeId ?: -1}"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVELS = listOf(
    TopLevel(Routes.MAP, "地図", Icons.Default.Map),
    TopLevel(Routes.ACTIVITIES, "活動記録", Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.ROUTES, "ルート", Icons.Default.Directions),
    TopLevel(Routes.OFFLINE, "オフライン", Icons.Default.DownloadForOffline),
    TopLevel(Routes.SETTINGS, "設定", Icons.Default.Settings),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TOP_LEVELS.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVELS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    onRecordingFinished = { id ->
                        navController.navigate(Routes.save(id)) { launchSingleTop = true }
                    },
                    onOpenRoutes = { navController.navigateTopLevel(Routes.ROUTES) },
                )
            }
            composable(
                Routes.SAVE,
                arguments = listOf(navArgument("activityId") { type = NavType.LongType }),
            ) {
                SaveRecordingScreen(
                    onSaved = { id ->
                        navController.navigate(Routes.activityDetail(id)) {
                            popUpTo(Routes.MAP)
                        }
                    },
                    onDiscarded = { navController.popBackStack(Routes.MAP, inclusive = false) },
                )
            }
            composable(Routes.ACTIVITIES) {
                ActivityListScreen(onOpen = { navController.navigate(Routes.activityDetail(it)) })
            }
            composable(
                Routes.ACTIVITY_DETAIL,
                arguments = listOf(navArgument("activityId") { type = NavType.LongType }),
            ) {
                ActivityDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ROUTES) {
                RouteListScreen(
                    onShowOnMap = { navController.navigateTopLevel(Routes.MAP) },
                    onDownloadAround = { navController.navigate(Routes.offlineDownload(it)) },
                )
            }
            composable(Routes.OFFLINE) {
                OfflineListScreen(onNewDownload = { navController.navigate(Routes.offlineDownload()) })
            }
            composable(
                Routes.OFFLINE_DOWNLOAD,
                arguments = listOf(
                    navArgument("routeId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                OfflineDownloadScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = {
                        navController.navigate(Routes.OFFLINE) {
                            popUpTo(Routes.OFFLINE_DOWNLOAD) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

/** 下部のタブの切り替え。タブごとの画面の状態を保つ。 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
