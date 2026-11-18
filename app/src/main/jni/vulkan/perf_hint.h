#pragma once

#include <array>
#include <cstdint>
#include <mutex>

struct APerformanceHintSession;
struct NdkApi;

namespace vkr {

// Wraps Android's ADPF PerformanceHintSession with a 2.0 ms pure CPU render target and
// tracks both the render thread and AImageReader callback thread so the scheduler boosts
// Cortex-A55 clocks promptly without being fooled by blocking fence/swapchain waits.
class PerfHint {
public:
    static constexpr int64_t kDefaultTargetNs = 2'000'000;
    static constexpr size_t kMaxThreads = 4;

    PerfHint() = default;
    ~PerfHint();

    PerfHint(const PerfHint&) = delete;
    PerfHint& operator=(const PerfHint&) = delete;

    void start(const NdkApi* ndk, int64_t targetDurationNs = kDefaultTargetNs);
    void addThread();
    void reportWork(int64_t durationNs);
    void stop();

private:
    const NdkApi* ndk_ = nullptr;
    int64_t targetDurationNs_ = kDefaultTargetNs;
    std::mutex mutex_;
    APerformanceHintSession* session_ = nullptr;
    std::array<int32_t, kMaxThreads> threadIds_ {};
    size_t threadCount_ = 0;
    bool stopped_ = false;
};

}  // namespace vkr
