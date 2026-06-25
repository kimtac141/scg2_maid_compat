@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.10.7-hotspot
call gradlew build --no-daemon %*
