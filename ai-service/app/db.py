"""
Application-wide asyncpg connection pool.

Pool sizing guidance
────────────────────
    min_size : connections kept alive even when idle. Set to 1-3 for low-traffic services.
                
    max_size :  maximum simultaneous DB connections.
                Must not exceed PostgreSQL's max_connections minus connections used by other services. 
                A safe default is 10-20 per service instance.
    command_timeout : seconds before a slow query is cancelled. Prevents a hanging query from blocking the pool indefinitely.    
"""

import asyncpg
from app.config import get_settings

settings = get_settings()

class DatabaseManager:
    """
    Manages the lifecycle of a single asyncpg connection pool shared by the
    entire application process.
 
    Usage:
        # In lifespan startup
        await db_manager.create()
 
        # In service methods
        async with db_manager.acquire() as conn:
            rows = await conn.fetch("SELECT ...")
 
        # In lifespan shutdown
        await db_manager.close()
    """
    def __init__(self) -> None:
        self._pool: asyncpg.Pool | None = None
    
    async def create(self) -> None:
        if self._pool is not None:
            raise RuntimeError("DatabaseManager.create() called more than once")
        
        self._pool = await asyncpg.create_pool(
            host=settings.DB_HOST,
            port=int(settings.DB_PORT),
            database=settings.DB_NAME,
            user=settings.POSTGRES_USERNAME,
            password=settings.POSTGRES_PASSWORD,
            # Connections kept alive when the pool is idle. Tune via DB_POOL_MIN_SIZE env var (default 2).
            min_size=int(getattr(settings, "DB_POOL_MIN_SIZE", 2)),
            # Maximum simultaneous connections this service may hold. Tune via DB_POOL_MAX_SIZE env var (default 10).
            max_size=int(getattr(settings, "DB_POOL_MAX_SIZE", 10)),
            # Tune via DB_COMMAND_TIMEOUT env var (default 30 s).
            command_timeout=float(getattr(settings, "DB_COMMAND_TIMEOUT", 30)),
        )  

    def acquire(self) -> asyncpg.pool.PoolAcquireContext:
        """
        Returns an async context manager that borrows one connection from the pool.
 
        The connection is automatically returned to the pool when the `async with`
        block exits — even if an exception is raised.

        Raises:
            RuntimeError: if create() has not been called yet.
        """        
        if self._pool is None:
            raise RuntimeError(
                "DatabaseManager.acquire() called before create(). "
                "Ensure db_manager.create() is awaited during application startup."
            )
        return self._pool.acquire()      

    async def close(self) -> None:
        """
        Gracefully closes all connections in the pool and releases resources.
 
        Call once during application shutdown (FastAPI lifespan teardown).
        Safe to call even if create() was never called (no-op in that case).
        """
        if self._pool is not None:
            await self._pool.close()
            self._pool = None                        

db_manager = DatabaseManager()