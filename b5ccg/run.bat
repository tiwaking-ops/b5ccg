@echo off
set ROOT=%~dp0
set OUT=%ROOT%out
set MARKER=%OUT%\b5ccg-build-ok.txt

REM Auto-detect JDK 25 support. The scripts call bare `javac`, and the
REM default PATH on this host has JDK 8, which rejects --release. If the
REM default javac cannot build, prepend the staged JDK 25 before delegating.
javac --release 8 -version >nul 2>&1
if errorlevel 1 (
  if exist "%ROOT%scratch\jdk25-win\jdk-25.0.4.1+1\bin\javac.exe" (
    set "PATH=%ROOT%scratch\jdk25-win\jdk-25.0.4.1+1\bin;%PATH%"
  )
)

REM A build is trustworthy only when the marker exists. A stale or partial
REM build leaves out/ present with b5ccg/model/AsylumCharacterCard.class and
REM the card resources missing, so existence of out/ alone is not proof the
REM tree compiled cleanly. compile.bat/compile.sh clean out/ first and always
REM write the marker last, so if the marker is missing we delegate to the
REM compile scripts (which re-build from scratch) instead of running a
REM half-built out/ as if it were a good build.
if not exist "%MARKER%" (
  echo Project not built or build is stale. Running compile.bat first...
  call "%ROOT%compile.bat"
  if errorlevel 1 exit /b 1
)

java -cp "%OUT%" b5ccg.Main %*
