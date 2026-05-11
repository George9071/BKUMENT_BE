import logging
import httpx
from typing import Dict, Any
from fastapi import UploadFile

from app.config import get_settings

logger = logging.getLogger(__name__)

settings = get_settings()

_TEXT_ELEMENT_TYPES = {"Title", "NarrativeText", "UncategorizedText", "ListItem"}

async def convert_pdf_to_text(file: UploadFile) -> Dict[str, Any]:
    """Service chuyển đổi PDF sang Text dùng Unstructured API."""
    
    # Read the full file content into memory.
    file_content = await file.read()
    
    files = {
        'files': (file.filename, file_content, 'application/pdf')
    }
    
    data = {
        "strategy": "auto",
        "split_pdf_page": "true",
        "split_pdf_allow_failed": "true",
        "split_pdf_concurrency_level": "15"
    }
    
    headers = {
        "unstructured-api-key": settings.UNSTRUCTURED_API_KEY,
        "accept": "application/json"
    }
    
    # TODO: make timeout configurable via settings.UNSTRUCTURED_TIMEOUT_SECONDS.
    async with httpx.AsyncClient(timeout=300.0) as client:
        response = await client.post(
            settings.UNSTRUCTURED_API_URL,
            headers=headers,
            files=files,
            data=data,
        )
        
        if response.status_code != 200:
            logger.error(
                "Unstructured API returned %d for file '%s': %s",
                response.status_code,
                file.filename,
                response.text[:500],  # truncate to avoid flooding logs
            )
            raise Exception(f"Unstructured API Error: {response.text}")

        elements = response.json()

    # Assemble plain text from the filtered elements.
    full_text_parts: list[str] = []

    
    for el in elements:
        el_type = el.get("type")
        el_text = el.get("text", "")
        
        if el_type == "Table":
            metadata = el.get("metadata", {})
            table_content = metadata.get("text_as_html", el_text)
            full_text_parts.append(f"\n\n=== [BẢNG SỐ LIỆU] ===\n{table_content}\n======================\n")
        
        elif el_type in _TEXT_ELEMENT_TYPES:
            full_text_parts.append(f"{el_text}\n")
    
    full_text = "".join(full_text_parts)

    # Reset the stream so the UploadFile can be re-read if a subsequent caller needs it.
    await file.seek(0)

    logger.info(
        "Unstructured extraction complete for '%s': %d elements, %d chars extracted.",
        file.filename,
        len(elements),
        len(full_text),
    )
    
    return {
        "full_text": full_text,
        "total_elements": len(elements),
    }