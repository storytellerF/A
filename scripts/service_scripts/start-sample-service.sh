#!/bin/bash
set -euo pipefail

./scripts/service_scripts/compose-service.sh sample false 'up -d --build --remove-orphans'
