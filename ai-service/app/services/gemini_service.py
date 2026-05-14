import json
import logging
import asyncio
import functools
from concurrent.futures import ThreadPoolExecutor
import google.generativeai as genai
from app.config import get_settings
from app.schemas import AnalysisResult

logger = logging.getLogger(__name__)
settings = get_settings()
genai.configure(api_key=settings.GEMINI_API_KEY)

_gemini_executor = ThreadPoolExecutor(
    max_workers=int(getattr(settings, "GEMINI_MAX_WORKERS", 4)),
    thread_name_prefix="gemini-worker",
)

class GeminiService:
    def __init__(self):
        self.model = genai.GenerativeModel(settings.GEMINI_MODEL)

    def _clean_json_text(self, raw_text: str) -> str:
        text = raw_text.strip()
        if text.startswith("```json"):
            text = text[7:]
        elif text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]
        return text.strip()

    async def extract_keywords(self, text: str) -> AnalysisResult:
        """
        Performs deep keyword extraction and summarisation on pre-extracted document text.
 
        Prompt contract — the model must return a JSON object with exactly two keys:
          "keywords": list[str]  — 25-35 domain-specific technical terms
          "summary":  str        — adaptive-length summary (Vietnamese)
 
        NOTE:
          The Google Generative AI Python SDK's generate_content() is SYNCHRONOUS and
          will block the event loop when called from an async context. For high-throughput
          deployments this starves other coroutines during the (potentially multi-second)
          model inference call.
 
        RECOMMENDED OPTIMIZATION — offload to a thread pool:
    
        Args:
            text: Full plain-text content of the document (output of Unstructured API).
 
        Returns:
            AnalysisResult with keywords and summary populated.
 
        Raises:
            Exception: Propagates Gemini SDK errors or JSON parse failures to the caller.
        """

        prompt = f"""Bạn là một chuyên gia phân tích kỹ thuật.
        Nhiệm vụ: Phân tích văn bản và trả về JSON thuần túy.

        1. KEYWORDS: 25-35 từ khóa chuyên ngành.
        2. SUMMARY: Tóm tắt nội dung (Adaptive length).

        Cấu trúc JSON bắt buộc:
        {{
            "keywords": ["Keyword1", "Keyword2"],
            "summary": "Nội dung tóm tắt..."
        }}

        --- INPUT ---
        {text}
        """
        
        config = genai.GenerationConfig(response_mime_type="application/json")
        
        try:
            loop = asyncio.get_running_loop()
            # response = self.model.generate_content(prompt, generation_config=config)
            response = await loop.run_in_executor(
                _gemini_executor,
                functools.partial(
                    self.model.generate_content,
                    prompt,
                    generation_config=config,
                ),
            )
            
            cleaned_text = self._clean_json_text(response.text)
            data = json.loads(cleaned_text)
            
            if isinstance(data, list):
                return AnalysisResult(keywords=data, summary="Tóm tắt không khả dụng")
                
            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "")
            )
            
        except Exception as e:
            logger.error("Gemini extract_keywords failed: %s", str(e))
            raise Exception(f"Gemini Error: {str(e)}")

    async def analyze_document_fast(self, file_content: bytes, file_name: str, mime_type: str) -> AnalysisResult:
        """
        Performs a quick (3-5 keyword) analysis by sending raw file bytes to Gemini's multimodal input
 
        Prompt contract:
          "keywords": list[str]  — 3-5 most important terms
          "summary":  str        — 3-5 sentence Vietnamese summary
 
        Fallback behaviour:
          - On ANY error (model refusal, JSON parse failure, network timeout), returns an AnalysisResult
            with empty keywords rather than raising. 
          - This ensures the document entity is always persisted even if fast AI analysis fails
 
        Args:
            file_content: Raw bytes of the uploaded file.
            file_name:    Original file name (used for error logging).
            mime_type:    MIME type string (e.g. "application/pdf", "image/png").
 
        Returns:
            AnalysisResult with a small set of keywords and a short summary.
            Returns empty keywords + error message on failure (never raises).
        """        

        prompt = """
        Phân tích tài liệu đính kèm và trả về JSON thuần túy (không dùng markdown).
        Yêu cầu:
        1. KEYWORDS: 3-5 từ khóa quan trọng nhất.
        2. SUMMARY: Tóm tắt ngắn gọn trong khoảng 3-5 câu, dùng tiếng Việt.

        Cấu trúc JSON:
        {
            "keywords": ["..."],
            "summary": "..."
        }
        """

        try:
            loop = asyncio.get_running_loop()
            content_parts = [
                prompt,
                {"mime_type": mime_type, "data": file_content},
            ]
            gen_config = genai.GenerationConfig(response_mime_type="application/json")

            response = await loop.run_in_executor(
                _gemini_executor,
                functools.partial(
                    self.model.generate_content,
                    content_parts,
                    generation_config=gen_config,
                ),
            )
            
            # response = self.model.generate_content(
            #     [
            #         prompt,
            #         {
            #             "mime_type": mime_type,
            #             "data": file_content
            #         }
            #     ],
            #     generation_config=genai.GenerationConfig(response_mime_type="application/json")
            # )
            
            # print(response.text)
            logger.debug("Gemini fast analysis raw response for '%s': %s", file_name, response.text)
            data = json.loads(response.text)
            
            if isinstance(data, list):
                data = data[0] if len(data) > 0 else {}

            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "Không có tóm tắt")
            )
            
        except Exception as e:
            # Fast-path errors are non-fatal — the document is still persisted with empty AI fields.
            # The background async job will attempt the full deep analysis immediately after.
            logger.error("Gemini analyze_document_fast failed for '%s': %s", file_name, str(e))
            # print(f"Fast Analysis Error: {str(e)}")
            # Fallback nếu Gemini từ chối đọc file trực tiếp (tùy model version)
            return AnalysisResult(keywords=[], summary="Lỗi xử lý nhanh")
