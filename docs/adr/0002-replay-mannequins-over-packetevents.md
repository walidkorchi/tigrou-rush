# Replay player representation: Mannequins over PacketEvents fake entities

PacketEvents 2.12.1 is a required dependency, making raw entity packet injection a tempting path. We chose CraftEngine Mannequins instead because: (1) Mannequins are already used in this codebase for the same conceptual purpose (representing a player body in the world); (2) they are real server-side entities, so all Viewers sharing the same Replay World see them identically with no per-viewer packet management; (3) spawning a convincing fake player entity via PacketEvents requires constructing skin profile packets, entity metadata bytes, and 1.21-specific network structures that are poorly documented and fragile across minor versions.

The tradeoff is that Mannequins exist server-side and consume entity slots. This is acceptable given the 20-replay cap and the fact that Replay Worlds are temporary and isolated.
