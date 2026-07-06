#!/usr/bin/env python3
"""
AT-SPI2 helper for the Appium Linux driver.
Called as a subprocess with JSON input/output.
"""
import sys
import json
import time
import pyatspi


def get_app(app_pid):
    desktop = pyatspi.Registry.getDesktop(0)
    for app in desktop:
        if app and app.get_process_id() == app_pid:
            return app
    return None


def wait_for_app(app_pid, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        app = get_app(app_pid)
        if app is not None:
            return app
        time.sleep(0.5)
    return None


def node_to_dict(node, depth=0, max_depth=20):
    if node is None or depth > max_depth:
        return None
    try:
        role = node.getRoleName()
        name = node.name or ""
        desc = node.description or ""
        try:
            component = node.queryComponent()
            bbox = component.getExtents(pyatspi.DESKTOP_COORDS)
            bounds = {"x": bbox.x, "y": bbox.y, "w": bbox.width, "h": bbox.height}
        except Exception:
            bounds = None
        try:
            text_iface = node.queryText()
            value = text_iface.getText(0, -1)
        except Exception:
            value = None
        children = []
        for i in range(node.childCount):
            child = node.getChildAtIndex(i)
            child_dict = node_to_dict(child, depth + 1, max_depth)
            if child_dict:
                children.append(child_dict)
        return {
            "role": role,
            "name": name,
            "desc": desc,
            "value": value,
            "bounds": bounds,
            "children": children,
        }
    except Exception as e:
        return {"role": "error", "name": str(e), "children": []}


def find_nodes(node, strategy, selector, results, max_results=10):
    if node is None or len(results) >= max_results:
        return
    try:
        role = node.getRoleName()
        name = node.name or ""
        desc = node.description or ""
        try:
            text_iface = node.queryText()
            value = text_iface.getText(0, -1)
        except Exception:
            value = ""
        matched = False
        if strategy == "name":
            matched = name == selector or desc == selector or value == selector
        elif strategy == "name_contains":
            matched = selector in name or selector in desc or selector in value
        elif strategy == "xpath":
            # simplified: treat xpath like //*/[@name='X'] or //*[contains(@name,'X')]
            import re
            m = re.search(r"@name='([^']+)'", selector)
            mc = re.search(r"contains\(@(?:name|value),'([^']+)'\)", selector)
            if selector == "//text-field":
                state = node.getState()
                matched = role == "text" and state.contains(pyatspi.STATE_EDITABLE)
            elif m:
                matched = name == m.group(1) or desc == m.group(1) or value == m.group(1)
            elif mc:
                needle = mc.group(1)
                matched = needle in name or needle in desc or needle in value
        if matched:
            results.append(node)
        for i in range(node.childCount):
            if len(results) >= max_results:
                break
            find_nodes(node.getChildAtIndex(i), strategy, selector, results, max_results)
    except Exception:
        pass


def wait_for_elements(app_pid, strategy, selector, timeout=15):
    deadline = time.time() + timeout
    last_results = []
    while time.time() < deadline:
        app = get_app(app_pid)
        if app:
            results = []
            find_nodes(app, strategy, selector, results)
            if results:
                return results
            last_results = results
        time.sleep(0.2)
    return last_results


def get_center(node):
    try:
        component = node.queryComponent()
        bbox = component.getExtents(pyatspi.DESKTOP_COORDS)
        return bbox.x + bbox.width // 2, bbox.y + bbox.height // 2
    except Exception:
        return None, None


def perform_action(node, preferred=("click", "press", "activate")):
    try:
        action = node.queryAction()
        for name in preferred:
            for i in range(action.nActions):
                if action.getName(i) == name:
                    return action.doAction(i), name
        if action.nActions > 0:
            return action.doAction(0), action.getName(0)
    except Exception:
        pass
    return False, None


def find_focusable(node):
    """Find first focusable/editable child."""
    try:
        state = node.getState()
        if state.contains(pyatspi.STATE_EDITABLE) or state.contains(pyatspi.STATE_FOCUSABLE):
            return node
        for i in range(node.childCount):
            result = find_focusable(node.getChildAtIndex(i))
            if result:
                return result
    except Exception:
        pass
    return None


def find_editable(node):
    try:
        role = node.getRoleName()
        state = node.getState()
        if role == "text" and state.contains(pyatspi.STATE_EDITABLE):
            return node
        for i in range(node.childCount):
            result = find_editable(node.getChildAtIndex(i))
            if result:
                return result
    except Exception:
        pass
    return None


def main():
    cmd = json.loads(sys.stdin.read())
    action = cmd["action"]

    if action == "wait_for_app":
        app = wait_for_app(cmd["pid"], cmd.get("timeout", 30))
        if app:
            print(json.dumps({"ok": True, "name": app.name}))
        else:
            print(json.dumps({"ok": False, "error": "app not found"}))

    elif action == "page_source":
        app = get_app(cmd["pid"])
        if not app:
            print(json.dumps({"ok": False, "error": "app not found"}))
            return
        tree = node_to_dict(app)
        print(json.dumps({"ok": True, "tree": tree}))

    elif action == "find_elements":
        app = wait_for_app(cmd["pid"], cmd.get("timeout", 15))
        if not app:
            print(json.dumps({"ok": False, "error": "app not found"}))
            return
        results = wait_for_elements(
            cmd["pid"],
            cmd["strategy"],
            cmd["selector"],
            cmd.get("timeout", 15),
        )
        elements = []
        for i, node in enumerate(results):
            cx, cy = get_center(node)
            elements.append({
                "id": i,
                "name": node.name or "",
                "role": node.getRoleName(),
                "cx": cx,
                "cy": cy,
            })
        print(json.dumps({"ok": True, "elements": elements}))

    elif action == "find_input":
        app = wait_for_app(cmd["pid"], cmd.get("timeout", 15))
        if not app:
            print(json.dumps({"ok": False, "error": "app not found"}))
            return
        results = []
        find_nodes(app, "name", cmd.get("selector", ""), results)
        target = results[0] if results else app
        focusable = find_focusable(target) or target
        cx, cy = get_center(focusable)
        print(json.dumps({"ok": True, "cx": cx, "cy": cy}))

    elif action == "click_element":
        app = wait_for_app(cmd["pid"], cmd.get("timeout", 15))
        if not app:
            print(json.dumps({"ok": False, "error": "app not found"}))
            return
        results = wait_for_elements(
            cmd["pid"],
            cmd.get("strategy", "name"),
            cmd["selector"],
            cmd.get("timeout", 15),
        )
        if not results:
            print(json.dumps({"ok": False, "error": "element not found"}))
            return
        ok, action_name = perform_action(results[0])
        cx, cy = get_center(results[0])
        print(json.dumps({"ok": ok, "action": action_name, "cx": cx, "cy": cy}))

    elif action == "set_text":
        app = wait_for_app(cmd["pid"], cmd.get("timeout", 15))
        if not app:
            print(json.dumps({"ok": False, "error": "app not found"}))
            return
        selector = cmd.get("selector", "")
        results = []
        if selector:
            results = wait_for_elements(
                cmd["pid"],
                cmd.get("strategy", "name"),
                selector,
                cmd.get("timeout", 15),
            )
        target = results[0] if results else find_editable(app)
        if not target:
            print(json.dumps({"ok": False, "error": "editable element not found"}))
            return
        perform_action(target)
        try:
            editable = target.queryEditableText()
            editable.setTextContents(cmd.get("text", ""))
            cx, cy = get_center(target)
            print(json.dumps({"ok": True, "cx": cx, "cy": cy}))
        except Exception as e:
            print(json.dumps({"ok": False, "error": str(e)}))


if __name__ == "__main__":
    main()
