# PROJECT CONTEXT: predictions

## Стартовый промт для нового диалога
```text
Ты работаешь с проектом predictions (Java 21, Spring Boot, PostgreSQL, Telegram Bot + Mini App).
Перед любыми изменениями прочитай файл PROJECT_CONTEXT.md целиком и опирайся на него как на источник контекста.

Что где находится:
- backend: src/main/java/zhigalin/predictions
- miniapp frontend: src/main/resources/static/miniapp
- SQL схема: src/main/resources/tablesInit.sql
- скрипты локального запуска: scripts/
- деплой и прод-окружение: deploy/
- документация/примеры: docs/

Подключения (секреты только в gitignored env, не коммитить):
- см. раздел «Env-файлы» и таблицу `${SPRING_DATASOURCE_URL:}` / `${BOT_TOKEN:}` / …
- LOCAL: deploy/local.env (+ application-local.yml)
- PROD: deploy/predicts.env (+ application-prod.yml)
- деплой/DynDNS: deploy/deploy.env, deploy/duckdns.env

Механизмы запуска:
- локально bot+miniapp: ./scripts/run-local.sh
- локально только бот: ./scripts/run-bot-only.sh
- сборка jar: mvn package -DskipTests
- деплой на прод: ./deploy/upload-jar.sh (читает deploy/deploy.env)
- прод: systemd unit predicts (+ caddy / net-refresh — см. deploy/)

Обязательное правило:
- если в проект добавлен новый код/фича/скрипт/конфиг/эндпоинт/процесс запуска, ОБЯЗАТЕЛЬНО обнови PROJECT_CONTEXT.md в этом же изменении (что добавлено, где лежит, как запускать/использовать).
- версия miniapp в UI — git short hash из maven-сборки (см. раздел «Версия miniapp»); после deploy проверяй подпись `ver. …` на главном экране.
- В PROJECT_CONTEXT.md НЕ писать: IP, домены, токены, пароли, SSH-порты/алиасы, пути с домашними логинами, данные роутера.
```

## Кратко о проекте
- `predictions` — сервис прогнозов матчей АПЛ.
- Интерфейсы: Telegram-бот + Telegram Mini App.
- Данные хранятся в PostgreSQL, базовые таблицы описаны в `src/main/resources/tablesInit.sql`.
- Стек: Java 21, Spring Boot 3.3, JDBC (без JPA), Unirest, Telegram Bots 6.9, vanilla JS/CSS/HTML в miniapp.

## Структура проекта
- `src/main/java/zhigalin/predictions/telegram` — команды и обработка апдейтов Telegram-бота.
- `src/main/java/zhigalin/predictions/miniapp` — REST API и auth для Telegram WebApp (`X-Telegram-Init-Data`).
- `src/main/java/zhigalin/predictions/service` — бизнес-логика (матчи, прогнозы, уведомления, синхронизация).
- `src/main/java/zhigalin/predictions/service/odds` — **`OddsService`**: коэффициенты ESPN scoreboard, TTL 60с, сохранение в БД.
- `src/main/java/zhigalin/predictions/service/api` — `ApiClient` (Telegram, API-Football, ESPN summary).
- `src/main/java/zhigalin/predictions/repository` — JDBC/DAO слой.
- `src/main/java/zhigalin/predictions/config` — конфигурация, в т.ч. **`MatchOddsSchemaMigration`** (миграция колонок odds при старте).
- `src/main/resources/static/miniapp` — клиентская часть Mini App (`index.html`, `js/app.js`, `css/app.css`, `js/live-event-ru.js`).
- `scripts` — локальный запуск, dev HTTPS, утилиты.
- `deploy` — конфиги и сценарии деплоя/прод запуска (секреты — только `*.env`, в git только `*.example`).
- `docs` — артефакты документации (`live-pitch-preview.svg`).

