// Mise en page d'écran adaptée au paysage.
//
// En portrait, les écrans ne padaient qu'en haut/bas (insets.top/bottom). En
// paysage, l'encoche / la caméra passe sur le CÔTÉ : sans marge latérale de
// sécurité, le contenu se retrouve masqué sous le découpage. Ce style ajoute
// donc les marges `insets.left/right`, et borne + centre la largeur du contenu
// (`MaxContentWidth`) pour éviter des lignes interminables sur un écran large
// (paysage téléphone, tablette).
//
// À étaler dans le `contentContainerStyle` d'un ScrollView/FlatList ; l'écran
// garde la main sur `paddingTop`/`paddingBottom`/`gap`.
import type { ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MaxContentWidth } from '@/constants/theme';

export function useScreenContentStyle(horizontal = 16): ViewStyle {
  const insets = useSafeAreaInsets();
  return {
    paddingLeft: insets.left + horizontal,
    paddingRight: insets.right + horizontal,
    maxWidth: MaxContentWidth,
    width: '100%',
    alignSelf: 'center',
  };
}
