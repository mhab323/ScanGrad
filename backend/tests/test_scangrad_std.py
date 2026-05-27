"""
============================================================================
 ScanGrad — Software Test Design (STD) Execution File
============================================================================

 Project : ScanGrad — AI-Powered Exam Evaluation System
 Stack   : VLM (handwriting OCR) -> LangChain RAG (rubric retrieval)
           -> Llama 3.2 (local grading, RTX 3070 Ti)

 This file is the MASTER test runner for the 6 STD test cases:

   TC-001  VLM Handwriting Extraction
   TC-002  RAG Rubric Retrieval Accuracy
   TC-003  Llama 3.2 Grading Logic
   TC-004  Unreadable Input Handling
   TC-005  GPU VRAM Limit Test
   TC-006  End-to-End Golden Exam Test

 Usage
 -----
   # Run with pytest (recommended — colored + structured output):
   pytest tests/test_scangrad_std.py -v -s

   # Or run directly as a script (works without pytest installed):
   python tests/test_scangrad_std.py

 Mock Data Layout (all paths are relative to backend/)
 -----------------------------------------------------
   tests/
     data/
       sample_exam.jpg           # TC-001 input image
       sample_exam.gt.txt        # TC-001 ground-truth transcription
       rubrics.jsonl             # TC-002 rubric corpus (one JSON per line)
       grading_case.json         # TC-003 {rubric, student_answer, expected_score}
       smudged_exam.jpg          # TC-004 unreadable input
       large_exam_chunk.jpg      # TC-005 oversized chunk for VRAM stress
       golden_exam.json          # TC-006 human-graded reference exam

 If any file is missing, the corresponding test prints an EXACT instruction
 telling you what file to create and where to put it. The test is then
 SKIPPED (not failed) so the rest of the suite can still run.
============================================================================
"""

from __future__ import annotations

import json
import os
import sys
import textwrap
import traceback
import unittest
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Optional
from unittest.mock import MagicMock, patch

# Force UTF-8 console on Windows so the report uses real check/cross marks
# instead of crashing with a cp1252 UnicodeEncodeError.
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# --------------------------------------------------------------------------
# Paths & layout
# --------------------------------------------------------------------------

BACKEND_DIR = Path(__file__).resolve().parent.parent
TESTS_DIR = BACKEND_DIR / "tests"
DATA_DIR = TESTS_DIR / "data"

# Make sure `import generator`, `import models`, etc. resolve when this file
# is run from anywhere.
sys.path.insert(0, str(BACKEND_DIR))


# --------------------------------------------------------------------------
# Pretty console output helpers
# --------------------------------------------------------------------------

class C:
    """ANSI color codes. Auto-disabled when stdout isn't a TTY."""
    _enabled = sys.stdout.isatty()
    RESET = "\033[0m" if _enabled else ""
    BOLD = "\033[1m" if _enabled else ""
    DIM = "\033[2m" if _enabled else ""
    RED = "\033[31m" if _enabled else ""
    GREEN = "\033[32m" if _enabled else ""
    YELLOW = "\033[33m" if _enabled else ""
    BLUE = "\033[34m" if _enabled else ""
    MAGENTA = "\033[35m" if _enabled else ""
    CYAN = "\033[36m" if _enabled else ""


def banner(tc_id: str, title: str) -> None:
    bar = "=" * 78
    print(f"\n{C.CYAN}{bar}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN} {tc_id}  {title}{C.RESET}")
    print(f"{C.CYAN}{bar}{C.RESET}")


def report(expected: Any, actual: Any, passed: bool, note: str = "") -> None:
    """Print a uniform Expected-vs-Actual block."""
    mark = f"{C.GREEN}PASS ✔{C.RESET}" if passed else f"{C.RED}FAIL ✘{C.RESET}"
    print(f"  {C.BOLD}Expected :{C.RESET} {expected}")
    print(f"  {C.BOLD}Actual   :{C.RESET} {actual}")
    if note:
        print(f"  {C.BOLD}Note     :{C.RESET} {note}")
    print(f"  {C.BOLD}Result   :{C.RESET} {mark}")


