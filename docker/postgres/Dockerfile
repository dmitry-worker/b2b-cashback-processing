FROM mdillon/postgis:10-alpine 

RUN mkdir -p /tmp/dump/
RUN mkdir -p /docker-entrypoint-initdb.d

COPY ./initdb-postgis.sh /docker-entrypoint-initdb.d/init.sh
COPY ./update-postgis.sh /usr/local/bin
