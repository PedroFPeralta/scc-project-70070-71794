FROM tomcat:10.0-jdk17-openjdk
WORKDIR /usr/local/tomcat
#ADD target/tukano-1.war tukano-1
COPY target/tukano-1.war /usr/local/tomcat/webapps/
EXPOSE 8080