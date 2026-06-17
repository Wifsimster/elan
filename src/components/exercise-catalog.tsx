import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Chip } from '@/components/chip';
import { EmptyState } from '@/components/empty-state';
import { ExerciseIllustration } from '@/components/exercise-illustration';
import { Gradient } from '@/components/gradient';
import { PressableScale } from '@/components/pressable-scale';
import { Gradients, Radius, Spacing, Type } from '@/constants/theme';
import {
  CATALOG,
  CATEGORIES,
  EQUIPMENTS,
  exerciseHowTo,
  goalLabel,
  recoHint,
  recommend,
  recoWeightLabel,
  type CatalogExercise,
  type Equipment,
  type ExerciseCategory,
  type RecoProfile,
} from '@/lib/exercises';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  /** Profil servant à calculer les recommandations (poids, taille, sexe, objectif). */
  profile: RecoProfile;
  /** Appelé quand l'utilisateur ajoute un exercice depuis le détail. */
  onPick: (ex: CatalogExercise) => void;
  /** Noms d'exercices déjà dans la séance (affiche une pastille « ajouté »). */
  addedNames?: string[];
  /** Libellé du bouton d'ajout (défaut « Ajouter à la séance »). */
  addLabel?: string;
};

const fmtCm = (v: number) => `${Math.round(v)} cm`;
const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

/**
 * Magasin d'exercices : recherche, filtres (groupe musculaire + matériel) et
 * liste, chaque entrée ouvrant une fiche détaillée (illustration, muscles,
 * exécution) avec un nombre de répétitions et une charge CONSEILLÉS pour le
 * profil de l'utilisateur. Présentationnel : occupe le `flex` que lui donne son
 * parent (feuille modale dans la séance, ou écran plein « Catalogue »).
 */
export function ExerciseCatalog({ profile, onPick, addedNames, addLabel }: Props) {
  const theme = useTheme();
  const [category, setCategory] = useState<ExerciseCategory | null>(null);
  const [equipment, setEquipment] = useState<Equipment | null>(null);
  const [query, setQuery] = useState('');
  const [detail, setDetail] = useState<CatalogExercise | null>(null);

  const added = useMemo(() => new Set(addedNames ?? []), [addedNames]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return CATALOG.filter(
      (ex) =>
        (!category || ex.category === category) &&
        (!equipment || ex.equipment.includes(equipment)) &&
        (!q ||
          ex.name.toLowerCase().includes(q) ||
          ex.muscles.some((m) => m.toLowerCase().includes(q))),
    );
  }, [category, equipment, query]);

  // Regroupe par rayon pour des en-têtes de section quand aucun groupe n'est filtré.
  const sections = useMemo(() => {
    const order = category ? [category] : CATEGORIES;
    return order
      .map((cat) => ({ cat, items: filtered.filter((e) => e.category === cat) }))
      .filter((s) => s.items.length > 0);
  }, [filtered, category]);

  const hasFilters = category !== null || equipment !== null || query.trim() !== '';
  const clearAll = () => {
    setCategory(null);
    setEquipment(null);
    setQuery('');
  };

  return (
    <View style={{ flex: 1, gap: Spacing.three }}>
      {/* Recherche */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: Spacing.two,
          backgroundColor: theme.background,
          borderRadius: Radius.sm,
          borderWidth: 1,
          borderColor: theme.border,
          paddingHorizontal: Spacing.three - Spacing.one,
        }}>
        <MaterialCommunityIcons name="magnify" size={20} color={theme.textMuted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Rechercher un exercice, un muscle…"
          placeholderTextColor={theme.textMuted}
          autoCapitalize="none"
          autoCorrect={false}
          style={{ flex: 1, color: theme.text, paddingVertical: Spacing.two + 2, ...Type.body }}
        />
        {query.length > 0 ? (
          <PressableScale onPress={() => setQuery('')} haptic="selection" hitSlop={10}>
            <MaterialCommunityIcons name="close-circle" size={18} color={theme.textMuted} />
          </PressableScale>
        ) : null}
      </View>

      {/* Filtres : groupe musculaire. Enveloppe sur plusieurs lignes plutôt que
          de défiler horizontalement : sinon les derniers libellés (Épaules,
          Bras, Gainage…) sortent de l'écran et restent illisibles. */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two }}>
        <Chip label="Tous" selected={category === null} color={theme.muscu} onPress={() => setCategory(null)} />
        {CATEGORIES.map((c) => (
          <Chip
            key={c}
            label={c}
            selected={category === c}
            color={theme.muscu}
            onPress={() => setCategory(category === c ? null : c)}
          />
        ))}
      </View>

      {/* Filtres : matériel (même logique d'enveloppe que ci-dessus). */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two }}>
        <Chip
          label="Tout matériel"
          selected={equipment === null}
          color={theme.accent}
          onPress={() => setEquipment(null)}
        />
        {EQUIPMENTS.map((e) => (
          <Chip
            key={e}
            label={e}
            selected={equipment === e}
            color={theme.accent}
            onPress={() => setEquipment(equipment === e ? null : e)}
          />
        ))}
      </View>

      {/* Barre de statut : nombre de résultats + raccourci pour tout réinitialiser. */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ ...Type.caption, color: theme.textMuted }}>
          {`${filtered.length} exercice${filtered.length > 1 ? 's' : ''}`}
        </Text>
        {hasFilters ? (
          <PressableScale onPress={clearAll} haptic="selection" hitSlop={8}>
            <Text style={{ ...Type.label, color: theme.accent }}>Tout effacer</Text>
          </PressableScale>
        ) : null}
      </View>

      {/* Liste */}
      <ScrollView
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        contentContainerStyle={{ paddingBottom: Spacing.three, gap: Spacing.three }}
        showsVerticalScrollIndicator={false}>
        {sections.length === 0 ? (
          <EmptyState
            icon="magnify-close"
            tint={theme.muscu}
            title="Aucun exercice trouvé"
            subtitle={
              query.trim()
                ? `Aucun résultat pour « ${query.trim()} ».`
                : "Aucun exercice avec cette combinaison. Essaie d'élargir tes filtres."
            }
            action={
              hasFilters
                ? { label: 'Réinitialiser les filtres', icon: 'filter-remove', onPress: clearAll }
                : undefined
            }
          />
        ) : null}

        {sections.map((section) => (
          <View key={section.cat} style={{ gap: Spacing.two }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={{ ...Type.overline, color: theme.textSecondary }}>{section.cat}</Text>
              <Text style={{ ...Type.caption, color: theme.textMuted }}>{section.items.length}</Text>
            </View>
            <View style={{ gap: Spacing.two }}>
              {section.items.map((ex) => (
                <ExerciseRow
                  key={ex.id}
                  ex={ex}
                  profile={profile}
                  added={added.has(ex.name)}
                  onPress={() => setDetail(ex)}
                  theme={theme}
                />
              ))}
            </View>
          </View>
        ))}
      </ScrollView>

      <ExerciseDetail
        ex={detail}
        profile={profile}
        added={detail ? added.has(detail.name) : false}
        addLabel={addLabel ?? 'Ajouter à la séance'}
        onAdd={(ex) => {
          onPick(ex);
          setDetail(null);
        }}
        onClose={() => setDetail(null)}
      />
    </View>
  );
}

