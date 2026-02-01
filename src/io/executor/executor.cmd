@echo off

rem  ----------------------------------------------------------------------------
rem Executor launcher
rem
rem Requires:
rem Visual C++ 2017+
rem  ----------------------------------------------------------------------------

set DIR=%~dp0
call:escape_trailing_slash "%DIR%" "DIR"

set EXECUTOR_X=%DIR%\bin\executor-x.exe

set LOGBACK_OPT="-Dlogback.configurationFile=%DIR%config\logback-x.xml"
set ENCODING_OPT="-Dfile.encoding=cp1251"
set LOCATION="-Dexecutor.location=%DIR%"
set OS_NAME=-Dos.name=Windows

for /f "tokens=2 delims=:." %%x in ('chcp') do set ENCODING=%%x
chcp 1251 > nul

rem Call Executor native-image
call "%EXECUTOR_X%" %LOGBACK_OPT% %ENCODING_OPT% %LOCATION% %OS_NAME% %*

set ERROR_CODE=%ERRORLEVEL%

chcp %ENCODING% > nul

goto end

:error
rem -- If error occurred - place a flag
set ERROR_CODE=1
goto end

:escape_trailing_slash
rem -- Remove trailing backslash
rem -- %~1: String from which to remove a trailing slash
rem -- %~2: Result
SETLOCAL
set datapath=%~1
if %datapath:~-1%==\ set datapath=%datapath:~0,-1%
( ENDLOCAL & rem Result values
    if "%~2" NEQ "" set "%~2=%datapath%"
)
goto:eof

rem Exit
:end
IF %ERRORLEVEL% NEQ 0 (
    if %ERROR_CODE% NEQ 0 (
        set ERROR_CODE=ERRORLEVEL
    )
)
cmd /C exit /B %ERROR_CODE%