import json
import google.generativeai as genai
from app.config import get_settings
from app.schemas import AnalysisResult

settings = get_settings()
genai.configure(api_key=settings.GEMINI_API_KEY)

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
            response = self.model.generate_content(prompt, generation_config=config)
            cleaned_text = self._clean_json_text(response.text)
            data = json.loads(cleaned_text)
            
            if isinstance(data, list):
                return AnalysisResult(keywords=data, summary="Tóm tắt không khả dụng")
                
            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "")
            )
            
        except Exception as e:
            raise Exception(f"Gemini Error: {str(e)}")

    async def analyze_document_fast(self, file_content: bytes, file_name: str, mime_type: str) -> AnalysisResult:
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
            # Gửi file trực tiếp dưới dạng bytes cho Gemini
            response = self.model.generate_content(
                [
                    prompt,
                    {
                        "mime_type": mime_type,
                        "data": file_content
                    }
                ],
                generation_config=genai.GenerationConfig(response_mime_type="application/json")
            )
            
            print(response.text)
            data = json.loads(response.text)
            
            if isinstance(data, list) and len(data) > 0:
                data = data[0]
            elif isinstance(data, list) and len(data) == 0:
                data = {}

            return AnalysisResult(
                keywords=data.get("keywords", []),
                summary=data.get("summary", "Không có tóm tắt")
            )
            
        except Exception as e:
            print(f"Fast Analysis Error: {str(e)}")
            # Fallback nếu Gemini từ chối đọc file trực tiếp (tùy model version)
            return AnalysisResult(keywords=[], summary="Lỗi xử lý nhanh")