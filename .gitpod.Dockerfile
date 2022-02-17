FROM gitpod/workspace-full

RUN echo 'unset JAVA_TOOL_OPTIONS' >> /home/gitpod/.bashrc.d/99-clear-java-tool-options &&\
    rm /home/gitpod/.bashrc.d/99-java &&\
    rm -rf /home/gitpod/.sdkman &&\
    curl -fLo ./cs https://git.io/coursier-cli-linux &&\
    chmod +x ./cs &&\
    ./cs java --jvm openjdk:1.11.0-2 --env >> /home/gitpod/.bashrc.d/90-cs &&\
    ./cs install --env >> /home/gitpod/.bashrc.d/90-cs &&\
    ./cs install \
      ammonite:2.1.4 \
      bloop \
      cs \
      sbt-launcher \
      scala:2.13.3 \
      scalafmt:2.5.3 &&\
    ./cs fetch org.scala-sbt:sbt:1.4.7 >/dev/null &&\
    ./cs fetch coursier:2.0.3 >/dev/null &&\
    rm -f ./cs
