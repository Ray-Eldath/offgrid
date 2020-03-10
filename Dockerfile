FROM adoptopenjdk/openjdk13-openj9:alpine
LABEL maintainer="Ray Eldath <ray.eldath@outlook.com>"
ENV TZ='Asia/Shanghai'
COPY build/libs/offgrid*all.jar /offgrid/
WORKDIR /offgrid
EXPOSE 8080
ENTRYPOINT exec java -jar $(ls | grep offgrid-**-all.jar)