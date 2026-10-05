// Vendored copy of https://github.com/orgzly/org-java (GPLv3), see org-java/README.md
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly("org.jetbrains:annotations:18.0.0")
}
