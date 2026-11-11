# Notes layout — maquette-bulletins-kobe.pdf

Source : `docs/maquette-bulletins-kobe.pdf` (Skia/Chromium, **3 pages** A4 portrait).
Aligné sur `docs/01-analyse-bulletins.md` § 3 (Z1–Z10).

## Pages
1. Bulletin **annuel FR** (référence design principale)
2. Bulletin **annuel EN** (même structure, libellés EN ; photo possible en Z2)
3. Bulletin **trimestre** (Eval1/Eval2, compétences, rang matière)

## Zones (à respecter dans ReportCardPdfService)

| Zone | Contenu |
|---|---|
| Z1 | En-tête bilingue miroir (FR gauche / EN droite), logo centre, année, titre |
| Z2 | Identité élève + classe ; photo si EN + photoFileId |
| Z3 | Tableau : Discipline(+ens.) \| DEV1..n \| Moy/20 \| Coef \| Total \| App |
| Z4 | Ligne groupe (fond distinct) : TOTAL GROUPE \| Σcoef \| Σtotal \| Moyenne |
| Z5–Z9 | Bande basse 5 blocs : résultat, rappels trim., profil classe, travail, conduite |
| Z10 | Visas parent / conseil / chef ; disclaimer école ; code 13 chiffres ; horodatage |

## Palette (spec design KOBE)
| Rôle | Hex |
|---|---|
| Titres / texte fort | `#0F3D4C` |
| En-tête tableau | `#1C6E7D` |
| Lignes de groupe / titres de cadres | `#E6F0F2` |
| Pastille mention générale | `#C98A1B` |
| Notes &lt; seuil (passMark) | `#B3261E` |
| Secondaire | `#55707A` / `#9DB4BB` |

## Contraintes
- Une seule page A4 portrait ; marges 8 mm ; ajuster police `minFontSizePt`–`maxFontSizePt` (plancher lecture 5,2 pt)
- Pied : « Développé par KOBE Corporation » uniquement (pas de marque tierce / USSD)
- Notes sous le seuil de validation (`GradingScheme.passMark`) en rouge discret
