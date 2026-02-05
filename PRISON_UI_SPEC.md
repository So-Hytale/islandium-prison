# Prison - Specification UI Joueur

> Document de reference pour la conception de l'UI generale du mode Prison.
> Le joueur accede a TOUT via `/pr` (menu principal). Aucune commande a taper manuellement.

---

## 1. Vue d'ensemble

### Principe
- **Une seule commande** : `/pr` ouvre le **Menu Principal Prison**
- Tout est accessible par **blocs/boutons cliquables avec icones**
- Le joueur ne tape JAMAIS de commande directement
- Un **HUD permanent** affiche les infos essentielles en overlay

### Architecture UI

```
/pr  -->  MENU PRINCIPAL
           |
           +-- [Icone Pioche]     MINES
           +-- [Icone Etoile]     RANKUP
           +-- [Icone Coffre]     VENDRE
           +-- [Icone Marteau]    UPGRADES
           +-- [Icone Maison]     CELLULE
           +-- [Icone Trophee]    CLASSEMENTS
           +-- [Icone Diamant]    PRESTIGE
           +-- [Icone Joueur]     PROFIL / STATS
```

---

## 2. HUD Permanent (toujours visible)

Affiche en overlay quand le joueur est connecte. Deja implemente dans `PrisonHud.java`.

### Donnees affichees

| Element           | ID UI             | Source                                          | Format            |
|-------------------|-------------------|-------------------------------------------------|-------------------|
| Rang actuel       | `#RankValue`      | `RankManager.getPlayerRank(uuid)`               | Lettre (A-Z/FREE) |
| Prestige          | `#PrestigeValue`  | `RankManager.getPlayerPrestige(uuid)`           | Nombre (cache si 0)|
| Multiplicateur    | `#MultiplierValue`| `RankManager.getPlayerMultiplier(uuid)`         | "x1.50"           |
| Mine actuelle     | `#MineValue`      | Detection position dans mines                   | Nom mine ou "---" |
| Solde             | `#BalanceValue`   | `EconomyService.getBalance(uuid)`               | "12.5K" / "1.3M"  |
| Blocs mines       | `#BlocksValue`    | `StatsManager.getBlocksMined(uuid)`             | "45.2K"           |
| Fortune           | `#FortuneLabel`   | `StatsManager.getFortuneLevel(uuid)`            | "F0" a "F5"       |
| Efficacite        | `#EfficiencyLabel`| `StatsManager.getEfficiencyLevel(uuid)`         | "E0" a "E5"       |
| Auto-Sell         | `#AutoSellLabel`  | `StatsManager.isAutoSellEnabled(uuid)`          | "AS:ON/OFF/---"   |

---

## 3. Menu Principal (`/pr`)

### Layout
Page interactive avec **8 boutons** disposes en grille (2x4 ou style hub).
Chaque bouton = un bloc 3D avec icone + label.

### Boutons

| #  | Label         | Icone suggere      | Action au clic              | Couleur theme |
|----|---------------|--------------------|-----------------------------|---------------|
| 1  | **Mines**     | Pioche en diamant  | Ouvre la page MINES         | Bleu          |
| 2  | **Rankup**    | Etoile dorée       | Ouvre la page RANKUP        | Or/Jaune      |
| 3  | **Vendre**    | Coffre             | Execute le SELL ALL         | Vert          |
| 4  | **Upgrades**  | Enclume/Marteau    | Ouvre la page UPGRADES      | Violet        |
| 5  | **Cellule**   | Maison/Porte       | Ouvre la page CELLULE       | Orange        |
| 6  | **Classement**| Trophee            | Ouvre la page CLASSEMENT    | Bronze/Dore   |
| 7  | **Prestige**  | Diamant/Eclair     | Ouvre la page PRESTIGE      | Magenta       |
| 8  | **Profil**    | Tete de joueur     | Ouvre la page PROFIL/STATS  | Cyan          |

---

