#include <jni.h>
#include <stdlib.h>
#include <time.h>
#include <android/log.h>

#include <mgba/core/blip_buf.h>
#include <mgba/core/core.h>
#include <mgba-util/vfs.h>

#define GBA_WIDTH 240
#define GBA_HEIGHT 160
#define GBA_PIXELS (GBA_WIDTH * GBA_HEIGHT)
#define LOG_TAG "EmuladorGba"

struct Machine {
    struct mCore* core;
    color_t video[GBA_PIXELS];
    jint argb[GBA_PIXELS];
    int64_t fpsWindowStart;
    int framesInWindow;
};

static int64_t nowNanos(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (int64_t) now.tv_sec * 1000000000LL + now.tv_nsec;
}

static void logFramesPerSecond(struct Machine* machine) {
    machine->framesInWindow += 1;
    int64_t now = nowNanos();
    int64_t elapsed = now - machine->fpsWindowStart;
    if (elapsed < 1000000000LL) return;
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "fps=%.1f",
                        machine->framesInWindow * 1e9 / (double) elapsed);
    machine->fpsWindowStart = now;
    machine->framesInWindow = 0;
}

JNIEXPORT jlong JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeCreate(JNIEnv* env, jobject thiz, jbyteArray rom) {
    jsize size = (*env)->GetArrayLength(env, rom);
    jbyte* bytes = (*env)->GetByteArrayElements(env, rom, NULL);
    if (!bytes) return 0;
    struct VFile* vf = VFileMemChunk(bytes, (size_t) size);
    (*env)->ReleaseByteArrayElements(env, rom, bytes, JNI_ABORT);
    if (!vf) return 0;

    struct mCore* core = mCoreFindVF(vf);
    if (!core || !core->init(core)) {
        vf->close(vf);
        return 0;
    }
    struct Machine* machine = calloc(1, sizeof(struct Machine));
    if (!machine) {
        core->deinit(core);
        vf->close(vf);
        return 0;
    }
    machine->core = core;
    mCoreInitConfig(core, NULL);
    core->setVideoBuffer(core, machine->video, GBA_WIDTH);
    if (!core->loadROM(core, vf)) {
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        vf->close(vf);
        free(machine);
        return 0;
    }
    core->reset(core);
    machine->fpsWindowStart = nowNanos();
    return (jlong) (intptr_t) machine;
}

JNIEXPORT void JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeRunFrame(
        JNIEnv* env, jobject thiz, jlong handle, jintArray out, jint keys) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return;
    struct mCore* core = machine->core;
    (void) keys;
    core->runFrame(core);
    blip_clear(core->getAudioChannel(core, 0));
    blip_clear(core->getAudioChannel(core, 1));
    for (int i = 0; i < GBA_PIXELS; ++i) {
        uint32_t abgr = machine->video[i];
        machine->argb[i] = (jint) (0xFF000000u
                                   | ((abgr & 0x000000FFu) << 16)
                                   | (abgr & 0x0000FF00u)
                                   | ((abgr & 0x00FF0000u) >> 16));
    }
    (*env)->SetIntArrayRegion(env, out, 0, GBA_PIXELS, machine->argb);
    logFramesPerSecond(machine);
}

JNIEXPORT void JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return;
    mCoreConfigDeinit(&machine->core->config);
    machine->core->deinit(machine->core);
    free(machine);
}
