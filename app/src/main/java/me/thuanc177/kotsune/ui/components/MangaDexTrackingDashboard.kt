import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.R
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaDexUserProfile
import me.thuanc177.kotsune.ui.components.LibraryTab
import me.thuanc177.kotsune.ui.components.MangaLibraryGrid
import me.thuanc177.kotsune.viewmodel.MangaDexTrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDexTrackingDashboard(
    navController: NavController,
    viewModel: MangaDexTrackingViewModel,
    userProfile: MangaDexUserProfile?,
    selectedTab: LibraryTab,
    filteredLibrary: List<MangaDexTrackingViewModel.MangaWithStatus>,
    isLibraryLoading: Boolean,
    onLogout: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val selectedItem = remember(selectedTab) {
        when (selectedTab) {
            LibraryTab.ALL -> 0
            LibraryTab.READING -> 1
            LibraryTab.PLAN_TO_READ -> 2
            LibraryTab.COMPLETED -> 3
            LibraryTab.ON_HOLD -> 4
            LibraryTab.RE_READING -> 5
            LibraryTab.DROPPED -> 6
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // User profile section at the top
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = userProfile?.avatarUrl ?: R.mipmap.default_mangadex_avatar,
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = userProfile?.username ?: "MangaDex User",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Logout button
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.padding(top = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Logout",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logout")
                        }
                    }
                }

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Navigation items
                NavigationDrawerItem(
                    label = { Text("Updates") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            // TODO: Navigate to Updates
                        }
                    },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Updates") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Library") },
                    selected = true, // Always selected as we're in library
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            // Already in Library
                        }
                    },
                    icon = { Icon(Icons.Default.Book, contentDescription = "Library") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("MDLists") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            // TODO: Navigate to MDLists
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = "MDLists") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("My Groups") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            // TODO: Navigate to My Groups
                        }
                    },
                    icon = { Icon(Icons.Default.Group, contentDescription = "My Groups") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Reading History") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            // TODO: Navigate to Reading History
                        }
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = "Reading History") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MangaDex Library") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // TODO: Show search
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // Library tabs for filtering
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedItem,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == LibraryTab.ALL,
                        onClick = { onTabSelected(LibraryTab.ALL) },
                        text = { Text("All") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.READING,
                        onClick = { onTabSelected(LibraryTab.READING) },
                        text = { Text("Reading") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.PLAN_TO_READ,
                        onClick = { onTabSelected(LibraryTab.PLAN_TO_READ) },
                        text = { Text("Plan to Read") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.COMPLETED,
                        onClick = { onTabSelected(LibraryTab.COMPLETED) },
                        text = { Text("Completed") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.ON_HOLD,
                        onClick = { onTabSelected(LibraryTab.ON_HOLD) },
                        text = { Text("On Hold") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.RE_READING,
                        onClick = { onTabSelected(LibraryTab.RE_READING) },
                        text = { Text("Re-reading") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.DROPPED,
                        onClick = { onTabSelected(LibraryTab.DROPPED) },
                        text = { Text("Dropped") }
                    )
                }

                // Library content
                if (isLibraryLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredLibrary.isEmpty()) {
                    EmptyLibraryMessage(selectedTab)
                } else {
                    MangaLibraryGrid(
                        mangaList = filteredLibrary,
                        onMangaClick = { mangaId ->
                            navController.navigate("manga_detail/$mangaId")
                        },
                        onStatusChange = { mangaId, newStatus ->
                            scope.launch {
                                viewModel.updateMangaStatus(mangaId, newStatus)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryMessage(tab: LibraryTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when(tab) {
                LibraryTab.ALL -> "Your library is empty"
                LibraryTab.READING -> "No manga in your reading list"
                LibraryTab.PLAN_TO_READ -> "No manga in your plan to read list"
                LibraryTab.COMPLETED -> "No completed manga"
                LibraryTab.ON_HOLD -> "No manga on hold"
                LibraryTab.RE_READING -> "No manga you're re-reading"
                LibraryTab.DROPPED -> "No dropped manga"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add manga to your library by marking its reading status",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}