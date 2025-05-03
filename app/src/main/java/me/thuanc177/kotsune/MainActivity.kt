package me.thuanc177.kotsune

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.navigation.AppNavigation
import me.thuanc177.kotsune.navigation.bottomNavItems
import me.thuanc177.kotsune.ui.theme.KotsuneTheme
import me.thuanc177.kotsune.viewmodel.ViewModelContextProvider

class MainActivity : ComponentActivity() {
    companion object {
        lateinit var appContext: android.content.Context
            private set
    }

    private val anilistClient: AnilistClient by lazy {
        AnilistClient(AppConfig.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize appContext for database operations
        appContext = applicationContext  // Use the Activity's applicationContext

        // Set context for ViewModelContextProvider
        ViewModelContextProvider.setContext(this)

        // Handle intent non-suspending way
        enableEdgeToEdge()
        setContent {
            KotsuneTheme {
                AppMainScreen()
            }
        }

        // Handle any incoming auth redirect in a coroutine
        intent?.let {
            handleIntentInBackground(it)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle callback from Anilist OAuth in a coroutine
        handleIntentInBackground(intent)
    }

    private fun handleIntentInBackground(intent: Intent?) {
        lifecycleScope.launch {
            intent?.data?.let { uri ->
                if (uri.toString().startsWith("kotsune://auth-callback")) {
                    val result = anilistClient.handleAuthRedirect(uri)
                    when (result) {
                        AnilistClient.AUTH_SUCCESS ->
                            Toast.makeText(this@MainActivity, "Successfully logged in to Anilist", Toast.LENGTH_SHORT).show()
                        AnilistClient.AUTH_ERROR ->
                            Toast.makeText(this@MainActivity, "Error during Anilist authentication", Toast.LENGTH_SHORT).show()
                        AnilistClient.AUTH_CANCELLED ->
                            Toast.makeText(this@MainActivity, "Anilist authentication cancelled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun AppMainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            // Show bottom bar only on top-level destinations
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    KotsuneTheme {
        AppMainScreen()
    }
}