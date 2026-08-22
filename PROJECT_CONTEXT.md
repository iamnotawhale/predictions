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

Подключения:
- TEST/LOCAL DB: jdbc:postgresql://localhost:5432/predicts_local (application-local.yml, можно переопределять через deploy/local.env)
- PROD DB: jdbc:postgresql://localhost:5432/predicts_prod (application-prod.yml, пароль через deploy/predicts.env -> SPRING_DATASOURCE_PASSWORD)
- TEST DB на сервере: predicts_test

Удаленный сервер:
- SSH: root@81.31.209.186
- APP_DIR: /home/predictions
- Mini App: https://81-31-209-186.sslip.io:8443/miniapp/
- DB tunnel for local debug: ./scripts/ssh-tunnel-prod-db.sh → localhost:15432

Механизмы запуска:
- локально bot+miniapp: ./scripts/run-local.sh
- локально только bot: ./scripts/run-bot-only.sh
- сборка jar: mvn package -DskipTests
- деплой (локальная сборка + upload): ./deploy/upload-jar.sh
- деплой на сервере: ./deploy/deploy_predicts.sh
- прод сервис: systemd unit predicts (deploy/predicts.service)

Обязательное правило:
- если в проект добавлен новый код/фича/скрипт/конфиг/эндпоинт/процесс запуска, ОБЯЗАТЕЛЬНО обнови PROJECT_CONTEXT.md в этом же изменении (что добавлено, где лежит, как запускать/использовать).
- при каждом push/deploy с изменениями miniapp frontend ОБЯЗАТЕЛЬНО увеличивай `ver` в `src/main/resources/static/miniapp/index.html` (см. раздел «Версия miniapp»).
```

## Кратко о проекте
- `predictions` — сервис прогнозов матчей АПЛ.
- Интерфейсы: Telegram-бот + Telegram Mini App.
- Данные хранятся в PostgreSQL, базовые таблицы описаны в `src/main/resources/tablesInit.sql`.

## Структура проекта
- `src/main/java/zhigalin/predictions/telegram` — команды и обработка апдейтов Telegram-бота.
- `src/main/java/zhigalin/predictions/miniapp` — REST API и auth для Telegram WebApp (`X-Telegram-Init-Data`).
- `src/main/java/zhigalin/predictions/service` — бизнес-логика (матчи, прогнозы, уведомления, синхронизация).
- `src/main/java/zhigalin/predictions/repository` — JDBC/DAO слой.
- `src/main/resources/static/miniapp` — клиентская часть Mini App.
- `scripts` — локальный запуск и dev HTTPS.
- `deploy` — конфиги и сценарии деплоя/прод запуска.

## Подключения и профили
- `application.yml` — базовый конфиг.
- `application-local.yml` — локальный/тестовый профиль (`spring.profiles.active=local`).
- `application-prod.yml` — прод профиль (`spring.profiles.active=prod`).
- `src/main/resources/static/miniapp/live_event_ru_translation.json` — единственный источник RU-перевода live-комментариев ESPN (типы событий, шаблоны фраз, замены, ассисты); загружается miniapp через `js/live-event-ru.js`.
- Локальные переопределения: `deploy/local.env` (по образцу `deploy/local.env.example`).
- Прод переменные: `deploy/predicts.env` (по образцу `deploy/predicts.env.example`).

## База данных
- LOCAL/TEST:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/predicts_local`
  - `SPRING_DATASOURCE_USERNAME=admin`
  - `SPRING_DATASOURCE_PASSWORD` через `deploy/local.env` (или дефолт локального профиля)
- PROD:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/predicts_prod`
  - `SPRING_DATASOURCE_USERNAME=admin`
  - `SPRING_DATASOURCE_PASSWORD` задается в `deploy/predicts.env`

## Удаленный сервер
- IP: `81.31.209.186`
- SSH: `root@81.31.209.186`
- Каталог приложения: `/home/predictions`
- Mini App HTTPS: `https://81-31-209-186.sslip.io:8443/miniapp/` (Caddy :8443 → Spring :8080)
- Прод сервис: `systemctl status predicts`
- Прод БД на сервере: `predicts_prod`; test БД: `predicts_test`
- Postgres слушает только `localhost` (снаружи закрыт). Для IDEA Debug: `./scripts/ssh-tunnel-prod-db.sh` → JDBC `jdbc:postgresql://localhost:15432/predicts_test`

