export HADOOP_HOME=./hadoop
rm -rf bin
mkdir bin
javac -sourcepath src -classpath $HADOOP_HOME/lib/commons-io-2.4.jar:$HADOOP_HOME/hadoop-0.20.1-core.jar -d bin src/$1.java
echo Main-Class: $1 > manifest.txt
echo Class-Path: $HADOOP_HOME/lib/commons-cli-1.2.jar $HADOOP_HOME/hadoop-0.20.1-core.jar $HADOOP_HOME/lib/commons-logging-1.0.4.jar $HADOOP_HOME/lib/commons-logging-api-1.0.4.jar $HADOOP_HOME/lib/commons-httpclient-3.0.1.jar $HADOOP_HOME/lib/commons-io-2.4.jar >> manifest.txt
jar -cvfm $1.jar manifest.txt -C bin/ .
rm -f manifest.txt
rm -rf bin

