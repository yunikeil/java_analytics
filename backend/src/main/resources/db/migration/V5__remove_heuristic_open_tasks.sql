DELETE FROM tasks
WHERE type = 'OPEN'
  AND NOT (correct_answer ? 'sandbox');
