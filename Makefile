SHELL := /bin/sh
.DEFAULT_GOAL := help

UV ?= uv
PYTHON_VERSION := 3.14.7
export UV_PROJECT_ENVIRONMENT ?= $(CURDIR)/cli/.venv
export UV_CACHE_DIR ?= $(CURDIR)/.cache/uv
export UV_PYTHON_INSTALL_DIR ?= $(CURDIR)/.tools/python
export GRADLE_USER_HOME ?= $(CURDIR)/.cache/gradle
GRADLE := ./gradlew --no-daemon --console=plain

.PHONY: help setup sync test build run
help:
	@printf '%s\n' 'make setup  Download locked dependencies, run tests, and build (online)' 'make test   Android JVM tests and Python pytest (offline, after setup)' 'make build  Debug APK and Python wheel/sdist (offline, after setup)' 'make run    Run the CLI; pass ARGS="--help" or another command'

setup:
	$(UV) sync --project cli --python $(PYTHON_VERSION) --locked --group dev
	$(UV) build --project cli --python $(PYTHON_VERSION) --out-dir cli/dist
	$(GRADLE) :app:testDebugUnitTest :app:assembleDebug
	$(UV) run --directory cli --python $(PYTHON_VERSION) --offline --no-sync python -m pytest

sync: setup

test:
	$(GRADLE) --offline :app:testDebugUnitTest
	$(UV) run --project cli --group dev --offline --no-sync pytest cli/tests

build:
	$(GRADLE) --offline :app:assembleDebug
	$(UV) build --project cli --python $(PYTHON_VERSION) --offline --out-dir cli/dist

run:
	$(UV) run --directory cli --python $(PYTHON_VERSION) --offline --no-sync opencell $(ARGS)