/** Une ligne du catalogue : icône, nom, muscles, et la cible/charge conseillées. */
function ExerciseRow({
  ex,
  profile,
  added,
  onPress,
  theme,
}: {
  ex: CatalogExercise;
  profile: RecoProfile;
  added: boolean;
  onPress: () => void;
  theme: ReturnType<typeof useTheme>;
}) {
  const rec = recommend(profile, ex);
  // Charge mise en avant en pastille seulement quand c'est un vrai poids ; pour
  // le gainage (chrono) ou le poids du corps, le libellé reste sur la ligne reco.
  const weighted = !rec.timed && rec.weightKg > 0;
  return (
    <PressableScale
      onPress={onPress}
      haptic="selection"
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: Spacing.three - Spacing.one,
        padding: Spacing.three - Spacing.one,
        borderRadius: Radius.md,
        borderCurve: 'continuous',
        backgroundColor: theme.backgroundElement,
        borderWidth: 1,
        borderColor: added ? theme.muscu : theme.border,
      }}>
      <View
        style={{
          width: 42,
          height: 42,
          borderRadius: Radius.sm,
          borderCurve: 'continuous',
          backgroundColor: theme.muscu + '22',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
        <MaterialCommunityIcons
          name={ex.icon as keyof typeof MaterialCommunityIcons.glyphMap}
          size={22}
          color={theme.muscu}
        />
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        {/* Nom sur 2 lignes max : les variantes longues (« … roumain haltères »
            vs « … roumain barre ») doivent rester distinguables. */}
        <Text style={{ ...Type.subtitle, color: theme.text }} numberOfLines={2}>
          {ex.name}
        </Text>
        {/* Reco + charge sur une ligne ; la charge est teintée pour ressortir
            sans voler de largeur au nom (pas de pastille concurrente). */}
        <Text style={{ ...Type.caption, color: theme.textSecondary }} numberOfLines={1}>
          {recoHint(ex, rec)} ·{' '}
          <Text style={{ color: weighted ? theme.muscu : theme.textSecondary, fontWeight: '700' }}>
            {recoWeightLabel(rec)}
          </Text>
        </Text>
        {ex.muscles.length > 0 ? (
          <Text style={{ ...Type.caption, color: theme.textMuted }} numberOfLines={1}>
            {ex.muscles.slice(0, 3).join(' · ')}
          </Text>
        ) : null}
      </View>
      {added ? (
        <MaterialCommunityIcons name="check-circle" size={22} color={theme.muscu} />
      ) : (
        <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
      )}
    </PressableScale>
  );
}

