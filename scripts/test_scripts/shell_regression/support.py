# This is a private project. All rights reserved.
"""Isolated fixtures that execute copies of the production Shell scripts."""
import os
from pathlib import Path
import shutil
import tempfile
import unittest

import yaml
from execution import ProcessLease, ProcessReleaseError, run_owned

ROOT = Path(__file__).resolve().parents[3]
BASH = os.environ.get("SHELL_TEST_BASH") or shutil.which("bash")
if not BASH and os.name == "nt":
    git = shutil.which("git")
    if git:
        BASH = str(Path(git).parents[1] / "bin/bash.exe")


def shell_path(path):
    path = Path(path).resolve()
    if os.name == "nt":
        return "/" + path.drive[0].lower() + path.as_posix()[2:]
    return str(path)


def cases(matrix):
    """Give every parameter row a discoverable, independently selectable test ID."""
    def decorate(function):
        function.case_matrix = matrix
        return function
    return decorate


def parameterize(cls):
    for name, function in list(vars(cls).items()):
        if not hasattr(function, "case_matrix"):
            continue
        delattr(cls, name)
        for label, values in function.case_matrix.items():
            def test(self, function=function, values=values):
                function(self, *values)
            test.__name__ = name + "_" + label
            setattr(cls, test.__name__, test)
    return cls


class ShellCase(unittest.TestCase):
    def setUp(self):
        if not BASH or not Path(BASH).is_file():
            raise RuntimeError("Bash is required; set SHELL_TEST_BASH to its executable")
        output = os.environ.get("SHELL_TEST_OUTPUT")
        parent = Path(output) if output else ROOT / "build/test-results/shell-regression"
        parent.mkdir(parents=True, exist_ok=True)
        self.artifacts = parent / self.id()
        self.artifacts.mkdir(parents=True, exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix="fixture-", dir=self.artifacts))
        self.process_leases = []
        self.addCleanup(self.remove_fixture)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.home = self.root / "home"
        self.home.mkdir()
        # Isolate operator deployment/desktop inputs. All external tools used by
        # these contracts are fixtures; no device, Docker or Gradle is contacted.
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith(("MOCK_", "BUILD_", "APPIUM_", "CLI_"))
                    and key not in {"APP_ENV_FILE", "BUNKER_ENV_FILE", "GRADLEW",
                                    "INIT_ENABLE", "LOG_PATH", "BASH_ENV", "ENV"}}
        self.env.update(PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        HOME=shell_path(self.home), APPIUM_DESKTOP_PLATFORM="Windows",
                        CALL_LOG=shell_path(self.root / "calls"))
        self.command_number = 0

    def remove_fixture(self):
        if any(not lease.safe_to_remove for lease in self.process_leases):
            raise ProcessReleaseError(f"Preserving fixture because process release is unconfirmed: {self.root}")
        shutil.rmtree(self.root)

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        path.chmod(0o755)
        return path

    def copy(self, relative):
        return self.write(relative, (ROOT / relative).read_text(encoding="utf-8"))

    def fake(self, name, body):
        return self.write("bin/" + name, "#!/bin/bash\nset -euo pipefail\n" + body)

    def recorder(self, name, body=""):
        return self.fake(name, '''printf '%s\\0' "${0##*/}" "$#" "$@" "${APP_ENV_FILE:-}" "${BUILD_ON:-}" >> "$CALL_LOG"
''' + body)

    def calls(self, name):
        path = self.root / "calls"
        if not path.exists():
            return []
        fields = path.read_bytes().decode().split("\0")[:-1]
        records = []
        while fields:
            command, count = fields[:2]
            count = int(count)
            arguments = fields[2:2 + count]
            app_env, build_on = fields[2 + count:4 + count]
            if command == name:
                records.append((arguments, app_env, build_on))
            fields = fields[4 + count:]
        return records

    def run_script(self, relative, *args, expected=0, cwd=None, env=None, interpreter=None, timeout=45):
        self.command_number += 1
        arguments = self.root / "arguments.nul"
        invocation = ([interpreter] if interpreter else []) + [shell_path(self.root / relative), *args]
        arguments.write_bytes(b"".join(arg.encode() + b"\0" for arg in invocation))
        environment = self.env | (env or {}) | {
            "SHELL_TEST_BIN": shell_path(self.bin),
            "SHELL_TEST_ARGUMENTS": shell_path(arguments)}
        # Native Windows -> MSYS startup can expand wildcards before Bash sees
        # argv. Read them inside Bash so this tests the production script's quoting.
        lease = ProcessLease()
        self.process_leases.append(lease)
        result = run_owned(
            [BASH, "-c", 'export PATH="$SHELL_TEST_BIN:$PATH"; '
             'mapfile -d "" -t args < "$SHELL_TEST_ARGUMENTS"; '
             'exec "${args[@]}"'],
            cwd=cwd or self.root, env=environment,
            output=self.artifacts / str(self.command_number), timeout=timeout, lease=lease,
        )
        self.assertEqual(result.returncode, expected,
                         (result.stdout + result.stderr).decode(errors="replace"))
        return result

    def yaml(self, relative):
        return yaml.safe_load((self.root / relative).read_text(encoding="utf-8"))
