import io
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def synthetic_document(kind="PNG"):
    """Non-sensitive, printed fixture. Not a handwriting/dirty-bag benchmark."""
    font_file = Path("C:/Windows/Fonts/msyh.ttc")
    if not font_file.is_file():
        raise RuntimeError("Synthetic Chinese fixture requires local Microsoft YaHei; no font is redistributed")
    image = Image.new("RGB", (1600, 1000), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(font_file), 52)
    text = ["合成提货称重单", "客户：合成测试厂", "订单号：000042", "型号：SiC-90", "袋数：12 袋", "净重：1250 kg", "仅限合成测试"]
    for index, line in enumerate(text):
        draw.text((100, 70 + index * 120), line, font=font, fill="black")
    output = io.BytesIO()
    image.save(output, kind)
    return output.getvalue()


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1] / ".runtime"
    root.mkdir(exist_ok=True)
    (root / "synthetic-pickup.png").write_bytes(synthetic_document())
