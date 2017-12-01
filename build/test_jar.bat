rem This is a Windows batch scrip for opening the archived .jar file.
rem Replace the JRE_PATH variable if your JRE is different.
SET JRE_PATH="C:\Program Files (x86)\Java\jre1.8.0_141"
SET JRE_BIN=%JRE_PATH%\bin
SET JAVA_EXE=%JRE_BIN%\java.exe
SET SRC_FILE=..\src\Pumpctrl.java
findstr "\"[0-9]\.[0-9]\.[0-9]\";" %SRC_FILE% > ver_num_line.txt
SET /p VER_NUM_LINE=<ver_num_line.txt
SET VER_NUM=%VER_NUM_LINE:~30%
findstr "\"20[1-9][0-9][0-9][0-9][0-9][0-9]\";" %SRC_FILE% > ver_date_line.txt
SET /p VER_DATE_LINE=<ver_date_line.txt
SET VER_DATE=%VER_DATE_LINE:~31,8%
SET VER_STRING=%VER_NUM:";=%-%VER_DATE%
SET JAR_FILE=Pumpctrl_%VER_STRING%.jar
del ver_num_line.txt ver_date_line.txt

%JAVA_EXE% -jar %JAR_FILE%
