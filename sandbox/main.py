import os
import subprocess
import tempfile
from pathlib import Path

import psycopg2
import psycopg2.extras
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel


DATABASE_URL = os.getenv("SANDBOX_DATABASE_URL", "postgresql://analytics_sandbox:sandbox@localhost:5432/analytics_sandbox")

app = FastAPI(title="Analytics Trainer Sandbox", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class SqlRequest(BaseModel):
    query: str
    limit: int = 100


class PythonRequest(BaseModel):
    script: str


SEED_SQL = """
CREATE TABLE IF NOT EXISTS customers (
    customer_id int PRIMARY KEY,
    full_name text NOT NULL,
    city text NOT NULL,
    signup_date date NOT NULL,
    acquisition_channel text NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id int PRIMARY KEY,
    customer_id int NOT NULL REFERENCES customers(customer_id),
    order_date date NOT NULL,
    status text NOT NULL,
    amount numeric(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    event_id int PRIMARY KEY,
    customer_id int NOT NULL REFERENCES customers(customer_id),
    event_time timestamptz NOT NULL,
    event_name text NOT NULL,
    device text NOT NULL
);

TRUNCATE events, orders, customers RESTART IDENTITY;

INSERT INTO customers(customer_id, full_name, city, signup_date, acquisition_channel) VALUES
(1, 'Анна Морозова', 'Москва', '2026-01-05', 'organic'),
(2, 'Илья Соколов', 'Казань', '2026-01-12', 'ads'),
(3, 'Мария Волкова', 'Санкт-Петербург', '2026-02-02', 'referral'),
(4, 'Павел Егоров', 'Москва', '2026-02-10', 'ads'),
(5, 'Ольга Орлова', 'Екатеринбург', '2026-03-03', 'organic'),
(6, 'Денис Ким', 'Новосибирск', '2026-03-18', 'partner'),
(7, 'Нина Лебедева', 'Казань', '2026-04-01', 'organic'),
(8, 'Роман Зайцев', 'Москва', '2026-04-11', 'referral');

INSERT INTO orders(order_id, customer_id, order_date, status, amount) VALUES
(101, 1, '2026-01-07', 'paid', 2400.00),
(102, 1, '2026-02-14', 'paid', 1800.00),
(103, 2, '2026-01-20', 'cancelled', 3100.00),
(104, 2, '2026-03-02', 'paid', 4500.00),
(105, 3, '2026-02-08', 'paid', 1200.00),
(106, 4, '2026-02-18', 'paid', 8700.00),
(107, 4, '2026-03-22', 'refunded', 2200.00),
(108, 5, '2026-03-09', 'paid', 990.00),
(109, 6, '2026-03-25', 'paid', 6200.00),
(110, 7, '2026-04-03', 'paid', 1750.00),
(111, 8, '2026-04-20', 'cancelled', 2600.00),
(112, 8, '2026-04-24', 'paid', 3900.00);

INSERT INTO events(event_id, customer_id, event_time, event_name, device) VALUES
(1001, 1, '2026-01-06 09:20:00+03', 'registration', 'desktop'),
(1002, 1, '2026-01-07 10:05:00+03', 'first_purchase', 'desktop'),
(1003, 2, '2026-01-12 18:40:00+03', 'registration', 'mobile'),
(1004, 2, '2026-03-02 12:10:00+03', 'first_purchase', 'mobile'),
(1005, 3, '2026-02-02 14:00:00+03', 'registration', 'mobile'),
(1006, 3, '2026-02-08 16:30:00+03', 'first_purchase', 'mobile'),
(1007, 4, '2026-02-10 11:00:00+03', 'registration', 'desktop'),
(1008, 4, '2026-02-18 11:45:00+03', 'first_purchase', 'desktop'),
(1009, 5, '2026-03-03 08:15:00+03', 'registration', 'mobile'),
(1010, 6, '2026-03-18 19:00:00+03', 'registration', 'desktop'),
(1011, 6, '2026-03-25 20:20:00+03', 'first_purchase', 'desktop'),
(1012, 7, '2026-04-01 12:00:00+03', 'registration', 'mobile'),
(1013, 8, '2026-04-11 13:20:00+03', 'registration', 'mobile'),
(1014, 8, '2026-04-24 10:10:00+03', 'first_purchase', 'mobile');
"""


@app.on_event("startup")
def prepare_database():
    with psycopg2.connect(DATABASE_URL) as connection:
        connection.autocommit = True
        with connection.cursor() as cursor:
            cursor.execute(SEED_SQL)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/metadata")
def metadata():
    tables = [
        {"name": "customers", "description": "Демо-клиенты и каналы привлечения"},
        {"name": "orders", "description": "Демо-заказы, статусы и суммы"},
        {"name": "events", "description": "Демо-события воронки регистрации и покупки"},
    ]
    with psycopg2.connect(DATABASE_URL) as connection:
        connection.set_session(readonly=True, autocommit=True)
        with connection.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
            for table in tables:
                cursor.execute(
                    """
                    SELECT column_name, data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = %s
                    ORDER BY ordinal_position
                    """,
                    (table["name"],),
                )
                table["columns"] = cursor.fetchall()
                cursor.execute(f"SELECT * FROM {table['name']} ORDER BY 1 LIMIT 5")
                table["sampleRows"] = cursor.fetchall()
    return {
        "database": "analytics_sandbox",
        "tables": tables,
        "examples": [
            "select city, count(*) as customers from customers group by city order by customers desc",
            "select status, sum(amount) as revenue from orders group by status",
            "select date_trunc('month', order_date) as month, sum(amount) from orders where status = 'paid' group by 1 order by 1",
        ],
    }


@app.post("/sql")
def run_sql(request: SqlRequest):
    query = request.query.strip()
    if not query:
        raise HTTPException(status_code=400, detail="Query is required")
    lowered = query.lower()
    if not (lowered.startswith("select") or lowered.startswith("with") or lowered.startswith("explain")):
        raise HTTPException(status_code=400, detail="Sandbox allows only SELECT, WITH and EXPLAIN queries")
    if ";" in query.rstrip(";"):
        raise HTTPException(status_code=400, detail="Only one statement is allowed")

    limited_query = query.rstrip(";")
    if lowered.startswith(("select", "with")) and " limit " not in lowered:
        limited_query = f"{limited_query} LIMIT {max(1, min(request.limit, 500))}"

    try:
        with psycopg2.connect(DATABASE_URL) as connection:
            connection.set_session(readonly=True, autocommit=True)
            with connection.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                cursor.execute(limited_query)
                rows = cursor.fetchall()
                columns = list(rows[0].keys()) if rows else [desc.name for desc in cursor.description or []]
                return {"columns": columns, "rows": rows, "count": len(rows), "query": limited_query}
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/python")
def run_python(request: PythonRequest):
    if not request.script.strip():
        raise HTTPException(status_code=400, detail="Script is required")
    prelude = """
import pandas as pd
customers = pd.DataFrame([
    {"customer_id": 1, "city": "Москва", "channel": "organic"},
    {"customer_id": 2, "city": "Казань", "channel": "ads"},
    {"customer_id": 3, "city": "Санкт-Петербург", "channel": "referral"},
    {"customer_id": 4, "city": "Москва", "channel": "ads"},
    {"customer_id": 5, "city": "Екатеринбург", "channel": "organic"},
])
orders = pd.DataFrame([
    {"order_id": 101, "customer_id": 1, "status": "paid", "amount": 2400.0},
    {"order_id": 102, "customer_id": 1, "status": "paid", "amount": 1800.0},
    {"order_id": 103, "customer_id": 2, "status": "cancelled", "amount": 3100.0},
    {"order_id": 104, "customer_id": 2, "status": "paid", "amount": 4500.0},
    {"order_id": 105, "customer_id": 3, "status": "paid", "amount": 1200.0},
    {"order_id": 106, "customer_id": 4, "status": "paid", "amount": 8700.0},
])
"""
    with tempfile.TemporaryDirectory() as tmp:
        script_path = Path(tmp) / "analysis.py"
        script_path.write_text(prelude + "\n" + request.script, encoding="utf-8")
        try:
            completed = subprocess.run(
                ["python", str(script_path)],
                cwd=tmp,
                capture_output=True,
                text=True,
                timeout=5,
                env={"PYTHONPATH": tmp, "PATH": os.getenv("PATH", "")},
            )
            return {
                "stdout": completed.stdout[-5000:],
                "stderr": completed.stderr[-5000:],
                "exitCode": completed.returncode,
            }
        except subprocess.TimeoutExpired as exc:
            raise HTTPException(status_code=408, detail="Python script timed out") from exc
