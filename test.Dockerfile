FROM android-in-docker:latest

RUN apt-get update && apt-get install -y curl build-essential dos2unix
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
RUN . ~/.cargo/env && cargo install tailspin

COPY ./scripts/test_scripts/start-android.sh /usr/local/bin/start-android.sh
RUN chmod +x /usr/local/bin/start-android.sh

WORKDIR /app
COPY . .
RUN ./scripts/tool_scripts/shell-crlf.sh

CMD ["./scripts/test_scripts/start-test.sh"]