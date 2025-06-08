#!/bin/sh
keytool -genkeypair -storepass 123456 -storetype pkcs12 -alias snapshot -validity 365 -v -keyalg RSA -keystore keystore2.p12