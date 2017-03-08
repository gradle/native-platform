
# Native-platform: Java bindings for native APIs

A collection of cross-platform Java APIs for various native APIs. Currently supports OS X, Linux, Windows and FreeBSD
on Intel architectures.

These APIs support Java 5 and later. Some of these APIs overlap with APIs available in later Java versions.

## Available bindings

### System information

* Query kernel name and version.
* Query machine architecture.
* Query total and available memory (OS X only).

See [SystemInfo](src/main/java/net/rubygrapefruit/platform/SystemInfo.java)

### Processes

* Query the PID of the current process.
* Query and set the process working directory.
* Query and set the process environment variables.
* Detach process from its controlling console

See [Process](src/main/java/net/rubygrapefruit/platform/Process.java)

### Terminal and console

These bindings work for both the UNIX terminal and the Windows console:

* Determine whether stdout/stderr are attached to a terminal.
* Query the terminal size.
* Switch between bold and normal text mode on the terminal.
* Change foreground color on the terminal.
* Move terminal cursor up, down, left, right, start of line.
* Clear to end of line.

See [Terminals](src/main/java/net/rubygrapefruit/platform/Terminals.java)

### File systems

* Watch for changes to a directory. Implementation is currently limited to watching on a single directory.

See [FileEvents](src/main/java/net/rubygrapefruit/platform/FileEvents.java)

* Query and set UNIX file mode.
* Create and read symbolic links.
* Query UNIX file uid and gid.
* Query file type, size and timestamps.
* Query directory contents.

See [Files](src/main/java/net/rubygrapefruit/platform/Files.java)

* List the available file systems on the machine and details of each file system.
* Query file system mount point.
* Query file system type.
* Query file system device name.
* Query whether a file system is local or remote.
* Query whether a file system is case sensitive and case preserving.

See [FileSystems](src/main/java/net/rubygrapefruit/platform/FileSystems.java)

### Windows registry

* Query registry value.
* Query the subkeys and values of a registry key.

See [WindowsRegistry](src/main/java/net/rubygrapefruit/platform/WindowsRegistry.java)

## Supported platforms

Currently ported to OS X, Linux, FreeBSD and Windows. Support for Solaris is a work in progress. Tested on:

* OS X 10.11.6 (x86_64), 10.6.7 (i386, amd64)
* Fedora 24 (amd64, i386)
* Ubuntu 16.10 (amd64), 13.10 (amd64), 12.10 (amd64), 8.04.4 (i386, amd64)
* OpenSUSE 13.1 (x86_64)
* FreeBSD 11.0 (amd64, i386), 10.0 (amd64, i386), 8.3 (amd64, i386)
* Windows 10 (x64), 8.1 (x64), 7 (x64), XP (x86, x64)

## Using

Include `native-platform.jar` and `native-platform-${os}-${arch}.jar` in your classpath. From Gradle, you can do
this:

    repositories {
        maven { url "https://repo.gradle.org/gradle/libs-releases-local" }
    }

    dependencies {
        compile "net.rubygrapefruit:native-platform:0.13"
    }