## Подключения и профили
- `application.yml` — базовый конфиг (placeholders `${ENV_VAR:default}`).
- `application-local.yml` — профиль `local` (`spring.profiles.active=local`).
- `application-prod.yml` — профиль `prod` (`spring.profiles.active=prod`).
- `src/main/resources/static/miniapp/live_event_ru_translation.json` — RU-перевод live-комментариев ESPN; грузится через `js/live-event-ru.js`.

### Env-файлы (значения НЕ в git; в репо только `*.example`)

| Файл (gitignore) | Пример | Кто читает | Назначение |
|------------------|--------|------------|------------|
| `deploy/local.env` | `deploy/local.env.example` | `scripts/run-local.sh`, `prepare-idea-debug.sh` (`source`) | локальный/dev запуск |
| `deploy/local-https.env` | генерируется скриптами | `run-local.sh` / IDEA | временный cloudflared URL |
| `deploy/predicts.env` | `deploy/predicts.env.example` | systemd `EnvironmentFile=` на проде | прод-секреты бота/БД/Mini App |
| `deploy/deploy.env` | `deploy/deploy.env.example` | `deploy/upload-jar.sh` | куда деплоить (SSH host, APP_DIR) |
| `deploy/duckdns.env` | `deploy/duckdns.env.example` | `predictions-net-refresh` на проде | DynDNS token + обновление IP/UPnP |

Создать: `cp deploy/<name>.env.example deploy/<name>.env` и заполнить. **Не коммитить** `*.env` (кроме `*.example`).

### Маппинг env → Spring / код (как в yml)

Формат как в `application.yml`: `${ИМЯ_ПЕРЕМЕННОЙ:}` или `${ИМЯ:default}`.

| Env-ключ | Куда попадает | Где задавать |
|----------|---------------|--------------|
| `${SPRING_DATASOURCE_URL:}` | `spring.datasource.url` | `local.env` / `predicts.env` (prod может брать url из `application-prod.yml`) |
| `${SPRING_DATASOURCE_USERNAME:admin}` | `spring.datasource.username` | обычно default; при необходимости env |
| `${SPRING_DATASOURCE_PASSWORD:}` | `spring.datasource.password` | `local.env` / `predicts.env` |
| `${BOT_USERNAME:}` | `bot.username` | `local.env` / `predicts.env` |
| `${BOT_TOKEN:}` | `bot.token` | `local.env` / `predicts.env` |
| `${BOT_CHAT_ID:}` | `bot.chatId` (чат/группа бота) | `local.env` / `predicts.env` |
| `${BOT_WEBAPP_URL:}` | `bot.webAppUrl` (кнопка Mini App) | `local.env` / `predicts.env` / `local-https.env` |
| `${ADMIN_CHAT_ID:}` | `chatId` (panic + admin-команды) | `local.env` / `predicts.env` |
| `${API_FOOTBALL_TOKEN:}` | `api.football.token` | `local.env` / `predicts.env` |
| `${MINIAPP_DEV_TELEGRAM_ID:}` | `miniapp.dev-telegram-id` (только local) | `local.env` |
| `${SEASON:2026}` | `season` | опционально env |
| `${SPRING_PROFILES_ACTIVE:}` | профиль Spring | `predicts.env` → обычно `prod` |
| `PUBLIC_HTTPS_URL` / `PUBLIC_DOMAIN` / `HTTPS_PORT` / `APP_PORT` | Caddy / menu URL (не Spring напрямую) | `predicts.env` |
| `DUCKDNS_DOMAIN` / `DUCKDNS_TOKEN` / `DUCKDNS_FULL` / `PUBLIC_IP` | скрипт обновления DynDNS | `duckdns.env` |
| `DEPLOY_SERVER` / `DEPLOY_APP_DIR` / `DEPLOY_REMOTE_SUDO` | `upload-jar.sh` | `deploy.env` |

