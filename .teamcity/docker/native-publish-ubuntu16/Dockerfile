# Released native-platform Linux libraries must stay on glibc 2.23 (Ubuntu 16.04).
FROM ubuntu:16.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    g++ \
    git \
    libncurses5-dev \
    openjdk-8-jdk \
    unzip \
    && rm -rf /var/lib/apt/lists/*

ARG JDK21_VERSION=jdk-21.0.11+10
RUN mkdir -p /opt/jdk \
    && curl -fsSL "https://api.adoptium.net/v3/binary/version/${JDK21_VERSION}/linux/x64/jdk/hotspot/normal/adoptium" -o /tmp/jdk21.tar.gz \
    && tar xf /tmp/jdk21.tar.gz -C /opt/jdk \
    && ln -s "/opt/jdk/${JDK21_VERSION}" /opt/jdk/open-jdk-21 \
    && rm /tmp/jdk21.tar.gz

ENV JDK8=/usr/lib/jvm/java-8-openjdk-amd64
ENV JDK21=/opt/jdk/open-jdk-21
ENV JAVA_HOME=/opt/jdk/open-jdk-21
