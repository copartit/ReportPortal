FROM dockerregistry.copart.com/base/centos7-java11-openjdk
ARG APP_NAME
ARG ARTIFACT_NAME
VOLUME /tmp
ENV PID_FOLDER /tmp
ENV LOG_PATH /tmp
ENV APP_NAME ${APP_NAME}
# Un-comment if additional java opts are required
ENV JAVA_OPTS ${JAVA_OPTS:-"-Xms256m -Xmx1g"} 
COPY target/thin/root/repository /.m2/repository
COPY target/${ARTIFACT_NAME} /${APP_NAME}.jar
ENTRYPOINT java -Dthin.root=/.m2 ${JAVA_OPTS} -jar /${APP_NAME}.jar --spring.profiles.active=${PROFILES}${EXTRA_PROFILES:+,${EXTRA_PROFILES}} ${SPRINGBOOT_APPLICATION_NAME:+--spring.application.name=${SPRINGBOOT_APPLICATION_NAME}}
