#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <numeric>

namespace vkr {

// Only feed timestamps confirmed by the presentation engine. Samples cover
// all content, including idle screens and interruptions upstream.
class DisplayCadence {
public:
    struct Snapshot {
        std::array<int64_t, 600> intervals {};
        size_t count = 0;
        size_t longGaps = 0;
        int64_t longestGapNs = 0;

        // Called on an overlay snapshot after releasing the renderer lock.
        double sortAndGetLowFps() {
            if (count < 100) return 0;
            const auto end = intervals.begin() + count;
            std::sort(intervals.begin(), end);
            const auto slowest = count / 100;
            const double total = std::accumulate(end - slowest, end, 0.0);
            return 1.0e9 * slowest / total;
        }
    };

    void record(int64_t timestamp, int64_t period) {
        if (timestamp <= last_) return;
        if (last_ > 0) {
            const int64_t delta = timestamp - last_;
            intervals_[next_] = delta;
            next_ = (next_ + 1) % intervals_.size();
            count_ = std::min(count_ + 1, intervals_.size());
            if (period > 0 && delta > period + period / 2) {
                gaps_.push_back({timestamp, delta});
            }
        }
        last_ = timestamp;
        while (!gaps_.empty() && gaps_.front().end <= timestamp - minuteNs) {
            gaps_.pop_front();
        }
    }

    Snapshot snapshot(int64_t now) const {
        Snapshot result;
        std::copy_n(intervals_.begin(), count_, result.intervals.begin());
        result.count = count_;
        for (const auto& gap : gaps_) {
            if (gap.end > now - minuteNs) {
                ++result.longGaps;
                result.longestGapNs = std::max(result.longestGapNs, gap.duration);
            }
        }
        return result;
    }

private:
    struct Gap { int64_t end, duration; };
    static constexpr int64_t minuteNs = 60'000'000'000;
    std::array<int64_t, 600> intervals_ {};
    std::deque<Gap> gaps_;
    size_t next_ = 0, count_ = 0;
    int64_t last_ = 0;
};

} // namespace vkr
