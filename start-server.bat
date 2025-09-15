@echo off
setlocal

:: ---------------------------
:: Config — EDIT if necessary
:: ---------------------------
set "KAFKA_HOME=E:\Project\AI_DSA_Analyzer\PipelineHelper\kafka"
set "REDIS_HOME=E:\Project\AI_DSA_Analyzer\PipelineHelper\redis"

:: These are the directories you said you wanted
set "KAFKA_LOG_DIR=E:\Project\AI_DSA_Analyzer\PipelineHelper\kafka-logs"
set "ZK_DATA_DIR=E:\Project\AI_DSA_Analyzer\PipelineHelper\zookeeper-logs"

set "KAFKA_CONFIG=%KAFKA_HOME%\config\server.properties"
set "ZK_CONFIG=%KAFKA_HOME%\config\zookeeper.properties"

:: ---------------------------
:: Basic checks
:: ---------------------------
echo Checking Java on PATH...
where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: java not found on PATH. Install JDK and add java to PATH or edit this script to point to java.exe.
  pause
  exit /b 1
)

:: ---------------------------
:: Cleanup old data/logs
:: ---------------------------
echo.
echo Cleaning Kafka logs: "%KAFKA_LOG_DIR%"
if exist "%KAFKA_LOG_DIR%" (
  echo Removing "%KAFKA_LOG_DIR%" ...
  rd /s /q "%KAFKA_LOG_DIR%" 2>nul
)
mkdir "%KAFKA_LOG_DIR%" 2>nul

echo Cleaning ZooKeeper data: "%ZK_DATA_DIR%"
if exist "%ZK_DATA_DIR%" (
  echo Removing "%ZK_DATA_DIR%" ...
  rd /s /q "%ZK_DATA_DIR%" 2>nul
)
mkdir "%ZK_DATA_DIR%" 2>nul

:: also remove any kafka zookeeper-data inside Kafka distribution (some setups use this)
if exist "%KAFKA_HOME%\zookeeper-data" (
  echo Removing "%KAFKA_HOME%\zookeeper-data" ...
  rd /s /q "%KAFKA_HOME%\zookeeper-data" 2>nul
)

:: small pause so filesystem settles (helps avoid "in use" issues)
ping -n 2 127.0.0.1 >nul

:: ---------------------------
:: Start ZooKeeper
:: ---------------------------
echo.
echo Starting ZooKeeper in a new window...
:: Use Java directly with wildcard classpath (avoids the bat that expands all jars)
start "ZooKeeper" cmd /k java -cp "%KAFKA_HOME%\libs\*;%KAFKA_HOME%\config" org.apache.zookeeper.server.quorum.QuorumPeerMain "%ZK_CONFIG%"

:: give ZK a bit of time to bind
echo Waiting 10 seconds for ZooKeeper to initialize...
timeout /t 10 >nul

:: ---------------------------
:: Start Kafka
:: ---------------------------
echo.
echo Starting Kafka broker in a new window...
start "Kafka" cmd /k java -Xmx1G -Xms512M -cp "%KAFKA_HOME%\libs\*;%KAFKA_HOME%\config" kafka.Kafka "%KAFKA_CONFIG%"

:: ---------------------------
:: Start Redis
:: ---------------------------
echo.
echo Starting Redis in a new window...
if exist "%REDIS_HOME%\redis-server.exe" (
  start "Redis" "%REDIS_HOME%\redis-server.exe" "%REDIS_HOME%\redis.windows.conf"
) else (
  echo WARNING: redis-server.exe not found at "%REDIS_HOME%". Please check REDIS_HOME in the script.
)

echo.
echo ===============================
echo All start commands issued.
echo Check the separate windows for ZooKeeper, Kafka and Redis logs.
echo ===============================
pause
endlocal
