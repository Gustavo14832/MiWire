#include "perf_hint.h"

#include <algorithm>
#include <unistd.h>
#include <android/log.h>

#include "ndk_api.h"

#define LOG_TAG "VulkanRenderer"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace vkr {

PerfHint::~PerfHint() {
    stop();
}

void PerfHint::start(const NdkApi* ndk, int64_t targetDurationNs) {
    std::lock_guard<std::mutex> lock(mutex_);
    ndk_ = ndk;
    targetDurationNs_ = std::max<int64_t>(500'000, targetDurationNs);
    stopped_ = false;
}

void PerfHint::addThread() {
    const int32_t tid = static_cast<int32_t>(gettid());
    std::lock_guard<std::mutex> lock(mutex_);
    if (stopped_ || !ndk_ || !ndk_->APerformanceHint_getManager ||
            !ndk_->APerformanceHint_createSession || !ndk_->APerformanceHint_reportActualWorkDuration ||
            !ndk_->APerformanceHint_closeSession) {
        return;
    }

    for (size_t i = 0; i < threadCount_; i++) {
        if (threadIds_[i] == tid) {
            return;
        }
    }
    if (threadCount_ >= kMaxThreads) {
        return;
    }
    threadIds_[threadCount_++] = tid;

    if (!session_) {
        APerformanceHintManager* manager = ndk_->APerformanceHint_getManager();
        if (manager) {
            session_ = ndk_->APerformanceHint_createSession(manager, threadIds_.data(), threadCount_, targetDurationNs_);
            if (session_) {
                ALOGI("ADPF hint session started (target %.2f ms, %zu thread(s))",
                      targetDurationNs_ / 1e6, threadCount_);
            }
        }
        return;
    }

    if (ndk_->APerformanceHint_setThreads) {
        if (ndk_->APerformanceHint_setThreads(session_, threadIds_.data(), threadCount_) == 0) {
            ALOGI("ADPF hint session updated (%zu threads)", threadCount_);
            return;
        }
    }

    // Fallback for API 31-33 without APerformanceHint_setThreads: recreate session with both threads.
    ndk_->APerformanceHint_closeSession(session_);
    session_ = nullptr;
    APerformanceHintManager* manager = ndk_->APerformanceHint_getManager();
    if (manager) {
        session_ = ndk_->APerformanceHint_createSession(manager, threadIds_.data(), threadCount_, targetDurationNs_);
    }
}

void PerfHint::reportWork(int64_t durationNs) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!session_ || !ndk_ || !ndk_->APerformanceHint_reportActualWorkDuration) {
        return;
    }
    const int64_t clampedNs = std::clamp<int64_t>(durationNs, 100'000, 16'000'000);
    ndk_->APerformanceHint_reportActualWorkDuration(session_, clampedNs);
}

void PerfHint::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    stopped_ = true;
    if (session_ && ndk_ && ndk_->APerformanceHint_closeSession) {
        ndk_->APerformanceHint_closeSession(session_);
    }
    session_ = nullptr;
    threadCount_ = 0;
}

}  // namespace vkr
