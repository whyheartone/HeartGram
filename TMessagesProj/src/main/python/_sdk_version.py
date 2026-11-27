"""SDK version metadata module matching exteraGram's ``_sdk_version`` surface."""

import sys


def _resolve_sdk_version() -> str:
    try:
        from app.exteraless.plugins import PythonBridge
        ver = PythonBridge.getSdkVersion()
        if ver:
            return str(ver)
    except Exception:
        pass
    try:
        loader = sys.modules.get("extera_utils.plugin_loader")
        engine_java_class = getattr(loader, "engine_java_class", None)
        if callable(engine_java_class):
            bridge = engine_java_class("app.exteraless.plugins.PythonBridge")
            if bridge is not None:
                ver = bridge.getSdkVersion()
                if ver:
                    return str(ver)
    except Exception:
        pass
    return "1.4.6.5"


def _parse_version_tuple(raw: str) -> tuple:
    parts = []
    for chunk in str(raw).split("."):
        digits = []
        for ch in chunk:
            if ch.isdigit():
                digits.append(ch)
            else:
                break
        parts.append(int("".join(digits)) if digits else 0)
    return tuple(parts)


__version__: str = _resolve_sdk_version()
version = _parse_version_tuple(__version__)
version_str: str = __version__
__beta__: bool = False
beta: bool = __beta__
