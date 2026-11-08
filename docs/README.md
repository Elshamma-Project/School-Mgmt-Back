# Documentation du projet SMELS : bulletins, notes, emploi du temps

Dossier de conception : **notes, bulletins (une page A4) et emploi du temps (une page A4)** d'une école bilingue du Cameroun (API Spring Boot / Kotlin / MongoDB), organisé en **modules qui deviendront des microservices**.
L'authentification, les services, la configuration et les modules suivants viennent ensuite (voir `06-feuille-de-route.md`).

## Ordre de lecture
1. `01-analyse-bulletins.md` : 7 bulletins (annuels et trimestre) et résumé de l'emploi du temps.
2. `02-domaine-metier.md` : glossaire, acteurs, relations, règles métier numérotées (BR-xx), parcours.
3. `03-modele-de-donnees.md` : collections par module, index, particularités Mongo + Kotlin, données de référence.
4. `04-regles-de-calcul.md` : formules vérifiées, arrondi, critères d'acceptation (calculs dans les services).
5. `05-roles-permissions.md` : rôles et matrice de permissions (conception).
6. `06-feuille-de-route.md` : étapes et prompts prêts à coller pour Cursor / Claude Code.
7. `07-questions-ouvertes.md` : réponses reçues, questions encore ouvertes, décisions provisoires, documents à demander.
8. `08-architecture-modulaire.md` : modules, frontières, événements, passage futur aux microservices.
9. `09-emploi-du-temps.md` : module timetable.

## Contenu livré
```
back-office/
├── AGENTS.md                       instructions pour Cursor / assistants IA
├── CLAUDE.md                       importe AGENTS.md (Claude Code)
├── docs/                           ce dossier
│   └── fixtures/                   7 bulletins + grille et 2 emplois du temps anonymisés (tests)
└── src/main/kotlin/com/kobe/smels/
    ├── model/<module>/             documents (écrits)
    ├── repository/<module>/        (vide : .gitkeep)
    ├── service/<module>/           (vide : .gitkeep) ; y mettre api/ pour l'interface publique du module
    ├── controller/<module>/        (vide)
    ├── dto/<module>/               (vide)
    ├── configuration/ exception/ shared/   (vides)
    └── modules : shared, user, academic, student, staff, grading, reporting, timetable
```

## Intégration dans votre projet
1. Copier `src/main/kotlin/com/kobe/smels/` dans le projet (en remplaçant le dossier `model` vide).
2. Copier `docs/`, `AGENTS.md` et `CLAUDE.md` à la racine du dépôt.
3. Ajouter les dépendances de `03-modele-de-donnees.md` § 4 puis lancer `./gradlew compileKotlin`.

## Vérifications effectuées
- 98 lignes de matière de 6 bulletins annuels recalculées : toutes retrouvées avec la règle « moyenne des moyennes de trimestre arrondies » (`04`) ; 16 blocs de groupe retrouvés avec la méthode de l'ancien logiciel ; bulletin de trimestre de Form 1 vérifié (total, moyenne, pourcentage).
- Le modèle (106 classes) compile avec Kotlin 2.0 contre des **annotations Spring simulées** : le comportement réel de Spring Data (mapping, index, `Decimal128`, audit) n'est **pas** testé ; à valider chez vous.
