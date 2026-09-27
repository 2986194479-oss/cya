package com.viva.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viva.downloader.ui.browse.BrowseScreen
import com.viva.downloader.ui.browse.BrowseViewModel
import com.viva.downloader.ui.detail.DetailScreen
import com.viva.downloader.ui.detail.DetailViewModel
import com.viva.downloader.ui.login.LoginScreen
import com.viva.downloader.ui.login.LoginViewModel
import com.viva.downloader.ui.theme.VivaTheme

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Browse : Screen("browse", "浏览", Icons.Default.List)
    object Detail : Screen("detail", "提取", Icons.Default.Search)
    object Login : Screen("login", "登录", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VivaTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val browseViewModel: BrowseViewModel = viewModel()
    val detailViewModel: DetailViewModel = viewModel()
    val loginViewModel: LoginViewModel = viewModel()

    val showBottomBar = currentRoute == Screen.Browse.route || currentRoute == Screen.Detail.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFFF6F1E5)) {
                    listOf(Screen.Browse, Screen.Detail).forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Browse.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Browse.route) {
                BrowseScreen(
                    viewModel = browseViewModel,
                    onOpenDiscussion = { discussion ->
                        detailViewModel.onOpenDiscussion(discussion.id, discussion.title)
                        navController.navigate(Screen.Detail.route)
                    },
                    onOpenLogin = { navController.navigate(Screen.Login.route) },
                )
            }
            composable(Screen.Detail.route) {
                DetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    viewModel = loginViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
