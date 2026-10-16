@echo off
REM Compile the B5 CCG Java project (JDK 25 --release 8, stdlib only; Hop 1 per B5-0007/D2)
setlocal enabledelayedexpansion

REM Resolve the script directory so it runs from any CWD and always targets
REM the b5ccg/ tree below (see compile.sh, B5-0306). %~dp0 = b5ccg/.
cd /d "%~dp0" || goto :eof

REM Relative roots so the accumulated file list stays well under the
REM cmd.exe 8,191-char command-line ceiling. The previous version built a
REM single variable of QUOTED ABSOLUTE paths (~12,966 chars on this tree).
REM The command line silently truncated the list and javac received a
REM mangled/empty list, so compile.bat printed "Build successful" while
REM producing ZERO class files. 146 files x ~30 chars (relative) =~4,400
REM chars fits with margin.
set SRC=src
set OUT=out
set RES=resources

echo === B5 CCG Build ===

REM B5-3487: clean out/ BEFORE compiling. javac writes a .class for everything
REM that compiled before it hits the first error batch, so a red build leaves a
REM PARTIAL out/ behind -- 77 class files for 146 sources, with b5ccg/engine/
REM DeckLoader among the missing. run.bat then guards on "does out/ exist", sees
REM it exists, skips the build and runs the partial one, dying at runtime with
REM NoClassDefFoundError instead of reporting the compile error that caused it.
REM The stale-output state is indistinguishable from a good build by existence
REM alone; it is only distinguishable by never having been allowed to persist.
if exist "%OUT%" rd /s /q "%OUT%"
if exist "%OUT%" (
  echo ERROR: could not remove %OUT% - it is probably locked by a running game.
  exit /b 1
)
mkdir "%OUT%"

REM Count the .java files first (a separate pass keeps the counter out of
REM the redirected block that writes the response file).
set N=0
for /r "%SRC%" %%f in (*.java) do set /a N+=1
if %N% equ 0 (
  echo ERROR: No .java files found.
  exit /b 1
)

REM Collect all .java files (relative paths, one per line) into a response
REM file so the list can exceed the command-line ceiling without trimming
REM javac's argument list.
set LIST=%TEMP%\b5ccg_compile_list.tmp
REM B5-0001: quote each path AND slash the backslashes — %%~f expands to a
REM FULL path, and javac @-files treat backslash as an escape (measured: the
REM quoted attempt still failed as "file not found: C:UsersTiwa Pene...").
REM Forward slashes are accepted by Windows javac and need no escaping.
> "%LIST%" (
  for /r "%SRC%" %%f in (*.java) do (
    set "P=%%~f"
    setlocal enabledelayedexpansion
    echo "!P:\=/!"
    endlocal
  )
)

if not exist "%LIST%" (
  echo ERROR: No .java files found.
  exit /b 1
)

echo Compiling source files...
REM B5-3487: -Xmaxerrs 2000 so a failure reports every error rather than the
REM first 100. javac's DEFAULT cap silently truncated the tree's 248 errors to
REM "100 errors in 13 files", which is how B5-3428 recorded two runs as gate-
REM consistent when both were equally truncated: the two builds agreed because
REM they both stopped at the same cap, not because they saw the same tree.
REM A capped count is a floor, never a total -- read the exit code, and treat any
REM error count printed without -Xmaxerrs as a lower bound.
javac --release 8 -encoding UTF-8 -Xmaxerrs 2000 -d "%OUT%" @"%LIST%"
if errorlevel 1 (
  del /f /q "%LIST%" 2>nul
  REM Leave no half-written output behind for run.bat to mistake for a build.
  rd /s /q "%OUT%" 2>nul
  echo Compilation failed. out\ removed so no partial build can be run.
  exit /b 1
)

echo Copying resources...
REM B5-3641: the resource copy is now a GATED step, not the fire-and-forget it
REM was at the former line 77. B5-3631 measured three silent-green shapes on that
REM bare line, each of which still printed "Build successful" and still wrote the
REM marker below:
REM   (a) empty resources   -- xcopy exits 0 having copied 0 files;
REM   (b) missing resources -- the old `if exist "%RES%"` was false so the copy
REM                            was simply SKIPPED, still exit 0;
REM   (c) ACL hard failure  -- xcopy exits 4 while the marker was STILL written.
REM run.bat consumes only b5ccg-build-ok.txt as the evidence that out/ is
REM complete, so a marker over an incomplete resource set is exactly the
REM silent-green B5-1725 recorded. errorlevel alone cannot prove completion
REM (case (a) copies nothing yet exits 0), so each of the three known resources
REM is checked by existence AFTER the copy. Every failure path mirrors the
REM javac-failure branch above -- rd /s /q out/, exit /b 1 -- so no marker-stamped
REM partial out/ can survive for run.bat to mistake for a good build.
if not exist "%RES%\\" (
  echo ERROR: resources directory "%RES%" not found - refusing to write the build marker.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
xcopy /E /Y /Q "%RES%\*" "%OUT%\"
if errorlevel 1 (
  echo ERROR: resource copy failed - xcopy exit code 1 or higher. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\deluxe.json" (
  echo ERROR: cards\deluxe.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\premiere.json" (
  echo ERROR: cards\premiere.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\index.json" (
  echo ERROR: cards\index.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\decks\premiere-starter-decks.json" (
  echo ERROR: decks\premiere-starter-decks.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)

REM B5-3487: written LAST, after javac succeeded and resources landed. run.bat
REM treats this marker as the only trustworthy evidence that out/ is complete,
REM which the mere existence of out/ is not. B5-3641 added the gates above so
REM that "resources landed" is now PROVEN rather than assumed.
REM NOTE the explicit backslash after %OUT%: it is defined as `out` with NO
REM trailing separator, so `%OUT%marker` would concatenate into a sibling FILE
REM named `outmarker` rather than a file inside out/. Measured: every marker
REM probe wrote beside the directory, never into it, and out/ stayed unmarked.
echo ok> "%OUT%\b5ccg-build-ok.txt"

REM B5-0016 (b): the response file is scratch. Delete it on the success path
REM too (the javac-failure branch above already deletes it), so no stale file
REM list survives the build in any directory.
if exist "%LIST%" del /f /q "%LIST%" 2>nul

echo.
echo Build successful. Run with: run.bat

REM ── Verification gate: parity with compile.sh (B5-0967, 2026-09-28) ──────
REM This script has NO native test branch BY DECISION: the test selection
REM lives only in compile.sh's RUN_TESTS=1 branch, so the gate list is
REM decided in exactly one file (B5-0956 owns which Headless classes gate).
REM The full verified build on this platform (every agent builds through
REM Git Bash) is:
REM   RUN_TESTS=1 sh compile.sh
REM (cmd.exe without a POSIX shell: `set RUN_TESTS=1` then `sh compile.sh`
REM  via Git Bash; a plain `compile.bat` remains the fast compile-only gate.)
REM Measured 2026-09-28: RUN_TESTS=1 sh compile.sh exits 0 end-to-end here.

endlocal
