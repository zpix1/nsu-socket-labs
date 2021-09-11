# renovate: datasource=docker depName=gcc versioning=docker
ARG GCC_VERSION=10
FROM gcc:$GCC_VERSION

# renovate: datasource=github-releases depName=Kitware/CMake
ARG CMAKE_VERSION=3.21.0

RUN wget https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-aarch64.sh \
      -q -O /tmp/cmake-install.sh \
      && chmod u+x /tmp/cmake-install.sh \
      && mkdir /usr/bin/cmake \
      && /tmp/cmake-install.sh --skip-license --prefix=/usr/bin/cmake \
      && rm /tmp/cmake-install.sh

ENV PATH="/usr/bin/cmake/bin:${PATH}"

WORKDIR /app
COPY *.cpp *.h CMakeLists.txt .
RUN cmake . && cmake --build .

# ENTRYPOINT ["./server", "CMakeLists.txt", "192.168.1.7", "8080"];