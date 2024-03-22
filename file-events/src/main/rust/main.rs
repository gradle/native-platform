use jni::JNIEnv;

use jni::objects::{JClass, JString};

use jni::sys::jstring;

#[no_mangle]
pub extern "system" fn Java_net_rubygrapefruit_platform_rust_RustJniCall_hello<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
) -> jstring {
    let input: String = env.get_string(&input)
        .expect("Couldn't get java string!").into();
    let output = env.new_string(format!("Hello, {}", input))
        .expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_net_rubygrapefruit_platform_internal_jni_AbstractNativeFileEventFunctions_getVersion0<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>
) -> jstring {
    let output = env.new_string("100fb08df4bc3b14c8652ba06237920a3bd2aa13389f12d3474272988ae205f9")
        .expect("Couldn't create java string!");
    output.into_raw()
}
