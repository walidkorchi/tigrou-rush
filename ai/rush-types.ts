// Team.Color enum names lowercased → Minecraft wool/banner item-name fragment.
// Mirrors color.name().toLowerCase() used in TeamSelectionGUI.createTeamIcon().
// Keep in sync with Team.Color in abstracts/Team.java (mirrored by rush.d.ts).
export const TeamColor = {
  WHITE: "white",
  ORANGE: "orange",
  MAGENTA: "magenta",
  LIGHT_BLUE: "light_blue",
  YELLOW: "yellow",
  LIME: "lime",
  PINK: "pink",
  GRAY: "gray",
  LIGHT_GRAY: "light_gray",
  CYAN: "cyan",
  PURPLE: "purple",
  BLUE: "blue",
  BROWN: "brown",
  GREEN: "green",
  RED: "red",
  BLACK: "black",
} as const;
export type TeamColor = (typeof TeamColor)[keyof typeof TeamColor];

// Wool item name shown per room state in GameSelectionGUI.
export const RoomStateItem = {
  WAITING_AVAILABLE: "yellow_wool",
  WAITING_FULL:      "red_wool",
  RUNNING:           "green_wool",
  ARCHIVED:          "orange_wool",
} as const;
export type RoomStateItem = (typeof RoomStateItem)[keyof typeof RoomStateItem];

// Slot ranges from CraftEngine GuiLayout patterns.
export const Slots = {
  // TeamSelectionGUI: '<AAAAAAA>' row in a 3-row chest.
  // slot 9='<' (prev), 10–16=team icons, 17='>' (next).
  teamSelection: { first: 10, last: 16 } as const,
} as const;
