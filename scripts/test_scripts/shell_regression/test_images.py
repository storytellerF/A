# This is a private project. All rights reserved.
from support import ShellCase, cases, parameterize, shell_path


@parameterize
class ServiceImagesTests(ShellCase):
    @cases({"local_success": ("local", 0), "docker_failure": ("docker", 7)})
    def test_builds_all_targets_concurrently(self, mode, status):
        self.copy("scripts/build_scripts/build-service-images.sh")
        self.write("scripts/build_scripts/build-cloud.sh", '#!/bin/bash\ntouch host-build\n')
        for directory in ("started", "finished"):
            (self.root / directory).mkdir()
        self.fake("docker", '''target=
arguments=("$@")
while [[ $# -gt 0 ]]; do
  if [[ $1 == --target ]]; then target=$2; shift; fi
  shift
done
[[ -n $target ]]
printf '%s\\0' "${arguments[@]}" > "$IMAGE_TEST_RUN/started/$target"
ready=false
for ((i=0; i<100; i++)); do
  if [[ $(find "$IMAGE_TEST_RUN/started" -type f | wc -l) == 4 ]]; then ready=true; break; fi
  sleep 0.05
done
[[ $ready == true ]] || { echo 'All four builds must start before any completes.' >&2; exit 1; }
touch "$IMAGE_TEST_RUN/finished/$target"
if [[ $target == worker ]]; then exit "${MOCK_IMAGE_EXIT:-0}"; fi
''')
        self.run_script("scripts/build_scripts/build-service-images.sh", mode, expected=status,
                        env={"IMAGE_TEST_RUN": shell_path(self.root), "MOCK_IMAGE_EXIT": str(status)})
        targets = {"server", "worker", "cli", "ws"}
        self.assertEqual({p.name for p in (self.root / "finished").iterdir()}, targets)
        self.assertEqual((self.root / "host-build").exists(), mode == "local")
        for target in targets:
            self.assertEqual((self.root / "started" / target).read_bytes().decode().split("\0")[:-1],
                             ["build", "--platform", "linux/amd64", "--target", target,
                              "--build-arg", f"BUILD_ON={mode}", "-t", f"a-{target}:latest", "."])
