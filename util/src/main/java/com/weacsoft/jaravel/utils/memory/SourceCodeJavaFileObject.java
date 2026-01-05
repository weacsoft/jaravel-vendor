package com.weacsoft.jaravel.utils.memory;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.nio.CharBuffer;

public class SourceCodeJavaFileObject extends SimpleJavaFileObject {
    private final String sourceCode;

    public SourceCodeJavaFileObject(String className, String sourceCode) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.sourceCode = sourceCode;
    }

    @Override
    public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
        return CharBuffer.wrap(sourceCode);
    }
}