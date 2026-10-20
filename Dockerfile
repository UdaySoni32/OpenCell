# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS jdk
FROM ghcr.io/astral-sh/uv:0.12.18 AS uv
FROM python:3.14.7-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl unzip make git build-essential zlib1g \
    && rm -rf /var/lib/apt/lists/*
COPY --from=jdk /opt/java/openjdk /opt/java/openjdk
COPY --from=uv /uv /usr/local/bin/uv

ENV JAVA_HOME=/opt/java/openjdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/workspace/.cache/gradle \
    UV_PROJECT_ENVIRONMENT=/workspace/cli/.venv \
    UV_CACHE_DIR=/workspace/.cache/uv \
    UV_PYTHON_DOWNLOADS=never
ENV PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/19.0/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Fixed Google command-line tools archive (19.0); no moving "latest" SDK lookup.
# SHA-1 published in Google's repository2-1.xml for this exact Linux archive.
RUN curl --fail --location --retry 3 \
      https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
      --output /tmp/android-tools.zip \
    && printf '%s\n' '5fdcc763663eefb86a5b8879697aa6088b041e70  /tmp/android-tools.zip' | sha1sum --check - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/19.0" \
    && rm -rf /tmp/android-tools /tmp/android-tools.zip \
    && yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses >/dev/null \
    && sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;android-36" "build-tools;36.0.0" "platform-tools"

WORKDIR /workspace
COPY . .
RUN make setup && make test && make build

ENTRYPOINT ["/workspace/cli/.venv/bin/opencell"]
CMD ["--help"]
