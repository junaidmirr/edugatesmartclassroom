package com.judev.edugate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.judev.edugate.auth.AuthState
import com.judev.edugate.auth.AuthViewModel
import com.judev.edugate.live.LiveStreamScreen
import com.judev.edugate.navigation.Screen
import com.judev.edugate.smartboard.SmartBoardScreen
import com.judev.edugate.student.StudentDashboardScreen
import com.judev.edugate.teacher.ClassroomDetailsScreen
import com.judev.edugate.teacher.JoinRequestsScreen
import com.judev.edugate.teacher.TeacherDashboardScreen
import com.judev.edugate.ui.theme.EdugateTheme
import com.judev.edugate.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val isDarkModeState by themeViewModel.isDarkMode
            val isDark = isDarkModeState ?: isSystemInDarkTheme()

            EdugateTheme(isDarkTheme = isDark) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showSettingsDialog by remember { mutableStateOf(false) }
                val userRole by authViewModel.userRole.collectAsState()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.background,
                            drawerTonalElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.School, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("EDUGATE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            
                            val navItemModifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)

                            if (userRole == "teacher") {
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                    label = { Text("JOIN REQUESTS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                                    selected = false,
                                    onClick = { 
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.JoinRequests.route)
                                    },
                                    modifier = navItemModifier,
                                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                                )
                            }

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("SETTINGS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                                selected = false,
                                onClick = { 
                                    scope.launch { drawerState.close() }
                                    showSettingsDialog = true 
                                },
                                modifier = navItemModifier,
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )

                            // --- Side Menu Sections ---
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("ABOUT US", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                                selected = false,
                                onClick = { scope.launch { drawerState.close() }; navController.navigate("about") },
                                modifier = navItemModifier,
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("TERMS & POLICY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                                selected = false,
                                onClick = { scope.launch { drawerState.close() }; navController.navigate("terms") },
                                modifier = navItemModifier,
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )
                            
                            Spacer(Modifier.weight(1f))

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                label = { Text("LOGOUT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                                selected = false,
                                onClick = { 
                                    scope.launch { drawerState.close() }
                                    authViewModel.logout {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                },
                                modifier = navItemModifier.padding(bottom = 24.dp),
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )
                        }
                    },
                    gesturesEnabled = true
                ) {
                    if (showSettingsDialog) {
                        SettingsDialog(
                            currentIsDark = isDarkModeState,
                            onDismiss = { showSettingsDialog = false },
                            onThemeChange = { themeViewModel.toggleTheme(it) }
                        )
                    }

                    NavHost(navController = navController, startDestination = Screen.Splash.route) {
                        composable(Screen.Splash.route) {
                            AnimatedSplashScreen { route ->
                                navController.navigate(route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.Login.route) {
                            LoginScreen(
                                onLoginSuccess = { role ->
                                    val route = if (role == "teacher") Screen.TeacherDashboard.route else Screen.StudentDashboard.route
                                    navController.navigate(route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
                            )
                        }
                        composable(Screen.Register.route) {
                            RegisterScreen(
                                onRegisterSuccess = { role ->
                                    val route = if (role == "teacher") Screen.TeacherDashboard.route else Screen.StudentDashboard.route
                                    navController.navigate(route) {
                                        popUpTo(Screen.Register.route) { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
                            )
                        }
                        composable(Screen.TeacherDashboard.route) {
                            AppScaffold(
                                title = "DASHBOARD",
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onProfileClick = { navController.navigate(Screen.Profile.route) }
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    TeacherDashboardScreen(onClassroomClick = { classId ->
                                        navController.navigate(Screen.ClassroomDetails.createRoute(classId))
                                    })
                                }
                            }
                        }
                        composable(Screen.StudentDashboard.route) {
                            AppScaffold(
                                title = "DASHBOARD",
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onProfileClick = { navController.navigate(Screen.Profile.route) }
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    StudentDashboardScreen(onClassroomClick = { classId ->
                                        navController.navigate(Screen.ClassroomDetails.createRoute(classId))
                                    })
                                }
                            }
                        }
                        composable(
                            route = Screen.ClassroomDetails.route,
                            arguments = listOf(navArgument("classId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val classId = backStackEntry.arguments?.getString("classId") ?: ""
                            ClassroomDetailsScreen(
                                classId = classId, 
                                onBack = { navController.popBackStack() },
                                onSmartBoardClick = { id -> navController.navigate(Screen.SmartBoard.createRoute(id)) },
                                onLiveStreamClick = { id, role, type -> 
                                    navController.navigate(Screen.LiveStream.createRoute(id, role, type))
                                },
                                userRole = userRole ?: "student"
                            )
                        }
                        composable(Screen.JoinRequests.route) {
                            JoinRequestsScreen(onBack = { 
                                navController.popBackStack()
                            })
                        }
                        composable(
                            route = Screen.SmartBoard.route,
                            arguments = listOf(navArgument("classId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val classId = backStackEntry.arguments?.getString("classId") ?: ""
                            SmartBoardScreen(
                                classId = classId, 
                                onBack = { navController.popBackStack() },
                                userRole = userRole ?: "student"
                            )
                        }
                        composable(
                            route = Screen.LiveStream.route,
                            arguments = listOf(
                                navArgument("classId") { type = NavType.StringType },
                                navArgument("role") { type = NavType.StringType },
                                navArgument("type") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val classId = backStackEntry.arguments?.getString("classId") ?: ""
                            val role = backStackEntry.arguments?.getString("role") ?: "student"
                            val type = backStackEntry.arguments?.getString("type") ?: "camera"
                            LiveStreamScreen(
                                classId = classId,
                                role = role,
                                type = type,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(onBack = { navController.popBackStack() }, onLogout = {
                                authViewModel.logout {
                                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                                }
                            })
                        }
                        composable("about") { SimpleInfoScreen("ABOUT US", "EduGate is a professional modern classroom management and live teaching platform. Developed to bridge the gap between teachers and students globally.", onBack = { navController.popBackStack() }) }
                        composable("terms") { SimpleInfoScreen("TERMS & POLICY", "1. User Privacy: Your data is secure with us.\n2. Respect: Maintain a professional environment.\n3. Content: Teachers own their lecture notes.\n\nPrivacy Policy: We do not share your profile with 3rd parties.", onBack = { navController.popBackStack() }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleInfoScreen(title: String, content: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text(content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(32.dp))
            Text("CONTACT US", fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("support@edugate.com", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, authViewModel: AuthViewModel = viewModel()) {
    val userRole by authViewModel.userRole.collectAsState()
    val profilePicUrl by authViewModel.profilePicUrl.collectAsState()
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { authViewModel.uploadProfilePicture(context, it) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MY PROFILE", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(profilePicUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(32.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(user?.email?.uppercase() ?: "USER@EMAIL.COM", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(userRole?.uppercase() ?: "STUDENT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("LOGOUT", fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { authViewModel.deleteAccount { onLogout() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Red),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("DELETE ACCOUNT", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentIsDark: Boolean?,
    onDismiss: () -> Unit,
    onThemeChange: (Boolean?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SETTINGS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text("APPEARANCE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = currentIsDark == false, onClick = { onThemeChange(false) })
                    Text("Light Mode", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = currentIsDark == true, onClick = { onThemeChange(true) })
                    Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = currentIsDark == null, onClick = { onThemeChange(null) })
                    Text("System Default", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(0.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(title, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        content = content
    )
}

@Composable
fun AnimatedSplashScreen(authViewModel: AuthViewModel = viewModel(), onNavigation: (String) -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(1500)
        authViewModel.checkUserStatus(onNavigation)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .scale(scaleAnim.value),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "EDUGATE",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp
            )
        }
    }
}

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by authViewModel.authState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                authViewModel.signInWithGoogle(token) { /* Handled via state */ }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    LaunchedEffect(state) {
        if (state is AuthState.Success) {
            onLoginSuccess((state as AuthState.Success).role)
        }
    }

    if (state is AuthState.GoogleRoleSelection) {
        GoogleRoleDialog(onRoleSelected = { authViewModel.completeGoogleRegistration(it) })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LOGIN", fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Access your portal", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 16.sp)
        
        Spacer(Modifier.height(48.dp))
        
        EdugateTextField(
            value = email,
            onValueChange = { email = it },
            label = "EMAIL",
            icon = Icons.Default.AlternateEmail
        )
        Spacer(Modifier.height(16.dp))
        EdugateTextField(
            value = password,
            onValueChange = { password = it },
            label = "PASSWORD",
            icon = Icons.Default.Lock,
            isPassword = true
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = { authViewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("CONTINUE", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }

        Spacer(Modifier.height(16.dp))

        // GOOGLE SIGN IN BUTTON
        OutlinedButton(
            onClick = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("cb5caa174dbf4dadb9bc52396bac4a90.apps.googleusercontent.com") // Replace with yours
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                launcher.launch(client.signInIntent)
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(0.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(20.dp)) // Generic icon if logo resource is missing
            Spacer(Modifier.width(12.dp))
            Text("SIGN IN WITH GOOGLE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "DON'T HAVE AN ACCOUNT? REGISTER",
            modifier = Modifier.clickable { onNavigateToRegister() }.padding(8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state is AuthState.Error) {
            Spacer(Modifier.height(16.dp))
            Text((state as AuthState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GoogleRoleDialog(onRoleSelected: (String) -> Unit) {
    Dialog(onDismissRequest = {}) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(32.dp)) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("WELCOME", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("CHOOSE YOUR ROLE TO CONTINUE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { onRoleSelected("student") }, 
                    modifier = Modifier.fillMaxWidth().height(50.dp), 
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("I AM A STUDENT", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onRoleSelected("teacher") }, 
                    modifier = Modifier.fillMaxWidth().height(50.dp), 
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("I AM A TEACHER", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel = viewModel(),
    onRegisterSuccess: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    val state by authViewModel.authState.collectAsState()

    LaunchedEffect(state) {
        if (state is AuthState.Success) {
            onRegisterSuccess((state as AuthState.Success).role)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("JOIN", fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Create your account", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 16.sp)
        
        Spacer(Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { role = "student" }
                    .background(if (role == "student") MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("STUDENT", fontWeight = FontWeight.Black, color = if (role == "student") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { role = "teacher" }
                    .background(if (role == "teacher") MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("TEACHER", fontWeight = FontWeight.Black, color = if (role == "teacher") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(32.dp))
        
        EdugateTextField(
            value = email,
            onValueChange = { email = it },
            label = "EMAIL",
            icon = Icons.Default.AlternateEmail
        )
        Spacer(Modifier.height(16.dp))
        EdugateTextField(
            value = password,
            onValueChange = { password = it },
            label = "PASSWORD",
            icon = Icons.Default.Lock,
            isPassword = true
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = { authViewModel.register(email, password, role) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("REGISTER", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "ALREADY HAVE AN ACCOUNT? LOGIN",
            modifier = Modifier.clickable { onNavigateToLogin() }.padding(8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state is AuthState.Error) {
            Spacer(Modifier.height(16.dp))
            Text((state as AuthState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EdugateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false
) {
    Column {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            singleLine = true,
            shape = RoundedCornerShape(0.dp)
        )
    }
}
