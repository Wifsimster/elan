# Changelog

All notable changes to this project will be documented in this file. See [commit-and-tag-version](https://github.com/absolute-version/commit-and-tag-version) for commit guidelines.

## [1.5.0](https://github.com/Wifsimster/tracker-activite/compare/v1.4.0...v1.5.0) (2026-06-06)


### ✨ Fonctionnalités

* **notifications:** liste les exercices du jour dans le rappel de séance ([#27](https://github.com/Wifsimster/tracker-activite/issues/27)) ([9370155](https://github.com/Wifsimster/tracker-activite/commit/9370155429cbc43cef892a3f4f386e61f21fec72))
* **planning:** propose les programmes Dos et Cervicales dans le planning hebdo ([#29](https://github.com/Wifsimster/tracker-activite/issues/29)) ([80a0b9c](https://github.com/Wifsimster/tracker-activite/commit/80a0b9c6d93d3d673517a10e7c0ba843509164d6)), closes [#28](https://github.com/Wifsimster/tracker-activite/issues/28)

## [1.4.0](https://github.com/Wifsimster/tracker-activite/compare/v1.3.0...v1.4.0) (2026-06-05)


### ✨ Fonctionnalités

* **muscu:** ajoute les programmes Dos/lombaire et Cervicales/nuque ([#26](https://github.com/Wifsimster/tracker-activite/issues/26)) ([bc99cdc](https://github.com/Wifsimster/tracker-activite/commit/bc99cdc3fb6a0ddb34b7dac95756683b8128e5d9))


### 🐛 Corrections

* **muscu:** n'afficher le sélecteur de programme que sur une séance vide ([#25](https://github.com/Wifsimster/tracker-activite/issues/25)) ([d718280](https://github.com/Wifsimster/tracker-activite/commit/d7182800bc7658cfc687859887543f6cb6f03d50))

## [1.3.0](https://github.com/Wifsimster/tracker-activite/compare/v1.2.1...v1.3.0) (2026-06-05)


### ✨ Fonctionnalités

* **muscu:** saisie directe du poids au kg près ([#22](https://github.com/Wifsimster/tracker-activite/issues/22)) ([46f9396](https://github.com/Wifsimster/tracker-activite/commit/46f93966101e0b565bb6c0504f03f6eb1434ada1))
* **partage:** afficher le fond de carte dans l'image partagée ([#19](https://github.com/Wifsimster/tracker-activite/issues/19)) ([e7e7024](https://github.com/Wifsimster/tracker-activite/commit/e7e7024ed8a7e9d82905d5949007e30651b3e85c))

## [1.2.1](https://github.com/Wifsimster/tracker-activite/compare/v1.2.0...v1.2.1) (2026-06-05)


### 🐛 Corrections

* **accueil:** bouton « Reprendre » sur la carte du jour si une muscu est en pause ([b8aae62](https://github.com/Wifsimster/tracker-activite/commit/b8aae62eb074500a7a7c662ef67793f2adbc45ab))

## [1.2.0](https://github.com/Wifsimster/tracker-activite/compare/v1.1.0...v1.2.0) (2026-06-05)


### ✨ Fonctionnalités

* **accueil:** mention « Dernière séance » sur la carte du jour ([#11](https://github.com/Wifsimster/tracker-activite/issues/11)) ([1040e04](https://github.com/Wifsimster/tracker-activite/commit/1040e046a481727230b41880dc17798f01b0f53f))
* minuteur de repos, 1RM estimé et comparaison hebdomadaire ([#16](https://github.com/Wifsimster/tracker-activite/issues/16)) ([e3d39fe](https://github.com/Wifsimster/tracker-activite/commit/e3d39fe75e2b3e22e32802b877ad89f8326c3efa))
* **muscu:** bouton pause/reprise pendant la séance ([#13](https://github.com/Wifsimster/tracker-activite/issues/13)) ([0d36db5](https://github.com/Wifsimster/tracker-activite/commit/0d36db52f89502093ee4fb8e7aba947d13786408))
* **muscu:** fiche d'exercice illustrée en bottom sheet ([#14](https://github.com/Wifsimster/tracker-activite/issues/14)) ([c984738](https://github.com/Wifsimster/tracker-activite/commit/c984738e384c7a53fce111dbaffad95be3baec34))
* **muscu:** illustrer les exercices par des photos départ → fin ([#17](https://github.com/Wifsimster/tracker-activite/issues/17)) ([802423f](https://github.com/Wifsimster/tracker-activite/commit/802423f3206ff34cb0f2a0ab011a7d167a85f513))
* **muscu:** mettre en pause et reprendre une séance en cours ([b3dcfbc](https://github.com/Wifsimster/tracker-activite/commit/b3dcfbcacabe5db0e1090f2337f7adfc7a004f25))
* **notifications:** rappels le jour même de la séance, midi par défaut ([6be5ae3](https://github.com/Wifsimster/tracker-activite/commit/6be5ae3b07f618780cbbe1d34a94a866dea035a5))
* **partage:** partager une séance en image (Discord & co.) ([#8](https://github.com/Wifsimster/tracker-activite/issues/8)) ([4d37e45](https://github.com/Wifsimster/tracker-activite/commit/4d37e4500cb692857e72046353ef9376fc122342))
* **reglages:** rappels du soir locaux (opt-in) la veille d'une séance ([#12](https://github.com/Wifsimster/tracker-activite/issues/12)) ([61e1ea1](https://github.com/Wifsimster/tracker-activite/commit/61e1ea1bb3192ea83232acb4ec7fe9870961e236))


### 🐛 Corrections

* **backup:** refuser la restauration d'une sauvegarde de format plus récent ([ac408d3](https://github.com/Wifsimster/tracker-activite/commit/ac408d3295530ca94051af2994580387b6982064))
* **format:** éviter « 1 h 60 » dans formatDurationShort ([059496a](https://github.com/Wifsimster/tracker-activite/commit/059496a4fbce7f844baf8b89466553daab326cc2))
* **muscu:** retirer l'animation d'entrée de la fiche d'exercice ([877bef2](https://github.com/Wifsimster/tracker-activite/commit/877bef20b182e65439c08b12069398e7d0813ac9))
* **velo:** intercepter le retour matériel et couper le GPS au démontage ([cc2b9c6](https://github.com/Wifsimster/tracker-activite/commit/cc2b9c66c7927022d568868afd6ed3087d9092d5))

## 1.1.0 (2026-06-03)


### ✨ Fonctionnalités

* **backup:** pré-remplir la config S3 du homelab par défaut ([b276560](https://github.com/Wifsimster/tracker-activite/commit/b276560fcbb9eaddc3bcd1156dc2c983ba08d4f4))
* **brand:** renommer l'app en « Élan » et nouvelle identité visuelle ([2f1dc47](https://github.com/Wifsimster/tracker-activite/commit/2f1dc470afdff54db6103e953feb86419143b401)), closes [#0A0C10](https://github.com/Wifsimster/tracker-activite/issues/0A0C10)
* **calories:** modèle MET interpolé + estimation cardio (Keytel) ([0eeaa38](https://github.com/Wifsimster/tracker-activite/commit/0eeaa38aad30cc0e48e8a6e0101ff50e715b2405))
* **calories:** modèle MET interpolé + estimation cardio (Keytel) ([3ea57cc](https://github.com/Wifsimster/tracker-activite/commit/3ea57ccff5d4bbab975bac32ccb97ce16f21db6f))
* **carte:** fond de carte MapLibre auto-hébergé pour les parcours ([5a30c1a](https://github.com/Wifsimster/tracker-activite/commit/5a30c1adc67c5e942be16faf198cb970a2d3c9ab))
* **export:** exporter les données pour un coach IA (Markdown + JSON) ([bd8f1a4](https://github.com/Wifsimster/tracker-activite/commit/bd8f1a4d10fe5c75aea88bfcf7912de2210a09df))
* **historique:** recherche, filtre par période et liste virtualisée ([4f8fc39](https://github.com/Wifsimster/tracker-activite/commit/4f8fc3942b79b1d2dedb11ccc503dea92d8ac013))
* **historique:** recherche, filtre par période et liste virtualisée ([97d7cca](https://github.com/Wifsimster/tracker-activite/commit/97d7ccab79660072166f7f6dff65e2d4ef94f661))
* **ios:** identifiant de bundle + splash screen iOS ([552cd73](https://github.com/Wifsimster/tracker-activite/commit/552cd73f7a9d4d343cbd79817f438c343f106ff7))
* **map:** utiliser le serveur de tuiles perso (osm-bright) par défaut ([b0d1d67](https://github.com/Wifsimster/tracker-activite/commit/b0d1d672a00df04c013ea16927ae0209710c1f32))
* **muscu:** cocher chaque série terminée en cours de séance ([43970d5](https://github.com/Wifsimster/tracker-activite/commit/43970d5edca9261cd650eaf85b0cf52873394b65))
* **muscu:** programme perso, séance du jour et suivi de progression ([0c5b373](https://github.com/Wifsimster/tracker-activite/commit/0c5b3738760fc2237cd04e75f2453b2e6eb94e01))
* **programme:** planning hebdomadaire personnalisable ([1e5eb15](https://github.com/Wifsimster/tracker-activite/commit/1e5eb152383c3a22ac43f1f9df665f435428e3f2))
* **programme:** planning hebdomadaire personnalisable ([f9562db](https://github.com/Wifsimster/tracker-activite/commit/f9562dbeeb95542f7e1fdb09d9106f67ecbd8b3d))
* **reglages:** liste déroulante pour la taille de pneu + export générique ([3c5b6db](https://github.com/Wifsimster/tracker-activite/commit/3c5b6dbdef0e6acf6f579df35383ec9ab5c5bb68))
* **séance:** graphes de données, records et effort façon Strava ([#7](https://github.com/Wifsimster/tracker-activite/issues/7)) ([b4d0109](https://github.com/Wifsimster/tracker-activite/commit/b4d0109f4051fac96424b4d69a69384207ec2233))
* **strava:** import Strava activities from GPX/TCX files ([b567c99](https://github.com/Wifsimster/tracker-activite/commit/b567c99438488ff911c79521328801b1a7ad3024))
* **strava:** importer les fichiers FIT et les exports compressés (.gz) ([73f239f](https://github.com/Wifsimster/tracker-activite/commit/73f239f002e39f95c3c8cefa0e05df95a7ff594a))
* **velo:** carte GPS interactive du tracé (live + historique) ([e2e2f72](https://github.com/Wifsimster/tracker-activite/commit/e2e2f72543e64c9d06df8969d0ec88757cd0abb7))


### ⚡ Performances

* **db,ble:** statements préparés, recherche dichotomique, abonnement aux trames brutes ([#10](https://github.com/Wifsimster/tracker-activite/issues/10)) ([d97d8ec](https://github.com/Wifsimster/tracker-activite/commit/d97d8ec0fcafce6926127a85669841178be20f24))
