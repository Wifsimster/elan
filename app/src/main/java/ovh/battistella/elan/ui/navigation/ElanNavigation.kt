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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.font.FontWeight
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Elevation
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.ui.screens.history.HistoryScreen
import ovh.battistella.elan.ui.screens.home.HomeScreen
import ovh.battistella.elan.ui.screens.home.RestoreSheet
import ovh.battistella.elan.ui.screens.outing.OutingScreen
import ovh.battistella.elan.ui.screens.catalog.CatalogScreen
import ovh.battistella.elan.ui.screens.exercise.ExerciseScreen
import ovh.battistella.elan.ui.screens.health.HealthRationaleScreen
import ovh.battistella.elan.ui.screens.progression.ProgressionScreen
import ovh.battistella.elan.ui.screens.session.SessionDetailScreen
import ovh.battistella.elan.ui.screens.session.SessionMapScreen
import ovh.battistella.elan.ui.screens.settings.SettingsScreen
import ovh.battistella.elan.ui.screens.strength.StrengthScreen
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
 * [pendingRoute] est une route demandée de l'extérieur (notification de séance,
 * lien profond) : ouverte dès qu'elle est fournie, puis signalée consommée
 * par [onPendingRouteConsumed] pour ne pas être rejouée.
 */
@Composable
fun ElanRoot(
    snackbar: SnackbarController = remember { SnackbarController() },
    viewModelFactory: ViewModelProvider.Factory? = null,
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            // `launchSingleTop` : revenir à la sortie déjà ouverte plutôt que l'empiler.
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onPendingRouteConsumed()
        }
    }
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
            composable(Routes.HOME) { entry ->
                HomeScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onStartOuting = { entry.ifResumed { navController.navigate(Routes.outing(it)) } },
                    onStartMuscu = { entry.ifResumed { navController.navigate(Routes.muscu(it)) } },
                    onOpenCatalog = { entry.ifResumed { navController.navigate(Routes.CATALOG) } },
                    onOpenProgression = { entry.ifResumed { navController.navigate(Routes.PROGRESSION) } },
                    onOpenHistory = { entry.ifResumed { navController.navigateToTab(Routes.HISTORY) } },
                    onOpenSession = { entry.ifResumed { navController.navigate(Routes.session(it)) } },
                    onOpenSettings = { entry.ifResumed { navController.navigateToTab(Routes.SETTINGS) } },
                    restoreSheet = { onCancel, onRestored ->
                        RestoreSheet(onCancel = onCancel, onRestored = onRestored, viewModel = screenViewModel(viewModelFactory))
                    },
                )
            }
            composable(Routes.HISTORY) { entry ->
                HistoryScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onOpenSession = { entry.ifResumed { navController.navigate(Routes.session(it)) } },
                    onOpenProgression = { entry.ifResumed { navController.navigate(Routes.PROGRESSION) } },
                    onOpenSettings = { entry.ifResumed { navController.navigateToTab(Routes.SETTINGS) } },
                )
            }
            composable(Routes.SETTINGS) { entry ->
                SettingsScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    sensorsViewModel = screenViewModel(viewModelFactory),
                    backupViewModel = screenViewModel(viewModelFactory),
                    onOpenWeight = { entry.ifResumed { navController.navigate(Routes.WEIGHT) } },
                )
            }

            // Écrans live : plein écran, fondu (modal `fade` d'origine).
            composable(
                route = Routes.OUTING,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
                enterTransition = { fadeIn() },
            ) { entry ->
                OutingScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onExit = { entry.ifResumed { navController.popBackStack() } },
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
                arguments = listOf(
                    navArgument("template") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("add") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
                enterTransition = { fadeIn() },
            ) { entry ->
                StrengthScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onExit = { entry.ifResumed { navController.popBackStack() } },
                    // `router.replace(détail)` : la séance quitte la pile.
                    onSaved = { id ->
                        navController.navigate(Routes.session(id)) {
                            popUpTo(Routes.MUSCU) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.SESSION,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                SessionDetailScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onOpenMap = { entry.ifResumed { navController.navigate(Routes.sessionMap(it)) } },
                    onOpenExercise = { entry.ifResumed { navController.navigate(Routes.exercise(it)) } },
                )
            }
            composable(
                route = Routes.SESSION_MAP,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                SessionMapScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                )
            }
            composable(Routes.WEIGHT) { entry ->
                WeightScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                )
            }
            composable(Routes.PROGRESSION) { entry ->
                ProgressionScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onOpenExercise = { entry.ifResumed { navController.navigate(Routes.exercise(it)) } },
                    onStartMuscu = { entry.ifResumed { navController.navigate(Routes.muscu()) } },
                )
            }
            composable(Routes.HEALTH_RATIONALE) { entry ->
                HealthRationaleScreen(contentPadding = innerPadding, onBack = { entry.ifResumed { navController.popBackStack() } })
            }
            composable(Routes.CATALOG) { entry ->
                CatalogScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onAddToSession = { entry.ifResumed { navController.navigate(Routes.muscuAdd(it)) } },
                )
            }
            composable(
                route = Routes.EXERCISE,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                ExerciseScreen(
                    contentPadding = innerPadding,
                    viewModel = screenViewModel(viewModelFactory),
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onOpenSession = { entry.ifResumed { navController.navigate(Routes.session(it)) } },
                )
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
    val colors = ElanTheme.colors
    // Onglet actif : pastille Volt sous encre sombre, libellé à l'encre
    // principale ; les autres restent en retrait. Barre tonale, sans ombre.
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = colors.brand,
        selectedIconColor = colors.onBrand,
        selectedTextColor = colors.text,
        unselectedIconColor = colors.textMuted,
        unselectedTextColor = colors.textMuted,
    )
    NavigationBar(containerColor = colors.backgroundElement, tonalElevation = Elevation.none) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navController.navigateToTab(dest.route) },
                // Le libellé est affiché en texte dessous ; null évite que
                // TalkBack le lise deux fois.
                colors = itemColors,
                icon = { Icon(painterResource(dest.icon), contentDescription = null) },
                label = { Text(stringResource(dest.labelRes), style = ElanType.micro.copy(fontWeight = FontWeight.Bold)) },
            )
        }
    }
}

/**
 * N'exécute une navigation que si l'écran est au premier plan : un double
 * appui (ou un événement émis pendant la transition de sortie) dépilerait
 * deux fois — NavHost vide — ou empilerait deux fois le même écran.
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
