#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include "utils.h"
#include "config.h"

namespace fs = std::filesystem;

bool download_file_from_socket(const int socket_fd) {
    char filename[MAX_FILENAME_SIZE];
    ssize_t read_bytes;
    if ((read_bytes = recv(socket_fd, filename, MAX_FILENAME_SIZE, 0)) < 0) {
        perror("filename reading from socket");
        return false;
    }

    if (read_bytes == 0) {
        std::cerr << "empty filename read" << std::endl;
        return false;
    }

    std::cout << "read filename " << filename << std::endl;

    if (send(socket_fd, SERVER_READY_REPLY, sizeof(SERVER_READY_REPLY), 0) < 0) {
        perror("server ready reply sending to socket");
        return false;
    }


}

int main(int argc, char **argv) {
    if (argc != 2) {
        std::cout << "Usage: ./server_addr 8080" << std::endl;
        exit(EXIT_FAILURE);
    }

    if (!fs::is_directory(UPLOADS_DIR) || !fs::exists(UPLOADS_DIR)) {
        std::cout << "Directory " << UPLOADS_DIR << " not found, creating one..." << std::endl;
        fs::create_directory(UPLOADS_DIR);
    }

    const unsigned short port = std::atoi(argv[1]);

    int accept_socket_fd;
    fail_if(accept_socket_fd = socket(AF_INET, SOCK_STREAM, 0),
            "create accept socket");

    struct sockaddr_in server_addr{};
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = INADDR_ANY;
    fail_if(bind(accept_socket_fd, (struct sockaddr *) &server_addr, sizeof(server_addr)),
            "bind accept socket");

    fail_if(listen(accept_socket_fd, 32), "listen accept socket");

    struct sockaddr_in client_addr{};
    const auto name_len = sizeof(client_addr);
    int client_socket_fd;

    while (true) {
        client_socket_fd = accept(accept_socket_fd, (struct sockaddr *) &client_addr, (socklen_t *) &name_len);
        if (client_socket_fd < 0) {
            perror("accept connection");
            break;
        }
    }

    return EXIT_SUCCESS;
}
