# Self-hosting

The server is a small FastAPI + MySQL stack that runs entirely in its own
Docker Compose project (`agilitycctracker`), isolated from anything else on
the host — its own network, its own named volume, no shared containers.

## First-time deploy

1. Copy `server/.env.example` to `server/.env` and fill in real values:
   - `APP_SECRET` — `openssl rand -hex 32`
   - `DB_PASSWORD` / `MYSQL_ROOT_PASSWORD` — `openssl rand -hex 24` each
   - `APP_URL` — the public HTTPS URL you'll reach the dashboard/API at
   - `API_PORT` — the port to bind the API container to on the host
   - `PUBLIC_READ` — `true` to allow unauthenticated read-only dashboard
     pages, `false` (default) to require login for everything
   - `HISCORES_ENABLED` / `ITEMS_ENABLED` — enable the background workers
     that refresh hiscores and GE/HA item prices
   - `chmod 600 server/.env` — it holds real secrets, never commit it
     (already covered by `.gitignore`).
2. `cd server && docker compose -p agilitycctracker up -d --build`. This
   builds the API image, starts MySQL, waits for it to report healthy, then
   starts the API — which runs `alembic upgrade head` before `uvicorn`, so
   the schema is always current on boot.
3. Bootstrap an admin dashboard user:
   ```bash
   docker compose -p agilitycctracker exec api python -m app.cli create-admin <username>
   ```
   Prints a generated password once — capture it immediately, it can't be
   retrieved again (only reset — see "Resetting a lost admin password"
   below).
4. Create the FC group:
   ```bash
   docker compose -p agilitycctracker exec api python -m app.cli create-group "<display name>" "<owner RSN>"
   ```
   Prints the plugin's API token once, and seeds the default Running/DD
   loadouts. Give the token to whoever is configuring the RuneLite plugin
   (see `DEV_SETUP.md`) — it's the plugin's only credential, and like the
   admin password, it can't be retrieved again, only rotated:
   ```bash
   docker compose -p agilitycctracker exec api python -m app.cli rotate-token <group_id>
   ```
5. Point a reverse proxy at the API container's bound port. This
   deployment uses Cloudflare Tunnel — the tunnel's ingress config targets
   the host's own LAN-facing address (not `localhost`), since `cloudflared`
   runs as a native process on the host rather than in a container:
   ```
   <public-hostname> → http://<host-lan-ip>:<API_PORT>
   ```
   Whatever proxy you use, make sure the container is actually reachable at
   the address the proxy targets (a loopback-only bind won't be reachable
   from a native host process trying to reach the host's own LAN IP — this
   bit us once during initial setup here).
6. Verify: `curl -s https://<your-host>/healthz` → `{"ok":true}`, and log
   in to the dashboard over HTTPS.

## Updating

```bash
cd server
git pull
docker compose -p agilitycctracker up -d --build
```

The container's own startup runs any new Alembic migrations automatically
— no separate migration step needed.

## Backups

`deploy/backup.sh` does a full `mysqldump` via the running `mysql`
container, gzips it into `server/backups/`, and prunes anything older than
14 days. It reads `DB_NAME`/`MYSQL_ROOT_PASSWORD` straight from
`server/.env`, so it never needs secrets passed on the command line or
written anywhere else.

Run it manually any time:

```bash
./deploy/backup.sh
```

Or schedule it via cron, e.g. nightly at 04:00:

```
0 4 * * * /bin/bash /path/to/agilitycctracker/deploy/backup.sh >> /path/to/agilitycctracker/server/backups/backup.log 2>&1
```

## Restoring from a backup

```bash
cd server
gunzip -c backups/agilitycc-<timestamp>.sql.gz | \
  docker compose -p agilitycctracker exec -T mysql \
  mysql -u root -p"$(grep -m1 '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)" "$(grep -m1 '^DB_NAME=' .env | cut -d= -f2-)"
```

This replays the dump's `CREATE TABLE`/`INSERT` statements into the
already-running database — if you're restoring onto a *fresh* deploy, bring
the stack up once first (so the schema/database exist) before piping the
dump in, rather than trying to restore into an empty Docker volume with no
running MySQL yet.

## Resetting a lost admin password

There's no CLI subcommand for this (`create-admin` refuses if the username
already exists, by design — see `app/cli.py`). Reset it directly using the
app's own password hasher, so the result is byte-for-byte what a real
login would produce:

```bash
cd server
docker compose -p agilitycctracker exec api python -c "
import asyncio, secrets
from sqlalchemy import select
from app.db import async_session_factory
from app.models.user import User
from app.web.security import hash_password

async def main():
    password = secrets.token_urlsafe(12)
    async with async_session_factory() as session:
        result = await session.execute(select(User).where(User.username == '<username>'))
        user = result.scalar_one()
        user.password_hash = hash_password(password)
        await session.commit()
    print(f'New password: {password}')

asyncio.run(main())
"
```

## What NOT to touch

- MySQL's data volume is a Docker-managed named volume
  (`agilitycctracker_db_data`) scoped to this compose project — don't
  create ad-hoc bind mounts for it, and don't run `docker compose down -v`
  unless you genuinely intend to delete all data.
- This project deliberately has its own Docker network and never shares a
  container, network, volume, or port with anything else on a host that
  also runs other services.
