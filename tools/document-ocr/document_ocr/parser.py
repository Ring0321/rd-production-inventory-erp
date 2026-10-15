import math
import re
import unicodedata
from decimal import Decimal, InvalidOperation

FIELDS = ("customer", "order", "model", "bagCount", "weightKg")
LABELS = {
    "customer": r"客户名称|客户单位|购货单位|客户",
    "order": r"提货单号|订单编号|订单号|单据编号|单号",
    "model": r"产品型号|规格型号|型号|规格",
    "bagCount": r"袋数|包数",
    "weightKg": r"提货重量|净重|凈重|淨重|重量",
}
LABEL = re.compile("|".join(f"(?P<{key}>{value})" for key, value in LABELS.items()))
MAX_LINES = 600
MAX_TEXT = 500


def confidence(value):
    try:
        value = float(value)
        return value if math.isfinite(value) and 0 <= value <= 1 else None
    except (TypeError, ValueError):
        return None


def normalize_results(results, page):
    lines = []
    for result in results:
        texts = result.get("rec_texts", [])
        scores = result.get("rec_scores", [])
        for index, text in enumerate(texts):
            text = " ".join(str(text).split())
            if not text:
                continue
            if len(text) > MAX_TEXT or len(lines) >= MAX_LINES:
                from .files import OcrError
                raise OcrError("OCR_OUTPUT_LIMIT")
            line = {"id": f"p{page}-l{len(lines) + 1}", "page": page, "text": text}
            score = confidence(scores[index]) if index < len(scores) else None
            if score is not None:
                line["confidence"] = score
            lines.append(line)
    return lines


def parse_value(field, text):
    text = text.strip(" :;：；\t")
    if not text or len(text) > 160:
        return None
    if field in ("order", "model"):
        return text if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/() -]{0,79}", text) else None
    if field == "customer":
        return text if not re.search(r"[<>\x00-\x1f]", text) else None
    if field == "bagCount":
        match = re.fullmatch(r"([0-9]{1,8})\s*(?:袋|包)?", text)
        return match[1] if match else None
    match = re.fullmatch(r"([0-9]{1,9}(?:\.[0-9]{1,4})?)\s*(kg|公斤|千克|吨|t)", text, re.I)
    if not match:
        return None
    try:
        weight = Decimal(match[1]) * (1000 if match[2].lower() in ("吨", "t") else 1)
        return format(weight.normalize(), "f")
    except InvalidOperation:
        return None


def extract_candidates(lines):
    fields = {key: {"state": "UNKNOWN", "candidates": []} for key in FIELDS}
    for index, line in enumerate(lines):
        text = unicodedata.normalize("NFKC", line["text"])
        matches = list(LABEL.finditer(text))
        for position, match in enumerate(matches):
            # Never interpret an embedded label in a customer value as a second field.
            if match.start() > 0 and text[match.start() - 1] not in " ;；|\t":
                continue
            field = match.lastgroup
            end = matches[position + 1].start() if position + 1 < len(matches) else len(text)
            value_text = text[match.end():end].strip(" :;：；\t")
            sources = [line]
            if not value_text and index + 1 < len(lines):
                following = lines[index + 1]
                next_text = unicodedata.normalize("NFKC", following["text"])
                if following["page"] == line["page"] and not LABEL.search(next_text):
                    value_text = next_text
                    sources.append(following)
            value = parse_value(field, value_text)
            if value is None:
                continue
            candidate = {"value": value, "sourceLineIds": [source["id"] for source in sources],
                         "evidence": "\n".join(source["text"] for source in sources)}
            scores = [source.get("confidence") for source in sources]
            if all(score is not None for score in scores):
                candidate["confidence"] = min(scores)
            fields[field]["state"] = "CANDIDATE"
            if len(fields[field]["candidates"]) < 30:
                fields[field]["candidates"].append(candidate)
    return fields
