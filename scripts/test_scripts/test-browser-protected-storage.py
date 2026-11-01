# This is a private project. All rights reserved.
"""Run compiled Wasm storage contracts in a fresh browser profile per case."""
import argparse
import os
import pathlib
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE = ROOT / "client/composeCore/build"
CASES = (
    "readsEmptyStorage",
    "persistsAcrossConnectionRecreation",
    "isolatesAccountNamespaces",
    "storesCiphertextWithoutPlaintext",
    "rejectsMasterKeyExport",
    "rejectsTamperedCiphertext",
    "rejectsCiphertextFromAnotherNamespace",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", required=True, help="Chrome or Edge executable")
    parser.add_argument("--test", choices=CASES, help="Run one independent contract")
    args = parser.parse_args()
    browser = pathlib.Path(args.browser).resolve(strict=True)
    environment = dict(os.environ, CHROME_BIN=str(browser))
    launcher = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    results = MODULE / "test-results/browserProtectedStorage" / browser.stem
    results.mkdir(parents=True, exist_ok=True)
    failures = []
    for case in (args.test,) if args.test else CASES:
        # Kotlin/Karma owns and removes a new browser profile for every invocation.
        # Separate profiles isolate the entire origin (IndexedDB and localStorage).
        output = results / case
        if output.exists():
            shutil.rmtree(output)
        output.mkdir()
        command = [
            str(launcher), ":client:composeCore:wasmJsBrowserTest", "--rerun",
            "--tests", "*BrowserProtectedValueStoreTest." + case,
            "-Pserver.flavor=dev", "-Pserver.buildType=debug", "--console=plain",
        ]
        with (output / "gradle.log").open("w", encoding="utf-8") as log:
            completed = subprocess.run(command, cwd=ROOT, env=environment,
                                       stdout=log, stderr=subprocess.STDOUT, check=False)
        for source, target in ((MODULE / "test-results/wasmJsBrowserTest", "junit"),
                               (MODULE / "reports/tests/wasmJsBrowserTest", "html")):
            if source.exists():
                shutil.copytree(source, output / target)
        reports = list((output / "junit").glob("*.xml"))
        suites = [ET.parse(report).getroot() for report in reports]
        tests = sum(int(suite.get("tests", 0)) for suite in suites)
        unsuccessful = sum(int(suite.get(status, 0)) for suite in suites
                           for status in ("skipped", "failures", "errors"))
        if completed.returncode or tests != 1 or unsuccessful:
            failures.append(case)
            print("FAIL:", case, "(see", output / "gradle.log", ")", flush=True)
        else:
            print("PASS:", case, flush=True)
    if failures:
        raise SystemExit("Failed contracts: " + ", ".join(failures))


if __name__ == "__main__":
    main()
