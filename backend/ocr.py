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


def render_pdf_sync(content: bytes) -> list[np.ndarray]:
    """Synchronous helper to convert EVERY PDF page to a numpy array."""
    pdf = pdfium.PdfDocument(content)
    pages = []
    for i in range(len(pdf)):
        pil_image = pdf[i].render(scale=2).to_pil().convert("RGB")
        pages.append(np.array(pil_image))
    return pages


def ocr_pages_sync(pages: list[np.ndarray]) -> str:
    """Run OCR over every page and join the results, page by page."""
    page_texts = []
    for idx, page in enumerate(pages, start=1):
        text = run_rapidocr_sync(page)
        if text.strip():
            page_texts.append(f"--- PAGE {idx} ---\n{text}")
    return "\n\n".join(page_texts)


async def extract_text_from_url(file_url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(file_url)
        response.raise_for_status()

    if ".pdf" in file_url.lower().split("?")[0]:
        print("PDF detected. Converting all pages to images...")
        pages = await asyncio.to_thread(render_pdf_sync, response.content)
        print(f"   -> {len(pages)} page(s) found. Running RapidOCR on each...")
        text = await asyncio.to_thread(ocr_pages_sync, pages)
    else:
        pil_image = Image.open(BytesIO(response.content)).convert("RGB")
        image_array = np.array(pil_image)
        print("Running RapidOCR...")
        text = await asyncio.to_thread(run_rapidocr_sync, image_array)

    return text.strip()
