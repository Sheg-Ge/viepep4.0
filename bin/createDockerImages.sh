#!/bin/bash

for i in {7..10}
do
	mkdir -p target/docker-images/service$i/target/
	cp src/test/resources/docker-images/Dockerfile target/docker-images/service$i
	sed -i s/__SERVICE_ID__/$i/g target/docker-images/service$i/Dockerfile 
	cp ../viepep-backend-services/target/service.war target/docker-images/service$i/target/
	(cd target/docker-images/service$i && docker build -t shegge/viepep-docker-$i .)
done

