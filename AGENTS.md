# SMELS back-office : instructions pour les assistants IA

> Ce fichier est lu par Cursor (`AGENTS.md`) et importé par Claude Code (`CLAUDE.md`).
> Lis-le en entier avant toute tâche, puis lis les documents de `../../Docs/back-office/docs` cités plus bas.

## Projet
API REST d'un logiciel de gestion scolaire pour un établissement **bilingue (français / anglais)** du Cameroun :
élèves, enseignants, matières, **notes**, génération automatique des **bulletins**, puis emplois du temps, cartes d'élèves et inscriptions.
Les utilisateurs finaux ne sont pas techniques ; les données sont celles de **mineurs**.

## Stack
- Kotlin + Spring Boot (monolithe modulaire, **pas de microservices**), Java 21, Gradle Kotlin DSL
- MongoDB (replica set, même à un nœud, pour les transactions) ; Spring Data MongoDB
- Front séparé en Next.js (hors de ce dépôt)
- Docker, Nginx, GitHub Actions ; déploiement sur VPS
- Package racine : `com.kobe.smels` ; dossiers : `configuration`, `controller`, `dto`, `exception`, `model`, `repository`, `service`

## Où lire quoi
| Besoin | Document |
|---|---|
| Comprendre les bulletins | `docs/01-analyse-bulletins.md` |
| Règles métier (BR-xx), glossaire, parcours | `docs/02-domaine-metier.md` |
| Collections, index, particularités Mongo/Kotlin, données de référence | `docs/03-modele-de-donnees.md` |
| Formules de calcul, arrondi, critères d'acceptation | `docs/04-regles-de-calcul.md` |
| Rôles et matrice de permissions | `docs/05-roles-permissions.md` |
| Plan de travail et prompts | `docs/06-feuille-de-route.md` |
| Points non tranchés (Q-xx) | `docs/07-questions-ouvertes.md` |
| Jeux de test anonymisés | `docs/fixtures/*.json` |

## État
Le package `model` est écrit (compilé contre des annotations simulées : à recompiler avec Gradle). Rien d'autre n'est implémenté : ni services, ni sécurité, ni configuration.

## Règles impératives
1. **Ne modifie pas le package `model` sans l'annoncer** et sans mettre à jour `docs/03-modele-de-donnees.md`. Si une règle ou un champ manque, propose-le plutôt que de contourner.
2. **Jamais `copy()` sur un document persistant** (les champs hérités `version`, `createdAt`... ne sont pas recopiés). Modifier l'instance chargée puis `save()`.
3. **Tout champ `BigDecimal` stocké** porte `@Field(targetType = FieldType.DECIMAL128)`. Comparer avec `compareTo`, jamais `equals`.
4. **Toute requête filtre par `schoolId`** ; tout index composite commence par `schoolId`.
5. **Les notes ne s'écrivent que par `GradeService`**, qui applique les règles BR-10 à BR-15 et écrit un `GradeAuditEntry` dans la même transaction.
6. **Les calculs (moyennes, totaux, rangs, profil de classe) s'effectuent dans la couche `service`** (`GradeCalculationService`, appelé par `ReportCardService`). `docs/04-regles-de-calcul.md` est la spécification à implémenter : ne pas créer de « moteur » séparé. Tester avec `docs/fixtures/`. Aucune règle codée en dur qui existe dans `GradingScheme`.
7. **Ne jamais exposer un document dans l'API** : DTO obligatoires ; jamais de `passwordHash` dans une réponse.
8. **Aucune donnée réelle d'élève** dans le code, les tests, les logs, les fixtures ou Git. Pas d'identifiant d'élève dans les logs.
9. **Ne pas inventer une règle ambiguë** : laisser un `TODO(Q-xx)` référencé dans `docs/07-questions-ouvertes.md` et le signaler.
10. **Aucune marque ni service d'un prestataire tiers** dans les bulletins produits (pas de « Powered by », pas de code USSD tiers).
11. Contrôles d'appartenance et de rôle dans la **couche service**, pas seulement dans les contrôleurs.

## Conventions de code
- Contrôleurs minces (validation, appel de service, mapping DTO) ; logique dans `service` ; accès données dans `repository`.
- Injection par constructeur ; `val` par défaut ; pas de `!!` ; exceptions métier dans `exception/` ; erreurs HTTP en `ProblemDetail`.
- Identifiants en `String` ; dates : `Instant` (UTC) et `LocalDate` ; fuseau d'affichage `Africa/Douala`.
- Messages et libellés exposés : bilingues FR / EN.
- Tests : JUnit 5, Testcontainers (Mongo en replica set), un test par règle métier citée (BR-xx).

## Commandes
```
./gradlew build          # compile et tests
./gradlew test
./gradlew bootRun
```

## À la fin de chaque tâche
Compile, lance les tests, puis résume : ce qui a été fait, les règles appliquées (BR-xx), les décisions prises, les questions ouvertes ajoutées.
