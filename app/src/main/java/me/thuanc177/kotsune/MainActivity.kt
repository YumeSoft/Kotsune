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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.navigation.AppNavigation
import me.thuanc177.kotsune.navigation.bottomNavItems
import me.thuanc177.kotsune.ui.theme.KotsuneTheme
import me.thuanc177.kotsune.viewmodel.TrackingViewModel
import me.thuanc177.kotsune.viewmodel.ViewModelContextProvider
import kotlin.text.get

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

        // Add this debug logging
        intent?.let {
            Log.d("MainActivity", "onCreate with intent action: ${it.action}")
            Log.d("MainActivity", "Intent data: ${it.data}")

            // Log all extras if any
            it.extras?.keySet()?.forEach { key ->
                Log.d("MainActivity", "Extra: $key = ${it.extras?.get(key)}")
            }
        }

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
                Log.d("MainActivity", "Handling deep link URI: $uri")

                if (uri.scheme == "kotsune") {
                    // Get your tracking view model
                    val trackingViewModel = ViewModelProvider(
                        this@MainActivity,
                        TrackingViewModel.Factory(anilistClient)
                    )[TrackingViewModel::class.java]

                    val success = trackingViewModel.handleOAuthRedirect(uri)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(
                                this@MainActivity,
                                "Successfully logged in to Anilist!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to authenticate with Anilist",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }}

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