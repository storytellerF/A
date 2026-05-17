#!/bin/sh

./gradlew app:android:installRelease -Pserver.flavor=alpha -Pserver.buildType=prod
./gradlew panel:android:installRelease -Pserver.flavor=alpha -Pserver.buildType=prod
