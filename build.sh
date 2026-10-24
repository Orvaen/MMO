#!/usr/bin/env sh
# Compile, lance les tests et produit build/orvaen-mmo.jar (Java 21+, aucune dépendance).
set -e
cd "$(dirname "$0")"

rm -rf build
mkdir -p build/classes build/test-classes

echo "▸ Compilation"
javac -encoding UTF-8 --release 21 -d build/classes $(find src/main/java -name '*.java')

echo "▸ Tests"
javac -encoding UTF-8 --release 21 -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp build/classes:build/test-classes dev.orvaen.mmo.SelfTest

echo "▸ Archive"
jar --create --file build/orvaen-mmo.jar --main-class dev.orvaen.mmo.Main -C build/classes .
echo "✔ build/orvaen-mmo.jar — lance-le avec : java -jar build/orvaen-mmo.jar"
