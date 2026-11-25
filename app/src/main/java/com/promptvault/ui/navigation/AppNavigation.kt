package com.promptvault.ui.navigation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptvault.R
import com.promptvault.domain.ContentType
import com.promptvault.domain.ItemFilter
import com.promptvault.ui.screens.AboutScreen
import com.promptvault.ui.screens.CategoriesScreen
import com.promptvault.ui.screens.DownloadsScreen
import com.promptvault.ui.screens.EditorScreen
import com.promptvault.ui.screens.FoldersScreen
import com.promptvault.ui.screens.HomeScreen
import com.promptvault.ui.screens.ListScreen
import com.promptvault.ui.screens.NewContentSheet
import com.promptvault.ui.screens.NewContentType
import com.promptvault.ui.screens.SearchScreen
import com.promptvault.ui.screens.SettingsScreen
import com.promptvault.ui.screens.SyncScreen
import com.promptvault.ui.screens.TelegramScreen
import kotlinx.coroutines.launch

object Screen {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EDITOR = "editor/{itemId}?type={type}"
    const val LIST = "list/{filter}"
    const val CATEGORIES = "categories"
    const val FOLDERS = "folders?parent={parent}&create={create}"
    const val DOWNLOADS = "downloads"
    const val TELEGRAM = "telegram"
    const val SYNC = "sync"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun editorRoute(itemId: String, type: ContentType = ContentType.PROMPT): String =
        "editor/$itemId?type=${type.name}"

    fun listRoute(filter: ItemFilter): String = "list/${filter.name}"

    fun foldersRoute(parentId: String? = null, create: Boolean = false): String =
        "folders?parent=${parentId.orEmpty()}&create=$create"

    data class DrawerItem(val route: String, val label: String, val icon: ImageVector)

    val drawerItems = listOf(
        DrawerItem(HOME, "Recent", Icons.Filled.List),
        DrawerItem(listRoute(ItemFilter.ALL), "All Prompts", Icons.Filled.AutoAwesome),
        DrawerItem(listRoute(ItemFilter.FAVORITES), "Favorites", Icons.Filled.Favorite),
        DrawerItem(CATEGORIES, "Categories", Icons.Filled.Category),
        DrawerItem(FOLDERS, "Folders", Icons.Filled.Folder),
        DrawerItem(listRoute(ItemFilter.TEXT), "Text Documents", Icons.Filled.Description),
        DrawerItem(listRoute(ItemFilter.CODE), "Code Snippets", Icons.Filled.Code),
        DrawerItem(DOWNLOADS, "Downloads", Icons.Filled.FileDownload),
        DrawerItem(TELEGRAM, "Telegram Cloud", Icons.Filled.Cloud),
        DrawerItem(SYNC, "Sync Status", Icons.Filled.Sync),
        DrawerItem(SETTINGS, "Settings", Icons.Filled.Settings),
        DrawerItem(ABOUT, "About", Icons.Filled.Info)
    )
}

@Composable
fun PromptVaultNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val rawRoute = backStack?.destination?.route.orEmpty()
    val selectedRoute = normalizeRoute(rawRoute)
    var showNewSheet by remember { mutableStateOf(false) }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun navigate(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
        closeDrawer()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("PromptVault", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                Screen.drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, null) },
                        selected = selectedRoute == normalizeRoute(item.route),
                        onClick = { navigate(item.route) }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, modifier = Modifier.size(18.dp))
                    Text(" Local-first • Telegram ready", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.HOME) {
            composable(Screen.HOME) {
                HomeScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = { navigate(Screen.SEARCH) },
                    onNewContent = { showNewSheet = true },
                    onOpenItem = { id -> navigate(Screen.editorRoute(id)) },
                    onNavigate = { route -> navigate(route) }
                )
            }
            composable(Screen.SEARCH) {
                SearchScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenItem = { id -> navigate(Screen.editorRoute(id)) },
                    onNavigate = { route -> navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Screen.EDITOR,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ContentType.PROMPT.name
                    }
                )
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId") ?: "new"
                val type = runCatching {
                    ContentType.valueOf(entry.arguments?.getString("type") ?: ContentType.PROMPT.name)
                }.getOrDefault(ContentType.PROMPT)
                EditorScreen(
                    itemId = itemId,
                    initialType = type,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        navigate(Screen.editorRoute(id, type))
                    },
                    onDeleted = { navController.popBackStack() }
                )
            }
            composable(
                Screen.LIST,
                arguments = listOf(navArgument("filter") { type = NavType.StringType })
            ) { entry ->
                val filter = runCatching {
                    ItemFilter.valueOf(entry.arguments?.getString("filter") ?: ItemFilter.ALL.name)
                }.getOrDefault(ItemFilter.ALL)
                ListScreen(
                    filter = filter,
                    onNewContent = { showNewSheet = true },
                    onOpenItem = { id -> navigate(Screen.editorRoute(id)) },
                    onNavigate = { route -> navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.FOLDERS,
                arguments = listOf(
                    navArgument("parent") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("create") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val parent = entry.arguments?.getString("parent")?.takeIf { it.isNotBlank() }
                val create = entry.arguments?.getBoolean("create") ?: false
                FoldersScreen(
                    parentId = parent,
                    autoCreate = create,
                    onNavigateFolder = { id -> navigate(Screen.foldersRoute(id)) },
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id -> navigate(Screen.editorRoute(id)) }
                )
            }
            composable(Screen.DOWNLOADS) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.TELEGRAM) {
                TelegramScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SYNC) {
                SyncScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navigate(route) }
                )
            }
            composable(Screen.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showNewSheet) {
            NewContentSheet(
                onDismiss = { showNewSheet = false },
                onSelect = { type ->
                    showNewSheet = false
                    when (type) {
                        NewContentType.PROMPT -> navigate(Screen.editorRoute("new", ContentType.PROMPT))
                        NewContentType.TEXT -> navigate(Screen.editorRoute("new", ContentType.TEXT))
                        NewContentType.CODE -> navigate(Screen.editorRoute("new", ContentType.CODE))
                        NewContentType.FOLDER -> navigate(Screen.foldersRoute(create = true))
                    }
                }
            )
        }
    }
}

private fun normalizeRoute(route: String): String = when {
    route.startsWith("list/") -> Screen.LIST
    route.startsWith("editor/") -> Screen.EDITOR
    route.startsWith("folders") -> Screen.FOLDERS
    else -> route
}

private fun unusedNavControllerReference(controller: NavHostController) = controller
