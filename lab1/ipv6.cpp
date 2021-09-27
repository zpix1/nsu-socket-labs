#include <cstdio>
#include <cstdlib>
#include <sys/ioctl.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <ctime>
#include <netinet/in.h>
#include <cstring>
#include <iostream>
#include <arpa/inet.h>
#include <netdb.h>
#include <unordered_map>
#include <algorithm>
#include <random>
#include <sys/socket.h>
#include <netinet/in.h>
#include <net/if.h>

#include "shared.h"

int main(int argc, char **argv) {
    if (argc != 2) {
        std::cerr << "usage: ./ipv6 ff12::1234" << std::endl;
        exit(EXIT_FAILURE);
    }
    DB db{};
    std::random_device rd;
    std::mt19937 rng(rd());
    std::uniform_int_distribution<int> uni(1000000, 9999999);

    const std::string my_token = "UID-" + std::to_string(uni(rng));
    std::cout << "My token: " << my_token << std::endl;

    int input_sock, output_sock;
    int true_flag = 1;
    int false_flag = 0;
    {
        if ((input_sock = socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP)) < 0) {
            perror("socket creation failed");
            exit(EXIT_FAILURE);
        }

        if (setsockopt(input_sock, SOL_SOCKET, SO_REUSEADDR, &true_flag, sizeof true_flag) < 0) {
            perror("setsockopt reuseaddr failed");
            exit(EXIT_FAILURE);
        }

        if (setsockopt(input_sock, SOL_SOCKET, SO_REUSEPORT, &true_flag, sizeof true_flag) < 0) {
            perror("setsockopt resuseport failed");
            exit(EXIT_FAILURE);
        }

        if (setsockopt(input_sock, SOL_SOCKET, SO_REUSEADDR, &true_flag, sizeof(true_flag))) {
            perror("setsockopt SOL_SOCKET");
            return 1;
        }

        int hops = 255;
        if (setsockopt(input_sock, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &hops, sizeof(hops))) {
            perror("setsockopt");
            return 1;
        }

        struct sockaddr_in6 server_addr{};
        server_addr.sin6_family = AF_INET6;
        server_addr.sin6_port = htons(PORT);
        server_addr.sin6_addr = in6addr_any;
        if (bind(input_sock, (const struct sockaddr *) &server_addr, sizeof(server_addr)) < 0) {
            perror("bind failed");
            exit(EXIT_FAILURE);
        }


        struct ipv6_mreq group;
        group.ipv6mr_interface = if_nametoindex("eth0");
        inet_pton(AF_INET6, argv[1], &group.ipv6mr_multiaddr);
        if (setsockopt(input_sock, IPPROTO_IPV6, IPV6_JOIN_GROUP, &group, sizeof group) < 0) {
            perror("IPV6_JOIN_GROUP");
            exit(EXIT_FAILURE);
        }
    }


    struct sockaddr_in6 broadcast_addr{};
    broadcast_addr.sin6_family = AF_INET;
    broadcast_addr.sin6_port = htons(PORT);
    inet_pton(AF_INET6, argv[1], &broadcast_addr.sin6_addr);
    {
        if ((output_sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0) {
            perror("output socket creation failed");
            exit(EXIT_FAILURE);
        }
    }

    struct pollfd fd{
            .fd = input_sock,
            .events = POLLIN
    };

    send_info_about_me(my_token, output_sock, reinterpret_cast<sockaddr *>(&broadcast_addr), sizeof(broadcast_addr));

    do {
        bool updated;
        int res = poll(&fd, 1, 1000);

        if (res < 0) {
            perror("poll failed");
            exit(EXIT_FAILURE);
        }

        updated = db.clear();

        if (res != 0) {
            if (fd.revents != POLLIN) {
                std::cerr << "bad: no POLLIN" << std::endl;
            }

            char buffer[MAXLINE];
            struct sockaddr_in6 client_addr{};
            const int len = sizeof(client_addr);

            ssize_t read = recvfrom(input_sock, (char *) buffer, MAXLINE, MSG_WAITALL, (struct sockaddr *) &client_addr,
                                    (socklen_t *) &len);

            if (read < 0) {
                perror("recvfrom");
                exit(EXIT_FAILURE);
            }

            buffer[read] = '\0';
            std::string token = buffer;

            if (token == my_token) {
                continue;
            }

            char host[INET6_ADDRSTRLEN];
            inet_ntop(AF_INET6, &(client_addr.sin6_addr), host, INET6_ADDRSTRLEN);

            updated = updated || !db.exists(token);
            db.add(host, token);
        } else {
            send_info_about_me(my_token, output_sock, reinterpret_cast<sockaddr *>(&broadcast_addr),
                               sizeof(broadcast_addr));
        }

        if (updated) {
            db.print();
        }
    } while (true);

    return 0;
}
