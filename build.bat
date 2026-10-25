@echo off
set VERSION=26.10.25.3
echo Building XSprint v%VERSION%...
call mvn clean package -q
if %ERRORLEVEL% equ 0 (
    if not exist "build" mkdir build
    copy "target\XSprint-%VERSION%.jar" "build\" >nul
    echo Build successful! Output: build\XSprint-%VERSION%.jar
) else (
    echo Build failed!
    exit /b 1
)
