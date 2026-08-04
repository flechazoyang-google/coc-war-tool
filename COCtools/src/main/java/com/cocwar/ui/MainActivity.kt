package com.cocwar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cocwar.ui.detail.EventDetailScreen
import com.cocwar.ui.eventlist.EventListScreen
import com.cocwar.ui.importflow.ImportScreen
import com.cocwar.ui.members.MemberManageScreen
import com.cocwar.ui.season.LeagueSeasonScreen
import com.cocwar.ui.stats.StatsScreen
import com.cocwar.ui.sync.SyncScreen
import com.cocwar.ui.theme.CocWarTheme
import com.cocwar.ui.theme.ThemePrefs
import com.cocwar.ui.theme.ThemeStyle
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.tools.ToolsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+：请求通知权限（悬浮球前台通知、截图完成通知需要）
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            val context = this
            var themeStyle by remember { mutableStateOf(ThemePrefs.load(context)) }
            CocWarTheme(style = themeStyle) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CocWarNavHost(
                        themeStyle = themeStyle,
                        onThemeChange = { style ->
                            themeStyle = style
                            ThemePrefs.save(context, style)
                        }
                    )
                }
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val BottomNavItems = listOf(
    BottomNavItem("event_list", "战报", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    BottomNavItem("stats", "统计", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    BottomNavItem("member_manage", "成员", Icons.Filled.Groups, Icons.Outlined.Groups),
    BottomNavItem("tools", "工具", Icons.Filled.Build, Icons.Outlined.Build),
)

private val TopLevelRoutes = setOf("event_list", "stats", "member_manage", "tools")

@Composable
private fun CocWarNavHost(
    themeStyle: ThemeStyle = ThemeStyle.LEDGER,
    onThemeChange: (ThemeStyle) -> Unit = {},
) {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "event_list"
    val showBottomBar = currentRoute in TopLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // 细线 + 纸面底栏，去掉 M3 默认 tonal 色块
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        BottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(item.route) {
                                        popUpTo(nav.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        item.label,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "event_list",
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable("event_list") {
                EventListScreen(
                    onOpen = { nav.navigate("detail/$it") },
                    onImport = { nav.navigate("import") },
                    onOpenSeason = { year, month, match ->
                        nav.navigate("league_season/$year/$month/$match")
                    },
                )
            }
            composable("league_season/{year}/{month}/{match}") {
                val year = it.arguments?.getString("year")?.toIntOrNull() ?: 0
                val month = it.arguments?.getString("month")?.toIntOrNull() ?: 0
                val match = it.arguments?.getString("match")?.toIntOrNull() ?: 1
                LeagueSeasonScreen(year = year, month = month, match = match, onBack = { nav.popBackStack() })
            }
            composable("detail/{eventId}") {
                val eventId = it.arguments?.getString("eventId") ?: ""
                EventDetailScreen(eventId = eventId, onBack = { nav.popBackStack() })
            }
            composable("import") {
                ImportScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
            composable("stats") {
                StatsScreen(onBack = { nav.popBackStack() })
            }
            composable("member_manage") {
                MemberManageScreen(onBack = { nav.popBackStack() })
            }
            composable("sync") {
                SyncScreen(onBack = { nav.popBackStack() })
            }
            composable("tools") {
                ToolsScreen(
                    onSync = { nav.navigate("sync") },
                    themeStyle = themeStyle,
                    onThemeChange = onThemeChange,
                )
            }
        }
    }
}
