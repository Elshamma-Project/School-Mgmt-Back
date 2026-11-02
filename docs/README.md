# Documentation du projet SMELS : bulletins et notes

Dossier de conception de la première étape : **le système de notes et la génération automatique des bulletins** (API Spring Boot / Kotlin / MongoDB).
L'authentification, les services, la configuration et les modules suivants viennent ensuite (voir `06-feuille-de-route.md`).

## Ordre de lecture
1. `01-analyse-bulletins.md` : décomposition des deux bulletins fournis (6ème francophone, Form 3 anglophone).
2. `02-domaine-metier.md` : glossaire, acteurs, relations, règles métier numérotées (BR-xx), parcours.
3. `03-modele-de-donnees.md` : collections, index, particularités Mongo + Kotlin, données de référence.
4. `04-regles-de-calcul.md` : formules vérifiées, arrondi, critères d'acceptation.
5. `05-roles-permissions.md` : rôles et matrice de permissions (conception).
6. `06-feuille-de-route.md` : étapes et prompts pour Cursor / Claude Code.
7. `07-questions-ouvertes.md` : ce qu'il reste à demander à l'école, et les décisions provisoires.

## Contenu livré
```
back-office/
├── AGENTS.md                      instructions pour Cursor / assistants IA
├── CLAUDE.md                      importe AGENTS.md (Claude Code)
├── docs/                          ce dossier
│   └── fixtures/                  bulletins anonymisés + valeurs attendues (tests)
└── src/main/kotlin/com/kobe/smels/model/
    ├── common/       AuditableDocument, TenantDocument, LocalizedText, ContactInfo, enums
    ├── school/       School, AcademicYear (+Term), Level, SchoolClass, Room
    ├── curriculum/   Subject, SubjectGroup, ClassSubject
    ├── people/       Staff, Student, Guardian, Enrollment
    ├── assessment/   EvaluationSession, Assessment, Grade, GradeAuditEntry
    ├── reporting/    GradingScheme, StudentPeriodReview, ReportCard, ReportPeriod
    └── security/     Role, UserAccount (squelette)
```

## Intégration dans votre projet
1. Copier `src/main/kotlin/com/kobe/smels/model/` dans le projet (le dossier `model` existe déjà et est vide).
2. Copier `docs/`, `AGENTS.md` et `CLAUDE.md` à la racine du dépôt.
3. Ajouter les dépendances listées dans `03-modele-de-donnees.md` § 4, puis lancer `./gradlew compileKotlin`.

## Vérifications effectuées
- Les 37 lignes de matière des deux bulletins ont été recalculées (moyennes, totaux, groupes, moyennes générales, trimestres, pourcentages) : voir `01` § 6 et `04`.
- Le modèle Kotlin compile (Kotlin 2.0, 79 classes) contre des **annotations Spring simulées**, car cet environnement n'avait pas accès à Maven Central. Le comportement réel de Spring Data (mapping, index, `Decimal128`, audit) n'a donc **pas** été testé : à valider chez vous.
