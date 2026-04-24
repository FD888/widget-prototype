FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py data.py db.py schema.sql seed.py dashboard.html ./
COPY yandex_speech/ ./yandex_speech/

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
