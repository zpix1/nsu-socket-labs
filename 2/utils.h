#ifndef INC_2_UTILS_H
#define INC_2_UTILS_H

#include <cstdlib>

void perror_fail_if(const int result, const char *message) {
    if (result < 0) {
        perror(message);
        exit(EXIT_FAILURE);
    }
}

void fail_if(const bool failure, const char* message) {
    if (failure) {
        std::cerr << message << ": error" << std::endl;
        exit(EXIT_FAILURE);
    }
}

#endif //INC_2_UTILS_H