## 4. Page MINES

### Fonctionnalites joueur (source: `MineCommand.java`, `MinesCommand.java`, `MineManager.java`)

#### Liste des mines
Affiche toutes les mines existantes avec leur statut d'acces pour le joueur.

| Donnee par mine      | Source                                                    |
|----------------------|-----------------------------------------------------------|
| Nom de la mine       | `mine.getDisplayName()`                                   |
| ID de la mine        | `mine.getId()` (A, B, C... Z, FREE)                      |
| Rang requis          | `mine.getRequiredRank()`                                  |
| Accessible ?         | `MineManager.canAccess(uuid, mine)` (compare rang joueur) |
| Blocs restants       | `mine.getRemainingPercentage()` → barre de progression    |
| Configuree ?         | `mine.isConfigured()`                                     |

#### Actions joueur

| Action                    | Backend                                              | Condition                           |
|---------------------------|------------------------------------------------------|-------------------------------------|
| Se teleporter a une mine  | `TeleportService.teleportWithWarmup(player, spawn)`  | `canAccess(uuid, mine)` + mine a un spawn |
| Voir la mine de son rang  | `MineManager.getHighestAccessibleMine(uuid)`         | Toujours                            |

#### UI proposee
- **Liste scrollable** des mines (A a Z + FREE)
- Chaque mine = une ligne/carte avec :
  - Icone de bloc representatif
  - Nom + rang requis
  - Barre de progression (% blocs restants) avec couleur (vert > jaune > rouge)
  - Icone cadenas si pas acces, check vert si acces
  - **Bouton "Teleporter"** (grise si pas acces)
- Bouton rapide **"Ma Mine"** en haut (teleporte a la mine du rang actuel)

---

## 5. Page RANKUP

### Fonctionnalites joueur (source: `RankupCommand.java`, `RanksCommand.java`, `PrisonRankManager.java`)

#### Systeme de rangs
- Progression : **A -> B -> C -> ... -> Z -> FREE**
- 27 rangs au total (26 lettres + FREE)
- Chaque rang debloque une nouvelle mine et un multiplicateur de gains

#### Donnees du rang

| Donnee               | Source                                                       |
|----------------------|--------------------------------------------------------------|
| Rang actuel          | `RankManager.getPlayerRank(uuid)`                            |
| Index du rang        | `RankManager.getRankIndex(rank)` (0=A, 25=Z, 26=FREE)       |
| Rang suivant         | `Config.getNextRank(currentRankId)`                          |
| Prix du rankup       | `RankManager.getRankupPrice(uuid, nextRank)` (avec prestige) |
| Solde joueur         | `EconomyService.getBalance(uuid)`                            |
| Multiplicateur actuel| `rankInfo.multiplier` (1.0 + index * 0.1)                   |
| Peut rankup ?        | `RankManager.canRankup(uuid)` → SUCCESS / NOT_ENOUGH_MONEY / MAX_RANK |

#### Tableau des prix (config par defaut)

| Rang | Prix base | Multiplicateur |
|------|-----------|----------------|
| A    | 1,000$    | x1.0           |
| B    | 1,500$    | x1.1           |
| C    | 2,250$    | x1.2           |
| ...  | x1.5 chaque | +0.1 chaque  |
| Z    | ~25.3M$   | x3.5           |
| FREE | 100M$     | x5.0           |

> Note : Le prix augmente de +50% par prestige : `price * (1 + prestige * 0.5)`

#### Actions joueur

| Action              | Backend                                  | Resultat                          |
|---------------------|------------------------------------------|-----------------------------------|
| Rankup (1 rang)     | `RankManager.rankup(uuid)`               | Deduit argent, change rang        |
| Max Rankup          | `RankManager.maxRankup(uuid)`            | Rankup en boucle tant que possible|

