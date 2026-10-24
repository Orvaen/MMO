@echo off
rem Compile, lance les tests et produit build\orvaen-mmo.jar (Java 21+, aucune dependance).
setlocal enabledelayedexpansion
cd /d "%~dp0"
chcp 65001 >nul

if exist build rmdir /s /q build
mkdir build\classes build\test-classes

echo - Compilation
(for /r src\main\java %%f in (*.java) do (set "p=%%f" & echo "!p:\=/!")) > build\main-sources.txt
javac -encoding UTF-8 --release 21 -d build\classes @build\main-sources.txt || goto :error

echo - Tests
(for /r src\test\java %%f in (*.java) do (set "p=%%f" & echo "!p:\=/!")) > build\test-sources.txt
javac -encoding UTF-8 --release 21 -cp build\classes -d build\test-classes @build\test-sources.txt || goto :error
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp build\classes;build\test-classes dev.orvaen.mmo.SelfTest || goto :error

echo - Archive
jar --create --file build\orvaen-mmo.jar --main-class dev.orvaen.mmo.Main -C build\classes . || goto :error
echo OK : build\orvaen-mmo.jar - lance-le avec : java -jar build\orvaen-mmo.jar
exit /b 0

:error
echo ECHEC du build.
exit /b 1
