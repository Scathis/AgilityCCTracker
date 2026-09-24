from fastapi import FastAPI

app = FastAPI(title="Wildy Agility FC Tracker")


@app.get("/healthz")
async def healthz() -> dict:
    return {"ok": True}
