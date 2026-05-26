import { createBot } from "mineflayer";
import { RoomStateItem, Slots } from "./rush-types";

const bot = createBot({
  host: "localhost",
  port: Number.parseInt("25565", 10),
  version: "26.1.2",
  username: "TigrouAI",
});

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

let step:
  | "init"
  | "opening_rooms"
  | "joining_room"
  | "opening_teams"
  | "joining_team"
  | "done" = "init";

bot.once("spawn", async () => {
  /** CraftEngine requires players to accept server resource pack,
   * otherwise they are stuck are the dialog on server join */
  bot.acceptResourcePack();

  console.log("[spawn] Hub ready, right-clicking compass...");
  await sleep(750);

  /** activates the currently held item, param boolean is for
   * offhand (defaults to false), this triggers windowOpen event */
  bot.activateItem();
});

bot.on("windowOpen", (window) => {
  const cs = window.inventoryStart;
  if (!cs) {
    return;
  }
  console.log(
    `[open] step=${step} type=${window.type} slots=${window.inventoryStart + window.inventoryEnd}`
  );

  // ── Room listing ──
  if (step === "opening_rooms") {
    // Rooms are listed first (before archived replays).
    // Yellow wool = waiting + available  |  Red wool = waiting + full  |  Green wool = running
    // Orange wool = archived replay (skip)
    for (let i = 0; i < cs; i++) {
      const item = window.slots[i];
      if (!item) {
        continue;
      }
      const name = item.name ?? "";
      console.log(`  [${i}] ${name}`);
      // Prefer yellow_wool (waiting with free slots)
      if (name.replace(/^minecraft:/, "") === RoomStateItem.WAITING_AVAILABLE) {
        console.log(`→ clicking room at slot ${i}`);
        step = "joining_room";
        bot.clickWindow(i, 0, 0);
        return;
      }
    }

    console.log("✗ no rooms");

    return;
  }

  // ── Team selection (2-row chest GUI) ──
  if (step === "opening_teams") {
    // Middle row (slots 9-17) has team wool items
    for (let i = Slots.teamSelection.first; i <= Slots.teamSelection.last && i < cs; i++) {
      const item = window.slots[i];
      if (item) {
        console.log(`→ clicking team slot ${i}: ${item.name}`);
        step = "joining_team";
        bot.clickWindow(i, 0, 0);
        return;
      }
    }

    console.log("✗ no team items");
  }
});

// ── Window closed ──
bot.on("windowClose", async () => {
  console.log(`[close] step=${step}`);

  if (step === "joining_room") {
    // Teleported to waiting room → right-click banner (slot 0) for team selection
    console.log("[wait] Room joined, opening team selection...");
    await sleep(3000);
    step = "opening_teams";
    bot.setQuickBarSlot(0);
    await sleep(400);
    bot.activateItem();
    return;
  }

  if (step === "joining_team") {
    step = "done";
    console.log("[done] Joined a team, auto-ready. Waiting for game start.");
    bot.chat("Let's go!");
  }
});

bot.on("error", (err) => console.error("[err]", err.message));
bot.on("end", (reason) => console.log("[end]", reason));
bot.on("messagestr", (msg) => {
  if (msg.includes("[") || msg.includes("§")) {
    return;
  }
  console.log("[chat]", msg);
});
