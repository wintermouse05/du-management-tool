#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
linux_nginx_pid="$repo_root/.tools/nginx-1.30.0/logs/nginx-linux.pid"

write_step() {
    printf '\n==> %s\n' "$1"
}

stop_process_by_name_safe() {
    local process_name="$1"
    mapfile -t pids < <(pgrep -x "$process_name" || true)

    if [[ "${#pids[@]}" -eq 0 ]]; then
        echo "No process found for $process_name"
        return
    fi

    local process_id
    for process_id in "${pids[@]}"; do
        if kill -9 "$process_id" 2>/dev/null; then
            echo "Stopped $process_name (PID $process_id)"
        else
            echo "Could not stop $process_name (PID $process_id)"
        fi
    done
}

stop_port_owner() {
    local port="$1"
    mapfile -t process_ids < <(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)

    if [[ "${#process_ids[@]}" -eq 0 ]]; then
        echo "No listener found on port $port"
        return
    fi

    local process_id
    for process_id in "${process_ids[@]}"; do
        if kill -9 "$process_id" 2>/dev/null; then
            echo "Stopped process on port $port (PID $process_id)"
        else
            echo "Could not stop process on port $port (PID $process_id)"
        fi
    done
}

write_step "Stopping ngrok"
stop_process_by_name_safe "ngrok"

write_step "Stopping nginx"
if [[ -f "$linux_nginx_pid" ]]; then
    nginx_pid="$(cat "$linux_nginx_pid" 2>/dev/null || true)"
    if [[ -n "${nginx_pid:-}" ]] && kill -0 "$nginx_pid" 2>/dev/null; then
        kill -9 "$nginx_pid" 2>/dev/null || true
        echo "Stopped nginx using PID file (PID $nginx_pid)"
    fi
fi
stop_process_by_name_safe "nginx"

write_step "Stopping backend on port 8080"
stop_port_owner "8080"

rm -f "$repo_root/.backend-local.pid" "$repo_root/.ngrok-local.pid" "$repo_root/nginx/du-management.local.linux.generated.conf" "$linux_nginx_pid"
