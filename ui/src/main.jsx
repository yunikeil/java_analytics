import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BarChart3, Check, Database, Eye, LogOut, Mail, Play, RefreshCw, ShieldCheck } from 'lucide-react';
import './styles.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const SANDBOX_URL = import.meta.env.VITE_SANDBOX_URL || 'http://localhost:8090';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || (hasAuthCookie() ? 'cookie' : ''));
  const [user, setUser] = useState(null);
  const [view, setView] = useState('trainer');

  useEffect(() => {
    if (!token) return;
    api('/api/me', token).then(setUser).catch(() => logout());
  }, [token]);

  function onAuth(auth) {
    localStorage.setItem('token', auth.token);
    setToken(auth.token);
    setUser(auth.user);
  }

  function logout() {
    localStorage.removeItem('token');
    document.cookie = 'auth_token=; Max-Age=0; Path=/; SameSite=Lax';
    setToken('');
    setUser(null);
  }

  if (!token) {
    return <AuthScreen onAuth={onAuth} />;
  }

  return (
    <div className="appShell">
      <aside className="sidebar">
        <div className="brand">
          <ShieldCheck size={24} />
          <span>Analytics Trainer</span>
        </div>
        <button className={view === 'trainer' ? 'nav active' : 'nav'} onClick={() => setView('trainer')}>
          <BarChart3 size={18} /> Тренажер
        </button>
        <button className={view === 'sandbox' ? 'nav active' : 'nav'} onClick={() => setView('sandbox')}>
          <Database size={18} /> Sandbox
        </button>
        <a className="nav" href="http://localhost:8081" target="_blank" rel="noreferrer">
          <Play size={18} /> Swagger
        </a>
        <div className="userBox">
          <strong>{user?.fullName}</strong>
          <span>{user?.email}</span>
          <button className="ghost" onClick={logout}><LogOut size={16} /> Выйти</button>
        </div>
      </aside>
      <main className="content">
        {view === 'trainer' ? <TrainerWorkspace token={token} /> : <Sandbox />}
      </main>
    </div>
  );
}

function AuthScreen({ onAuth }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ email: 'analyst@example.com', password: 'secret123', fullName: 'Demo Analyst', otp: '' });
  const [message, setMessage] = useState('');
  const [showPassword, setShowPassword] = useState(true);

  async function requestOtp() {
    setMessage('');
    await publicApi('/api/auth/register/request', {
      email: form.email,
      password: form.password,
      fullName: form.fullName,
    });
    setMode('confirm');
    setMessage('OTP отправлен в MailHog на localhost:8025');
  }

  async function confirmOtp() {
    const auth = await publicApi('/api/auth/register/confirm', { email: form.email, otp: form.otp });
    onAuth(auth);
  }

  async function login() {
    const auth = await publicApi('/api/auth/login', { email: form.email, password: form.password });
    onAuth(auth);
  }

  return (
    <div className="authPage">
      <section className="authPanel">
        <div className="brand large"><ShieldCheck size={30} /><span>Analytics Trainer</span></div>
        <div className="tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>Вход</button>
          <button className={mode !== 'login' ? 'active' : ''} onClick={() => setMode('register')}>Регистрация</button>
        </div>
        {mode !== 'login' && (
          <label>Имя<input value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></label>
        )}
        <label>Email<input value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
        {mode !== 'confirm' && (
          <>
            <label>Пароль<input type={showPassword ? 'text' : 'password'} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>
            <label className="inlineCheck">
              <input type="checkbox" checked={showPassword} onChange={e => setShowPassword(e.target.checked)} />
              Показывать пароль
            </label>
          </>
        )}
        {mode === 'confirm' && (
          <label>OTP из MailHog<input value={form.otp} onChange={e => setForm({ ...form, otp: e.target.value })} /></label>
        )}
        {mode === 'login' && <button className="primary" onClick={wrap(login, setMessage)}>Войти</button>}
        {mode === 'register' && <button className="primary" onClick={wrap(requestOtp, setMessage)}><Mail size={16} /> Получить OTP</button>}
        {mode === 'confirm' && <button className="primary" onClick={wrap(confirmOtp, setMessage)}><Check size={16} /> Подтвердить</button>}
        {message && <p className="notice">{message}</p>}
      </section>
    </div>
  );
}

