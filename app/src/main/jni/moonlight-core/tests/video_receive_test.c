// Test only the production receive loop. Stub the socket, clock, and downstream packet owner.
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef __linux__
#define __linux__ 1
#endif
#define VIDEO_UDP_BURST_MAX 16
#define MSG_DONTWAIT 1
#define MSG_WAITFORONE 2
#define POLLIN 1
#define SS_ENC_VIDEO 1
#define MAX_RTP_HEADER_SIZE 16
#define UDP_RECV_POLL_TIMEOUT_MS 100
#define FIRST_FRAME_TIMEOUT_SEC 10
#define ML_ERROR_NO_VIDEO_TRAFFIC 97
#define Limelog(...) ((void)0)
#define LastSocketError() errno
#define LastSocketFail() (-errno)
typedef struct { char bytes[16]; } RTP_PACKET, ENC_VIDEO_HEADER, RTPV_QUEUE_ENTRY;
struct iovec { void *iov_base; size_t iov_len; };
struct mmsghdr { struct {struct iovec *msg_iov; size_t msg_iovlen;} msg_hdr; unsigned msg_len; };
struct pollfd { int fd, events; };
static struct {int packetSize;} StreamConfig = {1024};
static int receiveThread, rtpSocket, EncryptionFeaturesEnabled;
static bool receivedDataFromPeer;
static int now, due[1024], total, consumed, processed, batches, singles, polls;
static int terminal, deadline, interruptions, refused;
static bool noSocketTimeout;
static int interruptDelay;
static uint64_t PltGetMillis(void) {return (uint64_t)now;}
static void terminate(int err) { terminal = err; }
static struct { void (*connectionTerminated)(int); } ListenerCallbacks = {terminate};
static int setNonFatalRecvTimeoutMs(int fd, int ms) { return noSocketTimeout ? -1 : 0; }
static bool PltIsThreadInterrupted(int *thread) {
    assert(now <= 10100 && batches < 3000);
    return total > 0 && processed == total;
}
static int64_t checkPartialFrameDeadline(void) {
    if (deadline >= 0 && now >= deadline) deadline = -1;
    return deadline < 0 ? -1 : (deadline - now) * 1000;
}
static bool waitReady(int timeout) {
    if (consumed < total && due[consumed] <= now + timeout) {
        if (due[consumed] > now) now = due[consumed];
        return true;
    }
    now += timeout;
    return false;
}
static int receiveOne(char *data) {
    assert(consumed < total && due[consumed] <= now);
    memcpy(data, &consumed, sizeof(consumed));
    consumed++;
    return 64;
}
static int recvmmsg(int fd, struct mmsghdr *msgs, int slots, int flags, void *timeout) {
    batches++;
    if (interruptions > 0) {interruptions--; now += interruptDelay; errno = EINTR; return -1;}
    if (refused > 0) {refused--; errno = ECONNREFUSED; return -1;}
    bool block = flags == MSG_WAITFORONE;
    if (block) assert(!noSocketTimeout && deadline < 0);
    if (!waitReady(block ? UDP_RECV_POLL_TIMEOUT_MS : 0)) {errno = EAGAIN; return -1;}
    int n = 0;
    while (n < slots && waitReady(0)) {
        assert(msgs[n].msg_hdr.msg_iovlen == 1 && msgs[n].msg_hdr.msg_iov->iov_len >= 64);
        msgs[n].msg_len = receiveOne(msgs[n].msg_hdr.msg_iov->iov_base);
        n++;
    }
    return n;
}
static int recvfrom(int fd, char *buf, int size, int flags, void *addr, void *len) {
    singles++;
    if (!waitReady(0)) {errno = EAGAIN; return -1;}
    return receiveOne(buf);
}
static int pollSockets(struct pollfd *pfd, int count, int timeout) {
    polls++;
    return waitReady(timeout);
}
static int recvUdpSocket(int fd, char *buf, int size, bool useSelect) {
    singles++;
    if (useSelect) polls++;
    return waitReady(UDP_RECV_POLL_TIMEOUT_MS) ? receiveOne(buf) : 0;
}
static int processVideoDatagram(int size, const char *data, char **owner,
                                int plain, int minSize, bool encrypted, uint64_t waitStartOrElapsed) {
    int id;
    memcpy(&id, data, sizeof(id));
    assert(id == processed && size == 64 && now == due[id]);
    assert(encrypted == !!EncryptionFeaturesEnabled && *owner);
    receivedDataFromPeer = true;
    free(*owner);   // Simulate a packet accepted by the video queue.
    *owner = NULL;
    processed++;
    return 1;
}
#include "receive-loop.inc"

static void run(const char *name, int count, int perBurst, bool baseline) {
    now = consumed = processed = singles = batches = polls = terminal = 0;
    receivedDataFromPeer = false;
    total = count;
    for (int i = 0; i < count; i++) due[i] = 10 + (i / perBurst) * 2;
    VideoReceiveThreadProc(NULL);
    assert(processed == count && consumed == count);
    if (!count) {
        assert(terminal == ML_ERROR_NO_VIDEO_TRAFFIC && now == 10000);
        assert(singles == (baseline ? 100 : 0));
    } else {
        assert(terminal == 0);
    }
    if (!strcmp(name, "isolated")) assert(batches + singles == (baseline ? 2000 : 1000));
    if (!strcmp(name, "bursts16")) assert(batches + singles == (baseline ? 192 : 64));
    printf("%s,%s,%d,%d,%d,%d,%d,%d\n", baseline ? "v324" : "new", name, count, batches, singles, polls, now, terminal);
}
int main(int argc, char **argv) {
    bool baseline = argc > 1 && !strcmp(argv[1], "baseline");
    puts("version,scenario,packets,batch_calls,single_calls,poll_calls,simulated_ms,terminal_code");
    deadline = -1;
    run("isolated", 1000, 1, baseline);
    run("bursts16", 1024, 16, baseline);
    EncryptionFeaturesEnabled = SS_ENC_VIDEO;
    run("encrypted_bursts", 1024, 16, baseline);
    EncryptionFeaturesEnabled = 0;
    deadline = 4;
    run("partial_deadline_before_packet", 1, 1, baseline);
    assert(polls > 0);
    noSocketTimeout = true;
    run("poll_fallback", 1, 1, baseline);
    assert(polls > 0);
    noSocketTimeout = false;
    interruptions = refused = 1;
    run("interrupted_and_icmp", 4, 1, baseline);
    run("no_traffic", 0, 1, baseline);
    if (!baseline) {
        interruptions = 250;
        interruptDelay = 50;
        run("repeated_interruptions_no_traffic", 0, 1, false);
        assert(interruptions == 50 && batches == 200);
    }
    return 0;
}
