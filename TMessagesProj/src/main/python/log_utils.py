"""Structured plugin logging utilities (compatible with exteraGram SDK 1.4.6.x)."""

import inspect
import sys
import threading
import time
import traceback
from enum import IntEnum
from typing import Any, Callable, Optional

__all__ = ["LogLevel", "LogRecord", "Logger", "get_logger", "dump"]


class LogLevel(IntEnum):
    VERBOSE = 2
    DEBUG = 3
    INFO = 4
    WARNING = 5
    ERROR = 6
    OFF = 100


_LEVEL_NAMES = {
    "VERBOSE": LogLevel.VERBOSE,
    "V": LogLevel.VERBOSE,
    "DEBUG": LogLevel.DEBUG,
    "D": LogLevel.DEBUG,
    "INFO": LogLevel.INFO,
    "I": LogLevel.INFO,
    "WARN": LogLevel.WARNING,
    "WARNING": LogLevel.WARNING,
    "W": LogLevel.WARNING,
    "ERROR": LogLevel.ERROR,
    "E": LogLevel.ERROR,
    "OFF": LogLevel.OFF,
}


def _coerce_level(value) -> Optional[LogLevel]:
    if value is None:
        return None
    if isinstance(value, LogLevel):
        return value
    if isinstance(value, int):
        try:
            return LogLevel(value)
        except ValueError:
            return LogLevel.INFO
    if isinstance(value, str):
        key = value.strip().upper()
        if key in _LEVEL_NAMES:
            return _LEVEL_NAMES[key]
        try:
            return LogLevel(int(key))
        except ValueError:
            return LogLevel.INFO
    return LogLevel.INFO


class LogRecord:
    """Single formatted log event passed to logger handlers."""

    def __init__(self, level: LogLevel, tag: str, message: str,
                 location: Optional[str], tb: Optional[str], created: float) -> None:
        self.level = level
        self.tag = tag
        self.message = message
        self.location = location
        self.traceback = tb
        self.created = created

    @property
    def text(self) -> str:
        prefix = f"[{self.level.name}]"
        if self.tag:
            prefix += f" [{self.tag}]"
        if self.location:
            prefix += f" ({self.location})"
        body = f"{prefix} {self.message}"
        if self.traceback:
            body += "\n" + self.traceback.rstrip()
        return body


class _Timer:
    """Context manager measuring and logging elapsed time for a block."""

    def __init__(self, logger: "Logger", label: str, level: LogLevel) -> None:
        self._logger = logger
        self._label = label
        self._level = level
        self._start = 0.0

    def __enter__(self):
        self._start = time.monotonic()
        return self

    def __exit__(self, exc_type, exc, tb):
        elapsed_ms = (time.monotonic() - self._start) * 1000.0
        if exc_type is not None:
            self._logger.log(
                self._level,
                "%s failed after %.1f ms (%s: %s)",
                self._label, elapsed_ms, exc_type.__name__, exc,
                stacklevel=2,
            )
        else:
            self._logger.log(
                self._level,
                "%s took %.1f ms",
                self._label, elapsed_ms,
                stacklevel=2,
            )
        return False


class _Unreadable:
    def __init__(self, error: BaseException) -> None:
        self.error = error

    def __repr__(self) -> str:
        return f"<unreadable: {type(self.error).__name__}: {self.error}>"


