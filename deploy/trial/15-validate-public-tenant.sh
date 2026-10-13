#!/bin/sh
set -eu
case "${RD_PUBLIC_TENANT_ID:-}" in
    ''|0*|*[!0-9]*)
        echo >&2 'RD_PUBLIC_TENANT_ID must be the positive organization ID for this deployment'
        exit 1
        ;;
esac
if [ "${#RD_PUBLIC_TENANT_ID}" -gt 18 ]; then
    echo >&2 'RD_PUBLIC_TENANT_ID exceeds the supported organization ID range'
    exit 1
fi
