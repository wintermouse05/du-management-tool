#!/usr/bin/env bash

set -euo pipefail

SKIP_FRONTEND_BUILD=false
NO_NGROK=false
ENV_FILE=".env"
FRONTEND_URL="http://localhost:8088"
WEB_SOCKET_ORIGINS="https://*.ngrok-free.dev,https://*.ngrok-free.app,https://*.ngrok.app,http://localhost:8088,http://localhost:5173,http://localhost:8080"
NOTIFICATION_EMAIL_ENABLED="false"
MAIL_USERNAME=""
MAIL_APP_PASSWORD=""
MAIL_FROM=""
CHATOPS_ENABLED="false"
CHATOPS_URL=""
CHATOPS_TOKEN=""
CHATOPS_ASSISTANT_ID=""
CHATOPS_CHANNEL_ID=""
CHATOPS_CONFIG_ENCRYPTION_SECRET=""

EXPLICIT_SKIP_FRONTEND_BUILD=0
EXPLICIT_NO_NGROK=0
EXPLICIT_FRONTEND_URL=0
EXPLICIT_WEB_SOCKET_ORIGINS=0
EXPLICIT_NOTIFICATION_EMAIL_ENABLED=0
EXPLICIT_MAIL_USERNAME=0
EXPLICIT_MAIL_APP_PASSWORD=0
EXPLICIT_MAIL_FROM=0
EXPLICIT_CHATOPS_ENABLED=0
EXPLICIT_CHATOPS_URL=0
EXPLICIT_CHATOPS_TOKEN=0
EXPLICIT_CHATOPS_ASSISTANT_ID=0
EXPLICIT_CHATOPS_CHANNEL_ID=0
EXPLICIT_CHATOPS_CONFIG_ENCRYPTION_SECRET=0

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

to_bool() {
    local value
    value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
        1|true|yes|y|on) printf 'true' ;;
        0|false|no|n|off|"") printf 'false' ;;
        *) printf '%s' "$1" ;;
    esac
}

print_usage() {
    cat <<'EOF'
Usage: ./start-local-ngrok.sh [options]

Options:
  --skip-frontend-build
  --no-ngrok
  --env-file <path>
  --frontend-url <url>
  --web-socket-origins <csv>
  --notification-email-enabled <true|false>
  --mail-username <value>
  --mail-app-password <value>
  --mail-from <value>
  --chatops-enabled <true|false>
  --chatops-url <value>
  --chatops-token <value>
  --chatops-assistant-id <value>
  --chatops-channel-id <value>
  --chatops-config-encryption-secret <value>
  -h, --help
EOF
}

require_value() {
    local flag="$1"
    local value="${2:-}"
    if [[ -z "$value" ]]; then
        echo "Missing value for $flag" >&2
        exit 1
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-frontend-build)
            SKIP_FRONTEND_BUILD=true
            EXPLICIT_SKIP_FRONTEND_BUILD=1
            shift
            ;;
        --no-ngrok)
            NO_NGROK=true
            EXPLICIT_NO_NGROK=1
            shift
            ;;
        --env-file)
            require_value "$1" "${2:-}"
            ENV_FILE="$2"
            shift 2
            ;;
        --frontend-url)
            require_value "$1" "${2:-}"
            FRONTEND_URL="$2"
            EXPLICIT_FRONTEND_URL=1
            shift 2
            ;;
        --web-socket-origins)
            require_value "$1" "${2:-}"
            WEB_SOCKET_ORIGINS="$2"
            EXPLICIT_WEB_SOCKET_ORIGINS=1
            shift 2
            ;;
        --notification-email-enabled)
            require_value "$1" "${2:-}"
            NOTIFICATION_EMAIL_ENABLED="$2"
            EXPLICIT_NOTIFICATION_EMAIL_ENABLED=1
            shift 2
            ;;
        --mail-username)
            require_value "$1" "${2:-}"
            MAIL_USERNAME="$2"
            EXPLICIT_MAIL_USERNAME=1
            shift 2
            ;;
        --mail-app-password)
            require_value "$1" "${2:-}"
            MAIL_APP_PASSWORD="$2"
            EXPLICIT_MAIL_APP_PASSWORD=1
            shift 2
            ;;
        --mail-from)
            require_value "$1" "${2:-}"
            MAIL_FROM="$2"
            EXPLICIT_MAIL_FROM=1
            shift 2
            ;;
        --chatops-enabled)
            require_value "$1" "${2:-}"
            CHATOPS_ENABLED="$2"
            EXPLICIT_CHATOPS_ENABLED=1
            shift 2
            ;;
        --chatops-url)
            require_value "$1" "${2:-}"
            CHATOPS_URL="$2"
            EXPLICIT_CHATOPS_URL=1
            shift 2
            ;;
        --chatops-token)
            require_value "$1" "${2:-}"
            CHATOPS_TOKEN="$2"
            EXPLICIT_CHATOPS_TOKEN=1
            shift 2
            ;;
        --chatops-assistant-id)
            require_value "$1" "${2:-}"
            CHATOPS_ASSISTANT_ID="$2"
            EXPLICIT_CHATOPS_ASSISTANT_ID=1
            shift 2
            ;;
        --chatops-channel-id)
            require_value "$1" "${2:-}"
            CHATOPS_CHANNEL_ID="$2"
            EXPLICIT_CHATOPS_CHANNEL_ID=1
            shift 2
            ;;
        --chatops-config-encryption-secret)
            require_value "$1" "${2:-}"
            CHATOPS_CONFIG_ENCRYPTION_SECRET="$2"
            EXPLICIT_CHATOPS_CONFIG_ENCRYPTION_SECRET=1
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            print_usage >&2
            exit 1
            ;;
    esac