Связка: значения из env подхватываются Spring Boot как environment variables (systemd `EnvironmentFile`, или `source deploy/local.env` перед `java`/`mvn`).

## База данных
- URL/user/password — через `${SPRING_DATASOURCE_*}` (см. таблицу выше).
- Имена БД по умолчанию: local `predicts_local`, prod `predicts_prod` (см. `application-*.yml`).
- Реальные пароли только в gitignored `deploy/*.env`.

**Таблица `match` (важные поля сверх базовой схемы):**
- `espn_id` — ESPN event id (заполняется уже на стадии `pre`).
- `live_score_message_id` — id Telegram-сообщения live-счёта (переживает рестарт).
- `odd_home`, `odd_draw`, `odd_away` — коэффициенты 1/X/2 (`NUMERIC(6,2)`), заполняются `OddsService`, читаются в miniapp и разборе тура.
- Миграция колонок odds: `MatchOddsSchemaMigration` + `ALTER` в `tablesInit.sql`; `OddsService` зависит от неё через `@DependsOn`.

**Дедуп уведомлений:**
- `notification_weekly_results_sent` (по `week_id`)
- `notification_reminder_sent` (по `user_id + match_public_id + reminder_minutes`)

## Прод-окружение (без секретов)
- Прод крутится на домашнем сервере (не VPS): Spring Boot jar + PostgreSQL + Caddy (HTTPS на нестандартном порту).
- Публичный URL Mini App и SSH/доступ — только в локальных env / SSH config оператора, не в репозитории.
- Typical units: `predicts`, `caddy`, `predictions-net-refresh.timer` (обновление DynDNS + UPnP пробросов), опционально `disable-wifi`.
- Сертификат: Let's Encrypt через DNS-01 (acme.sh + DuckDNS); файлы сертификатов на сервере вне git.
- С домашней Wi‑Fi сети нужен split-DNS / hairpin на роутере (локальная A-запись публичного hostname → LAN IP сервера), иначе Mini App с телефона в той же Wi‑Fi может не открыться.
- Legacy VPS больше не используется для прода (туннель/Caddy на VPS выключены).

## Запуск и деплой
- Локально (бот + miniapp + dev HTTPS): `./scripts/run-local.sh`
- `scripts/run-local.sh` подхватывает `deploy/local.env` и `deploy/local-https.env` как переменные окружения (через `source`), а не как Spring config-файлы.
- `scripts/run-local.sh` автоматически использует `~/.local/bin/cloudflared`, если `cloudflared` не найден в системном `PATH`.
- Для one-click Debug в IntelliJ IDEA добавлены shared run-конфиги в `.run/`:
  - `01 Prepare Local HTTPS` → запускает `scripts/prepare-idea-debug.sh` (обновляет `deploy/local-https.env` и `deploy/local-https.properties` через cloudflared и принудительно обновляет Telegram menu button на актуальный `BOT_WEBAPP_URL`);
  - `02 Predictions Local Debug` → Spring Boot Debug (profile `local`) с pre-launch шагом `01 Prepare Local HTTPS`, JDBC через SSH-туннель (см. `scripts/ssh-tunnel-prod-db.sh`) и автоподхватом `optional:file:./deploy/local-https.properties`.
- Локально только бот: `./scripts/run-bot-only.sh`
- Ручная сборка: `mvn package -DskipTests`
- Деплой: `./deploy/upload-jar.sh` (сервер/путь/sudo — из `deploy/deploy.env`)
- CI/CD (GitHub Actions): push в `master` (или manual `workflow_dispatch`) → build jar в CI → scp + `systemctl restart predicts` на прод.
  - Workflow: `.github/workflows/deploy-prod.yml`
  - Срабатывает только при изменениях в `src/**`, `pom.xml`, `application*.yml`, `deploy/predicts.service` (не на каждый README).
  - Секреты репозитория (Settings → Secrets → Actions):
    - `PROD_SSH_PRIVATE_KEY` — приватный ключ deploy (пара на сервере в `authorized_keys`)
    - `PROD_HOST` — hostname/IP для SSH с интернета
    - `PROD_SSH_PORT` — порт SSH (WAN)
    - `PROD_USER` — SSH user
    - `PROD_APP_DIR` — абсолютный путь приложения на сервере
  - Ключ CI локально (не в git): `deploy/ci/` (gitignore)

