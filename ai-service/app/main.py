from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sentence_transformers import SentenceTransformer

from app.config import get_settings
from app.routers.api_v1 import router as api_v1_router
from app.services.mpnet_service import VectorService

settings = get_settings()

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("Đang khởi động AI Service...")
    
    print("Đang tải model (Có thể mất 1-2 phút)...")
    embedding_model = SentenceTransformer('intfloat/multilingual-e5-base')
    print("Đã tải xong Model Embedding!")
    
    app.state.vector_service = VectorService(embedding_model)
    
    yield
    
    print("Đang dừng AI Service...")

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_v1_router)

@app.get("/")
def root():
    return {"message": "AI Service is running", "docs": "/docs"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8087, reload=True)