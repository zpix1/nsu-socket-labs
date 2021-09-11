#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include <thread>
#include <chrono>
#include <mutex>
#include <condition_variable>

#include "utils.h"
#include "config.h"

using namespace std::chrono;

namespace fs = std::filesystem;

struct FileLoadingState {
    const std::string filename;
    const milliseconds started_at;
    uint64_t loaded_bytes;
    const uint64_t filesize;
    bool done = false;
    std::mutex cv_m;
    std::condition_variable cv;
};

void log_state(const FileLoadingState *state) {
    std::cout << "[" << state->filename << "]: ";
    if (state->done) {
        std::cout << "done";
    } else {
        std::cout << state->loaded_bytes << " / " << state->filesize;
    }
    std::cout << std::endl;
}

void log_file_loading(FileLoadingState *state) {
    using namespace std::chrono_literals;
    log_state(state);
    while (!state->done) {
        std::unique_lock<std::mutex> lk(state->cv_m);
        state->cv.wait_for(lk, 3s, [&state] { return state->done; });
        log_state(state);
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

    auto log_thread = std::thread(log_file_loading, &state);

    char buf[BUF_SIZE];
    bool result = false;
    while (!state.done) {
        ssize_t part_read_bytes = recv(socket_fd, buf, BUF_SIZE, 0);
        if (part_read_bytes < 0) {
            result = false;
            state.done = true;
            perror("part reading from socket");
            break;
        }
        state.loaded_bytes += part_read_bytes;
        if (state.loaded_bytes == state.filesize) {
            result = true;
            state.done = true;
            break;
        }
    }

    state.done = true;
    state.cv.notify_all();
    log_thread.join();

    return result;
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
    perror_fail_if(accept_socket_fd = socket(AF_INET, SOCK_STREAM, 0),
                   "create accept socket");

    struct sockaddr_in server_addr{};
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = INADDR_ANY;
    perror_fail_if(bind(accept_socket_fd, (struct sockaddr *) &server_addr, sizeof(server_addr)),
                   "bind accept socket");

    perror_fail_if(listen(accept_socket_fd, 32), "listen accept socket");

    struct sockaddr_in client_addr{};
    const auto name_len = sizeof(client_addr);
    int client_socket_fd;

    while (true) {
        client_socket_fd = accept(accept_socket_fd, (struct sockaddr *) &client_addr, (socklen_t *) &name_len);
        if (client_socket_fd < 0) {
            perror("accept connection");
            break;
        }
        std::thread(download_file_from_socket, client_socket_fd).detach();
    }

    return EXIT_SUCCESS;
}
