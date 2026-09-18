package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.domain.CATALOG
import ovh.battistella.elan.domain.CATEGORIES
import ovh.battistella.elan.domain.CatalogExercise
import ovh.battistella.elan.domain.EQUIPMENTS
import ovh.battistella.elan.domain.Equipment
import ovh.battistella.elan.domain.ExerciseCategory
import ovh.battistella.elan.domain.RecoProfile
import ovh.battistella.elan.domain.exerciseHowTo
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.goalLabel
import ovh.battistella.elan.domain.recoHint
import ovh.battistella.elan.domain.recoWeightLabel
import ovh.battistella.elan.domain.recommend
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.roundToInt

/** Une section du catalogue : un rayon et ses exercices filtrés. */
data class CatalogSection(val category: ExerciseCategory, val items: List<CatalogExercise>)

/**
 * Filtre le catalogue par rayon, matériel et texte (nom ou muscle, sans
 * casse). Pur, extrait de l'écran pour être testable.
 */
fun filterCatalog(query: String, category: ExerciseCategory?, equipment: Equipment?): List<CatalogExercise> {
    val q = query.trim().lowercase()
    return CATALOG.filter { ex ->
        (category == null || ex.category == category) &&
            (equipment == null || equipment in ex.equipment) &&
            (q.isEmpty() || ex.name.lowercase().contains(q) || ex.muscles.any { it.lowercase().contains(q) })
    }
}

/** Regroupe par rayon (dans l'ordre des rayons), sans section vide. */
fun catalogSections(filtered: List<CatalogExercise>, category: ExerciseCategory?): List<CatalogSection> {
    val order = if (category != null) listOf(category) else CATEGORIES
    return order
        .map { cat -> CatalogSection(cat, filtered.filter { it.category == cat }) }
        .filter { it.items.isNotEmpty() }
}

