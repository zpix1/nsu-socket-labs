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

#include "shared.h"

int main(int argc, char **argv) {
    if (argc != 2) {
        std::cerr << "usage: ./ipv4 224.255.255.255" << std::endl;
        exit(EXIT_FAILURE);
    }

    DB db{};
    std::random_device rd;
    std::mt19937 rng(rd());
    std::uniform_int_distribution<int> uni(1000000, 9999999);

    const std::string my_token = "UID-" + std::to_string(uni(rng));
    std::cout << "my_token is " << my_token << std::endl;

    int input_sock, output_sock;
    int true_flag = 1;
    int false_flag = 0;
    {
        if ((input_sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
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

        struct sockaddr_in server_addr{};
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(PORT);
        server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(input_sock, (const struct sockaddr *) &server_addr, sizeof(server_addr)) < 0) {
            perror("bind failed");
            exit(EXIT_FAILURE);
        }

        struct ip_mreq mreq{};
        mreq.imr_multiaddr.s_addr = inet_addr(argv[1]);
        mreq.imr_interface.s_addr = htonl(INADDR_ANY);
        if (setsockopt(input_sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0) {
            perror("IP_ADD_MEMBERSHIP failed");
            exit(EXIT_FAILURE);
        }
    }


    struct sockaddr_in broadcast_addr{};
    broadcast_addr.sin_family = AF_INET;
    broadcast_addr.sin_addr.s_addr = inet_addr(argv[1]);
    broadcast_addr.sin_port = htons(PORT);
    {
        if ((output_sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
            perror("output socket creation failed");
            exit(EXIT_FAILURE);
        }

//        if (setsockopt(output_sock, SOL_SOCKET, SO_REUSEADDR, &true_flag, sizeof true_flag) < 0) {
//            perror("setsockopt reuseaddr failed");
//            exit(EXIT_FAILURE);
//        }
//
//        if (setsockopt(output_sock, SOL_SOCKET, SO_REUSEPORT, &true_flag, sizeof true_flag) < 0) {
//            perror("setsockopt resuseport failed");
//            exit(EXIT_FAILURE);
//        }
//
//        if (setsockopt(output_sock, IPPROTO_IP, IP_MULTICAST_LOOP, (char *) &false_flag, sizeof(false_flag)) < 0) {
//            perror("setsockopt reuseaddr failed");
//            exit(EXIT_FAILURE);
//        }
//
//        struct in_addr localInterface{};
//        localInterface.s_addr = htonl(INADDR_ANY);
//        if (setsockopt(output_sock, IPPROTO_IP, IP_MULTICAST_IF, (char *) &localInterface, sizeof(localInterface)) <
//            0) {
//            perror("setting local interface");
//            exit(1);
//        }
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
            struct sockaddr_in client_addr{};
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

            char host[NI_MAXHOST];
            std::string ip;
            if (getnameinfo((sockaddr *) &client_addr, len, host, NI_MAXHOST, nullptr, 0, NI_NUMERICHOST
            ) != 0) {
                perror("getnameinfo");
                exit(EXIT_FAILURE);
            } else {
                ip = std::string(host);
            }

            updated = updated || !db.exists(token);
            db.add(ip, token);
        } else {
            send_info_about_me(my_token, output_sock, reinterpret_cast<sockaddr *>(&broadcast_addr),
                               sizeof(broadcast_addr));
        }

        if (updated) {
            db.print();
        }
    } while (true);
}