### Скрипты (`scripts/`)
| Скрипт | Назначение |
|--------|------------|
| `run-local.sh` | Локальный jar + cloudflared HTTPS для Telegram Mini App |
| `run-bot-only.sh` | Только бот |
| `dev-https.sh`, `https-env.sh` | Настройка HTTPS-туннеля |
| `prepare-idea-debug.sh` | IDEA: cloudflared + обновление menu button |
| `ssh-tunnel-prod-db.sh` | SSH-туннель к удалённой БД для локального debug |
| `verify-timezone.sh` | Проверка TZ |
| `poll-lineups.sh` | Опрос API-Football на доступность составов |

## Надёжность и бот
- `PanicSender`: дедуп одинаковых паник на 10 минут + root cause в тексте.
- `DataInitService`: адаптивный sync (30с при live/ближайших матчах, иначе 120с); при голе в live шлёт `sendLiveScoreUpdate` в Telegram-чат (антиспам 60с/матч) с автором гола и ассистом (если есть) из ESPN `summary.commentary`; при переходе матча в `post/ft` очищает кэш составов через `ApiClient.evictLineups(publicId)`.
- `DataInitService` нормализует live-статус из ESPN scoreboard: halftime определяется по `status.type.detail/shortDetail/description` и сохраняется как `ht` (а не как `45'+...`), завершение — как `ft`.
- `DataInitService` сохраняет `espn_id` в `match` уже на стадии `pre` (не только `in`), чтобы можно было заранее использовать ESPN summary по конкретному событию.
- live-обновления счёта в Telegram редактируют одно сообщение на матч: ключ состояния строится с приоритетом `espnId` (fallback: `publicId`/пара команд), чтобы избежать дублей при разных источниках id.
- `message_id` live-сообщения хранится в БД (`match.live_score_message_id`), поэтому после рестарта приложения обновления продолжают редактировать старое сообщение, а не создавать новое.
- дедуп отправки итогов тура и remind-уведомлений вынесен в БД: `notification_weekly_results_sent` (по `week_id`) и `notification_reminder_sent` (по `user_id + match_public_id + reminder_minutes`), чтобы после рестарта не было дублей.
- Напоминания без прогноза: за 60/40/20 минут до kickoff.
- `ImageRenderer`: семафор на 1 параллельный рендер (снижает пики RAM).
- Сводка тура: картинка + текстовый зачёт + график; защита от повторной отправки на тот же `weekId`.
- `/start` и меню: кнопка «Открыть Mini App» первой.
- `MiniAppMenuConfigurer` всегда инициализируется на старте; URL берется из `bot.webAppUrl` (с пустым default), чтобы системная кнопка меню Telegram гарантированно обновлялась после деплоя.

## Mini App API (`MiniAppController`, base `/api/miniapp`)
Все эндпоинты требуют `X-Telegram-Init-Data` (в local-профиле — `miniapp.dev-mode` + `dev-telegram-id`).

