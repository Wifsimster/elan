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
