# This is a private project. All rights reserved.
"""Compatibility entry for the shared verification process container."""
from pathlib import Path
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts/tool_scripts"))
from process_container import main

if __name__ == "__main__":
    sys.exit(main())
