import { useEffect, useState } from 'react';
import { Alert, Switch, Text, View } from 'react-native';

import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingField } from '@/components/settings/setting-field';
import { getMapStyleUrl, isValidMapStyleUrl, OPENFREEMAP_STYLE_URL, setMapStyleUrl } from '@/lib/map';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : fond de carte MapLibre en ligne (opt-in, off par défaut). */
export function MapCard() {
  const theme = useTheme();
  const [mapUrl, setMapUrl] = useState('');

  useEffect(() => {
    getMapStyleUrl().then(setMapUrl);
  }, []);

  const updateMapUrl = (url: string) => {
    setMapUrl(url);
    // Non-bloquant : l'appelant a déjà validé l'URL (HTTPS). Le .catch évite une
    // rejection non gérée si une URL invalide passe malgré tout.
    setMapStyleUrl(url).catch(() => {});
  };

  // Fond de carte en ligne : opt-in. Vide = hors-ligne (tracé SVG sans réseau).
  const mapEnabled = mapUrl.trim().length > 0;
  // Le champ « serveur perso » reste vide quand on utilise le preset OpenFreeMap.
  const customMapUrl = mapUrl === OPENFREEMAP_STYLE_URL ? '' : mapUrl;

  const toggleMap = (on: boolean) => {
    updateMapUrl(on ? customMapUrl || OPENFREEMAP_STYLE_URL : '');
  };

  const updateCustomMapUrl = (url: string) => {
    const trimmed = url.trim();
    // Serveur de tuiles en HTTPS uniquement : un fond http fuiterait la zone du
    // parcours + l'IP en clair, et ne fonctionnerait pas en release.
    if (trimmed !== '' && !isValidMapStyleUrl(trimmed)) {
      Alert.alert('URL invalide', 'Le serveur de tuiles doit être en HTTPS (https://…).');
      return;
    }
    // Champ vidé : on retombe sur OpenFreeMap si la carte est activée, sinon hors-ligne.
    updateMapUrl(trimmed === '' ? (mapEnabled ? OPENFREEMAP_STYLE_URL : '') : trimmed);
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="map-outline" color={theme.velo} title="Carte" />
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600', flex: 1 }}>
          Fond de carte en ligne
        </Text>
        <Switch value={mapEnabled} onValueChange={toggleMap} />
      </View>
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {mapEnabled
          ? 'Tes sorties affichent un vrai fond de carte (rues). Les requêtes de tuiles (zone du parcours + adresse IP) transitent par le serveur ci-dessous. Désactive pour rester 100 % hors-ligne.'
          : 'Désactivé : le tracé s’affiche sur fond uni, 100 % hors-ligne (aucune donnée envoyée). Active pour un vrai fond de carte via OpenFreeMap (gratuit, open source, sans compte).'}
      </Text>
      {mapEnabled ? (
        <>
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
            {customMapUrl
              ? 'Fond : ton serveur de tuiles personnel.'
              : 'Fond : OpenFreeMap (données OpenStreetMap). Aucune clé requise.'}
          </Text>
          <SettingField
            label="Serveur de tuiles personnel (avancé, optionnel)"
            placeholder="https://exemple.com/styles/basic/style.json"
            value={customMapUrl}
            onChangeText={updateCustomMapUrl}
            keyboardType="url"
          />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
            Laisse vide pour OpenFreeMap. Renseigne ton propre serveur MapLibre pour ne
            dépendre d’aucun tiers.
          </Text>
        </>
      ) : null}
    </Card>
  );
}
