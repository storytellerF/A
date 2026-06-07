#!/bin/sh

./gradlew app:androidApp:installRelease -Pserver.flavor=alpha -Pserver.buildType=prod
./gradlew panel:androidApp:installRelease -Pserver.flavor=alpha -Pserver.buildType=prod
