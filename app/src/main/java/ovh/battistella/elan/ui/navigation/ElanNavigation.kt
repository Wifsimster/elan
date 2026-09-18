package ovh.battistella.elan.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.ui.screens.history.HistoryScreen
import ovh.battistella.elan.ui.screens.home.HomeScreen
import ovh.battistella.elan.ui.screens.settings.SettingsScreen

/**
 * Racine de l'interface : un seul [Scaffold] (hôte de snackbar partagé, barre
 * de navigation) autour du [NavHost]. Les écrans reçoivent `contentPadding`
 * et des callbacks, jamais le `NavController`.
 *
 * [snackbar] est le singleton Hilt injecté par MainActivity ; le défaut local
 * ne sert qu'aux tests et aux aperçus, où aucun émetteur n'existe.
 */
@Composable
fun ElanRoot(
    snackbar: SnackbarController = remember { SnackbarController() },
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Un seul hôte de snackbar rend les messages transitoires — y compris les
    // actions « Annuler » des suppressions réversibles — depuis n'importe où.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbar) {
        snackbar.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = if (message.isUndo) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = message.actionLabel == null,
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    Scaffold(
        // Dégage aussi l'encoche pour que le contenu ne passe pas sous un
        // notch latéral en paysage (edge-to-edge actif).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomBar(navController, currentDestination) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            // Fondu discret entre les onglets ; les ressorts par défaut
            // reprennent le motion scheme expressif du thème.
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) { HomeScreen(contentPadding = innerPadding) }
            composable(Routes.HISTORY) { HistoryScreen(contentPadding = innerPadding) }
            composable(Routes.SETTINGS) { SettingsScreen(contentPadding = innerPadding) }
        }
    }
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                // Le libellé est affiché en texte dessous ; null évite que
                // TalkBack le lise deux fois.
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}
