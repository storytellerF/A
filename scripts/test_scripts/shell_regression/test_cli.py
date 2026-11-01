# This is a private project. All rights reserved.
from support import ShellCase, shell_path


class CliEntrypointTests(ShellCase):
    def test_readiness_without_presets_is_logged_and_propagates_failure(self):
        self.copy("scripts/docker/cli-entrypoint.sh")
        self.write("cli", "")
        self.fake("nc", 'cat > "$READY_RESPONSE"\nexit 37\n')
        result = self.run_script("scripts/docker/cli-entrypoint.sh", expected=37, interpreter="sh", env={
            "READY_RESPONSE": shell_path(self.root / "response"),
            "CLI_PATH": shell_path(self.root / "cli"), "CLI_INIT_ENABLE": "false",
            "LOG_PATH": shell_path(self.root / "log directory")})
        self.assertEqual((self.root / "response").read_bytes(),
                         b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK")
        self.assertEqual(result.stdout, (
            b"[cli-entrypoint] CLI_INIT_ENABLE is not true; ready without preset data load.\n"
            b"[cli-entrypoint] ready server listening on port 8081.\n"))
        self.assertEqual((self.root / "log directory/cli.log").read_bytes(), result.stdout)
        self.assertEqual(result.stderr, b"")
