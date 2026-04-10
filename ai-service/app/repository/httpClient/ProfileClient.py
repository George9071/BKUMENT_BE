import httpx
from typing import List, Optional, Generic, TypeVar
from pydantic import BaseModel

T = TypeVar('T')

class APIResponse(BaseModel, Generic[T]):
    code: int
    message: Optional[str] = None
    result: Optional[T] = None

class ProfileResponse(BaseModel):
    id: str
    fullName: str
    avatarUrl: Optional[str] = None

class ProfileClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.client = httpx.AsyncClient(base_url=self.base_url)

    async def find_user_profile_by_id(self, id: str) -> ProfileResponse:
        response = await self.client.get(f"/internal/users/{id}")
        response.raise_for_status()
        
        return ProfileResponse(**response.json())

    async def close(self):
        await self.client.aclose()