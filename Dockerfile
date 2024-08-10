FROM amazoncorretto:21

COPY target/pack /srv/podpodge

# Using a non-privileged user:
USER nobody
WORKDIR /srv/podpodge

ENTRYPOINT ["sh", "./bin/podpodge"]