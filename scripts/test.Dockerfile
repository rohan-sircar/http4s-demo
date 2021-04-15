FROM scala/coursier/sbt:v0.0.1
# RUN apt search docker
RUN apt install -y docker.io
RUN docker --help