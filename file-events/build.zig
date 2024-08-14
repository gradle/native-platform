const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const lib = b.addSharedLibrary(.{ .name = "file-events", .target = target, .optimize = optimize });

    const env = std.process.getEnvMap(b.allocator) catch unreachable;
    const java_home = env.get("JAVA_HOME") orelse unreachable;
    const java_include_path = std.fmt.allocPrint(b.allocator, "{s}/include", .{java_home}) catch unreachable;
    const java_darwin_include_path = std.fmt.allocPrint(b.allocator, "{s}/include/darwin", .{java_home}) catch unreachable;

    // Add include directories
    lib.addIncludePath(b.path("build/generated/sources/headers/java/main"));
    lib.addIncludePath(b.path("build/generated/version/header"));
    lib.addIncludePath(b.path("src/file-events/headers"));
    lib.addSystemIncludePath(.{ .cwd_relative = java_include_path });
    lib.addSystemIncludePath(.{ .cwd_relative = java_darwin_include_path });

    // Set common C++ compiler flags
    const cpp_args = [_][]const u8{
        "--std=c++17",
        "-g",
        "-pedantic",
        "-Wall",
        "-Wextra",
        "-Wformat=2",
        "-Werror",
        "-Wno-deprecated-declarations",
        "-Wno-format-nonliteral",
        "-Wno-unguarded-availability-new",
    };

    // Add source files
    lib.addCSourceFiles(.{
        .files = &.{
            "src/file-events/cpp/apple_fsnotifier.cpp",
            "src/file-events/cpp/file-events-version.cpp",
            "src/file-events/cpp/generic_fsnotifier.cpp",
            "src/file-events/cpp/jni_support.cpp",
            "src/file-events/cpp/linux_fsnotifier.cpp",
            "src/file-events/cpp/logging.cpp",
            "src/file-events/cpp/services.cpp",
            "src/file-events/cpp/win_fsnotifier.cpp",
        },
        .flags = &cpp_args,
    });

    // Link against libc and libstdc++
    lib.linkLibC();
    lib.linkLibCpp();

    // // Platform-specific configurations
    // if (target.os.tag == .macos or target.os.tag == .linux) {
    //     lib.c_flags.append("-pthread");

    //     // Set linker flags
    //     lib.linker_flags.append("-pthread");
    // } else if (target.os.tag == .windows) {
    //     lib.c_flags = &[_][]const u8{
    //         "/DEBUG",
    //         "/permissive-",
    //         "/EShc",
    //         "/Zi",
    //         "/FS",
    //         "/Zc:inline",
    //         "/Zc:throwingNew",
    //         "/W3",
    //         "/WX",
    //         "/D_SILENCE_CXX17_CODECVT_HEADER_DEPRECATION_WARNING",
    //     };

    //     // Set linker flags
    //     lib.linker_flags = &[_][]const u8{
    //         "/DEBUG:FULL",
    //     };
    // }

    // lib.verbose_cc = true;
    // lib.verbose_link = true;

    const install = b.addInstallArtifact(lib, .{});

    // Ensure the library is built
    const build_step = b.step("build", "Build the file-events shared library");
    build_step.dependOn(&install.step);
}
