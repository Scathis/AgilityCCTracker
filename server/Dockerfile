FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY app ./app
COPY alembic.ini ./alembic.ini
COPY alembic ./alembic

RUN pip install --no-cache-dir .

# Vendored per BUILD_PLAN.md §6.1 ("vendor them ... fetch in Dockerfile") so
# the dashboard never depends on a third-party CDN at runtime.
RUN mkdir -p app/web/static/vendor/fonts \
    && curl -fsSL https://unpkg.com/htmx.org@2/dist/htmx.min.js -o app/web/static/vendor/htmx.min.js \
    && curl -fsSL https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css -o app/web/static/vendor/pico.min.css \
    && curl -fsSL https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.js -o app/web/static/vendor/chart.umd.js \
    && curl -fsSL https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources/net/runelite/client/ui/runescape.ttf -o app/web/static/vendor/fonts/runescape.ttf \
    && curl -fsSL https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources/net/runelite/client/ui/runescape_bold.ttf -o app/web/static/vendor/fonts/runescape_bold.ttf

EXPOSE 8000

CMD ["sh", "-c", "alembic upgrade head && uvicorn app.main:app --host 0.0.0.0 --port 8000 --proxy-headers --forwarded-allow-ips='*'"]
