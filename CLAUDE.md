# CLAUDE.md

This repo is the **Wildy Agility FC Tracker**: a RuneLite plugin (Java 11, at the
repo root), a FastAPI + MySQL server (`server/`) and a web dashboard, self-hosted
on the owner's server.

## Start here

The full specification is **`docs/BUILD_PLAN.md`**. When asked to build, execute it
phase by phase starting from Phase 0 (or from the last completed phase recorded in
`docs/BUILD_LOG.md`).

- Stop at every 🛑 checkpoint and wait for the owner's answer.
- Keep `docs/BUILD_LOG.md` updated (phase, what was done, check results).
- Commit at the end of each phase; never commit `server/.env` or backups.

## Non-negotiables

- **Plugin Hub rules (BUILD_PLAN §1.1–1.2):** Java only, no reflection or
  subprocesses, inject `OkHttpClient`/`Gson`. Only `scan/FcMemberScanner` may
  read other players, and nothing about other players may be uploaded except
  gear-gap slot names (e.g. `BOOTS`, `WEAPON`) and DD durations/booleans
  (`ui/GearFlagOverlay` only draws the players the scanner hands it). The
  player's own inventory is never uploaded (the kit check is local-only). No coordinates or item ids of other
  players ever leave the client.
- **Server safety (BUILD_PLAN §1.3):** this machine also runs `gimhub-app`. Never
  modify, stop or reuse its containers, networks, volumes, database or ports.
  Use compose project `agilitycctracker`. The API is published on
  `192.168.1.130:8090` because the Cloudflare tunnel targets that address
  (changing it means changing the tunnel too); uvicorn trusts forwarded headers
  only from `192.168.1.130`. Ask before touching the reverse proxy, firewall,
  cron or using `sudo`.
- **The repo is public (since 2026-10-27).** Never commit `server/.env`,
  backups or anything secret. The one deliberate exception is the plugin's
  API token in `api/ServerConfig.java`, which is public by design - the server's
  protection is the per-mass opt-in, not the token.

## Common commands

```bash
# Plugin build + tests (no local Java needed)
docker run --rm -u $(id -u):$(id -g) -v "$PWD":/p -w /p -e GRADLE_USER_HOME=/p/.gradle \
  gradle:8.10-jdk11 ./gradlew build --no-daemon

# Server tests (throwaway MySQL). Always `down -v` afterwards, and don't pipe
# it into `tail -0`/`head` - that kills the down mid-way, the test DB survives,
# and the next run fails on duplicate fixed usernames/RSNs.
cd server && docker compose -f docker-compose.test.yml build tests \
  && docker compose -f docker-compose.test.yml run --rm tests; \
  docker compose -f docker-compose.test.yml down -v >/dev/null 2>&1

# Deploy / update
cd server && docker compose -p agilitycctracker up -d --build
```