#### UI proposee
- **Affichage du rang actuel** en grand (lettre + nom)
- **Barre de progression** vers le rang suivant (solde / prix)
- **Bouton "RANKUP"** central
  - Vert si assez d'argent
  - Rouge/grise si pas assez
  - Affiche le prix en surbrillance
- **Bouton "MAX RANKUP"** (rankup en boucle)
- **Liste des rangs** en dessous (scrollable) montrant :
  - Chaque rang avec prix, multiplicateur, et statut (debloque/actuel/verrouille)

---

## 6. Page VENDRE (Sell)

### Fonctionnalites joueur (source: `SellCommand.java`, `SellAllCommand.java`, `SellService.java`)

#### Systeme de vente
Les blocs mines ont une valeur configuree. Le joueur vend depuis son inventaire.

#### Valeurs des blocs (config par defaut)

| Bloc               | Prix base |
|--------------------|-----------|
| Cobblestone        | 1$        |
| Stone              | 2$        |
| Coal Ore           | 5$        |
| Iron Ore           | 15$       |
| Gold Ore           | 50$       |
| Diamond Ore        | 200$      |
| Emerald Ore        | 500$      |
| Ancient Debris     | 1,000$    |

> Prix final = `prix_base * quantite * multiplicateur_rang * multiplicateur_prestige * blockSellMultiplier`

#### Calcul du multiplicateur total

```
multiplicateur = (rankInfo.multiplier) + (prestige * 0.25)
multiplicateur *= config.blockSellMultiplier
```

- `rankInfo.multiplier` : 1.0 (rang A) a 5.0 (rang FREE)
- Bonus prestige : +25% par niveau de prestige
- `blockSellMultiplier` : multiplicateur global (config, defaut 1.0)

#### Actions joueur

| Action              | Backend                                              | Description                        |
|---------------------|------------------------------------------------------|------------------------------------|
| Vendre tout         | `SellService.sellFromInventory(uuid, player, null)`  | Vend tous les blocs vendables (inventaire + hotbar) |
| Vendre un type      | `SellService.sellFromInventory(uuid, player, filter)`| Vend un seul type de bloc          |
| Auto-sell (passif)  | `SellService.autoSell(uuid, blockId, count)`         | Automatique au minage si active    |

#### Resultat de vente (`SellResult`)

| Champ            | Description                             |
|------------------|-----------------------------------------|
| `totalEarned`    | Montant total gagne (BigDecimal)        |
| `totalBlocksSold`| Nombre total de blocs vendus            |
| `soldItems`      | Map<String, Integer> des items vendus   |

#### UI proposee
- **Bouton "VENDRE TOUT"** central (gros, vert)
  - Au clic : execute sellAll, affiche le resume
- **Resume de vente** :
  - Liste des blocs vendus avec quantite
  - Total gagne
  - Multiplicateur applique
- **Preview de la valeur** de l'inventaire actuel (sans vendre)
- **Liste des prix** des blocs (tableau de reference)
- Statut Auto-Sell avec toggle ON/OFF

---

## 7. Page UPGRADES

### Fonctionnalites joueur (source: `UpgradeCommand.java`, `PickaxeUpgradeManager.java`)

#### 3 types d'upgrades

##### Fortune (5 niveaux)
Augmente la chance de doubler les drops.

| Niveau | Effet              | Prix passage |
|--------|--------------------|--------------|
| 0      | Pas de bonus       | -            |
| 1      | 10% chance 2x drop | 5,000$       |
| 2      | 20% chance 2x drop | 15,000$      |
| 3      | 30% chance 2x drop | 50,000$      |
| 4      | 40% chance 2x drop | 150,000$     |
| 5 (max)| 50% chance 2x drop | 500,000$     |

##### Efficacite (5 niveaux)
Augmente la vitesse de minage.

| Niveau | Effet        | Prix passage |
|--------|--------------|--------------|
| 0      | Pas de bonus | -            |
| 1      | +20% vitesse | 3,000$       |
| 2      | +40% vitesse | 10,000$      |
| 3      | +60% vitesse | 30,000$      |
| 4      | +80% vitesse | 100,000$     |
| 5 (max)| +100% vitesse| 300,000$     |

