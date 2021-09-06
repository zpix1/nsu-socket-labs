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

    int trueflag = 1;
    int falseflag = 1;
    struct sockaddr_in servaddr{}, cliaddr{};

    struct sockaddr_in broadcastaddr{};
    broadcastaddr.sin_addr.s_addr = inet_addr("225.1.1.1");
    broadcastaddr.sin_family = AF_INET;
    broadcastaddr.sin_port = htons(PORT);

    int sockfd;
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("socket creation failed");
        exit(EXIT_FAILURE);
    }
    if (ioctl(sockfd, FIONBIO, (char *) &trueflag) < 0) {
        perror("socket creation failed");
        exit(EXIT_FAILURE);
    }

    servaddr.sin_family = AF_INET;
    servaddr.sin_port = htons(PORT);
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);

    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &trueflag, sizeof trueflag) < 0) {
        perror("setsockopt reuseaddr failed");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEPORT, &trueflag, sizeof trueflag) < 0) {
        perror("setsockopt resuseport failed");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(sockfd, SOL_SOCKET, SO_BROADCAST, &trueflag, sizeof(trueflag)) < 0) {
        perror("setsockopt broadcast failed");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(sockfd, IPPROTO_IP, IP_MULTICAST_LOOP, &falseflag, sizeof(falseflag)) < 0) {
        perror("IP_MULTICAST_LOOP failed");
        exit(EXIT_FAILURE);
    }

    struct ip_mreq mreq{};
    mreq.imr_multiaddr.s_addr = inet_addr(argv[1]);
    mreq.imr_interface.s_addr = htonl(INADDR_ANY);

    if (setsockopt(sockfd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0) {
        perror("IP_ADD_MEMBERSHIP failed");
        exit(EXIT_FAILURE);
    }

    if (bind(sockfd, (const struct sockaddr *) &servaddr, sizeof(servaddr)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);
    }

    struct pollfd fd{
            .fd = sockfd,
            .events = POLLIN
    };

    send_info_about_me(MYSELF_ID, sockfd, reinterpret_cast<sockaddr *>(&broadcastaddr), sizeof(broadcastaddr));

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
            const int len = sizeof(cliaddr);

            ssize_t read = recvfrom(sockfd, (char *) buffer, MAXLINE, MSG_WAITALL, (struct sockaddr *) &cliaddr, (socklen_t *) &len);

            if (read < 0) {
                perror("recvfrom");
                exit(1);
                continue;
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

//            std::cout << "got info: " << client_data << ":" << cliaddr.sin_port << std::endl;
            updated = updated || !db.exists(client_data);
            db.add(client_data);
        }

        send_info_about_me(MYSELF_ID, sockfd, reinterpret_cast<sockaddr *>(&broadcastaddr), sizeof(broadcastaddr));

        if (updated) {
            db.print();
        }
    } while (true);

    return 0;
}



