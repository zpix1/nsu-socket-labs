#include <iostream>
#include <netdb.h>
#include <cstdio>
#include <filesystem>
#include <thread>
#include <chrono>
#include <mutex>
#include <fstream>
#include <string>
#include <condition_variable>

#include "utils.h"
#include "config.h"

using namespace std::chrono;

namespace fs = std::filesystem;

struct FileLoadingState {
    const std::string filename;
    const milliseconds started_at;
    milliseconds last_log_at;
    filesize_t loaded_bytes;
    filesize_t last_log_loaded_bytes;
    const filesize_t filesize;
    bool done = false;
    std::mutex cv_mutex;
    std::condition_variable cv;
};

milliseconds now() {
    return std::chrono::duration_cast<milliseconds>(
            system_clock::now().time_since_epoch()
    );
}

enum class DoneState {
    START,
    PROGRESS,
    DONE
};

void log_state(FileLoadingState *state, DoneState s) {
    auto current_time = now();

    unsigned long long average_speed = state->loaded_bytes * 1000 / (current_time - state->started_at).count();

    std::cout << "[" << state->filename << "]: ";
    if (s == DoneState::START) {
        std::cout << "started, " << humanSize(state->filesize);
    } else if (s == DoneState::DONE) {
        std::cout << "done," << std::fixed
                  << std::setprecision(2)
                  << " total speed: " << humanSize(average_speed) << "/s";
    } else if (s == DoneState::PROGRESS) {
        unsigned long long moment_speed = (state->loaded_bytes - state->last_log_loaded_bytes) * 1000 /
                                          (current_time - state->last_log_at).count();

        std::cout << humanSize(state->loaded_bytes) << " / " << humanSize(state->filesize) << ","
                  << std::fixed
                  << std::setprecision(2)
                  << " total speed: " << humanSize(average_speed)
                  << "/s, moment speed: " << humanSize(moment_speed) << "/s";
    }
    std::cout << std::endl;

    state->last_log_loaded_bytes = state->loaded_bytes;
    state->last_log_at = current_time;
}

void log_file_loading(FileLoadingState *state) {
    using namespace std::chrono_literals;
    log_state(state, DoneState::START);
    do {
        std::unique_lock<std::mutex> lock(state->cv_mutex);
        state->cv.wait_for(lock, 3s, [&state] { return state->done; });
        log_state(state, DoneState::PROGRESS);
    } while (!state->done);
    log_state(state, DoneState::DONE);
}

std::mutex filename_lock;

bool download_file_from_socket(const int socket_fd) {
    filesize_t filesize;
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

    filename[filename_read_bytes] = '\0';

    std::string filename_str{filename};

    if (!is_valid_filename(filename_str)) {
        if (send(socket_fd, SERVER_ERROR_REPLY, sizeof(SERVER_ERROR_REPLY), 0) < 0) {
            perror("server err reply sending to socket");
            return false;
        }
        std::cerr << "invalid filename read" << std::endl;
        return false;
    }

    if (send(socket_fd, SERVER_READY_REPLY, sizeof(SERVER_READY_REPLY), 0) < 0) {
        perror("server ready reply sending to socket");
        return false;
    }


    filename_lock.lock();

    auto filepath = fs::path(UPLOADS_DIR) / filename_str;
    unsigned int counter = 0;
    while (exists(filepath)) {
        filename_str = (filename + std::string("_") + std::to_string(counter));
        filepath = fs::path(UPLOADS_DIR) / filename_str;
        counter++;
    }

    // mark file as used
    std::ofstream temp_ofs(filepath);
    temp_ofs << '\0';
    temp_ofs.close();

    filename_lock.unlock();

    FileLoadingState state{
            .filename=filename_str,
            .started_at=now(),
            .last_log_at=now(),
            .loaded_bytes=0,
            .last_log_loaded_bytes=0,
            .filesize=filesize,
            .done=false
    };

    auto log_thread = std::thread(log_file_loading, &state);

    std::ofstream ofs(filepath);

    char buf[BUF_SIZE];
    bool result;
    while (true) {
        ssize_t part_read_bytes = recv(socket_fd, buf, BUF_SIZE, 0);

        if (part_read_bytes < 0) {
            result = false;
            state.done = true;
            perror("part reading from socket");
            break;
        }

        state.loaded_bytes += part_read_bytes;

        ofs.write(buf, part_read_bytes);

        if (state.loaded_bytes > state.filesize) {
            result = false;
            state.done = true;
            std::cerr << "read over filesize" << std::endl;
            break;
        }
        if (state.loaded_bytes == state.filesize) {
            result = true;
            state.done = true;
            break;
        }
    }

    state.done = true;
    state.cv.notify_all();
    log_thread.join();

    ofs.close();

    if (file_size(filepath) != state.filesize) {
        result = false;
    }

    if (result) {
        if (send(socket_fd, SERVER_END_REPLY, sizeof(SERVER_END_REPLY), 0) < 0) {
            perror("server end reply sending to socket");
        }
    } else {
        if (send(socket_fd, SERVER_ERROR_REPLY, sizeof(SERVER_ERROR_REPLY), 0) < 0) {
            perror("server err reply sending to socket");
        }
        remove(filepath);
    }

    return result;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        std::cerr << "usage: ./server 8080" << std::endl;
        exit(EXIT_FAILURE);
    }

    if (!fs::is_directory(UPLOADS_DIR) || !fs::exists(UPLOADS_DIR)) {
        std::cout << "directory " << UPLOADS_DIR << " not found, creating one..." << std::endl;
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