##### Auto-Sell (achat unique)
Les blocs mines sont vendus automatiquement sans passer par l'inventaire.

| Etat     | Prix     | Effet                                  |
|----------|----------|----------------------------------------|
| Non achete| 100,000$ | -                                      |
| Achete   | -        | Toggle ON/OFF, vente auto au minage    |

#### Actions joueur

| Action               | Backend                                         | Resultat              |
|----------------------|-------------------------------------------------|-----------------------|
| Acheter Fortune +1   | `UpgradeManager.purchaseFortune(uuid)`          | SUCCESS / NOT_ENOUGH / MAX |
| Acheter Efficacite +1| `UpgradeManager.purchaseEfficiency(uuid)`       | SUCCESS / NOT_ENOUGH / MAX |
| Acheter Auto-Sell    | `UpgradeManager.purchaseAutoSell(uuid)`         | SUCCESS / NOT_ENOUGH / ALREADY |
| Toggle Auto-Sell     | `UpgradeManager.toggleAutoSell(uuid)`           | true (ON) / false (OFF) |

#### UI proposee
- **3 sections** (Fortune / Efficacite / Auto-Sell)
- Chaque upgrade montre :
  - Icone representative
  - Niveau actuel / max (barre de progression)
  - Description de l'effet actuel
  - **Bouton "Ameliorer"** avec prix affiche
  - Grise si max ou pas assez d'argent
- Auto-Sell : bouton toggle ON/OFF avec indicateur visuel

---

## 8. Page CELLULE

### Fonctionnalites joueur (source: `CellCommand.java`, `CellManager.java`, `Cell.java`)

#### Systeme de cellules
- Chaque joueur peut posseder **1 cellule** (configurable)
- Zone privee avec spawn, coins, verrouillage
- Systeme de location (expiration configurable)

#### Donnees d'une cellule

| Donnee          | Source                           | Description                         |
|-----------------|----------------------------------|-------------------------------------|
| ID              | `cell.getId()`                   | Identifiant unique                  |
| Proprietaire    | `cell.getOwner()`                | UUID du joueur                      |
| Spawn           | `cell.getSpawnPoint()`           | Point de teleportation              |
| Verrouillee     | `cell.isLocked()`                | Etat du verrou                      |
| Date d'achat    | `cell.getPurchaseTime()`         | Timestamp                           |
| Expiration      | `cell.getExpirationTime()`       | 0 = permanent, sinon timestamp      |
| Expiree ?       | `cell.isExpired()`               | Calcul auto                         |

#### Configuration

| Parametre          | Valeur defaut | Source                          |
|--------------------|---------------|---------------------------------|
| Prix cellule       | 5,000$        | `config.getDefaultCellPrice()`  |
| Max par joueur     | 1             | `config.getMaxCellsPerPlayer()` |
| Duree location     | 7 jours       | `config.getCellRentDurationDays()` |

#### Actions joueur

| Action                | Backend                                        | Condition                           |
|-----------------------|------------------------------------------------|-------------------------------------|
| Acheter une cellule   | `CellManager.purchaseCell(uuid, name)`         | Pas deja de cellule + assez d'argent |
| Se teleporter         | `CellManager.teleportToCell(player)`           | Possede une cellule                 |
| Voir infos            | `CellManager.getPlayerCell(uuid)`              | -                                   |

#### Resultats d'achat (`PurchaseResult`)

| Resultat            | Description                      |
|---------------------|----------------------------------|
| SUCCESS             | Cellule achetee                  |
| NOT_ENOUGH_MONEY    | Pas assez d'argent               |
| ALREADY_HAS_CELL    | Deja une cellule                 |
| NO_CELLS_AVAILABLE  | Aucune cellule libre             |
| CELL_NOT_AVAILABLE  | Cellule specifique occupee       |

