package me.thuanc177.kotsune.ui.screens

import MangaDexTrackingDashboard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.R
import me.thuanc177.kotsune.viewmodel.MangaDexTrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDexTrackingScreen(
    navController: NavController,
    viewModel: MangaDexTrackingViewModel,
    modifier: Modifier = Modifier
) {
    // Get relevant states from ViewModel
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Login form states
    val clientId by viewModel.clientId.collectAsState()
    val clientSecret by viewModel.clientSecret.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val showGuide by viewModel.showGuide.collectAsState()

    // User tracking states
    val userProfile by viewModel.userProfile.collectAsState()
    val selectedTab by viewModel.selectedLibraryTab.collectAsState()
    val filteredLibrary by viewModel.userLibrary.collectAsState()
    val isLibraryLoading by viewModel.isLibraryLoading.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    if (isLoggedIn) {
        // User is logged in, show tracking dashboard
        MangaDexTrackingDashboard(
            navController = navController,
            viewModel = viewModel,
            userProfile = userProfile,
            selectedTab = selectedTab,
            filteredLibrary = filteredLibrary,
            isLibraryLoading = isLibraryLoading,
            onLogout = {
                coroutineScope.launch {
                    viewModel.logout()
                }
            },
            onTabSelected = { viewModel.selectLibraryTab(it.toString()) },
            modifier = modifier,
        )
    } else {
        // User is not logged in, show login form
        var secretVisible by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MangaDex Tracking") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // MangaDex logo
                Image(
                    painter = painterResource(id = R.drawable.mangadex_icon),
                    contentDescription = "MangaDex Logo",
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .size(150.dp)
                )

                // Login form section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Sign in to MangaDex",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Input fields
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { viewModel.updateClientId(it) },
                            label = { Text("Client ID") },
                            placeholder = { Text("personal-client-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = loginState == MangaDexTrackingViewModel.LoginState.INVALID_CLIENT_ID,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Client ID"
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Client Secret field
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { viewModel.updateClientSecret(it) },
                            label = { Text("Client Secret") },
                            placeholder = { Text("Your client secret") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = loginState == MangaDexTrackingViewModel.LoginState.INVALID_CLIENT_SECRET,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Client Secret"
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { secretVisible = !secretVisible }) {
                                    Icon(
                                        imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (secretVisible) "Hide client secret" else "Show client secret"
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = { viewModel.updateUsername(it) },
                            label = { Text("Username") },
                            placeholder = { Text("Your MangaDex username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = loginState == MangaDexTrackingViewModel.LoginState.INVALID_USERNAME,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Username"
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = { Text("Password") },
                            placeholder = { Text("Your MangaDex password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    coroutineScope.launch {
                                        viewModel.login()
                                    }
                                }
                            ),
                            isError = loginState == MangaDexTrackingViewModel.LoginState.INVALID_PASSWORD,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password"
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            }
                        )

                        // Show error message if any
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Login button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.login()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading &&
                                    clientId.isNotEmpty() &&
                                    clientSecret.isNotEmpty() &&
                                    username.isNotEmpty() &&
                                    password.isNotEmpty()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Sign In")
                            }
                        }

                        // "Need help" button
                        TextButton(
                            onClick = { viewModel.toggleGuide() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Need help with client credentials?")
                        }
                    }
                }

                // Guide dialog
                if (showGuide) {
                    AlertDialog(
                        onDismissRequest = { viewModel.toggleGuide() },
                        title = { Text("How to get client credentials") },
                        text = {
                            Column {
                                Text(
                                    "1. Log in to your MangaDex account on the website")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "2. Go to Account Settings > API Clients")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "3. Create a new client with name 'Kotsune App'")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "4. Copy the Client ID and Secret to use here")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.openMangadexRegistration() },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Open MangaDex Settings")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.toggleGuide() }) {
                                Text("Got it")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Register link
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Don't have an account?")
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.openMangadexRegistration() }) {
                        Text("Register on MangaDex")
                    }
                }
            }
        }
    }
}

@Composable
fun MangaDexLoggedInView(
    username: String,
    onLogout: () -> Unit,
    onBackPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // MangaDex logo
        Image(
            painter = painterResource(id = R.drawable.mangadex_icon),
            contentDescription = "MangaDex Logo",
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Logged in",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Logged in to MangaDex",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Username: $username",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You can now track your manga reading progress and sync favorites with MangaDex.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onBackPressed,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
}

