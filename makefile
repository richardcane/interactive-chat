package = interactivechat
version = 1.0-SNAPSHOT
shadowFilename = $(package)-$(version)-all.jar
shadowPath = build/libs

shadow: build.gradle
	sh gradlew shadowJar

run-shadow:
	java -jar $(shadowPath)/$(shadowFilename)
