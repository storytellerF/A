FROM android-in-docker:latest

RUN apt-get update && apt-get install -y curl build-essential dos2unix
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
RUN . ~/.cargo/env && cargo install tailspin

COPY ./scripts/test_scripts/disable-start-android.sh /usr/local/bin/start-android.sh
RUN chmod +x /usr/local/bin/start-android.sh

WORKDIR /app

CMD ["./scripts/test_scripts/test-entry-in-docker.sh"]