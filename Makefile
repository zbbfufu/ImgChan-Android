
IMG_TAG=zbbfufu/imgchan:latest
GRADLE_VERSION=4.4.1
GRADLE_LOCAL_CACHE=$(HOME)/.gradle/caches
GRADLE_CTN_CACHE=/root/.gradle/caches

img-build:
	tar -c -f - Dockerfile.build | docker build --build-arg GRADLE_VERSION -f Dockerfile.build -t $(IMG_TAG) -

debug:
	docker run -ti -v $(PWD):/build -v $(GRADLE_LOCAL_CACHE):$(GRADLE_CTN_CACHE) $(IMG_TAG) gradle assembleDebug

sh:
	docker run --rm -ti -v $(PWD):/build -v $(GRADLE_LOCAL_CACHE):$(GRADLE_CTN_CACHE) $(IMG_TAG) bash

.PHONY: img-build debug sh
