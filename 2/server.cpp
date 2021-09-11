#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include <thread>
#include <chrono>

using namespace std::chrono;

#include "utils.h"
#include "config.h"

namespace fs = std::filesystem;

struct FileLoadingState {
    const std::string filename;
    const milliseconds started_at;
    volatile uint64_t loaded_bytes;
    const uint64_t filesize;
    volatile bool done = false;
};

void log_file_loading(const FileLoadingState& state) {
    using namespace std::chrono_literals;
    while (!state.done) {
        std::cout << "[" << state.filename << "]: " << state.loaded_bytes << " / " << state.filesize << std::endl;
        std::this_thread::sleep_for(3s);
    }
}

bool download_file_from_socket(const int socket_fd) {
    uint64_t filesize;
    ssize_t filesize_read_bytes;
    if ((filesize_read_bytes = recv(socket_fd, &filesize, sizeof(filesize), 0)) < 0) {
        perror("filesize reading from socket");
        return false;
    }
    if (filesize_read_bytes != sizeof(filesize)) {
        std::cerr << "failed to read filesize" << std::endl;
        return false;
    }

    char filename[MAX_FILENAME_SIZE];
    ssize_t filename_read_bytes;
    if ((filename_read_bytes = recv(socket_fd, filename, MAX_FILENAME_SIZE, 0)) < 0) {
        perror("filename reading from socket");
        return false;
    }
    if (filename_read_bytes == 0) {
        std::cerr << "empty filename read" << std::endl;
        return false;
    }

    std::cout << "read filename " << filename << " of size " << filesize << std::endl;

    if (send(socket_fd, SERVER_READY_REPLY, sizeof(SERVER_READY_REPLY), 0) < 0) {
        perror("server ready reply sending to socket");
        return false;
    }

    FileLoadingState state{
            .filename=filename,
            .started_at=std::chrono::duration_cast<milliseconds>(
                    system_clock::now().time_since_epoch()
            ),
            .loaded_bytes=0,
            .filesize=filesize,
            .done=false
    };

    auto log_thread = std::thread(log_file_loading, state);

    char buf[BUF_SIZE];
    while (!state.done) {
        ssize_t part_read_bytes = recv(socket_fd, buf, BUF_SIZE, 0);
        if (part_read_bytes < 0) {
            state.done = true;
            log_thread.join();
            perror("part reading from socket");
            return false;
        }
        state.loaded_bytes += part_read_bytes;
        if (state.loaded_bytes == state.filesize) {
            break;
        }
    }

    return true;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        std::cout << "Usage: ./server 8080" << std::endl;
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
        std::thread file_load_thread(download_file_from_socket, client_socket_fd);
        file_load_thread.detach();
    }

    return EXIT_SUCCESS;
}
