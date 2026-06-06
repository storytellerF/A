#!/bin/sh
set -e

echo "compile-and-unit-test.sh is deprecated. Use build-and-test.sh --compile-and-unit instead."
exec ./scripts/test_scripts/build-and-test.sh --compile-and-unit "$@"
