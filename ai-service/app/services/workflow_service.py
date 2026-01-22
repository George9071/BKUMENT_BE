from fastapi import UploadFile
from typing import Dict, Any

from app.schemas import DocumentProcessResponse
from app.services.gemini_service import GeminiService
from app.services.mpnet_service import VectorService
from app.services.unstructured_service import convert_pdf_to_text

class DocumentWorkflowService:
    def __init__(self, ai_service: GeminiService, vector_service: VectorService):
        self.ai_service = ai_service
        self.vector_service = vector_service

    async def process_document(self, file: UploadFile) -> DocumentProcessResponse:
        pdf_result = await convert_pdf_to_text(file)
        full_text = pdf_result["full_text"]
        ai_result = await self.ai_service.extract_keywords(full_text)

        semantic_content = f"{ai_result.summary} {' '.join(ai_result.keywords)}"
        vector = self.vector_service.get_embedding(semantic_content)

        return DocumentProcessResponse(
            filename=file.filename,
            keywords=ai_result.keywords,
            summary=ai_result.summary,
            vector=vector,
            content=full_text
        )