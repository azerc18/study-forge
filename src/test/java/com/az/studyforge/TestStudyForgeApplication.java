package com.az.studyforge;

import org.springframework.boot.SpringApplication;

public class TestStudyForgeApplication {

    public static void main(String[] args) {
        SpringApplication.from(StudyForgeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
