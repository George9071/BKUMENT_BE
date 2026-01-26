import httpx
from typing import Dict, Any
from fastapi import UploadFile
from app.config import get_settings

settings = get_settings()

async def convert_pdf_to_text(file: UploadFile) -> Dict[str, Any]:
    """Service chuyển đổi PDF sang Text dùng Unstructured API."""
    
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
    
    async with httpx.AsyncClient(timeout=300.0) as client:
        response = await client.post(
            settings.UNSTRUCTURED_API_URL,
            headers=headers,
            files=files,
            data=data
        )
        
        if response.status_code != 200:
            raise Exception(f"Unstructured API Error: {response.text}")
            
        elements = response.json()

    full_text = ""
    for el in elements:
        el_type = el.get("type")
        el_text = el.get("text", "")
        
        if el_type == "Table":
            metadata = el.get("metadata", {})
            full_text += f"\n\n=== [BẢNG SỐ LIỆU] ===\n{el_text}\n======================\n"
        
        elif el_type in ["Title", "NarrativeText", "UncategorizedText", "ListItem"]:
            full_text += f"{el_text}\n"
    
    await file.seek(0)
    
    return {
        "full_text": full_text,
        "total_elements": len(elements)
    }