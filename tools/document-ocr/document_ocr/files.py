import io
import math
import warnings

MAX_BYTES = 10 * 1024 * 1024
MAX_PAGES = 3
MAX_SIDE = 5000
MAX_PIXELS = 12_000_000
MAX_TOTAL_PIXELS = 24_000_000
PDF_SCALE = 1.5


class OcrError(Exception):
    def __init__(self, code, status=422):
        super().__init__(code)
        self.code = code
        self.status = status


def inspect_magic(data):
    if not data or len(data) > MAX_BYTES:
        raise OcrError("OCR_FILE_TOO_LARGE" if data else "OCR_INVALID_FILE", 413 if data else 415)
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "PNG"
    if data.startswith(b"\xff\xd8\xff"):
        return "JPEG"
    if data.startswith(b"%PDF-"):
        return "PDF"
    raise OcrError("OCR_INVALID_FILE", 415)


def check_dimensions(width, height, total=0):
    if (not math.isfinite(width) or not math.isfinite(height) or width < 1 or height < 1
            or width > MAX_SIDE or height > MAX_SIDE or width * height > MAX_PIXELS
            or total + width * height > MAX_TOTAL_PIXELS):
        raise OcrError("OCR_PIXEL_LIMIT")


def decode_pages(data):
    """Only called inside the disposable inference process, before any model work."""
    from PIL import Image, ImageOps
    import numpy as np

    kind = inspect_magic(data)
    try:
        if kind != "PDF":
            Image.MAX_IMAGE_PIXELS = MAX_PIXELS
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                with Image.open(io.BytesIO(data)) as image:
                    if image.format != kind or getattr(image, "n_frames", 1) != 1:
                        raise OcrError("OCR_INVALID_FILE", 415)
                    check_dimensions(*image.size)
                    image.verify()
                with Image.open(io.BytesIO(data)) as image:
                    return [np.asarray(ImageOps.exif_transpose(image).convert("RGB"))[:, :, ::-1].copy()]
        import pypdfium2 as pdfium
        document = pdfium.PdfDocument(data)
        try:
            if not 1 <= len(document) <= MAX_PAGES:
                raise OcrError("OCR_PAGE_LIMIT")
            total = 0
            # Preflight every page before rendering the first one.
            for page in document:
                try:
                    width, height = [math.ceil(x * PDF_SCALE) for x in page.get_size()]
                    check_dimensions(width, height, total)
                    total += width * height
                finally:
                    page.close()
            pages = []
            for page in document:
                try:
                    bitmap = page.render(scale=PDF_SCALE)
                    try:
                        pages.append(np.asarray(bitmap.to_pil().convert("RGB"))[:, :, ::-1].copy())
                    finally:
                        bitmap.close()
                finally:
                    page.close()
            return pages
        finally:
            document.close()
    except OcrError:
        raise
    except (Image.DecompressionBombError, Image.DecompressionBombWarning):
        raise OcrError("OCR_PIXEL_LIMIT") from None
    except Exception:
        raise OcrError("OCR_INVALID_FILE", 415) from None
