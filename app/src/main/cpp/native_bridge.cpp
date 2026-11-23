#include <jni.h>

#include <cstdint>
#include <vector>

#include "audio_engine.h"

namespace {
inline AudioEngine* asEngine(jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle);
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeCreate(JNIEnv* /*env*/, jobject /*thiz*/,
                                                  jint sampleRate, jint channels,
                                                  jint bitrate) {
    return reinterpret_cast<jlong>(new AudioEngine(sampleRate, channels, bitrate));
}

JNIEXPORT void JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/,
                                                   jlong handle) {
    delete asEngine(handle);
}

JNIEXPORT jint JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeFrameSize(JNIEnv* /*env*/, jobject /*thiz*/,
                                                     jlong handle) {
    AudioEngine* engine = asEngine(handle);
    return engine != nullptr ? engine->frameSize() : 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeEncode(JNIEnv* env, jobject /*thiz*/,
                                                  jlong handle, jshortArray pcm) {
    AudioEngine* engine = asEngine(handle);
    if (engine == nullptr || pcm == nullptr) return nullptr;

    const jsize len = env->GetArrayLength(pcm);
    jshort* data = env->GetShortArrayElements(pcm, nullptr);
    if (data == nullptr) return nullptr;

    std::vector<uint8_t> out = engine->encode(reinterpret_cast<int16_t*>(data),
                                              static_cast<int>(len));
    env->ReleaseShortArrayElements(pcm, data, JNI_ABORT);

    if (out.empty()) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()),
                                reinterpret_cast<const jbyte*>(out.data()));
    }
    return result;
}

JNIEXPORT jshortArray JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeDecode(JNIEnv* env, jobject /*thiz*/,
                                                  jlong handle, jbyteArray packet,
                                                  jboolean fec) {
    AudioEngine* engine = asEngine(handle);
    if (engine == nullptr) return nullptr;

    std::vector<uint8_t> in;
    if (packet != nullptr) {
        const jsize len = env->GetArrayLength(packet);
        in.resize(static_cast<size_t>(len));
        env->GetByteArrayRegion(packet, 0, len, reinterpret_cast<jbyte*>(in.data()));
    }

    std::vector<int16_t> out = engine->decode(in.empty() ? nullptr : in.data(),
                                              static_cast<int>(in.size()),
                                              fec == JNI_TRUE);
    jshortArray result = env->NewShortArray(static_cast<jsize>(out.size()));
    if (result != nullptr) {
        env->SetShortArrayRegion(result, 0, static_cast<jsize>(out.size()),
                                 reinterpret_cast<const jshort*>(out.data()));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_sotak_app_audio_AudioEngine_nativePushPacket(JNIEnv* env, jobject /*thiz*/,
                                                      jlong handle, jint seq,
                                                      jbyteArray packet) {
    AudioEngine* engine = asEngine(handle);
    if (engine == nullptr || packet == nullptr) return;

    const jsize len = env->GetArrayLength(packet);
    std::vector<uint8_t> in(static_cast<size_t>(len));
    env->GetByteArrayRegion(packet, 0, len, reinterpret_cast<jbyte*>(in.data()));
    engine->pushPacket(static_cast<uint32_t>(seq), in.data(), in.size());
}

JNIEXPORT jshortArray JNICALL
Java_com_sotak_app_audio_AudioEngine_nativePopFrame(JNIEnv* env, jobject /*thiz*/,
                                                    jlong handle) {
    AudioEngine* engine = asEngine(handle);
    if (engine == nullptr) return nullptr;

    std::vector<int16_t> out;
    if (!engine->popFrame(out)) return nullptr;

    jshortArray result = env->NewShortArray(static_cast<jsize>(out.size()));
    if (result != nullptr) {
        env->SetShortArrayRegion(result, 0, static_cast<jsize>(out.size()),
                                 reinterpret_cast<const jshort*>(out.data()));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_sotak_app_audio_AudioEngine_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/,
                                                 jlong handle) {
    AudioEngine* engine = asEngine(handle);
    if (engine != nullptr) engine->reset();
}

}  // extern "C"