You can also download [here](https://repo.gradle.org/gradle/libs-releases-local/net/rubygrapefruit/)

Some sample code to use the terminal:

    import net.rubygrapefruit.platform.Native;
    import net.rubygrapefruit.platform.Terminals;
    import net.rubygrapefruit.platform.Terminal;
    import static net.rubygrapefruit.platform.Terminals.Output.*;

    Terminals terminals = Native.get(Terminals.class);

    // check if terminal
    terminals.isTerminal(Stdout);

    // use terminal
    Terminal stdout = terminals.getTerminal(Stdout);
    stdout.bold();
    System.out.println("bold text");

## Changes

### 0.14 (not released)

* Added `Memory`, for OS X only. Thanks to [Paul Merlin](https://github.com/eskatos)
* `NativeIntegrationLinkageException` is thrown by `Native.get()` when a particular native library cannot be loaded due to a linkage error.

### 0.13

* Added overloads of `Files.stat()` and `Files.listDir()` that follow links.
* Improvements to error handling for `Files.stat()` and `listDir()`.
* Fixes for build time detection of ncurses 6. Thanks to [Marcin Zajączkowski](https://github.com/szpak)

### 0.12

* Added `Files.listDir()`.
* Fixes for terminal integration for Linux distributions that use ncurses 6, such as Fedora 24 and later.
* Fixes for running on FreeBSD 10 and later without requiring GCC to be installed on the machine.

### 0.11

* Added support to detach the current process from its controlling console. Thanks to [Gary Hale](https://github.com/ghale).
* Fixes for handling Windows shares from Linux. Thanks to [Thierry Guérin](https://github.com/SchwingSK).
* Added initial implementation of `FileEvents`, which allows an application to listen for changes to a file system directory.
* Added more properties to `PosixFile`.
* Added `Files` and `WindowsFiles`.
* Added `FileSystem.isCaseSensitive()` and `FileSystem.isCasePreserving()`.
* Fixes running under GCJ.

### 0.10

* Fixes for broken 0.9 release.

### 0.9

* Fixes for non-ascii file names on OS X when running under the Apple JVM.

You should avoid using this release, and use 0.10 or later instead.

### 0.8

* Ported to FreeBSD. Thanks to [Zsolt Kúti](https://github.com/tinca).

### 0.7

* Some fixes for a broken 0.6 release.

### 0.6

* Some fixes for Windows 7 and OS X 10.6.

You should avoid using this release, and use 0.7 or later instead.

### 0.5

* Query the available values of a Windows registry key. Thanks to [Michael Putters](https://github.com/mputters).

### 0.4

* Get file type.
* Query Windows registry value and subkeys.
* Fixes to work on 64-bit Windows XP.

### 0.3

* Get and set process working directory.
* Get and set process environment variables.
* Launch processes.
* Fixed character set issue on Linux and Mac OS X.
* Fixes to work with 64-bit OpenJDK 7 on Mac OS X. Thanks to [Rene Groeschke](https://github.com/breskeby).

### 0.2

* Fixes to make native library extraction multi-process safe.
* Fixes to windows terminal detection and reset.

### 0.1

* Initial release.

# Development

## Building

This project uses (Gradle)[https://www.gradle.org] to build. Just run `gradlew` in the root of the source repo.
You will need Java 7 or later to run the tests.

### Ubuntu

The g++ compiler is required to build the native library. You will need to install the `g++` package for this.
Alternatively, you can use the Clang C++ compiler.

You need to install the `libncurses5-dev` package to pick up the ncurses header files. Also worth installing the `ncurses-doc` package too.

#### 64-bit machines with multi-arch support

Where multi-arch support is available (e.g. recent Ubuntu releases), you can build the i386 and amd64 versions of the library on the
same machine.

You need to install the `gcc-multilib` and `g++-multilib` packages to pick up i386 support.

You need to install the `lib32ncurses5-dev` package to pick up the ncurses i386 version.

### Windows

You need to install Visual studio 2010 or later, plus the Windows SDK to allow you to build both x86 and x64 binaries.

### OS X

The g++ compiler is required to build the native library. You will need to install the XCode command-line tools for this.

### Solaris

For Solaris 11, you need to install the `development/gcc-45` and `system/header` packages.

## Running

Run `gradle installApp` to install the test application into `test-app/build/install/native-platform-test`. Or
`gradle distZip` to create an application distribution in `test-app/build/distributions/native-platform-test-$version.zip`.

You can run `$INSTALL_DIR/bin/native-platform-test` to run the test application.

# Releasing

1. Check the version number in `build.gradle`.
2. Check that the native interface version has been incremented since last release, when changes have been made to native code.
3. Create a tag and push.
4. Build each variant. Note: for ensure linux ncurses6 is built before linux ncurses5
    1. Checkout tag.
    2. `./gradlew clean :test :uploadJni -Prelease -PartifactoryUserName=<> -PartifactoryPassword=<>`.
5. Build Java library:
    1. Checkout tag.
    2. `./gradlew clean :test :uploadArchives -Prelease -PartifactoryUserName=<> -PartifactoryPassword=<>`
6. Build the test app:
    1. Checkout tag.
    2. `./gradlew clean :testApp:uploadArchives -Prelease -PartifactoryUserName=<> -PartifactoryPassword=<>`
7. Checkout master
8. Increment version number in `build.gradle` and this readme.
9. Push changes.

Use `-Psnapshot` instead of `-Prelease` to publish a dev version.

## Testing

* Test on IBM JVM.
* Test on Java 5, 6, 7.
* Test on Windows 7, Windows XP

## TODO

### Fixes

* OS X: Watch for changes to files in directory.
* FreeBSD: Watch for changes to files in directory.
* Linux: Fix spurious change event on close.
* All: Handle deletion and recreation of watched file/directory.
* All: Watch for creation and changes to missing file/directory.
* Windows: Watch for changes to a file (directory works, file does not).
* All: `FileWatch` tests: file truncated, last modified changed, content changed, recreated as file/dir, file renamed
* All: Thread safety for `FileWatch`.
* All: Bulk read of multiple file change events, coalesce events, use background thread to drain queue.
* Linux: Fix detection of multiarch support
* FreeBSD: Fix detection of multiarch support
* All: `Process.getPid()` should return a long
* All: fail subsequent calls to `Native.get()` when `Native.initialize()` fails.
* Posix: allow terminal to be detected when ncurses cannot be loaded
* Windows: fix detection of shared drive under VMWare fusion and Windows XP
* Windows: restore std handles after launching child process
* Linux: detect remote filesystems.
* All: cache reflective lookup in native functions.
* Solaris: fix unicode file name handling.
* Solaris: fail for unsupported architecture.
* Solaris: build 32 bit and 64 bit libraries.

### Improvements

* All: fall back to WrapperProcessLauncher + DefaultProcessLauncher for all platforms, regardless of whether a
  native integration is available or not.
* All: change the terminal API to handle the fact that stdout/stderr/stdin can all be attached to the same or to
  different terminals.
* All: have `Terminal` extend `Appendable` and `Flushable`
* All: add a method to `Terminal` that returns a `PrintStream` that can be used to write to the terminal, regardless of what
  `System.out` or `System.err` point to.
* Windows: use `wchar_to_java()` for system and file system info.
* All: test network file systems
* Windows: test mount points
* All: cache class, method and field lookups
* Unix: change `readLink()` implementation so that it does not need to NULL terminate the encoded content
* All: don't use `NewStringUTF()` anywhere
* Mac: change `java_to_char()` to convert java string directly to utf-8 char string.
* Mac: change `char_to_java()` to assume utf-8 encoding and convert directly to java string.
* Linux: change `char_to_java()` to use `iconv()` to convert from C char string to UTF-16 then to java string.
* Windows: support for cygwin terminal
* Solaris: use `TERM=xtermc` instead of `TERM=xterm`.
* All: add diagnostics for terminal.
* All: version each native interface separately.
* Windows: string names for errno values.
* All: split into multiple projects.
* Mac: use fully decomposed form for unicode file names on hfs+ filesystems.
* All: extend FileSystem to deal with removable media.
* Unix: add a Terminal implementation that uses ANSI control codes. Use this when TERM != 'dumb' and
  libncurses cannot be loaded.
* All: add a method to Terminal that indicates whether the cursor wraps to the next line when a character is written
  to the rightmost character position.
* All: check for null parameters.
* Windows: long paths, prefix with `\\?\` or `\\?\UNC\`

### Ideas

* Publish to bintray.
* Normalise a unicode file name for a given file system (eg hfs+ uses fully decomposed form).
* Expose meta-data about an NTFS volume:
    * Does the volume support 8.3 file names: Query [FILE_FS_PERSISTENT_VOLUME_INFORMATION](https://msdn.microsoft.com/en-us/library/windows/hardware/ff540280.aspx)
      using [DeviceIoControl()](https://msdn.microsoft.com/en-us/library/aa363216.aspx)
* Expose native desktop notification services:
    * OS X message center
    * Growl
    * Snarl
    * dnotify
* Locate various system directories (eg program files on windows).
* Expose platform-specific HTTP proxy configuration. Query registry on windows to determine IE settings.
* Expose native named semaphores, mutexes and condition variables (CreateMutex, CreateSemaphore, CreateEvent, semget, sem_open, etc).
* Expose information about network interfaces.
    * Windows networking: https://msdn.microsoft.com/en-us/library/windows/desktop/ee663286(v=vs.85).aspx
    * Windows ip functions: https://msdn.microsoft.com/en-us/library/windows/desktop/aa366071(v=vs.85).aspx
    * Windows notification on change: https://msdn.microsoft.com/en-us/library/windows/desktop/aa366329(v=vs.85).aspx
* Expose information about memory size and usage:
    * http://nadeausoftware.com/articles/2012/09/c_c_tip_how_get_physical_memory_size_system
* Expose system monotonic clock, for timing:
    * clock_gettime(CLOCK_MONOTONIC) on Linux
    * mach_absolute_time() and mach_timebase_info() on OS X.
* Fire events when filesystems or network interfaces change in some way.
* Fire events when terminal size changes.
* Expose system keystores and authentication services.
* Expose a mechanism for generating a temporary directory.