done

declare -A DOTENV_VALUES=()

read_dotenv_file() {
    local file_path="$1"
    [[ -f "$file_path" ]] || return 0

    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        local trimmed_line
        trimmed_line="$(trim "$line")"

        [[ -z "$trimmed_line" ]] && continue
        [[ "${trimmed_line:0:1}" == "#" ]] && continue
        [[ "$trimmed_line" != *=* ]] && continue

        local key value
        key="$(trim "${trimmed_line%%=*}")"
        value="$(trim "${trimmed_line#*=}")"

        if [[ -n "$value" ]]; then
            if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
                value="${value:1:-1}"
            elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
                value="${value:1:-1}"
            fi
        fi

        DOTENV_VALUES["$key"]="$value"
    done < "$file_path"
}

dotenv_try_get() {
    local result_var_name="$1"
    shift
    local key
    for key in "$@"; do
        if [[ "${DOTENV_VALUES[$key]+x}" == "x" ]]; then
            printf -v "$result_var_name" '%s' "${DOTENV_VALUES[$key]}"
            return 0
        fi
    done
    return 1
}

apply_dotenv_value() {
    local target_var_name="$1"
    local explicit_flag="$2"
    shift 2

    [[ "$explicit_flag" -eq 1 ]] && return 0

    local value
    if dotenv_try_get value "$@"; then
        printf -v "$target_var_name" '%s' "$value"
    fi
}

write_step() {
    printf '\n==> %s\n' "$1"
}

require_command() {
    local command_name="$1"
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Missing required command: $command_name" >&2
        exit 1
    fi
}

stop_port_owner() {
    local port="$1"
    mapfile -t pids < <(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)
    local process_id
    for process_id in "${pids[@]:-}"; do
        if kill -0 "$process_id" 2>/dev/null; then
            kill -9 "$process_id" 2>/dev/null || true
        fi
    done
}

wait_for_port() {
    local port="$1"
    local timeout_seconds="${2:-60}"
    local deadline=$((SECONDS + timeout_seconds))

    while (( SECONDS < deadline )); do
        if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done

    return 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_dir="$repo_root/du-management-backend"
frontend_dir="$repo_root/du-management-frontend"

nginx_port=8088
backend_port=8080

log_stamp="$(date +%Y%m%d-%H%M%S)"
backend_log="$repo_root/backend-ngrok-$log_stamp.log"
ngrok_out_log="$repo_root/ngrok.out-$log_stamp.log"
ngrok_err_log="$repo_root/ngrok.err-$log_stamp.log"
linux_nginx_config="$repo_root/nginx/du-management.local.linux.generated.conf"
linux_nginx_pid="$repo_root/.tools/nginx-1.30.0/logs/nginx-linux.pid"
linux_nginx_logs_dir="$repo_root/nginx/logs"
linux_nginx_temp_dir="$repo_root/nginx/temp"

if [[ "$ENV_FILE" = /* ]]; then
    env_file_path="$ENV_FILE"
else
    env_file_path="$repo_root/$ENV_FILE"
fi

read_dotenv_file "$env_file_path"

apply_dotenv_value FRONTEND_URL "$EXPLICIT_FRONTEND_URL" FRONTEND_URL APP_FRONTEND_URL
apply_dotenv_value WEB_SOCKET_ORIGINS "$EXPLICIT_WEB_SOCKET_ORIGINS" WEB_SOCKET_ORIGINS APP_WEBSOCKET_ALLOWED_ORIGINS
apply_dotenv_value NOTIFICATION_EMAIL_ENABLED "$EXPLICIT_NOTIFICATION_EMAIL_ENABLED" NOTIFICATION_EMAIL_ENABLED
apply_dotenv_value MAIL_USERNAME "$EXPLICIT_MAIL_USERNAME" MAIL_USERNAME
apply_dotenv_value MAIL_APP_PASSWORD "$EXPLICIT_MAIL_APP_PASSWORD" MAIL_APP_PASSWORD
apply_dotenv_value MAIL_FROM "$EXPLICIT_MAIL_FROM" MAIL_FROM
apply_dotenv_value CHATOPS_ENABLED "$EXPLICIT_CHATOPS_ENABLED" CHATOPS_ENABLED
apply_dotenv_value CHATOPS_URL "$EXPLICIT_CHATOPS_URL" CHATOPS_URL
apply_dotenv_value CHATOPS_TOKEN "$EXPLICIT_CHATOPS_TOKEN" CHATOPS_TOKEN
apply_dotenv_value CHATOPS_ASSISTANT_ID "$EXPLICIT_CHATOPS_ASSISTANT_ID" CHATOPS_ASSISTANT_ID
apply_dotenv_value CHATOPS_CHANNEL_ID "$EXPLICIT_CHATOPS_CHANNEL_ID" CHATOPS_CHANNEL_ID
apply_dotenv_value CHATOPS_CONFIG_ENCRYPTION_SECRET "$EXPLICIT_CHATOPS_CONFIG_ENCRYPTION_SECRET" CHATOPS_CONFIG_ENCRYPTION_SECRET

if [[ "$EXPLICIT_SKIP_FRONTEND_BUILD" -eq 0 ]]; then
    if dotenv_try_get dotenv_skip_build SKIP_FRONTEND_BUILD; then
        SKIP_FRONTEND_BUILD="$(to_bool "$dotenv_skip_build")"
    fi
fi

if [[ "$EXPLICIT_NO_NGROK" -eq 0 ]]; then
    if dotenv_try_get dotenv_no_ngrok NO_NGROK; then
        NO_NGROK="$(to_bool "$dotenv_no_ngrok")"
    fi
fi

NOTIFICATION_EMAIL_ENABLED="$(to_bool "$NOTIFICATION_EMAIL_ENABLED")"
CHATOPS_ENABLED="$(to_bool "$CHATOPS_ENABLED")"

write_step "Checking required tools"
require_command java
require_command ngrok
require_command npm
require_command nginx
require_command lsof
require_command curl

if [[ ! -d "$backend_dir" ]]; then
    echo "Backend folder not found: $backend_dir" >&2
    exit 1
fi

if [[ ! -d "$frontend_dir" ]]; then
    echo "Frontend folder not found: $frontend_dir" >&2
    exit 1
fi

if [[ "$SKIP_FRONTEND_BUILD" != "true" ]]; then
    write_step "Building frontend production bundle"
    (
        cd "$frontend_dir"
        npm run build
    )
fi

write_step "Stopping previous local listeners if needed"
pkill -x ngrok >/dev/null 2>&1 || true
pkill -x nginx >/dev/null 2>&1 || true
stop_port_owner "$backend_port"
stop_port_owner "$nginx_port"
rm -f "$linux_nginx_pid"

write_step "Starting backend on port $backend_port"
(
    cd "$backend_dir"
    env \
        APP_WEBSOCKET_ALLOWED_ORIGINS="$WEB_SOCKET_ORIGINS" \
        APP_FRONTEND_URL="$FRONTEND_URL" \
        NOTIFICATION_EMAIL_ENABLED="$NOTIFICATION_EMAIL_ENABLED" \
        MAIL_USERNAME="$MAIL_USERNAME" \
        MAIL_APP_PASSWORD="$MAIL_APP_PASSWORD" \
        MAIL_FROM="$MAIL_FROM" \
        CHATOPS_ENABLED="$CHATOPS_ENABLED" \
        CHATOPS_URL="$CHATOPS_URL" \
        CHATOPS_TOKEN="$CHATOPS_TOKEN" \
        CHATOPS_ASSISTANT_ID="$CHATOPS_ASSISTANT_ID" \
        CHATOPS_CHANNEL_ID="$CHATOPS_CHANNEL_ID" \
        CHATOPS_CONFIG_ENCRYPTION_SECRET="$CHATOPS_CONFIG_ENCRYPTION_SECRET" \
        ./mvnw spring-boot:run > "$backend_log" 2>&1 &
    echo "$!" > "$repo_root/.backend-local.pid"
)

if ! wait_for_port "$backend_port" 180; then
    echo
    echo "Backend log tail:"
    if [[ -f "$backend_log" ]]; then
        tail -n 80 "$backend_log"
    fi
    echo "Backend did not start on port $backend_port in time." >&2
    exit 1
fi

frontend_dist="$frontend_dir/dist"
if [[ ! -d "$frontend_dist" ]]; then
    echo "Frontend dist folder not found: $frontend_dist" >&2
    exit 1
fi

mime_types_path="/etc/nginx/mime.types"
if [[ ! -f "$mime_types_path" ]]; then
    mime_types_path="$repo_root/.tools/nginx-1.30.0/conf/mime.types"
fi

if [[ ! -f "$mime_types_path" ]]; then
    echo "mime.types not found. Checked /etc/nginx/mime.types and $repo_root/.tools/nginx-1.30.0/conf/mime.types" >&2
    exit 1
fi

write_step "Preparing linux nginx config"
mkdir -p \
    "$linux_nginx_logs_dir" \
    "$linux_nginx_temp_dir/client_body" \
    "$linux_nginx_temp_dir/proxy" \
    "$linux_nginx_temp_dir/fastcgi" \
    "$linux_nginx_temp_dir/uwsgi" \
    "$linux_nginx_temp_dir/scgi"

cat > "$linux_nginx_config" <<EOF
worker_processes  1;
error_log "$linux_nginx_logs_dir/error.log" warn;
pid "$linux_nginx_pid";

events {
    worker_connections  1024;
}

http {
    include       "$mime_types_path";
    access_log    "$linux_nginx_logs_dir/access.log" combined;
    default_type  application/octet-stream;
    client_max_body_size 10m;
    client_body_temp_path "$linux_nginx_temp_dir/client_body";
    proxy_temp_path "$linux_nginx_temp_dir/proxy";
    fastcgi_temp_path "$linux_nginx_temp_dir/fastcgi";
    uwsgi_temp_path "$linux_nginx_temp_dir/uwsgi";
    scgi_temp_path "$linux_nginx_temp_dir/scgi";

    sendfile        on;
    keepalive_timeout  65;

    server {
        listen       $nginx_port;
        server_name  localhost;

        root   "$frontend_dist";
        index  index.html;

        location / {
            try_files \$uri \$uri/ /index.html;
        }

        location /api/ {
            proxy_pass http://127.0.0.1:$backend_port;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
            proxy_read_timeout 180;
            proxy_send_timeout 180;
        }

        location /ws {
            proxy_pass http://127.0.0.1:$backend_port;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host \$host;
            proxy_read_timeout 3600;
        }

        location /api-docs {
            proxy_pass http://127.0.0.1:$backend_port;
        }

        location /swagger-ui {
            proxy_pass http://127.0.0.1:$backend_port;
        }

        location /swagger-ui.html {
            proxy_pass http://127.0.0.1:$backend_port;
        }
    }
}
EOF

write_step "Validating nginx config"
nginx -t -c "$linux_nginx_config"

write_step "Starting nginx on port $nginx_port"
nginx -c "$linux_nginx_config"

if ! wait_for_port "$nginx_port" 30; then
    echo "nginx did not start on port $nginx_port in time." >&2
    exit 1
fi

local_home_code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$nginx_port/" || true)"
local_docs_code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$nginx_port/api-docs" || true)"

echo
echo "Frontend local: http://127.0.0.1:$nginx_port"
echo "Local status codes: / -> $local_home_code, /api-docs -> $local_docs_code"
echo "Mail enabled: $NOTIFICATION_EMAIL_ENABLED | ChatOps enabled: $CHATOPS_ENABLED"
if [[ -f "$env_file_path" ]]; then
    echo "Loaded .env file: $env_file_path"
fi

if [[ "$NO_NGROK" == "true" ]]; then
    echo
    echo "ngrok was skipped. Run this when you want a public URL:"
    echo "ngrok http $nginx_port"
    exit 0
fi

write_step "Starting ngrok"
ngrok http "$nginx_port" --log=stdout > "$ngrok_out_log" 2> "$ngrok_err_log" &
echo "$!" > "$repo_root/.ngrok-local.pid"

public_url=""
deadline=$((SECONDS + 30))
while (( SECONDS < deadline )) && [[ -z "$public_url" ]]; do
    sleep 2
    tunnel_response="$(curl -s "http://127.0.0.1:4040/api/tunnels" || true)"
    public_url="$(printf '%s' "$tunnel_response" | grep -oE 'https://[^"]+' | head -n 1 || true)"
done

echo
if [[ -n "$public_url" ]]; then
    echo "Public ngrok URL: $public_url"
else
    echo "ngrok started, but public URL was not detected automatically."
    echo "Check http://127.0.0.1:4040/api/tunnels or $ngrok_out_log"
fi

echo "Inspector: http://127.0.0.1:4040"
echo "Backend log: $backend_log"
echo "ngrok logs: $ngrok_out_log and $ngrok_err_log"
