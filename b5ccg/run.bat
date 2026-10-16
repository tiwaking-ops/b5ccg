@echo off
set ROOT=%~dp0
set OUT=%ROOT%out
set MARKER=%OUT%\b5ccg-build-ok.txt

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
