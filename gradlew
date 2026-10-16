#!/bin/sh

app_path=$0
while [ -h "$app_path" ]; do
    link_target=$(ls -ld "$app_path" | sed 's/.*-> //')
    case $link_target in
        /*) app_path=$link_target ;;
        *) app_path=$(dirname "$app_path")/$link_target ;;
    esac
done

app_home=$(cd "$(dirname "$app_path")" && pwd -P)
classpath=$app_home/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    java_cmd=$JAVA_HOME/bin/java
else
    java_cmd=$(command -v java)
fi

if [ ! -x "$java_cmd" ]; then
    echo "Java executable not found: $java_cmd" >&2
    exit 1
fi

exec "$java_cmd" $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname=gradlew \
    -classpath "$classpath" \
    org.gradle.wrapper.GradleWrapperMain "$@"
