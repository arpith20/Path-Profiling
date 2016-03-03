# Efficient Path Profiling
## Advanced Software Engineering Project

[Efficient path profiling](http://dl.acm.org/citation.cfm?id=243857)

<hr>

## Usage
### Possible Arguments for the PathProfiler
* To instrument a particular class, use:**   
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp [Class Name]#[Method Name]
```
Example:
```
--cp /home/arpith/iisc/ase/projects/path_profiling/testprogs/:/home/arpith/iisc/ase/projects/path_profiling/workspace/PathProfiler/bin/ -pp HelloWorld#simpleif
```
The above arguments ensures that only a method named simpleif() is instrumented. It also specifies that this method is present in HelloWorld class.

* To instrument all members in a class, use:
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp [Class Name]
```

* To instrument all classes in a directory, use:
```
--cp [classPath]:[...]/workspace/PathProfiler/bin/ -pp --process-dir [DIR]
```

### Run instrumented code
To run the instrumented class, run
```
java -cp [...]/workspace/PathProfiler/bin:. [Class Name]
```

### Regenerating a path
To regenerate a path, set ```boolean regeneratePath = true;```.   
This code will most likely be in line ```61``` in ```PathProfiler.java```.   
Now, the profiler should ask for an user input. Give a path sum to regenerate the path.