| Method | Path | Назначение |
|--------|------|------------|
| GET | `/profile` | Профиль, сезон, тур |
| GET | `/weeks` | Список туров |
| GET | `/weeks/{weekId}/matches` | Матчи тура |
| GET | `/weeks/{weekId}/my-predictions` | Прогнозы пользователя |
| GET | `/weeks/{weekId}/review` | **Разбор тура** |
| GET | `/match/{homeCode}/{awayCode}` | Матч + odds + canPredict |
| GET | `/match/.../insights` | Форма команд + новости Sports.ru |
| GET | `/match/.../live-details` | Live: составы, события, stats, цвета |
| GET | `/leaderboard?weekId=` | Общий / туровой зачёт (+ live provisional) |
| GET | `/standings` | Таблица АПЛ |
| GET | `/team/{teamCode}/matches` | Последние/ближайшие матчи команды |
| GET | `/h2h/{homeCode}/{awayCode}` | Личные встречи |
| GET | `/today` | Матчи сегодня |
| GET | `/chart` | Данные графика очков |
| POST/DELETE | `/predictions` | Сохранить / удалить прогноз |
| POST | `/client-log` | Клиентские логи на сервер |

## Mini App: экраны и UX
**4 экрана (нижняя навигация):**
- **Главная** (`screen-stats`): live-карточка, зачёт (Общий / Текущий тур), график очков, таблица АПЛ, версия miniapp.
- **Сегодня** (`screen-today`): матчи дня, счёт/старт, бейджи прогнозов.
- **Прогноз** (`screen-predict`): выбор тура → список матчей → модалка прогноза.
- **Мои** (`screen-my`): прогнозы тура + **Разбор тура**.

**Модалки:**
- `#score-modal` — прогноз, odds 1/X/2, H2H, форма, новости Sports.ru.
- `#live-modal` — только live: счёт, составы, мини-поле, лента событий (без odds/H2H/новостей).
- `#team-modal`, `#h2h-modal`, `#player-modal` — карточка игрока по тапу на расстановке.

**Файлы:** `static/miniapp/js/app.js`, `index.html`, `css/app.css`.

### Общее поведение
- «Сегодня»: текущий счёт / время старта, таймер до `kickoff+5м`, приоритет матчей без прогноза.
- **Polling:** 10с при live/pre-start на «Сегодня» и в live-модалке; 60с в idle; кэш leaderboard/chart/standings ~45с.
- overlay-уведомления о голах; offline-баннер при ошибках сети/API.
- `Cache-Control: no-store, must-revalidate` для `/miniapp/**` (фикс залипания WebView).
- header показывает сезон/тур (`profile.weekLabel`).
- `Crowd Meter` удален из UI; backend-эндпоинт остаётся.
- `Live Points Race` удалён; live-динамика встроена в зачёт (`provisionalPoints/liveDelta/liveActive`).
- live-подсчёт очков в leaderboard учитывает `-1` и пользователей без прогноза на live/finished матчах.
- график очков: целочисленная сетка Y.
- Backend `canPredict`: до `kickoff + 5 минут`; закрытые статусы `ft/aet/pen/canc/abd/awrd/wo` — нельзя.

### Версия miniapp
- В `index.html`: `<div class="miniapp-version">ver. @git.commit.id.abbrev@</div>`.
- Maven `git-commit-id-maven-plugin` подставляет **git short hash** (7 символов) при `mvn package` в HTML и `?v=` для CSS/JS.
- После deploy проверяй подпись `ver. …` на главном экране — она должна совпадать с коммитом сборки.

### Коэффициенты (odds)
- `OddsService.ensureFresh(...)` — ESPN scoreboard, TTL 60с.
- Odds сохраняются в БД (`match.odd_home/draw/away`) для переиспользования в miniapp и разборе тура.
- В модалке прогноза показываются odds из API матча.

### Разбор тура («Мои»)
- `GET /weeks/{weekId}/review` → список матчей: факт, прогноз, очки; заголовок «Разбор тура · N очк.».
- Очки суммируются по всем матчам тура (включая `-1`), логика как в leaderboard.

### Live-блок на главной и pre-start
- Показывает live-матчи и «скоро стартующие» за ~10 минут до kickoff (`LIVE_PRESTART_WINDOW_SECONDS`).
- API отдаёт `kickoffSecondsLeft` (секунды до свистка); pre-live держится при `sec <= 0`, пока статус `ns` (фикс пропадания блока сразу после kickoff).
- Если матч ещё не live — клик ведёт в `#score-modal`; если live — в `#live-modal`.
- В pre-start период polling «Сегодня» ускорен до 10с.

