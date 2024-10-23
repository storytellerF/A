docker rm -f android-container
docker run -d -p 6080:6080 \
  -p 5554:5554 -p 5555:5555 \
  -e EMULATOR_DEVICE="Samsung Galaxy S10" \
  -e WEB_VNC=true \
  --device /dev/kvm \
  --name android-container \
  budtmo/docker-android:emulator_14.0