function TrainerWorkspace({ token }) {
  const [trainers, setTrainers] = useState([]);
  const [trainerId, setTrainerId] = useState('');
  const [tasks, setTasks] = useState([]);
  const [taskId, setTaskId] = useState('');
  const [taskIndex, setTaskIndex] = useState(0);
  const [progress, setProgress] = useState([]);
  const [attempts, setAttempts] = useState([]);
  const [answer, setAnswer] = useState({});
  const [result, setResult] = useState(null);

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    if (!trainerId) return;
    api(`/api/trainers/${trainerId}/tasks`, token).then(items => {
      setTasks(items);
      setTaskId(items[0]?.id || '');
      setTaskIndex(0);
      setAnswer({});
      setResult(null);
    });
  }, [trainerId]);

  async function refresh() {
    const loadedTrainers = await api('/api/trainers', token);
    setTrainers(loadedTrainers);
    setTrainerId(current => current || loadedTrainers[0]?.id || '');
    setProgress(await api('/api/progress', token));
    setAttempts(await api('/api/attempts', token));
  }

  const task = useMemo(() => tasks.find(item => item.id === taskId), [tasks, taskId]);
  const latestAttemptsByTask = useMemo(() => {
    const byTask = {};
    attempts.forEach(attempt => {
      if (!byTask[attempt.taskId]) {
        byTask[attempt.taskId] = attempt;
      }
    });
    return byTask;
  }, [attempts]);

  async function submit() {
    const payload = task.artifact?.sandbox
      ? { answer: { sandbox: { kind: task.artifact.sandbox.kind, result: answer.sandboxResult } } }
      : task.type === 'OPEN'
        ? { answer: { text: answer.text || '' } }
        : { answer: selectedValues(answer) };

    const response = await api(`/api/tasks/${task.id}/submit`, token, payload);
    setResult(response);
    await refresh();

    if (response.correct && taskIndex < tasks.length - 1) {
      const nextIndex = taskIndex + 1;
      setTaskIndex(nextIndex);
      setTaskId(tasks[nextIndex].id);
      setAnswer({});
    }
  }

  function selectTask(index) {
    setTaskIndex(index);
    setTaskId(tasks[index]?.id || '');
    setAnswer({});
    setResult(null);
  }

  return (
    <div className="grid">
      <section className="panel span2">
        <div className="panelHead">
          <div>
            <h1>Рабочее место аналитика</h1>
            <p>Тренажеры, задания, попытки и прогресс обучения</p>
          </div>
          <button className="iconButton" onClick={refresh} title="Обновить"><RefreshCw size={18} /></button>
        </div>
      </section>

      <section className="panel">
        <h2>Тренажеры</h2>
        <div className="list">
          {trainers.map(trainer => (
            <button key={trainer.id} className={trainer.id === trainerId ? 'item active' : 'item'} onClick={() => setTrainerId(trainer.id)}>
              <strong>{trainer.title}</strong>
              <span>{trainer.difficulty} · {trainer.taskCount} заданий</span>
            </button>
          ))}
        </div>
      </section>

      <section className="panel">
        <h2>Прогресс</h2>
        {progress.length === 0 && <p className="muted">Пока нет завершенных попыток</p>}
        {progress.map(item => (
          <div className="progressRow" key={item.trainerId}>
            <div><strong>{item.trainerTitle}</strong><span>{item.completedTasks}/{item.totalTasks} заданий</span></div>
            <b>{item.totalScore}/{item.maxScore}</b>
          </div>
        ))}
      </section>

      <section className="panel span2">
        <div className="split">
          <div>
            <h2>Задания</h2>
            <div className="taskTabs">
              {tasks.map((item, index) => (
                <button className={taskButtonClass(item, taskId, latestAttemptsByTask)} key={item.id} onClick={() => selectTask(index)}>
                  {index + 1}
                </button>
              ))}
            </div>
            {task && (
              <TaskForm
                task={task}
                answer={answer}
                setAnswer={setAnswer}
                submit={wrap(submit, msg => setResult({ feedback: msg }))}
                showSolution={() => showSolution(task.id, token, setAnswer)}
                step={taskIndex + 1}
                total={tasks.length}
              />
            )}
          </div>
          <div className="resultBox">
            <h2>Результат</h2>
            {result ? (
              <>
                <div className="score">{result.score ?? 0}/{result.maxScore ?? task?.maxScore ?? 0}</div>
                <p>{result.feedback}</p>
              </>
            ) : <p className="muted">Отправьте ответ, чтобы увидеть оценку</p>}
            <h3>Последние попытки</h3>
            {attempts.slice(0, 5).map(attempt => <p className="attempt" key={attempt.id}>{attempt.score}/{attempt.maxScore} · {attempt.feedback}</p>)}
          </div>
        </div>
      </section>
    </div>
  );
}

