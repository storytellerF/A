FROM storytellerf/android-in-docker:latest

ARG USER_NAME

USER root
RUN apt update && apt install -y --no-install-recommends --no-install-suggests libavif-bin curl openssh-server fonts-noto
RUN groupadd -g 1001 docker \
    && usermod -aG docker $USER_NAME

# RUN echo "${USER_NAME}:123456" | chpasswd

# 复制并解压 Android Studio
# 假设已经将 android-studio-*.tar.gz 下载到 download 目录
COPY download/android-studio-*.tar.gz /tmp/android-studio.tar.gz

RUN mkdir -p /home/${USER_NAME}/Applications && \
    tar -xzf /tmp/android-studio.tar.gz -C /home/${USER_NAME}/Applications && \
    rm /tmp/android-studio.tar.gz

RUN chown -R ${USER_NAME}:${USER_NAME} /home/${USER_NAME}/Applications/android-studio

# 设置环境变量
ENV PATH="/home/${USER_NAME}/Applications/android-studio/bin:${PATH}"

# 创建桌面快捷方式
RUN mkdir -p /home/${USER_NAME}/Desktop && \
    printf "[Desktop Entry]\nVersion=1.0\nType=Application\nName=Android Studio\nExec=studio\nIcon=/home/${USER_NAME}/Applications/android-studio/bin/studio.svg\nTerminal=false\nCategories=Development;IDE;" > /home/${USER_NAME}/Desktop/android-studio.desktop && \
    chmod +x /home/${USER_NAME}/Desktop/android-studio.desktop && \
    chown ${USER_NAME}:${USER_NAME} /home/${USER_NAME}/Desktop/android-studio.desktop

USER $USER_NAME
WORKDIR /home/$USER_NAME
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-start-android.sh ./bin/start-android.sh
RUN chmod +x ./bin/start-android.sh
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/custom-entrypoint.sh ./bin/custom-entrypoint.sh
RUN chmod +x ./bin/custom-entrypoint.sh
COPY --chown=$USER_NAME:$USER_NAME ./scripts/test_scripts/action-after-create.sh ./bin/action-after-create.sh
RUN chmod +x ./bin/action-after-create.sh

RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
    && echo "$SNIPPET" >> ~/.bashrc

EXPOSE 22