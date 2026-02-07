FROM android-in-docker:latest

# RUN sudo apt-get update && apt-get install -y curl build-essential
# RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
# RUN . ~/.cargo/env && cargo install tailspin
USER root
RUN apt update && apt install -y libavif-bin fonts-noto curl
RUN groupadd -g 1001 docker \
    && usermod -aG docker ubuntu

USER ubuntu
WORKDIR /home/ubuntu
COPY --chown=ubuntu:ubuntu ./scripts/test_scripts/custom-start-android.sh ./bin/start-android.sh
RUN chmod +x ./bin/start-android.sh
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && echo "$SNIPPET" >> ~/.bashrc
