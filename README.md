# Efficient Path Profiling
## Advanced Software Engineering Project

[Efficient path profiling](http://dl.acm.org/citation.cfm?id=243857)

<hr>

## Usage
### Possible Arguments for the PathProfiler
* To instrument a particular class, use:   
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp [Class Name]#[Method Name]
```
Example:
```
--cp /home/arpith/iisc/ase/projects/path_profiling/testprogs/:/home/arpith/iisc/ase/projects/path_profiling/workspace/PathProfiler/bin/ -pp HelloWorld#simpleif
```
The above arguments specifies that only a method named simpleif() in HelloWorld.class is instrumented.

* To instrument all members in a class, use:
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp [Class Name]
```

* To instrument all classes in a directory or jar file, use:
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp --process-dir [DIR/JAR]
```

### Run instrumented code
To run the instrumented class, run
```
java -cp [...]/workspace/PathProfiler/bin:. [Class Name]
```

### Regenerating a path
To regenerate a path, set ```boolean regeneratePath = true;```.   
This code will most likely be in line ```79``` in ```PathProfiler.java```.   
Now, the profiler should ask for an user input. Give a path sum. The output would be the path taken by the program which generates taht path sum.

### Running Benchmarks
Download DaCapo from [here](https://drive.google.com/file/d/0B61DWhik0jONVW9fa3Z3aG1Ock0/view?usp=sharing).   
You may download it from official sources but in that case, you'll manually need to add ```xyz.arpith.pathprofiler.MyCounter.class``` (present in ```/workspace/PathProfiler/bin```) to the jar file.   

To run the benchmarks, find the file ```testprogs/DaCapo/runbm.sh```   
```
renbm.sh benchmark_name
```

### System Configuration
This project was compiled and run on a machine running
* Ubuntu 16.04 LTS (development branch)
* Eclipse Neon Milestone 5
* Java build 1.8.0_74-b02
* DaCapo benchmark suite 9.12