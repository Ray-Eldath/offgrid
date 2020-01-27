FROM openjdk:11-jdk
MAINTAINER Ray Eldath
ENV TZ='Asia/Shanghai'
COPY build/libs/offgrid*all.jar /offgrid/
WORKDIR /offgrid
EXPOSE 8080
CMD exec java -jar $(ls | grep offgrid-**-all.jar)