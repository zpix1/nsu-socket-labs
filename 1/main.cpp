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

const int remove_timeout = 2000;
const int timeout = 1000;

#define PORT     8080
#define MAXLINE 1024

namespace stuff {
    template< typename ContainerT, typename PredicateT >
    void erase_if( ContainerT& items, const PredicateT& predicate ) {
        for( auto it = items.begin(); it != items.end(); ) {
            if( predicate(*it) ) it = items.erase(it);
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

    void clear() {
        const time_t now = time(0);
        return stuff::erase_if(map, [&now](const auto& item) {
            return now - item.second >= remove_timeout;
        });
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

    struct sockaddr_in servaddr{}, cliaddr{};
    struct sockaddr_in broadcastaddr{};

    int sockfd;
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("socket creation failed");
        exit(EXIT_FAILURE);
    }

    servaddr.sin_family = AF_INET; // IPv4
    servaddr.sin_addr.s_addr = INADDR_ANY;
    servaddr.sin_port = htons(PORT);

    broadcastaddr.sin_family = AF_INET;
    broadcastaddr.sin_port = htons(PORT);
    inet_aton(argv[1], &broadcastaddr.sin_addr);

    int trueflag = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &trueflag, sizeof trueflag) < 0) {
        perror("setsockopt reuseaddr failed");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(sockfd, SOL_SOCKET, SO_BROADCAST, &trueflag, sizeof(trueflag)) < 0) {
        perror("setsockopt broadcast failed");
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
//        std::cout << "polling..." << std::endl;
        int res = poll(&fd, 1, 1000);

        if (res < 0) {
            perror("poll failed");
            exit(EXIT_FAILURE);
        }

        if (res == 0) {
            send_info_about_me(MYSELF_ID, sockfd, reinterpret_cast<sockaddr *>(&broadcastaddr), sizeof(broadcastaddr));
        } else {
            if (fd.revents != POLLIN) {
                std::cerr << "bad: no polling" << std::endl;
            }

            char buffer[MAXLINE];
            const int len = sizeof(cliaddr);

            ssize_t read = recvfrom(sockfd, (char *) buffer, MAXLINE, MSG_WAITALL, (struct sockaddr *) &cliaddr,
                                    (socklen_t *) &len);

            if (read < 0) {
                perror("recvfrom");
                exit(EXIT_FAILURE);
            }

            buffer[read] = '\0';
            std::string client_data = buffer;
//            if (client_data == MYSELF_ID) {
//                std::cout << "already me" << std::endl;
//                continue;
//            }

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
            db.clear();
            int exists = db.exists(client_data);
            if (!exists) {
                db.add(client_data);
                db.print();
            }
        }
    } while (true);

    return 0;
}



