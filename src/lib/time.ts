// Petit utilitaire isolé du rendu : le linter du React Compiler interdit
// d'appeler Date.now() directement dans un composant ou un hook.
export const nowMs = (): number => Date.now();
