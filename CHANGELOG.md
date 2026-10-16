# 1.5.0 — 2026-10-16

- Persistent offline mail with multiline compose dialogs, paginated all/new inboxes and per-message read state.
- OP level 4 broadcasts and selected-recipient administration mail bypass ignore; operators can attach up to five HTTP/HTTPS URLs, opened with native client link confirmation.
- PL/EN join/live notifications with orange text and a red underlined count linking to `/mail read new`.
- Integrate bilateral ignore, the `/ingore` alias, full/semi vanish privacy, account migration and rollback.
- Bind dialog actions to the connection, expiring session and fresh token; recheck permissions and privacy at send time, and rate-limit ordinary senders.
- Validate URLs without fetching them, preserve drafts on errors and retain old prototype message text/read state while discarding item attachments.
- Unit and vanilla-protocol integration coverage for persistence, failed writes, privacy, permissions, replay protection, attachments and pagination.

# 1.4.2 — 2026-10-13

- Mask real SPECTATOR mode as SURVIVAL in other players' TAB packets during initialization and live updates; preserve the owner's actual mode and controls.
- Hide the real spectator entity from all other viewers and refresh tracking immediately when game mode changes; restore tracking when spectator mode ends.
- Regression coverage for live mode changes, late observers, another spectator, reconnects and dimension transitions.

# 1.4.1 — 2026-10-11

- Combine the complete 1.4.0 feature branch with the PL/EN translation update; restore /ignore, /vanish, /semi-vanish and account migration in the distributed JAR.
- Retain social state, privacy mixins, account-migration safety fixes and real player game modes in TAB.
- Use the shared client-locale selection for social feedback and ignored-player labels; sort ignored TAB entries at the bottom without changing the player game mode.

# 1.4.0 — 2026-10-08

- Administrator account migrations: MOVE, SWAP and OVERRIDE, with before-images, rollback and startup recovery.
- Transfer Minecraft player data, stats, advancements, offline credentials, Utils skins/social state, Teleports ownership/settings and Community Clans membership.
- Persistent bilateral `/ignore` for vanilla chat, private-message aliases, team chat, clan chat and TPA; per-viewer spectator TAB presentation and disconnected ping.
- Persistent `/vanish [fake]` and `/semi-vanish`, with hidden TAB entries, command suggestions and public server-status samples.
- Full vanish removes player tracking, mob targeting, collisions, sounds, vibration events and visible container opening; semi vanish retains world and explicitly addressed private-message interactions.
- Console-only visibility recovery; OP level 4 for self-vanish and migrations. Mojang reservations, OP, bans and whitelists retain their original identities.
- AI-written code: OpenAI Codex, based on Bongo's requirements.
