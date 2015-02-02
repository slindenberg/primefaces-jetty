@echo off

:START
color 2f
pushd %~dp0
cd ..

:BUILD
call mvn clean compile package
IF ERRORLEVEL 1 GOTO ERROR
IF ERRORLEVEL 0 GOTO RUN

:RUN
cd dist
REM java -jar primefaces-jetty.jar
GOTO EXT

:ERROR
color c
echo.
echo ---------------------------------------------------------------------------
echo ERROR: The build could not be completed!
echo ---------------------------------------------------------------------------
GOTO EXT

:EXT
echo.
echo Press any key to exit ...
echo.
pause > nul
popd
color
exit