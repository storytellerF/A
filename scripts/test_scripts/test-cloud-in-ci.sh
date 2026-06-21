#!/bin/bash

mkdir -p ~/.gradle
echo "gpr.user=$ORG_GRADLE_PROJECT_gpr_user" >> ~/.gradle/gradle.properties
echo "gpr.key=$ORG_GRADLE_PROJECT_gpr_key" >> ~/.gradle/gradle.properties

ENABLE_TEST_CONTAINER=true ./gradlew :backend:core:test :backend:minio:test :cloud:cli:test :cloud:service:test :cloud:server:test --console=plain