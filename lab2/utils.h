#ifndef INC_2_UTILS_H
#define INC_2_UTILS_H

#include <cstdlib>

void perror_fail_if(const int result, const char *message) {
    if (result < 0) {
        perror(message);
        exit(EXIT_FAILURE);
    }
}

void fail_if(const bool failure, const char *message) {
    if (failure) {
        std::cerr << message << ": error" << std::endl;
        exit(EXIT_FAILURE);
    }
}

const char *humanSize(uint64_t bytes) {
    char *suffix[] = {"B", "KB", "MB", "GB", "TB"};
    char length = sizeof(suffix) / sizeof(suffix[0]);

    int i = 0;
    double dblBytes = bytes;

    if (bytes > 1024) {
        for (i = 0; (bytes / 1024) > 0 && i < length - 1; i++, bytes /= 1024)
            dblBytes = bytes / 1024.0;
    }

    static char output[200];
    sprintf(output, "%.02lf %s", dblBytes, suffix[i]);
    return output;
}

bool is_valid_filename(const std::string& str) {
    if (str.find('/') != std::string::npos) {
        return false;
    }
    if (str.find("..") != std::string::npos) {
        return false;
    }
    if (str.empty()) {
        return false;
    }
    if (str.size() > FILENAME_MAX) {
        return false;
    }
    return true;
}

#endif //INC_2_UTILS_H
