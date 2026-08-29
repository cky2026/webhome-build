#!/bin/bash
set -e

SRC_DIR=sources
OUT_DIR=out
DEX_DIR=dex
PLATFORM=$ANDROID_SDK_ROOT/platforms/android-33/android.jar
BT=$ANDROID_SDK_ROOT/build-tools/33.0.2

echo "=== 1. 编译 Java 源码 ==="
mkdir -p $OUT_DIR $DEX_DIR
rm -f $OUT_DIR/*.class 2>/dev/null || true
find $SRC_DIR -name "*.java" | sort > sources.txt
cat sources.txt
javac -cp "$PLATFORM" -d $OUT_DIR @sources.txt

echo "=== 2. 列出编译出的 class 文件 ==="
find $OUT_DIR -name "*.class" | sort > classes.txt
cat classes.txt

echo "=== 3. 转换为 dex ==="
$BT/d8 --release --lib $PLATFORM --min-api 21 --output $DEX_DIR @classes.txt
ls -la $DEX_DIR

if [ ! -f $DEX_DIR/classes.dex ]; then
  echo "!!! 错误：未生成 classes.dex"
  exit 1
fi

echo "=== 4. 打包 jar ==="
mkdir -p META-INF
printf "Manifest-Version: 1.0\nCreated-By: WebHome Build 1.0\n" > META-INF/MANIFEST.MF
rm -f webhome.jar
jar cfm webhome.jar META-INF/MANIFEST.MF -C $DEX_DIR .

echo "=== 5. 验证 jar 内容 ==="
unzip -l webhome.jar
$BT/dexdump $DEX_DIR/classes.dex | grep "Class descriptor" || true

echo "Build OK -> webhome.jar"
