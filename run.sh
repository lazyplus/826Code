export HADOOP_HOME=./hadoop
PROG=$1
shift
time java -jar $PROG.jar $* 2>/dev/null | tee local.log
rm -f $PROG.jar

