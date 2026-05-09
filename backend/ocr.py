import httpx
import pytesseract
from PIL import Image
from io import BytesIO
import pypdfium2 as pdfium  # New import

pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


async def extract_text_from_url(file_url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(file_url)
        response.raise_for_status()

    if ".pdf" in file_url.lower().split("?")[0]:
        print("PDF detected. Converting to image...")
        pdf = pdfium.PdfDocument(response.content)
        page = pdf[0]
        image = page.render(scale=2).to_pil()
    else:
        image = Image.open(BytesIO(response.content)).convert("RGB")

    print("Running OCR...")
    text = pytesseract.image_to_string(image)
    return text.strip()
