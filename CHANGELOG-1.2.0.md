# 1.2.0

## Medic work mode

- New **Medic** work mode: she heals a wounded owner, other maids or same-faction mobs with the Crusader syringe gun and healing bandages.
- Self-heal works now. When the medic is hurt and nobody else needs help, she injects herself, and the heal actually lands (SC2's syringe projectile ignores its own shooter, so the heal is applied directly).
- Medic reload, aim and fire animations and the fire sound are no longer missing.

## Target handling

- Maids stop staring at enemies they can't see. After 3 seconds without line of sight they walk to the last seen spot, and give up if nobody is there.
- If another enemy is visible they drop the hidden one and engage it instead. With resume enabled they remember the last seen spot and go back after the fight.
- Fixed a loop where a maid strafed behind cover, lost sight, stopped firing, walked back, and did it again. Strafing now keeps line of sight and reverses or holds still when it can't.

## Maids and their gear

- Fixed the maid's work area growing permanently after combat.
- Fixed reloading sometimes never finishing after a kill.
- Fixed the healing item swap duplicating items.

## Voice and animation

- Maids now play their mode voice every so often (from the TLM sound pack) instead of only when spotting or firing.
- The "enemy spotted" line only plays when a target is really acquired. It used to be one of the random ambient lines.
- Reload completion sound is configurable. Default is SC2's bolt-pull sound, because the gun's "cock" sound is really a reload recording.
- Gun animations (hold, aim, fire, reload) sync to the client properly for both gunner and medic.

## Chat bubbles

- Bubbles for spotting, firing, reloading, running out of ammo, healing and self-healing.
- Self-healing has its own lines now, instead of reusing the "let me patch you up" ones.
- Rewrote all of them in WineFox's voice, using TLM's built-in character setting.

## Config

- Everything is now in the in-game config screen (Cloth Config) and the TLM maid config screen, and Save writes the file.
- New options: `target_lost_sight_ticks`, `target_search_ticks`, `target_search_range`, `target_search_interruptible`, `target_search_resume`, `reload_end_sound`.

---

Dev builds 1.1.22 - 1.1.35 rolled up into 1.2.0.
