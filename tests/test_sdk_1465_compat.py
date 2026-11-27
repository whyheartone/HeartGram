import contextlib
import importlib
import sys
import types
from pathlib import Path

import pytest

PYTHON_ROOT = Path(__file__).resolve().parent.parent / "TMessagesProj" / "src" / "main" / "python"


@pytest.fixture(autouse=True)
def _python_path(monkeypatch):
    monkeypatch.syspath_prepend(str(PYTHON_ROOT))
    pkg = types.ModuleType("extera_utils")
    pkg.__path__ = [str(PYTHON_ROOT / "extera_utils")]
    loader_stub = types.ModuleType("extera_utils.plugin_loader")
    loader_stub.current_plugin_id = lambda: None
    loader_stub.engine_java_class = lambda name: None
    loader_stub.plain_java_class = lambda name: False
    loader_stub.guard_java_class = lambda name: True
    loader_stub.require_permission = lambda *a, **k: None
    loader_stub.plugin_frame_owner = lambda: None
    loader_stub.java_runtime_mark = lambda owner: contextlib.nullcontext()
    pkg.plugin_loader = loader_stub
    before = set(sys.modules)
    monkeypatch.setitem(sys.modules, "extera_utils", pkg)
    monkeypatch.setitem(sys.modules, "extera_utils.plugin_loader", loader_stub)
    yield
    for mod_name in list(sys.modules):
        if mod_name not in before:
            sys.modules.pop(mod_name, None)


def test_sdk_version_module_and_java_constant():
    java_constants = (
        Path(__file__).resolve().parent.parent
        / "TMessagesProj" / "src" / "main" / "java"
        / "app" / "exteraless" / "plugins" / "PluginsConstants.java"
    ).read_text(encoding="utf-8")
    assert 'SDK_VERSION = "1.4.6.5"' in java_constants

    sdk_ver = importlib.import_module("_sdk_version")
    assert sdk_ver.__version__ == "1.4.6.5"
    assert sdk_ver.version_str == "1.4.6.5"
    assert sdk_ver.version == (1, 4, 6, 5)
    assert isinstance(sdk_ver.__beta__, bool)


def test_log_utils_surface_and_logger():
    log_utils = importlib.import_module("log_utils")
    captured = []
    logger = log_utils.Logger("demo", show_location=False, _sink=captured.append)
    logger.level = log_utils.LogLevel.DEBUG
    logger.verbose("skip")
    logger.info("hello %s", "world")
    logger.dump({"a": [1, 2, 3]}, label="cfg")
    with logger.timed("work"):
        pass
    assert any("hello world" in line for line in captured)
    assert all("skip" not in line for line in captured)
    assert any("cfg: {'a': [1, 2, 3]}" in line for line in captured)
    assert any("work took" in line for line in captured)

    child = logger.child("sub")
    assert child.tag == "demo.sub"
    assert "{'x': 1}" == log_utils.dump({"x": 1})


def test_base_plugin_1465_additions():
    bp = importlib.import_module("base_plugin")
    logged = []

    class MyPlugin(bp.BasePlugin):
        pass

    plugin = MyPlugin()
    plugin._exteraless_attach("demo_plugin")
    plugin.log = logged.append
    plugin.logger.info("hi %d", 42)
    assert any("hi 42" in line for line in logged)

    cleaned = []
    plugin.register_cleanup(lambda: cleaned.append("done"))
    assert not plugin.stop_event.is_set()
    plugin._exteraless_cleanup_resources()
    assert plugin.stop_event.is_set()
    assert cleaned == ["done"]

    item = bp.MenuItemData(
        menu_type=bp.MenuItemType.MESSAGE_CONTEXT_MENU,
        text="Test",
        on_click=lambda ctx: None,
        on_long_click=lambda ctx: None,
    )
    assert callable(item.on_long_click)
    assert bp.HookFilter.ArgumentIsNull(1).filter_data.arg_index == 1
    assert bp.IntentHookType.BEFORE.value == 0
    assert bp.HookResult(error="boom").error == "boom"
    with pytest.raises(NotImplementedError):
        plugin.hook_call_site(None, None, lambda chain: None)


def test_client_markdown_settings_and_hook_utils():
    cu = importlib.import_module("client_utils")
    assert cu.account_scope is cu.hook_scope

    mu = importlib.import_module("markdown_utils")
    assert mu.ParsedMessage is mu.ParsedText
    parsed = mu.parse_markdown("**bold**")
    assert parsed.text == "bold"
    assert isinstance(parsed.entities[0].type, mu.TLEntityType)

    ps = importlib.import_module("plugin_settings")
    ps.invalidate("p1")
    ps.set_all_settings("p1", {"k": 123})
    assert ps.get_all_settings("p1")["k"] == 123
    ps.clear_settings("p1")
    assert ps.get_all_settings("p1") == {}

    hu = importlib.import_module("hook_utils")
    assert callable(hu.java_class)
    assert callable(hu.get_method)
    assert callable(hu.get_constructor)
