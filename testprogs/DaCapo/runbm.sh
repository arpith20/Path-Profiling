#!/bin/bash
# This script will run the specified benchmarks
# Author: Arpith K 
# Ubuntu 16.04 (Development Branch)

# Configuration
java="/home/arpith/system/java/jdk1.8.0_74"
project="/home/arpith/iisc/ase/projects/path_profiling"
benchpath="/home/arpith/iisc/ase/projects/path_profiling/testprogs/DaCapo"

if [ $# -eq 0 ]
  then
    echo "
Usage: ./runbm.sh name_of_benchmark

Following are the benchmarks in DaCapo 9.12
avrora		
batik 		
eclipse 	
fop			
h2			
jython		
luindex		
lusearch	
pmd			
sunflow		
tomcat 		
tradebeans 	
tradesoap 	
xalan 		
"
	exit
fi

ip_size="small"
benchmark=$1


echo "Running ".$benchmark
echo "============"
echo "Dumping classes and creating log files"
$java/bin/java -javaagent:poa.jar=out/$benchmark -jar dacapo-9.12-bach.jar $benchmark -s $ip_size &&
echo "Running Soot"
$java/bin/java -Xmx10G -Dfile.encoding=UTF-8 -classpath $project/workspace/PathProfiler/bin:$project/workspace/PathProfiler/lib/soot-trunk.jar xyz.arpith.pathprofiler.PathProfiler --cp $project/testprogs/:$project/workspace/PathProfiler/bin/:$java/jre/lib/jce.jar:$java/jre/lib/rt.jar:out -pp -app -p cg.spark enabled -p cg reflection-log:$benchpath/out/$benchmark/refl.log -process-dir $benchpath/out/$benchmark -main-class Harness -d $benchpath/soot/$benchmark Harness &&
echo "Running DaCapo with the transformed class files"
$java/bin/java -noverify -javaagent:pia.jar=soot/$benchmark -jar dacapo-9.12-bach.jar $benchmark -s $ip_size | tee output/$benchmark.txt &&
vi output/$benchmark.txt

: '
Following are the benchmarks in DaCapo 9.12
avrora		simulates a number of programs run on a grid of AVR microcontrollers
batik 		produces a number of Scalable Vector Graphics (SVG) images based on the unit tests in Apache Batik
eclipse 	executes some of the (non-gui) jdt performance tests for the Eclipse IDE
fop			takes an XSL-FO file, parses it and formats it, generating a PDF file.
h2			executes a JDBCbench-like in-memory benchmark, executing a number of transactions against a model of a banking application, replacing the hsqldb benchmark
jython		inteprets a the pybench Python benchmark
luindex		Uses lucene to indexes a set of documents; the works of Shakespeare and the King James Bible
lusearch	Uses lucene to do a text search of keywords over a corpus of data comprising the works of Shakespeare and the King James Bible
pmd			analyzes a set of Java classes for a range of source code problems
sunflow		renders a set of images using ray tracing
tomcat 		runs a set of queries against a Tomcat server retrieving and verifying the resulting webpages
tradebeans 	runs the daytrader benchmark via a Jave Beans to a GERONIMO backend with an in memory h2 as the underlying database
tradesoap 	runs the daytrader benchmark via a SOAP to a GERONIMO backend with in memory h2 as the underlying database
xalan 		transforms XML documents into HTML
'