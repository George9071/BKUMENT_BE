from fastapi import APIRouter, UploadFile, File, HTTPException, Depends, Request, Query
from typing import Annotated
import time
import logging

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
logger = logging.getLogger("uvicorn.error")

def get_ai_service():
    return GeminiService()

def get_vector_service(request: Request):
    return request.app.state.vector_service

@router.post(
    "/internal/analyze-fast", 
    response_model=AnalysisResult,
)
async def quick_analyze(
    file: UploadFile = File(...),
    ai_service: Annotated[GeminiService, Depends(get_ai_service)] = None
):
    if file.content_type != "application/pdf":
        raise HTTPException(status_code=400, detail="File phải là PDF")

    start_time = time.perf_counter()
    logger.info(f"--- Bắt đầu phân tích nhanh: {file.filename} ---")

    try:
        content = await file.read()
        
        result = await ai_service.analyze_document_fast(
            file_content=content,
            file_name=file.filename,
            mime_type=file.content_type or "application/pdf"
        )
        
        duration = time.perf_counter() - start_time
        logger.info(f"--- Hoàn thành phân tích nhanh trong: {duration:.2f} giây ---")
        
        return result

    except Exception as e:
        duration = time.perf_counter() - start_time
        logger.error(f"--- Lỗi phân tích nhanh sau {duration:.2f} giây: {str(e)} ---")
        raise HTTPException(status_code=500, detail=f"Lỗi khi xử lý nhanh: {str(e)}")

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

    start_time = time.perf_counter()
    logger.info(f"--- Bắt đầu xử lý file: {file.filename} ---")

    try:
        workflow = DocumentWorkflowService(ai_service, vector_service)
        result = await workflow.process_document(file)
        
        end_time = time.perf_counter()
        duration = end_time - start_time
        
        logger.info(f"--- Hoàn thành xử lý trong: {duration:.2f} giây ---")
        
        return result
        
    except Exception as e:
        end_time = time.perf_counter()
        logger.error(f"--- Lỗi sau {end_time - start_time:.2f} giây: {str(e)} ---")
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
        vector = service.get_embedding(req.content, False)
        return VectorResponse(vector=vector, dimension=len(vector))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/search", response_model=SearchResponse)
async def endpoint_search(
    service: Annotated[VectorService, Depends(get_vector_service)],

    query: str = Query(..., description="Từ khóa tìm kiếm"), 
    page: int = Query(1, ge=1, description="Số trang, bắt đầu từ 1"), 
    limit: int = Query(5, ge=1, le=50, description="Số lượng kết quả mỗi trang"),
):
    try:
        results = await service.search_documents(
            query_text=query, 
            page=page, 
            limit=limit
        )
        return SearchResponse(query=query, results=results)
        
    except Exception as e:
        print(f"Endpoint Error: {e}")
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