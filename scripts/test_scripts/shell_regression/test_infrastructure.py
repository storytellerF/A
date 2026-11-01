# This is a private project. All rights reserved.
import io
import json
import os
import runpy
import shutil
import subprocess
import sys
import time
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

from reporting import write_junit
from execution import ProcessCleanupError, ProcessLease, ProcessPhase, ProcessReleaseError, run_owned
from support import ROOT, ShellCase, cases, parameterize, shell_path

ENTRY_POINTS = {
    "build_args": ("build-and-test-args-test.sh", "test_build_and_test"),
    "images": ("build-service-images-test.sh", "test_images"),
    "cli": ("cli-entrypoint-test.sh", "test_cli"),
    "compose": ("compose-service-test.sh", "test_compose"),
    "desktop": ("run-linux-desktop-e2e-args-test.sh", "test_desktop"),
    "bootstrap": ("run-shell-tests.sh", "test_infrastructure"),
}


@parameterize
class EntryPointTests(ShellCase):
    @cases({**{name + "_sh": (*values, "sh") for name, values in ENTRY_POINTS.items()},
            **{name + "_executable": (*ENTRY_POINTS[name], None)
               for name in ("build_args", "cli", "compose")}})
    def test_original_entry_discovers_complete_suite(self, filename, module, interpreter):
        entry = ROOT / "scripts/test_scripts" / filename
        # Check original bytes before execution: fixture copies normalize line
        # endings, and Git Bash may tolerate CRLF that /bin/sh on Linux rejects.
        self.assertNotIn(b"\r", entry.read_bytes())
        args = ["--list"]
        if filename == "run-shell-tests.sh":
            args += ["--suite", "infrastructure"]
        result = self.run_script(str(entry), *args, interpreter=interpreter,
                                 env={"PYTHON_COMMAND": shell_path(sys.executable)})
        loader = unittest.TestLoader()
        suite = loader.discover(str(ROOT / "scripts/test_scripts/shell_regression"), pattern=module + ".py")

        def ids(tests):
            for test in tests:
                if isinstance(test, unittest.TestSuite):
                    yield from ids(test)
                else:
                    yield test.id()

        self.assertEqual(loader.errors, [])
        expected = list(ids(suite))
        self.assertTrue(expected)
        self.assertEqual(result.stdout.decode().splitlines(), expected)


@parameterize
class FixtureRetentionTests(ShellCase):
    @cases({"interruption": (KeyboardInterrupt, False), "io_error": (OSError, False),
            "later_success": (KeyboardInterrupt, True)})
    def test_failed_cleanup_preserves_fixture(self, error_type, later_success):
        # The owner is a protocol stub; it never launches an OS process. Teardown
        # may therefore remove this synthetic retained fixture after assertions.
        with patch.dict(os.environ, {"SHELL_TEST_OUTPUT": str(self.artifacts / "probe")}):
            probe = ShellCase()
            probe.setUp()
        self.addCleanup(shutil.rmtree, probe.root)
        sentinel = probe.write("sentinel", "preserve until release is confirmed")
        probe.write("probe.sh", "#!/bin/sh\nexit 0\n")
        journal = probe.artifacts / "1.container.jsonl"
        journal.write_text(json.dumps({"token": "retention", "phase": "running"}) + "\n", encoding="utf-8")
        primary = error_type("synthetic caller failure")
        owner = Mock()
        owner.poll.return_value = None
        owner.stdin = io.BytesIO()

        def fail_while_owned(_duration):
            with journal.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps({"token": "retention", "phase": "error",
                                         "error": "synthetic cleanup failure"}) + "\n")
            raise primary

        with patch("execution.subprocess.Popen", return_value=owner), \
                patch("execution.uuid.uuid4", return_value=Mock(hex="retention")), \
                patch("execution.time.sleep", side_effect=fail_while_owned):
            with self.assertRaises(error_type) as failure:
                probe.run_script("probe.sh")
        self.assertIs(failure.exception, primary)
        self.assertIsInstance(failure.exception.__cause__, ProcessReleaseError)
        self.assertEqual(probe.process_leases[0].phase, ProcessPhase.OWNED)
        if later_success:
            probe.run_script("probe.sh")
            self.assertEqual(probe.process_leases[-1].phase, ProcessPhase.RELEASED)
        with self.assertRaisesRegex(ProcessReleaseError, "Preserving fixture"):
            probe.remove_fixture()
        self.assertEqual(sentinel.read_text(), "preserve until release is confirmed")


