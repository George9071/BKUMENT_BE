IMAGE_NAME = kl2k4/bkment-identity-service
TAG ?= 2025-12-25-01
PLATFORM = linux/amd64

.PHONY: build push build-push

build-push:
	mvn clean package
	docker buildx build \
		--platform $(PLATFORM) \
		--no-cache \
		-t $(IMAGE_NAME):$(TAG) \
		--push .

build:
	mvn clean package
	docker buildx build \
		--platform $(PLATFORM) \
		--no-cache \
		-t $(IMAGE_NAME):$(TAG) .

push:
	docker push $(IMAGE_NAME):$(TAG)

inspect:
	docker manifest inspect $(IMAGE_NAME):$(TAG)
