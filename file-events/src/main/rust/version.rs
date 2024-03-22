mod bindings {
    include!(concat!(env!("OUT_DIR"), "/bindings.rs"));
}

use std::str::from_utf8;
use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use crate::version::bindings::NATIVE_VERSION;

#[no_mangle]
pub extern "system" fn Java_net_rubygrapefruit_platform_internal_jni_AbstractNativeFileEventFunctions_getVersion0<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>
) -> jstring {
    let my_string = from_utf8(NATIVE_VERSION)
        .expect("Couldn't load native version constant")
        .trim_end_matches('\0');
    let output = env.new_string(my_string)
        .expect("Couldn't create java string!");
    output.into_raw()
}
