# BlockChangeAction stores oldMaterial for reversible backwards seeking

The naive recording model captures only the new block state (what a block became after a place or break). We record both `newMaterial` and `oldMaterial` on every BlockChangeAction. This doubles the per-block storage cost but enables seeking backwards without re-pasting island schematics: rewinding applies `oldMaterial` to all blocks changed after the target timestamp, in reverse order, against the live Replay World.

The alternative — re-pasting schematics on every backwards seek — was rejected because schematic paste is an async operation taking 1–2 seconds, which would make the −5 s seek button feel broken. Periodic full world snapshots were also considered and rejected for dramatically increasing file size and recording complexity.