/** Fiche détaillée d'un exercice, en feuille modale, avec la recommandation. */
function ExerciseDetail({
  ex,
  profile,
  added,
  addLabel,
  onAdd,
  onClose,
}: {
  ex: CatalogExercise | null;
  profile: RecoProfile;
  added: boolean;
  addLabel: string;
  onAdd: (ex: CatalogExercise) => void;
  onClose: () => void;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const rec = ex ? recommend(profile, ex) : null;
  const howTo = ex ? exerciseHowTo(ex.id) : undefined;

  return (
    <Modal visible={ex != null} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable onPress={onClose} style={{ flex: 1, justifyContent: 'flex-end' }}>
        {/* Voile dégradé (sombre vers le haut) plutôt qu'une couleur en dur ;
            non interactif pour laisser le tap-pour-fermer au Pressable. */}
        <Gradient
          colors={Gradients.scrim}
          start={{ x: 0.5, y: 1 }}
          end={{ x: 0.5, y: 0 }}
          style={StyleSheet.absoluteFill}
          pointerEvents="none"
        />
        {ex && rec ? (
          <Pressable
            onPress={(e) => e.stopPropagation()}
            style={{
              backgroundColor: theme.backgroundElement,
              borderTopLeftRadius: Radius.xl,
              borderTopRightRadius: Radius.xl,
              maxHeight: '92%',
              overflow: 'hidden',
            }}>
            {/* Poignée + fermeture */}
            <View style={{ alignItems: 'center', paddingTop: 10, paddingBottom: 4 }}>
              <View
                style={{ width: 40, height: 5, borderRadius: Radius.pill, backgroundColor: theme.border }}
              />
            </View>

            <ScrollView
              contentContainerStyle={{ padding: 20, paddingBottom: 12, gap: 16 }}
              showsVerticalScrollIndicator={false}>
              <ExerciseIllustration imageKey={ex.imageKey} icon={ex.icon} height={150} />

              <Text style={{ ...Type.headline, color: theme.text }}>{ex.name}</Text>

              {/* Matériel */}
              <View style={{ gap: 8 }}>
                <Text style={{ ...Type.overline, color: theme.textMuted }}>Matériel</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {ex.equipment.map((eq) => (
                    <View
                      key={eq}
                      style={{
                        paddingHorizontal: 12,
                        paddingVertical: 7,
                        borderRadius: Radius.pill,
                        backgroundColor: theme.accent + '1F',
                      }}>
                      <Text style={{ ...Type.label, color: theme.accent }}>{eq}</Text>
                    </View>
                  ))}
                </View>
              </View>

              {/* Recommandation personnalisée */}
              <View
                style={{
                  gap: 8,
                  padding: 14,
                  borderRadius: Radius.md,
                  borderCurve: 'continuous',
                  backgroundColor: theme.muscu + '14',
                  borderWidth: 1,
                  borderColor: theme.muscu + '33',
                }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <MaterialCommunityIcons name="target" size={18} color={theme.muscu} />
                  <Text style={{ ...Type.overline, color: theme.muscu }}>
                    Conseillé · objectif {goalLabel(profile.goal).toLowerCase()}
                  </Text>
                </View>
                <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
                  <Text style={{ ...Type.metric, color: theme.text }}>{recoHint(ex, rec)}</Text>
                  <Text style={{ ...Type.headline, color: theme.muscu }}>{recoWeightLabel(rec)}</Text>
                </View>
                <Text style={{ ...Type.caption, color: theme.textSecondary }}>
                  Repos conseillé ~{rec.restSec} s entre les séries.
                </Text>
                <Text style={{ ...Type.caption, color: theme.textMuted, lineHeight: 17 }}>
                  {`D'après ton poids (${fmtKg(profile.weightKg)} kg), ta taille (${fmtCm(
                    profile.heightCm,
                  )}) et ton objectif. Un point de départ — tu ajustes reps et charge à ta guise.`}
                </Text>
              </View>

              {/* Muscles ciblés */}
              <View style={{ gap: 8 }}>
                <Text style={{ ...Type.overline, color: theme.textMuted }}>Muscles ciblés</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {ex.muscles.map((m) => (
                    <View
                      key={m}
                      style={{
                        paddingHorizontal: 12,
                        paddingVertical: 7,
                        borderRadius: Radius.pill,
                        backgroundColor: theme.muscu + '1F',
                      }}>
                      <Text style={{ ...Type.label, color: theme.muscu }}>{m}</Text>
                    </View>
                  ))}
                </View>
              </View>

              {/* Exécution */}
              {howTo ? (
                <View style={{ gap: 8 }}>
                  <Text style={{ ...Type.overline, color: theme.textMuted }}>Exécution</Text>
                  <Text style={{ ...Type.body, color: theme.text }}>{howTo}</Text>
                </View>
              ) : null}
            </ScrollView>

            {/* Action */}
            <View
              style={{
                padding: 16,
                paddingBottom: insets.bottom + 16,
                borderTopWidth: 1,
                borderTopColor: theme.hairline,
              }}>
              <Button
                title={added ? 'Ajouter à nouveau' : addLabel}
                icon={added ? 'plus' : 'plus-circle'}
                color={theme.muscu}
                onPress={() => onAdd(ex)}
              />
            </View>
          </Pressable>
        ) : null}
      </Pressable>
    </Modal>
  );
}
