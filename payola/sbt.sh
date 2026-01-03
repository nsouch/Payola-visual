export JAVA_HOME=/usr/local/Cellar/openjdk@11/11.0.26

#$JAVA_HOME/bin/java -Xmx1024M -XX:MaxPermSize=512M -Xss2M -jar `dirname $0`/sbt-launch.jar "$@"
$JAVA_HOME/bin/java -Xmx1024M -Xss2M -jar `dirname $0`/sbt-launch.jar "$@"