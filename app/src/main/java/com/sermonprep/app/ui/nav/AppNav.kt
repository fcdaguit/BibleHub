package com.sermonprep.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sermonprep.app.R
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.ui.assistant.AssistantScreen
import com.sermonprep.app.ui.bible.MarksScreen
import com.sermonprep.app.ui.bible.ReaderScreen
import com.sermonprep.app.ui.search.SearchScreen
import com.sermonprep.app.ui.sermons.SermonEditorScreen
import com.sermonprep.app.ui.sermons.SermonListScreen
import com.sermonprep.app.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Seeds the sample sermons the first time the app starts. */
@HiltViewModel
class RootViewModel @Inject constructor(sermons: SermonRepository) : ViewModel() {
    init { viewModelScope.launch { sermons.seedSamplesIfNeeded() } }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("sermons", R.string.tab_sermons, Icons.Filled.Description),
    Tab("reader/43/1/0", R.string.tab_bible, Icons.Filled.MenuBook),
    Tab("search", R.string.tab_search, Icons.Filled.Search),
    Tab("assistant", R.string.tab_assistant, Icons.Filled.AutoAwesome),
    Tab("settings", R.string.tab_settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    hiltViewModel<RootViewModel>()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBar = !route.startsWith("editor") && !route.startsWith("marks")

    fun openChapter(ref: BibleRef) =
        nav.navigate("reader/${ref.book}/${ref.chapter}/${ref.verseStart ?: 0}") { launchSingleTop = true }

    Scaffold(
        // Each screen's own Scaffold handles the status bar; avoids doubled insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    val base = tab.route.substringBefore('/')
                    NavigationBarItem(
                        selected = route.startsWith(base),
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "sermons", modifier = Modifier.padding(padding)) {
            composable("sermons") {
                SermonListScreen(onOpen = { nav.navigate("editor/$it") })
            }
            composable(
                "editor/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                SermonEditorScreen(onBack = { nav.popBackStack() }, onOpenChapter = ::openChapter)
            }
            composable(
                "reader/{book}/{chapter}/{verse}",
                arguments = listOf(
                    navArgument("book") { type = NavType.IntType },
                    navArgument("chapter") { type = NavType.IntType },
                    navArgument("verse") { type = NavType.IntType },
                ),
            ) {
                ReaderScreen(onOpenMarks = { nav.navigate("marks") }, onOpenSermon = { nav.navigate("editor/$it") })
            }
            composable("marks") {
                MarksScreen(onBack = { nav.popBackStack() }, onOpenChapter = ::openChapter)
            }
            composable("search") {
                SearchScreen(onOpenChapter = ::openChapter, onOpenSermon = { nav.navigate("editor/$it") })
            }
            composable("assistant") {
                AssistantScreen(onOpenChapter = ::openChapter, onOpenSermon = { nav.navigate("editor/$it") })
            }
            composable("settings") { SettingsScreen() }
        }
    }
}
