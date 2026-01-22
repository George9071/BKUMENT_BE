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

        1. KEYWORDS: 15-25 từ khóa chuyên ngành.
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