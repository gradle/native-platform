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

#ifndef __INCLUDE_WIN_H__
#define __INCLUDE_WIN_H__

#ifdef _WIN32

#include "generic.h"
#include <Shlwapi.h>
#include <wchar.h>
#include <windows.h>

//
// Converts a Java string to a UNICODE path, including the Long Path prefix ("\\?\")
// so that the resulting path supports paths longer than MAX_PATH (260 characters)
//
extern wchar_t* java_to_wchar_path(JNIEnv* env, jstring string);

//
// Converts a UJNICODE path to a Java string, removing the Long Path prefix ("\\?\")
// if present
//
extern jstring wchar_to_java_path(JNIEnv* env, const wchar_t* string);

//
// Returns 'true' if the path of the form "X:\", where 'X' is a drive letter.
//
extern bool is_path_absolute_local(wchar_t* path, int path_len);

//
// Returns 'true' if the path is of the form "\\server\share", i.e. is a UNC path.
//
extern bool is_path_absolute_unc(wchar_t* path, int path_len);

//
// Returns a UTF-16 string that is the concatenation of |prefix| and |path|.
//
extern wchar_t* add_prefix(wchar_t* path, int path_len, wchar_t* prefix);

//
// Returns a UTF-16 string that is the concatenation of |path| and |suffix|.
//
extern wchar_t* add_suffix(wchar_t* path, int path_len, wchar_t* suffix);

#endif

#endif
