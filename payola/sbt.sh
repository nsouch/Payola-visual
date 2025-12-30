# export JAVA_HOME=/usr/local/opt/openjdk
export JAVA_HOME=/usr/local/Cellar/openjdk@11/11.0.26
# export JAVA_HOME=/Users/nico/Downloads/zulu8.90.0.19-ca-jdk8.0.472-macosx_x64
# export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/

#$JAVA_HOME/bin/java -Xmx1024M -XX:MaxPermSize=512M -Xss2M -jar `dirname $0`/sbt-launch.jar "$@"
$JAVA_HOME/bin/java -Xmx1024M -Xss2M -jar `dirname $0`/sbt-launch.jar "$@"