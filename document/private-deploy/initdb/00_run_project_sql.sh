#!/bin/sh
set -e

run_sql_dir() {
  dir="$1"
  if [ ! -d "$dir" ]; then
    return
  fi
  find "$dir" -maxdepth 1 -type f -name '*.sql' | sort | while read -r file; do
    run_sql_file "$file"
  done
}

run_sql_file() {
  file="$1"
  if [ -f "$file" ]; then
    echo "running init sql: $file"
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < "$file"
  fi
}

run_sql_dir /init-sql/base
run_sql_file /init-sql/distribution/distribution.sql
find /init-sql/distribution -maxdepth 1 -type f -name '*.sql' ! -name 'distribution.sql' | sort | while read -r file; do
  run_sql_file "$file"
done