class _Dumper:
    def __init__(self, depth: int, max_items: int) -> None:
        self._depth = max(0, int(depth))
        self._max_items = max(1, int(max_items))
        self._seen = set()

    def render(self, obj: Any) -> str:
        return self._fmt(obj, self._depth)

    def _fmt(self, obj: Any, depth: int) -> str:
        if obj is None or isinstance(obj, (bool, int, float)):
            return repr(obj)
        if isinstance(obj, str):
            return repr(obj) if len(obj) <= 200 else repr(obj[:200] + "...")
        if isinstance(obj, bytes):
            return repr(obj) if len(obj) <= 64 else f"<bytes len={len(obj)}>"
        if depth <= 0:
            try:
                return repr(obj)
            except Exception as err:
                return repr(_Unreadable(err))

        obj_id = id(obj)
        if obj_id in self._seen:
            return "<recursion>"
        self._seen.add(obj_id)
        try:
            if isinstance(obj, dict):
                items = list(obj.items())
                clipped = len(items) > self._max_items
                shown = items[:self._max_items]
                inner = ", ".join(
                    f"{self._fmt(k, depth - 1)}: {self._fmt(v, depth - 1)}"
                    for k, v in shown
                )
                if clipped:
                    inner += f", ... (+{len(items) - self._max_items})"
                return "{" + inner + "}"
            if isinstance(obj, (list, tuple, set, frozenset)):
                seq = list(obj)
                clipped = len(seq) > self._max_items
                shown = seq[:self._max_items]
                inner = ", ".join(self._fmt(x, depth - 1) for x in shown)
                if clipped:
                    inner += f", ... (+{len(seq) - self._max_items})"
                if isinstance(obj, tuple):
                    return "(" + inner + ("," if len(shown) == 1 and not clipped else "") + ")"
                if isinstance(obj, (set, frozenset)):
                    return "{" + inner + "}" if inner else "set()"
                return "[" + inner + "]"
            try:
                return repr(obj)
            except Exception as err:
                return repr(_Unreadable(err))
        finally:
            self._seen.discard(obj_id)


def dump(obj: Any, depth: int = 2, max_items: int = 20) -> str:
    """Format an arbitrary value safely into a concise debug string."""
    return _Dumper(depth, max_items).render(obj)


def _format_message(msg: Any, args: tuple) -> str:
    text = str(msg)
    if not args:
        return text
    try:
        return text % args
    except (TypeError, ValueError):
        return " ".join([text] + [str(a) for a in args])


def _format_exc(exc_info) -> Optional[str]:
    if not exc_info:
        return None
    if exc_info is True:
        tb = traceback.format_exc()
        return None if tb.strip() == "NoneType: None" else tb
    if isinstance(exc_info, BaseException):
        return "".join(traceback.format_exception(type(exc_info), exc_info, exc_info.__traceback__))
    if isinstance(exc_info, tuple) and len(exc_info) == 3:
        return "".join(traceback.format_exception(*exc_info))
    return str(exc_info)


def _caller_location(stacklevel: int) -> Optional[str]:
    try:
        frame = inspect.currentframe()
        steps = max(1, int(stacklevel)) + 2
        while frame is not None and steps > 0:
            frame = frame.f_back
            steps -= 1
        if frame is None:
            return None
        filename = frame.f_code.co_filename.replace("\\", "/").rsplit("/", 1)[-1]
        return f"{filename}:{frame.f_lineno}"
    except Exception:
        return None


def _active_plugin_id() -> Optional[str]:
    loader = sys.modules.get("extera_utils.plugin_loader")
    getter = getattr(loader, "current_plugin_id", None)
    if callable(getter):
        try:
            return getter()
        except Exception:
            return None
    return None


def _emit_to_runtime(tag: str, text: str) -> None:
    plugin_id = _active_plugin_id()
    target_id = plugin_id or tag or "sdk"
    try:
        from app.exteraless.plugins import PythonBridge
        PythonBridge.log(target_id, text)
        return
    except Exception:
        pass
    try:
        from android_utils import log as android_log
        android_log(text)
        return
    except Exception:
        pass
    print(f"[{target_id}] {text}", file=sys.stderr)


