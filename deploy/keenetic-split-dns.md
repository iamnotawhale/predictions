# Keenetic: split-DNS для Mini App (домашний Wi‑Fi)

## Симптом

- `https://predicts.duckdns.org/miniapp/` не открывается с телефона/ПК в **домашней Wi‑Fi**
- `https://onchess.online` при этом открывается
- С **мобильного интернета** (LTE) miniapp обычно работает — порт 443 с WAN открыт

## Причина

Keenetic **не делает hairpin NAT** для проброшенных портов из LAN на свой же внешний IP.
`predicts.duckdns.org` резолвится в публичный IP → с домашней сети `:443` даёт *connection refused*.

Для `onchess.online` уже добавлена локальная DNS-запись → `192.168.1.38` (Odyssey).
Нужно то же для `predicts.duckdns.org`.

## Исправление (один раз)

Веб-панель Keenetic → **Общие настройки** → **Командная строка** (или SSH/Telnet на `192.168.1.1`):

```
(config)> ip host predicts.duckdns.org 192.168.1.38
(config)> system configuration save
```

Проверка на роутере:

```
(config)> show dns-proxy
```

Должна быть запись `predicts.duckdns.org` → `192.168.1.38`.

## Проверка с клиента

```bash
./deploy/check-miniapp-dns.sh
```

Ожидается:

- `predicts.duckdns.org` → `192.168.1.38` (из домашней сети)
- `curl https://predicts.duckdns.org/miniapp/` → HTTP 200

## Не нужно

- Отдельный nginx — Caddy уже reverse proxy для predictions и onchess
- Порт 8443 — с интернета открыт стандартный 443

## Проброс портов на роутере

| WAN | LAN (Odyssey) |
|-----|----------------|
| TCP 443 | 192.168.1.38:443 |
| TCP 80  | 192.168.1.38:80  |
| TCP 2222 | 192.168.1.38:22 |

UPnP на Odyssey (`predictions-net-refresh`) дублирует 443/2222, но **split-DNS всё равно нужен** для домашней Wi‑Fi.
