// Configuration Jest pour les fonctions pures de `src/lib/`.
// On utilise le preset officiel `jest-expo` (SDK 56) pour bénéficier de la
// transformation Babel + TypeScript prête à l'emploi. Les tests ne touchent
// que la couche framework-agnostique : pas de composants, pas de hooks, pas
// d'accès SQLite — d'où le testMatch limité aux fichiers `.test.ts`.

/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  testMatch: ['<rootDir>/__tests__/**/*.test.ts'],
  moduleNameMapper: {
    '^@/assets/(.*)$': '<rootDir>/assets/$1',
    '^@/(.*)$': '<rootDir>/src/$1',
  },
};
