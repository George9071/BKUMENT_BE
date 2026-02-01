from pydantic import BaseModel, Field
from typing import List, Optional, Any

class AnalyzeRequest(BaseModel):
    text: str = Field(..., min_length=10)

class AnalysisResult(BaseModel):
    keywords: List[str]
    summary: str

class PDFConversionResponse(BaseModel):
    filename: str
    full_text: str
    total_elements: int

class VectorRequest(BaseModel):
    content: str

class VectorResponse(BaseModel):
    vector: List[float]
    dimension: int

class SearchRequest(BaseModel):
    query: str
    page: int = Field(1, ge=1)
    limit: int = Field(10, ge=1)

class SearchResultItem(BaseModel):
    id: str                 
    title: Optional[str] = None 
    score: float            
    vector_score: float     
    keyword_score: float    

class SearchResponse(BaseModel):
    query: str
    results: List[SearchResultItem]
    
class DocumentProcessResponse(BaseModel):
    filename: str
    keywords: List[str]
    summary: str
    content: str
    vector: List[float]