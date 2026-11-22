@echo off
setlocal
set VERSION=8.13
set BASE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%VERSION%-bin\nova-pill
set GRADLE=%BASE%\gradle-%VERSION%\bin\gradle.bat
if exist "%GRADLE%" goto run
if not exist "%BASE%" mkdir "%BASE%"
set ZIP=%BASE%\gradle-%VERSION%-bin.zip
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip -OutFile '%ZIP%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BASE%'"
del "%ZIP%"
:run
call "%GRADLE%" %*
endlocal
