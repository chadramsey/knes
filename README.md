## kNES
An NES emulator written in Kotlin.

### Build Instructions
Once the project is cloned, execute the following Gradle command in the base directory
to generate the .jar and corresponding 'libs' directory containing the controller binaries
(Note: The 'libs' directory must exist in the same directory as the .jar, otherwise ROMs will 
not load properly):

`gradle build -q verifyLibsDirectory copyBinaries`

### Controller Mapping
Currently the only controller input available is through the keyboard (more support to come).

```
Up Arrow -> Up
Down Arrow -> Down
Left Arrow -> Left
Right Arrow -> Right
Enter -> Start
Shift -> Select
X -> A
Z -> B
```

### Versions
#### v0.1.0
This is a bare bones release to get the ball rolling for community support. Features are very limited 
but should provide enough mapper support for running a number of well-known NTSC-based titles. Support for
audio remains to be been implemented.
- NTSC support (PAL to come in a future release)
- Support for mappers MMC1, MMC2, NROM, and UNROM

### Known issues
- Frame limiting is currently on a fixed interval (16ms/frame) and performance depends entirely on
the system CPU.
- Loading a ROM on top of a currently running ROM will cause undesirable results. Reset functionality
remains to be implemented.

### Credits
A number of class implementations in this project are based off of Andrew Hoffman's 
[halfnes](https://github.com/andrew-hoffman/halfnes). For anyone seeking a fully comprehensive
NES emulator written for the JVM, please check out his work.