@parameterize
class ControlPipeCleanupTests(ShellCase):
    def prepare_owner(self, released=False, buffered=False):
        # Use a real buffered OS pipe so close() retries a failed flush. The
        # protocol owner is a stub and never launches or retains an OS process.
        reader, writer = os.pipe()
        os.close(reader)
        pipe = os.fdopen(writer, "wb")

        def close_pipe():
            if not pipe.closed:
                try:
                    pipe.close()
                except OSError:
                    pass

        self.addCleanup(close_pipe)
        if buffered:
            pipe.write(b"pending control message")
        owner = Mock()
        owner.stdin = pipe
        owner.poll.return_value = None
        owner.wait.return_value = 0
        self.output = self.artifacts / "pipe"
        self.journal = self.output.with_suffix(".container.jsonl")
        self.publish("released" if released else "running")
        return owner, ProcessLease()

    def publish(self, phase):
        state = {"token": "pipe", "phase": phase}
        if phase == "released":
            state.update(active=0, root_code=0)
        if phase == "error":
            state["error"] = "synthetic release failure"
        with self.journal.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(state) + "\n")

    def execute(self, owner, lease, timeout=45):
        with patch("execution.subprocess.Popen", return_value=owner), \
                patch("execution.uuid.uuid4", return_value=Mock(hex="pipe")):
            return run_owned(["synthetic"], cwd=self.root, env=self.env,
                             output=self.output, timeout=timeout, lease=lease)

    @cases({"interruption": (KeyboardInterrupt,), "io_error": (OSError,)})
    def test_broken_pipe_preserves_caller_and_all_cleanup_errors(self, error_type):
        owner, lease = self.prepare_owner()
        primary = error_type("synthetic caller failure")

        def fail_while_owned(_duration):
            self.publish("error")
            raise primary

        with patch("execution.time.sleep", side_effect=fail_while_owned):
            with self.assertRaises(error_type) as failure:
                self.execute(owner, lease)
        self.assertIs(failure.exception, primary)
        combined = failure.exception.__cause__
        self.assertIsInstance(combined, ProcessCleanupError)
        self.assertEqual(len(combined.errors), 2)
        self.assertIsInstance(combined.errors[0], ProcessReleaseError)
        self.assertIsInstance(combined.errors[1], OSError)
        self.assertTrue(owner.stdin.closed)
        self.assertEqual(lease.phase, ProcessPhase.OWNED)

    def test_close_failure_keeps_confirmed_release_and_interruption(self):
        owner, lease = self.prepare_owner()
        primary = KeyboardInterrupt("synthetic caller interruption")

        def interrupt_then_release(_duration):
            self.publish("released")
            raise primary

        with patch("execution.time.sleep", side_effect=interrupt_then_release):
            with self.assertRaises(KeyboardInterrupt) as failure:
                self.execute(owner, lease)
        self.assertIs(failure.exception, primary)
        self.assertIsInstance(failure.exception.__cause__, OSError)
        self.assertTrue(owner.stdin.closed)
        self.assertEqual(lease.phase, ProcessPhase.RELEASED)

    def test_close_failure_preserves_timeout_and_raw_output(self):
        owner, lease = self.prepare_owner()
        polls = 0

        def release_after_timeout():
            nonlocal polls
            polls += 1
            if polls == 2:
                self.publish("released")
            return None

        owner.poll.side_effect = release_after_timeout
        with self.assertRaises(subprocess.TimeoutExpired) as failure:
            self.execute(owner, lease, timeout=0)
        self.assertEqual(failure.exception.timeout, 0)
        self.assertEqual(failure.exception.stdout, self.output.with_suffix(".stdout").read_bytes())
        self.assertEqual(failure.exception.stderr, self.output.with_suffix(".stderr").read_bytes())
        self.assertIsInstance(failure.exception.__cause__, OSError)
        self.assertTrue(owner.stdin.closed)
        self.assertEqual(lease.phase, ProcessPhase.RELEASED)

    def test_close_only_failure_is_reported(self):
        owner, lease = self.prepare_owner(released=True, buffered=True)
        with self.assertRaises(OSError):
            self.execute(owner, lease)
        self.assertTrue(owner.stdin.closed)
        self.assertEqual(lease.phase, ProcessPhase.RELEASED)