#### UI proposee
- **Si pas de cellule** :
  - Message "Tu ne possedes pas de cellule"
  - **Bouton "Acheter"** avec prix affiche
  - Info sur la duree de location
- **Si cellule possedee** :
  - ID de la cellule
  - Statut verrouillage
  - Temps restant avant expiration (ou "Permanent")
  - **Bouton "Teleporter"**
  - **Bouton "Verrouiller/Deverrouiller"**

---

## 9. Page CLASSEMENT

### Fonctionnalites joueur (source: `TopCommand.java`)

#### 3 classements disponibles

##### Top Richesse (Balance)

| Donnee   | Source                                    |
|----------|-------------------------------------------|
| Joueurs  | `EconomyService.getTopPlayers(10)`        |
| Solde    | `EconomyService.getBalance(uuid)`         |
| Nom      | `StatsManager.getPlayerName(uuid)`        |

##### Top Blocs Mines

| Donnee       | Source                               |
|--------------|--------------------------------------|
| Blocs mines  | `playerStats.blocksMined`            |
| Nom          | `playerStats.playerName`             |
| Tri          | Par blocksMined decroissant          |

##### Top Prestige

| Donnee    | Source                                  |
|-----------|-----------------------------------------|
| Prestige  | `RankManager.getPlayerPrestige(uuid)`   |
| Rang      | `RankManager.getPlayerRank(uuid)`       |
| Nom       | `playerStats.playerName`                |
| Tri       | Par prestige decroissant puis rang      |

#### Config

- Top 10 joueurs par classement
- Cache de 60 secondes entre refreshs

#### UI proposee
- **3 onglets** en haut : Richesse / Blocs / Prestige
- Top 10 avec :
  - Podium visuel pour les 3 premiers (or, argent, bronze)
  - Liste pour les 4-10
  - Position du joueur actuel mise en evidence
- Refresh auto ou bouton de refresh

---

## 10. Page PRESTIGE

### Fonctionnalites joueur (source: `PrestigeCommand.java`, `PrisonRankManager.java`)

#### Systeme de prestige
- Disponible quand le joueur atteint le rang **FREE**
- Reset le rang a **A** et l'argent au solde de depart
- Donne des bonus permanents

#### Effets du prestige

| Effet                        | Formule                                  |
|------------------------------|------------------------------------------|
| Bonus multiplicateur gains   | +25% par niveau de prestige              |
| Prix rankup augmentes        | prix * (1 + prestige * 0.5)              |
| Reset rang                   | Retour a A                               |
| Reset argent                 | Retour au solde de depart (config core)  |

#### Conditions

| Condition         | Verification                              |
|-------------------|-------------------------------------------|
| Peut prestige ?   | `RankManager.canPrestige(uuid)` → rang == "FREE" |

#### Actions joueur

| Action     | Backend                           | Resultat                              |
|------------|-----------------------------------|---------------------------------------|
| Prestige   | `RankManager.prestige(uuid)`      | Reset rang+argent, increment prestige |

#### UI proposee
- **Affichage prestige actuel** (grand nombre)
- **Recapitulatif des effets** :
  - Bonus actuel (+X% gains)
  - Bonus apres prestige
- **Avertissement** bien visible :
  - "Ton rang sera reinitialise a A"
  - "Ton argent sera reinitialise"
- **Bouton "PRESTIGE"** (vert si disponible, grise sinon)
  - Avec confirmation avant execution
- Si rang != FREE : message "Tu dois atteindre le rang FREE"

---

## 11. Page PROFIL / STATS

### Fonctionnalites joueur (source: `PlayerStatsManager.java`)

#### Donnees du profil

