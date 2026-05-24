FROM storytellerf/android-in-docker:ubuntu-mate-cn-dev-latest

ARG USER_NAME

USER root
RUN apt update && DEBIAN_FRONTEND=nointeractive apt install -y \
    libavif-bin git-lfs

RUN git lfs install

USER $USER_NAME
WORKDIR /home/$USER_NAME
