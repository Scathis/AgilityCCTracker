FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY app ./app

RUN pip install --no-cache-dir .

EXPOSE 8000

# TODO (Phase 4): once alembic.ini/alembic/versions exist with a real initial
# migration, add `COPY alembic.ini ./alembic.ini` + `COPY alembic ./alembic`
# above and switch this CMD to an entrypoint script running
# `alembic upgrade head && uvicorn ...`, per BUILD_PLAN.md Phase 6 step 3.
# Left as a plain uvicorn launch for now since there is no migration yet.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
