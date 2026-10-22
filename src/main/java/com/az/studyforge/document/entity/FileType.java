package com.az.studyforge.document.entity;

public enum FileType {
    PDF("pdf"), PPTX("pptx");

    private final String extension;
    FileType(String extension) { this.extension = extension; }
    public String extension() { return extension; }
}
