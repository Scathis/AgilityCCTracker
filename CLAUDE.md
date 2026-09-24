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
  loadout gap keys and DD durations/booleans. No coordinates or item ids of other
  players ever leave the client.
- **Server safety (BUILD_PLAN §1.3):** this machine also runs `gimhub-app`. Never
  modify, stop or reuse its containers, networks, volumes, database or ports.
  Use compose project `agilitycctracker`; bind the API to `127.0.0.1` only; ask
  before touching the reverse proxy, firewall, cron or using `sudo`.

## Common commands

```bash
# Plugin build + tests (no local Java needed)
docker run --rm -u $(id -u):$(id -g) -v "$PWD":/p -w /p -e GRADLE_USER_HOME=/p/.gradle \
  gradle:8.10-jdk11 ./gradlew build --no-daemon

# Server tests (throwaway MySQL)
cd server && docker compose -f docker-compose.test.yml run --rm tests

# Deploy / update
cd server && docker compose -p agilitycctracker up -d --build
```
