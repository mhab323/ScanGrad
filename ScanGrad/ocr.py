import httpx
import pytesseract
from PIL import Image
from io import BytesIO

pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


async def extract_text_from_url(image_url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(image_url)
        response.raise_for_status()

    image = Image.open(BytesIO(response.content)).convert("RGB")
    text = pytesseract.image_to_string(image)
    return text.strip()