/**
 * Magasin d'exercices (port de `exercise-catalog.tsx`) : recherche, filtres
 * rayon (teinte muscu) et matériel (accent) sur plusieurs lignes, compteur +
 * « Tout effacer », sections par rayon ; chaque ligne ouvre une fiche avec la
 * recommandation calculée pour [profile]. Occupe l'espace que lui donne son
 * parent (modale plein écran dans la séance, ou écran « Catalogue »).
 *
 * @param addedNames noms déjà dans la séance (pastille `check-circle`)
 * @param addLabel libellé du bouton d'ajout (défaut « Ajouter à la séance »)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseCatalog(
    profile: RecoProfile,
    onPick: (CatalogExercise) -> Unit,
    modifier: Modifier = Modifier,
    addedNames: Set<String> = emptySet(),
    addLabel: String = "Ajouter à la séance",
) {
    val colors = ElanTheme.colors
    var category by rememberSaveable { mutableStateOf<ExerciseCategory?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<CatalogExercise?>(null) }

    val filtered = remember(query, category, equipment) { filterCatalog(query, category, equipment) }
    val sections = remember(filtered, category) { catalogSections(filtered, category) }
    val hasFilters = category != null || equipment != null || query.isNotBlank()
    val clearAll = {
        category = null
        equipment = null
        query = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.fillMaxSize()) {
        SearchField(query = query, onQueryChange = { query = it })

        // Filtres : rayon. Enveloppe sur plusieurs lignes plutôt que de défiler
        // horizontalement, sinon les derniers libellés sortent de l'écran.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseChip(label = "Tous", selected = category == null, color = colors.muscu, onClick = { category = null })
            CATEGORIES.forEach { c ->
                PulseChip(
                    label = c.label,
                    selected = category == c,
                    color = colors.muscu,
                    onClick = { category = if (category == c) null else c },
                )
            }
        }
        // Filtres : matériel.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseChip(label = "Tout matériel", selected = equipment == null, color = colors.accent, onClick = { equipment = null })
            EQUIPMENTS.forEach { e ->
                PulseChip(
                    label = e.label,
                    selected = equipment == e,
                    color = colors.accent,
                    onClick = { equipment = if (equipment == e) null else e },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${filtered.size} exercice${if (filtered.size > 1) "s" else ""}",
                style = PulseType.caption,
                color = colors.textMuted,
            )
            if (hasFilters) {
                Text(
                    "Tout effacer",
                    style = PulseType.label,
                    color = colors.accent,
                    modifier = Modifier.pressableScale(onClick = clearAll).padding(4.dp),
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (sections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = MdiIcons.MagnifyClose,
                        tint = colors.muscu,
                        title = "Aucun exercice trouvé",
                        subtitle = if (query.isNotBlank()) "Aucun résultat pour « ${query.trim()} »."
                        else "Aucun exercice avec cette combinaison. Essaie d'élargir tes filtres.",
                        action = if (hasFilters) EmptyAction("Réinitialiser les filtres", MdiIcons.FilterRemove, clearAll) else null,
                    )
                }
            }
            sections.forEachIndexed { index, section ->
                item(key = "header-${section.category.name}") {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 8.dp),
                    ) {
                        Text(section.category.label.uppercase(), style = PulseType.overline, color = colors.textSecondary)
                        Text("${section.items.size}", style = PulseType.caption, color = colors.textMuted)
                    }
                }
                items(count = section.items.size, key = { section.items[it].id }) { i ->
                    val ex = section.items[i]
                    ExerciseRow(ex = ex, profile = profile, added = ex.name in addedNames, onClick = { detail = ex })
                }
            }
            item(key = "bottom") { Box(Modifier.height(16.dp)) }
        }
    }

    detail?.let { ex ->
        ExerciseDetailSheet(
            ex = ex,
            profile = profile,
            added = ex.name in addedNames,
            addLabel = addLabel,
            onAdd = {
                onPick(ex)
                detail = null
            },
            onClose = { detail = null },
        )
    }
}

/** Champ de recherche : loupe, texte, croix d'effacement. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background, shape)
            .border(1.dp, colors.border, shape)
            .clip(shape)
            .padding(horizontal = 12.dp),
    ) {
        Icon(painter = painterResource(MdiIcons.Magnify), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = PulseType.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
                .semantics { contentDescription = "Rechercher un exercice, un muscle" },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Rechercher un exercice, un muscle…", style = PulseType.body, color = colors.textMuted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(MdiIcons.CloseCircle),
                contentDescription = "Effacer la recherche",
                tint = colors.textMuted,
                modifier = Modifier.pressableScale(onClick = { onQueryChange("") }).padding(4.dp).size(18.dp),
            )
        }
    }
}

/** Une ligne du catalogue : pastille icône, nom (2 lignes), reco + charge teintée, 3 muscles, état ajouté. */
@Composable
private fun ExerciseRow(ex: CatalogExercise, profile: RecoProfile, added: Boolean, onClick: () -> Unit) {
    val colors = ElanTheme.colors
    val rec = remember(ex, profile) { recommend(profile, ex) }
    // Charge teintée seulement quand c'est un vrai poids ; gainage / poids du
    // corps restent en couleur secondaire.
    val weighted = !rec.timed && rec.weightKg > 0
    val shape = RoundedCornerShape(Radius.md)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(onClick = onClick)
            .background(colors.backgroundElement, shape)
            .border(1.dp, if (added) colors.muscu else colors.border, shape)
            .clip(shape)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp).background(colors.muscu.copy(alpha = 0.13f), RoundedCornerShape(Radius.sm)),
        ) {
            Icon(
                painter = painterResource(MdiIcons.byName(ex.icon) ?: MdiIcons.Dumbbell),
                contentDescription = null,
                tint = colors.muscu,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(ex.name, style = PulseType.subtitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${recoHint(ex, rec)} ·", style = PulseType.caption, color = colors.textSecondary, maxLines = 1)
                Text(
                    recoWeightLabel(rec),
                    style = PulseType.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (weighted) colors.muscu else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ex.muscles.isNotEmpty()) {
                Text(ex.muscles.take(3).joinToString(" · "), style = PulseType.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            painter = painterResource(if (added) MdiIcons.CheckCircle else MdiIcons.ChevronRight),
            contentDescription = if (added) "Déjà ajouté" else null,
            tint = if (added) colors.muscu else colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Fiche détaillée d'un exercice en feuille basse : illustration, nom,
 * matériel, bloc de recommandation (cible + charge, repos conseillé, phrase
 * poids/taille), muscles, exécution, et pied « Ajouter ».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(
    ex: CatalogExercise,
    profile: RecoProfile,
    added: Boolean,
    addLabel: String,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = ElanTheme.colors
    val rec = remember(ex, profile) { recommend(profile, ex) }
    val howTo = exerciseHowTo(ex.id)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Zone défilante bornée pour que le pied « Ajouter » reste toujours visible.
    val maxScroll = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = colors.backgroundElement,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .heightIn(max = maxScroll)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
            ) {
                ExerciseIllustration(imageKey = ex.imageKey, icon = ex.icon, height = 150.dp)
                Text(ex.name, style = PulseType.headline, color = colors.text)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Matériel".uppercase(), style = PulseType.overline, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ex.equipment.forEach { TagPill(label = it.label, color = colors.accent) }
                    }
                }

                // Recommandation personnalisée.
                val recoShape = RoundedCornerShape(Radius.md)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.muscu.copy(alpha = 0.08f), recoShape)
                        .border(1.dp, colors.muscu.copy(alpha = 0.2f), recoShape)
                        .padding(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(MdiIcons.Target), contentDescription = null, tint = colors.muscu, modifier = Modifier.size(18.dp))
                        Text(
                            "Conseillé · objectif ${goalLabel(profile.goal).lowercase()}".uppercase(),
                            style = PulseType.overline,
                            color = colors.muscu,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(recoHint(ex, rec), style = PulseType.metric, color = colors.text)
                        Text(recoWeightLabel(rec), style = PulseType.headline, color = colors.muscu, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text("Repos conseillé ~${rec.restSec} s entre les séries.", style = PulseType.caption, color = colors.textSecondary)
                    Text(
                        "D'après ton poids (${fmtKg(profile.weightKg)} kg), ta taille (${profile.heightCm.roundToInt()} cm) et ton objectif. " +
                            "Un point de départ — tu ajustes reps et charge à ta guise.",
                        style = PulseType.caption.copy(lineHeight = 17.sp),
                        color = colors.textMuted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Muscles ciblés".uppercase(), style = PulseType.overline, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ex.muscles.forEach { TagPill(label = it, color = colors.muscu) }
                    }
                }

                if (howTo != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exécution".uppercase(), style = PulseType.overline, color = colors.textMuted)
                        Text(howTo, style = PulseType.body, color = colors.text)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Box(Modifier.padding(16.dp).navigationBarsPadding()) {
                PulseButton(
                    title = if (added) "Ajouter à nouveau" else addLabel,
                    icon = if (added) MdiIcons.Plus else MdiIcons.PlusCircleOutline,
                    color = colors.muscu,
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
