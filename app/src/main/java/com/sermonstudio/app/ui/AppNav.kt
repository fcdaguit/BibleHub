package com.sermonstudio.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sermonstudio.app.R
import com.sermonstudio.app.ui.assistant.AssistantScreen
import com.sermonstudio.app.ui.bible.BibleScreen
import com.sermonstudio.app.ui.editor.EditorScreen
import com.sermonstudio.app.ui.notes.NotesScreen
import com.sermonstudio.app.ui.search.SearchScreen
import com.sermonstudio.app.ui.sermons.SermonListScreen
import com.sermonstudio.app.ui.settings.SettingsScreen
import com.sermonstudio.app.ui.settings.VersionsScreen

private data class TopDest(val route: String, val label: Int, val icon: ImageVector)

private val topDests = listOf(
    TopDest("sermons", R.string.nav_sermons, Icons.Default.Description),
    TopDest("bible", R.string.nav_bible, Icons.Default.MenuBook),
    TopDest("search", R.string.nav_search, Icons.Default.Search),
    TopDest("assistant", R.string.nav_assistant, Icons.Default.AutoAwesome),
    TopDest("notes", R.string.nav_notes, Icons.Default.EditNote),
)

/**
 * App shell. Phones (< 600dp wide) get a bottom navigation bar; tablets and landscape get a
 * navigation rail. The sermon editor and settings pages are full screen (no navigation chrome).
 */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentRoute = destination?.route?.substringBefore('?')
    val showNav = currentRoute in topDests.map { it.route }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (showNav && !wide) NavigationBar {
                topDests.forEach { d ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route?.substringBefore('?') == d.route } == true,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(stringResource(d.label)) },
                    )
                }
            }
        },
    ) { pad ->
        Row(Modifier.padding(pad).fillMaxSize()) {
            if (showNav && wide) NavigationRail {
                topDests.forEach { d ->
                    NavigationRailItem(
                        selected = destination?.hierarchy?.any { it.route?.substringBefore('?') == d.route } == true,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(stringResource(d.label)) },
                    )
                }
            }
            NavHost(nav, startDestination = "sermons", modifier = Modifier.weight(1f)) {
                composable("sermons") {
                    SermonListScreen(onOpen = { nav.navigate("editor/$it") }, onSettings = { nav.navigate("settings") })
                }
                composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    EditorScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChapter = { b, c, v -> nav.navigate("bible?book=$b&chapter=$c&verse=$v") },
                    )
                }
                composable(
                    "bible?book={book}&chapter={chapter}&verse={verse}",
                    arguments = listOf(
                        navArgument("book") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("chapter") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("verse") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { BibleScreen(onSettings = { nav.navigate("settings") }) }
                composable("search") {
                    SearchScreen(
                        onOpenChapter = { b, c, v -> nav.navigate("bible?book=$b&chapter=$c&verse=$v") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("assistant") {
                    AssistantScreen(
                        onOpenSermon = { nav.navigate("editor/$it") },
                        onOpenChapter = { b, c, v -> nav.navigate("bible?book=$b&chapter=$c&verse=$v") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("notes") {
                    NotesScreen(
                        onOpenChapter = { b, c, v -> nav.navigate("bible?book=$b&chapter=$c&verse=$v") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() }, onVersions = { nav.navigate("versions") })
                }
                composable("versions") { VersionsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
