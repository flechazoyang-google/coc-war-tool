package com.cocwar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cocwar.ui.detail.EventDetailScreen
import com.cocwar.ui.eventlist.EventListScreen
import com.cocwar.ui.importflow.AiImportScreen
import com.cocwar.ui.importflow.ImportScreen
import com.cocwar.ui.members.MemberManageScreen
import com.cocwar.ui.settings.AiConfigScreen
import com.cocwar.ui.stats.StatsScreen
import com.cocwar.ui.sync.SyncScreen
import com.cocwar.ui.theme.CocWarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CocWarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CocWarNavHost()
                }
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BottomNavItems = listOf(
    BottomNavItem("event_list", "战报", Icons.Filled.Home),
    BottomNavItem("stats", "统计", Icons.Filled.BarChart),
    BottomNavItem("member_manage", "成员", Icons.Filled.ManageAccounts),
    BottomNavItem("ai_config", "设置", Icons.Filled.Settings),
)

private val TopLevelRoutes = setOf("event_list", "stats", "member_manage", "ai_config")

@Composable
private fun CocWarNavHost() {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "event_list"
    val showBottomBar = currentRoute in TopLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == "event_list") {
                FloatingActionButton(onClick = { nav.navigate("import") }) {
                    Icon(Icons.Filled.Add, contentDescription = "导入战报")
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
                    onSync = { nav.navigate("sync") },
                    onAiImport = { nav.navigate("ai_import") },
                )
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
            composable("ai_config") {
                AiConfigScreen(onBack = { nav.popBackStack() })
            }
            composable("ai_import") {
                AiImportScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
        }
    }
}
