# Politique de confidentialité — Élan

**Dernière mise à jour : 6 juin 2026**

Élan est une application Android de suivi d'activité physique (vélo et musculation)
**personnelle et locale**. Cette politique explique quelles données l'application
traite et comment. En résumé : **vos données ne quittent jamais votre téléphone**,
sauf si vous activez vous-même une sauvegarde vers votre propre serveur.

## Responsable du traitement

- **Éditeur :** Damien Battistella — BATTISTELLA EI (entreprise individuelle)
- **Adresse :** Artigues-près-Bordeaux, 33370, France
- **Contact :** battistella@proton.me

## Aucun compte, aucun serveur, aucune publicité

Élan fonctionne **entièrement hors-ligne** :

- pas de création de compte ni d'authentification ;
- pas de serveur de l'éditeur : **aucune donnée n'est transmise à BATTISTELLA EI** ;
- pas de publicité, pas de traceur, pas d'outil de mesure d'audience ou de
  télémétrie, pas de SDK tiers de collecte ;
- aucune revente ni partage de données avec des tiers.

## Données traitées et stockées localement

Toutes les données ci-dessous sont enregistrées **uniquement** dans une base de
données locale (SQLite) sur votre appareil. Elles restent sous votre contrôle et
sont supprimées lorsque vous désinstallez l'application.

| Donnée | Finalité | Origine |
| --- | --- | --- |
| Position GPS (latitude, longitude, altitude, vitesse) | Mesurer la distance, la vitesse et le tracé de vos sorties vélo | Capteur GPS du téléphone, **pendant une séance active uniquement** |
| Fréquence cardiaque | Afficher et enregistrer votre effort, calculer les zones cardio | Ceinture/capteur Bluetooth externe (optionnel) |
| Cadence et vitesse vélo | Mesures de séance | Capteur Bluetooth externe (optionnel) |
| Données d'entraînement (durée, distance, dénivelé, calories estimées, séries, charges, répétitions) | Historique et suivi de progression | Saisie et calculs dans l'application |
| Profil (poids, fréquence cardiaque maximale) | Estimer les calories et les zones cardio | Saisie par vous |
| Notes de séance | Champ libre facultatif | Saisie par vous |

La **localisation est utilisée au premier plan uniquement** : le suivi GPS ne
fonctionne que lorsque l'application est ouverte et la séance en cours. Élan ne
demande **pas** la permission de localisation en arrière-plan.

Les estimations de calories reposent sur la méthode des équivalents métaboliques
(MET) : ce sont des ordres de grandeur, **pas une mesure médicale**.

## Fonctions réseau optionnelles (désactivées par défaut)

Élan n'accède au réseau que si **vous** le configurez explicitement dans les
Réglages. Tant que vous ne renseignez pas vous-même les paramètres ci-dessous,
l'application reste totalement hors-ligne.

### Sauvegarde sur votre serveur (compatible S3)

Vous pouvez configurer une sauvegarde vers un serveur de stockage compatible S3
(par exemple votre propre instance MinIO/SeaweedFS auto-hébergée). Dans ce cas :

- une copie de vos données est envoyée **vers le serveur que vous indiquez**,
  via une connexion chiffrée (HTTPS) ;
- l'adresse du serveur et les identifiants d'accès sont saisis par vous et
  stockés localement ; ils ne sont **jamais** transmis à l'éditeur ni inclus
  dans la sauvegarde ;
- BATTISTELLA EI n'opère pas ce serveur et n'a accès ni à vos données ni à vos
  identifiants. Le responsable du serveur que vous choisissez est seul concerné.

### Fonds de carte (tuiles MapLibre)

Pour afficher vos parcours sur une carte, vous pouvez renseigner l'URL d'un
serveur de tuiles cartographiques. L'application récupère alors les tuiles depuis
**ce serveur**, qui reçoit techniquement les requêtes correspondantes (dont votre
adresse IP). À défaut d'URL, Élan affiche le tracé en mode vectoriel **sans aucun
accès réseau**.

### Import de fichiers (Strava / GPX / TCX / FIT)

Vous pouvez importer des activités à partir de fichiers que vous sélectionnez
depuis votre appareil. Cet import est **local** : il ne s'agit pas d'une connexion
au compte Strava ni à une API en ligne.

### Partage et export

Le partage d'une séance (image) ou l'export de vos données (Markdown/JSON) passe
par le sélecteur de partage du système Android. Vous choisissez la destination ;
Élan n'envoie rien de lui-même.

## Permissions Android demandées

- **Localisation (précise et approximative)** — suivi GPS des sorties vélo, au
  premier plan uniquement.
- **Bluetooth (recherche et connexion)** — connexion aux capteurs cardio/vélo.
  La recherche Bluetooth est déclarée comme **n'étant pas utilisée pour déduire
  la localisation**.
- **Notifications** — rappels locaux de séance (facultatif).

## Conservation et suppression

Vos données sont conservées sur votre appareil tant que l'application est
installée. Vous pouvez à tout moment :

- supprimer une séance depuis l'historique ;
- effacer les données de l'application via les réglages Android ;
- **désinstaller l'application**, ce qui supprime l'intégralité des données
  locales.

Les sauvegardes que vous avez vous-même envoyées vers votre serveur S3 doivent
être supprimées sur ce serveur, dont vous êtes responsable.

## Vos droits (RGPD)

L'application ne transmettant aucune donnée à l'éditeur, BATTISTELLA EI ne détient
aucune donnée vous concernant. Vous gardez la maîtrise complète de vos données,
stockées localement. Pour toute question relative à cette politique ou à
l'exercice de vos droits (accès, rectification, effacement, limitation,
portabilité, opposition), vous pouvez écrire à **battistella@proton.me**
(réponse sous 30 jours).

## Enfants

Élan ne s'adresse pas spécifiquement aux enfants et ne collecte sciemment aucune
donnée les concernant.

## Modifications

Cette politique peut évoluer avec l'application. La date de dernière mise à jour
figure en haut du document ; les changements importants seront signalés dans les
notes de version.

## Contact

Pour toute question : **battistella@proton.me**
