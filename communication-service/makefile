run:
	-lsof -ti:8099 | xargs kill -9
	rm -rf target
	export $$(cat ../.env | grep -v '^#' | xargs) && ./mvnw clean -DskipTests spring-boot:run