| Donnee                | Source                                     | Format              |
|-----------------------|--------------------------------------------|---------------------|
| Nom du joueur         | `StatsManager.getPlayerName(uuid)`         | String              |
| Rang actuel           | `RankManager.getPlayerRank(uuid)`          | A-Z / FREE          |
| Prestige              | `RankManager.getPlayerPrestige(uuid)`      | Nombre              |
| Multiplicateur total  | `RankManager.getPlayerMultiplier(uuid)`    | x1.50               |
| Solde                 | `EconomyService.getBalance(uuid)`          | Formate             |
| Blocs mines (total)   | `StatsManager.getBlocksMined(uuid)`        | Nombre formate      |
| Argent gagne (total)  | `StatsManager.getTotalMoneyEarned(uuid)`   | Montant formate     |
| Temps joue            | `StatsManager.getTimePlayed(uuid)`         | HH:MM:SS ou jours   |
| Fortune level         | `StatsManager.getFortuneLevel(uuid)`       | 0-5                 |
| Efficacite level      | `StatsManager.getEfficiencyLevel(uuid)`    | 0-5                 |
| Auto-sell achete      | `StatsManager.hasAutoSell(uuid)`           | Oui/Non             |
| Auto-sell actif       | `StatsManager.isAutoSellEnabled(uuid)`     | ON/OFF              |

#### UI proposee
- **Carte joueur** avec avatar/tete
- Section **Progression** : rang, prestige, multiplicateur
- Section **Stats** : blocs mines, argent gagne, temps joue
- Section **Upgrades** : niveaux fortune, efficacite, auto-sell
- Peut inclure un graphique de progression si possible

---

## 12. Mecaniques passives (pas d'UI dediee mais impactent le jeu)

### Minage (source: `BlockBreakListener.java`)

Quand un joueur casse un bloc dans une mine :

1. **Verification mine** : le bloc est-il dans une mine configuree ?
2. **Mode naturel** : si active, seuls les blocs de la composition sont cassables
3. **Decremente** le compteur de blocs restants de la mine
4. **Verification rang** : le joueur a-t-il acces a cette mine ?
5. **Stats** : incremente blocksMined du joueur
6. **Fortune** : calcule le bonus de drop (tier * 10% chance de 2x)
7. **Auto-sell** : si active, vend directement sans passer par l'inventaire
   - Affiche un message discret : "+X$ (auto-sell)"
8. Si auto-sell desactive : les blocs vont dans l'inventaire normalement

### Reset des mines (source: `MineManager.java`)

- Reset automatique toutes les X minutes (defaut: 15 min)
- Reset force si < 20% de blocs restants
- Avertissement 30 secondes avant le reset (configurable)
- Broadcast aux joueurs dans la mine

### Connexion joueur (source: `PrisonJoinListener.java`)

A la connexion :
1. Enregistre le timestamp de connexion
2. Enregistre le nom du joueur
3. Initialise le rang si nouveau
4. Affiche le HUD Prison
5. Envoie les infos de rang/prestige/multiplicateur en chat

### Deconnexion joueur (source: `PrisonQuitListener.java`)

A la deconnexion :
1. Met a jour le temps joue total
2. Nettoie le HUD

---

## 13. Resume des donnees necessaires par page

| Page        | Donnees requises                                                  |
|-------------|-------------------------------------------------------------------|
| HUD         | rang, prestige, multiplicateur, mine, solde, blocs, fortune, efficiency, autosell |
| Menu        | Aucune (juste les boutons)                                        |
| Mines       | Liste mines, rang joueur, canAccess par mine, % restant           |
| Rankup      | Rang actuel, rang suivant, prix, solde, multiplicateur, liste rangs |
| Vendre      | Inventaire, blockValues, multiplicateur, SellResult                |
| Upgrades    | Fortune/Efficiency/AutoSell levels + prix prochain tier            |
| Cellule     | PlayerCell (ou null), prix, temps restant                          |
| Classement  | Top10 balance/blocks/prestige                                      |
| Prestige    | Prestige actuel, rang actuel (== FREE ?), bonus                    |
| Profil      | Toutes les stats joueur                                            |

---

