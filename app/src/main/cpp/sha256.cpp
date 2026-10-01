#include "auth_guard.h"

#include <cstring>

namespace {

constexpr size_t kSha256Block = 64;

const uint32_t kRoundConstants[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
    0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
    0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
};

inline uint32_t rotateRight(uint32_t value, unsigned bits) {
    return (value >> bits) | (value << (32 - bits));
}

struct Sha256Context {
    uint32_t state[8];
    uint64_t bitLength;
    unsigned char buffer[kSha256Block];
    size_t bufferLength;
};

void sha256Init(Sha256Context& ctx) {
    ctx.state[0] = 0x6a09e667; ctx.state[1] = 0xbb67ae85;
    ctx.state[2] = 0x3c6ef372; ctx.state[3] = 0xa54ff53a;
    ctx.state[4] = 0x510e527f; ctx.state[5] = 0x9b05688c;
    ctx.state[6] = 0x1f83d9ab; ctx.state[7] = 0x5be0cd19;
    ctx.bitLength = 0;
    ctx.bufferLength = 0;
}

void sha256Transform(Sha256Context& ctx, const unsigned char* block) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = (static_cast<uint32_t>(block[i * 4]) << 24) |
               (static_cast<uint32_t>(block[i * 4 + 1]) << 16) |
               (static_cast<uint32_t>(block[i * 4 + 2]) << 8) |
               static_cast<uint32_t>(block[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        const uint32_t s0 = rotateRight(w[i - 15], 7) ^ rotateRight(w[i - 15], 18) ^ (w[i - 15] >> 3);
        const uint32_t s1 = rotateRight(w[i - 2], 17) ^ rotateRight(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = ctx.state[0], b = ctx.state[1], c = ctx.state[2], d = ctx.state[3];
    uint32_t e = ctx.state[4], f = ctx.state[5], g = ctx.state[6], h = ctx.state[7];
    for (int i = 0; i < 64; ++i) {
        const uint32_t s1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25);
        const uint32_t ch = (e & f) ^ (~e & g);
        const uint32_t t1 = h + s1 + ch + kRoundConstants[i] + w[i];
        const uint32_t s0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22);
        const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        const uint32_t t2 = s0 + maj;
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    ctx.state[0] += a; ctx.state[1] += b; ctx.state[2] += c; ctx.state[3] += d;
    ctx.state[4] += e; ctx.state[5] += f; ctx.state[6] += g; ctx.state[7] += h;
}

void sha256Update(Sha256Context& ctx, const unsigned char* data, size_t length) {
    ctx.bitLength += static_cast<uint64_t>(length) * 8;
    while (length > 0) {
        const size_t take = (ctx.bufferLength + length <= kSha256Block)
            ? length : kSha256Block - ctx.bufferLength;
        std::memcpy(ctx.buffer + ctx.bufferLength, data, take);
        ctx.bufferLength += take;
        data += take;
        length -= take;
        if (ctx.bufferLength == kSha256Block) {
            sha256Transform(ctx, ctx.buffer);
            ctx.bufferLength = 0;
        }
    }
}

void sha256Final(Sha256Context& ctx, unsigned char out_digest[32]) {
    const uint64_t totalBits = ctx.bitLength;
    unsigned char pad = 0x80;
    sha256Update(ctx, &pad, 1);
    const unsigned char zero = 0x00;
    while (ctx.bufferLength != 56) sha256Update(ctx, &zero, 1);
    unsigned char lengthBytes[8];
    for (int i = 0; i < 8; ++i) {
        lengthBytes[i] = static_cast<unsigned char>((totalBits >> (56 - i * 8)) & 0xFF);
    }
    ctx.bitLength += 64 * 8;
    sha256Update(ctx, lengthBytes, 8);
    for (int i = 0; i < 8; ++i) {
        out_digest[i * 4] = static_cast<unsigned char>((ctx.state[i] >> 24) & 0xFF);
        out_digest[i * 4 + 1] = static_cast<unsigned char>((ctx.state[i] >> 16) & 0xFF);
        out_digest[i * 4 + 2] = static_cast<unsigned char>((ctx.state[i] >> 8) & 0xFF);
        out_digest[i * 4 + 3] = static_cast<unsigned char>(ctx.state[i] & 0xFF);
    }
}

}  // namespace

void sha256_bytes(const unsigned char* data, size_t length, unsigned char out_digest[32]) {
    Sha256Context ctx;
    sha256Init(ctx);
    sha256Update(ctx, data, length);
    sha256Final(ctx, out_digest);
}
