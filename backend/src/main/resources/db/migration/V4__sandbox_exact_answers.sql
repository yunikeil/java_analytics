UPDATE tasks
SET correct_answer = '{"sandbox":{"kind":"sql","rows":[{"city":"Москва","customers":3},{"city":"Казань","customers":2},{"city":"Екатеринбург","customers":1},{"city":"Новосибирск","customers":1},{"city":"Санкт-Петербург","customers":1}]}}'::jsonb,
    artifact = jsonb_set(artifact, '{sandbox,solution}', '"select city, count(*) as customers\nfrom customers\ngroup by city\norder by customers desc;"'::jsonb, true)
WHERE id = '22222222-2222-2222-2222-222222222201';

UPDATE tasks
SET correct_answer = '{"sandbox":{"kind":"sql","rows":[{"paid_revenue":31440.0}]}}'::jsonb,
    artifact = jsonb_set(artifact, '{sandbox,solution}', '"select sum(amount) as paid_revenue\nfrom orders\nwhere status = ''paid'';"'::jsonb, true)
WHERE id = '22222222-2222-2222-2222-222222222202';

UPDATE tasks
SET correct_answer = '{"sandbox":{"kind":"python","stdout":"3720.0\n"}}'::jsonb,
    artifact = jsonb_set(artifact, '{sandbox,solution}', '"paid = orders[orders[\"status\"] == \"paid\"]\nprint(paid[\"amount\"].mean())"'::jsonb, true)
WHERE id = '33333333-3333-3333-3333-333333333301';

UPDATE tasks
SET correct_answer = '{"sandbox":{"kind":"python","stdout":"status\npaid         18600.0\ncancelled     3100.0\nName: amount, dtype: float64\n"}}'::jsonb,
    artifact = jsonb_set(artifact, '{sandbox,solution}', '"print(orders.groupby(\"status\")[\"amount\"].sum().sort_values(ascending=False))"'::jsonb, true)
WHERE id = '33333333-3333-3333-3333-333333333302';

UPDATE tasks
SET correct_answer = '{"sandbox":{"kind":"sql","rows":[{"buyers":6}]}}'::jsonb,
    artifact = jsonb_set(artifact, '{sandbox,solution}', '"select count(distinct customer_id) as buyers\nfrom events\nwhere event_name = ''first_purchase'';"'::jsonb, true)
WHERE id = '44444444-4444-4444-4444-444444444402';
