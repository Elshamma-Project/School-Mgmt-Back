# SMELS back-office : instructions pour les assistants IA

> Lu par Cursor (`AGENTS.md`) et importé par Claude Code (`CLAUDE.md`).
> Lis-le en entier avant toute tâche, puis les documents de `../../Docs/back-office/docs` cités plus bas.

## Projet
API REST d'un logiciel de gestion scolaire pour un établissement **bilingue (français / anglais)** du Cameroun : élèves, enseignants, matières, **fiches de notes**, génération des **bulletins**, **emploi du temps**, puis cartes d'élèves et inscriptions.
Utilisateurs non techniques ; données de **mineurs**. **Tout doit être paramétrable depuis l'interface graphique, dans les deux langues.**

## Stack
- Kotlin + Spring Boot, Java 21, Gradle Kotlin DSL ; MongoDB (replica set, même à un nœud) ; Spring Data MongoDB
- **Monolithe modulaire aujourd'hui, microservices demain** (voir `docs/08-architecture-modulaire.md`)
- Front séparé en Next.js (hors dépôt) ; Docker, Nginx, GitHub Actions ; déploiement sur VPS
- Package racine : `com.kobe.smels` ; couches : `configuration`, `controller`, `dto`, `exception`, `model`, `repository`, `service`, `shared`

## Modules (même sous-dossier dans chaque couche)
`iam` · `academic` · `student` · `staff` · `grading` · `reporting` · `timetable` · (`shared` : noyau commun)
Exemple : `model/grading/`, `repository/grading/`, `service/grading/` (+ `service/grading/api/` = interface publique), `controller/grading/`, `dto/grading/`.

## Où lire quoi
| Besoin | Document |
|---|---|
| Comprendre les bulletins | `docs/01-analyse-bulletins.md` |
| Règles métier (BR-xx), glossaire, parcours | `docs/02-domaine-metier.md` |
| Collections, index, Mongo/Kotlin, données de référence | `docs/03-modele-de-donnees.md` |
| Formules de calcul, arrondi, critères d'acceptation | `docs/04-regles-de-calcul.md` |
| Rôles et permissions | `docs/05-roles-permissions.md` |
| Plan et prompts | `docs/06-feuille-de-route.md` |
| Réponses reçues et questions ouvertes (Q-xx) | `docs/07-questions-ouvertes.md` |
| Modules, frontières, événements | `docs/08-architecture-modulaire.md` |
| Emploi du temps | `docs/09-emploi-du-temps.md` |
| Jeux de test anonymisés | `docs/fixtures/*.json` |

## État
Le package `model` (8 sous-packages, 106 classes) est écrit, compilé contre des annotations simulées : à recompiler avec Gradle. Les dossiers des autres couches existent par module mais sont vides. Rien d'autre n'est implémenté : ni services, ni sécurité, ni configuration.

## Règles impératives
1. **Frontières de modules** : un module ne lit et n'écrit que ses collections ; il n'importe jamais le `repository` ni le `model` d'un autre module ; il passe par `service/<autre>/api` et les `dto/<autre>`. Pas de jointure entre modules, pas de transaction entre modules, pas de dépendance cyclique (graphe dans `docs/08`). Un test ArchUnit le vérifie.
2. **Références par identifiant** ; libellés copiés (figés) quand un module a besoin d'un nom d'un autre ; mises à jour par événements (`OutboxEvent`, catalogue dans `docs/08` § 4), traitement idempotent.
3. **Ne modifie pas `model` sans l'annoncer** et sans mettre à jour `docs/03-modele-de-donnees.md`. Si un champ ou une règle manque, propose-le.
4. **Jamais `copy()` sur un document persistant** ; modifier l'instance chargée puis `save()`.
5. **Tout `BigDecimal` stocké** porte `@Field(targetType = FieldType.DECIMAL128)` ; comparer avec `compareTo`.
6. **Toute requête filtre par `schoolId`** ; tout index composite commence par `schoolId`.
7. **Les calculs s'effectuent dans la couche `service`** (`GradeCalculationService`, `SequenceMarkService`, `ReportCardService`). `docs/04-regles-de-calcul.md` est la spécification ; ne pas créer de « moteur » séparé. Garder l'arithmétique dans des méthodes privées/internal sans accès base, testables avec `../../Docs/back-office/docs/fixtures`. **Aucun seuil ni règle codés en dur** s'ils existent dans `GradingScheme` ou `ReportTemplate`.
8. **Les notes ne s'écrivent que par `GradeService`**, qui applique BR-11 à BR-18, écrit un `GradeAuditEntry`, recalcule la `SequenceMark` et émet l'événement dans la même transaction. **Une note non saisie est `PENDING`, jamais 0.**
9. **Une seule page A4** : bulletin (A4 portrait) et emploi du temps (A4 paysage). Ajuster police et hauteurs de ligne ; échouer avec un message clair plutôt que produire une 2e page ; test automatique « le PDF a 1 page ». Seules les fiches de notes personnelles d'un enseignant peuvent s'étendre sur plusieurs pages.
10. **Bilingue** : tous les libellés (groupes, mentions, légendes, matières, messages) en FR et EN (`LocalizedText`) ; les documents se génèrent dans les deux langues, la langue de la section est prioritaire.
11. **Pas de DTO = pas d'API** : ne jamais exposer un document ; jamais de `passwordHash`.
12. **Aucune donnée réelle d'élève ou d'enseignant** dans le code, les tests, les logs, les fixtures ou Git ; pas d'identifiant d'élève dans les logs.
13. **Ne pas inventer une règle ambiguë** : `TODO(Q-xx)` référencé dans `docs/07-questions-ouvertes.md`, et le signaler.
14. **Aucune marque ni service d'un prestataire tiers** sur les documents (pas de « Powered by », pas de code USSD tiers).
15. Contrôles de rôle et d'appartenance dans la **couche service** de chaque module.

## Conventions de code
- Contrôleurs minces (validation, appel de service, mapping DTO) ; logique dans `service` ; données dans `repository`.
- Injection par constructeur ; `val` par défaut ; pas de `!!` ; exceptions métier dans `exception/` ; erreurs HTTP en `ProblemDetail`.
- Identifiants `String` ; dates `Instant` (UTC), `LocalDate`, `LocalTime`, `DayOfWeek` ; affichage `Africa/Douala`.
- Tests : JUnit 5, Testcontainers (Mongo replica set), un test par règle citée (BR-xx) ; données de `../../Docs/back-office/docs/fixtures`.

## Commandes
```
./gradlew build
./gradlew test
./gradlew bootRun
```

## À la fin de chaque tâche
Compile, lance les tests, puis résume : ce qui a été fait, règles appliquées (BR-xx), décisions prises, questions ouvertes ajoutées.
