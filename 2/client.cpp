#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include <thread>
#include <unistd.h>
#include <arpa/inet.h>

#include "utils.h"
#include "config.h"

int main(int argc, char **argv) {
    sleep(2);
    if (argc != 4) {
        std::cout << "Usage: ./client test_file 192.168.1.7 8080" << std::endl;
        exit(EXIT_FAILURE);
    }

    struct hostent *hostname;
    hostname = gethostbyname(argv[2]);
    if (hostname == nullptr) {
        std::cerr << "gethostbyname failed: " << argv[2] << std::endl;
        exit(EXIT_FAILURE);
    }

    std::cout << inet_ntoa(hostname->h_addr);

    const unsigned short port = std::atoi(argv[2]);

    struct sockaddr_in server_addr{};
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = *((unsigned long *) hostname->h_addr);

    int socket_fd;
    perror_fail_if(socket_fd = socket(AF_INET, SOCK_STREAM, 0), "socket creating");
    perror_fail_if(connect(socket_fd, (sockaddr *) &server_addr, sizeof(server_addr)), "socket connecting");

    filesize_t filesize = 100 * BUF_SIZE;
    char filename[] = "memes";

    fail_if(send(socket_fd, &filesize, sizeof(filesize), 0) != sizeof(filesize), "sending filesize");
    fail_if(send(socket_fd, filename, sizeof(filename), 0) != sizeof(filename), "sending filesize");

    char reply_buf[sizeof(SERVER_READY_REPLY)];
    fail_if(recv(socket_fd, reply_buf, sizeof(reply_buf), 0) != sizeof(reply_buf), "reading reply");
    fail_if(std::string(reply_buf) != SERVER_READY_REPLY, ("invalid reply: " + std::string(reply_buf)).c_str());

    for (int i = 0; i < 100; i++) {
        char buf[BUF_SIZE] = {};
        fail_if(send(socket_fd, buf, sizeof(buf), 0) != sizeof(buf), "sending part");
    }

    return EXIT_SUCCESS;
}
