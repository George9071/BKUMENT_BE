from asyncio import sleep
import logging
import json
import fitz
import base64
from openai import AsyncOpenAI
from app.config import get_settings
from app.schemas import AnalysisResult

logger = logging.getLogger(__name__)

settings = get_settings()

class OpenRouterService:
    def __init__(self):
        self.client = AsyncOpenAI(
            base_url="https://openrouter.ai/api/v1",
            api_key=settings.OPENROUTER_API_KEY,
        )
        self.model_name = settings.OPENROUTER_MODEL 

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
        Performs deep keyword extraction and summarisation on pre-extracted document text
 
        Prompt contract:
          "keywords": list[str]  — 25-35 domain-specific technical terms
          "summary":  str        — adaptive-length summary (Vietnamese)
 
        Args:
            text: Full plain-text content of the document.
 
        Returns:
            AnalysisResult with keywords and summary.
 
        Raises:
            Exception: Propagates OpenRouter / JSON parse errors to the caller.
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
        
        # sleep(70)
        # return AnalysisResult(
        #     keywords=["Tư duy phản biện", "Kỹ năng lập luận", "Tư duy logic", "Bác bỏ ngụy biện","Chuẩn mực trí tuệ"],
        #     summary="Tài liệu cung cấp kiến thức hệ thống về tư duy phản biện, " \
        #     "từ các khái niệm cơ bản, đặc điểm đến các phẩm chất cần thiết của một người phản biện sắc sảo. " \
        #     "Nội dung nhấn mạnh tư duy logic là nền tảng cốt lõi, hướng dẫn chi tiết cách xây dựng lập luận, " \
        #     "chứng minh và nhận diện các dạng ngụy biện thường gặp. " \
        #     "Bên cạnh đó, cuốn sách còn đề xuất các phương pháp rèn luyện kỹ năng giải quyết vấn đề và " \
        #     "đối thoại dựa trên những chuẩn mực trí tuệ phổ quát."
        # )
        
        try:
            response = await self.client.chat.completions.create(
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                response_format={"type": "json_object"},
            )
            
            raw_response = response.choices[0].message.content
            cleaned_text = self._clean_json_text(raw_response)
            data = json.loads(cleaned_text)
            
            if isinstance(data, list):
                return AnalysisResult(keywords=data, summary="Tóm tắt không khả dụng")
                
            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "")
            )
            
        except Exception as e:
            logger.error("OpenRouter extract_keywords failed: %s", str(e))
            raise Exception(f"OpenRouter Error: {str(e)}")

    async def analyze_document_fast(self, file_content: bytes, file_name: str, mime_type: str) -> AnalysisResult:
        """
        Performs a quick (3-5 keyword) analysis of a document via OpenRouter.

        Text extraction is handled by:
          - PyMuPDF (fitz) for PDFs
          - Base64 data URI for images (vision-capable models only)
          - UTF-8 decode for plain text / other types
 
        Text is truncated to 60,000 characters before sending to avoid exceeding context
        window limits of most OpenRouter-hosted models.
 
        IMPROVEMENT — PDF text extraction strategy:
          PyMuPDF's page.get_text() concatenates all pages in order. 
          For very large PDFs this may exceed the 60k char limit and discard the latter half. 
          A better strategy is to extract text from the first N pages plus the last M pages
          (introduction +  conclusion) to give the model a more representative sample.
        
        Args:
            file_content: Raw bytes of the uploaded file.
            file_name:    Original file name (used for logging).
            mime_type:    MIME type string (e.g. "application/pdf", "image/png").
 
        Returns:
            AnalysisResult with keywords and short summary.
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
        
        
        # sleep(70)
        # return AnalysisResult(
        #     keywords=["Tư duy phản biện", "Kỹ năng lập luận", "Tư duy logic", "Bác bỏ ngụy biện","Chuẩn mực trí tuệ"],
        #     summary="Tài liệu cung cấp kiến thức hệ thống về tư duy phản biện, " \
        #     "từ các khái niệm cơ bản, đặc điểm đến các phẩm chất cần thiết của một người phản biện sắc sảo. " \
        #     "Nội dung nhấn mạnh tư duy logic là nền tảng cốt lõi, hướng dẫn chi tiết cách xây dựng lập luận, " \
        #     "chứng minh và nhận diện các dạng ngụy biện thường gặp. " \
        #     "Bên cạnh đó, cuốn sách còn đề xuất các phương pháp rèn luyện kỹ năng giải quyết vấn đề và " \
        #     "đối thoại dựa trên những chuẩn mực trí tuệ phổ quát."
        # )

        try:
            messages = []

            if "pdf" in mime_type.lower():
                try:
                    doc = fitz.open(stream=file_content, filetype="pdf")
                    extracted_text = ""
                    for page in doc:
                        extracted_text += page.get_text() + "\n"
                    doc.close()
                    
                    # TODO: consider a smarter truncation strategy (first N + last M pages)
                    #       to preserve both introduction and conclusion context.
                    max_chars = 60000 
                    if len(extracted_text) > max_chars:
                        extracted_text = extracted_text[:max_chars] + "\n\n... [NỘI DUNG PHÍA SAU ĐÃ BỊ LƯỢC BỎ ĐỂ TỐI ƯU TÓM TẮT] ..."

                    messages = [{"role": "user", "content": f"{prompt}\n\n--- NỘI DUNG TÀI LIỆU ---\n{extracted_text}"}]
                    
                except Exception as e:
                    logger.error("PyMuPDF failed to read PDF '%s': %s", file_name, str(e))
                    # print(f"Lỗi khi dùng PyMuPDF đọc file: {e}")
                    return AnalysisResult(keywords=[], summary="Lỗi giải mã PDF tại server")

            elif "image" in mime_type.lower():
                base64_data = base64.b64encode(file_content).decode('utf-8')
                data_uri = f"data:{mime_type};base64,{base64_data}"
                messages = [
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": data_uri}}
                        ]
                    }
                ]
            
            else:
                text_data = file_content.decode("utf-8", errors="ignore")
                if len(text_data) > 60000:
                    text_data = text_data[:60000] + "\n..."
                messages = [{"role": "user", "content": f"{prompt}\n\n--- NỘI DUNG TÀI LIỆU ---\n{text_data}"}]

            # print(f"DEBUG: Đang gửi lên LLM. Độ dài chuỗi text: {len(messages[0]['content'] if 'pdf' in mime_type.lower() or 'text' in mime_type.lower() else 0)} ký tự.")
            content = messages[0]["content"]
            payload_len = len(content) if isinstance(content, str) else len(str(content))
            logger.debug("Sending to LLM for '%s'. Payload size: %d chars.", file_name, payload_len)

            response = await self.client.chat.completions.create(
                model=self.model_name,
                messages=messages,
                response_format={"type": "json_object"}
            )
            
            if not hasattr(response, 'choices') or response.choices is None:
                error_info = getattr(response, 'error', 'Lỗi không xác định')
                # print(f"OpenRouter trả về lỗi/rỗng: {error_info}")
                logger.warning("OpenRouter returned empty/error response for '%s': %s", file_name, error_info)
                return AnalysisResult(keywords=[], summary="Mô hình AI từ chối phản hồi hoặc quá tải.")

            raw_response = response.choices[0].message.content
            cleaned_text = self._clean_json_text(raw_response)
            data = json.loads(cleaned_text)
            
            if isinstance(data, list):
                data = data[0] if len(data) > 0 else {}

            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "Không có tóm tắt")
            )
            
        except Exception as e:
            # import traceback
            # print(f"--- Fast Analysis Error ---")
            # traceback.print_exc()
            logger.error("OpenRouter analyze_document_fast failed for '%s':", file_name, exc_info=True)
            return AnalysisResult(keywords=[], summary="Lỗi xử lý nhanh")