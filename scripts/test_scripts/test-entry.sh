#!/usr/bin/env sh
trap "supervisorctl shutdown" EXIT

/usr/bin/supervisord -c /etc/supervisor/supervisord.conf &
. /root/.cargo/env
./scripts/test_scripts/build-and-test.sh | /root/.cargo/bin/tspin

#tail -f /dev/null