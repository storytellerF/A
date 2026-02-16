FROM storytellerf/android-in-docker:latest

# RUN sudo apt-get update && apt-get install -y curl build-essential
# RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
# RUN . ~/.cargo/env && cargo install tailspin
ARG USER_NAME

USER root
RUN apt update && apt install -y libavif-bin curl
RUN groupadd -g 1001 docker \
    && usermod -aG docker $USER_NAME

USER $USER_NAME
WORKDIR /home/$USER_NAME
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-start-android.sh ./bin/start-android.sh
RUN chmod +x ./bin/start-android.sh
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-entrypoint.sh ./bin/custom-entrypoint.sh
RUN chmod +x ./bin/custom-entrypoint.sh

RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && echo "$SNIPPET" >> ~/.bashrc
