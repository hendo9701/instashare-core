# instashare-core


![example workflow](https://github.com/hendo9701/instashare-core/actions/workflows/main.yml/badge.svg)
[![codecov](https://codecov.io/gh/hendo9701/instashare-core/branch/master/graph/badge.svg?token=UJWMOTORCM)](https://codecov.io/gh/hendo9701/instashare-core)
![quality-score](https://api.codiga.io/project/34301/score/svg)
![code-grade](https://api.codiga.io/project/34301/status/svg)


## Description

This is an API for performing the following operations on files:

1. Uploading
1. Downloading
1. Renaming
1. Retrieving

The application uses AWS S3 for file storage and MongoDB for holding domain data.

## OpenAPI

API documentation is available at ```http://{app-host}:{app-port}/webjars/swagger-ui/index.html#/```

## Bootstrapping the API

1. Run ``docker-compose up`` from the project's root folder
1. Run ``mvn spring-boot:run``

## Running the tests

Run ``mvn clean test``


