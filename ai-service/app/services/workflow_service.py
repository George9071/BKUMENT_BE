from fastapi import UploadFile
from typing import Dict, Any
import logging

from app.schemas import DocumentProcessResponse
from app.services.gemini_service import GeminiService
from app.services.open_router_service import OpenRouterService
from app.services.mpnet_service import VectorService
from app.services.unstructured_service import convert_pdf_to_text

logger = logging.getLogger(__name__)
class DocumentWorkflowService:
    def __init__(self, ai_service: OpenRouterService, vector_service: VectorService):
        self.ai_service = ai_service
        self.vector_service = vector_service

    async def process_document(self, file: UploadFile) -> DocumentProcessResponse:
        pdf_result = await convert_pdf_to_text(file)
        full_text = pdf_result["full_text"]

        ai_result = await self.ai_service.extract_keywords(full_text)

        logger.info(
            "[AI] Summary preview: %s",
            shorten(ai_result.summary)
        )

        semantic_content = f"{ai_result.summary or ''} {' '.join(ai_result.keywords or [])}"
        vector = self.vector_service.get_embedding(semantic_content, False)

        logger.info(
            "[AI] Embedding generated: vector_size=%d, semantic_len=%d",
            len(vector),
            len(semantic_content)
        )
        
        if not ai_result.summary:
            logger.warning(
                "[AI] Summary is EMPTY: filename=%s, text_len=%d, keywords=%d",
                file.filename,
                len(full_text),
                len(ai_result.keywords) if ai_result.keywords else 0
            )


        return DocumentProcessResponse(
            filename=file.filename,
            keywords=ai_result.keywords,
            summary=ai_result.summary,
            vector=vector,
            content=full_text
        )

def shorten(text: str | None, max_len: int = 120):
    if not text:
        return None
    return text if len(text) <= max_len else text[:max_len] + "..."