function TaskForm({ task, answer, setAnswer, submit, showSolution, step, total }) {
  const hasSandbox = !!task.artifact?.sandbox;

  return (
    <div className="taskForm">
      <div className="taskMeta">
        <div className="badge">{task.type}</div>
        <span>{step}/{total}</span>
      </div>
      <h3>{task.title}</h3>
      <p>{task.prompt}</p>
      {task.artifact?.hint && <p className="hint">{task.artifact.hint}</p>}
      {task.artifact?.text && <pre>{task.artifact.text}</pre>}
      {task.artifact?.sql && <CodeBlock code={task.artifact.sql} language="sql" />}
      {task.artifact?.sandbox && (
        <SandboxRunner
          kind={task.artifact.sandbox.kind}
          starter={task.artifact.sandbox.starter}
          answer={answer}
          setAnswer={setAnswer}
          submit={submit}
        />
      )}
      {task.type === 'OPEN' && !hasSandbox ? (
        <textarea rows="8" value={answer.text || ''} onChange={e => setAnswer({ text: e.target.value })} placeholder="Введите ответ..." />
      ) : !hasSandbox && (
        <div className="checks">
          {task.options.map(option => (
            <label key={option.id}>
              <input type="checkbox" checked={!!answer[option.id]} onChange={e => setAnswer({ ...answer, [option.id]: e.target.checked })} />
              {option.text}
            </label>
          ))}
        </div>
      )}
      <div className="taskActions">
        {!hasSandbox && <button className="primary" onClick={submit}><Check size={16} /> Отправить ответ</button>}
        <button className="ghost" onClick={showSolution}><Eye size={16} /> Показать ответ</button>
      </div>
    </div>
  );
}

