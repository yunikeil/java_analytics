CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    full_name text NOT NULL,
    role text NOT NULL DEFAULT 'LEARNER',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS trainers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title text NOT NULL,
    description text NOT NULL,
    difficulty text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trainer_id uuid NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    type text NOT NULL CHECK (type IN ('TEST', 'ERROR_SEARCH', 'OPEN')),
    title text NOT NULL,
    prompt text NOT NULL,
    max_score int NOT NULL CHECK (max_score > 0),
    options jsonb NOT NULL DEFAULT '[]'::jsonb,
    correct_answer jsonb NOT NULL DEFAULT '{}'::jsonb,
    artifact jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    submitted_answer jsonb NOT NULL,
    score numeric(8, 2) NOT NULL,
    max_score int NOT NULL,
    is_correct boolean NOT NULL,
    feedback text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS progress (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trainer_id uuid NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    completed_tasks int NOT NULL DEFAULT 0,
    total_score numeric(8, 2) NOT NULL DEFAULT 0,
    max_score int NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, trainer_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_trainer ON tasks(trainer_id);
CREATE INDEX IF NOT EXISTS idx_attempts_user ON attempts(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_attempts_task ON attempts(task_id);

INSERT INTO trainers(id, title, description, difficulty)
VALUES
('11111111-1111-1111-1111-111111111111', 'SQL и продуктовая аналитика', 'Практика чтения требований, поиска ошибок и подготовки аналитических выводов.', 'junior-middle')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tasks(id, trainer_id, type, title, prompt, max_score, options, correct_answer, artifact)
VALUES
(
 '21111111-1111-1111-1111-111111111111',
 '11111111-1111-1111-1111-111111111111',
 'TEST',
 'Метрика конверсии',
 'Какой числитель корректен для конверсии из регистрации в первую покупку?',
 10,
 '[{"id":"a","text":"Количество всех визитов"},{"id":"b","text":"Количество пользователей с первой покупкой"},{"id":"c","text":"Количество пользователей с повторной покупкой"}]',
 '{"correct":["b"]}',
 '{}'
),
(
 '21111111-1111-1111-1111-111111111112',
 '11111111-1111-1111-1111-111111111111',
 'TEST',
 'Качество SQL-выборки',
 'Какие приемы помогают избежать дублей при join с таблицей событий? Выберите все подходящие.',
 10,
 '[{"id":"a","text":"Предварительная агрегация событий"},{"id":"b","text":"SELECT * во всех запросах"},{"id":"c","text":"Проверка кардинальности ключей"},{"id":"d","text":"Удаление WHERE-условий"}]',
 '{"correct":["a","c"]}',
 '{}'
),
(
 '21111111-1111-1111-1111-111111111113',
 '11111111-1111-1111-1111-111111111111',
 'TEST',
 'A/B-тест',
 'Что нужно проверить перед интерпретацией результата A/B-теста?',
 10,
 '[{"id":"a","text":"SRM и корректность сплита"},{"id":"b","text":"Только цвет графика"},{"id":"c","text":"Наличие случайных пользователей в обеих группах"}]',
 '{"correct":["a","c"]}',
 '{}'
),
(
 '31111111-1111-1111-1111-111111111111',
 '11111111-1111-1111-1111-111111111111',
 'ERROR_SEARCH',
 'Ошибки в постановке задачи',
 'Найдите ошибки в требованиях к отчету.',
 12,
 '[{"id":"missing_acceptance_criteria","text":"Нет критериев приемки"},{"id":"ambiguous_metric","text":"Метрика описана неоднозначно"},{"id":"good_deadline","text":"Указан срок сдачи"},{"id":"missing_owner","text":"Не указан владелец решения"}]',
 '{"errors":["missing_acceptance_criteria","ambiguous_metric","missing_owner"]}',
 '{"text":"Сделать отчет по активности клиентов. Нужно быстро, чтобы стало понятно, где все плохо. Сдать к пятнице."}'
),
(
 '31111111-1111-1111-1111-111111111112',
 '11111111-1111-1111-1111-111111111111',
 'ERROR_SEARCH',
 'Ошибки в SQL',
 'Отметьте проблемы в запросе.',
 12,
 '[{"id":"select_star","text":"SELECT * в аналитической витрине"},{"id":"no_date_filter","text":"Нет фильтра по периоду"},{"id":"inner_join_loss","text":"INNER JOIN может потерять пользователей без заказов"},{"id":"clear_aliases","text":"Алиасы читаемые"}]',
 '{"errors":["select_star","no_date_filter","inner_join_loss"]}',
 '{"sql":"SELECT * FROM users u INNER JOIN orders o ON o.user_id = u.id;"}'
),
(
 '31111111-1111-1111-1111-111111111113',
 '11111111-1111-1111-1111-111111111111',
 'ERROR_SEARCH',
 'Ошибки в дашборде',
 'Найдите ошибки в описании дашборда.',
 12,
 '[{"id":"no_refresh_policy","text":"Не описана частота обновления"},{"id":"mixed_granularity","text":"Смешаны дневные и месячные данные"},{"id":"no_filters","text":"Нет базовых фильтров"},{"id":"has_business_goal","text":"Есть бизнес-цель"}]',
 '{"errors":["no_refresh_policy","mixed_granularity","no_filters"]}',
 '{"text":"Дашборд должен показать продажи по дням, месячный план и общий статус команды. Фильтры добавим потом."}'
),
(
 '41111111-1111-1111-1111-111111111111',
 '11111111-1111-1111-1111-111111111111',
 'OPEN',
 'План аналитического исследования',
 'Опишите план исследования падения конверсии в оплату.',
 15,
 '[]',
 '{"keywords":["гипотез","сегмент","воронк","данн","метрик"],"minLength":180}',
 '{"context":"Конверсия в оплату упала на 12% за неделю после релиза новой формы заказа."}'
),
(
 '41111111-1111-1111-1111-111111111112',
 '11111111-1111-1111-1111-111111111111',
 'OPEN',
 'Артефакт требований',
 'Сформулируйте короткий BRD-фрагмент для отчета по удержанию.',
 15,
 '[]',
 '{"keywords":["цель","пользовател","метрик","период","критер"],"minLength":160}',
 '{"context":"Бизнес хочет видеть динамику удержания новых пользователей после первой покупки."}'
),
(
 '41111111-1111-1111-1111-111111111113',
 '11111111-1111-1111-1111-111111111111',
 'OPEN',
 'Проверка результата SQL',
 'Опишите, как вы проверите корректность SQL-запроса для расчета DAU.',
 15,
 '[]',
 '{"keywords":["дублик","период","timezone","сравн","source"],"minLength":160}',
 '{"context":"Нужно убедиться, что DAU совпадает с продуктовым определением активного пользователя."}'
),
(
 '41111111-1111-1111-1111-111111111114',
 '11111111-1111-1111-1111-111111111111',
 'OPEN',
 'Выводы для заказчика',
 'Напишите структуру итогового аналитического вывода после исследования.',
 15,
 '[]',
 '{"keywords":["вывод","рекомендац","ограничен","эффект","следующ"],"minLength":140}',
 '{"context":"Исследование показало, что падение связано в основном с мобильным сегментом."}'
)
ON CONFLICT (id) DO NOTHING;

GRANT USAGE ON SCHEMA public TO analytics_sandbox;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO analytics_sandbox;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO analytics_sandbox;

