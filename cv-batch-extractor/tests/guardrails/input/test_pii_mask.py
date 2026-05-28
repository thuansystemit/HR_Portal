import pytest
from app.guardrails.base import PipelineContext
from app.guardrails.input.pii_mask import PiiMaskGuard, PiiTokenizer


# ---------------------------------------------------------------------------
# PiiTokenizer unit tests
# ---------------------------------------------------------------------------

class TestPiiTokenizerMask:
    def test_masks_email(self):
        tok = PiiTokenizer()
        result = tok.mask("Contact alice@example.com for details")
        assert "alice@example.com" not in result
        assert "__PII_EMAIL_0__" in result

    def test_masks_multiple_emails(self):
        tok = PiiTokenizer()
        result = tok.mask("alice@example.com and bob@example.org")
        assert "__PII_EMAIL_0__" in result
        assert "__PII_EMAIL_1__" in result

    def test_masks_linkedin_url(self):
        tok = PiiTokenizer()
        result = tok.mask("See https://linkedin.com/in/alice-smith for profile")
        assert "alice-smith" not in result
        assert "__PII_LINKEDIN_0__" in result

    def test_masks_github_url(self):
        tok = PiiTokenizer()
        result = tok.mask("Code at https://github.com/alice-dev/repo")
        assert "alice-dev" not in result
        assert "__PII_GITHUB_0__" in result

    def test_masks_phone_international(self):
        tok = PiiTokenizer()
        result = tok.mask("Call +1-800-555-1234 anytime")
        assert "+1-800-555-1234" not in result
        assert "__PII_PHONE_0__" in result

    def test_masks_phone_nanp(self):
        tok = PiiTokenizer()
        result = tok.mask("My number is (800) 555-1234")
        assert "(800) 555-1234" not in result
        assert "__PII_PHONE_0__" in result

    def test_does_not_mask_year_range(self):
        tok = PiiTokenizer()
        result = tok.mask("Worked from 2019-2023 at Acme Corp")
        assert result == "Worked from 2019-2023 at Acme Corp"

    def test_linkedin_masked_before_generic_content(self):
        tok = PiiTokenizer()
        text = "https://linkedin.com/in/john-doe and john@example.com"
        result = tok.mask(text)
        assert "__PII_LINKEDIN_0__" in result
        assert "__PII_EMAIL_0__" in result
        assert "john-doe" not in result
        assert "john@example.com" not in result

    def test_total_masked_count(self):
        tok = PiiTokenizer()
        tok.mask("alice@example.com — +1-800-555-1234")
        assert tok.total_masked == 2

    def test_no_pii_returns_original(self):
        tok = PiiTokenizer()
        text = "Senior Python Developer with 5 years experience"
        assert tok.mask(text) == text


class TestPiiTokenizerRestore:
    def test_restore_replaces_token(self):
        tok = PiiTokenizer()
        tok.mask("alice@example.com")
        assert tok.restore("__PII_EMAIL_0__") == "alice@example.com"

    def test_restore_none_returns_none(self):
        tok = PiiTokenizer()
        assert tok.restore(None) is None

    def test_restore_empty_string(self):
        tok = PiiTokenizer()
        assert tok.restore("") == ""

    def test_restore_unknown_token_unchanged(self):
        tok = PiiTokenizer()
        assert tok.restore("__PII_EMAIL_99__") == "__PII_EMAIL_99__"

    def test_first_of_kind_email(self):
        tok = PiiTokenizer()
        tok.mask("alice@example.com")
        assert tok.first_of_kind("EMAIL") == "alice@example.com"

    def test_first_of_kind_missing(self):
        tok = PiiTokenizer()
        tok.mask("no email here, just text")
        assert tok.first_of_kind("EMAIL") is None

    def test_roundtrip_multiple_pii(self):
        tok = PiiTokenizer()
        original = "Email alice@example.com or call (800) 555-1234"
        masked = tok.mask(original)
        restored = tok.restore(tok.restore(masked))
        assert "alice@example.com" in restored
        assert "(800) 555-1234" in restored


# ---------------------------------------------------------------------------
# PiiMaskGuard integration tests
# ---------------------------------------------------------------------------

class TestPiiMaskGuard:
    def _ctx(self, raw_text: str, prompt_text: str | None = None) -> PipelineContext:
        ctx = PipelineContext(document_id="doc1", category_id="cat1", file_path="/f")
        ctx.raw_text = raw_text
        ctx.prompt_text = prompt_text
        return ctx

    def test_guard_masks_raw_text(self):
        ctx = self._ctx("alice@example.com")
        report = PiiMaskGuard().run(ctx)
        assert report.status == "PASS"
        assert "alice@example.com" not in ctx.raw_text
        assert "__PII_EMAIL_0__" in ctx.raw_text

    def test_guard_masks_prompt_text(self):
        ctx = self._ctx("plain", prompt_text="bob@example.com")
        PiiMaskGuard().run(ctx)
        assert "bob@example.com" not in ctx.prompt_text
        assert "__PII_EMAIL_0__" in ctx.prompt_text

    def test_guard_attaches_tokenizer(self):
        ctx = self._ctx("alice@example.com")
        PiiMaskGuard().run(ctx)
        assert ctx.pii_tokenizer is not None
        assert ctx.pii_tokenizer.first_of_kind("EMAIL") == "alice@example.com"

    def test_guard_reports_masked_count(self):
        ctx = self._ctx("alice@example.com — +1-800-555-1234")
        report = PiiMaskGuard().run(ctx)
        assert report.metadata["masked_tokens"] == 2

    def test_guard_passthrough_when_no_pii(self):
        text = "Senior Engineer, 5 years experience"
        ctx = self._ctx(text)
        PiiMaskGuard().run(ctx)
        assert ctx.raw_text == text

    def test_guard_handles_none_raw_text(self):
        ctx = PipelineContext(document_id="d", category_id="c", file_path="/f")
        report = PiiMaskGuard().run(ctx)
        assert report.status == "PASS"
        assert ctx.raw_text is None
