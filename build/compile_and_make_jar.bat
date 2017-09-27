SET JDK_PATH="C:\Program Files (x86)\Java\jdk1.8.0_141"
SET JDK_BIN=%JDK_PATH%\bin
SET JAVAC_EXE=%JDK_BIN%\javac.exe
SET JAR_EXE=%JDK_BIN%\jar.exe
SET SRC_FILE=..\src\Pumpctrl.java
findstr "[0-9]\.[0-9]\.[0-9]-20[1-9][0-9][0-9][0-9][0-9][0-9]" %SRC_FILE% > ver_line.txt
SET /p VER_LINE=<ver_line.txt
SET VER_STRING=%VER_LINE:~-17,14%
SET JAR_FILE=Pumpctrl_%VER_STRING%.jar
del %JAR_FILE% ver_line.txt

%JAVAC_EXE% -d . ..\src\*.java
%JAR_EXE% cvfm %JAR_FILE% mainclass.txt *.class

del *.class

pause
