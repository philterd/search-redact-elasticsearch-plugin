FROM elasticsearch:9.4.2

ARG VERSION="9.4.2-SNAPSHOT"

COPY ./build/distributions/search-redact-${VERSION}.zip /tmp/

RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:/tmp/search-redact-${VERSION}.zip
