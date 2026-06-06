# Métadonnées Google Play (fastlane)

Textes de la fiche Play Store, versionnés. Réutilisables tels quels par
copier-coller dans la Google Play Console, ou via `fastlane supply`.

## Structure

```
fastlane/metadata/android/<locale>/
  title.txt              # Nom de l'app (max 30 caractères)
  short_description.txt  # Description courte (max 80 caractères)
  full_description.txt   # Description complète (max 4000 caractères)
```

Locales fournies : `fr-FR`, `en-US`.

## Assets graphiques encore à produire

Non versionnés ici (binaires) — à téléverser dans la Play Console :

- **Icône** 512×512 PNG → déjà disponible en source : `assets/images/icon.png` (1024×1024)
- **Feature graphic** 1024×500 PNG/JPG → **à créer** (obligatoire)
- **Captures d'écran téléphone** (min. 2, 320–3840 px) → disponibles dans
  `docs/screenshots/`

## À renseigner côté Play Console (hors fichiers)

- URL de la politique de confidentialité (héberger `PRIVACY.md`)
- Catégorie : Santé et remise en forme
- Questionnaire « Sécurité des données »
- Classification du contenu
