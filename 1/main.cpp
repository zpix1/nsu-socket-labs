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
#include <sys/fcntl.h>

const int remove_timeout = 5;

#define PORT     8080
#define MAXLINE 1024

namespace stuff {
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
    std::unordered_map<std::string, time_t> map;

public:
    void add(const std::string& s) {
        map[s] = time(0);
    }

    bool clear() {
        const int before = map.size();
        const time_t now = time(0);
        stuff::erase_if(map, [&now](const auto& item) {
            return now - item.second >= remove_timeout;
        });
//        std::cout << "before: " << before << " " << "after: " << map.size() << std::endl;
        return map.size() != before;
    }

    void print() {
        std::cout << "[" << std::endl;
        for (const auto& res: map) {
            std::cout << "\t" << res.first << " = " << res.second << "," << std::endl;
        }
        std::cout << "]" << std::endl;
    }

    bool exists(const std::string& s) {
        return map.find(s) != map.end();
    }
};


int main(int argc, char **argv) {
    DB db{};
    srand(time(0));

    const std::string MYSELF_ID = "UID-" + std::to_string(rand());
    std::cout << "MYSELF_ID is " << MYSELF_ID << std::endl;

    int input_sock, output_sock;
    int trueflag = 1;
    int falseflag = 1;
    {
        if ((input_sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
            perror("socket creation failed");
            exit(EXIT_FAILURE);
        }

        if (ioctl(input_sock, FIONBIO, (char *) &trueflag) < 0) {
            perror("socket creation failed");
            exit(EXIT_FAILURE);
        }

        if (setsockopt(input_sock, SOL_SOCKET, SO_REUSEADDR, &trueflag, sizeof trueflag) < 0) {
            perror("setsockopt reuseaddr failed");
            exit(EXIT_FAILURE);
        }

        if (setsockopt(input_sock, SOL_SOCKET, SO_REUSEPORT, &trueflag, sizeof trueflag) < 0) {
            perror("setsockopt resuseport failed");
            exit(EXIT_FAILURE);
        }

        struct ip_mreq mreq{};
        mreq.imr_multiaddr.s_addr = inet_addr(argv[1]);
        mreq.imr_interface.s_addr = htonl(INADDR_ANY);
        if (setsockopt(input_sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0) {
            perror("IP_ADD_MEMBERSHIP failed");
            exit(EXIT_FAILURE);
        }

        struct sockaddr_in servaddr{};
        servaddr.sin_family = AF_INET;
        servaddr.sin_port = htons(PORT);
        servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(input_sock, (const struct sockaddr *) &servaddr, sizeof(servaddr)) < 0) {
            perror("bind failed");
            exit(EXIT_FAILURE);
        }
    }


    struct sockaddr_in broadcastaddr{};
    broadcastaddr.sin_family = AF_INET;
    broadcastaddr.sin_addr.s_addr = inet_addr(argv[1]);
    broadcastaddr.sin_port = htons(PORT);
    {
        if ((output_sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
            perror("output socket creation failed");
            exit(EXIT_FAILURE);
        }
        if (setsockopt(output_sock, SOL_SOCKET, SO_REUSEADDR, &trueflag, sizeof trueflag) < 0) {
            perror("setsockopt reuseaddr failed");
            exit(EXIT_FAILURE);
        }
        if (setsockopt(output_sock, SOL_SOCKET, SO_REUSEPORT, &trueflag, sizeof trueflag) < 0) {
            perror("setsockopt resuseport failed");
            exit(EXIT_FAILURE);
        }
        if (setsockopt(output_sock, IPPROTO_IP, IP_MULTICAST_LOOP, (char *) &falseflag, sizeof(falseflag)) < 0) {
            perror("setsockopt reuseaddr failed");
            exit(EXIT_FAILURE);
        }
        struct in_addr localInterface;
        localInterface.s_addr = htonl(INADDR_ANY);
        if (setsockopt(output_sock, IPPROTO_IP, IP_MULTICAST_IF, (char *) &localInterface, sizeof(localInterface)) <
            0) {
            perror("setting local interface");
            exit(1);
        }

        if (bind(output_sock, (const struct sockaddr *) &broadcastaddr, sizeof(broadcastaddr)) < 0) {
            perror("output bind failed");
            exit(EXIT_FAILURE);
        }
    }

    struct pollfd fd{
            .fd = input_sock,
            .events = POLLIN
    };

    send_info_about_me(MYSELF_ID, output_sock, reinterpret_cast<sockaddr *>(&broadcastaddr), sizeof(broadcastaddr));

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
            struct sockaddr_in cliaddr{};
            const int len = sizeof(cliaddr);

            ssize_t read = recvfrom(input_sock, (char *) buffer, MAXLINE, MSG_WAITALL, (struct sockaddr *) &cliaddr,
                                    (socklen_t *) &len);

            if (read < 0) {
                perror("recvfrom");
                exit(1);
            }

            buffer[read] = '\0';
            std::string client_data = buffer;
            if (client_data == MYSELF_ID) {
                std::cout << "already me" << std::endl;
                continue;
            }

            char host[NI_MAXHOST];
            if (getnameinfo((sockaddr *) &cliaddr, len,
                            host, NI_MAXHOST, // to try to get domain name don't put NI_NUMERICHOST flag
                            nullptr, 0,          // use char serv[NI_MAXSERV] if you need port number
                            NI_NUMERICHOST    // | NI_NUMERICSERV
            ) != 0) {
                perror("getnameinfo");
                exit(EXIT_FAILURE);
            } else {
                client_data += "-" + std::string(host);
            }

            std::cout << "got info: " << client_data << ":" << cliaddr.sin_port << std::endl;
            updated = updated || !db.exists(client_data);
            db.add(client_data);
        } else {
            send_info_about_me(MYSELF_ID, output_sock, reinterpret_cast<sockaddr *>(&broadcastaddr),
                               sizeof(broadcastaddr));
        }

        if (updated) {
            db.print();
        }
    } while (true);

    return 0;
}



