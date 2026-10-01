#ifndef AUTH_GUARD_H
#define AUTH_GUARD_H

#include <cstddef>
#include <cstdint>

void sha256_bytes(const unsigned char* data, size_t length, unsigned char out_digest[32]);

#endif
