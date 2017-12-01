rem This is a Windows batch scrip for compiling and archiving into a .jar file
SET JDK_PATH="C:\Program Files (x86)\Java\jdk1.8.0_141"
SET JDK_BIN=%JDK_PATH%\bin
SET JAVAC_EXE=%JDK_BIN%\javac.exe
SET JAR_EXE=%JDK_BIN%\jar.exe
SET SRC_FILE=..\src\Pumpctrl.java
findstr "\"[0-9]\.[0-9]\.[0-9]\";" %SRC_FILE% > ver_num_line.txt
SET /p VER_NUM_LINE=<ver_num_line.txt
SET VER_NUM=%VER_NUM_LINE:~30%
findstr "\"20[1-9][0-9][0-9][0-9][0-9][0-9]\";" %SRC_FILE% > ver_date_line.txt
SET /p VER_DATE_LINE=<ver_date_line.txt
SET VER_DATE=%VER_DATE_LINE:~31,8%
SET VER_STRING=%VER_NUM:";=%-%VER_DATE%
SET JAR_FILE=Pumpctrl_%VER_STRING%.jar
del %JAR_FILE% ver_num_line.txt ver_date_line.txt

%JAVAC_EXE% -d . ..\src\*.java
%JAR_EXE% cvfm %JAR_FILE% mainclass.txt *.class

del *.class

pause
