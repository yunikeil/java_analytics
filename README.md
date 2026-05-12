# Учебный проект 

MVP бэкенд-сервиса тренажера для аналитиков. В проекте есть регистрация по OTP на почту, REST API, Swagger UI, Postgres, Redis, MailHog, pgAdmin, Grafana, Loki/Tempo/OpenTelemetry, отдельная песочница для SQL и Python, UI.

## Стек

- Java 21, Javalin, JDBC, Flyway, HikariCP
- PostgreSQL, Redis
- MailHog для локальной проверки OTP
- Python FastAPI sandbox для SQL-запросов и аналитических скриптов
- React + Vite UI
- Grafana + Loki + Tempo + OpenTelemetry Collector

## Быстрый запуск

```bash
docker compose up --build
```

После запуска:

| Сервис | URL | Доступ |
| --- | --- | --- |
| UI  | http://localhost:5173 | UI..?) |
| Backend API | http://localhost:8080 | REST API |
| Swagger UI | http://localhost:8081 | OpenAPI для backend |
| OpenAPI YAML | http://localhost:8080/openapi.yaml | спецификация API |
| Sandbox API UI | http://localhost:8090/docs | Swagger FastAPI для SQL/Python sandbox |
| MailHog UI | http://localhost:8025 | просмотр OTP-писем |
| pgAdmin UI | http://localhost:5050 | `admin@local.dev` / `admin` |
| Redis Insight UI | http://localhost:5540 | без логина, подключение `analytics-trainer-redis` преднастроено |
| Grafana UI | http://localhost:3000 | `admin` / `admin` |
| Grafana Services Dashboard | http://localhost:3000/d/analytics-trainer-services/analytics-trainer-services-overview | готовый dashboard по сервисам |
| Prometheus UI | http://localhost:9090 | targets и PromQL |
| Prometheus default query | http://localhost:9090/query?g0.expr=up&g0.tab=1 | стартовый запрос `up` по всем targets |
| cAdvisor UI | http://localhost:8083 | контейнерные метрики Docker |
| Loki API | http://localhost:3100 | источник логов для Grafana |
| Tempo API | http://localhost:3200 | источник трейсов для Grafana |

Логин pgAdmin: `admin@local.dev`, пароль: `admin`. Сервер Postgres уже добавлен автоматически.

Redis Insight преднастроен на подключение `analytics-trainer-redis` к `redis:6379`. При старте compose создается несколько демонстрационных ключей `analytics_trainer:*`, а во время регистрации backend кладет OTP-состояние в Redis с TTL.

Grafana автоматически получает datasources `Prometheus`, `Loki` и `Tempo`, а также dashboard `Analytics Trainer - Services Overview`. На нем есть health targets, CPU/RAM/сеть контейнеров, backend RPS/latency, Redis commands, Postgres connections и поток warning/error логов.

## API

Swagger UI доступен на http://localhost:8081

Ключевые эндпоинты:

- `POST /api/auth/register/request`
- `POST /api/auth/register/confirm`
- `POST /api/auth/login`
- `GET /api/me`
- `GET /api/trainers`
- `GET /api/trainers/{id}/tasks`
- `GET /api/tasks/{id}`
- `POST /api/tasks/{id}/submit`
- `GET /api/progress`
- `GET /api/attempts`

## Модель данных

- `users` - пользователи и роли
- `trainers` - учебные тренажеры
- `tasks` - задания трех типов: `TEST`, `ERROR_SEARCH`, `OPEN`
- `attempts` - попытки выполнения заданий
- `progress` - агрегированный прогресс пользователя по тренажеру

Баллы начисляются единообразно:

- тесты: полный балл только при точном совпадении набора ответов;
- поиск ошибок: пропорционально найденным правильным ошибкам, лишние ответы штрафуют;
