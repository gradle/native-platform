
# Native-platform: Java bindings for various native APIs

## Available bindings

### Generic

* Get and set UNIX file mode.
* Get PID of current process.

### Terminal and console

These bindings work for both the UNIX terminal and Windows console:

* Determine if stdout/stderr are attached to a terminal.
* Query the terminal size.
* Switch between bold and normal mode on the terminal.
* Change foreground color on the terminal.
* Move terminal cursor up, down, left, right, start of line.
* Clear to end of line.

Currently ported to OS X, Linux, Solaris and Windows. Tested on:

* OS X 10.7.4
* Ubunutu 12.04 (amd64)
* Solaris 11 (x86)
* Windows 7 (amd64)

## Building

You will need a very recent snapshot of [Gradle](http://www.gradle.org/).

### Ubuntu

The g++ compiler is required to build the native library. You will need to `g++` package for this. Generally this is already installed.

You need to install the `libncurses5-dev` package to pick up the ncurses header files. Also worth installing the `ncurses-doc` package too.

### Windows

You need to install Visual studio, and build from a Visual studio command prompt.

### OS X

The g++ compiler is required to build the native library. You will need to install the XCode tools for this.

### Solaris

For Solaris 11, you need to install the `development/gcc-45` and `system/header` packages.

## Running

Run `gradle compileMain install` to build.

Run `./build/install/native-platform/bin/native-platform` to run test application.

## TODO

* Fix terminal detection on OS X 10.8
* Package up native lib into self-extracting jar.
* Build 32 bit and 64 bit libraries.
* Support for cygwin terminal
* Use TERM=xtermc instead of TERM=xterm on Solaris.
* Add diagnostics for terminal.
* Split out separate native library for terminal handling.
* Version each native interface separately.
* String names for errno values.
* Split into multiple projects.
* Test on IBM JVM.
* Convert to c.
* Thread safety.
* Windows: flush System.out or System.err on attribute change.
* Solaris: fix unicode file name handling.
* Improve error message when unsupported capability is used.
