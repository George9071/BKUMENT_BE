from fastapi import APIRouter, UploadFile, File, HTTPException, Depends, Request
from typing import Annotated

from app.schemas import (
    AnalysisResult, AnalyzeRequest, DocumentProcessResponse, 
    PDFConversionResponse, 
    VectorRequest, VectorResponse,
    SearchRequest, SearchResponse
)
from app.services.gemini_service import GeminiService
from app.services.mpnet_service import VectorService
from app.services.unstructured_service import convert_pdf_to_text
from app.services.workflow_service import DocumentWorkflowService

router = APIRouter(prefix="/ai")

def get_ai_service():
    return GeminiService()

def get_vector_service(request: Request):
    return request.app.state.vector_service


@router.post(
    "/internal/process-document", 
    response_model=DocumentProcessResponse,
)
async def endpoint_process_document(
    ai_service: Annotated[GeminiService, Depends(get_ai_service)],
    vector_service: Annotated[VectorService, Depends(get_vector_service)],
    file: UploadFile = File(...),
):
    if file.content_type != "application/pdf":
        raise HTTPException(status_code=400, detail="File phải là PDF")

    try:
        workflow = DocumentWorkflowService(ai_service, vector_service)
        result = await workflow.process_document(file)
        
        return result
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    
@router.post("/convert-pdf", response_model=PDFConversionResponse)
async def endpoint_convert_pdf(file: UploadFile = File(...)):
    if file.content_type != "application/pdf":
        raise HTTPException(status_code=400, detail="File phải là PDF")
    try:
        result = await convert_pdf_to_text(file)
        return PDFConversionResponse(
            filename=file.filename,
            full_text=result["full_text"],
            total_elements=result["total_elements"]
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/extract-keywords", response_model=AnalysisResult)
async def endpoint_analyze(
    req: AnalyzeRequest,
    service: Annotated[GeminiService, Depends(get_ai_service)]
):
    try:
        return await service.extract_keywords(req.text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/vectorize", response_model=VectorResponse)
async def endpoint_vectorize(
    req: VectorRequest,
    service: Annotated[VectorService, Depends(get_vector_service)]
):
    try:
        vector = service.get_embedding(req.content)
        return VectorResponse(vector=vector, dimension=len(vector))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/search", response_model=SearchResponse)
async def endpoint_search(
    req: SearchRequest,
    service: Annotated[VectorService, Depends(get_vector_service)]
):
    try:
        results = await service.search_documents(req.query, req.limit)
        return SearchResponse(query=req.query, results=results)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    
@router.get("/", summary="Kiểm tra trạng thái hệ thống")
async def health_check(request: Request):
    is_vector_loaded = hasattr(request.app.state, "vector_service") and request.app.state.vector_service is not None
    
    return {
        "status": "active",
        "api_version": "v1",
        "services": {
            "vector_model": "loaded" if is_vector_loaded else "not_loaded",
        }
    }