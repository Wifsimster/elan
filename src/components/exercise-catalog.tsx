import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Chip } from '@/components/chip';
import { ExerciseIllustration } from '@/components/exercise-illustration';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
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

  return (
    <View style={{ flex: 1, gap: 12 }}>
      {/* Recherche */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: 8,
          backgroundColor: theme.background,
          borderRadius: Radius.sm,
          borderWidth: 1,
          borderColor: theme.border,
          paddingHorizontal: 12,
        }}>
        <MaterialCommunityIcons name="magnify" size={20} color={theme.textMuted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Rechercher un exercice, un muscle…"
          placeholderTextColor={theme.textMuted}
          autoCapitalize="none"
          autoCorrect={false}
          style={{ flex: 1, color: theme.text, paddingVertical: 10, fontSize: 15 }}
        />
        {query.length > 0 ? (
          <PressableScale onPress={() => setQuery('')} haptic="selection" hitSlop={10}>
            <MaterialCommunityIcons name="close-circle" size={18} color={theme.textMuted} />
          </PressableScale>
        ) : null}
      </View>

      {/* Filtres : groupe musculaire */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: 8, paddingRight: 4 }}>
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
      </ScrollView>

      {/* Filtres : matériel */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: 8, paddingRight: 4 }}>
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
      </ScrollView>

      {/* Liste */}
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ paddingBottom: 16, gap: 16 }}
        showsVerticalScrollIndicator={false}>
        {sections.length === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: 40, gap: 8 }}>
            <MaterialCommunityIcons name="magnify-close" size={36} color={theme.textMuted} />
            <Text style={{ color: theme.textSecondary, fontSize: 14 }}>Aucun exercice trouvé.</Text>
          </View>
        ) : null}

        {sections.map((section) => (
          <View key={section.cat} style={{ gap: 8 }}>
            <Text style={{ ...Type.overline, color: theme.textMuted }}>{section.cat}</Text>
            <View style={{ gap: 8 }}>
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
  return (
    <PressableScale
      onPress={onPress}
      haptic="selection"
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        padding: 12,
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
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '700' }} numberOfLines={1}>
          {ex.name}
        </Text>
        <Text style={{ color: theme.textSecondary, fontSize: 12 }} numberOfLines={1}>
          {recoHint(ex, rec)} · {recoWeightLabel(rec)}
        </Text>
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
      <Pressable
        onPress={onClose}
        style={{ flex: 1, backgroundColor: '#00000099', justifyContent: 'flex-end' }}>
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
                      <Text style={{ color: theme.accent, fontWeight: '700', fontSize: 13 }}>{eq}</Text>
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
                  <Text style={{ color: theme.text, fontSize: 22, fontWeight: '800' }}>
                    {recoHint(ex, rec)}
                  </Text>
                  <Text style={{ color: theme.muscu, fontSize: 16, fontWeight: '800' }}>
                    {recoWeightLabel(rec)}
                  </Text>
                </View>
                <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
                  Repos conseillé ~{rec.restSec} s entre les séries.
                </Text>
                <Text style={{ color: theme.textMuted, fontSize: 12, lineHeight: 17 }}>
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
                      <Text style={{ color: theme.muscu, fontWeight: '700', fontSize: 13 }}>{m}</Text>
                    </View>
                  ))}
                </View>
              </View>

              {/* Exécution */}
              {howTo ? (
                <View style={{ gap: 8 }}>
                  <Text style={{ ...Type.overline, color: theme.textMuted }}>Exécution</Text>
                  <Text style={{ color: theme.text, fontSize: 15, lineHeight: 23 }}>{howTo}</Text>
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
