#!/usr/bin/env sh
shutdown() {
    echo "shutdown supervisor gracefully..."
    supervisorctl stop all
    echo "supervisor shut down."
}

if [ -f /usr/local/bin/install-sdk.sh ]; then
    echo "Found /usr/local/bin/install-sdk.sh, executing..."
    /usr/local/bin/install-sdk.sh
    echo "SDK setup finished."
else
    echo "/usr/local/bin/install-sdk.sh not found, skipping."
fi

/usr/bin/supervisord -c /etc/supervisor/supervisord.conf &
trap shutdown EXIT

. /root/.cargo/env
./scripts/test_scripts/build-and-test.sh "$@" | /root/.cargo/bin/tspin

#tail -f /dev/null