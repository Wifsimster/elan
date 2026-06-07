# Publier Élan sur le Google Play Store

Guide pas à pas, de zéro à l'app en ligne. Élan utilise le workflow **managed
Expo + EAS** (pas de dossier `android/` committé) : EAS compile, signe et peut
soumettre l'app.

## 0. Prérequis (une seule fois)

```bash
npm install -g eas-cli      # CLI EAS
eas login                   # se connecter au compte Expo (owner : wifsimster)
eas whoami                  # vérifier la connexion
```

- Compte **Google Play Console** créé (frais uniques de 25 $).
  - Type **Particulier** suffisant (pas de D-U-N-S nécessaire, même pour pub /
    achats intégrés). Choisir **Organisation** seulement pour afficher
    « BATTISTELLA » comme éditeur (→ D-U-N-S requis, délai ~30 j).
- Vérification d'identité Play Console terminée.

## 1. Créer l'application dans la Play Console

1. Play Console → **Créer une application**.
2. Nom : **Élan** · langue par défaut : **français (France)**.
3. Type : **Application** · gratuite (ou payante, mais ce choix est définitif).
4. Le **nom de package** sera `ovh.battistella.elan` (déjà figé dans `app.json`).
   ⚠️ Irréversible une fois la première version envoyée.

## 2. Premier build de production (AAB)

```bash
eas build --platform android --profile production
```

- Au premier build, EAS propose de **générer la clé de signature** (« Generate
  new keystore »). Accepter → EAS conserve la *upload key*. **Irréversible** :
  EAS gère ensuite la signature pour toi.
- À la fin, EAS fournit un lien vers l'artefact **`.aab`** (App Bundle, requis
  par le Play Store). Le télécharger.
- Vérifier dans les logs : `targetSdkVersion 35` (exigence Google actuelle).

> Sauvegarder la clé de signature : `eas credentials` → plateforme Android →
> exporter/visualiser. À conserver précieusement (perte = impossible de mettre à
> jour l'app sous le même package).

## 3. Test fermé obligatoire (nouveaux comptes)

Tout **nouveau compte développeur** doit, avant la production :

- recruter **au moins 20 testeurs**,
- les garder **opt-in pendant 14 jours consécutifs** sur une piste de test
  fermée.

Étapes : Play Console → **Test → Test fermé** → créer une version → importer
l'AAB → créer une liste d'e-mails de testeurs → partager le lien d'opt-in.

## 4. Remplir la fiche et les déclarations

- **Fiche Play Store** (Présence sur le Store → Fiche principale) : les textes
  sont versionnés dans `fastlane/metadata/android/<locale>/` (fr-FR, en-US,
  es-ES, hi-IN, pt-BR) — copier/coller titre, description courte, description
  complète.
- **Captures d'écran** : réutiliser `docs/screenshots/`.
- **Feature graphic 1024×500** : prêt → `fastlane/metadata/android/fr-FR/images/featureGraphic.png`
  (logo Élan + palette PULSE, sans canal alpha — conforme à l'exigence Google).
  Régénérable via `scripts/feature-graphic.sh`.
- **Politique de confidentialité** : héberger `PRIVACY.md` à une URL publique
  (ex. `https://pro.battistella.ovh/elan/confidentialite`) et la coller dans
  Politique de confidentialité.
- **Sécurité des données** : suivre `docs/DATA_SAFETY.md`.
- **Classification du contenu** : remplir le questionnaire (app sportive →
  classification « Tout public » attendue).
- **Catégorie** : *Santé et remise en forme*.
- **Coordonnées** : e-mail de contact `battistella@proton.me`.

## 5. Soumission

### Option A — automatique via EAS (recommandée)

`eas.json` est déjà configuré (`submit.production`, piste `internal`, statut
`draft`). Il faut juste la clé de service Google :

1. Play Console → **Configuration → accès à l'API** → créer/lier un **compte de
   service Google Cloud** avec le rôle de publication, puis générer une **clé
   JSON**.
2. Enregistrer le fichier à la racine sous **`google-play-service-account.json`**
   (déjà ignoré par git — ne jamais le committer).
3. Lancer :

```bash
eas submit --platform android --profile production
```

EAS envoie le dernier build sur la piste **interne** en **brouillon**. Ajuster
`track` dans `eas.json` (`internal` → `production`) le jour du déploiement grand
public.

### Option B — manuelle

Importer l'AAB directement dans Play Console (Production → Créer une version).

## 6. Mises à jour suivantes

```bash
npm run release      # bump version + versionCode + tag (commit-and-tag-version)
eas build --platform android --profile production
eas submit --platform android --profile production
```

`versionCode` est auto-incrémenté (script `app-json-updater.cjs` +
`autoIncrement` du profil `production`). Le **package** et la **clé de
signature** ne doivent jamais changer.

## Aide-mémoire commandes

```bash
eas build --platform android --profile production   # AAB de prod
eas submit --platform android --profile production  # envoi Play Console
eas credentials                                     # voir/gérer la signature
eas build:list                                      # historique des builds
```
