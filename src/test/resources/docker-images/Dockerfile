FROM tomcat
MAINTAINER Gerta Sheganaku

ENV SERVICE_NAME service__SERVICE_ID__

# if fails, build the source first
ADD target/service.war /usr/local/tomcat/webapps/

RUN apt-get update && apt-get -y install g++ build-essential && \
   wget http://www.devin.com/lookbusy/download/lookbusy-1.4.tar.gz && \
   tar -xzf lookbusy-1.4.tar.gz && \
   cd lookbusy-1.4 && \
   ./configure && \
   make && \
   make install

CMD ["catalina.sh", "run"]
