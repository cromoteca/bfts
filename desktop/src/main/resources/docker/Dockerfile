FROM openjdk:8
RUN apt-get update
RUN apt-get upgrade -y
RUN apt-get install sqlite -y
RUN mkdir -p /usr/bfts
COPY ./bfts.jar /usr/bfts
WORKDIR /usr/bfts
RUN java -jar bfts.jar init local /usr/bfts/storage 8715 dkrpwd
CMD ["java", "-jar", "bfts.jar", "start", "false", "dkrpwd"]
