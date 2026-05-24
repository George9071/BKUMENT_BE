import secrets

from fastapi import Header, HTTPException, status

from app.config import get_settings

INTERNAL_API_HEADER = "X-Internal-Api-Key"


async def verify_internal_api_key(
    x_internal_api_key: str | None = Header(default=None, alias=INTERNAL_API_HEADER)
) -> None:
    settings = get_settings()
    expected = settings.INTERNAL_API_SECRET

    if not x_internal_api_key or not secrets.compare_digest(x_internal_api_key, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Internal API key invalid",
        )
