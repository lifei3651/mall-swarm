#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "用法：$0 <源目录> <输出.tar.gz>" >&2
  exit 2
}

[[ "$#" == 2 ]] || usage
SOURCE_DIR=$1
OUTPUT_ARCHIVE=$2
[[ -d "$SOURCE_DIR" ]] || { echo "源目录不存在：$SOURCE_DIR" >&2; exit 1; }

SOURCE_DIR=$(cd "$SOURCE_DIR" && pwd)
OUTPUT_PARENT=$(dirname "$OUTPUT_ARCHIVE")
mkdir -p "$OUTPUT_PARENT"
OUTPUT_PARENT=$(cd "$OUTPUT_PARENT" && pwd)
OUTPUT_ARCHIVE="$OUTPUT_PARENT/$(basename "$OUTPUT_ARCHIVE")"

case "$OUTPUT_ARCHIVE" in
  "$SOURCE_DIR"/*)
    echo "输出包不能放在源目录内部" >&2
    exit 1
    ;;
esac

STAGING=$(mktemp -d "${TMPDIR:-/tmp}/lingqimall-clean-archive.XXXXXX")
cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT HUP INT TERM

mkdir -p "$STAGING/content"
cp -R "$SOURCE_DIR/." "$STAGING/content/"

# macOS 会给拷贝与归档附加 com.apple.* / AppleDouble 元数据；仅清理临时副本。
if command -v xattr >/dev/null 2>&1; then
  xattr -cr "$STAGING/content"
fi
if find "$STAGING/content" \( -name '._*' -o -name '__MACOSX' \) -print -quit | grep -q .; then
  echo "源目录包含 macOS 隐藏元数据，已停止封包" >&2
  exit 1
fi

export COPYFILE_DISABLE=1
tar_options=(-czf "$OUTPUT_ARCHIVE" -C "$STAGING/content" .)
if tar --help 2>&1 | grep -q -- '--no-xattrs'; then
  tar_options=(--no-xattrs "${tar_options[@]}")
fi
if tar --help 2>&1 | grep -q -- '--no-mac-metadata'; then
  tar_options=(--no-mac-metadata "${tar_options[@]}")
fi
tar "${tar_options[@]}"

LISTING="$STAGING/listing.txt"
ERRORS="$STAGING/tar-errors.txt"
tar -tzf "$OUTPUT_ARCHIVE" >"$LISTING" 2>"$ERRORS"
if grep -Eiq 'LIBARCHIVE\.xattr|SCHILY\.xattr|com\.apple' "$ERRORS" \
  || gzip -cd "$OUTPUT_ARCHIVE" 2>/dev/null | LC_ALL=C grep -aEq 'LIBARCHIVE\.xattr|SCHILY\.xattr|com\.apple\.'; then
  echo "归档仍包含扩展属性，已停止交付" >&2
  exit 1
fi
if grep -Eq '(^|/)\._|(^|/)__MACOSX(/|$)|(^|/)\.\.(/|$)|^/' "$LISTING"; then
  echo "归档包含危险路径或 macOS 隐藏文件，已停止交付" >&2
  exit 1
fi

echo "clean-archive-ok path=$OUTPUT_ARCHIVE"
