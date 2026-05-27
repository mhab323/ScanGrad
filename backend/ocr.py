import httpx
import asyncio
from io import BytesIO
import pypdfium2 as pdfium
from PIL import Image
import numpy as np
from rapidocr_onnxruntime import RapidOCR

ocr_engine = RapidOCR()


def run_rapidocr_sync(image_array: np.ndarray) -> str:
    """Synchronous helper function to run RapidOCR"""
    result, elapse = ocr_engine(image_array)

    if result:
        extracted_text = [line[1] for line in result]
        return "\n".join(extracted_text)

    return ""


def render_pdf_sync(content: bytes) -> np.ndarray:
    """Synchronous helper to convert PDF to numpy array"""
    pdf = pdfium.PdfDocument(content)
    page = pdf[0]
    pil_image = page.render(scale=2).to_pil().convert("RGB")
    return np.array(pil_image)


async def extract_text_from_url(file_url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(file_url)
        response.raise_for_status()

    if ".pdf" in file_url.lower().split("?")[0]:
        print("PDF detected. Converting to image...")
        image_array = await asyncio.to_thread(render_pdf_sync, response.content)
    else:
        pil_image = Image.open(BytesIO(response.content)).convert("RGB")
        image_array = np.array(pil_image)

    print("Running RapidOCR...")
    text = await asyncio.to_thread(run_rapidocr_sync, image_array)

    return text.strip()
