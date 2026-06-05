@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul 2>&1

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "APP_NAME=zestflow-admin"
set "CONFIG_DIR=%SCRIPT_DIR%\config"
set "LOG_DIR=%SCRIPT_DIR%\log"
set "ENV_FILE=%CONFIG_DIR%\start-admin.env"

if exist "%ENV_FILE%" (
  for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
    set "line=%%a"
    if not "!line!"=="" if not "!line:~0,1!"=="#" set "%%a=%%b"
  )
)

if not defined SPRING_PROFILE set "SPRING_PROFILE=prod"
if not defined JVM_XMS set "JVM_XMS=512m"
if not defined JVM_XMX set "JVM_XMX=768m"
if not defined APP_TIMEZONE set "APP_TIMEZONE=Asia/Shanghai"

call :load_secrets

if "%~1"=="" goto do_restart
if /i "%~1"=="start" goto do_start
if /i "%~1"=="stop" goto do_stop
if /i "%~1"=="restart" goto do_restart
goto usage

:load_secrets
if exist "%CONFIG_DIR%\secret" for /f "usebackq delims=" %%s in ("%CONFIG_DIR%\secret") do set "ZESTFLOW_JWT_SECRET=%%s"
if exist "%CONFIG_DIR%\registry-token" for /f "usebackq delims=" %%s in ("%CONFIG_DIR%\registry-token") do set "ZESTFLOW_ADMIN_REGISTRY_TOKEN=%%s"
if exist "%CONFIG_DIR%\executor-access-token" for /f "usebackq delims=" %%s in ("%CONFIG_DIR%\executor-access-token") do set "ZESTFLOW_ADMIN_EXECUTOR_ACCESS_TOKEN=%%s"
if exist "%CONFIG_DIR%\collector.access-token" for /f "usebackq delims=" %%s in ("%CONFIG_DIR%\collector.access-token") do set "ZESTFLOW_COLLECTOR_ACCESS_TOKEN=%%s"
if exist "%CONFIG_DIR%\bootstrap-admin.password" for /f "usebackq delims=" %%s in ("%CONFIG_DIR%\bootstrap-admin.password") do set "ZESTFLOW_ADMIN_DEFAULT_USER_PASSWORD=%%s"
exit /b 0

:find_jar
set "JAR_FILE="
for /f "delims=" %%j in ('dir /b /o-n "%SCRIPT_DIR%\%APP_NAME%-*.jar" 2^>nul') do (
  set "JAR_FILE=%SCRIPT_DIR%\%%j"
  goto jar_ok
)
:jar_ok
if not defined JAR_FILE (
  echo [FAIL] 未找到 %APP_NAME%-*.jar
  pause
  exit /b 1
)
exit /b 0

:do_stop
for /f "tokens=2" %%p in ('wmic process where "CommandLine like '%%zestflow-admin%%.jar%%'" get ProcessId /format:list 2^>nul ^| findstr "="') do (
  echo 停止 PID %%p
  taskkill /PID %%p /T /F >nul 2>&1
)
exit /b 0

:do_restart
call :do_stop
timeout /t 2 /nobreak >nul

:do_start
call :find_jar || exit /b 1
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
where java >nul 2>&1 || (
  echo [FAIL] 未找到 java
  pause
  exit /b 1
)

set "ARGS=--logging.file.path=%LOG_DIR% --spring.config.additional-location=file:%CONFIG_DIR%/"
if defined SPRING_PROFILE set "ARGS=%ARGS% --spring.profiles.active=%SPRING_PROFILE%"

java -Xms%JVM_XMS% -Xmx%JVM_XMX% -Dfile.encoding=UTF-8 -Duser.timezone=%APP_TIMEZONE% -jar "!JAR_FILE!" !ARGS!
set "RC=!ERRORLEVEL!"
echo.
echo 进程已退出 code=!RC!
pause
exit /b !RC!

:usage
echo 用法: start-admin.bat [start^|stop^|restart]
echo   双击 = restart，Spring Boot 日志直接输出在本窗口
pause
exit /b 1
