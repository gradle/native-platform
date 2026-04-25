/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/*
 * POSIX platform functions.
 */
#ifndef _WIN32

#include "generic.h"
#include "net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixFileFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_PosixTypeFunctions.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#if defined(__APPLE__)
    #include <util.h>
#elif defined(__FreeBSD__)
    #include <libutil.h>
#else
    #include <pty.h>
#endif
#ifdef __linux__
    #include <sys/utsname.h>
    // Don't include sys/sysctl.h on Linux - it's deprecated
#else
    #include <sys/sysctl.h>  // Keep for BSD/macOS
#endif

jmethodID fileStatDetailsMethodId;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_NativeLibraryFunctions_getSystemInfo(JNIEnv* env, jclass target, jobject info, jobject result) {
    jclass infoClass = env->GetObjectClass(info);

    struct utsname machine_info;
    if (uname(&machine_info) != 0) {
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }

    jfieldID osNameField = env->GetFieldID(infoClass, "osName", "Ljava/lang/String;");
    env->SetObjectField(info, osNameField, char_to_java(env, machine_info.sysname, result));
    jfieldID osVersionField = env->GetFieldID(infoClass, "osVersion", "Ljava/lang/String;");
    env->SetObjectField(info, osVersionField, char_to_java(env, machine_info.release, result));
    jfieldID hostnameField = env->GetFieldID(infoClass, "hostname", "Ljava/lang/String;");
    env->SetObjectField(info, hostnameField, char_to_java(env, machine_info.nodename, result));

    jfieldID machineArchitectureField = env->GetFieldID(infoClass, "machineArchitecture", "Ljava/lang/String;");
#ifndef __APPLE__
    env->SetObjectField(info, machineArchitectureField, char_to_java(env, machine_info.machine, result));
#else
    // On macOS, uname() reports the architecture of the current binary.
    // Instead, use a macOS specific sysctl() to query the CPU name, which can be mapped to the architecture
    int mib[5];
    size_t len = 5;
    size_t value_len;
    char *value;

    if (sysctlnametomib("machdep.cpu.brand_string", mib, &len) != 0) {
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }

    if (sysctl(mib, len, NULL, &value_len, NULL, 0) != 0) {
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }
    value = (char*)malloc(value_len);
    if (sysctl(mib, len, value, &value_len, NULL, 0) != 0) {
        free(value);
        mark_failed_with_errno(env, "could not query machine details", result);
        return;
    }
    env->SetObjectField(info, machineArchitectureField, char_to_java(env, value, result));
    free(value);
#endif
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTypeFunctions_getNativeTypeInfo(JNIEnv* env, jclass target, jobject info) {
    jclass infoClass = env->GetObjectClass(info);
    env->SetIntField(info, env->GetFieldID(infoClass, "int_bytes", "I"), sizeof(int));
    env->SetIntField(info, env->GetFieldID(infoClass, "u_long_bytes", "I"), sizeof(u_long));
    env->SetIntField(info, env->GetFieldID(infoClass, "size_t_bytes", "I"), sizeof(size_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "uid_t_bytes", "I"), sizeof(uid_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "gid_t_bytes", "I"), sizeof(gid_t));
    env->SetIntField(info, env->GetFieldID(infoClass, "off_t_bytes", "I"), sizeof(off_t));
}

/*
 * File functions
 */

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_chmod(JNIEnv* env, jclass target, jstring path, jint mode, jobject result) {
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    int retval = chmod(pathStr, mode);
    free(pathStr);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not chmod file", result);
    }
}

jlong toMillis(struct timespec t) {
    return (jlong)(t.tv_sec) * 1000 + (jlong)(t.tv_nsec) / 1000000;
}