@parameterize
class InterpreterTests(ShellCase):
    @cases({"sh_shebang": ("sh", None), "explicit_sh": ("bash", "sh")})
    def test_sh_contract_and_exact_arguments(self, shebang, interpreter):
        self.write("probe.sh", f"#!/bin/{shebang}\n" + '''if [ -n "${BASH_VERSION:-}" ]; then
  case ":${SHELLOPTS:-}:" in *:posix:*) ;; *) exit 99 ;; esac
fi
printf '%s\\0' "$@"
''')
        self.write("expanded.test", "")
        result = self.run_script("probe.sh", "with spaces", "*.test", "", interpreter=interpreter)
        self.assertEqual(result.stdout, b"with spaces\0*.test\0\0")

    def test_bash_shebang_keeps_bash_features(self):
        self.write("probe.sh", '#!/bin/bash\nvalues=(one two)\n[[ ${values[1]} == two ]]\n')
        self.run_script("probe.sh")


@parameterize
class ProcessOwnershipTests(ShellCase):
    def setUp(self):
        super().setUp()
        # This process belongs to the test runner, outside the command's owner.
        self.unrelated = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"],
                                          stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                          stderr=subprocess.DEVNULL)
        self.addCleanup(self.stop_unrelated)

    def stop_unrelated(self):
        if self.unrelated.poll() is None:
            self.unrelated.terminate()
        self.unrelated.wait(timeout=5)

    def prepare_tree(self, detached, root_exits):
        child = self.write("child.py", '''from pathlib import Path
import os
import time
Path("ready").write_text(str(os.getpid()))
os.write(1, b"child stdout\\n")
os.write(2, b"child stderr\\n")
time.sleep(60)
''')
        launcher = self.write("launch.py", '''import os
import subprocess
import sys
options = {}
if os.environ["DETACHED"] == "true":
    if os.name == "nt":
        options["creationflags"] = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        options["start_new_session"] = True
subprocess.Popen([sys.executable, os.environ["CHILD_SCRIPT"]], stdout=sys.stdout, stderr=sys.stderr, **options)
''')
        self.env.update(PROBE_PYTHON=shell_path(sys.executable), LAUNCH_SCRIPT=shell_path(launcher),
                        CHILD_SCRIPT=str(child), DETACHED="true" if detached else "false")
        self.write("tree.sh", '''#!/bin/sh
"$PROBE_PYTHON" "$LAUNCH_SCRIPT"
while [ ! -f ready ]; do sleep 0.01; done
''' + ("exit 7\n" if root_exits else "sleep 60\n"))

    def assert_released(self):
        states = [json.loads(line) for line in (self.artifacts / "1.container.jsonl").read_text().splitlines()]
        self.assertEqual(states[-1]["phase"], "released")
        self.assertEqual(states[-1]["active"], 0)
        self.assertIsNotNone(states[-1]["root_code"])
        self.assertTrue((self.root / "ready").is_file(), "Descendant must start before cleanup is tested")
        self.assert_pid_gone(int((self.root / "ready").read_text()))
        self.assertIsNone(self.unrelated.poll(), "Cleanup must preserve unrelated processes")
        return states

    def assert_pid_gone(self, pid):
        if os.name != "nt":
            with self.assertRaises(ProcessLookupError):
                os.kill(pid, 0)
            return
        import ctypes
        from ctypes import wintypes
        api = ctypes.WinDLL("kernel32", use_last_error=True)
        api.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        api.OpenProcess.restype = wintypes.HANDLE
        api.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        api.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = api.OpenProcess(0x00100000, False, pid)
        if not handle:
            self.assertEqual(ctypes.get_last_error(), 87, "PID must be gone, not merely inaccessible")
            return
        try:
            self.assertEqual(api.WaitForSingleObject(handle, 0), 0, "Descendant is still running")
        finally:
            api.CloseHandle(handle)

    @cases({"live_root": (False, False), "exited_root": (False, True),
            "detached_child": (True, False), "exited_root_detached_child": (True, True)})
    def test_timeout_releases_entire_tree(self, detached, root_exits):
        self.prepare_tree(detached, root_exits)
        started = time.monotonic()
        with self.assertRaises(subprocess.TimeoutExpired) as failure:
            self.run_script("tree.sh", timeout=2)
        self.assertLess(time.monotonic() - started, 9)
        self.assertEqual(failure.exception.timeout, 2)
        self.assertIn(b"child stdout", failure.exception.stdout)
        self.assertIn(b"child stderr", failure.exception.stderr)
        states = self.assert_released()
        if root_exits:
            self.assertTrue(any(state.get("root_code") == 7 and state.get("active", 0) > 0 for state in states))

    def test_interrupt_releases_tree_and_preserves_exception(self):
        self.prepare_tree(True, False)
        sleep = time.sleep
        interrupted = False

        def interrupt_when_ready(duration):
            nonlocal interrupted
            if not interrupted and (self.root / "ready").exists():
                interrupted = True
                raise KeyboardInterrupt("synthetic caller interruption")
            sleep(duration)

        with patch("execution.time.sleep", side_effect=interrupt_when_ready):
            with self.assertRaisesRegex(KeyboardInterrupt, "synthetic caller interruption"):
                self.run_script("tree.sh")
        self.assert_released()


