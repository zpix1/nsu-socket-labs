#include <cstdio>
#include <cstdlib>
#include <sys/ioctl.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <ctime>
#include <netinet/in.h>
#include <cerrno>
#include <unistd.h>
#include <cstring>

#define SERVER_PORT  12345

int main(int argc, char **argv) {
    int on = 1, rc;
    int listen_sd = socket(AF_INET6, SOCK_DGRAM, 0);

    if (listen_sd < 0) {
        perror("socket() failed");
        exit(-1);
    }

    if (setsockopt(listen_sd, SOL_SOCKET, SO_REUSEADDR, (char *) &on, sizeof(on)) < 0) {
        perror("setsockopt() failed");
        close(listen_sd);
        exit(1);
    }

    if (ioctl(listen_sd, FIONBIO, (char *) &on) < 0) {
        perror("ioctl() failed");
        close(listen_sd);
        exit(1);
    }

    struct sockaddr_in6 addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin6_family = AF_INET6;
    memcpy(&addr.sin6_addr, &in6addr_any, sizeof(in6addr_any));
    addr.sin6_port = htons(SERVER_PORT);
    if (bind(listen_sd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        perror("bind() failed");
        close(listen_sd);
        exit(-1);
    }

    if (listen(listen_sd, 32) < 0) {
        perror("listen() failed");
        close(listen_sd);
        exit(-1);
    }

    struct pollfd fd{};

    fd.fd = listen_sd;
    fd.events = POLLIN;

    int timeout = 3 * 1000;

    do {
        printf("Waiting on poll()...\n");
        int res = poll(&fd, 1, timeout);

        if (res < 0) {
            perror("  poll() failed");
            break;
        }

        if (rc == 0) {
            printf("  poll() timed out.  End program.\n");
            break;
        }

        if (fd.revents == 0)
            continue;

        if (fd.revents != POLLIN) {
            printf("  Error! revents = %d\n", fd.revents);
            break;
        }

        printf("  Listening socket is readable\n");

        const int bufsize = 1000;
        char* buffer[bufsize];
        int n = recvfrom(listen_sd, (char *)buffer, bufsize,
                     MSG_WAITALL, ( struct sockaddr *) &cliaddr,
                     &len);
        buffer[n] = '\0';
        printf("Client : %s\n", buffer);
        sendto(sockfd, (const char *)hello, strlen(hello),
               MSG_CONFIRM, (const struct sockaddr *) &cliaddr,
               len);
    } while (true);
}



