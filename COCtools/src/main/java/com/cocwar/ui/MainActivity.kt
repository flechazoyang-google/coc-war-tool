package com.cocwar.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cocwar.CocWarApplication
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.ui.detail.EventDetailScreen
import com.cocwar.ui.eventlist.EventListScreen
import com.cocwar.ui.importflow.ImportScreen
import com.cocwar.ui.members.MemberManageScreen
import com.cocwar.ui.stats.StatsScreen
import com.cocwar.ui.sync.SyncScreen
import com.cocwar.ui.theme.CocWarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CocWarTheme {
                CocWarNavHost()
            }
        }
    }
}

@Composable
private fun CocWarNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as CocWarApplication
    val repo = application.repository

    // 打开软件时自动识别剪切板中的战报 JSON，识别到就弹确认对话框
    var clipboardParsed by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }

    LaunchedEffect(Unit) {
        val cm = application.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        if (clipText.isNotBlank() && looksLikeWarJson(clipText)) {
            when (val r = WarJsonParser.parse(clipText)) {
                is WarJsonParser.ParseResult.Success -> clipboardParsed = r.data
                is WarJsonParser.ParseResult.Error -> Unit
            }
        }
    }

    androidx.navigation.compose.NavHost(
        navController = nav,
        startDestination = "event_list"
    ) {
        composable("event_list") {
            EventListScreen(
                onImport = { nav.navigate("import") },
                onOpen = { id -> nav.navigate("detail/$id") },
                onStats = { nav.navigate("stats") },
                onMembers = { nav.navigate("member_manage") },
                onSync = { nav.navigate("sync") }
            )
        }
        composable("import") {
            ImportScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() }
            )
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
        composable(
            "detail/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("eventId") ?: ""
            EventDetailScreen(eventId = id, onBack = { nav.popBackStack() })
        }
    }

    clipboardParsed?.let { parsed ->
        ClipboardImportDialog(
            parsed = parsed,
            repo = repo,
            onSaved = { id ->
                clipboardParsed = null
                nav.navigate("detail/$id")
            },
            onDismiss = { clipboardParsed = null }
        )
    }
}
