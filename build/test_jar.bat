SET JRE_PATH="C:\Program Files (x86)\Java\jre1.8.0_141"
SET JRE_BIN=%JRE_PATH%\bin
SET JAVA_EXE=%JRE_BIN%\java.exe
SET SRC_FILE=..\src\Pumpctrl.java
findstr "[0-9]\.[0-9]\.[0-9]-20[1-9][0-9][0-9][0-9][0-9][0-9]" %SRC_FILE% > ver_line.txt
SET /p VER_LINE=<ver_line.txt
SET VER_STRING=%VER_LINE:~-17,14%
SET JAR_FILE=Pumpctrl_%VER_STRING%.jar
del ver_line.txt

%JAVA_EXE% -jar %JAR_FILE%
