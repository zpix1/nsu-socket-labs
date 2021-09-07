//
// Created by Иван Бакшеев on 07.09.2021.
//

#ifndef LAB1_SHARED_H
#define LAB1_SHARED_H


const int remove_timeout = 5;

#define PORT 8080
#define MAXLINE 1024

namespace erase_if_filling {
    template<typename ContainerT, typename PredicateT>
    void erase_if(ContainerT& items, const PredicateT& predicate) {
        for (auto it = items.begin(); it != items.end();) {
            if (predicate(*it)) it = items.erase(it);
            else ++it;
        }
    }
}

void send_info_about_me(const std::string& info, int socket_fd, struct sockaddr *client_addr_ptr,
                        const size_t client_addr_len) {
    sendto(socket_fd, info.c_str(), info.length(), 0, client_addr_ptr, client_addr_len);
}

class DB {
    struct Entry {
        time_t last_seen{};
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
        erase_if_filling::erase_if(map, [&now](const auto& item) {
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
            const auto timeinfo = localtime(&res.second.last_seen);
            strftime(buffer, sizeof(buffer), "%H:%M:%S", timeinfo);
            std::string timeStr(buffer);
            printf("%-20s %-20s %-20s\n", res.second.token.c_str(), res.second.ip.c_str(), buffer);
        }
    }

    bool exists(const std::string& token) {
        return map.find(token) != map.end();
    }
};

#endif //LAB1_SHARED_H
