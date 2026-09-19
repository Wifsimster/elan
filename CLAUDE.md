# CLAUDE.md

Ce fichier guide Claude Code (claude.ai/code) dans ce dépôt. Les conventions
complètes (structure des paquets, commandes, commits, versions, dépendances,
scripts, invariants de vie privée) sont dans `AGENTS.md`, importé ci-dessous ;
ce fichier n'ajoute que ce qui est propre à Claude Code.

@AGENTS.md

## Rappels pour Claude Code

- **Élan 2.0 est natif** : Kotlin 2.0 + Jetpack Compose (Material 3
  Expressive), Hilt, Room, WorkManager. Il n'y a plus d'Expo, de npm, de Metro
  ni de Jest ; les fichiers `src/`, `__tests__/`, `app.json`, `package.json`,
  `metro.config.js`, `eas.json` et `plugins/` encore présents sur la branche
  `native` sont des restes de la 1.x, supprimés à la fusion. Ne pas s'en
  servir comme référence de comportement : les sources Kotlin font foi, et
  `docs/port-spec/*.md` décrit ce qui a été porté.
- **Commande de vérification** avant de conclure une tâche :
  `./gradlew testDebugUnitTest assembleDebug`. Pour une modification d'écran,
  faire aussi tourner le `XScreenTest` correspondant ; pour une modification
  du schéma, `MigrationTest` et `SchemaTest`.
- **Lire `DESIGN.md` avant toute UI** ; les tokens sont dans `ui/theme/` et il
  n'y a aucune valeur de style en dur dans les écrans.
- **Ne pas** ajouter de dépendance sans vérifier l'alignement 16 Ko
  (`scripts/check-16kb-alignment.sh`), ni de dépendance réseau/cloud sans
  demande explicite.
- **Ne pas** éditer `VERSION_NAME` (`gradle.properties`) ni `CHANGELOG.md` :
  semantic-release s'en charge à partir des commits conventionnels en
  français.
- **Ne pas** toucher `data/legacy/` sans lire `docs/MIGRATION-1.x.md` : cette
  couche lit une base produite par une autre application et ses tests
  s'appuient sur `LegacyFixture`.
- Commentaires et chaînes d'interface en **français**.
