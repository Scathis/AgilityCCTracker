import pytest


@pytest.mark.parametrize("path", ["/docs", "/redoc", "/openapi.json", "/docs/oauth2-redirect"])
async def test_api_docs_not_exposed(client, path) -> None:
    response = await client.get(path)
    assert response.status_code == 404
