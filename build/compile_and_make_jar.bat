SET JDK_PATH="C:\Program Files (x86)\Java\jdk1.8.0_141"
SET JDK_BIN=%JDK_PATH%"\bin"
SET JAVAC_EXE=%JDK_BIN%"\javac.exe"
set JAR_EXE=%JDK_BIN%"\jar.exe"
SET JAR_FILE="Pumpctrl.jar"
del %JAR_FILE%

%JAVAC_EXE% -d . ..\src\*.java
%JAR_EXE% cvfm %JAR_FILE% mainclass.txt *.class

del *.class

pause
