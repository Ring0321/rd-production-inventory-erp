import unittest

from document_ocr.parser import extract_candidates, normalize_results


class ParserTest(unittest.TestCase):
    def test_identifiers_stay_strings_and_evidence_is_exact(self):
        lines = [
            {"id": "p1-l1", "page": 1, "text": "客户：合成测试厂", "confidence": 0.9},
            {"id": "p1-l2", "page": 1, "text": "订单号：00012345678901234567890"},
            {"id": "p1-l3", "page": 1, "text": "型号：090-SiC"},
            {"id": "p1-l4", "page": 1, "text": "袋数：12袋"},
            {"id": "p1-l5", "page": 1, "text": "净重：1.250吨"},
        ]
        fields = extract_candidates(lines)
        self.assertEqual("00012345678901234567890", fields["order"]["candidates"][0]["value"])
        self.assertEqual("090-SiC", fields["model"]["candidates"][0]["value"])
        self.assertEqual("1250", fields["weightKg"]["candidates"][0]["value"])
        self.assertEqual("12", fields["bagCount"]["candidates"][0]["value"])
        self.assertEqual("客户：合成测试厂", fields["customer"]["candidates"][0]["evidence"])
        self.assertNotIn("confidence", fields["order"]["candidates"][0])

    def test_unknown_ambiguous_and_unlabelled_values_are_not_invented(self):
        fields = extract_candidates([{"id": "a", "page": 1, "text": "2500 12 000001"}])
        self.assertTrue(all(field == {"state": "UNKNOWN", "candidates": []} for field in fields.values()))
        fields = extract_candidates([
            {"id": "a", "page": 1, "text": "净重：1250"},
            {"id": "b", "page": 1, "text": "袋数：-2袋"},
            {"id": "c", "page": 1, "text": "袋数：1.5袋"},
        ])
        self.assertEqual("UNKNOWN", fields["weightKg"]["state"])
        self.assertEqual("UNKNOWN", fields["bagCount"]["state"])

    def test_split_labels_and_competing_candidates_keep_all_sources(self):
        fields = extract_candidates([
            {"id": "a", "page": 1, "text": "订单号："},
            {"id": "b", "page": 1, "text": "000042"},
            {"id": "c", "page": 1, "text": "订单号：000043"},
            {"id": "d", "page": 1, "text": "型号："},
            {"id": "e", "page": 2, "text": "不是上一页的型号"},
        ])
        self.assertEqual(["000042", "000043"], [c["value"] for c in fields["order"]["candidates"]])
        self.assertEqual(["a", "b"], fields["order"]["candidates"][0]["sourceLineIds"])
        self.assertEqual("UNKNOWN", fields["model"]["state"])

    def test_official_v3_result_and_confidence_are_normalized_without_paths(self):
        result = normalize_results([{"rec_texts": ["客户：合成厂", "订单号：00007"],
                                     "rec_scores": [0.96, float("nan")], "input_path": "private"}], 1)
        self.assertEqual("p1-l1", result[0]["id"])
        self.assertAlmostEqual(0.96, result[0]["confidence"])
        self.assertNotIn("confidence", result[1])
        self.assertNotIn("input_path", result[0])

    def test_recognized_traditional_net_weight_labels_keep_original_evidence(self):
        for label in ("凈重", "淨重"):
            raw = f"{label}：1250 kg"
            field = extract_candidates([{"id": "p1-l6", "page": 1, "text": raw, "confidence": 0.926}])["weightKg"]
            self.assertEqual("1250", field["candidates"][0]["value"])
            self.assertEqual(raw, field["candidates"][0]["evidence"])
            self.assertEqual(0.926, field["candidates"][0]["confidence"])


if __name__ == "__main__":
    unittest.main()
