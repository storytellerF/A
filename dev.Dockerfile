FROM storytellerf/android-in-docker:dev-latest

ARG USER_NAME

USER root
RUN apt update && DEBIAN_FRONTEND=nointeractive apt install -y \
    libavif-bin git-lfs fcitx fcitx-googlepinyin

RUN git lfs install

USER $USER_NAME
WORKDIR /home/$USER_NAME

COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-entrypoint.sh ./bin/custom-entrypoint.sh
RUN chmod +x ./bin/custom-entrypoint.sh

ENTRYPOINT ["sh", "-c", "$HOME/bin/custom-entrypoint.sh"]