mod version;

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
