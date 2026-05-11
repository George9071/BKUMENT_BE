from fastapi import UploadFile
import logging

from app.schemas import DocumentProcessResponse
from app.services.gemini_service import GeminiService
from app.services.open_router_service import OpenRouterService
from app.services.mpnet_service import VectorService
from app.services.unstructured_service import convert_pdf_to_text

logger = logging.getLogger(__name__)
class DocumentWorkflowService:
    """
    Orchestrates the full document processing pipeline.

    This service is the entry point called by DocumentAsyncService.runBackgroundAiProcess()
    after a document has been uploaded and an initial fast-AI pass has already run. 
    It coordinates three sequential stages:

      Stage 1 — Text extraction (Unstructured API):
        Converts the raw PDF bytes into structured plain text, preserving tables and headings.
 
      Stage 2 — AI analysis (GeminiService or OpenRouterService):
        Extracts 25-35 domain-specific keywords and generates a comprehensive summary from the full extracted text.
        
      Stage 3 — Embedding generation (VectorService / E5 model):
        Encodes the summary + keywords into a 768-dim vector for semantic similarity search.
 
    The result (keywords, summary, vector, full content) is returned to the caller and also stored in the database for later retrieval.
    """
    def __init__(self, ai_service: GeminiService, vector_service: VectorService):
        self.ai_service = ai_service
        self.vector_service = vector_service

    async def process_document(self, file: UploadFile) -> DocumentProcessResponse:
        """
        Args:
            file: The uploaded PDF file as a FastAPI UploadFile object.
 
        Returns:
            DocumentProcessResponse containing:
              - filename:  original file name
              - keywords:  list of 25-35 domain-specific terms
              - summary:   adaptive-length Vietnamese summary
              - vector:    768-dim embedding as List[float] (empty if AI or embedding failed)
              - content:   full extracted plain text (stored for downstream use)        
        """
        # --- Stage 1: Text extraction ---
        pdf_result = await convert_pdf_to_text(file)
        full_text = pdf_result["full_text"]

        if not full_text.strip():
            logger.warning("[AI] Empty text extracted from '%s'; skipping AI analysis.", file.filename)
            return DocumentProcessResponse(filename=file.filename, keywords=[], summary="", vector=[0.0] * 768, content="")

        # --- Stage 2: AI keyword + summary extraction ---
        ai_result = await self.ai_service.extract_keywords(full_text)

        if not ai_result.summary and not (ai_result.keywords and len(ai_result.keywords) > 0):
            logger.warning(
                "[AI] Both summary and keywords are EMPTY for '%s': text_len=%d chars, keyword_count=%d",
                file.filename,
                len(full_text),
                len(ai_result.keywords) if ai_result.keywords else 0,
            )
            # Falling back to zero vector.
            vector = [0.0] * 768
        else:
            logger.info(
                "[AI] Summary preview for '%s': %s",
                file.filename,
                _shorten(ai_result.summary),
            )
            # --- Stage 3: Embedding generation ---
            semantic_content = f"{ai_result.summary or ''} {' '.join(ai_result.keywords or '')}"
            vector = self.vector_service.get_embedding(semantic_content, is_query=False)

        # Guard where get_embedding() returns empty due to internal error
        if not vector:
            vector = [0.0] * 768

        logger.info(
            "[AI] Embedding generated for '%s': vector_size=%d, semantic_content_len=%d chars",
            file.filename,
            len(vector),
            len(semantic_content),
        )

        return DocumentProcessResponse(
            filename=file.filename,
            keywords=ai_result.keywords,
            summary=ai_result.summary,
            vector=vector,
            content=full_text,
        )

def _shorten(text: str | None, max_len: int = 120) -> str | None:
    """
    Truncates a string to max_len characters for safe log output.
 
    Returns None if the input is None or empty, preventing "None" from appearing in log lines. 
    The ellipsis suffix ("...") signals that the string was truncated.
 
    Args:
        text:    The string to shorten (may be None).
        max_len: Maximum number of characters to include (default: 120).
 
    Returns:
        The original string if len <= max_len, a truncated string with "..." suffix,
        or None if the input was None or empty.
    """    
    if not text:
        return None
    return text if len(text) <= max_len else text[:max_len] + "..."