## Запуск и деплой
- Локально (бот + miniapp + dev HTTPS): `./scripts/run-local.sh`
- `scripts/run-local.sh` подхватывает `deploy/local.env` и `deploy/local-https.env` как переменные окружения (через `source`), а не как Spring config-файлы.
- `scripts/run-local.sh` автоматически использует `~/.local/bin/cloudflared`, если `cloudflared` не найден в системном `PATH`.
- Для one-click Debug в IntelliJ IDEA добавлены shared run-конфиги в `.run/`:
  - `01 Prepare Local HTTPS` → запускает `scripts/prepare-idea-debug.sh` (обновляет `deploy/local-https.env` и `deploy/local-https.properties` через cloudflared и принудительно обновляет Telegram menu button на актуальный `BOT_WEBAPP_URL`);
  - `02 Predictions Local Debug` → Spring Boot Debug (profile `local`) с pre-launch шагом `01 Prepare Local HTTPS`, JDBC через SSH-туннель `localhost:15432/predicts_test` (см. `scripts/ssh-tunnel-prod-db.sh`) и автоподхватом `optional:file:./deploy/local-https.properties`.
- **Демо Telegram + TEST БД + авто-счет + откат БД:** `./scripts/run-telegram-test-demo.sh` (нужен `cloudflared`, остановка `Ctrl+C`)
- Локально на TEST БД `predicts_test` (браузер, без cloudflared): `./scripts/run-local-test-db.sh`
- Локально на TEST БД + Telegram HTTPS: `./scripts/run-local-test-db.sh --telegram` (нужен `cloudflared`)
- Симуляция матчей «Сегодня» в TEST БД: `./scripts/simulate-today-matches-test-db.sh`
- Откат симуляции: `./scripts/restore-today-matches-test-db.sh`
- Тест live-уведомлений (смена счета): `./scripts/bump-today-live-scores-test-db.sh`
- Локально только бот: `./scripts/run-bot-only.sh`
- Ручная сборка: `mvn package -DskipTests`
- Деплой с локальной сборкой jar: `./deploy/upload-jar.sh` (использует `deploy/deploy.env`, сервер `root@81.31.209.186`)
- Деплой на сервере: `./deploy/deploy_predicts.sh`
- Прод процесс: `systemctl status predicts`

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

