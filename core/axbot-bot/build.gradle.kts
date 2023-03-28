plugins {
    id("java")
    id("java-library")
}

group = "com.github.axiangcoding.axbot.bot"
version = "1.0-SNAPSHOT"

dependencies {
    // https://mvnrepository.com/artifact/com.squareup.retrofit2/retrofit
    api("com.squareup.retrofit2:retrofit:2.9.0")
    // https://mvnrepository.com/artifact/com.squareup.retrofit2/converter-jackson
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/logging-interceptor
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")

    // https://mvnrepository.com/artifact/org.projectlombok/lombok
    compileOnly("org.projectlombok:lombok:1.18.26")
    annotationProcessor("org.projectlombok:lombok:1.18.26")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    testImplementation("org.slf4j:slf4j-simple:2.0.7")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    implementation("org.slf4j:slf4j-api:2.0.7")

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}