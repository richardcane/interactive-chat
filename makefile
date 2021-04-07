group = interactivechat
version = SHADOW
shadowFilename = $(group)-$(version)-all.jar
shadowPath = build/libs

shadow: build.gradle
	sh gradlew shadowJar -Pversion=$(version)

$(shadowPath)/$(shadowFilename): shadow
run: $(shadowPath)/$(shadowFilename)
	java -jar $(shadowPath)/$(shadowFilename)