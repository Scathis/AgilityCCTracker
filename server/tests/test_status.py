async def test_valid_token_returns_status(client, fc_group):
    group, token = fc_group
    response = await client.get("/api/v1/status", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    body = response.json()
    assert body["fc_owner"] == group.owner_rsn
    assert body["fc_name"] == group.name


async def test_invalid_token_is_rejected(client, fc_group):
    response = await client.get(
        "/api/v1/status", headers={"Authorization": "Bearer not-a-real-token"}
    )
    assert response.status_code == 401


async def test_missing_token_is_rejected(client):
    response = await client.get("/api/v1/status")
    assert response.status_code == 401


async def test_rate_limit_triggers_after_five_failures(client):
    for _ in range(5):
        response = await client.get("/api/v1/status", headers={"Authorization": "Bearer bad"})
        assert response.status_code == 401

    response = await client.get("/api/v1/status", headers={"Authorization": "Bearer bad"})
    assert response.status_code == 429