@parameterize
class ReportSerializationTests(ShellCase):
    @cases({"failure": ("failure",), "error": ("error",), "skip": ("skipped",)})
    def test_diagnostics_round_trip_with_xml_forbidden_characters(self, kind):
        result_class = runpy.run_path(str(ROOT / "scripts/test_scripts/run-shell-tests.py"))["ReportResult"]
        message = '\x1b[31mShell\x1b[0m\x00\ud800\ufffe 中文 🎯 <&"'

        def scenario(case):
            if kind == "failure":
                case.fail(message)
            elif kind == "error":
                raise ValueError(message)
            else:
                case.skipTest(message)

        probe_class = type("DiagnosticProbe", (unittest.TestCase,), {"test_diagnostic": scenario})
        result = unittest.TextTestRunner(stream=io.StringIO(), resultclass=result_class).run(
            unittest.defaultTestLoader.loadTestsFromTestCase(probe_class))
        path = self.artifacts / "diagnostic.xml"
        write_junit(result.xml, path)
        diagnostic = ET.parse(path).getroot().find(f"testcase/{kind}")
        self.assertIsNotNone(diagnostic)
        serialized = diagnostic.attrib["message"]
        for escaped in ("\\u001b", "\\u0000", "\\ud800", "\\ufffe"):
            self.assertIn(escaped, serialized)
        self.assertIn('中文 🎯 <&"', serialized)
        raw = json.loads(path.with_suffix(".raw.json").read_text())
        self.assertTrue(any(element["attributes"].get("message") == message for element in raw))
        self.assertEqual(result.xml.find(f"testcase/{kind}").attrib["message"], message)
