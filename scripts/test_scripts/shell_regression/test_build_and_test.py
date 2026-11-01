# This is a private project. All rights reserved.
from support import ShellCase, cases, parameterize

TASKS = [f":{target}:{platform}:" + task
         for target, platform, task in (
             ("app", "androidAppium", "appiumTest"),
             ("app", "desktopAppium", "appiumTest"),
             ("app", "wasmAppium", "appiumTest"),
             ("panel", "androidAppium", "appiumTest"),
             ("panel", "desktopAppium", "appiumTest"),
             ("panel", "wasmAppium", "appiumTest"),
             ("app", "cliE2e", "e2eTest"),
             ("panel", "cliE2e", "e2eTest"))]
PROPERTIES = ["--console=plain", "-Pserver.flavor=dev", "-Pserver.buildType=debug"]


@parameterize
class BuildAndTestTests(ShellCase):
    def setUp(self):
        super().setUp()
        self.copy("scripts/test_scripts/build-and-test.sh")
        gradle = self.recorder("gradlew", '''case "$*" in
  *:app:androidAppium:appiumTest*) exit "${MOCK_APPIUM_EXIT:-0}" ;;
esac
''')
        self.write("gradlew", gradle.read_text())
        self.recorder("adb", '''if [[ $1 == devices ]]; then
  printf 'List of devices attached\\nphysical-device\\tdevice\\n'
elif [[ $* == '-s physical-device shell getprop sys.boot_completed' ]]; then
  printf '1\\n'
else
  echo 'Unexpected adb command (the caller owns the device lock)' >&2
  exit 1
fi
''')

    def test_preserves_spaces_and_glob_filters(self):
        self.write("com.example.ExpandedTest", "")
        filters = ["com.example.FirstTest", "com.example.SecondTest.method with spaces",
                   "com.example.ThirdTest", "com.example.*Test"]
        self.run_script("scripts/test_scripts/build-and-test.sh", "--desktop", "--tests",
                        *filters[:2], "--tests", *filters[2:], "--plain")
        self.assertEqual([call[0] for call in self.calls("gradlew")], [
            ["detekt", *PROPERTIES],
            ["app:composeApp:desktopTest", *[arg for value in filters for arg in ("--tests", value)],
             *PROPERTIES]])

    @cases({"success": (0,), "failure": (7,)})
    def test_compose_mode_runs_shell_suite_after_detekt(self, status):
        launcher = self.recorder("run-shell-tests.sh", 'exit "${MOCK_SUITE_EXIT:-0}"\n')
        self.write("scripts/test_scripts/run-shell-tests.sh", launcher.read_text())
        self.run_script("scripts/test_scripts/build-and-test.sh", "--compose", "--plain",
                        expected=status, env={"MOCK_SUITE_EXIT": str(status)})
        self.assertEqual([call[0] for call in self.calls("gradlew")], [["detekt", *PROPERTIES]])
        self.assertEqual([call[0] for call in self.calls("run-shell-tests.sh")], [[]])
        self.assertEqual((self.root / "calls").read_bytes().split(b"\0")[0], b"gradlew")

    @cases({"missing_filters": ("--desktop", "--tests", "--plain"),
            "removed_unit": ("--unit",), "removed_prepare": ("--prepare",),
            "removed_run": ("--run",)})
    def test_rejects_invalid_arguments(self, *args):
        self.run_script("scripts/test_scripts/build-and-test.sh", *args, expected=1)
        self.assertEqual(self.calls("gradlew"), [])
        self.assertEqual(self.calls("adb"), [])

    @cases({"all_targets": (False, 0, "Windows"), "filtered_targets": (True, 0, "Windows"),
            "failure_propagation": (False, 7, "Windows"), "linux_targets": (False, 0, "Linux")})
    def test_e2e_uses_one_invocation(self, filtered, status, platform):
        args = ["--e2e", "--plain"]
        targets = TASKS + ([":app:linuxAppium:appiumTest"] if platform == "Linux" else [])
        if filtered:
            args += ["--tests", "com.example.*Test"]
            targets = [arg for task in targets for arg in (task, "--tests", "com.example.*Test")]
        self.run_script("scripts/test_scripts/build-and-test.sh", *args, expected=status,
                        env={"MOCK_APPIUM_EXIT": str(status), "APPIUM_DESKTOP_PLATFORM": platform})
        self.assertEqual([call[0] for call in self.calls("gradlew")], [
            ["detekt", *PROPERTIES], [*targets, "--continue", *PROPERTIES]])
        self.assertEqual([call[0] for call in self.calls("adb")], [
            ["devices"], ["-s", "physical-device", "shell", "getprop", "sys.boot_completed"]])
