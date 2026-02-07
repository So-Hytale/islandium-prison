@echo off
echo === Building islandium-prison ===
cd /d "%~dp0.."
call gradlew.bat :islandium-prison:shadowJar %*
if %ERRORLEVEL% EQU 0 (
    echo.
    echo === BUILD OK ===
    echo JAR: %~dp0build\libs\islandium-prison-1.0.0.jar
    copy /Y "%~dp0build\libs\islandium-prison-1.0.0.jar" "%~dp0.._output\islandium-prison-1.0.0.jar" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo Copie dans _output/ OK
    )
) else (
    echo.
    echo === BUILD FAILED ===
)
