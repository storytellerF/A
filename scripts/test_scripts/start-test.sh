#!/usr/bin/env sh
shutdown() {
    echo "shutdown supervisor gracefully..."
    supervisorctl stop all
    echo "supervisor shut down."
}

/usr/bin/supervisord -c /etc/supervisor/supervisord.conf &
trap shutdown EXIT

. /root/.cargo/env
./scripts/test_scripts/build-and-test.sh | /root/.cargo/bin/tspin

#tail -f /dev/null