#!/bin/bash
# Общие функции для HTTPS-конфигурации (local + prod).

https_strip_trailing_slash() {
    local url="$1"
    while [[ "$url" == */ ]]; do
        url="${url%/}"
    done
    printf '%s' "$url"
}

https_derive_webapp_url() {
    local base
    base="$(https_strip_trailing_slash "$1")"
    printf '%s/miniapp/' "$base"
}

# Записать PUBLIC_HTTPS_URL и BOT_WEBAPP_URL в env-файл
https_write_env_file() {
    local file="$1"
    local public_url="$2"
    public_url="$(https_strip_trailing_slash "$public_url")"
    local webapp_url
    webapp_url="$(https_derive_webapp_url "$public_url")"

    mkdir -p "$(dirname "$file")"
    touch "$file"

    if grep -q '^PUBLIC_HTTPS_URL=' "$file" 2>/dev/null; then
        sed -i "s|^PUBLIC_HTTPS_URL=.*|PUBLIC_HTTPS_URL=$public_url|" "$file"
    else
        echo "PUBLIC_HTTPS_URL=$public_url" >> "$file"
    fi

    if grep -q '^BOT_WEBAPP_URL=' "$file" 2>/dev/null; then
        sed -i "s|^BOT_WEBAPP_URL=.*|BOT_WEBAPP_URL=$webapp_url|" "$file"
    else
        echo "BOT_WEBAPP_URL=$webapp_url" >> "$file"
    fi

    chmod 600 "$file" 2>/dev/null || true
    echo "PUBLIC_HTTPS_URL=$public_url"
    echo "BOT_WEBAPP_URL=$webapp_url"
}

https_from_domain() {
    local domain="$1"
    domain="${domain#https://}"
    domain="${domain#http://}"
    domain="${domain%%/*}"
    printf 'https://%s' "$domain"
}
