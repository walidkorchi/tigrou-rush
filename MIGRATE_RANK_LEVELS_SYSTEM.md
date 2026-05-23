# TigrouRush — Rank System v2

## Overview

Replace the linear numeric level system (1–150, XP capped at ~238K cumulative) with a **36-rank prestige system** rendered via a CraftEngine `BitmapImage` spritesheet (3×12 grid). Each rank within a prestige is 25% harder to reach than the previous one, but the growth rate is **nerfed per prestige** to keep Gold accessible. Players start unranked and must earn 10,000 XP to achieve their first rank.

**Prestige nerf coefficients** (applied to the growth rate, not to XP earnings):
- Bronze: ×1.0 → all 12 ranks use the full ×1.25 multiplier
- Silver: ×0.8 → effective multiplier = 1 + 0.25 × 0.8 = **×1.20**
- Gold: ×0.5 → effective multiplier = 1 + 0.25 × 0.5 = **×1.125**

This means each prestige curves more gently than the last, so Gold Ruby III requires ~14K games instead of ~80K.

---

## Rank Structure

### Spritesheet Grid (3 rows × 12 columns)

Each row is a **prestige**; each column is a rank within that prestige, ordered left-to-right:

| Row | Prestige | Cols 0–2 | Cols 3–5 | Cols 6–8 | Cols 9–11 |
|-----|----------|----------|----------|----------|-----------|
| 0   | Bronze   | Emerald I–III | Amethyst I–III | Diamond I–III | Ruby I–III |
| 1   | Silver   | Emerald I–III | Amethyst I–III | Diamond I–III | Ruby I–III |
| 2   | Gold     | Emerald I–III | Amethyst I–III | Diamond I–III | Ruby I–III |

**Rank index** = `row × 12 + col` (0–35).  
- `rankIndex / 12` → prestige index (0=Bronze, 1=Silver, 2=Gold)  
- `rankIndex % 12` → position within prestige  
- `(rankIndex % 12) / 3` → gem index (0=Emerald, 1=Amethyst, 2=Diamond, 3=Ruby)  
- `(rankIndex % 12) % 3` → gem level (0→I, 1→II, 2→III), displayed as +1

### Unranked State

A player with `totalXP < 10,000` has `rankIndex = -1`. They are "unranked" — no rank image is shown in chat/scoreboard. Instead, a progress bar toward the first rank is displayed.

---

## XP Thresholds

### Formula

Each prestige has its own effective multiplier: `effMultiplier = 1 + (RANK_MULTIPLIER - 1) × PRESTIGE_NERF[prestige]`

| Prestige | Nerf | effMultiplier | Formula within prestige |
|----------|------|---------------|------------------------|
| Bronze (ranks 0–11) | ×1.0 | ×1.25 | `threshold(n) = 10_000 × 1.25ⁿ` |
| Silver (ranks 12–23) | ×0.8 | ×1.20 | `threshold(12+k) = threshold(11) × 1.20^(k+1)` |
| Gold (ranks 24–35) | ×0.5 | ×1.125 | `threshold(24+k) = threshold(23) × 1.125^(k+1)` |

Thresholds are cumulative — the player's `totalXP` must reach or exceed a threshold to attain that rank.

### Full Table

