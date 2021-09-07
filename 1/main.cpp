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

const int remove_timeout = 5;

#define PORT     8080
#define MAXLINE 1024

namespace erase_if_fill {
    template<typename ContainerT, typename PredicateT>
    void erase_if(ContainerT& items, const PredicateT& predicate) {
        for (auto it = items.begin(); it != items.end();) {
            if (predicate(*it)) it = items.erase(it);
            else ++it;
        }
    }
}

void send_info_about_me(const std::string& info, int sockfd, struct sockaddr *cliaddr_ptr, const size_t cliaddr_len) {
//    std::cout << "sending info about me" << std::endl;
    sendto(sockfd, info.c_str(), info.length(), 0, cliaddr_ptr, cliaddr_len);
}

class DB {
    struct Entry {
        time_t last_seen = 0;
        std::string ip;
        std::string token;
    };
    std::unordered_map<std::string, Entry> map;

public:
    void add(const std::string& ip, const std::string& token) {
        Entry e{
                time(nullptr),
                ip,
                token
        };
        map[token] = e;
    }

    bool clear() {
        const size_t before = map.size();
        const time_t now = time(nullptr);
        erase_if_fill::erase_if(map, [&now](const auto& item) {
            return now - item.second.last_seen >= remove_timeout;
        });
        return map.size() != before;
    }

    void print() {
        system("clear");
        printf("%-20s %-20s %-20s\n", "Token", "IP", "First seen");
        printf("------------------------------------------------------------\n");

        for (const auto& res: map) {
            char buffer[100];
            const auto time_info = localtime(&res.second.last_seen);
            strftime(buffer, sizeof(buffer), "%H:%M:%S", time_info);
            std::string timeStr(buffer);
            printf("%-20s %-20s %-20s\n", res.second.token.c_str(), res.second.ip.c_str(), buffer);
        }
    }

    bool exists(const std::string& token) {
        return map.find(token) != map.end();
    }
};

int main(int argc, char **argv) {
    DB db{};
    srand(time(nullptr));
    std::random_device rd;
    std::mt19937 rng(rd());
    std::uniform_int_distribution<int> uni(1000000, 9999999);

    const std::string my_token = "UID-" + std::to_string(uni(rng));
    std::cout << "My token: " << my_token << std::endl;

    int input_sock, output_sock;
    int true_flag = 1;
    int false_flag = 0;
    {
        if ((input_sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0) {
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

        struct sockaddr_in6 server_addr{};
        server_addr.sin6_family = AF_INET;
        server_addr.sin6_port = htons(PORT);
        server_addr.sin6_addr = in6addr_any;
        if (bind(input_sock, (const struct sockaddr *) &server_addr, sizeof(server_addr)) < 0) {
            perror("bind failed");
            exit(EXIT_FAILURE);
        }
        struct ipv6_mreq mreq{};
        mreq.ipv6mr_multiaddr = inet_addr(argv[1]);
        mreq.ipv6mr_interface = in6addr_any;
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
            if (getnameinfo((sockaddr *) &client_addr, len, host, NI_MAXHOST, nullptr, 0, NI_NUMERICHOST) != 0) {
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

    return 0;
}
