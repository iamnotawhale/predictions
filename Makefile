all: docker

push: dockerPush

docker:
        docker build -t imnotawhale/predicts:latest .

dockerPush:
        docker push imnotawhale/predicts:latest