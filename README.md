## Pre-requisites

Docker-compose a localstack of rabbit and postgres
```
bash docker-compose up
```

Ensure that postgis extensions are properly set up and types are registered at the database.

## Build

Use the simple build tool [sbt](https://www.scala-sbt.org) to create a docker for an app you need

```bash
>processor/docker:publishLocal
>partner-azigo/docker:publishLocal
...
etc
```

## Usage

Application consists of small services to perform offer management, purchase tracking, cashback awards for third-party customers. A lot of options are still held in .conf files, that is generally a bad idea due to possible mess with finding the right one in classpath, but still may be helpful to run the application without environment settings / parameters.

### `merch-api`
Provides an interface for the offers and transactions for the customers as well as authentication + authorization

### `partner-*`
Is a set of connector services with the merchants. Provides tracking information based on customer ids, throughout the purchase process. Converts third-party data into owned types and then publishes the received data to the RabbitMQ

### `processor`
Contains the logic to handle all the incoming transactions from RabbitMQ. Transaction validation, settlement occurs here.

### `testserver`
Is a stress test application with data generator and artificial customer's webhook.
