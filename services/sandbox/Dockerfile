FROM docker.io/python:3.12-slim

RUN pip install --no-cache-dir numpy pandas matplotlib sympy scipy && \
    rm -rf /root/.cache

USER nobody
WORKDIR /tmp

CMD ["python3", "-c", "print('sandbox ready')"]
