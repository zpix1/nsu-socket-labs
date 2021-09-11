#ifndef INC_2_UTILS_H
#define INC_2_UTILS_H

#include <cstdlib>

void perror_fail_if(const int result, const char *message) {
    if (result < 0) {
        perror(message);
        exit(EXIT_FAILURE);
    }
}

void fail_if(const bool value, const char* message) {
    if (!value) {
        std::cerr << message << ": error" << std::endl;
    }
}

#endif //INC_2_UTILS_H
