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
- PROD DB: jdbc:postgresql://localhost:5432/predicts_2 (application-prod.yml, пароль через deploy/predicts.env -> SPRING_DATASOURCE_PASSWORD)

Удаленный сервер:
- SSH: root@146.255.188.80
- APP_DIR: /home/predictions

Механизмы запуска:
- локально bot+miniapp: ./scripts/run-local.sh
- локально только bot: ./scripts/run-bot-only.sh
- сборка jar: mvn package -DskipTests
- деплой (локальная сборка + upload): ./deploy/upload-jar.sh
- деплой на сервере: ./deploy/deploy_predicts.sh
- прод сервис: systemd unit predicts (deploy/predicts.service)

Обязательное правило:
- если в проект добавлен новый код/фича/скрипт/конфиг/эндпоинт/процесс запуска, ОБЯЗАТЕЛЬНО обнови PROJECT_CONTEXT.md в этом же изменении (что добавлено, где лежит, как запускать/использовать).
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
- Локальные переопределения: `deploy/local.env` (по образцу `deploy/local.env.example`).
- Прод переменные: `deploy/predicts.env` (по образцу `deploy/predicts.env.example`).

## База данных
- LOCAL/TEST:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/predicts_local`
  - `SPRING_DATASOURCE_USERNAME=admin`
  - `SPRING_DATASOURCE_PASSWORD` через `deploy/local.env` (или дефолт локального профиля)
- PROD:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/predicts_2`
  - `SPRING_DATASOURCE_USERNAME=admin`
  - `SPRING_DATASOURCE_PASSWORD` задается в `deploy/predicts.env`

## Удаленный сервер
- IP: `146.255.188.80`
- SSH: `root@146.255.188.80`
- Каталог приложения: `/home/predictions`

## Запуск и деплой
- Локально (бот + miniapp + dev HTTPS): `./scripts/run-local.sh`
- **Демо Telegram + TEST БД + авто-счет + откат БД:** `./scripts/run-telegram-test-demo.sh` (нужен `cloudflared`, остановка `Ctrl+C`)
- Локально на TEST БД `predicts` (браузер, без cloudflared): `./scripts/run-local-test-db.sh`
- Локально на TEST БД + Telegram HTTPS: `./scripts/run-local-test-db.sh --telegram` (нужен `cloudflared`)
- Симуляция матчей «Сегодня» в TEST БД: `./scripts/simulate-today-matches-test-db.sh`
- Откат симуляции: `./scripts/restore-today-matches-test-db.sh`
- Тест live-уведомлений (смена счета): `./scripts/bump-today-live-scores-test-db.sh`
- Локально только бот: `./scripts/run-bot-only.sh`
- Ручная сборка: `mvn package -DskipTests`
- Деплой с локальной сборкой jar: `./deploy/upload-jar.sh` (использует `deploy/deploy.env`)
- Деплой на сервере: `./deploy/deploy_predicts.sh`
- Прод процесс: `systemctl status predicts`

## Mini App: экран "Сегодня" и live-уведомления
- Файл `src/main/resources/static/miniapp/js/app.js`:
  - в списке "Сегодня" (`/api/miniapp/today`) отображается **текущий счёт** для матчей с начавшейся игрой и **время начала** для не начавшихся матчей;
  - прогноз на экране "Сегодня" открывается по нажатию на матч (через существующую модалку выбора счёта);
  - добавлен фоновый polling `/today` (интервал 15 сек) для отслеживания изменений счёта независимо от активной вкладки.
- Файлы `src/main/resources/static/miniapp/index.html` и `src/main/resources/static/miniapp/css/app.css`:
  - добавлен глобальный overlay-контейнер уведомлений о голах;
  - при изменении счёта показывается анимированное уведомление вида `MUN 3-1 MAC` поверх любых экранов mini app;
  - уведомление можно закрыть вручную, также есть авто-скрытие.
- Backend правило изменения прогноза:
  - в `src/main/java/zhigalin/predictions/miniapp/MiniAppService.java` обновлена логика `canPredict(...)`:
    - разрешено изменение/удаление прогноза до `kickoff + 5 минут` даже после старта матча;
    - для завершённых/отменённых статусов (`ft`, `aet`, `pen`, `canc`, `abd`, `awrd`, `wo`) прогноз недоступен.

## Обязательная актуализация файла
- Любое изменение архитектуры, конфигов, env, запусков, деплоя, эндпоинтов, интеграций или новых файлов должно сопровождаться обновлением `PROJECT_CONTEXT.md`.
- Если агент меняет код и не обновил этот файл при контекстно значимом изменении — задача считается выполненной не полностью.