| # | Prestige | Gem | Lvl | Eff. mult. | Cumul. XP | XP → next | ~2v2 games |
|---|----------|-----|-----|------------|-----------|-----------|------------|
| -1 | (unranked) | — | — | — | 0 | 10,000 | 0 |
| 0 | Bronze | Emeraude | I | ×1.250 | 10,000 | 2,500 | 32 |
| 1 | Bronze | Emeraude | II | ×1.250 | 12,500 | 3,125 | 40 |
| 2 | Bronze | Emeraude | III | ×1.250 | 15,625 | 3,906 | 50 |
| 3 | Bronze | Améthyste | I | ×1.250 | 19,531 | 4,883 | 63 |
| 4 | Bronze | Améthyste | II | ×1.250 | 24,414 | 6,103 | 79 |
| 5 | Bronze | Améthyste | III | ×1.250 | 30,518 | 7,630 | 99 |
| 6 | Bronze | Diamant | I | ×1.250 | 38,148 | 9,537 | 123 |
| 7 | Bronze | Diamant | II | ×1.250 | 47,685 | 11,921 | 154 |
| 8 | Bronze | Diamant | III | ×1.250 | 59,606 | 14,902 | 193 |
| 9 | Bronze | Rubis | I | ×1.250 | 74,508 | 18,627 | 241 |
| 10 | Bronze | Rubis | II | ×1.250 | 93,135 | 23,284 | 302 |
| 11 | Bronze | Rubis | III | ×1.250 | 116,419 | 23,284 | 377 |
| 12 | Argent | Emeraude | I | ×1.200 | 139,703 | 23,284 | 453 |
| 13 | Argent | Emeraude | II | ×1.200 | 167,644 | 27,941 | 544 |
| 14 | Argent | Emeraude | III | ×1.200 | 201,173 | 33,529 | 653 |
| 15 | Argent | Améthyste | I | ×1.200 | 241,408 | 40,235 | 783 |
| 16 | Argent | Améthyste | II | ×1.200 | 289,690 | 48,282 | 940 |
| 17 | Argent | Améthyste | III | ×1.200 | 347,628 | 57,938 | 1,128 |
| 18 | Argent | Diamant | I | ×1.200 | 417,154 | 69,526 | 1,354 |
| 19 | Argent | Diamant | II | ×1.200 | 500,585 | 83,431 | 1,625 |
| 20 | Argent | Diamant | III | ×1.200 | 600,702 | 100,117 | 1,950 |
| 21 | Argent | Rubis | I | ×1.200 | 720,842 | 120,140 | 2,340 |
| 22 | Argent | Rubis | II | ×1.200 | 865,010 | 144,168 | 2,808 |
| 23 | Argent | Rubis | III | ×1.200 | 1,038,012 | 129,752 | 3,370 |
| 24 | Or | Emeraude | I | ×1.125 | 1,167,764 | 145,970 | 3,791 |
| 25 | Or | Emeraude | II | ×1.125 | 1,313,734 | 164,217 | 4,265 |
| 26 | Or | Emeraude | III | ×1.125 | 1,477,951 | 184,744 | 4,798 |
| 27 | Or | Améthyste | I | ×1.125 | 1,662,695 | 207,837 | 5,398 |
| 28 | Or | Améthyste | II | ×1.125 | 1,870,532 | 233,816 | 6,073 |
| 29 | Or | Améthyste | III | ×1.125 | 2,104,348 | 263,044 | 6,832 |
| 30 | Or | Diamant | I | ×1.125 | 2,367,392 | 295,924 | 7,686 |
| 31 | Or | Diamant | II | ×1.125 | 2,663,316 | 332,914 | 8,647 |
| 32 | Or | Diamant | III | ×1.125 | 2,996,230 | 374,529 | 9,728 |
| 33 | Or | Rubis | I | ×1.125 | 3,370,759 | 421,345 | 10,944 |
| 34 | Or | Rubis | II | ×1.125 | 3,792,104 | 474,013 | 12,312 |
| 35 | Or | Rubis | III | ×1.125 | 4,266,117 | — | 13,851 |

### Implementation

```java
public static final long FIRST_RANK_XP = 10_000;
public static final double RANK_MULTIPLIER = 1.25;
public static final double[] PRESTIGE_NERFS = { 1.0, 0.8, 0.5 };

public static long getRankThreshold(int rankIndex) {
    if (rankIndex < 0) return 0;
    if (rankIndex < 12) {
        // Bronze: pure ×1.25 from base
        return (long) (FIRST_RANK_XP * Math.pow(RANK_MULTIPLIER, rankIndex));
    }
    int prestige = rankIndex / 12;
    int offset = rankIndex % 12;
    long base = getRankThreshold(prestige * 12 - 1);
    double effMultiplier = 1 + (RANK_MULTIPLIER - 1) * PRESTIGE_NERFS[prestige];
    return (long) (base * Math.pow(effMultiplier, offset + 1));
}

public static double getEffectiveMultiplier(int rankIndex) {
    if (rankIndex < 12) return RANK_MULTIPLIER;
    int prestige = rankIndex / 12;
    return 1 + (RANK_MULTIPLIER - 1) * PRESTIGE_NERFS[prestige];
}

public static int getRankIndex(long totalXP) {
    if (totalXP < FIRST_RANK_XP) return -1;
    int rank = 0;
    while (rank < 35 && getRankThreshold(rank + 1) <= totalXP) rank++;
    return rank;
}
```

