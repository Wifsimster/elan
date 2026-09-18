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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.ui.screens.history.HistoryScreen
import ovh.battistella.elan.ui.screens.home.HomeScreen
import ovh.battistella.elan.ui.screens.outing.OutingScreen
import ovh.battistella.elan.ui.screens.placeholder.SoonScreen
import ovh.battistella.elan.ui.screens.session.SessionDetailScreen
import ovh.battistella.elan.ui.screens.session.SessionMapScreen
import ovh.battistella.elan.ui.screens.settings.SettingsScreen
import ovh.battistella.elan.ui.screens.weight.WeightScreen

/**
 * Racine de l'interface : un seul [Scaffold] (hôte de snackbar partagé, barre
 * de navigation sur les trois onglets seulement) autour du [NavHost]. Les
 * écrans reçoivent `contentPadding` et des callbacks, jamais le `NavController`.
 *
 * [snackbar] est le singleton Hilt injecté par MainActivity ; le défaut local
 * ne sert qu'aux tests et aux aperçus, où aucun émetteur n'existe.
 * [viewModelFactory] permet aux tests de construire les ViewModels sans Hilt
 * (`null` en production : `hiltViewModel()`).
 */
@Composable
fun ElanRoot(
    snackbar: SnackbarController = remember { SnackbarController() },
    viewModelFactory: ViewModelProvider.Factory? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // La barre d'onglets n'existe que sur les onglets : les écrans empilés
    // (sortie, muscu, détail, carte, poids…) sont plein écran, comme la pile
    // hors `(tabs)` de l'app d'origine.
    val showBottomBar = currentDestination?.hierarchy?.any { d -> TopLevelDestination.entries.any { it.route == d.route } } != false

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
        bottomBar = { if (showBottomBar) BottomBar(navController, currentDestination) },
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
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onStartOuting = { navController.navigate(Routes.outing(it)) },
                    onStartMuscu = { navController.navigate(Routes.muscu(it)) },
                    onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                    onOpenProgression = { navController.navigate(Routes.PROGRESSION) },
                    onOpenHistory = { navController.navigateToTab(Routes.HISTORY) },
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                    onOpenProgression = { navController.navigate(Routes.PROGRESSION) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(contentPadding = innerPadding) }

            // Écrans live : plein écran, fondu (modal `fade` d'origine).
            composable(
                route = Routes.OUTING,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
                enterTransition = { fadeIn() },
            ) {
                OutingScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onExit = { navController.popBackStack() },
                    // `router.replace(détail)` : la sortie quitte la pile.
                    onSaved = { id ->
                        navController.navigate(Routes.session(id)) {
                            popUpTo(Routes.OUTING) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.MUSCU,
                arguments = listOf(navArgument("template") { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { fadeIn() },
            ) {
                SoonScreen(
                    title = stringResource(R.string.muscu_title),
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.SESSION,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                SessionDetailScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenMap = { navController.navigate(Routes.sessionMap(it)) },
                    onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                )
            }
            composable(
                route = Routes.SESSION_MAP,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                SessionMapScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.WEIGHT) {
                WeightScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PROGRESSION) {
                SoonScreen(stringResource(R.string.progression_title), innerPadding) { navController.popBackStack() }
            }
            composable(Routes.CATALOG) {
                SoonScreen(stringResource(R.string.catalog_title), innerPadding) { navController.popBackStack() }
            }
            composable(
                route = Routes.EXERCISE,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                SoonScreen(entry.arguments?.getString("name") ?: "", innerPadding) { navController.popBackStack() }
            }
        }
    }
}

/**
 * ViewModel d'un écran : Hilt en production, fabrique fournie en test. La
 * fabrique reçoit les extras du `NavBackStackEntry` (dont le `SavedStateHandle`
 * avec les arguments de route).
 */
@Composable
private inline fun <reified VM : ViewModel> screenViewModel(factory: ViewModelProvider.Factory?): VM =
    if (factory == null) hiltViewModel() else viewModel(factory = factory)

/** Bascule d'onglet : pile sauvegardée / restaurée, une seule instance. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
                onClick = { if (!selected) navController.navigateToTab(dest.route) },
                // Le libellé est affiché en texte dessous ; null évite que
                // TalkBack le lise deux fois.
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

