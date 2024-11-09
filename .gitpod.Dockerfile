FROM gitpod/workspace-full

RUN echo 'unset JAVA_TOOL_OPTIONS' >> /home/gitpod/.bashrc.d/99-clear-java-tool-options \
  && rm /home/gitpod/.bashrc.d/99-java \
  && rm -rf /home/gitpod/.sdkman \
  && curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > cs \
  && chmod +x ./cs \
  && ./cs java --jvm 11 --env >> /home/gitpod/.bashrc.d/90-cs \
  && ./cs install --env >> /home/gitpod/.bashrc.d/90-cs \
  && ./cs install bloop cs sbt \
  && rm -f ./cs
