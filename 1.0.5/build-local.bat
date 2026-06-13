@echo off
setlocal
cd /d "%~dp0"

set "ENV_ROOT=%~dp0.codex-runclient-env"
if not exist "%ENV_ROOT%\gradle" mkdir "%ENV_ROOT%\gradle"
if not exist "%ENV_ROOT%\tmp" mkdir "%ENV_ROOT%\tmp"
if not exist "%ENV_ROOT%\userhome" mkdir "%ENV_ROOT%\userhome"
if not exist "%ENV_ROOT%\appdata" mkdir "%ENV_ROOT%\appdata"
if not exist "%ENV_ROOT%\localappdata" mkdir "%ENV_ROOT%\localappdata"

set "JAVA_HOME=E:\Program Files\Java"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%ENV_ROOT%\gradle"
set "TEMP=%ENV_ROOT%\tmp"
set "TMP=%ENV_ROOT%\tmp"
set "USERPROFILE=%ENV_ROOT%\userhome"
set "APPDATA=%ENV_ROOT%\appdata"
set "LOCALAPPDATA=%ENV_ROOT%\localappdata"
set "JAVA_TOOL_OPTIONS=-Duser.home=%USERPROFILE% -Djava.io.tmpdir=%TEMP%"

call "%~dp0gradlew.bat" build --no-daemon %*
exit /b %ERRORLEVEL%
