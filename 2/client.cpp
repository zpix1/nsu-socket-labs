#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include <thread>
#include <arpa/inet.h>
#include <unistd.h>

#include "utils.h"
#include "config.h"

namespace fs = std::filesystem;

int main(int argc, char **argv) {
    if (argc != 4) {
        std::cerr << "usage: ./client test_file 192.168.1.7 8080" << std::endl;
        exit(EXIT_FAILURE);
    }

    auto path = fs::path(argv[1]);
    if (!exists(path) || is_directory(path)) {
        std::cerr << "file " << path << " not found" << std::endl;
        exit(EXIT_FAILURE);
    }

    struct hostent *hostname;
    hostname = gethostbyname(argv[2]);
    if (hostname == nullptr) {
        std::cerr << "gethostbyname failed: " << argv[2] << std::endl;
        exit(EXIT_FAILURE);
    }

    const unsigned short port = std::atoi(argv[3]);

    struct sockaddr_in server_addr{};
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = *((unsigned long *) hostname->h_addr);

    int socket_fd;
    perror_fail_if(socket_fd = socket(AF_INET, SOCK_STREAM, 0), "socket creating");
    perror_fail_if(connect(socket_fd, (sockaddr *) &server_addr, sizeof(server_addr)), "socket connecting");

    filesize_t filesize = file_size(path);
    auto filename_str = std::string{path.filename().u8string()};
    auto filename_len = filename_str.size() + 1;
    auto *filename = filename_str.c_str();
    std::cout << "sending " << filename << " of size " << humanSize(filesize) << std::endl;

    fail_if(send(socket_fd, &filesize, sizeof(filesize), 0) != sizeof(filesize), "sending filesize");
    fail_if(send(socket_fd, filename, filename_len, 0) != filename_len, "sending filename");

    char reply_buf[sizeof(SERVER_READY_REPLY)];
    fail_if(recv(socket_fd, reply_buf, sizeof(reply_buf), 0) != sizeof(reply_buf), "reading reply");
    fail_if(std::string(reply_buf) != SERVER_READY_REPLY, ("invalid reply: " + std::string(reply_buf)).c_str());

    FILE *f = fopen(path.string().c_str(), "rb");
    char buf[BUF_SIZE];
    size_t read;
    while ((read = fread(buf, 1, BUF_SIZE, f)) > 0) {
        fail_if(send(socket_fd, buf, read, 0) != read, "sending part");
        sleep(rand() % 3);
    }

    char end_reply_buf[sizeof(SERVER_END_REPLY)];
    perror_fail_if(recv(socket_fd, reply_buf, sizeof(end_reply_buf), 0), "reading end reply");
    fail_if(std::string(reply_buf) != SERVER_END_REPLY, ("invalid reply " + std::string(end_reply_buf)).c_str());

    std::cout << "file " << filename << " successfully sent" << std::endl;

    return EXIT_SUCCESS;
}
