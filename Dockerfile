FROM debian AS deb
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get upgrade -y && apt-get install -y locales && apt-get clean && \
    ln -sf /usr/share/zoneinfo/Europe/Moscow /etc/localtime && dpkg-reconfigure --frontend noninteractive tzdata && \
    locale-gen C.UTF-8 && update-locale
ENV LC_ALL=C.UTF-8

FROM deb AS jre
RUN apt-get install -y ca-certificates-java && \
    apt-get install -y openjdk-17-jre-headless

FROM jre AS maven
RUN apt-get install -y maven

FROM maven AS builder
WORKDIR /build
COPY . /build

RUN mvn dependency:go-offline

RUN mvn package

FROM jre AS runner

RUN mkdir -p app/tmp app/logs app/config
WORKDIR /app

COPY --from=builder build/target/predictions-1.0.0.jar /app/predictions-1.0.0.jar
ENTRYPOINT ["java", "-jar", "predictions-1.0.0.jar"]