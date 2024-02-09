#
# Copyright 2021 ABSA Group Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Atum Service docker file

# build via (docker root = project root):
# docker build -t absaoss/atum-service:latest \
# --build-arg BUILD_PROXY=http://my.cool.proxy.here:3128 \
# --build-arg CONFIG=./path/to/my.awesome.local.reference.conf \
# --build-arg SSL=true \
# --build-arg SSL_DNAME="CN=*.my.domain.com, OU=project1, O=mycorp, L=Johannesburg, ST=Gauteng, C=za" .
# run via
# docker run -p 8080:8080 -p 8443:8443 absaoss/atum-service:latest

# test via:
# http://localhost:8080/token/public-key
# https://localhost:8443/token/public-key

# Conditional Docker image creation idea: https://stackoverflow.com/a/60820156/1773349
# change to false to disable SSL
ARG SSL
# Amazon correto base image
ARG BASE_IMAGE=amazoncorretto:11.0.22

# --- Base image part (common for SSL true|false) ---
FROM $BASE_IMAGE as base

# Provide your proxy if needed, e.g. http://my.proxy.examle.com:3128
ARG BUILD_PROXY
# Override of the example application config is possible
ARG CONFIG=./src/main/resources/resource.conf
# Provide path to the directory with LDAP certs in PEM format
ARG LDAP_SSL_CERTS_PATH
# ARG SSL_DNAME is defined below in the SSL-enabled image
# In case you build the Dockerfile from another location than the default 'service' dir, provide a prefix to reach it
ARG AS_PREFIX=.
# Version of scala that server was built against
ARG SCALA_VERSION=2.13

LABEL org.opencontainers.image.authors="ABSA"

# The application's jar file
ARG JAR_FILE=${AS_PREFIX}/target/jvm-${SCALA_VERSION}/*.jar

# Add the application's jar to the container
ADD ${JAR_FILE} app.jar

ENV http_proxy=$BUILD_PROXY
ENV https_proxy=$BUILD_PROXY
ENV HTTP_PROXY=$BUILD_PROXY
ENV HTTPS_PROXY=$BUILD_PROXY

RUN mkdir -p /opt/certs

COPY $LDAP_SSL_CERTS_PATH /opt/certs/

RUN for file in `ls /opt/certs/*.pem`; \
do \
    echo yes | keytool -import -file $file -alias ldaps$RANDOM -keystore /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts -storepass changeit; \
done

# --- SSL=true image specifics ---
FROM base AS base-ssl-true
ENV SSL_ENABLED=true
RUN echo "This stage sets SSL=$SSL_ENABLED"

# DNAME for self-signed cert, only applied for SSL=true
ARG SSL_DNAME="CN=*.my.example.com, OU=project1, O=yourcompany, L=Johannesburg, ST=Gauteng, C=za"

# A self-signed certificate for HTTPS
RUN keytool -genkeypair -keyalg RSA -alias selfsigned -keysize 2048 \
    -dname "$SSL_DNAME" \
    -validity 365 -storepass changeit -keystore /etc/ssl/certs/selfsigned.jks -storetype JKS

EXPOSE 8080 8443
ENTRYPOINT ["java","-jar","/app.jar"]

# --- SSL=false image specifics ---
FROM base AS base-ssl-false
ENV SSL_ENABLED=false
RUN echo "This stage sets SSL=$SSL_ENABLED"

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]

# --- Final image assembly ---
FROM base-ssl-${SSL} AS final
RUN echo "Running final stage with SSL_ENABLED=$SSL_ENABLED."