void unpackStat(struct stat* source, file_stat_t* result) {
    switch (source->st_mode & S_IFMT) {
        case S_IFREG:
            result->fileType = FILE_TYPE_FILE;
            result->size = source->st_size;
            break;
        case S_IFDIR:
            result->fileType = FILE_TYPE_DIRECTORY;
            result->size = 0;
            break;
        case S_IFLNK:
            result->fileType = FILE_TYPE_SYMLINK;
            result->size = 0;
            break;
        default:
            result->fileType = FILE_TYPE_OTHER;
            result->size = 0;
    }
#ifdef __linux__
    result->lastModified = toMillis(source->st_mtim);
#else
    result->lastModified = toMillis(source->st_mtimespec);
#endif
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_stat(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject dest, jobject result) {
    struct stat fileInfo;
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    int retval;
    if (followLink) {
        retval = stat(pathStr, &fileInfo);
    } else {
        retval = lstat(pathStr, &fileInfo);
    }
    free(pathStr);
    if (retval != 0 && errno != ENOENT && errno != ENOTDIR) {
        mark_failed_with_errno(env, "could not stat file", result);
        return;
    }

    if (retval != 0) {
        env->CallVoidMethod(dest, fileStatDetailsMethodId, FILE_TYPE_MISSING, (jint) 0, (jint) 0, (jint) 0, (jlong) 0, (jlong) 0, (jint) 0);
    } else {
        file_stat_t fileResult;
        unpackStat(&fileInfo, &fileResult);
        env->CallVoidMethod(dest,
            fileStatDetailsMethodId,
            fileResult.fileType,
            (jint) (0777 & fileInfo.st_mode),
            (jint) fileInfo.st_uid,
            (jint) fileInfo.st_gid,
            fileResult.size,
            fileResult.lastModified,
            (jint) fileInfo.st_blksize);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readdir(JNIEnv* env, jclass target, jstring path, jboolean followLink, jobject contents, jobject result) {
    jclass contentsClass = env->GetObjectClass(contents);
    jmethodID mid = env->GetMethodID(contentsClass, "addFile", "(Ljava/lang/String;IJJ)V");
    if (mid == NULL) {
        mark_failed_with_message(env, "could not find method", result);
        return;
    }

    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    long pathLen = strlen(pathStr);
    DIR* dir = opendir(pathStr);
    if (dir == NULL) {
        mark_failed_with_errno(env, "could not open directory", result);
        free(pathStr);
        return;
    }
    struct dirent entry;
    struct dirent* next;
    while (true) {
        if (readdir_r(dir, &entry, &next) != 0) {
            mark_failed_with_errno(env, "could not read directory entry", result);
            break;
        }
        if (next == NULL) {
            break;
        }
        if (strcmp(".", entry.d_name) == 0 || strcmp("..", entry.d_name) == 0) {
            continue;
        }

        size_t childPathLen = pathLen + strlen(entry.d_name) + 2;
        char* childPath = (char*) malloc(childPathLen);
        strncpy(childPath, pathStr, pathLen);
        childPath[pathLen] = '/';
        strcpy(childPath + pathLen + 1, entry.d_name);

        struct stat fileInfo;
        int retval;
        if (followLink) {
            retval = stat(childPath, &fileInfo);
        } else {
            retval = lstat(childPath, &fileInfo);
        }
        free(childPath);
        file_stat fileResult;
        if (retval != 0) {
            if (!followLink || errno != ENOENT) {
                mark_failed_with_errno(env, "could not stat file", result);
                break;
            }
            fileResult.fileType = FILE_TYPE_MISSING;
            fileResult.size = 0;
            fileResult.lastModified = 0;
        } else {
            unpackStat(&fileInfo, &fileResult);
        }

        jstring childName = char_to_java(env, entry.d_name, result);
        env->CallVoidMethod(contents, mid, childName, fileResult.fileType, fileResult.size, fileResult.lastModified);
    }

    closedir(dir);
    free(pathStr);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_symlink(JNIEnv* env, jclass target, jstring path, jstring contents, jobject result) {
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return;
    }
    char* contentStr = java_to_char(env, contents, result);
    if (contentStr == NULL) {
        free(pathStr);
        return;
    }
    int retval = symlink(contentStr, pathStr);
    free(contentStr);
    free(pathStr);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not symlink", result);
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixFileFunctions_readlink(JNIEnv* env, jclass target, jstring path, jobject result) {
    struct stat link_info;
    char* pathStr = java_to_char(env, path, result);
    if (pathStr == NULL) {
        return NULL;
    }
    int retval = lstat(pathStr, &link_info);
    if (retval != 0) {
        free(pathStr);
        mark_failed_with_errno(env, "could not lstat file", result);
        return NULL;
    }

    char* contents = (char*) malloc(link_info.st_size + 1);
    if (contents == NULL) {
        free(pathStr);
        mark_failed_with_message(env, "could not create array", result);
        return NULL;
    }

    retval = readlink(pathStr, contents, link_info.st_size);
    free(pathStr);
    if (retval < 0) {
        free(contents);
        mark_failed_with_errno(env, "could not readlink", result);
        return NULL;
    }
    contents[link_info.st_size] = 0;
    jstring contents_str = char_to_java(env, contents, result);
    free(contents);
    return contents_str;
}

/*
 * Process functions
 */

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getPid(JNIEnv* env, jclass target) {
    return getpid();
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_detach(JNIEnv* env, jclass target, jobject result) {
    if (setsid() == -1) {
        // Ignore if the error is that the process is already detached from the terminal
        if (errno != EPERM) {
            mark_failed_with_errno(env, "could not setsid()", result);
        }
    }
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getWorkingDirectory(JNIEnv* env, jclass target, jobject result) {
    char* path = getcwd(NULL, 0);
    if (path == NULL) {
        mark_failed_with_errno(env, "could not getcwd()", result);
        return NULL;
    }
    jstring dir = char_to_java(env, path, result);
    free(path);
    return dir;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setWorkingDirectory(JNIEnv* env, jclass target, jstring dir, jobject result) {
    char* path = java_to_char(env, dir, result);
    if (path == NULL) {
        return;
    }
    if (chdir(path) != 0) {
        mark_failed_with_errno(env, "could not setcwd()", result);
    }
    free(path);
}

JNIEXPORT jstring JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_getEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jobject result) {
    char* varStr = java_to_utf_char(env, var, result);
    char* valueStr = getenv(varStr);
    free(varStr);
    if (valueStr == NULL) {
        return NULL;
    }
    return utf_char_to_java(env, valueStr, result);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable(JNIEnv* env, jclass target, jstring var, jstring value, jobject result) {
    char* varStr = java_to_utf_char(env, var, result);
    if (varStr != NULL) {
        if (value == NULL) {
            if (setenv(varStr, "", 1) != 0) {
                mark_failed_with_errno(env, "could not putenv()", result);
            }
        } else {
            char* valueStr = java_to_utf_char(env, value, result);
            if (valueStr != NULL) {
                if (setenv(varStr, valueStr, 1) != 0) {
                    mark_failed_with_errno(env, "could not putenv()", result);
                }
            }
            free(valueStr);
        }
    }
    free(varStr);
}

/*
 * Terminal functions
 */

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_isatty(JNIEnv* env, jclass target, jint output) {
    struct stat fileInfo;
    int result;
    switch (output) {
        case STDIN_DESCRIPTOR:
            return isatty(STDIN_FILENO) ? JNI_TRUE : JNI_FALSE;
        case STDOUT_DESCRIPTOR:
            return isatty(STDOUT_FILENO) ? JNI_TRUE : JNI_FALSE;
        case STDERR_DESCRIPTOR:
            return isatty(STDERR_FILENO) ? JNI_TRUE : JNI_FALSE;
        default:
            return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_getTerminalSize(JNIEnv* env, jclass target, jint output, jobject dimension, jobject result) {
    struct winsize screen_size;
    int retval = ioctl(output + 1, TIOCGWINSZ, &screen_size);
    if (retval != 0) {
        mark_failed_with_errno(env, "could not fetch terminal size", result);
        return;
    }
    jclass dimensionClass = env->GetObjectClass(dimension);
    jfieldID widthField = env->GetFieldID(dimensionClass, "cols", "I");
    env->SetIntField(dimension, widthField, screen_size.ws_col);
    jfieldID heightField = env->GetFieldID(dimensionClass, "rows", "I");
    env->SetIntField(dimension, heightField, screen_size.ws_row);
}

int input_init = 0;
struct termios original_input_mode;

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_rawInputMode(JNIEnv* env, jclass target, jobject result) {
    if (input_init == 0) {
        tcgetattr(STDIN_FILENO, &original_input_mode);
        input_init = 1;
    }
    struct termios new_mode;
    new_mode = original_input_mode;
    new_mode.c_lflag &= ~(ICANON | ECHO);
    tcsetattr(STDIN_FILENO, TCSANOW, &new_mode);
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixTerminalFunctions_resetInputMode(JNIEnv* env, jclass target, jobject result) {
    if (input_init == 0) {
        return;
    }
    tcsetattr(STDIN_FILENO, TCSANOW, &original_input_mode);
}

/*
 * PTY functions
 */

JNIEXPORT jboolean JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_isPtyAvailable(JNIEnv* env, jclass target) {
    int masterFd = -1;
    int slaveFd = -1;
    if (openpty(&masterFd, &slaveFd, NULL, NULL, NULL) != 0) {
        return JNI_FALSE;
    }
    close(masterFd);
    close(slaveFd);
    return JNI_TRUE;
}

static void diag_write(int fd, char stage, int err) {
    write(fd, &stage, 1);
    write(fd, &err, sizeof(err));
}

static const char* find_path_in_env(char** envp) {
    if (envp == NULL) return NULL;
    for (int i = 0; envp[i] != NULL; i++) {
        if (strncmp(envp[i], "PATH=", 5) == 0) {
            return envp[i] + 5;
        }
    }
    return NULL;
}

static void child_search_and_exec(char** argv, char** envp, int diagFd) {
    int err = 0;
    if (strchr(argv[0], '/') != NULL) {
        execve(argv[0], argv, envp);
        err = errno;
        diag_write(diagFd, 'X', err);
        _exit(127);
    }

    const char* path = find_path_in_env(envp);
    if (path == NULL) {
        diag_write(diagFd, 'P', ENOENT);
        _exit(127);
    }

    int sawEacces = 0;
    int sawEnoexec = 0;
    size_t cmdLen = strlen(argv[0]);
    const char* p = path;
    const char* end = path + strlen(path);

    while (1) {
        const char* colon = strchr(p, ':');
        size_t dirLen = colon ? (size_t)(colon - p) : (size_t)(end - p);

        char candidate[4096];
        if (dirLen == 0) {
            if (cmdLen + 1 > sizeof(candidate)) {
                err = ENAMETOOLONG;
            } else {
                memcpy(candidate, argv[0], cmdLen);
                candidate[cmdLen] = '\0';
                execve(candidate, argv, envp);
                err = errno;
            }
        } else {
            if (dirLen + 1 + cmdLen + 1 > sizeof(candidate)) {
                err = ENAMETOOLONG;
            } else {
                memcpy(candidate, p, dirLen);
                candidate[dirLen] = '/';
                memcpy(candidate + dirLen + 1, argv[0], cmdLen);
                candidate[dirLen + 1 + cmdLen] = '\0';
                execve(candidate, argv, envp);
                err = errno;
            }
        }

        if (err == EACCES) sawEacces = 1;
        if (err == ENOEXEC) {
            sawEnoexec = 1;
            break;
        }

        if (colon == NULL) break;
        p = colon + 1;
    }

    if (sawEnoexec) {
        diag_write(diagFd, 'N', ENOEXEC);
    } else if (sawEacces) {
        diag_write(diagFd, 'E', EACCES);
    } else {
        diag_write(diagFd, 'F', ENOENT);
    }
    _exit(127);
}

// Allocate the PTY pair plus the stderr pipe. Does not fork — the Java
// caller starts master-side drainer threads on masterFd and stderrReadFd
// before invoking spawnInPty. Required for portability: POSIX leaves the
// master read after slave close implementation-defined and FreeBSD's pty
// driver discards buffered output on slave close.
JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_createPty(
        JNIEnv* env, jclass target,
        jint cols, jint rows,
        jintArray outFds,
        jobject result) {
    int masterFd = -1;
    int slaveFd = -1;
    int stderrPipe[2] = {-1, -1};
    bool failed = false;

    do {
        if (openpty(&masterFd, &slaveFd, NULL, NULL, NULL) != 0) {
            mark_failed_with_errno(env, "could not allocate pty", result);
            failed = true;
            break;
        }
        {
            int f = fcntl(masterFd, F_GETFD);
            if (f != -1) fcntl(masterFd, F_SETFD, f | FD_CLOEXEC);
        }
        if (pipe(stderrPipe) != 0) {
            mark_failed_with_errno(env, "could not create stderr pipe", result);
            failed = true;
            break;
        }
        {
            int f = fcntl(stderrPipe[0], F_GETFD);
            if (f != -1) fcntl(stderrPipe[0], F_SETFD, f | FD_CLOEXEC);
        }

        struct winsize ws;
        ws.ws_col = (unsigned short) cols;
        ws.ws_row = (unsigned short) rows;
        ws.ws_xpixel = 0;
        ws.ws_ypixel = 0;
        ioctl(masterFd, TIOCSWINSZ, &ws);

        jint fds[4] = { masterFd, slaveFd, stderrPipe[0], stderrPipe[1] };
        env->SetIntArrayRegion(outFds, 0, 4, fds);
    } while (0);

    if (failed) {
        if (masterFd >= 0) close(masterFd);
        if (slaveFd >= 0) close(slaveFd);
        if (stderrPipe[0] >= 0) close(stderrPipe[0]);
        if (stderrPipe[1] >= 0) close(stderrPipe[1]);
    }
}

// Forks and execs the child inside an already-allocated PTY. On success,
// the parent keeps slaveFd and stderrWriteFd open — Java closes them
// from a watcher thread once waitpid has returned, so the slave stays
// open through the child's own exit and FreeBSD's pty driver cannot
// flush its line discipline buffer before the parent has read it.
// On failure, both fds are closed here.
// Helper: fully read N bytes from fd, retrying on EINTR.
// Returns total bytes read; <N means short (peer closed early).
static ssize_t full_read(int fd, void* buf, size_t want) {
    ssize_t total = 0;
    while ((size_t) total < want) {
        ssize_t r = read(fd, (char*) buf + total, want - (size_t) total);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (r == 0) break;
        total += r;
    }
    return total;
}

// Helper: fully write N bytes to fd, retrying on EINTR.
static int full_write(int fd, const void* buf, size_t want) {
    size_t total = 0;
    while (total < want) {
        ssize_t w = write(fd, (const char*) buf + total, want - total);
        if (w < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        total += (size_t) w;
    }
    return 0;
}

// Decode a waitpid status into the {WEXITSTATUS, 128+signal} convention.
static int decode_wait_status(int status) {
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

// Use the two-fork "anchor + grandchild" pattern only on BSD-family
// kernels other than macOS.  Those kernels run a synchronous
// killjobc()/VOP_REVOKE/tty_flush(FWRITE) pass on the slave PTY when
// the controlling-tty session leader exits, which discards any
// slave-to-master bytes the master has not yet read.  macOS shares the
// 4.4BSD lineage but empirically preserves bytes (different
// spec_revoke aliasing behaviour); Linux explicitly skips the vhangup
// path for PTYs.  See PORTABILITY.md.
#if defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__DragonFly__)
  #define PTY_USE_ANCHOR 1
#else
  #define PTY_USE_ANCHOR 0
#endif

JNIEXPORT jlong JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_spawnInPty(
        JNIEnv* env, jclass target,
        jint slaveFd, jint stderrWriteFd,
        jobjectArray command,
        jobjectArray environment,
        jstring workingDir,
        jlongArray outAux,
        jobject result) {
    // outAux receives [anchorPid, infoPipeRead, syncPipeWrite] on success.
    // The grandchild PID is returned via the JNI return value.
    //
    // Architecture: this JNI does TWO forks.
    //   daemon -> anchor:    anchor calls setsid + ioctl(TIOCSCTTY, slaveFd).
    //   anchor -> grandchild: grandchild dup2's slave/stderr-write and execs
    //                         the user command.
    // The anchor stays alive past the grandchild's exit (blocked on read of
    // a sync pipe that the daemon closes when it has finished draining the
    // master).  This prevents FreeBSD's killjobc() from running its
    // synchronous VOP_REVOKE -> ttydev_close(FREVOKE) -> tty_flush(FWRITE)
    // path on the slave PTY while there is still buffered output the master
    // has not yet read, which is the data-loss bug documented in
    // PORTABILITY.md.  Linux and macOS gain identical structural plumbing
    // at trivial cost.
    //
    // Pipes (all CLOEXEC, anchor closes daemon-only ends after fork):
    //   diagPipe : anchor + grandchild -> daemon, for setup/exec failures.
    //              EOF when grandchild exec succeeds (CLOEXEC on grandchild's
    //              copy + explicit close on anchor's copy).
    //   infoPipe : anchor -> daemon, for grandchild PID and exit status.
    //              Anchor writes 8-byte pid then 4-byte status.
    //   syncPipe : daemon -> anchor.  Daemon closes its write end to release
    //              the anchor.  Anchor reads from its read end (returns 0 on
    //              EOF) and then _exits.
    if (outAux == NULL || env->GetArrayLength(outAux) < 3) {
        close(slaveFd);
        close(stderrWriteFd);
        mark_failed_with_message(env, "outAux array must have length >= 3", result);
        return 0;
    }

    jsize argc = env->GetArrayLength(command);
    if (argc < 1) {
        close(slaveFd);
        close(stderrWriteFd);
        mark_failed_with_message(env, "command array is empty", result);
        return 0;
    }

    char** argv = (char**) calloc((size_t)(argc + 1), sizeof(char*));
    if (argv == NULL) {
        close(slaveFd);
        close(stderrWriteFd);
        mark_failed_with_message(env, "could not allocate argv", result);
        return 0;
    }
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(command, i);
        argv[i] = java_to_utf_char(env, s, result);
        env->DeleteLocalRef(s);
        if (argv[i] == NULL) {
            for (jsize j = 0; j < i; j++) free(argv[j]);
            free(argv);
            close(slaveFd);
            close(stderrWriteFd);
            return 0;
        }
    }
    argv[argc] = NULL;

    jsize envc = environment != NULL ? env->GetArrayLength(environment) : 0;
    char** envp = (char**) calloc((size_t)(envc + 1), sizeof(char*));
    if (envp == NULL) {
        for (jsize i = 0; i < argc; i++) free(argv[i]);
        free(argv);
        close(slaveFd);
        close(stderrWriteFd);
        mark_failed_with_message(env, "could not allocate envp", result);
        return 0;
    }
    for (jsize i = 0; i < envc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(environment, i);
        envp[i] = java_to_utf_char(env, s, result);
        env->DeleteLocalRef(s);
        if (envp[i] == NULL) {
            for (jsize j = 0; j < i; j++) free(envp[j]);
            free(envp);
            for (jsize j = 0; j < argc; j++) free(argv[j]);
            free(argv);
            close(slaveFd);
            close(stderrWriteFd);
            return 0;
        }
    }
    envp[envc] = NULL;

    char* wdStr = NULL;
    if (workingDir != NULL) {
        wdStr = java_to_utf_char(env, workingDir, result);
        if (wdStr == NULL) {
            for (jsize i = 0; i < envc; i++) free(envp[i]);
            free(envp);
            for (jsize i = 0; i < argc; i++) free(argv[i]);
            free(argv);
            close(slaveFd);
            close(stderrWriteFd);
            return 0;
        }
    }

    int diagPipe[2] = {-1, -1};
    int infoPipe[2] = {-1, -1};
    int syncPipe[2] = {-1, -1};
    pid_t anchorPid = 0;
    jlong returnPid = 0;
    bool failed = false;
    bool slaveClosed = false;

    // Default outAux for the simple path: anchorPid = 0 signals to Java
    // that this spawn is anchorless; the waiter falls back to the
    // legacy waitpid-on-grandchild path.  The two fd slots are set to
    // -1 to make accidental closeFd calls harmless.
    {
        jlong aux[3] = { 0, -1, -1 };
        env->SetLongArrayRegion(outAux, 0, 3, aux);
    }

    do {
        if (pipe(diagPipe) != 0) {
            mark_failed_with_errno(env, "could not create diagnostic pipe", result);
            failed = true;
            break;
        }
#if PTY_USE_ANCHOR
        if (pipe(infoPipe) != 0) {
            mark_failed_with_errno(env, "could not create info pipe", result);
            failed = true;
            break;
        }
        if (pipe(syncPipe) != 0) {
            mark_failed_with_errno(env, "could not create sync pipe", result);
            failed = true;
            break;
        }
#endif
        // CLOEXEC on every end so an unrelated fork+exec in the daemon can't
        // leak these.  The anchor explicitly closes daemon-only ends after
        // fork; the grandchild's diag write end closes on successful execve.
        for (int p = 0; p < 2; p++) {
            int f = fcntl(diagPipe[p], F_GETFD);
            if (f != -1) fcntl(diagPipe[p], F_SETFD, f | FD_CLOEXEC);
#if PTY_USE_ANCHOR
            f = fcntl(infoPipe[p], F_GETFD);
            if (f != -1) fcntl(infoPipe[p], F_SETFD, f | FD_CLOEXEC);
            f = fcntl(syncPipe[p], F_GETFD);
            if (f != -1) fcntl(syncPipe[p], F_SETFD, f | FD_CLOEXEC);
#endif
        }

        anchorPid = fork();
        if (anchorPid < 0) {
            mark_failed_with_errno(env, "could not fork", result);
            failed = true;
            break;
        }

#if PTY_USE_ANCHOR
        if (anchorPid == 0) {
            // ===== ANCHOR =====
            // Only async-signal-safe calls until the grandchild path
            // execve's: setsid, ioctl, fork, dup2, close, chdir, _exit,
            // read, write, waitpid, sigaction.  No malloc, no JNI.
            //
            // Reset signal handlers the daemon JVM installed.  Critically,
            // the JVM may set SIGCHLD to SIG_IGN or to a handler that
            // reaps any child via wait(-1) — both would cause our
            // waitpid(grand) below to fail with ECHILD.  SIGPIPE may
            // similarly be SIG_IGN; that one is benign for us but resetting
            // is safer.  Resetting every signal we care about post-fork is
            // standard practice for non-exec'ing forked helpers.
            struct sigaction dfl;
            memset(&dfl, 0, sizeof(dfl));
            dfl.sa_handler = SIG_DFL;
            sigemptyset(&dfl.sa_mask);
            sigaction(SIGCHLD, &dfl, NULL);
            sigaction(SIGPIPE, &dfl, NULL);
            sigaction(SIGTERM, &dfl, NULL);
            sigaction(SIGINT, &dfl, NULL);
            sigaction(SIGHUP, &dfl, NULL);
            sigaction(SIGQUIT, &dfl, NULL);
            // Ignore SIGTTOU and SIGTTIN: tcsetpgrp on the slave below
            // can raise SIGTTOU even though we're the session leader on
            // some kernels (POSIX session-leader exemption is not
            // universal), and SIGTSTP/SIGTTIN/SIGTTOU all default to
            // stopping the process.  We don't read from / write to the
            // tty after the initial setup, so ignoring these is safe.
            struct sigaction ign;
            memset(&ign, 0, sizeof(ign));
            ign.sa_handler = SIG_IGN;
            sigemptyset(&ign.sa_mask);
            sigaction(SIGTTOU, &ign, NULL);
            sigaction(SIGTTIN, &ign, NULL);
            sigaction(SIGTSTP, &ign, NULL);
            // Also unblock SIGCHLD specifically — the JVM blocks it on
            // most threads except the process reaper, and the post-fork
            // signal mask is inherited.
            sigset_t unblock;
            sigemptyset(&unblock);
            sigaddset(&unblock, SIGCHLD);
            pthread_sigmask(SIG_UNBLOCK, &unblock, NULL);

            close(diagPipe[0]);
            close(infoPipe[0]);
            close(syncPipe[1]);
            int diagFd = diagPipe[1];
            int infoFd = infoPipe[1];
            int syncFd = syncPipe[0];

            if (setsid() < 0) { diag_write(diagFd, 'S', errno); _exit(126); }
            if (ioctl(slaveFd, TIOCSCTTY, 0) < 0) { diag_write(diagFd, 'T', errno); _exit(126); }

            pid_t grand = fork();
            if (grand < 0) { diag_write(diagFd, 'A', errno); _exit(126); }

            if (grand == 0) {
                // ===== GRANDCHILD =====
                // Move into our own process group so killProcess's
                // kill(-grandchildPid, sig) targets us (and any
                // descendants we spawn) but not the anchor.  Idempotent
                // with the anchor-side setpgid below.
                setpgid(0, 0);
                close(infoFd);
                close(syncFd);
                if (dup2(slaveFd, 0) < 0) { diag_write(diagFd, '0', errno); _exit(126); }
                if (dup2(slaveFd, 1) < 0) { diag_write(diagFd, '1', errno); _exit(126); }
                if (dup2(stderrWriteFd, 2) < 0) { diag_write(diagFd, '2', errno); _exit(126); }
                if (slaveFd > 2) close(slaveFd);
                if (stderrWriteFd > 2) close(stderrWriteFd);

                if (wdStr != NULL && chdir(wdStr) < 0) {
                    diag_write(diagFd, 'D', errno);
                    _exit(126);
                }
                child_search_and_exec(argv, envp, diagFd);
                // not reached
            }

            // ===== ANCHOR (post-grandchild-fork) =====
            // Mirror the grandchild's setpgid (race-free: whichever side
            // runs first wins, the other is a no-op).
            setpgid(grand, grand);
            // Make the grandchild's pgrp the foreground pgrp of the
            // controlling tty so it receives SIGWINCH/SIGINT/SIGTSTP and
            // can read stdin without SIGTTIN.  The anchor is the session
            // leader, so tcsetpgrp does not raise SIGTTOU on us.
            tcsetpgrp(slaveFd, grand);
            // Drop the slave + stderr-write copies the anchor inherited;
            // the grandchild has its own dup'd copies on fds 0/1/2 and the
            // daemon already closed its slave (see below).  The slave PTY's
            // controlling-terminal status is held alive by this session's
            // s_ttyvp reference, not by any open fd.
            close(slaveFd);
            close(stderrWriteFd);
            // Close diag write end so the daemon can see exec-success EOF
            // (the grandchild's copy has CLOEXEC and closes on execve).
            close(diagFd);

            // Send grandchild PID to daemon.
            int64_t gpid = (int64_t) grand;
            if (full_write(infoFd, &gpid, sizeof(gpid)) != 0) {
                // Daemon went away.  Take the grandchild down with us.
                kill(grand, SIGKILL);
                int dummy;
                do {} while (waitpid(grand, &dummy, 0) == -1 && errno == EINTR);
                _exit(126);
            }

            // Wait for grandchild and forward its exit status.
            int status = 0;
            pid_t wret;
            do { wret = waitpid(grand, &status, 0); } while (wret == -1 && errno == EINTR);
            int32_t code = (int32_t) decode_wait_status(status);
            (void) full_write(infoFd, &code, sizeof(code));
            close(infoFd);

            // Block until the daemon finishes draining the master and
            // closes its end of the sync pipe.  Only THEN do we exit and
            // let the kernel run killjobc() -> VOP_REVOKE on us; by this
            // point t_outq has been fully consumed, so the synchronous
            // tty_flush(FWRITE) discards nothing.
            char tmp;
            ssize_t r;
            do { r = read(syncFd, &tmp, 1); } while (r < 0 && errno == EINTR);
            // r == 0 (EOF, daemon closed its end) or r == 1 (a stray byte)
            // both mean "you may exit now".
            (void) r;
            _exit(0);
        }

        // ===== DAEMON =====
        // Close anchor-only ends.
        close(diagPipe[1]); diagPipe[1] = -1;
        close(infoPipe[1]); infoPipe[1] = -1;
        close(syncPipe[0]); syncPipe[0] = -1;
        // Anchor and grandchild have their own copies of slave + stderrWrite
        // via fork; the daemon doesn't need them.  No deferred-close trick:
        // the anchor is the structural fix, not a held-open slave fd.
        close(slaveFd);
        close(stderrWriteFd);
        slaveClosed = true;

        // Wait for the diag pipe.  EOF means anchor + grandchild exec
        // succeeded; data means a setup or exec failure with stage+errno.
        char stage = 0;
        int childErrno = 0;
        ssize_t stageRead = 0;
        do {
            stageRead = read(diagPipe[0], &stage, 1);
        } while (stageRead == -1 && errno == EINTR);
        if (stageRead == 1) {
            ssize_t r = 0;
            do {
                r = read(diagPipe[0], &childErrno, sizeof(childErrno));
            } while (r == -1 && errno == EINTR);
        }
        close(diagPipe[0]); diagPipe[0] = -1;

        if (stageRead == 1) {
            // Setup or exec failed.  Drain the info pipe (anchor may have
            // already written pid + status if the failure was in the
            // grandchild's exec), close our sync pipe end so the anchor
            // exits, then reap.
            char drain[16];
            while (read(infoPipe[0], drain, sizeof(drain)) > 0) { }
            close(infoPipe[0]); infoPipe[0] = -1;
            close(syncPipe[1]); syncPipe[1] = -1;

            int astatus = 0;
            pid_t wret;
            do {
                wret = waitpid(anchorPid, &astatus, 0);
            } while (wret == -1 && errno == EINTR);

            char msg[512];
            const char* stageName = "child setup failed";
            switch (stage) {
                case 'S': stageName = "setsid failed"; break;
                case 'T': stageName = "TIOCSCTTY failed"; break;
                case 'A': stageName = "could not fork grandchild"; break;
                case '0': stageName = "dup2(stdin) failed"; break;
                case '1': stageName = "dup2(stdout) failed"; break;
                case '2': stageName = "dup2(stderr) failed"; break;
                case 'D': stageName = "could not chdir to working directory"; break;
                case 'X': stageName = "could not execute command"; break;
                case 'P': stageName = "command not found (no PATH in environment)"; break;
                case 'F': stageName = "command not found"; break;
                case 'E': stageName = "could not execute command"; break;
                case 'N': stageName = "not an executable"; break;
            }
            const char* cmd = argc > 0 && argv[0] ? argv[0] : "<null>";
            snprintf(msg, sizeof(msg), "%s: '%s'", stageName, cmd);
            errno = childErrno;
            mark_failed_with_errno(env, msg, result);
            failed = true;
            break;
        }

        // Exec succeeded.  Read the grandchild PID from the info pipe.
        int64_t gpid = 0;
        ssize_t got = full_read(infoPipe[0], &gpid, sizeof(gpid));
        if (got != (ssize_t) sizeof(gpid)) {
            // Should not happen: anchor is contracted to write pid before
            // closing info.  Treat as a setup failure and reap.
            close(infoPipe[0]); infoPipe[0] = -1;
            close(syncPipe[1]); syncPipe[1] = -1;
            int astatus = 0;
            pid_t wret;
            do { wret = waitpid(anchorPid, &astatus, 0); } while (wret == -1 && errno == EINTR);
            mark_failed_with_message(env, "anchor did not deliver child pid", result);
            failed = true;
            break;
        }
        returnPid = (jlong) gpid;

        // Hand info-read and sync-write off to Java; the waiter thread will
        // consume the exit status from infoPipe[0] and release the anchor by
        // closing syncPipe[1].
        jlong aux[3];
        aux[0] = (jlong) anchorPid;
        aux[1] = (jlong) infoPipe[0];
        aux[2] = (jlong) syncPipe[1];
        env->SetLongArrayRegion(outAux, 0, 3, aux);
        infoPipe[0] = -1;
        syncPipe[1] = -1;
#else  // !PTY_USE_ANCHOR — Linux / macOS single-fork path
        // The single-fork pattern: child becomes the session leader and
        // execs the user command directly.  Linux skips the vhangup path
        // for PTYs and macOS empirically preserves bytes through its
        // revoke path, so the FreeBSD-style killjobc flush is not a
        // concern.  The waiter thread will call waitpid(child) directly.
        if (anchorPid == 0) {
            // ===== CHILD =====
            close(diagPipe[0]);
            int diagFd = diagPipe[1];

            if (setsid() < 0) { diag_write(diagFd, 'S', errno); _exit(126); }
            if (ioctl(slaveFd, TIOCSCTTY, 0) < 0) { diag_write(diagFd, 'T', errno); _exit(126); }
            if (dup2(slaveFd, 0) < 0) { diag_write(diagFd, '0', errno); _exit(126); }
            if (dup2(slaveFd, 1) < 0) { diag_write(diagFd, '1', errno); _exit(126); }
            if (dup2(stderrWriteFd, 2) < 0) { diag_write(diagFd, '2', errno); _exit(126); }
            if (slaveFd > 2) close(slaveFd);
            if (stderrWriteFd > 2) close(stderrWriteFd);
            if (wdStr != NULL && chdir(wdStr) < 0) {
                diag_write(diagFd, 'D', errno);
                _exit(126);
            }
            child_search_and_exec(argv, envp, diagFd);
            // not reached
        }

        // ===== DAEMON =====
        close(diagPipe[1]); diagPipe[1] = -1;
        // Linux/macOS don't have the FreeBSD synchronous-flush bug, so
        // the daemon can close slave + stderrWrite immediately.  The
        // child has its own dup'd copies on fds 0/1/2; Java holds master
        // and stderrRead.
        close(slaveFd);
        close(stderrWriteFd);
        slaveClosed = true;

        char stage = 0;
        int childErrno = 0;
        ssize_t stageRead = 0;
        do {
            stageRead = read(diagPipe[0], &stage, 1);
        } while (stageRead == -1 && errno == EINTR);
        if (stageRead == 1) {
            ssize_t r = 0;
            do {
                r = read(diagPipe[0], &childErrno, sizeof(childErrno));
            } while (r == -1 && errno == EINTR);
        }
        close(diagPipe[0]); diagPipe[0] = -1;

        if (stageRead == 1) {
            char msg[512];
            const char* stageName = "child setup failed";
            switch (stage) {
                case 'S': stageName = "setsid failed"; break;
                case 'T': stageName = "TIOCSCTTY failed"; break;
                case '0': stageName = "dup2(stdin) failed"; break;
                case '1': stageName = "dup2(stdout) failed"; break;
                case '2': stageName = "dup2(stderr) failed"; break;
                case 'D': stageName = "could not chdir to working directory"; break;
                case 'X': stageName = "could not execute command"; break;
                case 'P': stageName = "command not found (no PATH in environment)"; break;
                case 'F': stageName = "command not found"; break;
                case 'E': stageName = "could not execute command"; break;
                case 'N': stageName = "not an executable"; break;
            }
            const char* cmd = argc > 0 && argv[0] ? argv[0] : "<null>";
            snprintf(msg, sizeof(msg), "%s: '%s'", stageName, cmd);
            int astatus = 0;
            pid_t wret;
            do {
                wret = waitpid(anchorPid, &astatus, 0);
            } while (wret == -1 && errno == EINTR);
            errno = childErrno;
            mark_failed_with_errno(env, msg, result);
            failed = true;
            break;
        }

        // Exec succeeded.  Return the child's pid.  outAux was already
        // populated with [0, -1, -1] above so the Java attach() sees
        // anchorPid == 0 and uses the legacy waitpid path.
        returnPid = (jlong) anchorPid;
#endif // PTY_USE_ANCHOR
    } while (0);

    // Cleanup.  If the daemon branch ran, slave + stderrWrite are already
    // closed (slaveClosed == true).  Otherwise (pipe-creation error, fork
    // error, or argv/envp allocation failures earlier) we still own them.
    if (failed && !slaveClosed) {
        close(slaveFd);
        close(stderrWriteFd);
    }
    if (diagPipe[0] >= 0) close(diagPipe[0]);
    if (diagPipe[1] >= 0) close(diagPipe[1]);
    if (infoPipe[0] >= 0) close(infoPipe[0]);
    if (infoPipe[1] >= 0) close(infoPipe[1]);
    if (syncPipe[0] >= 0) close(syncPipe[0]);
    if (syncPipe[1] >= 0) close(syncPipe[1]);

    for (jsize i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    for (jsize i = 0; i < envc; i++) free(envp[i]);
    free(envp);
    if (wdStr != NULL) free(wdStr);
    return failed ? 0 : returnPid;
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_waitPid(JNIEnv* env, jclass target, jlong pid, jobject result) {
    int status = 0;
    pid_t ret;
    do {
        ret = waitpid((pid_t) pid, &status, 0);
    } while (ret == -1 && errno == EINTR);
    if (ret == -1) {
        mark_failed_with_errno(env, "could not waitpid", result);
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_closeFd(JNIEnv* env, jclass target, jint fd, jobject result) {
    if (fd < 0) {
        return;
    }
    if (close(fd) != 0) {
        mark_failed_with_errno(env, "could not close fd", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_setPtySize(
        JNIEnv* env, jclass target, jint fd, jint cols, jint rows, jobject result) {
    if (fd < 0) {
        return;
    }
    struct winsize ws;
    ws.ws_col = (unsigned short) cols;
    ws.ws_row = (unsigned short) rows;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    if (ioctl(fd, TIOCSWINSZ, &ws) != 0) {
        mark_failed_with_errno(env, "could not set pty size", result);
    }
}

JNIEXPORT void JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_killProcess(
        JNIEnv* env, jclass target, jlong pid, jint signal, jobject result) {
    if (pid <= 0) {
        mark_failed_with_message(env, "invalid pid", result);
        return;
    }
    if (kill((pid_t) -pid, signal) != 0 && errno != ESRCH) {
        mark_failed_with_errno(env, "could not signal process group", result);
    }
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_nativeRead(
        JNIEnv* env, jclass target,
        jint fd, jbyteArray buf, jint off, jint len,
        jobject result) {
    if (fd < 0) {
        mark_failed_with_message(env, "invalid fd", result);
        return -1;
    }
    jbyte* data = env->GetByteArrayElements(buf, NULL);
    if (data == NULL) {
        mark_failed_with_message(env, "could not access byte array", result);
        return -1;
    }
    ssize_t n;
    do {
        n = read(fd, data + off, (size_t) len);
    } while (n == -1 && errno == EINTR);
    int savedErrno = errno;
    env->ReleaseByteArrayElements(buf, data, 0);
    if (n == -1) {
        if (savedErrno == EIO || savedErrno == ENXIO) {
            return -1;
        }
        errno = savedErrno;
        mark_failed_with_errno(env, "could not read from fd", result);
        return -1;
    }
    if (n == 0) {
        return -1;
    }
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_net_rubygrapefruit_platform_internal_jni_PosixPtyFunctions_nativeWrite(
        JNIEnv* env, jclass target,
        jint fd, jbyteArray buf, jint off, jint len,
        jobject result) {
    if (fd < 0) {
        mark_failed_with_message(env, "invalid fd", result);
        return -1;
    }
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLOUT;
    pfd.revents = 0;
    if (poll(&pfd, 1, 0) > 0 && (pfd.revents & POLLHUP)) {
        errno = EIO;
        mark_failed_with_errno(env, "process has exited", result);
        return -1;
    }
    jbyte* data = env->GetByteArrayElements(buf, NULL);
    if (data == NULL) {
        mark_failed_with_message(env, "could not access byte array", result);
        return -1;
    }
    ssize_t n;
    do {
        n = write(fd, data + off, (size_t) len);
    } while (n == -1 && errno == EINTR);
    int savedErrno = errno;
    env->ReleaseByteArrayElements(buf, data, JNI_ABORT);
    if (n == -1) {
        errno = savedErrno;
        mark_failed_with_errno(env, "could not write to fd", result);
        return -1;
    }
    return (jint) n;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void*) {
    JNIEnv* env;
    jint ret = jvm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        return -1;
    }
    jclass destClass = env->FindClass("net/rubygrapefruit/platform/internal/FileStat");
    fileStatDetailsMethodId = env->GetMethodID(destClass, "details", "(IIIIJJI)V");
    return JNI_VERSION_1_6;
}

#endif
