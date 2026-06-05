# Illustrations d'exercices

Photos des mouvements de musculation (position de départ `*-0.jpg` → position
finale `*-1.jpg`), affichées dans la fiche d'exercice.

- **Source** : [free-exercise-db](https://github.com/yuhonas/free-exercise-db)
- **Licence** : The Unlicense (domaine public) — aucune attribution requise.

Les images sont **bundlées dans l'app** (résolues via `require()` dans
`src/components/exercise-images.ts`) : aucun accès réseau, conforme à l'approche
local-first du projet. Pour ajouter un exercice, dépose la paire `slug-0.jpg` /
`slug-1.jpg` ici, puis référence le `slug` comme `imageKey` dans
`src/lib/program.ts` et dans le registre `exercise-images.ts`.
