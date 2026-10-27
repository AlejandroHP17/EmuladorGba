#include <jni.h>
#include <stdlib.h>
#include <time.h>
#include <android/log.h>

#include <mgba/core/blip_buf.h>
#include <mgba/core/core.h>
#include <mgba/core/serialize.h>
#include <mgba-util/vfs.h>

#define GBA_WIDTH 240
#define GBA_HEIGHT 160
#define GBA_PIXELS (GBA_WIDTH * GBA_HEIGHT)
#define AUDIO_RATE 48000
#define AUDIO_FRAME_SAMPLES 804
#define AUDIO_BUFFER_SAMPLES (AUDIO_FRAME_SAMPLES * 2)
#define LOG_TAG "EmuladorGba"

struct Machine {
    struct mCore* core;
    color_t video[GBA_PIXELS];
    jint argb[GBA_PIXELS];
    jshort pcm[AUDIO_BUFFER_SAMPLES * 2];
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
    core->setAudioBufferSize(core, AUDIO_BUFFER_SAMPLES);
    double clock = core->frequency(core);
    blip_set_rates(core->getAudioChannel(core, 0), clock, AUDIO_RATE);
    blip_set_rates(core->getAudioChannel(core, 1), clock, AUDIO_RATE);
    machine->fpsWindowStart = nowNanos();
    return (jlong) (intptr_t) machine;
}

JNIEXPORT jint JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeRunFrame(
        JNIEnv* env, jobject thiz, jlong handle, jintArray out, jint keys, jshortArray audioOut) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return 0;
    struct mCore* core = machine->core;
    core->setKeys(core, (uint32_t) keys);
    core->runFrame(core);
    struct blip_t* left = core->getAudioChannel(core, 0);
    struct blip_t* right = core->getAudioChannel(core, 1);
    jint produced = 0;
    if (left && right) {
        int avail = blip_samples_avail(left);
        if (avail > AUDIO_BUFFER_SAMPLES) avail = AUDIO_BUFFER_SAMPLES;
        if (avail > 0) {
            blip_read_samples(left, machine->pcm, avail, 1);
            blip_read_samples(right, machine->pcm + 1, avail, 1);
            produced = avail;
            (*env)->SetShortArrayRegion(env, audioOut, 0, produced * 2, machine->pcm);
        }
    }
    for (int i = 0; i < GBA_PIXELS; ++i) {
        uint32_t abgr = machine->video[i];
        machine->argb[i] = (jint) (0xFF000000u
                                   | ((abgr & 0x000000FFu) << 16)
                                   | (abgr & 0x0000FF00u)
                                   | ((abgr & 0x00FF0000u) >> 16));
    }
    (*env)->SetIntArrayRegion(env, out, 0, GBA_PIXELS, machine->argb);
    logFramesPerSecond(machine);
    return produced;
}

JNIEXPORT jbyteArray JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeExportState(JNIEnv* env, jobject thiz, jlong handle) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return NULL;
    struct VFile* vf = VFileMemChunk(NULL, 0);
    if (!vf) return NULL;
    bool ok = mCoreSaveStateNamed(machine->core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    ssize_t size = ok ? vf->size(vf) : 0;
    jbyteArray out = NULL;
    if (ok && size > 0) {
        void* mapped = vf->map(vf, (size_t) size, MAP_READ);
        if (mapped) {
            out = (*env)->NewByteArray(env, (jsize) size);
            if (out) {
                (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, mapped);
            }
            vf->unmap(vf, mapped, (size_t) size);
        }
    }
    vf->close(vf);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeImportState(
        JNIEnv* env, jobject thiz, jlong handle, jbyteArray state) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine || !state) return JNI_FALSE;
    jsize size = (*env)->GetArrayLength(env, state);
    if (size <= 0) return JNI_FALSE;
    jbyte* bytes = (*env)->GetByteArrayElements(env, state, NULL);
    if (!bytes) return JNI_FALSE;
    struct VFile* vf = VFileFromConstMemory(bytes, (size_t) size);
    bool ok = vf && mCoreLoadStateNamed(machine->core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    if (vf) vf->close(vf);
    (*env)->ReleaseByteArrayElements(env, state, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeExportCartridge(JNIEnv* env, jobject thiz, jlong handle) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return NULL;
    void* sram = NULL;
    size_t size = machine->core->savedataClone(machine->core, &sram);
    if (!size || !sram) return NULL;
    jbyteArray out = (*env)->NewByteArray(env, (jsize) size);
    if (out) {
        (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, sram);
    }
    free(sram);
    return out;
}

JNIEXPORT void JNICALL
Java_pelkidev_com_mx_emuladorgba_data_MgbaCore_nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    struct Machine* machine = (struct Machine*) (intptr_t) handle;
    if (!machine) return;
    mCoreConfigDeinit(&machine->core->config);
    machine->core->deinit(machine->core);
    free(machine);
}
