FROM elementsproject/lightningd:v25.12.1

USER root

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates python3; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY harness.py /app/harness.py

EXPOSE 3021

ENTRYPOINT ["python3", "/app/harness.py"]
