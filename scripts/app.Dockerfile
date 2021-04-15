FROM scala/coursier-sbt:0.0.2

ARG DOCKER_TAG

# RUN apt-get update
# RUN apt-get -y install git
# RUN apt-get -y install curl
# RUN sh -c '(echo "#!/usr/bin/env sh" && curl -fLo cs https://git.io/coursier-cli-"$(uname | tr LD ld)") && chmod +x cs'
# RUN ./cs install cs
# ENV PATH=${PATH}:/root/.local/share/coursier/bin
# RUN export PATH="$PATH:/root/.local/share/coursier/bin"
# RUN rm ./cs

# ENV PATH=${PATH}:/root/.local/share/coursier/bin
# RUN export PATH="$PATH:/root/.local/share/coursier/bin"
# RUN mkdir -p /root/.local/share/coursier
# COPY coursier/bin /root/.local/share/coursier/bin
# RUN echo $PATH
# RUN cs install sbt

RUN mkdir -p /usr/src/app/bin
WORKDIR /usr/src/app
COPY ./ /usr/src/app

# RUN cat /etc/hosts

# COPY wait-for-it.sh wait-for-it.sh 
# RUN chmod +x wait-for-it.sh
# ENTRYPOINT [ "/bin/bash", "-c" ]
# CMD ["./wait-for-it.sh" , "project_db:5432" , "--strict" , "--timeout=30000" , "--" , "echo 'db has started'"]
# RUN bash ./wait-for-it.sh project_db:5432 --timeout=3000 --strict -- echo "db is up"

# RUN cat /etc/hosts
# CMD [ "sbt" , "flyway/flywayMigrate" ]
# CMD ["sbtn","universal:packageBin"]
# CMD sh sbtn flyway/flywayMigrate; sbtn universal:packageBin
# RUN sbt flyway/flywayMigrate
# RUN sbt docker:stage

CMD sh Docker/app.sh

# CMD ["coursier", "--help"]

# RUN coursier install sbt
# RUN sbt docker:stage
# RUN 