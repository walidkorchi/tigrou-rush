import mcData from "minecraft-data";
import { createBot } from "mineflayer";
import type { Item } from "prismarine-item";

const mc = mcData("1.21.4");

const WOOL_NAMES = new Set(
  Object.values(mc.itemsByName)
    .filter((i) => i.name.endsWith("_wool"))
    .map((i) => i.name)
);

const bot = createBot({
  host: "localhost",
  port: Number.parseInt("25565", 10),
  version: "26.1.2",
  username: "TigrouAI",
});

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

const Step = {
  Init: "init",
  OpeningRooms: "opening_rooms",
  JoiningRoom: "joining_room",
  OpeningTeams: "opening_teams",
  JoiningTeam: "joining_team",
  Done: "done",
} as const;
type Step = (typeof Step)[keyof typeof Step];

let step: Step = Step.Init;

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
  if (step === Step.OpeningRooms) {
    // Rooms are listed first (before archived replays).
    // Yellow wool = waiting + available  |  Red wool = waiting + full  |  Green wool = running
    // Orange wool = archived replay (skip)
    for (let i = 0; i < cs; i++) {
      const item: Item | null = window.slots[i] as Item | null;
      if (!item) {
        continue;
      }
      console.log(`  [${i}] ${item.name}`);
      // Yellow wool = GameState.WAITING with free slots
      if (item.name === "yellow_wool") {
        console.log(`→ clicking room at slot ${i}`);
        step = Step.JoiningRoom;
        bot.clickWindow(i, 0, 0);
        return;
      }
    }

    console.log("✗ no rooms");

    return;
  }

  // ── Team selection (2-row chest GUI, CraftEngine GuiLayout '<AAAAAAA>') ──
  if (step === Step.OpeningTeams) {
    // Scan all slots for team wool items (nav arrows at 9 and 17 contain no wool)
    for (let i = 0; i < cs; i++) {
      const item = window.slots[i];
      if (item != null && WOOL_NAMES.has(item.name)) {
        console.log(`→ clicking team slot ${i}: ${item.name}`);
        step = Step.JoiningTeam;
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

  if (step === Step.JoiningRoom) {
    // Teleported to waiting room → right-click banner (slot 0) for team selection
    console.log("[wait] Room joined, opening team selection...");
    await sleep(3000);
    step = Step.OpeningTeams;
    bot.setQuickBarSlot(0);
    await sleep(400);
    bot.activateItem();
    return;
  }

  if (step === Step.JoiningTeam) {
    step = Step.Done;
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
