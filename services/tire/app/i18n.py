"""
Internationalization (i18n) support for TIRE.

Simple JSON-based translation with no external dependencies.
"""

import json
from pathlib import Path
from typing import Callable, Dict


class I18n:
    """Loads JSON locale files and provides translation lookups."""

    SUPPORTED_LANGS = ("en", "zh")
    DEFAULT_LANG = "en"

    def __init__(self):
        self.translations: Dict[str, Dict[str, str]] = {}
        locale_dir = Path(__file__).parent.parent / "locales"
        for lang in self.SUPPORTED_LANGS:
            path = locale_dir / f"{lang}.json"
            if path.exists():
                with open(path, encoding="utf-8") as f:
                    self.translations[lang] = json.load(f)
            else:
                self.translations[lang] = {}

    def t(self, key: str, lang: str = "en", **kwargs) -> str:
        """Translate *key* into *lang*, falling back to English then the key itself."""
        msg = self.translations.get(lang, {}).get(key)
        if msg is None:
            msg = self.translations.get("en", {}).get(key, key)
        if kwargs:
            try:
                msg = msg.format(**kwargs)
            except (KeyError, IndexError):
                pass
        return msg

    def get_translator(self, lang: str) -> Callable[..., str]:
        """Return a bound ``t(key, **kwargs)`` callable for *lang*."""
        resolved = lang if lang in self.SUPPORTED_LANGS else self.DEFAULT_LANG

        def _t(key: str, **kwargs) -> str:
            return self.t(key, resolved, **kwargs)

        return _t


# Singleton – import and use directly.
i18n = I18n()
