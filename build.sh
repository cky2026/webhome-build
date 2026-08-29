#!/bin/bash
set -e

SRC_DIR=sources
OUT_DIR=out
DEX_DIR=dex
PLATFORM=$ANDROID_SDK_ROOT/platforms/android-33/android.jar
BT=$ANDROID_SDK_ROOT/build-tools/33.0.2

mkdir -p $OUT_DIR $DEX_DIR

find $SRC_DIR -name "*.java" > sources.txt
javac -cp "$PLATFORM" -d $OUT_DIR @sources.txt

find $OUT_DIR -name "*.class" > classes.txt
$BT/d8 --lib $PLATFORM --min-api 21 --output $DEX_DIR @classes.txt

mkdir -p META-INF
printf "Manifest-Version: 1.0\nCreated-By: WebHome Build 1.0\n" > META-INF/MANIFEST.MF
jar cfm webhome.jar META-INF/MANIFEST.MF -C $DEX_DIR .
echo "Build OK -> webhome.jar"
