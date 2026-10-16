@echo off
set PATH=C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot\bin;%PATH%
cd /d "%~dp0"
if exist out rd /s /q out
mkdir out
for /r src %%f in (*.java) do echo %%f >> compile_list.tmp
javac -source 6 -target 6 -encoding UTF-8 -Xmaxerrs 2000 -d out @compile_list.tmp
set RC=%ERRORLEVEL%
del compile_list.tmp 2>nul
exit /b %RC%
