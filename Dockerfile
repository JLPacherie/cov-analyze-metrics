#
# Dockfile to create an minimal image to test cov-analyze-metrics
#
FROM openjdk:8-jre-alpine

# Install Bash
RUN apk add --no-cache bash libxslt

# Install the app
COPY /bin /cov-analyze-metrics/bin/
COPY /config /cov-analyze-metrics/config/

WORKDIR /cov-analyze-metrics

# Run tests
CMD ./bin/cov-analyze-metrics --help