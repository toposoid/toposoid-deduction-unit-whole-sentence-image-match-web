# toposoid-deduction-unit-whole-sentence-image-match-web
This is a WEB API that works as a microservice within the toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice makes inferences by matching images to a knowledge database.

[![Test And Build](https://github.com/toposoid/toposoid-deduction-unit-whole-sentence-image-match-web/actions/workflows/action.yml/badge.svg)](https://github.com/toposoid/toposoid-deduction-unit-whole-sentence-image-match-web/actions/workflows/action.yml)

* API Image
    * Input
    * 
    * Output
    * 

## Requirements
* Docker version 20.10.x, or late
* docker-compose version 1.22.x
* The following microservices must be running
    * scala-data-accessor-neo4j-web
    * toposoid-common-nlp-japanese-web
    * toposoid-common-nlp-english-web
    * toposoid-common-image-recognition-web
    * data-accessor-weaviate-web
    * semitechnologies/weaviate
    * neo4j

## Recommended Environment For Standalone
* Required: at least 16GB of RAM
* Required: at least 40G of HDD(Total required Docker Image size)
* Please understand that since we are dealing with large models such as LLM, the Dockerfile size is large and the required machine SPEC is high.


## Setup For Standalone
```bssh
docker-compose up
```
The first startup takes a long time until docker pull finishes.
## Usage
```bash
# Please refer to the following for information on registering data to try deduction.
# ref. https://github.com/toposoid/toposoid-knowledge-register-web
#for example
curl -X POST -H "Content-Type: application/json" -d '' http://localhost:9002/regist

# Deduction
curl -X POST -H "Content-Type: application/json" -d '' http://localhost:9105/execute
```

## For details on Input Json
see below.
* ref. https://github.com/toposoid/toposoid-deduction-admin-web?tab=readme-ov-file#json-details

# Note
* This microservice uses 9105 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.

## License
toposoid/toposoid-deduction-unit-whole-sentence-image-match-web is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!
