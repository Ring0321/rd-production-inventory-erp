import tempfile
import multiprocessing
from pathlib import Path
import unittest
from unittest.mock import patch

from document_ocr.files import OcrError
from document_ocr.runner import LocalRunner, verify_models


class RunnerTest(unittest.TestCase):
    def test_missing_models_explicitly_unavailable_without_paddle_import(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = LocalRunner(Path(directory))
            self.assertFalse(runner.status()["available"])
            with self.assertRaises(OcrError) as error:
                runner.recognize(b"%PDF-1.7\n")
            self.assertEqual("OCR_UNAVAILABLE", error.exception.code)
            self.assertFalse(verify_models(Path(directory)))

    def test_single_job_concurrency_is_nonblocking(self):
        runner = LocalRunner()
        runner.ready = True
        runner.slot.acquire()
        try:
            with self.assertRaises(OcrError) as error:
                runner.recognize(b"%PDF-1.7\n")
            self.assertEqual("OCR_BUSY", error.exception.code)
        finally:
            runner.slot.release()

    def test_hard_timeout_kills_and_joins_worker_before_releasing_slot(self):
        class Pipe:
            closed = False
            def poll(self, timeout): return False
            def close(self): self.closed = True
        class Process:
            pid = 42
            killed = joined = closed = False
            def start(self): pass
            def is_alive(self): return True
            def kill(self): self.killed = True
            def join(self, timeout): self.joined = True
            def close(self): self.closed = True
        class Context:
            def __init__(self):
                self.process = Process()
                self.parent, self.child = Pipe(), Pipe()
            def Pipe(self, duplex): return self.parent, self.child
            def Process(self, **kwargs): return self.process
        runner = LocalRunner()
        runner.ready = True
        context = Context()
        with patch("document_ocr.runner.multiprocessing.get_context", return_value=context):
            with self.assertRaises(OcrError) as error:
                runner.recognize(b"%PDF-1.7\n")
        self.assertEqual("OCR_TIMEOUT", error.exception.code)
        self.assertTrue(context.process.killed and context.process.joined and context.process.closed)
        self.assertTrue(context.parent.closed and context.child.closed)
        self.assertTrue(runner.slot.acquire(blocking=False))
        runner.slot.release()

    def test_actual_spawned_process_is_reaped_on_hard_deadline(self):
        before = {process.pid for process in multiprocessing.active_children()}
        runner = LocalRunner(timeout=0.001)
        runner.ready = True
        with self.assertRaises(OcrError) as error:
            runner.recognize(b"%PDF-1.7\n")
        self.assertEqual("OCR_TIMEOUT", error.exception.code)
        self.assertEqual(before, {process.pid for process in multiprocessing.active_children()})


if __name__ == "__main__":
    unittest.main()
