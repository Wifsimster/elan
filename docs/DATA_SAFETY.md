# Sécurité des données — réponses Play Console (Élan)

Réponses prêtes à reporter dans **Play Console → Contenu de l'application →
Sécurité des données**. Adapte si l'app évolue.

## Important : ce que « collecter » veut dire pour Google

Google définit la **collecte** comme une donnée **transmise hors de l'appareil**.
Élan stocke **tout en local** (SQLite) et **n'envoie aucune donnée à l'éditeur**.
Deux fonctions, **optionnelles et désactivées par défaut**, peuvent toutefois
faire sortir des données de l'appareil :

- **Sauvegarde S3** : l'utilisateur envoie ses données vers **son propre**
  serveur (qu'il configure lui-même).
- **Fonds de carte** : si une URL de tuiles est renseignée, le serveur de tuiles
  reçoit techniquement l'adresse IP.

> **Recommandation : déclaration prudente.** Comme une transmission hors-appareil
> est possible (sauvegarde opt-in), on déclare ces données comme « collectées »
> mais **jamais partagées avec des tiers**. C'est l'option la plus sûre vis-à-vis
> des règles Google, même si l'app n'envoie rien par défaut.

## Questions de haut niveau

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des types de données utilisateur requis ? | **Oui** (par prudence, à cause de la sauvegarde opt-in) |
| Toutes les données collectées sont-elles **chiffrées en transit** ? | **Oui** (la sauvegarde S3 utilise HTTPS) |
| Fournissez-vous un moyen de **demander la suppression** des données ? | **Oui** (suppression dans l'app + désinstallation efface tout en local) |

## Types de données à déclarer

Pour **chaque** type ci-dessous :
- **Collectée** : Oui · **Partagée** : **Non** (aucun tiers)
- **Traitement** : facultatif pour l'utilisateur (sauf position, voir note)
- **Finalité** : *Fonctionnalité de l'application* (uniquement)

| Catégorie Google | Type de donnée | Collectée | Partagée | Finalité |
|---|---|---|---|---|
| Position | **Position précise** (GPS) | Oui | Non | Fonctionnalité de l'app (mesure des sorties vélo) |
| Infos de santé et de remise en forme | **Infos de remise en forme** (FC, distance, vitesse, calories, cadence, séries/charges) | Oui | Non | Fonctionnalité de l'app |
| Infos personnelles | **Autres infos** (poids, FC max) | Oui | Non | Fonctionnalité de l'app |
| Messages | **Autres messages in-app** (notes de séance libres) | Oui | Non | Fonctionnalité de l'app |

### Notes par catégorie

- **Position précise** : utilisée **au premier plan uniquement** pendant une
  séance vélo active. Pas de localisation en arrière-plan
  (`ACCESS_BACKGROUND_LOCATION` non demandée). À déclarer comme **requise** pour
  la fonction de suivi vélo.
- **Toutes les autres** : **facultatives** (l'utilisateur les saisit ou branche
  un capteur s'il le souhaite).

## Ce qu'il NE faut PAS déclarer

Aucune de ces données n'est présente dans Élan — laisser **non coché** :

- Identifiants utilisateur, e-mail, nom, téléphone (pas de compte).
- Informations financières / de paiement.
- Identifiants d'appareil ou publicitaires, **analytics**, rapports de plantage.
- Historique de navigation/recherche, contacts, photos, fichiers, audio.
- **Aucun SDK tiers de collecte** (pas de pub, pas de télémétrie).

## Pratiques de sécurité (cases à cocher)

| Pratique | Réponse |
|---|---|
| Les données sont chiffrées en transit | **Oui** (HTTPS pour la sauvegarde S3 ; sinon tout reste local) |
| L'utilisateur peut demander la suppression de ses données | **Oui** |
| Engagement envers les règles « Familles » de Google Play | **Non** (l'app ne cible pas les enfants) |
| L'app a fait l'objet d'un examen de sécurité indépendant | **Non** (facultatif) |

## Phrase de justification (si Google demande des précisions)

> Élan est une application de suivi sportif **locale**. Les données restent sur
> l'appareil (base SQLite) et **ne sont jamais transmises à l'éditeur ni à un
> tiers**. Une sauvegarde **optionnelle**, désactivée par défaut, permet à
> l'utilisateur d'envoyer ses propres données vers **son propre serveur**
> compatible S3, via HTTPS ; les identifiants saisis ne sont jamais partagés.
> L'application ne contient ni publicité, ni traceur, ni outil de mesure
> d'audience.
