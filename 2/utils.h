#ifndef INC_2_UTILS_H
#define INC_2_UTILS_H

#include <cstdlib>

void fail_if(const int result, const char *message) {
    if (result < 0) {
        perror(message);
        exit(EXIT_FAILURE);
    }
}

#endif //INC_2_UTILS_H
