import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import types
import unittest

sys.dont_write_bytecode = True
sys.modules['pyatspi'] = types.SimpleNamespace(STATE_DEFUNCT='defunct')
spec = importlib.util.spec_from_file_location(
    'helper', Path(__file__).parent.parent / 'lib' / 'atspi_helper.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class Node:
    def __init__(self, path, children=(), defunct=False):
        self.path = path
        self.children = children
        self.childCount = len(children)
        self.defunct = defunct
        self.text = ''
        self.writes = []

    def getChildAtIndex(self, index):
        return self.children[index]

    def getState(self):
        return types.SimpleNamespace(contains=lambda state: self.defunct)

    def queryEditableText(self):
        return self

    def setTextContents(self, text):
        self.writes.append(text)
        self.text = text

    def queryText(self):
        return self

    def getText(self, start, end):
        return self.text


class SetTextTest(unittest.TestCase):
    def run_command(self, children, path='/saved'):
        helper.wait_for_app = lambda *args: Node('/app', children)
        helper.wait_for_elements = lambda *args: self.fail('Must not re-find by selector')
        command = dict(action='set_text', pid=1, path=path, index=0,
                       selector='duplicate', text='requested')
        with contextlib.redirect_stdout(io.StringIO()) as output:
            with unittest.mock.patch.object(sys, 'stdin', io.StringIO(json.dumps(command))):
                helper.main()
        return json.loads(output.getvalue())

    def test_reordered_duplicate_uses_saved_path(self):
        other, saved = Node('/other'), Node('/saved')
        self.assertTrue(self.run_command([other, saved])['ok'])
        self.assertEqual(saved.writes, ['requested'])
        self.assertEqual(other.writes, [])

    def test_missing_target_never_writes_replacement(self):
        replacement = Node('/replacement')
        self.assertTrue(self.run_command([replacement])['stale'])
        self.assertEqual(replacement.writes, [])

    def test_defunct_target_is_stale(self):
        saved = Node('/saved', defunct=True)
        self.assertTrue(self.run_command([saved])['stale'])
        self.assertEqual(saved.writes, [])

    def test_disappearance_during_write_is_stale(self):
        saved = Node('/saved')

        def disappear(text):
            saved.defunct = True
            raise RuntimeError('Object disappeared')

        saved.setTextContents = disappear
        self.assertTrue(self.run_command([saved])['stale'])

    def test_missing_path_never_uses_index(self):
        saved = Node('/saved')
        self.assertTrue(self.run_command([saved], path=None)['stale'])
        self.assertEqual(saved.writes, [])


if __name__ == '__main__':
    import unittest.mock
    unittest.main()
