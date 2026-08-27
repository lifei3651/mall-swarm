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
# macOS 自带 bsdtar 支持这些选项，但帮助文本不一定列出；用空目录实际探测，
# 避免因帮助文本缺项而把 provenance 等 PAX 扩展属性带入交付包。
mkdir -p "$STAGING/tar-option-probe"
if tar --no-xattrs --no-mac-metadata -cf "$STAGING/tar-option-probe.tar" \
  -C "$STAGING/tar-option-probe" . >/dev/null 2>&1; then
  tar_options=(--no-xattrs --no-mac-metadata "${tar_options[@]}")
else
  if tar --no-xattrs -cf "$STAGING/tar-option-probe-xattrs.tar" \
    -C "$STAGING/tar-option-probe" . >/dev/null 2>&1; then
    tar_options=(--no-xattrs "${tar_options[@]}")
  fi
  if tar --no-mac-metadata -cf "$STAGING/tar-option-probe-mac.tar" \
    -C "$STAGING/tar-option-probe" . >/dev/null 2>&1; then
    tar_options=(--no-mac-metadata "${tar_options[@]}")
  fi
fi
tar "${tar_options[@]}"

LISTING="$STAGING/listing.txt"
ERRORS="$STAGING/tar-errors.txt"
tar -tzf "$OUTPUT_ARCHIVE" >"$LISTING" 2>"$ERRORS"
if ! python3 - "$OUTPUT_ARCHIVE" <<'PY'
import sys
import tarfile

with tarfile.open(sys.argv[1], "r:gz") as archive:
    global_headers = getattr(archive, "pax_headers", {})
    if any("xattr" in key.lower() or "com.apple" in key.lower() for key in global_headers):
        raise SystemExit(1)
    for member in archive.getmembers():
        headers = getattr(member, "pax_headers", {})
        if any("xattr" in key.lower() or "com.apple" in key.lower() for key in headers):
            raise SystemExit(1)
PY
then
  echo "归档仍包含扩展属性，已停止交付" >&2
  exit 1
fi
if grep -Eq '(^|/)\._|(^|/)__MACOSX(/|$)|(^|/)\.\.(/|$)|^/' "$LISTING"; then
  echo "归档包含危险路径或 macOS 隐藏文件，已停止交付" >&2
  exit 1
fi

echo "clean-archive-ok path=$OUTPUT_ARCHIVE"
