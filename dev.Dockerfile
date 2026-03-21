FROM storytellerf/android-in-docker:latest-dev

ARG USER_NAME

USER root
RUN apt update && apt install -y --no-install-recommends --no-install-suggests \
    libavif-bin git-lfs fcitx fcitx-googlepinyin

RUN git lfs install

RUN groupadd -g 1001 docker \
    && usermod -aG docker $USER_NAME

USER $USER_NAME
WORKDIR /home/$USER_NAME

COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-entrypoint.sh ./bin/custom-entrypoint.sh
RUN chmod +x ./bin/custom-entrypoint.sh

ENTRYPOINT ["sh", "-c", "$HOME/bin/custom-entrypoint.sh"]