function Sandbox() {
  const [sql, setSql] = useState('select city, count(*) as customers\nfrom customers\ngroup by city\norder by customers desc;');
  const [script, setScript] = useState('paid = orders[orders["status"] == "paid"]\nprint(paid["amount"].mean())');
  const [sqlResult, setSqlResult] = useState(null);
  const [pyResult, setPyResult] = useState(null);
  const [metadata, setMetadata] = useState(null);

  useEffect(() => {
    fetch(`${SANDBOX_URL}/metadata`).then(response => response.json()).then(setMetadata).catch(() => {});
  }, []);

  async function runSql() {
    const response = await fetch(`${SANDBOX_URL}/sql`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: sql }),
    });
    setSqlResult(await response.json());
  }

  async function runPython() {
    const response = await fetch(`${SANDBOX_URL}/python`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ script }),
    });
    setPyResult(await response.json());
  }

  return (
    <div className="grid">
      <section className="panel span2">
        <div className="panelHead">
          <div>
            <h1>Sandbox</h1>
            <p>Учебная база <b>{metadata?.database || 'analytics_sandbox'}</b> и короткие Python-скрипты для аналитических проверок.</p>
          </div>
        </div>
        {metadata && (
          <div className="schemaBoard">
            {metadata.tables.map(table => (
              <article key={table.name} className="schemaCard">
                <button onClick={() => setSql(`select *\nfrom ${table.name}\nlimit 20;`)}>
                  <strong>{table.name}</strong>
                  <span>{table.description}</span>
                </button>
                <div className="columnsLine">
                  {table.columns.map(column => <code key={column.column_name}>{column.column_name}: {column.data_type}</code>)}
                </div>
                <SqlResult result={{ columns: table.columns.map(column => column.column_name), rows: table.sampleRows, count: table.sampleRows.length }} compact />
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <h2>PostgreSQL</h2>
        <div className="quickExamples">
          {metadata?.examples?.map(example => <button key={example} onClick={() => setSql(example)}>{example.split('\n')[0]}</button>)}
        </div>
        <CodeEditor value={sql} onChange={setSql} language="sql" rows={9} />
        <button className="primary" onClick={runSql}><Play size={16} /> Выполнить SQL</button>
        <SqlResult result={sqlResult} />
      </section>

      <section className="panel">
        <h2>Python</h2>
        <CodeEditor value={script} onChange={setScript} language="python" rows={9} />
        <button className="primary" onClick={runPython}><Play size={16} /> Запустить</button>
        <PythonResult result={pyResult} />
      </section>
    </div>
  );
}

function SandboxRunner({ kind, starter, answer, setAnswer, submit }) {
  const [code, setCode] = useState(starter || '');
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (answer.code && answer.code !== code) {
      setCode(answer.code);
      setResult(null);
    }
  }, [answer.code]);

  async function run() {
    const path = kind === 'python' ? '/python' : '/sql';
    const body = kind === 'python' ? { script: code } : { query: code };
    const response = await fetch(`${SANDBOX_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const payload = await response.json();
    setResult(payload);
    setAnswer({ code, sandboxResult: payload });
  }

  return (
    <div className="embeddedSandbox">
      <div className="embeddedHead">
        <strong>{kind === 'python' ? 'Python sandbox' : 'SQL sandbox'}</strong>
      </div>
      <CodeEditor value={code} onChange={setCode} language={kind === 'python' ? 'python' : 'sql'} rows={7} />
      <div className="taskActions">
        <button className="primary" onClick={run}><Play size={16} /> Запустить в sandbox</button>
        <button className="ghost" onClick={submit} disabled={!answer.sandboxResult}><Check size={16} /> Проверить ответ</button>
      </div>
      {kind === 'python' ? <PythonResult result={result} /> : <SqlResult result={result} />}
    </div>
  );
}

function CodeEditor({ value, onChange, rows = 9 }) {
  return (
    <textarea
      className="codeInput"
      rows={rows}
      value={value || ''}
      onChange={e => onChange(e.target.value)}
      spellCheck={false}
    />
  );
}

function SqlResult({ result, compact = false }) {
  if (!result) return <p className="muted">Результат появится здесь</p>;
  if (result.detail) return <p className="errorText">{result.detail}</p>;

  return (
    <div className="resultTableWrap">
      {!compact && <div className="resultSummary">{result.count} строк</div>}
      <table className="resultTable">
        <thead>
          <tr>{result.columns.map(column => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          {result.rows.map((row, index) => (
            <tr key={index}>{result.columns.map(column => <td key={column}>{formatCell(row[column])}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PythonResult({ result }) {
  if (!result) return <p className="muted">Вывод появится здесь</p>;
  if (result.detail) return <p className="errorText">{result.detail}</p>;

  return (
    <div className="pythonOutput">
      <div className={result.exitCode === 0 ? 'exit ok' : 'exit fail'}>exit code {result.exitCode}</div>
      <pre>{result.stdout || 'stdout пуст'}</pre>
      {result.stderr && <pre className="stderr">{result.stderr}</pre>}
    </div>
  );
}

function CodeBlock({ code, language }) {
  return (
    <pre className="codePreview">
      <code>{highlightCode(code, language)}</code>
    </pre>
  );
}

function formatCell(value) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function selectedValues(answer) {
  return Object.entries(answer).filter(([, checked]) => checked).map(([id]) => id);
}

function taskButtonClass(task, currentTaskId, attemptsByTask) {
  const attempt = attemptsByTask[task.id];
  const classes = ['taskStep'];

  if (task.id === currentTaskId) classes.push('active');

  if (attempt) {
    const ratio = Number(attempt.score) / Math.max(1, Number(attempt.maxScore));
    if (ratio >= 0.8) classes.push('passedGood');
    else if (ratio >= 0.4) classes.push('passedWarn');
    else classes.push('passedBad');
  }

  return classes.join(' ');
}

function highlightCode(code, language) {
  const pattern = language === 'python'
    ? /(#.*$|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\b(?:import|from|as|print|def|return|if|else|elif|for|while|try|except|with|in|is|and|or|not|True|False|None)\b|\b\d+(?:\.\d+)?\b)/gm
    : /('(?:[^'\\]|\\.)*'|\b(?:select|from|where|join|left|right|inner|outer|on|group|by|order|limit|with|as|and|or|not|count|sum|avg|min|max|distinct|case|when|then|else|end|having|union|all|insert|update|delete|create|table|drop|null|is)\b|\b\d+(?:\.\d+)?\b)/gim;

  return String(code || '').split(pattern).filter(Boolean).map((part, index) => {
    const lower = part.toLowerCase();
    let className = '';

    if (part.startsWith('#')) className = 'tok-comment';
    else if (part.startsWith('"') || part.startsWith("'")) className = 'tok-str';
    else if (/^\d/.test(part)) className = 'tok-num';
    else if (/^(select|from|where|join|left|right|inner|outer|on|group|by|order|limit|with|as|and|or|not|count|sum|avg|min|max|distinct|case|when|then|else|end|having|union|all|insert|update|delete|create|table|drop|null|is|import|print|def|return|if|else|elif|for|while|try|except|with|in|true|false|none)$/.test(lower)) {
      className = 'tok-kw';
    }

    return className ? <span className={className} key={index}>{part}</span> : <React.Fragment key={index}>{part}</React.Fragment>;
  });
}

async function showSolution(taskId, token, setAnswer) {
  const task = await api(`/api/tasks/${taskId}/solution`, token);

  if (task.artifact?.sandbox?.solution) {
    setAnswer({ code: task.artifact.sandbox.solution });
    return;
  }

  if (task.type === 'OPEN') {
    setAnswer({ text: task.artifact?.solution || task.artifact?.context || '' });
    return;
  }

  if (task.type === 'TEST') {
    setAnswer(Object.fromEntries((task.correctAnswer?.correct || []).map(id => [id, true])));
    return;
  }

  if (task.type === 'ERROR_SEARCH') {
    setAnswer(Object.fromEntries((task.correctAnswer?.errors || []).map(id => [id, true])));
  }
}

function wrap(fn, setMessage) {
  return async () => {
    try {
      await fn();
    } catch (error) {
      setMessage(error.message);
    }
  };
}

async function publicApi(path, body) {
  const response = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });

  if (!response.ok) throw new Error((await response.json()).error || 'Request failed');
  return response.json();
}

async function api(path, token, body) {
  const headers = { 'Content-Type': 'application/json' };

  if (token && token !== 'cookie') {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_URL}${path}`, {
    method: body ? 'POST' : 'GET',
    headers,
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) throw new Error((await response.json()).error || 'Request failed');
  return response.json();
}

function hasAuthCookie() {
  return document.cookie.split(';').some(item => item.trim().startsWith('auth_token='));
}

createRoot(document.getElementById('root')).render(<App />);