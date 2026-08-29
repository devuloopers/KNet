#ifndef KNET_HEV_BRIDGE_H
#define KNET_HEV_BRIDGE_H

#include <net/if.h>
#include <arpa/inet.h>
#include <CommonCrypto/CommonDigest.h>
#include <stdint.h>
#include <string.h>
#include <sys/socket.h>

/** Find the utun descriptor already owned by NetworkExtension without creating or claiming a socket. */
static inline int knet_find_packet_tunnel_file_descriptor(void) {
    const int knet_system_control_protocol = 2; /* SYSPROTO_CONTROL */
    const int knet_utun_interface_name_option = 2; /* UTUN_OPT_IFNAME */
    for (int descriptor = 0; descriptor <= 1024; descriptor++) {
        struct sockaddr_storage address;
        socklen_t address_length = sizeof(address);
        memset(&address, 0, sizeof(address));
        if (getpeername(descriptor, (struct sockaddr *)&address, &address_length) != 0) continue;
        if (((struct sockaddr *)&address)->sa_family != AF_SYSTEM) continue;

        char interface_name[IFNAMSIZ];
        socklen_t interface_name_length = sizeof(interface_name);
        memset(interface_name, 0, sizeof(interface_name));
        if (getsockopt(
                descriptor,
                knet_system_control_protocol,
                knet_utun_interface_name_option,
                interface_name,
                &interface_name_length
            ) == 0 && strncmp(interface_name, "utun", 4) == 0) {
            return descriptor;
        }
    }
    return -1;
}

int hev_socks5_tunnel_main_from_str(
    const unsigned char *config_str,
    unsigned int config_len,
    int tun_fd
);
void hev_socks5_tunnel_quit(void);

static inline int knet_ip_address_family(const char *value) {
    struct in_addr ipv4;
    struct in6_addr ipv6;
    if (inet_pton(AF_INET, value, &ipv4) == 1) return 4;
    if (inet_pton(AF_INET6, value, &ipv6) == 1) return 6;
    return 0;
}

static inline void knet_sha256(
    const unsigned char *input,
    unsigned int input_len,
    unsigned char output[CC_SHA256_DIGEST_LENGTH]
) {
    CC_SHA256(input, input_len, output);
}

#endif