## Mini App: экран "Сегодня", live и новые фичи
- Файлы `static/miniapp/js/app.js`, `index.html`, `css/app.css`:
  - «Сегодня»: текущий счёт / время старта, таймер до `kickoff+5м`, приоритет матчей без прогноза, polling 15с (live) / 60с (idle);
  - overlay-уведомления о голах;
  - offline-баннер при ошибках сети/API;
  - кэш leaderboard/chart/standings ~45с;
  - серверные заголовки для `/miniapp/**`: `Cache-Control: no-store, must-revalidate` (фикс против залипания старого фронта в Telegram WebView);
  - на главной (`screen-stats`) внизу по центру добавлена малозаметная подпись версии (`.miniapp-version`, формат `ver. YYYYMMDD-N`);
  - **версия miniapp (обязательно при push/deploy):** строка `ver` в `src/main/resources/static/miniapp/index.html` (`<div class="miniapp-version">ver. …</div>`). При каждом push/deploy, где меняется miniapp (`index.html`, `js/app.js`, `css/app.css` или связанный backend API miniapp) — **увеличивай версию в том же коммите**. Формат: `YYYYMMDD-N` (дата + порядковый номер деплоя за день, напр. `20260822-33` → `20260822-34`; в новый день можно начать с `-1`). По подписи на главном экране в Telegram WebView проверяют, что подтянулся свежий фронт после деплоя.
  - header показывает сезон/тур (`profile.weekLabel`);
  - `Crowd Meter` удален из UI модалки матча (блок и клиентская загрузка выключены);
  - odds для матчей Mini App обновляются через `OddsService.ensureFresh(...)` с TTL 60с (источник ESPN scoreboard), чтобы коэффициенты появлялись без запуска today-уведомлений;
  - `Live Points Race` удалён из UI/API; live-динамика встроена в `Общий зачёт` и `Текущий тур` (`GET /api/miniapp/leaderboard` возвращает `provisionalPoints/liveDelta/liveActive`);
  - live-подсчёт очков в leaderboard считает очки по тем же правилам, что и финальный зачёт (включая `-1`), и учитывает пользователей без прогноза на уже live/finished матчах.
  - **Разбор тура** в «Мои» (`GET /api/miniapp/weeks/{weekId}/review`);
  - для live-матча в модалке центральный блок показывает текущий счёт и live-статус/время (`17'`, `HT`) вместо `VS + kickoff`;
  - live-события для модалки берутся из ESPN summary endpoint `.../summary?event=<espnEventId>` только из `commentary`, сортируются с учётом тайма (`play.period.number`), затем времени/sequence (свежие сверху), а в UI показываются с иконкой типа события (по `commentary.play.type.type`).
  - текст live-событий в miniapp переводится на русский из `live_event_ru_translation.json` (шаблоны + замены фраз; имена игроков/команд не переводятся).
  - live-матч открывается в отдельной модалке `#live-modal` (не `#score-modal`): только шапка (логотипы, коды, счёт, минута), составы, мини-поле и лента событий — без котировок, сетки прогноза, H2H и новостей; модалка прогноза `#score-modal` остаётся отдельно.
  - в live-модалке над лентой событий добавлено постоянное мини-поле (горизонтальное, с разметкой и вертикальными полосами травы) в пропорции профессионального поля `105:68`; разметка отрисовывается встроенным `SVG` (стабильнее в Telegram WebView), геометрия приведена к значениям IFAB (штрафная `16.5m`, вратарская `5.5m`, точка пенальти `11m`, радиус центра/дуги `9.15m`, угловые четверти окружности `1m`), включая полуокружности штрафной; точка удара `fieldStart` цветом команды; для **блока** — вторая точка на `fieldEnd` цветом соперника (блокирующий); для **удара в створ** при наличии `fieldEnd` — линия и вторая точка на вратаре (цвет соперника), без продолжения за сейв; иначе линия к `goalPositionY`; **гол — сплошная**, **створ/мимо/блок — пунктир**; бейдж `мин' фамилия · ТИП`; ESPN: атака к `x≈100`, ось Y общая (верх поля); после смены ворот во 2-м тайме зеркалим только X (`x → 100−x`) когда `away XOR 2-й тайм` (`period`), Y/`goalPositionY` не трогаем; на `HT` авто-маркер скрывается, тап по событию в ленте снова показывает; превью: `docs/live-pitch-preview.svg`.
  - live-статистика матча (`GET .../live-details` → `matchStats`) берётся из ESPN `boxscore.teams[].statistics` с ключами `possessionPct`, `totalShots`, `shotsOnTarget`, `foulsCommitted`, `offsides`, `wonCorners`, `yellowCards`, `redCards` (владение с суффиксом `%`).
  - составы в live-модалке отображаются в одном общем свернутом спойлере (`Составы команд`) и раскрываются по тапу; внутри — две колонки по командам; мини-поле ограничено по ширине и центрировано, чтобы корректно помещаться на узких экранах.
  - блок `Live` на главной начинает показывать «скоро стартующие» матчи за ~10 минут до kickoff; если матч еще не live, клик ведет в обычную модалку прогноза, а polling в этот период работает как live (15с).
  - график очков рисует горизонтальные линии/подписи `Y` по целым значениям (без округления дробных грид-уровней), чтобы точки точно совпадали с числовыми отметками.
  - маппинг иконок live-событий кастомизирован: пенальти `P`, офсайд белый флаг, удар в створ target, угловой красный флаг.
- Backend `canPredict`: до `kickoff + 5 минут`; закрытые статусы `ft/aet/pen/canc/abd/awrd/wo` — нельзя.
- `ApiClient.getLineups(matchPublicId)` использует in-memory `ConcurrentHashMap` кэш (по `publicId`) и держит составы до завершения матча; очистка вызывается в `DataInitService` и `NotificationService.sendFullTime`.
- Для remind-уведомлений бот сначала пытается взять стартовые составы из ESPN `summary?event=<espn_id>` (`rosters`, только `starter=true`) и использует API-Football lineups как fallback.

## Генерация изображений уведомлений
- В `ImageRenderer` fallback цветов команд по `teamId`, если нет записи в `team_colors.json`.
- Масштабирование логотипов с сохранением пропорций (padding).
- Логотипы сезона 2026: `64.webp` (HUL), `1346.webp` (COV).

## Обязательная актуализация файла
- Любое изменение архитектуры, конфигов, env, запусков, деплоя, эндпоинтов, интеграций или новых файлов должно сопровождаться обновлением `PROJECT_CONTEXT.md`.
- Если агент меняет код и не обновил этот файл при контекстно значимом изменении — задача считается выполненной не полностью.
- Перед push/deploy с изменениями miniapp: проверь, что в `src/main/resources/static/miniapp/index.html` обновлена подпись `ver. YYYYMMDD-N`.
