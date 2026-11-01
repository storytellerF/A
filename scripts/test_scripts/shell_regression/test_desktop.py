# This is a private project. All rights reserved.
from support import ShellCase, cases, parameterize


@parameterize
class DesktopRunnerTests(ShellCase):
    def setUp(self):
        super().setUp()
        self.copy("scripts/test_scripts/run-linux-desktop-e2e.sh")
        for command in ("Xvfb", "openbox", "appium"):
            self.fake(command, '''printf '%s\\n' "$$" >> "$DESKTOP_PID_LOG"
if [[ ${0##*/} == Xvfb ]]; then printf '99\\n' >&3; fi
exec sleep 60
''')
        self.fake("xprop", "printf '_NET_SUPPORTING_WM_CHECK(WINDOW): window id # 0x1\\n'\n")
        for command in ("xdpyinfo", "curl"):
            self.fake(command, "exit 0\n")
        gradle = self.recorder("gradlew", '''# Wait until the owned fake processes have registered before completing.
for ((i=0; i<100; i++)); do
  [[ $(wc -l < "$DESKTOP_PID_LOG") == 3 ]] && break
  sleep 0.05
done
exit "${MOCK_GRADLE_EXIT:-0}"
''')
        self.write("gradlew", gradle.read_text())
        self.env["DESKTOP_PID_LOG"] = self.env["CALL_LOG"].rsplit("/", 1)[0] + "/pids"
        self.write("pids", "")
        # Probe MSYS/POSIX PIDs through the same Shell runtime that owns them.
        # This supervisor also cleans only this fixture's processes if an assertion fails.
        self.write("supervise.sh", '''#!/bin/bash
set -euo pipefail
sleep 60 >/dev/null 2>&1 &
unrelated=$!
cleanup() {
  kill "$unrelated" 2>/dev/null || true
  wait "$unrelated" 2>/dev/null || true
  while read -r pid; do kill "$pid" 2>/dev/null || true; done < "$DESKTOP_PID_LOG"
}
trap cleanup EXIT
result=0
bash scripts/test_scripts/run-linux-desktop-e2e.sh --desktop-session "$@" || result=$?
kill -0 "$unrelated" || { echo 'Unrelated process was terminated' >&2; exit 1; }
while read -r pid; do
  if kill -0 "$pid" 2>/dev/null; then
    echo "Owned desktop process was not cleaned up: $pid" >&2
    exit 1
  fi
done < "$DESKTOP_PID_LOG"
exit "$result"
''')

    @cases({"success": (0,), "failure": (7,)})
    def test_options_status_and_owned_process_cleanup(self, status):
        options = ["--tests", "com.example.FirstTest.method with spaces", "--tests=com.example.*Test",
                   "--no-debug-jvm", "--test-dry-run", "--rerun"]
        global_options = ["--max-workers=4", "-Pserver.flavor=sample", "-Pserver.buildType=test"]
        self.write("com.example.ExpandedTest", "")
        self.run_script("supervise.sh", *options, *global_options, expected=status,
                        env={"MOCK_GRADLE_EXIT": str(status)})
        self.assertEqual([call[0] for call in self.calls("gradlew")], [[
            ":app:desktopAppium:appiumTest", *options,
            ":panel:desktopAppium:appiumTest", *options,
            "--continue", "--console=plain", *global_options]])
        self.assertEqual(len((self.root / "pids").read_text().splitlines()), 3)

    @cases({"missing": ("--tests",), "empty_equals": ("--tests=",),
            "option_as_filter": ("--tests", "--rerun")})
    def test_invalid_filter_starts_no_processes(self, *args):
        self.run_script("supervise.sh", *args, expected=1)
        self.assertEqual(self.calls("gradlew"), [])
        self.assertEqual((self.root / "pids").read_text(), "")
