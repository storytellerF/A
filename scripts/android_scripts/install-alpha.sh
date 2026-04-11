#!/bin/sh

./gradlew app:android:installRelease --no-daemon -Pserver.flavor=alpha -Pserver.buildType=prod
./gradlew panel:android:installRelease --no-daemon -Pserver.flavor=alpha -Pserver.buildType=prod
