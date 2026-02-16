#!/bin/bash
# Custom entrypoint script for executing various maintenance tasks
# Task execution status is tracked in ~/.a/cmd

set -e

./bin/action-after-create.sh

./bin/entrypoint.sh