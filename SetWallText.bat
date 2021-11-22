@echo off
echo Executing GoogleWallpaper...

rem change to directory of batch file
cd /d %~dp0

java -cp ".;.\json.jar;.\jna-5.8.0.jar;.\jna-platform-5.8.0.jar;.\win32-x86-64.jar;" SetWallpaperShowtext %1

echo Finished.
