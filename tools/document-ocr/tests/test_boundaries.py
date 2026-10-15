import io
import unittest

from PIL import Image

from document_ocr.files import OcrError, inspect_magic, decode_pages, MAX_BYTES


def png(width=120, height=80):
    output = io.BytesIO()
    Image.new("RGB", (width, height), "white").save(output, "PNG")
    return output.getvalue()


class FileTest(unittest.TestCase):
    def test_magic_not_extension_or_mime_is_authoritative(self):
        self.assertEqual("PNG", inspect_magic(png()))
        self.assertEqual("PDF", inspect_magic(b"%PDF-1.7\n"))
        for body in [b"", b"<svg></svg>", b"https://x/a.png", b"C:/secret.png", b"MZevil"]:
            with self.subTest(body=body), self.assertRaises(OcrError):
                inspect_magic(body)

    def test_size_truncation_and_pixel_limits(self):
        with self.assertRaises(OcrError) as error:
            inspect_magic(b"a" * (MAX_BYTES + 1))
        self.assertEqual("OCR_FILE_TOO_LARGE", error.exception.code)
        with self.assertRaises(OcrError):
            decode_pages(b"\x89PNG\r\n\x1a\nnot an image")
        with self.assertRaises(OcrError) as error:
            decode_pages(png(5001, 1))
        self.assertEqual("OCR_PIXEL_LIMIT", error.exception.code)
        pages = decode_pages(png())
        self.assertEqual((80, 120, 3), pages[0].shape)

    def test_pdf_page_limit_and_invalid_pdf(self):
        import pypdfium2 as pdfium
        doc = pdfium.PdfDocument.new()
        for _ in range(4):
            doc.new_page(100, 100)
        output = io.BytesIO()
        doc.save(output)
        doc.close()
        with self.assertRaises(OcrError) as error:
            decode_pages(output.getvalue())
        self.assertEqual("OCR_PAGE_LIMIT", error.exception.code)
        with self.assertRaises(OcrError):
            decode_pages(b"%PDF-1.7\ngarbage")

    def test_jpeg_and_three_page_pdf_decode_but_oversized_pdf_never_renders(self):
        import pypdfium2 as pdfium
        image = io.BytesIO()
        Image.new("RGB", (120, 80), "white").save(image, "JPEG")
        self.assertEqual("JPEG", inspect_magic(image.getvalue()))
        self.assertEqual((80, 120, 3), decode_pages(image.getvalue())[0].shape)
        for count, width, expected in [(3, 100, None), (1, 9000, "OCR_PIXEL_LIMIT")]:
            doc = pdfium.PdfDocument.new()
            for _ in range(count):
                doc.new_page(width, 100)
            output = io.BytesIO()
            doc.save(output)
            doc.close()
            if expected:
                with self.assertRaises(OcrError) as error:
                    decode_pages(output.getvalue())
                self.assertEqual(expected, error.exception.code)
            else:
                self.assertEqual(3, len(decode_pages(output.getvalue())))


if __name__ == "__main__":
    unittest.main()
