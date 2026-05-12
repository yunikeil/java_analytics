INSERT INTO trainers(id, title, description, difficulty, created_at)
VALUES
('12222222-2222-2222-2222-222222222222', 'Sandbox SQL: продажи и клиенты', 'Практика запросов к отдельной учебной базе analytics_sandbox.', 'junior', '2026-01-01 10:00:00+00'),
('13333333-3333-3333-3333-333333333333', 'Python для аналитика', 'Короткие pandas-задачи в безопасном sandbox окружении.', 'junior-middle', '2026-01-01 11:00:00+00'),
('14444444-4444-4444-4444-444444444444', 'Диагностика метрик продукта', 'Последовательный разбор метрик, SQL-проверок и выводов.', 'middle', '2026-01-01 12:00:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tasks(id, trainer_id, type, title, prompt, max_score, options, correct_answer, artifact, created_at)
VALUES
(
 '22222222-2222-2222-2222-222222222201',
 '12222222-2222-2222-2222-222222222222',
 'OPEN',
 'SQL: клиенты по городам',
 'Запустите запрос в sandbox и напишите, какой город лидирует по количеству клиентов. В ответе укажите город и короткую проверку.',
 10,
 '[]',
 '{"keywords":["москва","count","клиент"],"minLength":50}',
 '{"sandbox":{"kind":"sql","starter":"select city, count(*) as customers\nfrom customers\ngroup by city\norder by customers desc;"},"hint":"Учебная БД: analytics_sandbox, таблица customers."}',
 '2026-01-01 10:01:00+00'
),
(
 '22222222-2222-2222-2222-222222222202',
 '12222222-2222-2222-2222-222222222222',
 'OPEN',
 'SQL: оплаченная выручка',
 'Через sandbox посчитайте выручку только по оплаченным заказам. В ответе укажите сумму и фильтр, который использовали.',
 10,
 '[]',
 '{"keywords":["paid","24740","sum","amount"],"minLength":60}',
 '{"sandbox":{"kind":"sql","starter":"select sum(amount) as paid_revenue\nfrom orders\nwhere status = ''paid'';"},"hint":"Не включайте cancelled/refunded в оплаченный revenue."}',
 '2026-01-01 10:02:00+00'
),
(
 '22222222-2222-2222-2222-222222222203',
 '12222222-2222-2222-2222-222222222222',
 'ERROR_SEARCH',
 'SQL: ошибка агрегации',
 'Отметьте проблемы запроса перед тем, как запускать его в отчете.',
 12,
 '[{"id":"cancelled_in_revenue","text":"В revenue попадут cancelled/refunded заказы"},{"id":"no_group_by_city","text":"city выбран без GROUP BY"},{"id":"join_ok","text":"JOIN по customer_id корректен"},{"id":"select_star","text":"SELECT * используется в итоговой витрине"}]',
 '{"errors":["cancelled_in_revenue","no_group_by_city"]}',
 '{"sql":"select c.city, sum(o.amount) as revenue\nfrom customers c\njoin orders o on o.customer_id = c.customer_id;"}',
 '2026-01-01 10:03:00+00'
),
(
 '33333333-3333-3333-3333-333333333301',
 '13333333-3333-3333-3333-333333333333',
 'OPEN',
 'Python: средний чек',
 'Запустите pandas-код в sandbox и напишите средний чек по оплаченным заказам. Добавьте, почему cancelled лучше исключить.',
 12,
 '[]',
 '{"keywords":["paid","mean","amount","3712"],"minLength":70}',
 '{"sandbox":{"kind":"python","starter":"paid = orders[orders[\"status\"] == \"paid\"]\nprint(paid[\"amount\"].mean())"},"hint":"В Python sandbox уже есть DataFrame orders."}',
 '2026-01-01 11:01:00+00'
),
(
 '33333333-3333-3333-3333-333333333302',
 '13333333-3333-3333-3333-333333333333',
 'OPEN',
 'Python: revenue по статусам',
 'Сгруппируйте заказы по статусу в Python sandbox и опишите, какой статус дает основную сумму.',
 12,
 '[]',
 '{"keywords":["groupby","status","paid","amount"],"minLength":70}',
 '{"sandbox":{"kind":"python","starter":"print(orders.groupby(\"status\")[\"amount\"].sum().sort_values(ascending=False))"},"hint":"Используйте DataFrame orders."}',
 '2026-01-01 11:02:00+00'
),
(
 '33333333-3333-3333-3333-333333333303',
 '13333333-3333-3333-3333-333333333333',
 'TEST',
 'Python: безопасный вывод',
 'Что стоит вывести после группировки, чтобы результат было удобно проверить?',
 8,
 '[{"id":"shape","text":"Размер результата и первые строки"},{"id":"raw_secret","text":"Секреты окружения"},{"id":"aggregation_logic","text":"Логику фильтра и агрегации"},{"id":"nothing","text":"Ничего, если код выполнился"}]',
 '{"correct":["shape","aggregation_logic"]}',
 '{}',
 '2026-01-01 11:03:00+00'
),
(
 '44444444-4444-4444-4444-444444444401',
 '14444444-4444-4444-4444-444444444444',
 'TEST',
 'Диагностика падения оплаты',
 'С чего начать разбор падения оплаты после релиза?',
 10,
 '[{"id":"segment","text":"Разбить метрику по платформам/каналам/версиям"},{"id":"ignore_release","text":"Игнорировать дату релиза"},{"id":"data_quality","text":"Проверить полноту событий и заказов"},{"id":"only_average","text":"Смотреть только среднее по всем пользователям"}]',
 '{"correct":["segment","data_quality"]}',
 '{}',
 '2026-01-01 12:01:00+00'
),
(
 '44444444-4444-4444-4444-444444444402',
 '14444444-4444-4444-4444-444444444444',
 'OPEN',
 'SQL: первая покупка',
 'В sandbox проверьте, сколько пользователей дошли до события first_purchase. В ответе укажите число и таблицу.',
 10,
 '[]',
 '{"keywords":["first_purchase","events","6","count"],"minLength":60}',
 '{"sandbox":{"kind":"sql","starter":"select count(distinct customer_id) as buyers\nfrom events\nwhere event_name = ''first_purchase'';"},"hint":"Используйте events, а не orders: это проверка событийной воронки."}',
 '2026-01-01 12:02:00+00'
),
(
 '44444444-4444-4444-4444-444444444403',
 '14444444-4444-4444-4444-444444444444',
 'OPEN',
 'Вывод по проверке',
 'Сформулируйте короткий вывод для заказчика: что проверили в sandbox, что получилось и какой следующий шаг.',
 14,
 '[]',
 '{"keywords":["провер","sandbox","вывод","следующ","сегмент"],"minLength":120}',
 '{"context":"Свяжите SQL/Python-проверку с бизнес-решением, а не только с числом."}',
 '2026-01-01 12:03:00+00'
)
ON CONFLICT (id) DO NOTHING;
