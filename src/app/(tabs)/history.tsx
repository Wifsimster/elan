import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { FlatList, TextInput, View, Text } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { EmptyState } from '@/components/empty-state';
import { PressableScale } from '@/components/pressable-scale';
import { SessionRow } from '@/components/session-row';
import { Radius, Type } from '@/constants/theme';
import { listSessions } from '@/lib/db';
import type { ActivityType, Session } from '@/lib/types';
import { useScreenContentStyle } from '@/hooks/use-screen-layout';
import { useTheme } from '@/hooks/use-theme';

type TypeFilter = 'all' | ActivityType;
type RangeKey = 'all' | '7' | '30' | '90';

const TYPE_FILTERS: { key: TypeFilter; label: string }[] = [
  { key: 'all', label: 'Tout' },
  { key: 'velo', label: 'Vélo' },
  { key: 'muscu', label: 'Muscu' },
];

const RANGE_FILTERS: { key: RangeKey; label: string; days: number | null }[] = [
  { key: '7', label: '7 j', days: 7 },
  { key: '30', label: '30 j', days: 30 },
  { key: '90', label: '90 j', days: 90 },
  { key: 'all', label: 'Tout', days: null },
];

const PAGE_SIZE = 50;

export default function HistoryScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const contentStyle = useScreenContentStyle();
  const router = useRouter();

  const [typeFilter, setTypeFilter] = useState<TypeFilter>('all');
  const [range, setRange] = useState<RangeKey>('all');
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');

  const [sessions, setSessions] = useState<Session[]>([]);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  // Identifie le « run » de chargement courant : tout résultat d'un run
  // obsolète (filtre changé entre temps) est ignoré, ce qui évite les
  // courses de réponses.
  const runRef = useRef(0);

  // Debounce de la saisie (200 ms) — déclenche le rechargement.
  useEffect(() => {
    const id = setTimeout(() => setSearch(searchInput.trim()), 200);
    return () => clearTimeout(id);
  }, [searchInput]);

  // Convertit la plage de dates en borne `fromMs`. Évalué hors render
  // (`Date.now()` impur) — appelé depuis les callbacks de chargement.
  const computeFromMs = (key: RangeKey) => {
    const days = RANGE_FILTERS.find((r) => r.key === key)?.days;
    return days != null ? Date.now() - days * 86_400_000 : undefined;
  };

  // (Re)charge la première page à chaque changement de filtre, et au focus
  // de l'onglet pour refléter une séance fraîchement enregistrée.
  const loadFirstPage = useCallback(async () => {
    const myRun = ++runRef.current;
    setLoading(true);
    const rows = await listSessions({
      limit: PAGE_SIZE,
      offset: 0,
      type: typeFilter === 'all' ? undefined : typeFilter,
      search: search || undefined,
      fromMs: computeFromMs(range),
    });
    if (runRef.current !== myRun) return;
    setSessions(rows);
    setHasMore(rows.length === PAGE_SIZE);
    setLoading(false);
  }, [typeFilter, search, range]);

  useFocusEffect(
    useCallback(() => {
      loadFirstPage();
    }, [loadFirstPage]),
  );

  // Pagination : charge la page suivante quand on approche du bas.
  const loadMore = useCallback(async () => {
    if (loading || !hasMore) return;
    const myRun = ++runRef.current;
    setLoading(true);
    const rows = await listSessions({
      limit: PAGE_SIZE,
      offset: sessions.length,
      type: typeFilter === 'all' ? undefined : typeFilter,
      search: search || undefined,
      fromMs: computeFromMs(range),
    });
    if (runRef.current !== myRun) return;
    setSessions((prev) => [...prev, ...rows]);
    setHasMore(rows.length === PAGE_SIZE);
    setLoading(false);
  }, [loading, hasMore, sessions.length, typeFilter, search, range]);

  return (
    <FlatList
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        ...contentStyle,
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        gap: 10,
      }}
      data={sessions}
      keyExtractor={(s) => String(s.id)}
      renderItem={({ item }) => <SessionRow session={item} />}
      onEndReached={loadMore}
      onEndReachedThreshold={0.5}
      keyboardShouldPersistTaps="handled"
      ListHeaderComponent={
        <View style={{ gap: 14, paddingBottom: 4 }}>
          <Text style={{ ...Type.title, color: theme.text }}>Historique</Text>

          <Link href="/progression" asChild>
            <PressableScale>
              <Card
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 14,
                  paddingVertical: 14,
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
                  <MaterialCommunityIcons name="chart-line" size={22} color={theme.muscu} />
                </View>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={{ ...Type.subtitle, color: theme.text }}>Progression muscu</Text>
                  <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                    Suivi des charges par exercice
                  </Text>
                </View>
                <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
              </Card>
            </PressableScale>
          </Link>

          {/* Recherche — exercice, note ou type. */}
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 10,
              paddingHorizontal: 14,
              height: 44,
              borderRadius: Radius.pill,
              borderWidth: 1,
              borderColor: theme.border,
              backgroundColor: theme.backgroundElement,
            }}>
            <MaterialCommunityIcons name="magnify" size={20} color={theme.textMuted} />
            <TextInput
              value={searchInput}
              onChangeText={setSearchInput}
              placeholder="Rechercher (exercice, note…)"
              placeholderTextColor={theme.textMuted}
              autoCorrect={false}
              autoCapitalize="none"
              returnKeyType="search"
              style={{
                flex: 1,
                color: theme.text,
                fontSize: 15,
                paddingVertical: 0,
              }}
            />
            {searchInput.length > 0 ? (
              <PressableScale onPress={() => setSearchInput('')} scaleTo={0.9}>
                <MaterialCommunityIcons
                  name="close-circle"
                  size={18}
                  color={theme.textMuted}
                />
              </PressableScale>
            ) : null}
          </View>

          {/* Filtres par type d'activité. */}
          <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
            {TYPE_FILTERS.map((f) => (
              <Chip
                key={f.key}
                label={f.label}
                selected={typeFilter === f.key}
                color={
                  f.key === 'velo'
                    ? theme.velo
                    : f.key === 'muscu'
                      ? theme.muscu
                      : theme.accent
                }
                onPress={() => setTypeFilter(f.key)}
              />
            ))}
          </View>

          {/* Plage de dates. */}
          <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
            {RANGE_FILTERS.map((r) => (
              <Chip
                key={r.key}
                label={r.label}
                selected={range === r.key}
                onPress={() => setRange(r.key)}
              />
            ))}
          </View>
        </View>
      }
      ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
      ListEmptyComponent={
        loading ? null : (
          (() => {
            const filtered = search || typeFilter !== 'all' || range !== 'all';
            return (
              <EmptyState
                icon="clipboard-text-clock-outline"
                title={filtered ? 'Aucun résultat' : 'Votre historique est vide'}
                subtitle={
                  filtered
                    ? 'Aucune séance ne correspond à ces filtres.'
                    : 'Vos séances enregistrées apparaîtront ici. Vous pouvez aussi importer votre historique Strava.'
                }
                // Pas d'import quand un filtre masque les résultats : l'utilisateur
                // a des séances, elles sont juste filtrées.
                action={
                  filtered
                    ? undefined
                    : {
                        label: 'Importer depuis Strava',
                        icon: 'file-import-outline',
                        onPress: () => router.push('/settings'),
                      }
                }
              />
            );
          })()
        )
      }
    />
  );
}