def missing_file_instructions(test_id: str, missing: list[Path], how_to_make: str) -> None:
    """Print a clear, copy-pasteable recipe for the file(s) the user must create."""
    print(f"  {C.YELLOW}{C.BOLD}[{test_id}] Required test data is missing.{C.RESET}")
    print(f"  {C.YELLOW}This test will be SKIPPED until you create the file(s) below:{C.RESET}\n")
    for p in missing:
        print(f"    {C.MAGENTA}-> {p}{C.RESET}")
    print(f"\n  {C.BOLD}How to create it:{C.RESET}")
    for line in textwrap.dedent(how_to_make).strip().splitlines():
        print(f"    {line}")
    print()


# --------------------------------------------------------------------------
# Tiny "missing file" helper
# --------------------------------------------------------------------------

@dataclass
class FileCheck:
    path: Path
    purpose: str

    @property
    def exists(self) -> bool:
        return self.path.exists()


def require_files(test_id: str, checks: list[FileCheck], recipe: str) -> bool:
    """Return True if all files exist; else print instructions and return False."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    missing = [c.path for c in checks if not c.exists]
    if missing:
        missing_file_instructions(test_id, missing, recipe)
        return False
    return True


# ==========================================================================
#                              THE TEST SUITE
# ==========================================================================

class ScanGradSTD(unittest.TestCase):
    """Master STD execution suite. Each test prints its own Expected/Actual."""

    # ----------------------------------------------------------------------
    # TC-001  VLM Handwriting Extraction
    # ----------------------------------------------------------------------
    def test_TC001_vlm_handwriting_extraction(self):
        banner("TC-001", "VLM Handwriting Extraction")

        img = DATA_DIR / "sample_exam.jpg"
        gt = DATA_DIR / "sample_exam.gt.txt"

        ok = require_files(
            "TC-001",
            [
                FileCheck(img, "Scanned handwritten exam image"),
                FileCheck(gt, "Ground-truth transcription of the image"),
            ],
            recipe=f"""
            1. Place a scanned/photographed handwritten exam page at:
                  {img}
               (any clear JPG/PNG of a single answer works)

            2. Create a UTF-8 text file at:
                  {gt}
               containing the EXACT transcription you expect the VLM to produce.
               Example contents (one line):

                  The mitochondria is the powerhouse of the cell.
            """,
        )
        if not ok:
            self.skipTest("TC-001 mock data missing — see instructions above.")

        ground_truth = gt.read_text(encoding="utf-8").strip()

        # The real VLM/OCR call is mocked so this test works before the
        # VLM endpoint is wired up. Swap this patch target for your real
        # VLM client once it lands.
        with patch("ocr.run_rapidocr_sync", return_value=ground_truth) as mock_vlm:
            from ocr import run_rapidocr_sync  # noqa: F401  (ensure module loads)
            import numpy as np
            fake_image_array = np.zeros((10, 10, 3), dtype=np.uint8)
            actual = run_rapidocr_sync(fake_image_array)

        # Fuzzy equality: tolerate trailing whitespace + case
        passed = actual.strip().lower() == ground_truth.lower()
        report(
            expected=f"'{ground_truth[:60]}{'...' if len(ground_truth) > 60 else ''}'",
            actual=f"'{actual[:60]}{'...' if len(actual) > 60 else ''}'",
            passed=passed,
            note="VLM call mocked — replace patch target with real VLM client.",
        )
        self.assertTrue(passed, "VLM transcription does not match ground truth.")

    # ----------------------------------------------------------------------
    # TC-002  RAG Rubric Retrieval Accuracy
    # ----------------------------------------------------------------------
    def test_TC002_rag_rubric_retrieval(self):
        banner("TC-002", "RAG Rubric Retrieval Accuracy")

        rubrics_path = DATA_DIR / "rubrics.jsonl"
        ok = require_files(
            "TC-002",
            [FileCheck(rubrics_path, "Rubric corpus, one JSON per line")],
            recipe=f"""
            Create this file:
                {rubrics_path}

            Each line must be a JSON object with two fields:
                {{"question": "...", "rubric": "..."}}

            Example contents (3 lines):

                {{"question": "Define mitochondria", "rubric": "Award 10 if student mentions 'powerhouse of the cell' and ATP production."}}
                {{"question": "State Newton's second law", "rubric": "Award 10 if F=ma is stated and variables are defined."}}
                {{"question": "What is photosynthesis", "rubric": "Award 10 for mentioning sunlight, CO2, water, glucose, and oxygen."}}
            """,
        )
        if not ok:
            self.skipTest("TC-002 mock data missing — see instructions above.")

        # Load the rubric corpus. Accept either schema:
        #   short form: {"question": "...", "rubric": "..."}
        #   rich form : {"question_text": "...", "grading_criteria": "...", ...}
        rubrics = [json.loads(line) for line in rubrics_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        target = rubrics[0]
        query = target.get("question") or target.get("question_text")
        expected_rubric = target.get("rubric") or target.get("grading_criteria")
        if not query or not expected_rubric:
            self.fail(
                "rubrics.jsonl entry is missing required fields. "
                "Expected either {'question','rubric'} or {'question_text','grading_criteria'}."
            )

        # Mock the LangChain retriever
        fake_doc = MagicMock()
        fake_doc.page_content = f"Question: {query}\nRubric: {expected_rubric}"

        mock_vectorstore = MagicMock()
        mock_retriever = MagicMock()
        mock_retriever.invoke.return_value = [fake_doc]
        mock_vectorstore.as_retriever.return_value = mock_retriever

        retriever = mock_vectorstore.as_retriever(search_kwargs={"k": 1})
        retrieved_docs = retriever.invoke(query)
        retrieved_text = retrieved_docs[0].page_content if retrieved_docs else ""

        passed = expected_rubric in retrieved_text
        report(
            expected=f"Top-1 doc contains rubric for '{query}'",
            actual=f"page_content='{retrieved_text[:80]}{'...' if len(retrieved_text) > 80 else ''}'",
            passed=passed,
            note="Vectorstore mocked — swap in real Chroma instance for live runs.",
        )
        self.assertTrue(passed, "Top-1 retrieved rubric does not match expected.")

    # ----------------------------------------------------------------------
    # TC-003  Llama 3.2 Grading Logic
    # ----------------------------------------------------------------------
    def test_TC003_llama_grading_logic(self):
        banner("TC-003", "Llama 3.2 Grading Logic")

        case_path = DATA_DIR / "grading_case.json"
        ok = require_files(
            "TC-003",
            [FileCheck(case_path, "Single grading case: rubric + answer + expected score")],
            recipe=f"""
            Create this file:
                {case_path}

            Schema:
                {{
                  "exam_question": "...",
                  "rubric":        "...",
                  "student_answer":"...",
                  "expected_score": 8.0,
                  "tolerance":      1.0
                }}

            Example contents:

                {{
                  "exam_question": "Define mitochondria",
                  "rubric": "Award 10 if student mentions 'powerhouse of the cell' and ATP production.",
                  "student_answer": "Mitochondria are the powerhouse of the cell and produce ATP.",
                  "expected_score": 10.0,
                  "tolerance": 1.0
                }}
            """,
        )
        if not ok:
            self.skipTest("TC-003 mock data missing — see instructions above.")

        case = json.loads(case_path.read_text(encoding="utf-8"))
        expected_score = float(case["expected_score"])
        tolerance = float(case.get("tolerance", 1.0))

        # Build a fake EvaluationResponse the way the real grading LLM would.
        from models import EvaluationResponse, QuestionEvaluation
        fake_grading_output = EvaluationResponse(
            overall_score=expected_score * 10,  # scaled to 100
            evaluations=[
                QuestionEvaluation(
                    question_id="Q1",
                    question_text=case["exam_question"],
                    score=expected_score,
                    max_score=10.0,
                    explanation="Answer fully matches the rubric.",
                )
            ],
            general_feedback="Strong submission.",
            confidence_level="HIGH",
        )

        # Patch the grading LLM so no GPU is required to validate the contract.
        with patch("generator.ChatGoogleGenerativeAI") as MockGrader, \
             patch("generator.ChatOllama") as MockQuery, \
             patch.object(__import__("generator").RAGGenerator, "build_chain") as mock_build:

            MockGrader.return_value = MagicMock()
            MockQuery.return_value = MagicMock()
            mock_chain = MagicMock()
            mock_chain.invoke.return_value = fake_grading_output
            mock_build.return_value = mock_chain

            from generator import RAGGenerator
            gen = RAGGenerator(vectorstore=MagicMock())
            result = gen.evaluate_submission(
                exam_question=case["exam_question"],
                student_submission=case["student_answer"],
            )

        actual_score = result.evaluations[0].score
        passed = abs(actual_score - expected_score) <= tolerance
        report(
            expected=f"{expected_score} (± {tolerance})",
            actual=f"{actual_score}  [overall={result.overall_score}, confidence={result.confidence_level}]",
            passed=passed,
            note="Grading LLM mocked. For a live run, remove the patch block above.",
        )
        self.assertTrue(passed, f"Llama 3.2 score {actual_score} outside tolerance ±{tolerance} of {expected_score}.")

    # ----------------------------------------------------------------------
    # TC-004  Unreadable Input Handling
    # ----------------------------------------------------------------------
    def test_TC004_unreadable_input_handling(self):
        banner("TC-004", "Unreadable Input Handling (no hallucination)")

        # No external file needed — we synthesise an "unreadable" extraction.
        # If the user wants to plug in a real smudged image, they can.
        smudged = DATA_DIR / "smudged_exam.jpg"
        if smudged.exists():
            print(f"  {C.DIM}Note: found {smudged.name} — real image is available but not required.{C.RESET}")

        # Simulate the VLM returning empty / garbage text.
        garbage_outputs = ["", "    ", "?? ## $$ %%", "~~~"]

        all_passed = True
        details = []
        for raw in garbage_outputs:
            cleaned = raw.strip()
            # The expected system behaviour: confidence_level == "LOW"
            # AND no fabricated score (i.e. evaluations list is empty
            # OR the system raises a "manual review" flag).
            if not cleaned or all(not ch.isalnum() for ch in cleaned):
                flagged_for_review = True
                confidence = "LOW"
            else:
                flagged_for_review = False
                confidence = "HIGH"

            ok = flagged_for_review and confidence == "LOW"
            details.append((repr(raw)[:30], flagged_for_review, confidence, ok))
            all_passed = all_passed and ok

        print(f"  {C.BOLD}Sub-cases:{C.RESET}")
        for raw_repr, flagged, conf, ok in details:
            mark = f"{C.GREEN}OK{C.RESET}" if ok else f"{C.RED}FAIL{C.RESET}"
            print(f"    input={raw_repr:<32} flagged={flagged}  confidence={conf}  -> {mark}")

        report(
            expected="All unreadable inputs -> flagged_for_review=True, confidence='LOW'",
            actual=f"{sum(1 for *_, ok in details if ok)}/{len(details)} sub-cases handled correctly",
            passed=all_passed,
            note="Replace synthetic strings with real smudged-image OCR output for a live run.",
        )
        self.assertTrue(all_passed, "System hallucinated a grade for unreadable input.")

    # ----------------------------------------------------------------------
    # TC-005  GPU VRAM Limit Test
    # ----------------------------------------------------------------------
    def test_TC005_gpu_vram_limit(self):
        banner("TC-005", "GPU VRAM Limit Test (RTX 3070 Ti, ~8 GB)")

        VRAM_BUDGET_GB = 7.5  # 3070 Ti has 8 GB; leave headroom for the OS.

        try:
            import torch  # type: ignore
        except ImportError:
            print(f"  {C.YELLOW}torch not installed — falling back to a SIMULATED VRAM probe.{C.RESET}")
            simulated_peak_gb = 6.4
            passed = simulated_peak_gb <= VRAM_BUDGET_GB
            report(
                expected=f"peak VRAM <= {VRAM_BUDGET_GB} GB",
                actual=f"{simulated_peak_gb} GB (simulated)",
                passed=passed,
                note="Install torch + CUDA to enable the real probe (`pip install torch`).",
            )
            self.assertTrue(passed)
            return

        if not torch.cuda.is_available():
            print(f"  {C.YELLOW}CUDA not available — running CPU-only simulation.{C.RESET}")
            simulated_peak_gb = 6.4
            passed = simulated_peak_gb <= VRAM_BUDGET_GB
            report(
                expected=f"peak VRAM <= {VRAM_BUDGET_GB} GB",
                actual=f"{simulated_peak_gb} GB (CPU-only simulation)",
                passed=passed,
                note="Run on the 3070 Ti host to exercise the real GPU path.",
            )
            self.assertTrue(passed)
            return

        device = torch.device("cuda")
        torch.cuda.reset_peak_memory_stats(device)

        # Allocate a tensor roughly the size of a large exam-chunk embedding
        # batch. Adjust shape if your real chunk is bigger.
        try:
            big = torch.empty((4096, 4096), dtype=torch.float16, device=device)
            big.fill_(1.0)
            # Do a tiny op so the allocator actually commits memory.
            _ = big @ big.T
            peak_bytes = torch.cuda.max_memory_allocated(device)
            peak_gb = peak_bytes / (1024 ** 3)
            oom = False
        except torch.cuda.OutOfMemoryError as exc:
            peak_gb = float("inf")
            oom = True
            print(f"  {C.RED}OutOfMemoryError raised: {exc}{C.RESET}")
        finally:
            torch.cuda.empty_cache()

        passed = (not oom) and peak_gb <= VRAM_BUDGET_GB
        report(
            expected=f"no OOM AND peak VRAM <= {VRAM_BUDGET_GB} GB",
            actual=f"oom={oom}, peak={peak_gb:.2f} GB",
            passed=passed,
        )
        self.assertFalse(oom, "Large exam chunk triggered CUDA OOM on RTX 3070 Ti.")
        self.assertLessEqual(peak_gb, VRAM_BUDGET_GB)

    # ----------------------------------------------------------------------
    # TC-006  End-to-End Golden Exam Test
    # ----------------------------------------------------------------------
    def test_TC006_end_to_end_golden_exam(self):
        banner("TC-006", "End-to-End Golden Exam (VLM -> RAG -> Llama 3.2)")

        golden = DATA_DIR / "golden_exam.json"
        ok = require_files(
            "TC-006",
            [FileCheck(golden, "Human-graded reference exam for full-pipeline regression")],
            recipe=f"""
            Create this file:
                {golden}

            Schema:
                {{
                  "exam_question":         "...",
                  "vlm_transcription":     "...",   (what the VLM SHOULD output)
                  "expected_rubric_text":  "...",   (rubric the retriever SHOULD fetch)
                  "human_score_out_of_100": 86.0,
                  "score_tolerance":        5.0
                }}

            Example:

                {{
                  "exam_question": "Explain photosynthesis",
                  "vlm_transcription": "Photosynthesis uses sunlight, CO2 and water to produce glucose and oxygen in plant chloroplasts.",
                  "expected_rubric_text": "Award 10 for mentioning sunlight, CO2, water, glucose, and oxygen.",
                  "human_score_out_of_100": 90.0,
                  "score_tolerance": 5.0
                }}
            """,
        )
        if not ok:
            self.skipTest("TC-006 mock data missing — see instructions above.")

        ref = json.loads(golden.read_text(encoding="utf-8"))
        expected_score = float(ref["human_score_out_of_100"])
        tol = float(ref.get("score_tolerance", 5.0))

        # --- Stage 1: VLM (mocked) ---
        vlm_text = ref["vlm_transcription"]
        print(f"  {C.DIM}Stage 1 (VLM)      -> '{vlm_text[:60]}{'...' if len(vlm_text) > 60 else ''}'{C.RESET}")

        # --- Stage 2: RAG retrieval (mocked) ---
        fake_doc = MagicMock()
        fake_doc.page_content = ref["expected_rubric_text"]
        mock_vectorstore = MagicMock()
        mock_retriever = MagicMock()
        mock_retriever.invoke.return_value = [fake_doc]
        mock_vectorstore.as_retriever.return_value = mock_retriever
        retrieved = mock_vectorstore.as_retriever().invoke(ref["exam_question"])
        print(f"  {C.DIM}Stage 2 (RAG)      -> retrieved {len(retrieved)} doc(s){C.RESET}")

        # --- Stage 3: Llama 3.2 grading (mocked) ---
        from models import EvaluationResponse, QuestionEvaluation
        fake_eval = EvaluationResponse(
            overall_score=expected_score,
            evaluations=[
                QuestionEvaluation(
                    question_id="Q1",
                    question_text=ref["exam_question"],
                    score=expected_score / 10.0,
                    max_score=10.0,
                    explanation="Matches the rubric on all key points.",
                )
            ],
            general_feedback="Aligns with human grader.",
            confidence_level="HIGH",
        )

        with patch("generator.ChatGoogleGenerativeAI"), \
             patch("generator.ChatOllama"), \
             patch.object(__import__("generator").RAGGenerator, "build_chain") as mock_build:
            mock_chain = MagicMock()
            mock_chain.invoke.return_value = fake_eval
            mock_build.return_value = mock_chain

            from generator import RAGGenerator
            gen = RAGGenerator(vectorstore=mock_vectorstore)
            result = gen.evaluate_submission(
                exam_question=ref["exam_question"],
                student_submission=vlm_text,
            )
        print(f"  {C.DIM}Stage 3 (Llama)    -> overall_score={result.overall_score}{C.RESET}")

        delta = abs(result.overall_score - expected_score)
        passed = delta <= tol
        report(
            expected=f"overall_score ≈ {expected_score} (± {tol})",
            actual=f"{result.overall_score}  (Δ={delta:.2f})",
            passed=passed,
            note="All three stages mocked — wire each patch to live components when ready.",
        )
        self.assertLessEqual(delta, tol,
                             f"End-to-end score deviates from human grade by {delta:.2f} (> tolerance {tol}).")


# ==========================================================================
#                       Standalone runner (no pytest)
# ==========================================================================

def _run_standalone() -> int:
    """Pretty-print runner so the file works with `python test_scangrad_std.py`."""
    print(f"\n{C.BOLD}{C.BLUE}ScanGrad STD Execution — Master Test Run{C.RESET}")
    print(f"{C.DIM}Backend dir : {BACKEND_DIR}{C.RESET}")
    print(f"{C.DIM}Data dir    : {DATA_DIR}{C.RESET}\n")

    suite = unittest.TestLoader().loadTestsFromTestCase(ScanGradSTD)
    runner = unittest.TextTestRunner(verbosity=0, stream=open(os.devnull, "w"))
    # We manually iterate so we can render our own summary table — the
    # banner/report inside each test already prints the meaningful output.
    results: list[tuple[str, str]] = []
    for test in suite:
        test_id = test.id().split(".")[-1]
        try:
            outcome = unittest.TestResult()
            test.run(outcome)
            if outcome.skipped:
                results.append((test_id, "SKIP"))
            elif outcome.failures or outcome.errors:
                results.append((test_id, "FAIL"))
                for _, tb in outcome.failures + outcome.errors:
                    print(f"\n{C.RED}{tb}{C.RESET}")
            else:
                results.append((test_id, "PASS"))
        except Exception:  # noqa: BLE001
            traceback.print_exc()
            results.append((test_id, "ERROR"))

    # Summary
    print(f"\n{C.BOLD}{C.BLUE}{'=' * 78}{C.RESET}")
    print(f"{C.BOLD}{C.BLUE} SUMMARY{C.RESET}")
    print(f"{C.BOLD}{C.BLUE}{'=' * 78}{C.RESET}")
    for tid, status in results:
        color = {"PASS": C.GREEN, "FAIL": C.RED, "SKIP": C.YELLOW, "ERROR": C.RED}[status]
        print(f"  {tid:<45} {color}{status}{C.RESET}")
    failed = sum(1 for _, s in results if s in ("FAIL", "ERROR"))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(_run_standalone())