### Live-модалка: составы
- Свернутый спойлер «Составы команд» (по умолчанию закрыт).
- Переключатель HOME/AWAY с подписью схемы (`NEW · 4-2-3-1`).
- Единый блок `.formation-lineup`: **расстановка** + разделитель + **запасные** (центрированный текстовый список, без клика).
- Данные из ESPN `summary.rosters`: `formation`, `formationPlace`, `jerseyImages`, stats, `subbedOut`/`subbedIn`, связи `subbedOutFor`/`subbedInFor` → `subPartnerId/Name`.
- Fallback: API-Football lineups, если ESPN rosters пусты.
- Расстановка по Opta `formationPlace` (словарь схем 4-2-3-1, 4-3-3, …; fallback на 4-2-3-1 при неизвестной схеме).
- **Форма игрока:** картинка ESPN `jerseyImages` (не цветной прямоугольник); при отсутствии — номер на фоне цвета команды.
- **Замены на поле:** вышедший запасной занимает позицию `formationPlace` заменённого; зелёная обводка формы; иконка ↕ справа сверху (без фонового бейджа).
- Бейджи событий на поле: голы, ассисты, карточки (слева сверху).
- Тап по игроку основного состава → `#player-modal` (форма без двоения: при `jerseyImage` номер/рамка не рисуются поверх картинки; stats из ESPN).
- Запасные в списке: `23 Murphy · RB` (без `#`, позиция `SUB` скрыта).

### Live-модалка: мини-поле и события
- Постоянное мини-поле (горизонтальное, 105:68, SVG IFAB + полосы травы) над лентой событий.
- События из ESPN `commentary` (сортировка по period/time/sequence, свежие сверху); RU-перевод из `live_event_ru_translation.json`.
- Маркер на поле: последнее событие с координатами; тап по событию в ленте — просмотр выбранного; при **новом** событии в ленте авто-переключение на него.
- На `HT` авто-маркер скрывается; выбранное событие из ленты можно показать вручную.
- Траектории: гол — сплошная; створ/мимо/блок — пунктир; offside — пунктир player→линия; X зеркалится во 2-м тайме для away (`period`); Y не трогаем.
- Тап по полю → overlay live-статистики (`matchStats` из ESPN boxscore: владение, удары, фолы, …).
- Превью геометрии/пример: `docs/live-pitch-preview.svg`.
- Live-модалка polling `/live-details` каждые 10с.

### Прочее
- `ApiClient.getLineups(matchPublicId)` — in-memory кэш до завершения матча; очистка в `DataInitService` и `NotificationService.sendFullTime`.
- Remind-уведомления: ESPN rosters (`starter=true`) → fallback API-Football.
- Новости в miniapp: Sports.ru RSS по тегам команд (`MiniAppService.loadMatchNews`).

## Генерация изображений уведомлений
- В `ImageRenderer` fallback цветов команд по `teamId`, если нет записи в `team_colors.json`.
- Масштабирование логотипов с сохранением пропорций (padding).
- Логотипы сезона 2026: `64.webp` (HUL), `1346.webp` (COV).

## Обязательная актуализация файла
- Любое изменение архитектуры, конфигов, env, запусков, деплоя, эндпоинтов, интеграций или новых файлов должно сопровождаться обновлением `PROJECT_CONTEXT.md`.
- Если агент меняет код и не обновил этот файл при контекстно значимом изменении — задача считается выполненной не полностью.
- После deploy с изменениями miniapp: проверь, что на главном экране отображается актуальный `ver. <git-hash>`.
- Не добавлять в этот файл секреты и инфраструктурные идентификаторы (IP, домены, порты WAN SSH, логины, пароли, токены).
