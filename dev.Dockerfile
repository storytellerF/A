FROM storytellerf/android-in-docker:latest-dev

ARG USER_NAME

USER root
RUN apt update && apt install -y --no-install-recommends --no-install-suggests libavif-bin

USER $USER_NAME
WORKDIR /home/$USER_NAME

COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-entrypoint.sh ./bin/custom-entrypoint.sh
RUN chmod +x ./bin/custom-entrypoint.sh
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/action-after-create.sh ./bin/action-after-create.sh
RUN chmod +x ./bin/action-after-create.sh

RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && echo "$SNIPPET" >> ~/.bashrc

ENTRYPOINT ["sh", "-c", "$HOME/bin/custom-entrypoint.sh"]