## 14. Mapping commandes existantes -> Boutons UI

| Commande existante    | Equivalent dans l'UI                                |
|-----------------------|-----------------------------------------------------|
| `/mine [nom]`         | Page Mines > bouton "Teleporter" sur une mine        |
| `/mines`              | Page Mines (liste)                                   |
| `/rankup`             | Page Rankup > bouton "RANKUP"                        |
| `/ranks`              | Page Rankup > liste des rangs                        |
| `/sell [type]`        | Page Vendre > bouton "VENDRE TOUT" ou filtre par type|
| `/sellall`            | Page Vendre > bouton "VENDRE TOUT"                   |
| `/upgrade [type]`     | Page Upgrades > bouton par type                      |
| `/cell tp`            | Page Cellule > bouton "Teleporter"                   |
| `/cell buy`           | Page Cellule > bouton "Acheter"                      |
| `/cell info`          | Page Cellule (affichage infos)                       |
| `/top [type]`         | Page Classement > onglets                            |
| `/prestige`           | Page Prestige > bouton "PRESTIGE"                    |

---

## 15. Services et API utilises

| Service            | Classe                  | Utilisation                        |
|--------------------|-------------------------|------------------------------------|
| Economie           | `EconomyService`        | Solde, ajout/retrait argent        |
| Teleportation      | `TeleportService`       | TP avec warmup                     |
| Joueurs            | `PlayerManager`         | Obtenir IslandiumPlayer            |
| Config principale  | `PrisonConfig`          | Tous les parametres configurables  |
| Ranks              | `PrisonRankManager`     | Gestion des rangs et prestige      |
| Stats              | `PlayerStatsManager`    | Stats et niveaux d'upgrades        |
| Vente              | `SellService`           | Logique de vente                   |
| Upgrades           | `PickaxeUpgradeManager` | Achat et gestion des upgrades      |
| Cellules           | `CellManager`           | CRUD cellules                      |
| Mines              | `MineManager`           | CRUD mines, reset, acces           |
| UI                 | `PrisonUIManager`       | Gestion HUD et pages               |

---

## 16. Notes techniques

### UI existante
- **HUD** : `PrisonHud.java` + `PrisonHud.ui` - fonctionne via MultipleHUD
- **Page admin Mine Manager** : `MineManagerPage.java` + `MineManagerPage.ui` - page interactive admin
- Les pages utilisent `InteractiveCustomUIPage` avec `UICommandBuilder` / `UIEventBuilder`
- Les events UI sont geres via `CustomUIEventBindingType` (Activating, ValueChanged)
- Les donnees transitent via un `PageData` avec `BuilderCodec`

### Pattern a suivre pour les nouvelles pages
1. Creer la classe Java etendant `InteractiveCustomUIPage<PageData>`
2. Creer le fichier `.ui` dans `resources/Common/UI/Custom/Pages/Prison/`
3. Implementer `build()` pour le rendu initial
4. Implementer `handleDataEvent()` pour les interactions
5. Definir un `PageData` avec codec pour les donnees d'events
6. Enregistrer l'ouverture dans `PrisonUIManager`

### Fichiers a creer

```
src/main/java/com/islandium/prison/ui/pages/
  PrisonMainMenuPage.java      -- Menu principal
  MinesPage.java               -- Liste des mines joueur
  RankupPage.java              -- Page rankup
  SellPage.java                -- Page vente
  UpgradesPage.java            -- Page upgrades
  CellPage.java                -- Page cellule
  LeaderboardPage.java         -- Page classements
  PrestigePage.java            -- Page prestige
  ProfilePage.java             -- Page profil/stats

src/main/resources/Common/UI/Custom/Pages/Prison/
  PrisonMainMenu.ui
  MinesPage.ui
  RankupPage.ui
  SellPage.ui
  UpgradesPage.ui
  CellPage.ui
  LeaderboardPage.ui
  PrestigePage.ui
  ProfilePage.ui
```
