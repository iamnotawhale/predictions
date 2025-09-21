.PHONY: all push docker dockerPush

IMAGE := imnotawhale/predicts
TAG   := latest

all: docker

push: dockerPush

docker:
	# TAB at the start of this line
	docker build -t $(IMAGE):$(TAG) .

dockerPush:
	# TAB at the start of this line
	docker push $(IMAGE):$(TAG)
