run:
	rm -rf target
	export $$(cat ../.env | grep -v '^#' | xargs) && ./mvnw clean -DskipTests spring-boot:run