`Math.pow(1.25, n)` for n up to 35 stays within `double`'s exact integer precision range.

---

## XP Earning

### Base XP Values (per event)

| Event | Base XP | Notes |
|-------|---------|-------|
| Kill | +15 | Awarded to killer |
| Assist | +8 | Awarded per assister (≥25% of killer's damage to victim) |
| Bed destroy | +30 | Awarded to the destroyer player |
| Win | +200 | Awarded to every player on the winning team |
| Loss | 0 | No XP for losing |
| Death | 0 | No XP penalty on death |

### Game Mode Coefficients

Each game awards `baseXP × coefficient`. Coefficient depends on the number of teams and players per team:

| Teams | 1v1 | 2v2 | 3v3 | 4v4 |
|-------|-----|-----|-----|-----|
| 2 | 1.00 | 1.15 | 1.25 | 1.35 |
| 3 | 1.05 | 1.20 | 1.30 | 1.40 |
| 4 | 1.10 | 1.25 | 1.35 | 1.45 |

Legacy mode (non-GameRoom single-world games) uses coefficient **1.0**.

### Implementation in `Game.java`

```java
// Field set at construction
private double coefficient = 1.0;

// In onPlayerDeath (killer + assists):
if (killer != null) {
    levelManager.addXP(killer.getUniqueId(), (int) Math.round(15 * coefficient));
    for (Player assist : assists) {
        levelManager.addXP(assist.getUniqueId(), (int) Math.round(8 * coefficient));
    }
}

// In onBedDestroyed (destroyer):
if (destroyer != null) {
    levelManager.addXP(destroyer.getUniqueId(), (int) Math.round(30 * coefficient));
}

// In endGame (winners only):
for (Player player : playerStats.keySet()) {
    boolean won = winner != null && winner.equals(getPlayerTeam(player));
    if (won) {
        levelManager.addXP(player.getUniqueId(), (int) Math.round(200 * coefficient));
    }
}
```

Coefficient is injected from `GameRoomConfig` for GameRoom mode, or defaults to 1.0 for legacy games.

---

## Files to Modify

### 1. `PlayerLevel.java` — Complete Rewrite

**Entity fields:**
| Old field | New field | Type | Default | SQL |
|-----------|-----------|------|---------|-----|
| `level` | `rankIndex` | `int` | `-1` | `smallint` |
| `currentXP` | *(removed)* | — | — | — |
| `totalXP` | *(unchanged name)* | `long` | `0` | `bigint` |

**Key methods to add/change:**
- `getFormattedRank()` — returns MiniMessage tag from spritesheet for display (falls back to text `"§7Non classé"` for rank -1)
- `getRankIndex(long totalXP)` — static
- `getRankThreshold(int rankIndex)` — static
- `getPrestigeName(int rankIndex)` — static, returns "Bronze"/"Argent"/"Or"/""
- `getGemName(int rankIndex)` — static, returns "Emeraude"/"Améthyste"/"Diamant"/"Rubis"/""
- `getLevelInRank(int rankIndex)` — static, returns 1/2/3
- `getProgressInRank()` — `totalXP - getRankThreshold(rankIndex)`
- `getXPToNextRank()` — `getRankThreshold(rankIndex + 1) - totalXP`
- `getXPForNextRank()` — since we removed currentXP, this is derived from totalXP

**CraftEngine spritesheet loading:**
```java
private static String[] rankMiniMessageTags = new String[36];
private static boolean ranksLoaded = false;

public static void loadRankImages() {
    Image image = CraftEngineImages.byId(Key.of("tland:level_ranks"));
    if (!(image instanceof BitmapImage bitmap)) return;
    int i = 0;
    for (int row = 0; row < bitmap.rows(); row++)
        for (int col = 0; col < bitmap.columns(); col++)
            rankMiniMessageTags[i++] = bitmap.miniMessageAt(row, col);
    ranksLoaded = true;
}
```

Call `loadRankImages()` on plugin enable (lazy, safe to retry if not yet available).

**Rank-up detection in `addXP`:**
```java
public void addXP(long xp) {
    this.totalXP += xp;
    int newRank = getRankIndex(this.totalXP);
    if (newRank > this.rankIndex) {
        this.rankIndex = newRank;
        // flag for level-up sound/animation
    } else if (newRank < this.rankIndex) {
        this.rankIndex = newRank;
    }
}
```

**Prestige crossover celebration:**
When a player crosses a prestige boundary (rank 11→12 Bronze→Silver, or rank 23→24 Silver→Gold), fire a distinct celebration event:
- Custom title/subtitle (e.g. "§6✧ Prestige Argent ✧")
- Unique sound different from normal rank-up
- Broadcast a server message: `"{player} §7a atteint le prestige §e{name}§7!"`

Detect in `PlayerLevelManager.addXP()`: if `getRankIndexAfter() / 12 > getRankIndexBefore() / 12`, trigger prestige celebration instead of normal rank-up sound.

### 2. `PlayerLevelManager.java` — Update

- Remove static fields: `maxLevel`, `xpWins`, `xpLosses`, `xpKills`, `xpAssists`, `xpDeaths`
- Remove `loadConfig()` references to `maxLevel` and `xpMultipliers` section
- Add config loading for `firstRankXP`, `rankMultiplier`, and `prestigeNerfs`
- `addXP()` — simplified: load entity, call `playerLevel.addXP(xp)`, save. On level-up: play sound + update leaderboards.
- `resetXP()` — replaces `removeXP()`. Sets `totalXP = 0`, `rankIndex = -1`.
- `getTop10ByLevel()` → `getTop10ByXP()` — sort by `totalXP` desc, limit 10

### 3. `Game.java` — XP Awards

- Add `double coefficient` field, initialised to `1.0`
- Add setter `setCoefficient(double)` (called from `GameRoom` constructor)
- In `onPlayerDeath`: remove death penalty block; award 15×coef to killer, 8×coef per assist
- In `onBedDestroyed`: award 30×coef to destroyer
- In `endGame`: award 200×coef to winners only

### 4. `GameRoomConfig.java` — Coefficient Lookup

Add method:
```java
public double getCoefficient() {
    int t = maxTeams;
    int p = teamSize.getPlayersPerTeam();
    if (t == 2) return switch (p) { case 1 -> 1.0; case 2 -> 1.15; case 3 -> 1.25; case 4 -> 1.35; default -> 1.0; };
    if (t == 3) return switch (p) { case 1 -> 1.05; case 2 -> 1.20; case 3 -> 1.30; case 4 -> 1.40; default -> 1.0; };
    if (t == 4) return switch (p) { case 1 -> 1.10; case 2 -> 1.25; case 3 -> 1.35; case 4 -> 1.45; default -> 1.0; };
    return 1.0;
}
```

### 5. `GameRoom.java` — Inject Coefficient

In the `GameRoom` constructor, after `this.game` is created:
```java
this.game.setCoefficient(config.getCoefficient());
```

### 6. `ScoreboardManager.java` — Rank Display

The spritesheet rank image **replaces** the `✪ Niveau:` prefix entirely. The image already conveys prestige colour, gem, and level — no text label needed.

**Lobby scoreboard — ranked player:**
```
{rankMiniMessageTag} §8[{progress}/{threshold}§8]
§8[{progressBar}§8]
```

**Lobby scoreboard — unranked player:**
```
§8Non classé
§8[{progressBar_5K/10K}§8]
```

**Unranked progress** uses `totalXP / FIRST_RANK_XP` as the ratio (unranked players have no rank threshold to compare against).

Progress bar uses 15 segments (`§6■` filled, `§8■` empty), same as current.

Remove `playerLevel.getCurrentXP()`, `getXPForNextLevel()`, `getFormattedLevel()` calls — replace with rank-based equivalents.

### 7. `PlayerActivity.java` — Chat Format

Replace:
```java
String formattedLevel = playerLevel.getFormattedLevel();
Component.text("§7[" + formattedLevel + "§7] ...")
```

With:
```java
String rankTag = playerLevel.getFormattedRank(); // MiniMessage tag or "§8Non classé"
// For ranked: use the image tag
// For unranked: "§8Non classé" or empty
```

For unranked players, show `§7[§8Non classé§7]` prefix. For ranked players, use the MiniMessage tag directly in the component (renders the spritesheet image).

### 8. `LeaderboardCommand.java` — Rank Leaderboard

Replace numeric level display with rank name + image tag:
```
{medal} <white>{player}</white> <dark_gray>-</dark_gray> {rankMiniMessageTag} Grade
```

`fetchTop10()` for `LEVEL` type now queries `totalXP` desc instead of `level` desc.

### 9. `LevelDebugCommand.java` — Rank Info

**Changes:**
- `addxp`: shows new rank name, rank index, totalXP
- `resetxp`: replaces `removexp`. Fully resets a player: `totalXP = 0`, `rankIndex = -1`. No partial de-ranking (avoids reverse prestige-nerf complexity).
- `setrank`: new subcommand to set rankIndex directly (adjusts totalXP to threshold)
- `info`: shows rank name, rank index, totalXP, progress to next rank
- `recalculate`: **remove entirely** — no longer meaningful (XP is awarded directly per-action, not derived from stats)

**Remove command literals:** `removexp`, `recalculate`

### 10. `config.yml` — Cleanup

Remove:
```yaml
maxLevel: 150
xpMultipliers:
  wins: 100
  losses: 20
  kills: 15
  assists: 5
  deaths: -10
```

Add:
```yaml
rank:
  first-rank-xp: 10000
  rank-multiplier: 1.25
  prestige-nerfs:
    - 1.0   # Bronze
    - 0.8   # Silver
    - 0.5   # Gold
```

### 11. `Main.java` — Init

On plugin enable, call `PlayerLevel.loadRankImages()`. Should be safe to call even if CraftEngine images aren't ready yet (the method handles the null check gracefully). A retry can be scheduled 1 tick later if `ranksLoaded` remains false.

### 12. Database Migration

`player_levels` table changes:

| Old column | New column | Type | Default |
|-----------|-----------|------|---------|
| `level` int | `rank_index` | `smallint` | `-1` |
| `current_xp` int | *(drop)* | — | — |
| `total_xp` int | `total_xp` | `bigint` | `0` |

The `hibernate.hbm2ddl.auto=update` setting will handle column type changes — but Hibernate won't drop `current_xp` automatically. A manual SQL migration on the production DB is needed:

```sql
ALTER TABLE player_levels ADD COLUMN IF NOT EXISTS rank_index smallint DEFAULT -1;
ALTER TABLE player_levels ALTER COLUMN total_xp TYPE bigint;
ALTER TABLE player_levels DROP COLUMN IF EXISTS current_xp;
```

**Existing player data migration:**
Old `total_xp` values were earned under a different XP system (win=100, loss=20, kill=15, assist=5, death=-10). To give fair credit under the new system, compute a fresh `totalXP` from lifetime stats on first startup:

```sql
UPDATE player_levels pl
SET total_xp = (
    SELECT COALESCE(ps.wins, 0) * 200
         + COALESCE(ps.kills, 0) * 15
         + COALESCE(ps.assists, 0) * 8
         + COALESCE(ps.destroyed_beds, 0) * 30
    FROM player_statistics ps
    WHERE ps.uuid = pl.uuid
);
```

Then `rank_index` is computed automatically by `getRankIndex()` on first load after migration.

---

## Edge Cases & Gotchas

### 1. `destroyedBeds` stat is never tracked (bug in existing code)

`PlayerStatistic` has a `currentDestroyedBeds` field with full merge logic in `addCurrentValues()`, but **no code ever increments it**. `Game.onBedDestroyed()` (line 996) handles the bed destruction logic but never calls `stat.setCurrentDestroyedBeds(...)` on the destroyer's stat.

**Fix:** In `onBedDestroyed()`, after awarding XP, increment the destroyer's stat:
```java
if (destroyer != null) {
    PlayerStatistic stat = playerStats.get(destroyer);
    if (stat != null) {
        stat.setCurrentDestroyedBeds(stat.getCurrentDestroyedBeds() + 1);
    }
}
```

### 2. `recalculateLevelFromStats()` must be removed

The old system could derive `totalXP` from lifetime stats via a linear formula. The new system has **no such formula** — XP is awarded per-action during gameplay with game-mode coefficients applied. This method is unsalvageable.

**Remove:**
- `PlayerLevelManager.recalculateLevelFromStats(UUID)`
- `PlayerLevelManager.calculateTotalXP(PlayerStatistic)`
- `PlayerLevelManager.calculateLevel(int)`
- `LevelDebugCommand.runRecalculate()` and the `recalculate` command literal
- `LevelDebugCommand.runSetStat()` triggering recalculation

### 3. `totalXP` type: `int` → `long` everywhere

| Location | Current | Must be |
|----------|---------|---------|
| `PlayerLevel.totalXP` field | `int` | `long` |
| `PlayerLevel.addXP(int xp)` param | `int` | `long` |
| `PlayerLevelManager.addXP(UUID, int)` param | `int` | `long` |
| `PlayerLevelManager.removeXP(UUID, int)` → `resetXP(UUID)` | `int` | `void` (no amount) |
| DB column `total_xp` | `int` | `bigint` |

**Why:** Gold Ruby III requires ~4.2M XP. With a coefficient of 1.45 (4v4), players earn up to ~400 XP/game. While 4.2M fits in an `int`, the cumulative XP of a highly active player over years could exceed `int` max (2.1B) — especially if more ranks are added later. Being safe with `long` avoids future overflow.

### 4. `current_xp` column becomes orphaned

Hibernate's `hbm2ddl.auto=update` never drops columns. After removing the `currentXP` field from `PlayerLevel.java`, the `current_xp` column remains in the database forever — it's just never written to. The migration script must explicitly drop it:

```sql
ALTER TABLE player_levels DROP COLUMN IF EXISTS current_xp;
```

### 5. Existing `totalXP` from old system doesn't map cleanly

Old XP rates: win=100, loss=20, kill=15, assist=5, death=-10 (net −10 per death)
New XP rates: win=200, loss=0, kill=15, assist=8, death=0, +bedDestroy=30 +coefficient

A player with `totalXP = 50,000` under the old system would have needed ~400 games at 50% win rate. Under new rates, 400 games with same stats would yield ~70,000 XP — a 40% difference.

**Fix:** On migration, recompute `totalXP` from lifetime stats using the new formula (see Database Migration section above). This ensures every player gets fair credit for their actual performance.

### 6. Hologram leaderboard references removed methods

`LeaderboardCommand.LeaderboardHologram.buildContent()` (line 307) calls:
- `PlayerLevel.getTierIcon(lvl)`
- `PlayerLevel.tierColorMiniMessage(lvl)`

Both methods are removed in the new system. The `LEVEL` leaderboard type must:
- Query `PlayerLevelManager.getTop10ByXP()` — sort by `totalXP` DESC instead of `level` DESC
- Display the rank's MiniMessage tag from the spritesheet instead of tier icon + colour

### 7. Scoreboard `getCurrentXP()` / `getXPForNextLevel()` removed

`ScoreboardManager.generateProgressBar()` (line 99) uses:
- `playerLevel.getCurrentXP()` → replaced with `playerLevel.getProgressInRank()` (for ranked) or `playerLevel.getTotalXP()` (for unranked)
- `playerLevel.getXPForNextLevel()` → replaced with `playerLevel.getXPToNextRank()`

The scoreboard line at line 85 builds the XP display from these methods. All must be updated.

### 8. `PlayerLevelManager.addXP()` currentXP sync line removed

Line 102 currently does:
```java
playerLevel.setCurrentXP(playerLevel.getTotalXP() - PlayerLevel.getCumulativeXP(level));
```

With `currentXP` removed, this line is deleted. The `addXP()` method in `PlayerLevelManager` simply calls `playerLevel.addXP(xp)` and saves — no post-sync needed.

### 9. Legacy game mode coefficient default

The legacy `Game(String name)` constructor (line 109) has no `GameRoomConfig`. The `coefficient` field must be declared as `private double coefficient = 1.0;` so legacy mode always gets a default of 1.0.

---

## Implementation Order

1. **`PlayerLevel.java`** — data model, threshold math, spritesheet loading, formatted rank display
2. **`PlayerLevelManager.java`** — XP add/remove, rank-up detection, top-10 query
3. **`GameRoomConfig.java`** + **`GameRoom.java`** — coefficient lookup and injection
4. **`Game.java`** — XP awards with coefficient
5. **`ScoreboardManager.java`** — rank display in scoreboard
6. **`PlayerActivity.java`** — rank display in chat
7. **`LeaderboardCommand.java`** — rank leaderboard
8. **`LevelDebugCommand.java`** — updated debug commands
9. **`config.yml`** — remove old config, add rank config
10. **`Main.java`** — init call for image loading