class Logger:
    """Plugin logger compatible with ``log_utils.Logger``."""

    def __init__(self, tag: str, *, show_location: bool = True,
                 _sink: Optional[Callable[[str], Any]] = None) -> None:
        self._tag = str(tag or "sdk")
        self._show_location = bool(show_location)
        self._level: Optional[LogLevel] = None
        self._handlers: list = []
        self._lock = threading.RLock()
        self._sink = _sink

    @property
    def tag(self) -> str:
        return self._tag

    @property
    def level(self) -> Optional[LogLevel]:
        return self._level

    @level.setter
    def level(self, value) -> None:
        self._level = _coerce_level(value)

    @property
    def effective_level(self) -> LogLevel:
        return self._level if self._level is not None else LogLevel.VERBOSE

    def is_enabled_for(self, level) -> bool:
        target = _coerce_level(level) or LogLevel.INFO
        return target >= self.effective_level

    def child(self, name: str) -> "Logger":
        child_tag = f"{self._tag}.{name}" if self._tag else str(name)
        child_logger = Logger(child_tag, show_location=self._show_location, _sink=self._sink)
        child_logger._level = self._level
        return child_logger

    def add_handler(self, handler: Callable[[LogRecord], Any]) -> None:
        with self._lock:
            if handler not in self._handlers:
                self._handlers.append(handler)

    def remove_handler(self, handler: Callable[[LogRecord], Any]) -> bool:
        with self._lock:
            try:
                self._handlers.remove(handler)
                return True
            except ValueError:
                return False

    def log(self, level, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        lvl = _coerce_level(level) or LogLevel.INFO
        if lvl < self.effective_level or lvl >= LogLevel.OFF:
            return
        location = _caller_location(stacklevel + 1) if self._show_location else None
        record = LogRecord(
            level=lvl,
            tag=self._tag,
            message=_format_message(msg, args),
            location=location,
            tb=_format_exc(exc_info),
            created=time.time(),
        )
        with self._lock:
            handlers = list(self._handlers)
        for handler in handlers:
            try:
                handler(record)
            except Exception:
                pass
        if self._sink is not None:
            try:
                self._sink(record.text)
                return
            except Exception:
                pass
        _emit_to_runtime(self._tag, record.text)

    def verbose(self, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        self.log(LogLevel.VERBOSE, msg, *args, exc_info=exc_info, stacklevel=stacklevel + 1)

    def debug(self, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        self.log(LogLevel.DEBUG, msg, *args, exc_info=exc_info, stacklevel=stacklevel + 1)

    def info(self, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        self.log(LogLevel.INFO, msg, *args, exc_info=exc_info, stacklevel=stacklevel + 1)

    def warning(self, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        self.log(LogLevel.WARNING, msg, *args, exc_info=exc_info, stacklevel=stacklevel + 1)

    def error(self, msg: Any, *args, exc_info: Any = None, stacklevel: int = 1) -> None:
        self.log(LogLevel.ERROR, msg, *args, exc_info=exc_info, stacklevel=stacklevel + 1)

    def exception(self, msg: Any, *args, stacklevel: int = 1) -> None:
        self.log(LogLevel.ERROR, msg, *args, exc_info=True, stacklevel=stacklevel + 1)

    def dump(self, obj: Any, label: Optional[str] = None, *,
             level: LogLevel = LogLevel.DEBUG, depth: int = 2,
             max_items: int = 20, stacklevel: int = 1) -> None:
        rendered = dump(obj, depth=depth, max_items=max_items)
        msg = f"{label}: {rendered}" if label else rendered
        self.log(level, msg, stacklevel=stacklevel + 1)

    def timed(self, label: str, level: LogLevel = LogLevel.DEBUG) -> _Timer:
        lvl = _coerce_level(level) or LogLevel.DEBUG
        return _Timer(self, label, lvl)


_loggers: dict = {}
_loggers_lock = threading.RLock()


def get_logger(name: Optional[str] = None) -> Logger:
    """Return a cached Logger for *name* (or the active plugin id)."""
    if name is None:
        name = _active_plugin_id()
    tag = str(name or "sdk")
    with _loggers_lock:
        logger = _loggers.get(tag)
        if logger is None:
            logger = Logger(tag)
            _loggers[tag] = logger
        return logger
