#!/usr/bin/env bash
set -euo pipefail

while true; do
  mvn -q compile exec:java &
  app_pid=$!

  inotifywait -q -r -e modify,create,delete,move src pom.xml >/dev/null 2>&1 &
  watcher_pid=$!

  wait -n "$app_pid" "$watcher_pid" || true
  kill "$app_pid" >/dev/null 2>&1 || true
  kill "$watcher_pid" >/dev/null 2>&1 || true
  wait "$app_pid" >/dev/null 2>&1 || true
  wait "$watcher_pid" >/dev/null 2>&1 || true
  echo "Source changed, restarting backend..."
done
